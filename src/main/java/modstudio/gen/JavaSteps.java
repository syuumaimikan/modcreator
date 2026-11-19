package modstudio.gen;

import modstudio.core.Element;
import modstudio.core.Elements.*;
import modstudio.core.Ids;
import modstudio.core.ModElements;
import modstudio.core.Project;
import modstudio.core.Target;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collects inline {@code java_code} / {@code java_call} procedure steps and {@link CodeFile} lifecycle hooks
 * so any Java API, custom event, Command, Tick handler, or third-party library integrates cleanly on all targets.
 */
public final class JavaSteps {
    public record Entry(String id, Step step) {}

    public record HookCall(String fqcn, String method, boolean paperListener) {}

    private static final Pattern PKG_PAT = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z0-9_.]+)\\s*;?");
    private static final Pattern TYPE_PAT = Pattern.compile("(?m)^\\s*(?:public\\s+|private\\s+|protected\\s+|internal\\s+|open\\s+|abstract\\s+|sealed\\s+|final\\s+|data\\s+|case\\s+|non-sealed\\s+)*(?:class|object|trait|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern OBJECT_PAT = Pattern.compile("(?m)^\\s*(?:public\\s+|internal\\s+|private\\s+)?object\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private final List<Entry> entries = new ArrayList<>();
    private final IdentityHashMap<Step, String> ids = new IdentityHashMap<>();

    private JavaSteps() {}

    public List<Entry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public String id(Step s) {
        return ids.get(s);
    }

    public static JavaSteps collect(GenContext ctx) {
        JavaSteps js = new JavaSteps();
        for (Element el : ctx.p.elements) {
            modstudio.core.StepWalker.walkElement(el, s -> {
                if ("java_code".equals(s.kind) || "java_call".equals(s.kind)) {
                    String id = "j" + js.entries.size();
                    js.ids.put(s, id);
                    js.entries.add(new Entry(id, s));
                }
            });
        }
        return js;
    }

    public static boolean hasAny(GenContext ctx) {
        return !collect(ctx).isEmpty();
    }

    /** Matching CodeFile JVM lifecycle hooks (Java, Kotlin, Scala, Groovy) for the given hook kind. */
    public static List<HookCall> hooks(GenContext ctx, String hookKind) {
        List<HookCall> out = new ArrayList<>();
        for (CodeFile cf : ctx.all(CodeFile.class)) {
            if (!Scaffold.matchesTarget(cf.target, ctx.target)) continue;
            if (cf.path == null || !cf.isJvmLanguage()) continue;
            if (!hookKind.equals(cf.hook)) continue;
            String resolved = resolvePlaceholders(cf.content, ctx);
            String fqcn = extractFqcn(cf.path, resolved, ctx);
            if (fqcn.isBlank()) continue;
            String lang = cf.resolvedLanguage();
            boolean hasObj = OBJECT_PAT.matcher(resolved).find();
            boolean hasType = TYPE_PAT.matcher(resolved).find();
            boolean listener = resolved.contains("implements Listener")
                    || resolved.contains(": Listener")
                    || resolved.contains("extends Listener")
                    || resolved.contains("org.bukkit.event.Listener");
            if ("kotlin".equals(lang)) {
                if (!hasType && resolved.contains("fun ") && !fqcn.endsWith("Kt")) {
                    fqcn = fqcn + "Kt";
                } else if (hasObj && (!"event_bus".equals(hookKind) || listener || !resolved.contains("@JvmStatic"))) {
                    fqcn = fqcn + ".INSTANCE";
                }
            } else if ("scala".equals(lang) && hasObj && (!"event_bus".equals(hookKind) || listener)) {
                fqcn = fqcn + "$.MODULE$";
            }
            String m = (cf.hookMethod == null || cf.hookMethod.isBlank()) ? "init" : cf.hookMethod.trim();
            out.add(new HookCall(fqcn, m, listener));
        }
        return out;
    }

    public static String resolvePlaceholders(String text, GenContext ctx) {
        if (text == null) return "";
        return text.replace("{package_name}", ctx.pkg).replace("{modid}", ctx.ns).replace("{main_class}", ctx.mainClass);
    }

    /** Infers the Java FQCN from source text (package + type name), or falls back to src/main/java path. */
    public static String extractFqcn(String rawPath, String resolvedSource, GenContext ctx) {
        String pkg = "";
        String cls = "";
        if (resolvedSource != null) {
            Matcher mp = PKG_PAT.matcher(resolvedSource);
            if (mp.find()) pkg = mp.group(1);
            Matcher mt = TYPE_PAT.matcher(resolvedSource);
            if (mt.find()) cls = mt.group(1);
        }
        if (cls.isEmpty() && rawPath != null) {
            String p = rawPath.replace("{package}", ctx.pkgPath).replace("{modid}", ctx.ns).replace('\\', '/');
            for (String root : List.of("src/main/java/", "src/main/kotlin/", "src/main/scala/", "src/main/groovy/")) {
                if (p.startsWith(root)) {
                    p = p.substring(root.length());
                    break;
                }
            }
            for (String ext : List.of(".java", ".kt", ".kts", ".scala", ".sc", ".groovy")) {
                if (p.endsWith(ext)) {
                    p = p.substring(0, p.length() - ext.length());
                    break;
                }
            }
            int slash = p.lastIndexOf('/');
            if (slash > 0 && pkg.isEmpty()) pkg = p.substring(0, slash).replace('/', '.');
            cls = slash >= 0 ? p.substring(slash + 1) : p;
        }
        if (cls.isEmpty()) return "";
        return pkg.isEmpty() ? cls : pkg + "." + cls;
    }

    /** Writes ModJavaHandlers.java for mod loaders so java_code/java_call steps execute in real Java. */
    public static void writeModHandlers(GenContext ctx) {
        JavaSteps js = collect(ctx);
        if (js.isEmpty()) return;
        Src s = new Src(ctx.pkg);
        s.imp("net.minecraft.commands.CommandSourceStack",
                "net.minecraft.core.BlockPos",
                "net.minecraft.network.chat.Component",
                "net.minecraft.server.MinecraftServer",
                "net.minecraft.server.level.ServerLevel",
                "net.minecraft.server.level.ServerPlayer",
                "net.minecraft.world.entity.Entity",
                "net.minecraft.world.item.ItemStack",
                "net.minecraft.world.level.Level",
                "org.slf4j.Logger",
                "com.mojang.logging.LogUtils");
        collectStepImportsAndFiles(ctx, js, s);
        s.l("/** Executes inline Java steps (java_code / java_call) invoked from procedures and commands. */");
        s.l("public final class ModJavaHandlers {");
        s.l("private static final Logger LOGGER = LogUtils.getLogger();");
        s.blank();
        s.l("private ModJavaHandlers() {").l("}");
        s.blank();
        s.l("public static void run(CommandSourceStack source, String id) {");
        s.l("MinecraftServer server = source.getServer();");
        s.l("ServerLevel level = source.getLevel();");
        s.l("Entity entity = source.getEntity();");
        s.l("ServerPlayer player = entity instanceof ServerPlayer sp ? sp : null;");
        s.l("BlockPos pos = BlockPos.containing(source.getPosition());");
        s.l("try {");
        s.l("switch (id) {");
        for (Entry e : js.entries()) {
            s.l("case " + Src.str(e.id()) + " -> " + e.id() + "(source, server, level, entity, player, pos);");
        }
        s.l("default -> {}");
        s.l("}");
        s.l("} catch (Throwable ex) {");
        s.l("LOGGER.error(\"[" + ctx.ns + "] Java step {} failed\", id, ex);");
        s.l("}");
        s.l("}");
        for (Entry e : js.entries()) {
            s.blank();
            s.l("private static void " + e.id() + "(CommandSourceStack source, MinecraftServer server, ServerLevel level, Entity entity, ServerPlayer player, BlockPos pos) throws Exception {");
            emitStepBody(ctx, e, s);
            s.l("}");
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModJavaHandlers.java", s.toString());
    }

    /** Writes JavaHandlers.java for Paper/Folia plugins so java_code/java_call steps run inside Paper commands/events. */
    public static void writePaperHandlers(GenContext ctx, String main) {
        JavaSteps js = collect(ctx);
        if (js.isEmpty()) return;
        Src s = new Src(ctx.pkg);
        s.imp("org.bukkit.Bukkit",
                "org.bukkit.Location",
                "org.bukkit.World",
                "org.bukkit.entity.Entity",
                "org.bukkit.entity.Player",
                "org.bukkit.plugin.java.JavaPlugin");
        collectStepImportsAndFiles(ctx, js, s);
        s.l("public final class JavaHandlers {");
        s.l("private JavaHandlers() {").l("}");
        s.blank();
        s.l("public static void run(JavaPlugin plugin, Player player, String id) {");
        s.l("Entity entity = player;");
        s.l("Location pos = player != null ? player.getLocation() : null;");
        s.l("World level = pos != null ? pos.getWorld() : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));");
        s.l("try {");
        s.l("switch (id) {");
        for (Entry e : js.entries()) {
            s.l("case " + Src.str(e.id()) + " -> " + e.id() + "(plugin, level, entity, player, pos);");
        }
        s.l("default -> {}");
        s.l("}");
        s.l("} catch (Throwable ex) {");
        s.l("plugin.getLogger().severe(\"Java step \" + id + \" failed: \" + ex);");
        s.l("}");
        s.l("}");
        for (Entry e : js.entries()) {
            s.blank();
            s.l("private static void " + e.id() + "(JavaPlugin plugin, World level, Entity entity, Player player, Location pos) throws Exception {");
            emitStepBody(ctx, e, s);
            s.l("}");
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "JavaHandlers.java", s.toString());
    }

    private static void collectStepImportsAndFiles(GenContext ctx, JavaSteps js, Src s) {
        for (Entry e : js.entries()) {
            String imps = e.step().get("imports");
            if (!imps.isBlank()) {
                for (String token : imps.split("[,;\\r\\n]+")) {
                    String imp = resolvePlaceholders(token.trim(), ctx);
                    if (!imp.isBlank()) s.imp(imp.startsWith("import ") ? imp.substring(7).replace(";", "").trim() : imp);
                }
            }
            if ("java_call".equals(e.step().kind)) {
                String method = e.step().get("method").trim();
                int dot = method.lastIndexOf('.');
                if (dot > 0) {
                    String owner = method.substring(0, dot);
                    if (!owner.contains(".")) {
                        for (CodeFile cf : ctx.all(CodeFile.class)) {
                            if (!cf.isJvmLanguage() || !Target.matchesSelector(cf.target, ctx.target)) continue;
                            String fqcn = extractFqcn(cf.path, resolvePlaceholders(cf.content, ctx), ctx);
                            if (fqcn.endsWith("." + owner) || fqcn.equals(owner)) {
                                s.imp(fqcn);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private static void emitStepBody(GenContext ctx, Entry e, Src s) {
        if ("java_call".equals(e.step().kind)) {
            String method = resolvePlaceholders(e.step().get("method").trim(), ctx);
            String args = resolvePlaceholders(e.step().get("args").trim(), ctx);
            if (!method.isBlank()) {
                if (method.contains("(")) s.l(method + (method.endsWith(";") ? "" : ";"));
                else s.l(method + "(" + args + ");");
            }
        } else {
            String code = resolvePlaceholders(e.step().get("code"), ctx);
            for (String line : code.split("\\R")) {
                if (!line.isBlank()) s.l(line);
            }
        }
    }
}

