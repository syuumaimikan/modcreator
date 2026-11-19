package modstudio;

import modstudio.core.*;
import modstudio.core.Elements.*;
import modstudio.core.ModElements.*;
import modstudio.core.Systems.*;
import modstudio.gen.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class GeneratorsMatrixTest {

    private static Project fullProject() {
        Project p = SampleProject.create();
        p.meta.mcVersions = new java.util.ArrayList<>(List.of("1.20.1", "1.21.1", "1.21.9", "1.21.11", "26.1"));
        p.meta.mcVersion = "26.1";
        p.meta.targets = new java.util.ArrayList<>(List.of("forge", "neoforge", "fabric", "quilt", "paper", "folia", "datapack", "resourcepack", "bedrock"));

        ModDependency dep = new ModDependency();
        dep.modId = "architectury";
        dep.version = "*";
        dep.required = false;
        p.meta.dependencies.add(dep);

        String rubyTex = p.textureByName("ruby").id;
        String blockTex = p.textureByName("ruby_block").id;
        String swordTex = p.textureByName("ruby_sword").id;

        Fluid fl = new Fluid();
        fl.name = "ruby_elixir";
        fl.displayName = "Ruby Elixir";
        fl.stillTexture = blockTex;
        fl.flowingTexture = blockTex;
        fl.damagePerTick = 1.0;

        Elements.Block slab = new Elements.Block();
        slab.name = "ruby_slab";
        slab.shape = "slab";
        slab.texture = blockTex;
        slab.waterloggable = true;
        slab.redstoneSignal = 5;

        Elements.Block door = new Elements.Block();
        door.name = "ruby_door";
        door.shape = "door";
        door.texture = blockTex;

        Elements.Item bow = new Elements.Item();
        bow.name = "ruby_bow";
        bow.kind = "bow";
        bow.texture = swordTex;
        bow.durability = 384;

        Elements.Item elytra = new Elements.Item();
        elytra.name = "ruby_wings";
        elytra.kind = "elytra";
        elytra.texture = rubyTex;
        elytra.durability = 432;

        Container ct = new Container();
        ct.name = "gem_kiln";
        ct.displayName = "Gem Kiln";
        ct.block = "rubymod:ruby_block";
        ContainerRecipe cr = new ContainerRecipe();
        cr.input = "rubymod:ruby_ore";
        cr.output = "rubymod:ruby";
        ct.recipes.add(cr);

        CustomEntity mob = new CustomEntity();
        mob.name = "ruby_sentry";
        mob.displayName = "Ruby Sentry";
        mob.naturalSpawn = true;
        mob.texture = blockTex;

        ModConfig cfg = new ModConfig();
        cfg.name = "ruby_cfg";
        ConfigEntry ce = new ConfigEntry();
        ce.key = "bonus_drops"; ce.kind = "bool"; ce.value = "true";
        cfg.entries.add(ce);

        MixinSpec mx = new MixinSpec();
        mx.name = "player_hook";
        mx.targetClass = "net.minecraft.world.entity.player.Player";
        mx.methodTarget = "tick";
        mx.code = "// injected";

        Portal pt = new Portal();
        pt.name = "ruby_gate";
        pt.frameBlock = "rubymod:ruby_block";
        pt.targetDimension = "minecraft:the_nether";

        WorldFeature wf = new WorldFeature();
        wf.name = "ruby_patch";
        wf.featureType = "patch";
        wf.block = "rubymod:ruby_block";

        Profession pf = new Profession();
        pf.name = "jeweler";
        pf.workstationBlock = "rubymod:ruby_block";
        ProfessionTrade tr = new ProfessionTrade();
        tr.result = "rubymod:ruby";
        pf.trades.add(tr);

        ArmorTrim at = new ArmorTrim();
        at.name = "crimson";
        at.generateTemplate = true;
        at.texture = rubyTex;

        BannerPattern bp = new BannerPattern();
        bp.name = "ruby_crest";
        bp.texture = rubyTex;

        CustomRecipeType crt = new CustomRecipeType();
        crt.name = "gem_pressing";

        Shader post = new Shader();
        post.name = "crimson_post";
        post.program = "phosphor";
        post.kind = "post";
        post.mode = "nodes";
        post.nodes.addAll(ShaderNodes.starter());

        CustomScreen scr = new CustomScreen();
        scr.name = "ruby_panel";
        ScreenWidget tf = new ScreenWidget();
        tf.kind = "textfield";
        tf.placeholder = "Filter...";
        scr.widgets.add(tf);
        ScreenWidget sld = new ScreenWidget();
        sld.kind = "slider";
        sld.text = "Level";
        scr.widgets.add(sld);
        ScreenWidget cb = new ScreenWidget();
        cb.kind = "checkbox";
        cb.text = "Active";
        cb.checked = true;
        scr.widgets.add(cb);

        Recipe brew = new Recipe();
        brew.name = "ruby_smith";
        brew.recipeType = "smithing";
        brew.input = "minecraft:diamond_sword";
        brew.addition = "rubymod:ruby";
        brew.template = "minecraft:netherite_upgrade_smithing_template";
        brew.result = "rubymod:ruby_sword";

        Recipe smoke = new Recipe();
        smoke.name = "ruby_smoke";
        smoke.recipeType = "smoking";
        smoke.input = "minecraft:apple";
        smoke.result = "rubymod:ruby_apple";

        Dialog dlg = new Dialog();
        dlg.name = "ruby_notice";
        dlg.title = "Ruby Guide";
        dlg.body = "Welcome!";

        p.elements.addAll(List.of(fl, slab, door, bow, elytra, ct, mob, cfg, mx, pt, wf, pf, at, bp, crt, post, scr, brew, smoke, dlg));
        return p;
    }

    private static String text(GenContext ctx, String path) {
        byte[] b = ctx.files.get(path);
        assertNotNull(b, "Missing generated file: " + path + " in " + ctx.target + "/" + ctx.v.id);
        return new String(b, StandardCharsets.UTF_8);
    }

    @Test
    void generatesAllModTargetsAndModernVersions() {
        Project p = fullProject();

        // Fabric 1.20.1, 1.21.1, 1.21.9, 26.1
        GenContext f1201 = Generators.generate(p, Target.FABRIC, McVersion.V1_20_1);
        assertTrue(f1201.files.has("src/main/java/com/example/rubymod/container/GemKilnBlockEntity.java"));
        assertFalse(text(f1201, "src/main/java/com/example/rubymod/container/GemKilnBlockEntity.java").contains("ValueInput"));
        assertTrue(text(f1201, "src/main/java/com/example/rubymod/ModScreens.java").contains("TEXTFIELD"));
        assertTrue(text(f1201, "src/main/java/com/example/rubymod/ModScreens.java").contains("focusedField"));
        assertFalse(text(f1201, "src/main/java/com/example/rubymod/ModScreens.java").contains("MouseButtonEvent"));

        GenContext f1219 = Generators.generate(p, Target.FABRIC, McVersion.V1_21_9);
        assertTrue(text(f1219, "src/main/java/com/example/rubymod/container/GemKilnBlockEntity.java").contains("ValueInput"));
        assertTrue(text(f1219, "src/main/java/com/example/rubymod/ModScreens.java").contains("MouseButtonEvent"));
        assertTrue(text(f1219, "src/main/java/com/example/rubymod/ModItems.java").contains("spawnEgg("));

        GenContext forge1219 = Generators.generate(p, Target.FORGE, McVersion.V1_21_9);
        assertTrue(text(forge1219, "src/main/resources/pack.mcmeta").contains("\"min_format\""));

        GenContext f26 = Generators.generate(p, Target.FABRIC, McVersion.V26_1);
        String fg = text(f26, "build.gradle");
        assertTrue(fg.contains("JavaVersion.VERSION_25"));
        assertTrue(fg.contains("net.fabricmc.fabric-loom") && fg.contains("implementation "));
        assertFalse(fg.contains("officialMojangMappings"));
        assertTrue(f26.files.has("src/main/resources/rubymod.mixins.json"));
        assertTrue(f26.files.has("src/main/java/com/example/rubymod/ModConfigs.java"));
        assertTrue(f26.files.has("src/main/java/com/example/rubymod/ModFluids.java"));
        assertTrue(f26.files.has("src/main/java/com/example/rubymod/ModEntities.java"));
        assertTrue(f26.files.has("src/main/java/com/example/rubymod/ModProfessions.java"));
        assertTrue(f26.files.has("src/main/java/com/example/rubymod/ModRecipeTypes.java"));

        // Quilt
        GenContext q = Generators.generate(p, Target.QUILT, McVersion.V1_21_1);
        assertTrue(q.files.has("src/main/resources/quilt.mod.json"));
        assertTrue(text(q, "src/main/resources/quilt.mod.json").contains("architectury"));

        // Forge & NeoForge
        GenContext fg201 = Generators.generate(p, Target.FORGE, McVersion.V1_20_1);
        assertFalse(text(fg201, "build.gradle").contains("sourcesSets/"));

        GenContext fg204 = Generators.generate(p, Target.FORGE, McVersion.V1_20_4);
        assertTrue(text(fg204, "build.gradle").contains("it.output.resourcesDir = dir"));
        assertTrue(text(fg204, "build.gradle").contains("it.java.destinationDirectory = dir"));

        GenContext fg21 = Generators.generate(p, Target.FORGE, McVersion.V1_21_1);
        assertTrue(fg21.files.has("src/main/resources/META-INF/mods.toml"));
        assertTrue(text(fg21, "src/main/resources/META-INF/mods.toml").contains("architectury"));
        assertTrue(text(fg21, "build.gradle").contains("MixinConfigs"));
        assertTrue(text(fg21, "build.gradle").contains("sourcesSets/$it.name"));
        assertTrue(text(fg21, "build.gradle").contains("it.output.resourcesDir = dir"));
        assertTrue(text(fg21, "build.gradle").contains("it.java.destinationDirectory = dir"));

        GenContext nf21 = Generators.generate(p, Target.NEOFORGE, McVersion.V1_21_8);
        assertTrue(nf21.files.has("src/main/resources/META-INF/neoforge.mods.toml"));
        assertTrue(text(nf21, "src/main/resources/META-INF/neoforge.mods.toml").contains("[[mixins]]"));
        assertTrue(nf21.files.has("src/main/java/com/example/rubymod/ModEntities.java"));
    }

    @Test
    void generatesPaperFoliaPacksAndBedrock() {
        Project p = fullProject();

        // Paper & Folia
        GenContext paper = Generators.generate(p, Target.PAPER, McVersion.V1_21_8);
        assertTrue(paper.files.has("src/main/resources/plugin.yml"));
        assertTrue(text(paper, "src/main/resources/plugin.yml").contains("softdepend"));
        assertTrue(text(paper, "src/main/resources/plugin.yml").contains("architectury"));

        GenContext folia = Generators.generate(p, Target.FOLIA, McVersion.V26_1);
        String fyml = text(folia, "src/main/resources/plugin.yml");
        assertTrue(fyml.contains("folia-supported: true"));
        String mainJava = text(folia, "src/main/java/com/example/rubymod/RubyModPlugin.java");
        assertTrue(mainJava.contains("getGlobalRegionScheduler") || mainJava.contains("getScheduler"));

        // Datapack across old & 1.21.9+ / 1.21.11 / 26.1
        GenContext dpOld = Generators.generate(p, Target.DATAPACK, McVersion.V1_21_1);
        assertTrue(dpOld.files.has("data/rubymod/trim_pattern/crimson.json"));
        assertTrue(text(dpOld, "data/rubymod/trim_pattern/crimson.json").contains("template_item"));
        assertTrue(dpOld.files.has("data/rubymod/banner_pattern/ruby_crest.json"));
        assertTrue(dpOld.files.has("data/rubymod/worldgen/configured_feature/ruby_patch.json"));
        assertTrue(dpOld.files.has("data/rubymod/worldgen/placed_feature/ruby_patch.json"));

        GenContext dpModern = Generators.generate(p, Target.DATAPACK, McVersion.V1_21_11);
        assertTrue(text(dpModern, "pack.mcmeta").replaceAll("\\s+", "").contains("[94,1]"));
        assertFalse(text(dpModern, "data/rubymod/trim_pattern/crimson.json").contains("template_item"));

        GenContext rp = Generators.generate(p, Target.RESOURCEPACK, McVersion.V1_21_9);
        assertTrue(text(rp, "pack.mcmeta").contains("\"min_format\""));
        assertTrue(rp.files.has("assets/minecraft/shaders/program/phosphor.fsh"));
        assertTrue(rp.files.has("assets/rubymod/textures/entity/banner/ruby_crest.png"));

        // Bedrock
        GenContext be = Generators.generate(p, Target.BEDROCK, McVersion.V1_21_1);
        assertTrue(be.files.has("rubymod_BP/manifest.json"));
        assertTrue(be.files.has("rubymod_RP/manifest.json"));
        assertTrue(be.files.has("rubymod_RP/texts/en_US.lang"));
        assertTrue(text(be, "rubymod_BP/scripts/main.js").contains("const dialogs = {"));
    }

    @Test
    void cliValidateAndGenerateWorkHeadlessly(@TempDir Path dir) throws Exception {
        Project p = fullProject();
        Path proj = dir.resolve("rubymod.msproj");
        ProjectIO.save(p, proj);

        int valCode = Cli.run(new String[]{"--validate", proj.toString()});
        assertEquals(0, valCode);

        Path out = dir.resolve("out");
        int genCode = Cli.run(new String[]{"--generate", out.toString(), proj.toString(), "1.21.1,26.1", "fabric,folia,datapack,bedrock"});
        assertEquals(0, genCode);
        assertTrue(Files.isRegularFile(out.resolve("fabric-26.1").resolve("build.gradle")));
        assertTrue(Files.isRegularFile(out.resolve("folia-26.1").resolve("src/main/resources/plugin.yml")));
        assertTrue(Files.isRegularFile(out.resolve("datapack-26.1").resolve("pack.mcmeta")));
        assertTrue(Files.isRegularFile(out.resolve("dist").resolve(Exporter.universalFileName(p, Target.DATAPACK))));
        assertTrue(Files.isRegularFile(out.resolve("bedrock").resolve("rubymod_BP/manifest.json")));
    }
}
