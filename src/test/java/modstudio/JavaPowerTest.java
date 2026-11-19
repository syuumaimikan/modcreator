package modstudio;

import modstudio.core.*;
import modstudio.core.Elements.*;
import modstudio.core.ModElements.*;
import modstudio.gen.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies full Java extensibility (hooks, inline Java steps, raw Mixins, Maven/libraries, AT/AW) and bytecode Mixin import. */
class JavaPowerTest {

    private static String text(GenContext ctx, String path) {
        byte[] b = ctx.files.get(path);
        assertNotNull(b, "Missing " + path + " for " + ctx.target + "/" + ctx.v.id);
        return new String(b, StandardCharsets.UTF_8);
    }

    @Test
    void fullJavaHooksAndProceduresAcrossAllTargets() {
        Project p = new Project();
        p.meta.name = "JavaPower";
        p.meta.modId = "javapower";
        p.meta.packageName = "com.example.javapower";
        p.meta.mcVersion = "1.21.1";
        p.meta.mcVersions = new ArrayList<>(List.of("1.21.1"));
        p.meta.mavenRepos.add("https://maven.blamejared.com");
        p.meta.jvmArgs = "-XX:+UseG1GC -Xmx4G";
        p.meta.customGradle = "// custom gradle verification block\next.customFlag = true";

        GradleDep dep1 = new GradleDep();
        dep1.scope = "implementation";
        dep1.target = "all";
        dep1.coordinate = "com.google.code.gson:gson:2.11.0";
        GradleDep dep2 = new GradleDep();
        dep2.scope = "jarJar";
        dep2.target = "mods";
        dep2.coordinate = "org.apache.commons:commons-lang3:3.14.0";
        p.meta.libraries.addAll(List.of(dep1, dep2));

        // Raw Mixin + disabled external Mixin
        MixinSpec rawMx = new MixinSpec();
        rawMx.name = "player_boost";
        rawMx.side = "common";
        rawMx.rawMode = true;
        rawMx.rawCode = """
                package {package_name}.mixin;

                import net.minecraft.world.entity.player.Player;
                import org.spongepowered.asm.mixin.Mixin;
                import org.spongepowered.asm.mixin.Unique;
                import org.spongepowered.asm.mixin.injection.At;
                import org.spongepowered.asm.mixin.injection.Inject;
                import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

                @Mixin(Player.class)
                public abstract class {mixin_class} {
                    @Unique
                    private int javapower$ticks = 0;

                    @Inject(method = "tick", at = @At("HEAD"))
                    private void javapower$onTick(CallbackInfo ci) {
                        javapower$ticks++;
                    }
                }
                """;
        MixinSpec disabledMx = new MixinSpec();
        disabledMx.name = "external_skip";
        disabledMx.enabled = false;
        disabledMx.rawMode = true;
        disabledMx.rawCode = "// disabled external mixin";

        // Access Transformer & Access Widener
        CodeFile at = new CodeFile();
        at.name = "at_cfg";
        at.target = "mods";
        at.path = "src/main/resources/META-INF/accesstransformer.cfg";
        at.content = "# AT marker\n";

        CodeFile aw = new CodeFile();
        aw.name = "aw_cfg";
        aw.target = "fabric";
        aw.path = "src/main/resources/{modid}.accesswidener";
        aw.content = "accessWidener v2 named\n";

        // CodeFiles with every lifecycle hook
        CodeFile common = new CodeFile();
        common.name = "common_boot";
        common.target = "all";
        common.hook = "common_init";
        common.hookMethod = "boot";
        common.path = "src/main/java/{package}/custom/CommonBoot.java";
        common.content = "package {package_name}.custom;\npublic final class CommonBoot {\n    public static void boot() {}\n    public static void helper(String tag) {}\n}\n";

        CodeFile client = new CodeFile();
        client.name = "client_boot";
        client.target = "mods";
        client.hook = "client_init";
        client.hookMethod = "init";
        client.path = "src/main/java/{package}/custom/ClientBoot.java";
        client.content = "package {package_name}.custom;\npublic final class ClientBoot {\n    public static void init() {}\n}\n";

        CodeFile cmdHook = new CodeFile();
        cmdHook.name = "cmd_boot";
        cmdHook.target = "mods";
        cmdHook.hook = "register_commands";
        cmdHook.hookMethod = "reg";
        cmdHook.path = "src/main/java/{package}/custom/CmdBoot.java";
        cmdHook.content = "package {package_name}.custom;\nimport com.mojang.brigadier.CommandDispatcher;\nimport net.minecraft.commands.CommandSourceStack;\npublic final class CmdBoot {\n    public static void reg(CommandDispatcher<CommandSourceStack> d) {}\n}\n";

        CodeFile tickHook = new CodeFile();
        tickHook.name = "tick_boot";
        tickHook.target = "mods";
        tickHook.hook = "server_tick";
        tickHook.hookMethod = "onTick";
        tickHook.path = "src/main/java/{package}/custom/TickBoot.java";
        tickHook.content = "package {package_name}.custom;\nimport net.minecraft.server.MinecraftServer;\npublic final class TickBoot {\n    public static void onTick(MinecraftServer s) {}\n}\n";

        // Procedure with java_code + java_call
        Procedure proc = new Procedure();
        proc.name = "java_action";
        proc.trigger = "player_join";
        Step s1 = Logic.newStep("java_code");
        s1.p.put("imports", "java.util.UUID");
        s1.p.put("code", "UUID u = player != null ? player.getUUID() : null;\nif (u != null && level != null) { level.getServer(); }");
        Step s2 = Logic.newStep("java_call");
        s2.p.put("method", "CommonBoot.helper");
        s2.p.put("args", "\"ok\"");
        proc.steps.addAll(List.of(s1, s2));

        p.elements.addAll(List.of(rawMx, disabledMx, at, aw, common, client, cmdHook, tickHook, proc));
        assertTrue(Validator.validate(p).stream().noneMatch(pr -> pr.severity() == Validator.Severity.ERROR),
                () -> "Unexpected errors: " + Validator.validate(p));

        for (Target t : List.of(Target.NEOFORGE, Target.FORGE, Target.FABRIC, Target.QUILT)) {
            GenContext ctx = Generators.generate(p, t, McVersion.V1_21_1);
            String bg = text(ctx, "build.gradle");
            assertTrue(bg.contains("https://maven.blamejared.com"), t.id);
            assertTrue(bg.contains("com.google.code.gson:gson:2.11.0"), t.id);
            assertTrue(bg.contains("// custom gradle verification block"), t.id);

            assertTrue(ctx.files.has("src/main/java/com/example/javapower/mixin/PlayerBoostMixin.java"), t.id);
            assertFalse(ctx.files.has("src/main/java/com/example/javapower/mixin/ExternalSkipMixin.java"), t.id);
            String mxJson = text(ctx, "src/main/resources/javapower.mixins.json");
            assertTrue(mxJson.contains("PlayerBoostMixin"), t.id);
            assertFalse(mxJson.contains("ExternalSkipMixin"), t.id);

            String handlers = text(ctx, "src/main/java/com/example/javapower/ModJavaHandlers.java");
            assertTrue(handlers.contains("import java.util.UUID;"), t.id);
            assertTrue(handlers.contains("CommonBoot.helper(\"ok\");"), t.id);
            String bridge = text(ctx, "src/main/java/com/example/javapower/ModBridge.java");
            assertTrue(bridge.contains("ModJavaHandlers.run("), t.id);
            String fn = text(ctx, "src/main/resources/data/javapower/function/procedures/java_action.mcfunction");
            assertTrue(fn.contains("javapower_bridge java j0"), t.id);
            assertTrue(fn.contains("javapower_bridge java j1"), t.id);

            assertTrue(text(ctx, "src/main/java/com/example/javapower/ModCommands.java").contains("CmdBoot.reg(dispatcher)"), t.id);
        }

        GenContext nf = Generators.generate(p, Target.NEOFORGE, McVersion.V1_21_1);
        assertTrue(text(nf, "build.gradle").contains("jarJar 'org.apache.commons:commons-lang3:3.14.0'"));
        assertTrue(text(nf, "src/main/java/com/example/javapower/" + nf.mainClass + ".java").contains("CommonBoot.boot()"));
        assertTrue(text(nf, "src/main/java/com/example/javapower/" + nf.mainClass + ".java").contains("ClientBoot.init()"));
        assertTrue(text(nf, "src/main/java/com/example/javapower/ModEvents.java").contains("TickBoot.onTick(event.getServer())"));

        GenContext fg = Generators.generate(p, Target.FORGE, McVersion.V1_21_1);
        assertTrue(text(fg, "build.gradle").contains("accessTransformer = file('src/main/resources/META-INF/accesstransformer.cfg')"));
        assertTrue(text(fg, "build.gradle").contains("sourcesSets/$it.name"));

        GenContext fb = Generators.generate(p, Target.FABRIC, McVersion.V1_21_1);
        assertTrue(text(fb, "build.gradle").contains("accessWidenerPath = file(\"src/main/resources/javapower.accesswidener\")"));
        assertTrue(text(fb, "build.gradle").contains("include 'org.apache.commons:commons-lang3:3.14.0'"));
        assertTrue(text(fb, "src/main/resources/fabric.mod.json").contains("\"accessWidener\": \"javapower.accesswidener\""));
        assertTrue(text(fb, "src/main/java/com/example/javapower/" + fb.mainClass + ".java").contains("CommonBoot.boot()"));
        assertTrue(text(fb, "src/main/java/com/example/javapower/" + fb.mainClass + "Client.java").contains("ClientBoot.init()"));
        assertTrue(text(fb, "src/main/java/com/example/javapower/ModEvents.java").contains("TickBoot.onTick(server)"));

        // Paper & Folia
        GenContext paper = Generators.generate(p, Target.PAPER, McVersion.V1_21_1);
        assertTrue(text(paper, "build.gradle").contains("https://maven.blamejared.com"));
        assertTrue(text(paper, "build.gradle").contains("com.google.code.gson:gson:2.11.0"));
        assertTrue(text(paper, "src/main/java/com/example/javapower/JavaHandlers.java").contains("CommonBoot.helper(\"ok\")"));
        assertTrue(text(paper, "src/main/java/com/example/javapower/Bridge.java").contains("JavaHandlers.run("));
    }

    @Test
    void bytecodeMixinImportResolvesRealTargets(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("testmod-1.21.1.jar");
        byte[] validMixinClass = minimalAnnotatedMixinClass(
                "com/example/testmod/mixin/PlayerTickMixin",
                false,
                "Lnet/minecraft/world/entity/player/Player;",
                "tick");
        byte[] accessorIfaceClass = minimalAnnotatedMixinClass(
                "com/example/testmod/mixin/ArrowAccess",
                true,
                "Lnet/minecraft/world/entity/projectile/AbstractArrow;",
                null);
        byte[] obfMixinClass = minimalAnnotatedMixinClass(
                "com/example/testmod/mixin/LegacyMixin",
                false,
                "Lnet/minecraft/class_1657;",
                "method_5773");

        try (ZipOutputStream zos = new ZipOutputStream(java.nio.file.Files.newOutputStream(jar))) {
            zos.putNextEntry(new ZipEntry("META-INF/neoforge.mods.toml"));
            zos.write("""
                    modLoader="javafml"
                    loaderVersion="[4,)"
                    license="MIT"
                    [[mods]]
                    modId="testmod"
                    version="1.0.0"
                    displayName="TestMod"
                    """.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("testmod.mixins.json"));
            zos.write("""
                    {
                      "required": true,
                      "package": "com.example.testmod.mixin",
                      "mixins": ["PlayerTickMixin", "ArrowAccess", "LegacyMixin"]
                    }
                    """.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("com/example/testmod/mixin/PlayerTickMixin.class"));
            zos.write(validMixinClass);
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("com/example/testmod/mixin/ArrowAccess.class"));
            zos.write(accessorIfaceClass);
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("com/example/testmod/mixin/LegacyMixin.class"));
            zos.write(obfMixinClass);
            zos.closeEntry();
        }

        ProjectImporter.Report rep = ProjectImporter.importArtifact(jar);
        List<MixinSpec> mixins = rep.project().all(MixinSpec.class);
        assertEquals(3, mixins.size());

        MixinSpec playerMx = mixins.stream().filter(m -> m.name.contains("playertick")).findFirst().orElseThrow();
        assertTrue(playerMx.enabled);
        assertTrue(playerMx.rawMode);
        assertEquals("net.minecraft.world.entity.player.Player", playerMx.targetClass);
        assertTrue(playerMx.rawCode.contains("@Mixin(Player.class)"));

        MixinSpec accMx = mixins.stream().filter(m -> m.name.contains("arrowaccess")).findFirst().orElseThrow();
        assertTrue(accMx.enabled);
        assertTrue(accMx.rawCode.contains("public interface {mixin_class}"));
        assertFalse(accMx.rawCode.contains("ArrowAccess.class"));

        MixinSpec legMx = mixins.stream().filter(m -> m.name.contains("legacymixin")).findFirst().orElseThrow();
        assertFalse(legMx.enabled);

        // Generated code skips disabled obfuscated mixin and has 0 validation errors
        assertTrue(Validator.validate(rep.project()).stream().noneMatch(pr -> pr.severity() == Validator.Severity.ERROR));
        GenContext gen = Generators.generate(rep.project(), Target.NEOFORGE, McVersion.V1_21_1);
        assertFalse(gen.files.has("src/main/java/com/example/testmod/mixin/LegacymixinMixin.java"));
        assertTrue(text(gen, "src/main/java/com/example/testmod/mixin/ArrowaccessMixin.java").contains("@Mixin(AbstractArrow.class)"));
    }

    /** Synthesizes a minimal valid JVM .class file with RuntimeInvisibleAnnotations for @Mixin (and optional @Inject). */
    private static byte[] minimalAnnotatedMixinClass(String internalName, boolean isInterface, String targetDesc, String injectMethod) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        List<String> utf8 = new ArrayList<>();
        utf8.add(""); // dummy 0
        int cThis = utf8.size(); utf8.add(internalName);
        int cObj = utf8.size(); utf8.add("java/lang/Object");
        int cRia = utf8.size(); utf8.add("RuntimeInvisibleAnnotations");
        int cMixin = utf8.size(); utf8.add("Lorg/spongepowered/asm/mixin/Mixin;");
        int cVal = utf8.size(); utf8.add("value");
        int cTarget = utf8.size(); utf8.add(targetDesc);
        int cMethodName = utf8.size(); utf8.add("hook");
        int cMethodDesc = utf8.size(); utf8.add("()V");
        int cInject = utf8.size(); utf8.add("Lorg/spongepowered/asm/mixin/injection/Inject;");
        int cMethodKey = utf8.size(); utf8.add("method");
        int cInjectTarget = utf8.size(); utf8.add(injectMethod == null ? "tick" : injectMethod);

        // CP entries: UTF8s 1..N, then Class(this), Class(Object)
        int cpThisCls = utf8.size();
        int cpObjCls = cpThisCls + 1;
        out.writeInt(0xCAFEBABE);
        out.writeShort(0);
        out.writeShort(61);
        out.writeShort(cpObjCls + 1);
        for (int i = 1; i < utf8.size(); i++) {
            out.writeByte(1);
            out.writeUTF(utf8.get(i));
        }
        out.writeByte(7); out.writeShort(cThis);
        out.writeByte(7); out.writeShort(cObj);

        out.writeShort(isInterface ? (0x0200 | 0x0400 | 0x0001) : 0x0401);
        out.writeShort(cpThisCls);
        out.writeShort(cpObjCls);
        out.writeShort(0); // interfaces
        out.writeShort(0); // fields

        if (injectMethod == null) {
            out.writeShort(0);
        } else {
            out.writeShort(1); // 1 method with @Inject
            out.writeShort(0x0002);
            out.writeShort(cMethodName);
            out.writeShort(cMethodDesc);
            out.writeShort(1);
            out.writeShort(cRia);
            out.writeInt(11);
            out.writeShort(1);
            out.writeShort(cInject);
            out.writeShort(1);
            out.writeShort(cMethodKey);
            out.writeByte('s');
            out.writeShort(cInjectTarget);
        }

        // Class RuntimeInvisibleAnnotations: @Mixin(value = { targetDesc })
        out.writeShort(1);
        out.writeShort(cRia);
        out.writeInt(14);
        out.writeShort(1);
        out.writeShort(cMixin);
        out.writeShort(1);
        out.writeShort(cVal);
        out.writeByte('[');
        out.writeShort(1);
        out.writeByte('c');
        out.writeShort(cTarget);
        out.flush();
        return baos.toByteArray();
    }
}

