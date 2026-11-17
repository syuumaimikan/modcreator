package modstudio.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Random;

/**
 * Pictures and metadata for UI themes: enchantment glint patterns drawn in a colour (the game adds the glint onto the
 * item, so dark parts show nothing and bright parts glow) and .mcmeta files of replaced vanilla GUI textures, whose
 * nine-slice borders are scaled to the size of the new picture.
 */
public final class ThemeArt {
    /** Size of generated glint pictures; every pattern repeats seamlessly at this size. */
    public static final int GLINT = 64;

    private ThemeArt() {}

    public static int[] glint(String hex, String pattern) {
        int rgb;
        try {
            rgb = Integer.parseInt(hex.trim().replace("#", ""), 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            rgb = 0x8040CC;
        }
        int n = GLINT;
        double[] sparkle = pattern.equals("sparkles") ? sparkles(n) : null;
        int[] px = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double v = switch (pattern) {
                    case "stripes" -> (x + y) % 16 < 6 ? 1.0 : 0.12;
                    case "sparkles" -> sparkle[y * n + x];
                    case "aurora" -> 0.5 + 0.28 * Math.sin(2 * Math.PI * (2.0 * x / n + 0.5 * Math.sin(2 * Math.PI * y / n)))
                            + 0.22 * Math.sin(2 * Math.PI * (3.0 * y / n + 1.0 * x / n));
                    default -> 0.55 + 0.45 * Math.sin(2 * Math.PI * (x + y) / 32.0) * Math.cos(2 * Math.PI * (x - y) / 64.0);
                };
                v = Math.max(0, Math.min(1, v));
                int r = (int) Math.round((rgb >> 16 & 0xFF) * v), g = (int) Math.round((rgb >> 8 & 0xFF) * v), b = (int) Math.round((rgb & 0xFF) * v);
                px[y * n + x] = 0xFF000000 | r << 16 | g << 8 | b;
            }
        }
        return px;
    }

    /** Small stars at fixed random places, wrapping at the edges. */
    private static double[] sparkles(int n) {
        double[] v = new double[n * n];
        java.util.Arrays.fill(v, 0.06);
        Random r = new Random(7);
        for (int i = 0; i < 46; i++) {
            int cx = r.nextInt(n), cy = r.nextInt(n);
            double peak = 0.55 + r.nextDouble() * 0.45;
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int d = Math.abs(dx) + Math.abs(dy);
                    if (d > 2) continue;
                    double s = d == 0 ? peak : d == 1 ? peak * 0.55 : peak * 0.2;
                    int x = Math.floorMod(cx + dx, n), y = Math.floorMod(cy + dy, n);
                    v[y * n + x] = Math.max(v[y * n + x], s);
                }
            }
        }
        return v;
    }

    /**
     * Metadata for a picture that replaces a vanilla texture: the vanilla GUI scaling with sizes and borders scaled to
     * the new picture, and the animation of an animated GUI sprite. Null when nothing is needed.
     */
    public static JsonObject meta(String vanillaMeta, TextureAsset t, boolean sprite) {
        JsonObject o = new JsonObject();
        if (vanillaMeta != null && !vanillaMeta.isBlank()) {
            try {
                JsonObject v = JsonParser.parseString(vanillaMeta).getAsJsonObject();
                if (v.has("gui")) {
                    JsonObject gui = v.getAsJsonObject("gui").deepCopy();
                    JsonObject sc = gui.has("scaling") ? gui.getAsJsonObject("scaling") : null;
                    if (sc != null && sc.has("width") && sc.has("height")) {
                        int ow = Math.max(1, sc.get("width").getAsInt()), oh = Math.max(1, sc.get("height").getAsInt());
                        int w = t.width, h = t.frameHeight();
                        double fx = w / (double) ow, fy = h / (double) oh;
                        sc.addProperty("width", w);
                        sc.addProperty("height", h);
                        if (sc.has("border")) {
                            JsonElement b = sc.get("border");
                            if (b.isJsonPrimitive()) {
                                sc.addProperty("border", Math.max(1, (int) Math.round(b.getAsInt() * Math.min(fx, fy))));
                            } else if (b.isJsonObject()) {
                                JsonObject bo = b.getAsJsonObject();
                                for (String k : new String[] {"left", "right"}) if (bo.has(k)) bo.addProperty(k, Math.max(0, (int) Math.round(bo.get(k).getAsInt() * fx)));
                                for (String k : new String[] {"top", "bottom"}) if (bo.has(k)) bo.addProperty(k, Math.max(0, (int) Math.round(bo.get(k).getAsInt() * fy)));
                            }
                        }
                    }
                    o.add("gui", gui);
                }
            } catch (RuntimeException ignored) {
                // a broken copy of the vanilla metadata is left out
            }
        }
        if (sprite && t.animated()) {
            JsonObject a = new JsonObject();
            a.addProperty("frametime", Math.max(1, t.frameTime));
            if (t.interpolate) a.addProperty("interpolate", true);
            o.add("animation", a);
        }
        return o.size() == 0 ? null : o;
    }
}
