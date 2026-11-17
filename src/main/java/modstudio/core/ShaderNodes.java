package modstudio.core;

import modstudio.core.Systems.Shader;
import modstudio.core.Systems.ShaderNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shader graphs: the node kinds, the GLSL a graph turns into and an evaluator that runs the same graph on the CPU for
 * the editor preview. A graph is one function, {@code vec4 f(vec4 color, vec2 uv, float time, vec2 screen)}, that
 * takes the colour a shader would draw and returns a new one. For a core program it is applied to the final
 * fragColor of the original source; for an include file it is written as a function other shaders can call.
 */
public final class ShaderNodes {
    private ShaderNodes() {}

    /** Value types; every value is held as four numbers in the evaluator. */
    public enum VT {
        F("float"), V2("vec2"), V3("vec3"), V4("vec4");

        public final String glsl;

        VT(String glsl) {
            this.glsl = glsl;
        }
    }

    /** An input or output. def is the value of an unconnected input: a number, "x, y", "#RRGGBB" or @color, @uv, @time, @screen. */
    public record Port(String key, VT type, String def, boolean fixed) {
        static Port in(String key, VT type, String def) {
            return new Port(key, type, def, false);
        }

        static Port fixed(String key, VT type, String def) {
            return new Port(key, type, def, true);
        }

        static Port out(String key, VT type) {
            return new Port(key, type, "", false);
        }
    }

    /** GLSL expressions of the outputs, from the input expressions (already of the input types). */
    interface Glsl {
        String[] expr(String[] in);
    }

    /** CPU version of a node: reads the inputs and writes the outputs (four numbers each). */
    interface Eval {
        void run(double[][] in, double[][] out, Env env);
    }

    /** What the inputs of a graph are while the preview draws one pixel. */
    public static final class Env {
        public final double[] color = new double[4];
        public double u, v, time, sx, sy;
        public Sampler texture;
    }

    public interface Sampler {
        void sample(double u, double v, double[] out);
    }

    public record Def(String id, String category, List<Port> ins, List<Port> outs, Glsl glsl, Eval eval) {}

    public static final Map<String, Def> DEFS = new LinkedHashMap<>();
    public static final List<String> CATEGORIES = List.of("input", "math", "vector", "color", "pattern", "output");

    private static void def(String id, String cat, List<Port> ins, List<Port> outs, Glsl glsl, Eval eval) {
        DEFS.put(id, new Def(id, cat, ins, outs, glsl, eval));
    }

    private static List<Port> ins(Port... p) {
        return List.of(p);
    }

    private static List<Port> out(VT t) {
        return List.of(Port.out("out", t));
    }

    private static String[] one(String s) {
        return new String[] {s};
    }

    /** Element-wise math on four numbers. */
    private interface Op1 {
        double f(double a);
    }

    private interface Op2 {
        double f(double a, double b);
    }

    private static Eval map1(Op1 op) {
        return (in, out, env) -> {
            for (int i = 0; i < 4; i++) out[0][i] = op.f(in[0][i]);
        };
    }

    private static Eval map2(Op2 op) {
        return (in, out, env) -> {
            for (int i = 0; i < 4; i++) out[0][i] = op.f(in[0][i], in[1][i]);
        };
    }

    private static double fract(double x) {
        return x - Math.floor(x);
    }

    private static double clamp(double x, double a, double b) {
        return Math.max(a, Math.min(b, x));
    }

    private static double smooth(double e0, double e1, double x) {
        double t = clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static double lum(double[] c) {
        return c[0] * 0.299 + c[1] * 0.587 + c[2] * 0.114;
    }

    static double hash(double x, double y) {
        double px = fract(x * 123.34), py = fract(y * 456.21);
        double d = px * (px + 45.32) + py * (py + 45.32);
        px += d;
        py += d;
        return fract(px * py);
    }

    static double noise(double x, double y) {
        double ix = Math.floor(x), iy = Math.floor(y), fx = x - ix, fy = y - iy;
        double ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy);
        double a = hash(ix, iy), b = hash(ix + 1, iy), c = hash(ix, iy + 1), d = hash(ix + 1, iy + 1);
        double ab = a + (b - a) * ux, cd = c + (d - c) * ux;
        return ab + (cd - ab) * uy;
    }

    private static void hue(double[] c, double a, double[] out) {
        double k = 0.57735026919, ca = Math.cos(a), sa = Math.sin(a);
        double dot = k * (c[0] + c[1] + c[2]);
        // cross(k, c) with k = (k, k, k)
        double cx = k * c[2] - k * c[1], cy = k * c[0] - k * c[2], cz = k * c[1] - k * c[0];
        out[0] = c[0] * ca + cx * sa + k * dot * (1 - ca);
        out[1] = c[1] * ca + cy * sa + k * dot * (1 - ca);
        out[2] = c[2] * ca + cz * sa + k * dot * (1 - ca);
        out[3] = c[3];
    }

    static {
        VT F = VT.F, V2 = VT.V2, V4 = VT.V4;
        // ---------------------------------------------------------------- inputs
        def("input_color", "input", List.of(), out(V4), in -> one("color"), (in, out, env) -> System.arraycopy(env.color, 0, out[0], 0, 4));
        def("uv", "input", List.of(), out(V2), in -> one("uv"), (in, out, env) -> {
            out[0][0] = env.u;
            out[0][1] = env.v;
        });
        def("time", "input", List.of(), out(F), in -> one("time"), (in, out, env) -> out[0][0] = env.time);
        def("screen", "input", List.of(), out(V2), in -> one("screen"), (in, out, env) -> {
            out[0][0] = env.sx;
            out[0][1] = env.sy;
        });
        def("number", "input", ins(Port.fixed("value", F, "1")), out(F), in -> one(in[0]), (in, out, env) -> out[0][0] = in[0][0]);
        def("color", "input", ins(Port.fixed("color", V4, "#FF4060")), out(V4), in -> one(in[0]), (in, out, env) -> System.arraycopy(in[0], 0, out[0], 0, 4));
        def("texture", "input", ins(Port.in("uv", V2, "@uv")), out(V4), in -> one("texture(Sampler0, " + in[0] + ")"), (in, out, env) -> {
            if (env.texture != null) env.texture.sample(in[0][0], in[0][1], out[0]);
        });

        // ---------------------------------------------------------------- math (four components at once)
        def("add", "math", ins(Port.in("a", V4, "@color"), Port.in("b", V4, "0")), out(V4), in -> one("(" + in[0] + " + " + in[1] + ")"), map2(Double::sum));
        def("subtract", "math", ins(Port.in("a", V4, "@color"), Port.in("b", V4, "0")), out(V4), in -> one("(" + in[0] + " - " + in[1] + ")"),
                map2((a, b) -> a - b));
        def("multiply", "math", ins(Port.in("a", V4, "@color"), Port.in("b", V4, "1")), out(V4), in -> one("(" + in[0] + " * " + in[1] + ")"),
                map2((a, b) -> a * b));
        def("divide", "math", ins(Port.in("a", V4, "@color"), Port.in("b", V4, "1")), out(V4), in -> one("(" + in[0] + " / " + in[1] + ")"),
                map2((a, b) -> a / b));
        def("mix", "math", ins(Port.in("a", V4, "@color"), Port.in("b", V4, "#FFFFFF"), Port.in("t", F, "0.5")), out(V4),
                in -> one("mix(" + in[0] + ", " + in[1] + ", " + in[2] + ")"), (in, out, env) -> {
                    for (int i = 0; i < 4; i++) out[0][i] = in[0][i] + (in[1][i] - in[0][i]) * in[2][0];
                });
        def("min", "math", ins(Port.in("a", V4, "@color"), Port.in("b", V4, "1")), out(V4), in -> one("min(" + in[0] + ", " + in[1] + ")"), map2(Math::min));
        def("max", "math", ins(Port.in("a", V4, "@color"), Port.in("b", V4, "0")), out(V4), in -> one("max(" + in[0] + ", " + in[1] + ")"), map2(Math::max));
        def("power", "math", ins(Port.in("a", V4, "@color"), Port.in("b", V4, "2")), out(V4), in -> one("pow(max(" + in[0] + ", 0.0), " + in[1] + ")"),
                map2((a, b) -> Math.pow(Math.max(a, 0), b)));
        def("clamp01", "math", ins(Port.in("x", V4, "@color")), out(V4), in -> one("clamp(" + in[0] + ", 0.0, 1.0)"), map1(a -> clamp(a, 0, 1)));
        def("one_minus", "math", ins(Port.in("x", V4, "@color")), out(V4), in -> one("(1.0 - " + in[0] + ")"), map1(a -> 1 - a));
        def("abs", "math", ins(Port.in("x", V4, "0")), out(V4), in -> one("abs(" + in[0] + ")"), map1(Math::abs));
        def("fract", "math", ins(Port.in("x", V4, "0")), out(V4), in -> one("fract(" + in[0] + ")"), map1(ShaderNodes::fract));
        def("floor", "math", ins(Port.in("x", V4, "0")), out(V4), in -> one("floor(" + in[0] + ")"), map1(Math::floor));
        def("sin", "math", ins(Port.in("x", V4, "@time")), out(V4), in -> one("sin(" + in[0] + ")"), map1(Math::sin));
        def("cos", "math", ins(Port.in("x", V4, "@time")), out(V4), in -> one("cos(" + in[0] + ")"), map1(Math::cos));
        def("step", "math", ins(Port.in("edge", F, "0.5"), Port.in("x", V4, "0")), out(V4), in -> one("step(" + in[0] + ", " + in[1] + ")"),
                (in, out, env) -> {
                    for (int i = 0; i < 4; i++) out[0][i] = in[1][i] < in[0][0] ? 0 : 1;
                });
        def("smoothstep", "math", ins(Port.in("edge0", F, "0"), Port.in("edge1", F, "1"), Port.in("x", V4, "0")), out(V4),
                in -> one("smoothstep(" + in[0] + ", " + in[1] + ", " + in[2] + ")"), (in, out, env) -> {
                    for (int i = 0; i < 4; i++) out[0][i] = smooth(in[0][0], in[1][0], in[2][i]);
                });
        def("remap", "math", ins(Port.in("x", F, "0.5"), Port.in("in_min", F, "0"), Port.in("in_max", F, "1"), Port.in("out_min", F, "0"),
                Port.in("out_max", F, "1")), out(F),
                in -> one("(" + in[3] + " + (" + in[0] + " - " + in[1] + ") / (" + in[2] + " - " + in[1] + ") * (" + in[4] + " - " + in[3] + "))"),
                (in, out, env) -> out[0][0] = in[3][0] + (in[0][0] - in[1][0]) / (in[2][0] - in[1][0]) * (in[4][0] - in[3][0]));
        def("length", "math", ins(Port.in("v", V2, "@uv")), out(F), in -> one("length(" + in[0] + ")"),
                (in, out, env) -> out[0][0] = Math.hypot(in[0][0], in[0][1]));
        def("distance", "math", ins(Port.in("a", V2, "@uv"), Port.in("b", V2, "0.5, 0.5")), out(F), in -> one("distance(" + in[0] + ", " + in[1] + ")"),
                (in, out, env) -> out[0][0] = Math.hypot(in[0][0] - in[1][0], in[0][1] - in[1][1]));

        // ---------------------------------------------------------------- vectors and texture coordinates
        def("split", "vector", ins(Port.in("v", V4, "@color")),
                List.of(Port.out("x", F), Port.out("y", F), Port.out("z", F), Port.out("w", F)),
                in -> new String[] {in[0] + ".x", in[0] + ".y", in[0] + ".z", in[0] + ".w"}, (in, out, env) -> {
                    for (int i = 0; i < 4; i++) out[i][0] = in[0][i];
                });
        def("combine", "vector", ins(Port.in("x", F, "0"), Port.in("y", F, "0"), Port.in("z", F, "0"), Port.in("w", F, "1")), out(V4),
                in -> one("vec4(" + in[0] + ", " + in[1] + ", " + in[2] + ", " + in[3] + ")"), (in, out, env) -> {
                    for (int i = 0; i < 4; i++) out[0][i] = in[i][0];
                });
        def("uv_scroll", "vector", ins(Port.in("uv", V2, "@uv"), Port.in("speed", V2, "0.1, 0")), out(V2),
                in -> one("(" + in[0] + " + " + in[1] + " * time)"), (in, out, env) -> {
                    out[0][0] = in[0][0] + in[1][0] * env.time;
                    out[0][1] = in[0][1] + in[1][1] * env.time;
                });
        def("uv_tile", "vector", ins(Port.in("uv", V2, "@uv"), Port.in("scale", V2, "2, 2")), out(V2), in -> one("fract(" + in[0] + " * " + in[1] + ")"),
                (in, out, env) -> {
                    out[0][0] = fract(in[0][0] * in[1][0]);
                    out[0][1] = fract(in[0][1] * in[1][1]);
                });
        def("uv_rotate", "vector", ins(Port.in("uv", V2, "@uv"), Port.in("angle", F, "0.5")), out(V2), in -> one("ms_g_rotate(" + in[0] + ", " + in[1] + ")"),
                (in, out, env) -> {
                    double dx = in[0][0] - 0.5, dy = in[0][1] - 0.5, s = Math.sin(in[1][0]), c = Math.cos(in[1][0]);
                    out[0][0] = dx * c - dy * s + 0.5;
                    out[0][1] = dx * s + dy * c + 0.5;
                });
        def("uv_wave", "vector", ins(Port.in("uv", V2, "@uv"), Port.in("amplitude", F, "0.02"), Port.in("frequency", F, "12"), Port.in("speed", F, "3")),
                out(V2), in -> one("(" + in[0] + " + vec2(sin(" + in[0] + ".y * " + in[2] + " + time * " + in[3] + ") * " + in[1] + ", 0.0))"),
                (in, out, env) -> {
                    out[0][0] = in[0][0] + Math.sin(in[0][1] * in[2][0] + env.time * in[3][0]) * in[1][0];
                    out[0][1] = in[0][1];
                });
        def("uv_pixelate", "vector", ins(Port.in("uv", V2, "@uv"), Port.in("pixels", F, "8")), out(V2),
                in -> one("((floor(" + in[0] + " * " + in[1] + ") + 0.5) / " + in[1] + ")"), (in, out, env) -> {
                    out[0][0] = (Math.floor(in[0][0] * in[1][0]) + 0.5) / in[1][0];
                    out[0][1] = (Math.floor(in[0][1] * in[1][0]) + 0.5) / in[1][0];
                });

        // ---------------------------------------------------------------- colour (alpha is kept)
        def("grayscale", "color", ins(Port.in("color", V4, "@color")), out(V4),
                in -> one("vec4(vec3(dot(" + in[0] + ".rgb, vec3(0.299, 0.587, 0.114))), " + in[0] + ".a)"), (in, out, env) -> {
                    double l = lum(in[0]);
                    out[0][0] = out[0][1] = out[0][2] = l;
                    out[0][3] = in[0][3];
                });
        def("saturation", "color", ins(Port.in("color", V4, "@color"), Port.in("amount", F, "1.5")), out(V4),
                in -> one("vec4(mix(vec3(dot(" + in[0] + ".rgb, vec3(0.299, 0.587, 0.114))), " + in[0] + ".rgb, " + in[1] + "), " + in[0] + ".a)"),
                (in, out, env) -> {
                    double l = lum(in[0]);
                    for (int i = 0; i < 3; i++) out[0][i] = l + (in[0][i] - l) * in[1][0];
                    out[0][3] = in[0][3];
                });
        def("hue_shift", "color", ins(Port.in("color", V4, "@color"), Port.in("angle", F, "1")), out(V4),
                in -> one("vec4(ms_g_hue(" + in[0] + ".rgb, " + in[1] + "), " + in[0] + ".a)"), (in, out, env) -> hue(in[0], in[1][0], out[0]));
        def("tint", "color", ins(Port.in("color", V4, "@color"), Port.in("tint", V4, "#FF4060"), Port.in("amount", F, "0.5")), out(V4),
                in -> one("vec4(mix(" + in[0] + ".rgb, " + in[0] + ".rgb * " + in[1] + ".rgb, " + in[2] + "), " + in[0] + ".a)"), (in, out, env) -> {
                    for (int i = 0; i < 3; i++) out[0][i] = in[0][i] + (in[0][i] * in[1][i] - in[0][i]) * in[2][0];
                    out[0][3] = in[0][3];
                });
        def("posterize", "color", ins(Port.in("color", V4, "@color"), Port.in("steps", F, "4")), out(V4),
                in -> one("vec4(floor(" + in[0] + ".rgb * " + in[1] + " + 0.5) / " + in[1] + ", " + in[0] + ".a)"), (in, out, env) -> {
                    for (int i = 0; i < 3; i++) out[0][i] = Math.floor(in[0][i] * in[1][0] + 0.5) / in[1][0];
                    out[0][3] = in[0][3];
                });
        def("invert", "color", ins(Port.in("color", V4, "@color")), out(V4), in -> one("vec4(1.0 - " + in[0] + ".rgb, " + in[0] + ".a)"),
                (in, out, env) -> {
                    for (int i = 0; i < 3; i++) out[0][i] = 1 - in[0][i];
                    out[0][3] = in[0][3];
                });
        def("brightness_contrast", "color", ins(Port.in("color", V4, "@color"), Port.in("brightness", F, "0"), Port.in("contrast", F, "1.2")), out(V4),
                in -> one("vec4((" + in[0] + ".rgb - 0.5) * " + in[2] + " + 0.5 + " + in[1] + ", " + in[0] + ".a)"), (in, out, env) -> {
                    for (int i = 0; i < 3; i++) out[0][i] = (in[0][i] - 0.5) * in[2][0] + 0.5 + in[1][0];
                    out[0][3] = in[0][3];
                });
        def("set_alpha", "color", ins(Port.in("color", V4, "@color"), Port.in("alpha", F, "1")), out(V4),
                in -> one("vec4(" + in[0] + ".rgb, " + in[1] + ")"), (in, out, env) -> {
                    System.arraycopy(in[0], 0, out[0], 0, 3);
                    out[0][3] = in[1][0];
                });
        def("emissive", "color", ins(Port.in("color", V4, "@color"), Port.in("glow", V4, "#FF2050"), Port.in("strength", F, "0.5")), out(V4),
                in -> one("vec4(" + in[0] + ".rgb + " + in[1] + ".rgb * " + in[2] + ", " + in[0] + ".a)"), (in, out, env) -> {
                    for (int i = 0; i < 3; i++) out[0][i] = in[0][i] + in[1][i] * in[2][0];
                    out[0][3] = in[0][3];
                });

        // ---------------------------------------------------------------- patterns (0 to 1)
        def("pulse", "pattern", ins(Port.in("speed", F, "1")), out(F), in -> one("(0.5 + 0.5 * sin(time * " + in[0] + " * 6.2831853))"),
                (in, out, env) -> out[0][0] = 0.5 + 0.5 * Math.sin(env.time * in[0][0] * Math.PI * 2));
        def("sine", "pattern", ins(Port.in("x", F, "@time"), Port.in("frequency", F, "1"), Port.in("phase", F, "0")), out(F),
                in -> one("(0.5 + 0.5 * sin(" + in[0] + " * " + in[1] + " + " + in[2] + "))"),
                (in, out, env) -> out[0][0] = 0.5 + 0.5 * Math.sin(in[0][0] * in[1][0] + in[2][0]));
        def("noise", "pattern", ins(Port.in("uv", V2, "@uv"), Port.in("scale", F, "16")), out(F), in -> one("ms_g_hash(floor(" + in[0] + " * " + in[1] + "))"),
                (in, out, env) -> out[0][0] = hash(Math.floor(in[0][0] * in[1][0]), Math.floor(in[0][1] * in[1][0])));
        def("smooth_noise", "pattern", ins(Port.in("uv", V2, "@uv"), Port.in("scale", F, "6")), out(F), in -> one("ms_g_noise(" + in[0] + " * " + in[1] + ")"),
                (in, out, env) -> out[0][0] = noise(in[0][0] * in[1][0], in[0][1] * in[1][0]));
        def("checker", "pattern", ins(Port.in("uv", V2, "@uv"), Port.in("scale", F, "8")), out(F),
                in -> one("mod(floor(" + in[0] + ".x * " + in[1] + ") + floor(" + in[0] + ".y * " + in[1] + "), 2.0)"),
                (in, out, env) -> out[0][0] = Math.floorMod((long) (Math.floor(in[0][0] * in[1][0]) + Math.floor(in[0][1] * in[1][0])), 2L));
        def("stripes", "pattern", ins(Port.in("uv", V2, "@uv"), Port.in("count", F, "6"), Port.in("speed", F, "0.5")), out(F),
                in -> one("step(0.5, fract((" + in[0] + ".x + " + in[0] + ".y) * " + in[1] + " + time * " + in[2] + "))"),
                (in, out, env) -> out[0][0] = fract((in[0][0] + in[0][1]) * in[1][0] + env.time * in[2][0]) < 0.5 ? 0 : 1);
        def("gradient", "pattern", ins(Port.in("uv", V2, "@uv"), Port.in("angle", F, "0")), out(F),
                in -> one("clamp(dot(" + in[0] + " - 0.5, vec2(cos(" + in[1] + "), sin(" + in[1] + "))) + 0.5, 0.0, 1.0)"),
                (in, out, env) -> out[0][0] = clamp((in[0][0] - 0.5) * Math.cos(in[1][0]) + (in[0][1] - 0.5) * Math.sin(in[1][0]) + 0.5, 0, 1));
        def("radial", "pattern", ins(Port.in("uv", V2, "@uv"), Port.in("radius", F, "0.5")), out(F),
                in -> one("clamp(1.0 - distance(" + in[0] + ", vec2(0.5)) / " + in[1] + ", 0.0, 1.0)"),
                (in, out, env) -> out[0][0] = clamp(1 - Math.hypot(in[0][0] - 0.5, in[0][1] - 0.5) / in[1][0], 0, 1));

        // ---------------------------------------------------------------- output
        def("output", "output", ins(Port.in("color", V4, "@color")), List.of(), in -> new String[0], (in, out, env) -> {
        });
    }

    /** Helper functions the generated code may call, behind a guard so several graphs can share one shader. */
    static final String HELPERS = """
            #ifndef MS_GRAPH_HELPERS
            #define MS_GRAPH_HELPERS
            float ms_g_hash(vec2 p) {
                p = fract(p * vec2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return fract(p.x * p.y);
            }

            float ms_g_noise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                vec2 u = f * f * (3.0 - 2.0 * f);
                float a = ms_g_hash(i);
                float b = ms_g_hash(i + vec2(1.0, 0.0));
                float c = ms_g_hash(i + vec2(0.0, 1.0));
                float d = ms_g_hash(i + vec2(1.0, 1.0));
                return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
            }

            vec3 ms_g_hue(vec3 c, float a) {
                const vec3 k = vec3(0.57735026919);
                float ca = cos(a);
                return c * ca + cross(k, c) * sin(a) + k * dot(k, c) * (1.0 - ca);
            }

            vec2 ms_g_rotate(vec2 uv, float a) {
                vec2 d = uv - 0.5;
                float s = sin(a);
                float c = cos(a);
                return vec2(d.x * c - d.y * s, d.x * s + d.y * c) + 0.5;
            }
            #endif
            """;

    // ------------------------------------------------------------------ graph helpers

    public static Def def(ShaderNode n) {
        return DEFS.get(n.type);
    }

    public static ShaderNode create(String type, String id, double x, double y) {
        ShaderNode n = new ShaderNode();
        n.type = type;
        n.id = id;
        n.x = x;
        n.y = y;
        return n;
    }

    /** A new graph: input colour straight to the output. */
    public static List<ShaderNode> starter() {
        List<ShaderNode> r = new ArrayList<>();
        ShaderNode in = create("input_color", "n1", 40, 120);
        ShaderNode out = create("output", "out", 520, 120);
        out.links.put("color", "n1:out");
        r.add(in);
        r.add(out);
        return r;
    }

    public static ShaderNode output(List<ShaderNode> nodes) {
        for (ShaderNode n : nodes) if (n.type.equals("output")) return n;
        return null;
    }

    public static ShaderNode byId(List<ShaderNode> nodes, String id) {
        for (ShaderNode n : nodes) if (n.id.equals(id)) return n;
        return null;
    }

    /** Nodes the output depends on, inputs first; null when the links form a loop. */
    public static List<ShaderNode> order(List<ShaderNode> nodes) {
        ShaderNode out = output(nodes);
        List<ShaderNode> r = new ArrayList<>();
        if (out == null) return r;
        Set<String> done = new HashSet<>(), active = new HashSet<>();
        return visit(nodes, out, done, active, r) ? r : null;
    }

    private static boolean visit(List<ShaderNode> nodes, ShaderNode n, Set<String> done, Set<String> active, List<ShaderNode> r) {
        if (done.contains(n.id)) return true;
        if (!active.add(n.id)) return false;
        Def d = def(n);
        if (d != null) {
            for (Port p : d.ins()) {
                String link = n.links.get(p.key());
                ShaderNode src = link == null ? null : byId(nodes, link.substring(0, link.indexOf(':') < 0 ? link.length() : link.indexOf(':')));
                if (src != null && !visit(nodes, src, done, active, r)) return false;
            }
        }
        active.remove(n.id);
        done.add(n.id);
        r.add(n);
        return true;
    }

    /** Whether linking from -> to would make a loop. */
    public static boolean wouldLoop(List<ShaderNode> nodes, ShaderNode from, ShaderNode to) {
        if (from == to) return true;
        // a loop exists if "to" is already upstream of "from"
        List<ShaderNode> stack = new ArrayList<>(List.of(from));
        Set<String> seen = new HashSet<>();
        while (!stack.isEmpty()) {
            ShaderNode n = stack.remove(stack.size() - 1);
            if (n == to) return true;
            if (!seen.add(n.id)) continue;
            for (String link : n.links.values()) {
                ShaderNode src = byId(nodes, link.contains(":") ? link.substring(0, link.indexOf(':')) : link);
                if (src != null) stack.add(src);
            }
        }
        return false;
    }

    private static Port outPort(Def d, String key) {
        for (Port p : d.outs()) if (p.key().equals(key)) return p;
        return d.outs().isEmpty() ? null : d.outs().get(0);
    }

    public static boolean uses(List<ShaderNode> nodes, String type) {
        List<ShaderNode> o = order(nodes);
        if (o == null) return false;
        for (ShaderNode n : o) if (n.type.equals(type)) return true;
        return false;
    }

    /** Whether the graph reads time, directly or through a node that animates. */
    public static boolean usesTime(List<ShaderNode> nodes) {
        List<ShaderNode> o = order(nodes);
        if (o == null) return false;
        for (ShaderNode n : o) {
            if (List.of("time", "pulse", "uv_scroll", "uv_wave", "stripes").contains(n.type)) return true;
            Def d = def(n);
            if (d != null) for (Port p : d.ins()) if (!n.links.containsKey(p.key()) && "@time".equals(value(n, p))) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ values

    /** The value of an unconnected input (its own setting or the default). */
    public static String value(ShaderNode n, Port p) {
        String v = n.values.get(p.key());
        return v == null || v.isBlank() ? p.def() : v.trim();
    }

    /** Parses a value into four numbers in the port's type (vectors pad like GLSL constructors). */
    public static double[] parse(String v, VT type, Env env) {
        double[] r = new double[4];
        if (v.startsWith("@")) {
            switch (v) {
                case "@color" -> System.arraycopy(env.color, 0, r, 0, 4);
                case "@uv" -> {
                    r[0] = env.u;
                    r[1] = env.v;
                }
                case "@time" -> r[0] = env.time;
                case "@screen" -> {
                    r[0] = env.sx;
                    r[1] = env.sy;
                }
                default -> {
                }
            }
            VT from = switch (v) {
                case "@color" -> VT.V4;
                case "@uv", "@screen" -> VT.V2;
                default -> VT.F;
            };
            double[] c = new double[4];
            cast(r, from, type, c);
            return c;
        }
        if (v.startsWith("#")) {
            int rgb = parseHex(v);
            double[] c = {(rgb >> 16 & 255) / 255.0, (rgb >> 8 & 255) / 255.0, (rgb & 255) / 255.0, 1};
            double[] o = new double[4];
            cast(c, VT.V4, type, o);
            return o;
        }
        String[] parts = v.split("[,\\s]+");
        int n = 0;
        for (String part : parts) {
            if (part.isBlank() || n >= 4) continue;
            try {
                r[n++] = Double.parseDouble(part);
            } catch (NumberFormatException ignored) {
                n++;
            }
        }
        if (n <= 1) {
            // a single number fills every component, like vec4(x) in GLSL (adding 0 or multiplying by 1 keeps alpha)
            double x = r[0];
            return new double[] {x, x, x, x};
        }
        if (type == VT.V4 && n < 4) r[3] = 1;
        return r;
    }

    private static int parseHex(String v) {
        try {
            String h = v.substring(1);
            if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
            return Integer.parseInt(h.length() > 6 ? h.substring(h.length() - 6) : h, 16);
        } catch (RuntimeException e) {
            return 0xFFFFFF;
        }
    }

    static String num(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) d = 0;
        String s = String.format(Locale.ROOT, "%.6f", d).replaceAll("0+$", "");
        return s.endsWith(".") ? s + "0" : s;
    }

    /** GLSL literal of an unconnected input. */
    static String literal(String v, VT type) {
        switch (v) {
            case "@color" -> {
                return cast("color", VT.V4, type);
            }
            case "@uv" -> {
                return cast("uv", VT.V2, type);
            }
            case "@time" -> {
                return cast("time", VT.F, type);
            }
            case "@screen" -> {
                return cast("screen", VT.V2, type);
            }
            default -> {
            }
        }
        double[] c = parse(v, type, new Env());
        return switch (type) {
            case F -> num(c[0]);
            case V2 -> "vec2(" + num(c[0]) + ", " + num(c[1]) + ")";
            case V3 -> "vec3(" + num(c[0]) + ", " + num(c[1]) + ", " + num(c[2]) + ")";
            case V4 -> "vec4(" + num(c[0]) + ", " + num(c[1]) + ", " + num(c[2]) + ", " + num(c[3]) + ")";
        };
    }

    /** GLSL conversion between value types (a number becomes a grey, opaque colour). */
    static String cast(String e, VT from, VT to) {
        if (from == to) return e;
        return switch (to) {
            case F -> e + ".x";
            case V2 -> from == VT.F ? "vec2(" + e + ")" : e + ".xy";
            case V3 -> from == VT.F ? "vec3(" + e + ")" : from == VT.V2 ? "vec3(" + e + ", 0.0)" : e + ".xyz";
            case V4 -> switch (from) {
                case F -> "vec4(vec3(" + e + "), 1.0)";
                case V2 -> "vec4(" + e + ", 0.0, 1.0)";
                default -> "vec4(" + e + ", 1.0)";
            };
        };
    }

    /** The same conversion on four numbers. */
    static void cast(double[] v, VT from, VT to, double[] out) {
        if (from == to) {
            System.arraycopy(v, 0, out, 0, 4);
            return;
        }
        switch (from) {
            case F -> {
                out[0] = out[1] = out[2] = v[0];
                out[3] = to == VT.V4 ? 1 : v[0];
            }
            case V2 -> {
                out[0] = v[0];
                out[1] = v[1];
                out[2] = 0;
                out[3] = to == VT.V4 ? 1 : 0;
            }
            case V3 -> {
                out[0] = v[0];
                out[1] = v[1];
                out[2] = v[2];
                out[3] = to == VT.V4 ? 1 : 0;
            }
            default -> System.arraycopy(v, 0, out, 0, 4);
        }
    }

    // ------------------------------------------------------------------ GLSL

    /** The graph as a GLSL function with the given name (helpers included before it). */
    public static String function(List<ShaderNode> nodes, String name) {
        StringBuilder b = new StringBuilder();
        b.append("// Generated by ModStudio from a node graph.\n").append(HELPERS).append('\n');
        b.append("vec4 ").append(name).append("(vec4 color, vec2 uv, float time, vec2 screen) {\n");
        List<ShaderNode> order = order(nodes);
        String result = "color";
        if (order != null) {
            Map<String, String> vars = new HashMap<>();
            int i = 0;
            for (ShaderNode n : order) {
                Def d = def(n);
                if (d == null) continue;
                String[] ins = new String[d.ins().size()];
                for (int k = 0; k < ins.length; k++) {
                    Port p = d.ins().get(k);
                    String link = n.links.get(p.key());
                    if (link != null && vars.containsKey(link)) ins[k] = cast(vars.get(link), outType(nodes, link), p.type());
                    else ins[k] = literal(value(n, p), p.type());
                }
                if (n.type.equals("output")) {
                    result = ins[0];
                    continue;
                }
                String[] exprs = d.glsl().expr(ins);
                for (int k = 0; k < d.outs().size(); k++) {
                    Port o = d.outs().get(k);
                    String var = "g" + i + "_" + o.key();
                    b.append("    ").append(o.type().glsl).append(' ').append(var).append(" = ").append(exprs[k]).append(";\n");
                    vars.put(n.id + ":" + o.key(), var);
                }
                i++;
            }
        }
        b.append("    return ").append(result).append(";\n}\n");
        return b.toString();
    }

    private static VT outType(List<ShaderNode> nodes, String link) {
        int c = link.indexOf(':');
        ShaderNode n = byId(nodes, c < 0 ? link : link.substring(0, c));
        Def d = n == null ? null : def(n);
        Port p = d == null ? null : outPort(d, c < 0 ? "out" : link.substring(c + 1));
        return p == null ? VT.V4 : p.type();
    }

    private static final Pattern FRAG_COLOR = Pattern.compile("fragColor\\s*=\\s*([^;]+);");
    private static final Pattern MAIN = Pattern.compile("(?m)^\\s*void\\s+main\\s*\\(");

    /** Whether the original source has a final colour the graph can be applied to. */
    public static boolean patchable(String src) {
        return src != null && MAIN.matcher(src).find() && FRAG_COLOR.matcher(src).find();
    }

    /** Name of the function an include file made from a graph defines. */
    public static String includeFunction(Shader s) {
        return "ms_" + Ids.toId(s.program.isBlank() ? s.name : s.program.trim());
    }

    /**
     * The fragment source to export. Hand-written shaders are exported as they are. A core program built from nodes is
     * its original source with the graph applied to the last colour written to fragColor; an include is the graph as a
     * function.
     */
    public static String fragment(Shader s) {
        if (!s.graph()) return s.fragment;
        if (s.kind.equals("include")) {
            String fn = includeFunction(s);
            return "// Import it, then call " + fn + "(color, uv, time, screen). Uses Sampler0 when the graph samples the texture.\n"
                    + function(s.nodes, fn);
        }
        String src = s.fragment;
        if (!patchable(src)) return src;
        Matcher main = MAIN.matcher(src);
        main.find();
        int at = main.start();
        String head = src.substring(0, at), body = src.substring(at);
        Matcher m = FRAG_COLOR.matcher(body);
        int start = -1, end = -1;
        String expr = null;
        while (m.find()) {
            start = m.start();
            end = m.end();
            expr = m.group(1).trim();
        }
        String uv = Pattern.compile("\\bin\\s+vec2\\s+texCoord0\\b").matcher(src).find() ? "texCoord0" : "vec2(0.0)";
        String time = src.contains("GameTime") || src.contains("globals.glsl") ? "GameTime * 1200.0" : "0.0";
        String call = "fragColor = ms_graph(" + expr + ", " + uv + ", " + time + ", gl_FragCoord.xy);";
        return head + "\n" + function(s.nodes, "ms_graph") + "\n" + body.substring(0, start) + call + body.substring(end);
    }

    /** Problems of a graph as i18n keys. */
    public static List<String> check(Shader s) {
        List<String> r = new ArrayList<>();
        if (!s.graph()) return r;
        if (output(s.nodes) == null) r.add("shader.graph_no_output");
        else if (order(s.nodes) == null) r.add("shader.graph_loop");
        if (s.kind.equals("core")) {
            if (s.fragment.isBlank()) r.add("shader.graph_no_base");
            else if (!patchable(s.fragment)) r.add("shader.graph_no_frag_color");
            else {
                if (uses(s.nodes, "texture") && !s.fragment.contains("Sampler0")) r.add("shader.graph_no_sampler");
                if (usesTime(s.nodes) && !s.fragment.contains("GameTime") && !s.fragment.contains("globals.glsl")) r.add("shader.graph_no_time");
            }
        }
        return r;
    }

    // ------------------------------------------------------------------ CPU evaluation for the preview

    /** A graph prepared for the preview: nodes in order, with buffers, so drawing a pixel allocates nothing. */
    public static final class Program {
        private record Step(Def def, int[] src, VT[] srcType, double[][] constants, double[][] ins, double[][] outs, String[] defaults) {}

        private final List<Step> steps = new ArrayList<>();
        private int result = -1;
        private VT resultType = VT.V4;
        private String resultDefault = "@color";
        private final List<double[][]> outsByStep = new ArrayList<>();
        private final double[] tmp = new double[4];

        public Program(List<ShaderNode> nodes) {
            List<ShaderNode> order = order(nodes);
            if (order == null) return;
            Map<String, int[]> where = new HashMap<>();
            Map<String, VT> types = new HashMap<>();
            for (ShaderNode n : order) {
                Def d = def(n);
                if (d == null) continue;
                int[] src = new int[d.ins().size() * 2];
                VT[] srcType = new VT[d.ins().size()];
                double[][] constants = new double[d.ins().size()][];
                String[] defaults = new String[d.ins().size()];
                for (int k = 0; k < d.ins().size(); k++) {
                    Port p = d.ins().get(k);
                    String link = n.links.get(p.key());
                    int[] w = link == null ? null : where.get(link);
                    if (w != null) {
                        src[k * 2] = w[0];
                        src[k * 2 + 1] = w[1];
                        srcType[k] = types.get(link);
                    } else {
                        src[k * 2] = -1;
                        String v = value(n, p);
                        defaults[k] = v.startsWith("@") ? v : null;
                        constants[k] = v.startsWith("@") ? null : parse(v, p.type(), new Env());
                    }
                }
                if (n.type.equals("output")) {
                    if (src[0] >= 0) {
                        result = src[0] * 8 + src[1];
                        resultType = srcType[0];
                    } else {
                        resultDefault = defaults[0] != null ? defaults[0] : "@color";
                    }
                    continue;
                }
                double[][] outs = new double[Math.max(1, d.outs().size())][4];
                Step st = new Step(d, src, srcType, constants, new double[d.ins().size()][4], outs, defaults);
                int idx = steps.size();
                steps.add(st);
                outsByStep.add(outs);
                for (int k = 0; k < d.outs().size(); k++) {
                    where.put(n.id + ":" + d.outs().get(k).key(), new int[] {idx, k});
                    types.put(n.id + ":" + d.outs().get(k).key(), d.outs().get(k).type());
                }
            }
        }

        /** Runs the graph for one pixel and writes the colour (RGBA, unclamped) into out. */
        public void run(Env env, double[] out) {
            for (Step st : steps) {
                for (int k = 0; k < st.ins.length; k++) {
                    int s = st.src[k * 2];
                    VT to = st.def.ins().get(k).type();
                    if (s >= 0) cast(outsByStep.get(s)[st.src[k * 2 + 1]], st.srcType[k], to, st.ins[k]);
                    else if (st.constants[k] != null) System.arraycopy(st.constants[k], 0, st.ins[k], 0, 4);
                    else {
                        double[] v = parse(st.defaults[k], to, env);
                        System.arraycopy(v, 0, st.ins[k], 0, 4);
                    }
                }
                st.def.eval().run(st.ins, st.outs, env);
            }
            if (result >= 0) cast(outsByStep.get(result / 8)[result % 8], resultType, VT.V4, out);
            else {
                double[] v = parse(resultDefault, VT.V4, env);
                System.arraycopy(v, 0, out, 0, 4);
            }
            for (int i = 0; i < 4; i++) {
                if (Double.isNaN(out[i])) out[i] = 0;
            }
            System.arraycopy(out, 0, tmp, 0, 4);
        }
    }
}
