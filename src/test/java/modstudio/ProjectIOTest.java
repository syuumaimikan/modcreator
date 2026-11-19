package modstudio;

import modstudio.core.*;
import modstudio.core.Elements.*;
import modstudio.core.ModElements.*;
import modstudio.core.Systems.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ProjectIOTest {

    @Test
    void roundTripSampleAndAllExtendedTypes(@TempDir Path dir) throws Exception {
        Project p = SampleProject.create();
        p.meta.mcVersions = new java.util.ArrayList<>(List.of("1.20.1", "1.21.1", "1.21.9", "26.1"));
        p.meta.mcVersion = "26.1";
        p.meta.targets = new java.util.ArrayList<>(List.of("forge", "neoforge", "fabric", "quilt", "paper", "folia", "datapack", "resourcepack", "bedrock"));

        ModDependency dep = new ModDependency();
        dep.modId = "jei";
        dep.version = "[19.0,)";
        dep.required = false;
        p.meta.dependencies.add(dep);

        String blockTex = p.textureByName("ruby_block").id;
        String swordTex = p.textureByName("ruby_sword").id;

        Fluid fl = new Fluid();
        fl.name = "crystal_water";
        fl.displayName = "Crystal Water";
        fl.stillTexture = blockTex;
        fl.flowingTexture = blockTex;
        fl.luminance = 8;

        Elements.Block slab = new Elements.Block();
        slab.name = "ruby_slab";
        slab.shape = "slab";
        slab.texture = blockTex;
        slab.redstoneSignal = 7;
        slab.waterloggable = true;

        Elements.Item bow = new Elements.Item();
        bow.name = "ruby_bow";
        bow.kind = "bow";
        bow.durability = 384;
        bow.projectileDamage = 8.0;
        bow.texture = swordTex;

        Container ct = new Container();
        ct.name = "gem_refiner";
        ct.displayName = "Gem Refiner";
        ct.block = "rubymod:ruby_block";
        ct.useCustomSlots = true;
        SlotDef s0 = new SlotDef();
        s0.index = 0; s0.x = 56; s0.y = 35; s0.kind = "input";
        ct.slots.add(s0);
        ContainerRecipe cr = new ContainerRecipe();
        cr.input = "minecraft:diamond"; cr.output = "rubymod:ruby"; cr.processTicks = 60;
        ct.recipes.add(cr);

        CustomEntity ent = new CustomEntity();
        ent.name = "ruby_golem";
        ent.displayName = "Ruby Golem";
        ent.kind = "mob";
        ent.health = 50;
        ent.naturalSpawn = true;

        ModConfig cfg = new ModConfig();
        cfg.name = "common_settings";
        ConfigEntry ce = new ConfigEntry();
        ce.key = "drop_rate"; ce.kind = "double"; ce.value = "1.5"; ce.min = "0.1"; ce.max = "10.0";
        cfg.entries.add(ce);

        MixinSpec mx = new MixinSpec();
        mx.name = "player_tick_hook";
        mx.targetClass = "net.minecraft.world.entity.player.Player";
        mx.methodTarget = "tick";

        Portal pt = new Portal();
        pt.name = "ruby_portal";
        pt.frameBlock = "rubymod:ruby_block";
        pt.targetDimension = "minecraft:the_nether";

        WorldFeature wf = new WorldFeature();
        wf.name = "deep_ruby_vein";
        wf.featureType = "ore";
        wf.block = "rubymod:ruby_ore";

        Profession pf = new Profession();
        pf.name = "gemologist";
        pf.workstationBlock = "rubymod:ruby_block";
        ProfessionTrade tr = new ProfessionTrade();
        tr.result = "rubymod:ruby";
        pf.trades.add(tr);

        ArmorTrim at = new ArmorTrim();
        at.name = "ruby_trim";
        at.generateTemplate = true;

        BannerPattern bp = new BannerPattern();
        bp.name = "ruby_crest";

        CustomRecipeType crt = new CustomRecipeType();
        crt.name = "refining";
        crt.maxInputs = 2;

        Shader sh = new Shader();
        sh.name = "ruby_glow_post";
        sh.kind = "post";
        sh.mode = "nodes";
        sh.nodes.addAll(ShaderNodes.starter());

        CustomScreen cs = new CustomScreen();
        cs.name = "hud_menu";
        ScreenWidget tf = new ScreenWidget();
        tf.kind = "textfield";
        tf.placeholder = "Filter...";
        cs.widgets.add(tf);
        ScreenWidget sl = new ScreenWidget();
        sl.kind = "slider";
        sl.text = "Volume";
        sl.min = 0;
        sl.maxVal = 100;
        cs.widgets.add(sl);

        p.elements.addAll(List.of(fl, slab, bow, ct, ent, cfg, mx, pt, wf, pf, at, bp, crt, sh, cs));

        Path file = dir.resolve("rubymod.msproj");
        ProjectIO.save(p, file);
        Project loaded = ProjectIO.load(file);

        assertEquals("26.1", loaded.meta.mcVersion);
        assertEquals(1, loaded.meta.dependencies.size());
        assertEquals("jei", loaded.meta.dependencies.get(0).modId);
        assertEquals(1, loaded.all(Fluid.class).size());
        assertEquals(1, loaded.all(Container.class).size());
        assertEquals(1, loaded.all(CustomEntity.class).size());
        assertEquals(1, loaded.all(ModConfig.class).size());
        assertEquals(1, loaded.all(MixinSpec.class).size());
        assertEquals(1, loaded.all(Portal.class).size());
        assertEquals(1, loaded.all(WorldFeature.class).size());
        assertEquals(1, loaded.all(Profession.class).size());
        assertEquals(1, loaded.all(ArmorTrim.class).size());
        assertEquals(1, loaded.all(BannerPattern.class).size());
        assertEquals(1, loaded.all(CustomRecipeType.class).size());
        assertEquals("slab", loaded.all(Elements.Block.class).stream().filter(b -> b.name.equals("ruby_slab")).findFirst().orElseThrow().shape);
        assertEquals("bow", loaded.all(Elements.Item.class).stream().filter(i -> i.name.equals("ruby_bow")).findFirst().orElseThrow().kind);
        assertEquals("post", loaded.all(Shader.class).stream().filter(s -> s.name.equals("ruby_glow_post")).findFirst().orElseThrow().kind);
        assertEquals("textfield", loaded.all(CustomScreen.class).stream().filter(s -> s.name.equals("hud_menu")).findFirst().orElseThrow().widgets.get(0).kind);
    }

    @Test
    void migratesLegacyTriggerAndToolchainMap() {
        String legacy = """
                {
                  "meta": {
                    "name": "Legacy",
                    "modId": "legacymod",
                    "mcVersion": "1.21.1",
                    "targets": ["fabric"]
                  },
                  "versions": {
                    "fabric": { "fabric_loader": "0.16.0" }
                  },
                  "elements": [
                    {
                      "type": "advancement",
                      "name": "nap",
                      "title": "Nap",
                      "trigger": "sleeping_in_bed"
                    }
                  ]
                }
                """;
        Project p = ProjectIO.fromJson(legacy);
        assertNotNull(p);
        assertNotNull(p.meta.dependencies);
        Elements.Advancement a = p.all(Elements.Advancement.class).get(0);
        assertEquals("slept_in_bed", a.trigger);
        assertEquals("0.16.0", p.versions.getOrDefault("fabric@1.21.1", java.util.Map.of()).get("fabric_loader"));
    }

    @Test
    void starterTemplatesHaveNoValidationErrors() {
        for (String id : ProjectTemplates.IDS) {
            Project p = new Project();
            p.meta.modId = "tplmod";
            p.meta.name = "Template Mod";
            ProjectTemplates.apply(p, id);
            var errs = Validator.validate(p).stream()
                    .filter(pr -> pr.severity() == Validator.Severity.ERROR)
                    .toList();
            assertTrue(errs.isEmpty(), "Template " + id + " had errors: " + errs);
        }
    }
}
