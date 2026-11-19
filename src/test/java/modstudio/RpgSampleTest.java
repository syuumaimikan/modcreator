package modstudio;

import modstudio.core.*;
import modstudio.core.Elements.*;
import modstudio.core.Systems.*;
import modstudio.gen.GenContext;
import modstudio.gen.Generators;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class RpgSampleTest {

    @Test
    void rpgSampleValidatesWithZeroErrorsAndWarnings() {
        Project p = RpgSampleProject.create();
        List<Validator.Problem> problems = Validator.validate(p);
        List<Validator.Problem> bad = problems.stream()
                .filter(pr -> pr.severity() != Validator.Severity.INFO)
                .toList();
        assertTrue(bad.isEmpty(), () -> "Expected 0 errors/warnings but found: " +
                bad.stream().map(pr -> pr.severity() + ": " + pr.message()).toList());

        assertEquals("aetheria", p.meta.modId);
        assertTrue(p.textures.size() >= 35, "Expected rich hand-crafted texture set");
        assertNotNull(p.model("astral_greatsword"));
        assertNotNull(p.model("gale_staff"));
        assertNotNull(p.model("waypoint_shrine"));
        assertNotNull(p.model("resonance_beacon"));
        Model bossRig = p.model("astral_warden");
        assertNotNull(bossRig);
        assertFalse(bossRig.bones.isEmpty());
        assertEquals(6, bossRig.animations.size());
        assertTrue(Rig.reach(bossRig) <= 96, "Rig reach must stay within 96 units");
    }

    @Test
    void rpgSampleGeneratesCleanNeoForgeAndFabricAcrossVersions() {
        Project p = RpgSampleProject.create();

        for (Target target : List.of(Target.NEOFORGE, Target.FABRIC)) {
            for (McVersion ver : List.of(McVersion.V1_21_1, McVersion.V1_21_11)) {
                GenContext ctx = Generators.generate(p, target, ver);

                assertTrue(ctx.files.has("src/main/java/com/aetheria/rpg/ModItems.java"), target + " " + ver);
                assertTrue(ctx.files.has("src/main/java/com/aetheria/rpg/ModBlocks.java"), target + " " + ver);
                assertTrue(ctx.files.has("src/main/java/com/aetheria/rpg/ModEntities.java"), target + " " + ver);
                assertTrue(ctx.files.has("src/main/java/com/aetheria/rpg/ModConfigs.java"), target + " " + ver);
                assertTrue(ctx.files.has("src/main/java/com/aetheria/rpg/ModScreens.java"), target + " " + ver);
                assertTrue(ctx.files.has("src/main/java/com/aetheria/rpg/item/AstralGreatswordItem.java"), target + " " + ver);
                assertTrue(ctx.files.has("src/main/java/com/aetheria/rpg/block/WaypointShrineBlock.java"), target + " " + ver);
                assertTrue(ctx.files.has("src/main/java/com/aetheria/rpg/block/CelestiaPortalPortalBlock.java"), target + " " + ver);
                assertTrue(ctx.files.has("src/main/java/com/aetheria/rpg/container/RelicForgeBlockEntity.java"), target + " " + ver);
                assertTrue(ctx.files.has("src/main/java/com/aetheria/rpg/entity/SkyStalkerEntity.java"), target + " " + ver);

                String funcRoot = ver.atLeast(McVersion.V1_21_1) ? "function" : "functions";
                String advRoot = ver.atLeast(McVersion.V1_21_1) ? "advancement" : "advancements";
                assertTrue(ctx.files.has("src/main/resources/data/aetheria/" + funcRoot + "/procedures/rpg_mana_regen.mcfunction"));
                assertTrue(ctx.files.has("src/main/resources/data/aetheria/" + advRoot + "/slay_warden.json"));
                assertTrue(ctx.files.has("src/main/resources/data/aetheria/dimension/celestia_reach.json"));
                assertTrue(ctx.files.has("src/main/resources/data/aetheria/loot_table/chests/celestia_shrine.json")
                        || ctx.files.has("src/main/resources/data/aetheria/loot_tables/chests/celestia_shrine.json"));

                assertTrue(ctx.files.has("src/main/resources/assets/aetheria/models/item/astral_greatsword.json"));
                assertTrue(ctx.files.has("src/main/resources/assets/aetheria/models/block/waypoint_shrine.json"));
                assertTrue(ctx.files.has("src/main/resources/assets/aetheria/textures/gui/menu/astral_hub.png"));
                assertTrue(ctx.files.has("src/main/resources/assets/minecraft/font/default.json"));
                assertTrue(ctx.files.has("src/main/resources/assets/aetheria/lang/en_us.json"));
                assertTrue(ctx.files.has("src/main/resources/assets/aetheria/lang/ja_jp.json"));

                String greatswordJava = new String(ctx.files.get("src/main/java/com/aetheria/rpg/item/AstralGreatswordItem.java"), StandardCharsets.UTF_8);
                assertTrue(greatswordJava.contains("Starfall Cleave"));
                assertTrue(greatswordJava.contains("appendHoverText"));

                String jaJson = new String(ctx.files.get("src/main/resources/assets/aetheria/lang/ja_jp.json"), StandardCharsets.UTF_8);
                assertTrue(jaJson.contains("星落の大剣"));
                assertTrue(jaJson.contains("星辰の守護者・ソヴリン"));

                // Regression checks discovered by real NeoForge & Fabric javac builds
                assertTrue(ctx.files.has("src/main/java/com/aetheria/rpg/ModMenus.java"), "Chest GUI ModMenus missing");
                assertTrue(ctx.files.has("src/main/java/com/aetheria/rpg/ModContainerMenus.java"), "Container ModContainerMenus missing");
                String beJava = new String(ctx.files.get("src/main/java/com/aetheria/rpg/container/RelicForgeBlockEntity.java"), StandardCharsets.UTF_8);
                assertTrue(beJava.contains("import com.aetheria.rpg.ModItems;"));
                String ignJava = new String(ctx.files.get("src/main/java/com/aetheria/rpg/item/CelestiaPortalIgniterItem.java"), StandardCharsets.UTF_8);
                assertTrue(ignJava.contains("import net.minecraft.resources.ResourceLocation;"));
                assertTrue(ignJava.contains("import com.aetheria.rpg.ModBlocks;"));
                String portalJava = new String(ctx.files.get("src/main/java/com/aetheria/rpg/block/CelestiaPortalPortalBlock.java"), StandardCharsets.UTF_8);
                assertTrue(portalJava.contains("import net.minecraft.resources.ResourceLocation;"));
                String screensJava = new String(ctx.files.get("src/main/java/com/aetheria/rpg/ModScreens.java"), StandardCharsets.UTF_8);
                assertTrue(screensJava.contains("split(\"\\\\R\")"), "ModScreens must emit valid Java string literal \"\\\\R\"");
                assertFalse(screensJava.contains("split(\"\\R\")"));
                if (target == Target.FABRIC) {
                    String clientJava = new String(ctx.files.get("src/main/java/com/aetheria/rpg/AetheriaAstralResonanceClient.java"), StandardCharsets.UTF_8);
                    assertTrue(clientJava.contains("import net.minecraft.resources.ResourceLocation;"));
                    assertTrue(clientJava.contains("aetheria:block/mana_spring_still"));
                } else {
                    String mainJava = new String(ctx.files.get("src/main/java/com/aetheria/rpg/AetheriaAstralResonance.java"), StandardCharsets.UTF_8);
                    assertTrue(mainJava.contains("ModContainerMenus.MENUS.register(modBus);"));
                    assertTrue(mainJava.contains("(EntityAttributeCreationEvent ev) ->"));
                }
            }
        }
    }

    @Test
    void rpgTemplateAppliesAndRoundTripsCleanly(@TempDir Path dir) throws Exception {
        Project custom = new Project();
        custom.meta.name = "Chronosia Saga";
        custom.meta.modId = "chronosia";
        custom.meta.packageName = "net.chronosia.saga";
        custom.meta.targets = new ArrayList<>(List.of("neoforge", "fabric"));
        custom.meta.mcVersions = new ArrayList<>(List.of("1.21.1"));
        custom.normalizeVersions();

        ProjectTemplates.apply(custom, "rpg_aetheria");

        List<Validator.Problem> bad = Validator.validate(custom).stream()
                .filter(pr -> pr.severity() != Validator.Severity.INFO)
                .toList();
        assertTrue(bad.isEmpty(), () -> "Custom modId template issues: " +
                bad.stream().map(pr -> pr.severity() + ": " + pr.message()).toList());

        Path projFile = dir.resolve("chronosia.msproj");
        ProjectIO.save(custom, projFile);
        Project loaded = ProjectIO.load(projFile);

        assertEquals(custom.elements.size(), loaded.elements.size());
        assertEquals(custom.textures.size(), loaded.textures.size());
        assertNotNull(loaded.model("astral_warden"));
        assertTrue(Generators.generate(loaded, Target.NEOFORGE, McVersion.V1_21_1)
                .files.has("src/main/java/net/chronosia/saga/item/AstralGreatswordItem.java"));
    }
}

