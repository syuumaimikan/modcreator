package modstudio;

import com.google.gson.*;
import modstudio.core.*;
import modstudio.core.Elements.*;
import modstudio.core.ModElements.*;
import modstudio.core.Systems.*;
import modstudio.gen.*;
import modstudio.mcp.*;
import modstudio.ui.AppSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies all newly added features:
 * - Underground ore surface prevention (Block + WorldFeature)
 * - Multilingual HUD & CustomScreen localization (Sidebars, Bossbars, Actionbars, Screens)
 * - 3D Armor models & 3D Custom Entities / Projectiles across 1.20.1 - 26.1 and Bedrock
 * - Multi-language programming support (Kotlin, Scala, Groovy, Python, Lua, TypeScript) & pure-Java non-pollution
 * - Fine-grained Item, Block, Entity, and Model fields + Refactor/Validator checks
 * - MCP Server JSON-RPC 2.0, McpToolRegistry, and offline AiAgentClient
 * - Complete EN / JA / ZH / KO i18n bundles for all new keys
 */
final class NewFeaturesTest {

    private static String fileText(GenContext ctx, String path) {
        byte[] b = ctx.files.get(path);
        assertNotNull(b, "Missing generated file: " + path + " (target=" + ctx.target.id + ", mc=" + ctx.v.id + ")");
        return new String(b, StandardCharsets.UTF_8);
    }

    private static Project baseProject() {
        Project p = new Project();
        p.meta.name = "FeatureTestMod";
        p.meta.modId = "featmod";
        p.meta.packageName = "com.example.featmod";
        p.meta.mcVersion = "1.21.1";
        p.meta.mcVersions = new ArrayList<>(List.of("1.20.1", "1.21.1", "26.1"));
        p.meta.targets = new ArrayList<>(List.of("fabric", "neoforge", "forge", "paper", "datapack", "resourcepack", "bedrock"));
        p.meta.languages = new ArrayList<>(List.of("en_us", "ja_jp"));
        TextureAsset icon = TextureAsset.of("sample_tex", new int[16 * 16], 16, 16);
        p.textures.add(icon);
        return p;
    }

    @Test
    void oresStayUndergroundAndRespectGranularSettings() {
        Project p = baseProject();

        Block overworldOre = new Block();
        overworldOre.name = "mithril_ore";
        overworldOre.displayName = "Mithril Ore";
        overworldOre.oreGen = true;
        overworldOre.oreDimension = "overworld";
        overworldOre.oreMinY = -64;
        // Default oreMaxY should be safely below sea level (<= 32)
        assertEquals(32, overworldOre.oreMaxY);
        assertTrue(overworldOre.orePreventSurface);
        overworldOre.oreSurfaceDepth = 6;
        overworldOre.oreDiscardAir = 0.75;
        overworldOre.oreBiomes = "minecraft:deep_dark";

        Block netherOre = new Block();
        netherOre.name = "hellstone_ore";
        netherOre.oreGen = true;
        netherOre.oreDimension = "nether";
        netherOre.oreMinY = 8;
        netherOre.oreMaxY = 96;

        WorldFeature wf = new WorldFeature();
        wf.name = "deep_crystal_vein";
        wf.featureType = "ore";
        wf.block = "featmod:mithril_ore";
        wf.minY = -48;
        wf.maxY = 20;
        assertTrue(wf.preventSurface);

        p.elements.addAll(List.of(overworldOre, netherOre, wf));

        for (McVersion v : List.of(McVersion.V1_20_1, McVersion.V1_21_1, McVersion.V26_1)) {
            GenContext ctx = Generators.generate(p, Target.FABRIC, v);
            String owPlaced = fileText(ctx, "src/main/resources/data/featmod/worldgen/placed_feature/mithril_ore.json");
            assertTrue(owPlaced.contains("minecraft:surface_relative_threshold_filter"), v.id);
            assertTrue(owPlaced.contains("OCEAN_FLOOR_WG"), v.id);
            assertTrue(owPlaced.contains("\"max_inclusive\": -6"), v.id);
            assertTrue(owPlaced.contains("minecraft:trapezoid"), v.id);

            String owCfg = fileText(ctx, "src/main/resources/data/featmod/worldgen/configured_feature/mithril_ore.json");
            assertTrue(owCfg.contains("\"discard_chance_on_air_exposure\": 0.75"), v.id);

            String wfPlaced = fileText(ctx, "src/main/resources/data/featmod/worldgen/placed_feature/deep_crystal_vein.json");
            assertTrue(wfPlaced.contains("minecraft:surface_relative_threshold_filter"), v.id);

            String netherPlaced = fileText(ctx, "src/main/resources/data/featmod/worldgen/placed_feature/hellstone_ore.json");
            assertFalse(netherPlaced.contains("surface_relative_threshold_filter"), "Nether ore must not apply overworld OCEAN_FLOOR_WG filter");

            String eventsCls = fileText(ctx, "src/main/java/com/example/featmod/ModEvents.java");
            assertTrue(eventsCls.contains("deep_dark"), "Custom biome filter missing in Fabric BiomeModifications");
        }

        GenContext nf = Generators.generate(p, Target.NEOFORGE, McVersion.V1_21_1);
        String modifier = fileText(nf, "src/main/resources/data/featmod/neoforge/biome_modifier/mithril_ore.json");
        assertTrue(modifier.contains("minecraft:deep_dark"));
    }

    @Test
    void hudsAndScreensSupportMultilingualTranslationsAcrossVersions() {
        Project p = baseProject();

        Hud sidebar = new Hud();
        sidebar.name = "quest_board";
        sidebar.kind = "sidebar";
        sidebar.title = "&6Status";
        sidebar.lines = new ArrayList<>(List.of("Kills: {kills} 100%", "Stage: {stage}"));
        sidebar.setLoc("title", "ja_jp", "&6ステータス");
        sidebar.setLoc("line.0", "ja_jp", "撃破数: {kills} 100%");
        sidebar.setLoc("line.1", "ja_jp", "ステージ: {stage}");

        Hud boss = new Hud();
        boss.name = "raid_progress";
        boss.kind = "bossbar";
        boss.title = "Wave {wave}";
        boss.setLoc("title", "ja_jp", "ウェーブ {wave}");

        Hud action = new Hud();
        action.name = "combo_hint";
        action.kind = "actionbar";
        action.title = "Combo x{combo}";
        action.setLoc("title", "ja_jp", "コンボ {combo}回");

        CustomScreen scr = new CustomScreen();
        scr.name = "skill_menu";
        scr.mode = "screen";
        scr.title = "Skill Tree";
        scr.setLoc("title", "ja_jp", "スキルツリー");
        ScreenWidget btn = new ScreenWidget();
        btn.kind = "button";
        btn.text = "Upgrade";
        ScreenWidget tf = new ScreenWidget();
        tf.kind = "textfield";
        tf.placeholder = "Enter name";
        ScreenWidget sl = new ScreenWidget();
        sl.kind = "scroll_list";
        sl.options = "Alpha\nBeta";
        scr.widgets.addAll(List.of(btn, tf, sl));
        scr.setLoc("w.0.text", "ja_jp", "強化する");
        scr.setLoc("w.1.placeholder", "ja_jp", "名前を入力");
        scr.setLoc("w.2.options", "ja_jp", "アルファ\nベータ");

        p.elements.addAll(List.of(sidebar, boss, action, scr));

        Map<String, Map<String, String>> locs = Localization.files(p);
        assertEquals("§6ステータス", locs.get("ja_jp").get("hud.featmod.quest_board.title"));
        assertEquals("撃破数: %1$s 100%%", locs.get("ja_jp").get("hud.featmod.quest_board.line.0"));
        assertEquals("ウェーブ %1$s", locs.get("ja_jp").get("hud.featmod.raid_progress.title"));
        assertEquals("コンボ %1$s回", locs.get("ja_jp").get("hud.featmod.combo_hint.title"));
        assertEquals("スキルツリー", locs.get("ja_jp").get("screen.featmod.skill_menu.title"));
        assertEquals("強化する", locs.get("ja_jp").get("screen.featmod.skill_menu.w.0.text"));
        assertEquals("名前を入力", locs.get("ja_jp").get("screen.featmod.skill_menu.w.1.placeholder"));

        // Datapack InterfaceGen functions across pre-macro 1.20.1 & macro 1.21.1+
        for (McVersion v : List.of(McVersion.V1_20_1, McVersion.V1_21_1, McVersion.V26_1)) {
            GenContext dp = Generators.generate(p, Target.DATAPACK, v);
            String fnBase = v.singular() ? "function" : "functions";
            String sidebarFile = v.atLeast(McVersion.V1_20_4)
                    ? "data/featmod/" + fnBase + "/hud/quest_board/lines.mcfunction"
                    : "data/featmod/" + fnBase + "/_load.mcfunction";
            String showSidebar = fileText(dp, sidebarFile);
            assertTrue(showSidebar.contains("\"translate\":\"hud.featmod.quest_board.line.0\""), v.id);
            String showBoss = fileText(dp, "data/featmod/" + fnBase + "/hud/raid_progress/refresh.mcfunction");
            assertTrue(showBoss.contains("\"translate\":\"hud.featmod.raid_progress.title\""), v.id);
            String showAction = fileText(dp, "data/featmod/" + fnBase + "/hud/combo_hint/refresh.mcfunction");
            assertTrue(showAction.contains("\"translate\":\"hud.featmod.combo_hint.title\""), v.id);
        }

        // ScreenGen emits Language.getInstance() + preserves split("\\R")
        for (McVersion v : List.of(McVersion.V1_20_1, McVersion.V1_21_1, McVersion.V26_1)) {
            GenContext fab = Generators.generate(p, Target.FABRIC, v);
            String ms = fileText(fab, "src/main/java/com/example/featmod/ModScreens.java");
            assertTrue(ms.contains("Language.getInstance()") && ms.contains("lang.has(key)"), v.id);
            assertFalse(ms.contains("I18n.exists("), "I18n.exists was removed in 26.x: " + v.id);
            assertTrue(ms.contains("split(\"\\\\R\")"), v.id);
        }
    }

    @Test
    void supports3dArmorAnd3dEntitiesAcrossLoadersAndBedrock() {
        Project p = baseProject();
        String texId = p.textures.get(0).id;

        Model helmM = new Model();
        helmM.name = "paladin_helm_3d";
        Models.applyTemplate(helmM, "armor_helmet");
        helmM.cubes.get(0).inflate = 0.5;
        helmM.textures.put("0", texId);

        Model beastM = new Model();
        beastM.name = "stone_beast_mdl";
        Models.applyTemplate(beastM, "quadruped");
        beastM.textures.put("0", texId);

        ArmorMaterial am = new ArmorMaterial();
        am.name = "paladin";
        am.helmetModel = "paladin_helm_3d";
        am.armorTexture = texId;
        am.setBonusEffect = "minecraft:resistance";
        am.setBonusAmplifier = 1;

        Item helm = new Item();
        helm.name = "paladin_helmet";
        helm.kind = "helmet";
        helm.armorMaterial = "paladin";
        helm.texture = texId;

        CustomEntity ce = new CustomEntity();
        ce.name = "stone_beast";
        ce.kind = "hostile";
        ce.model = "stone_beast_mdl";
        ce.texture = texId;
        ce.armor = 8.0;
        ce.scale = 1.35;
        ce.stepHeight = 1.0;
        ce.xpReward = 40;
        ce.boss = true;
        ce.bossColor = "red";
        ce.bossStyle = "notched_10";
        ce.mainhand = "minecraft:iron_axe";
        ce.head = "featmod:paladin_helmet";

        p.elements.addAll(List.of(helmM, beastM, am, helm, ce));

        for (Target tgt : List.of(Target.FABRIC, Target.NEOFORGE, Target.FORGE)) {
            for (McVersion v : List.of(McVersion.V1_20_1, McVersion.V1_21_1, McVersion.V26_1)) {
                if (!tgt.supports(v)) continue;
                GenContext ctx = Generators.generate(p, tgt, v);
                String armorCls = fileText(ctx, "src/main/java/com/example/featmod/client/ModArmorModels.java");
                assertTrue(armorCls.contains("LAYER_PALADIN_HELMET"), tgt + " " + v.id);
                if (tgt == Target.FABRIC && v.compareTo(McVersion.V26_1) >= 0) {
                    assertTrue(armorCls.contains("submitTransformCopyingModel"), "Fabric 26.1 SubmitNodeCollector API missing");
                }
                assertTrue(ctx.files.has("src/main/java/com/example/featmod/client/StoneBeastModel.java"), tgt + " " + v.id);
                assertTrue(ctx.files.has("src/main/java/com/example/featmod/client/StoneBeastRenderer.java"), tgt + " " + v.id);
                String entCls = fileText(ctx, "src/main/java/com/example/featmod/entity/StoneBeastEntity.java");
                assertTrue(entCls.contains("Attributes.ARMOR"), tgt + " " + v.id);
                assertTrue(entCls.contains("ServerBossEvent"), tgt + " " + v.id);
            }
        }

        // Bedrock 3D armor export
        GenContext bed = Generators.generate(p, Target.BEDROCK, McVersion.V1_21_1);
        String geo = fileText(bed, "featmod_RP/models/entity/paladin_helmet.geo.json");
        assertTrue(geo.contains("\"inflate\": 0.5"));
        assertTrue(bed.files.has("featmod_RP/attachables/paladin_helmet.json"));

        // Refactor rename updates ArmorMaterial & Item wornModel
        Item cloak = new Item();
        cloak.name = "knight_helm";
        cloak.kind = "helmet";
        cloak.wornModel = "paladin_helm_3d";
        p.elements.add(cloak);
        helmM.name = "paladin_helm_v2";
        Refactor.renameElement(p, helmM, "paladin_helm_3d", "paladin_helm_v2");
        assertEquals("paladin_helm_v2", am.helmetModel);
        assertEquals("paladin_helm_v2", cloak.wornModel);
    }

    @Test
    void multiLanguageCodeSupportDoesNotPollutePureJavaProjects() {
        Project pure = baseProject();
        CodeFile javaFile = new CodeFile();
        javaFile.name = "pure_helper";
        javaFile.path = "src/main/java/com/example/featmod/custom/PureHelper.java";
        javaFile.content = "package com.example.featmod.custom;\npublic final class PureHelper { public static void init() {} }\n";
        pure.elements.add(javaFile);

        GenContext pureFab = Generators.generate(pure, Target.FABRIC, McVersion.V1_21_1);
        String pureGradle = fileText(pureFab, "build.gradle");
        assertFalse(pureGradle.contains("kotlin"), "Pure-Java project must not emit Kotlin plugin");
        assertFalse(pureGradle.contains("scala"), "Pure-Java project must not emit Scala plugin");

        Project polyglot = baseProject();
        CodeFile kt = new CodeFile();
        kt.name = "kt_boot";
        kt.language = "kotlin";
        kt.hook = "common_init";
        kt.hookMethod = "init";
        kt.path = "src/main/kotlin/{package}/custom/KtBoot.kt";
        kt.content = "package {package_name}.custom\nobject KtBoot { fun init() {} }\n";

        CodeFile py = new CodeFile();
        py.name = "build_script";
        py.language = "python";
        py.target = "all";
        py.path = "scripts/helper.py";
        py.content = "print('ok')\n";

        CodeFile ts = new CodeFile();
        ts.name = "bp_script";
        ts.language = "typescript";
        ts.target = "bedrock";
        ts.path = "behavior_packs/{modid}/scripts/main.ts";
        ts.content = "import { world } from '@minecraft/server';\n";

        polyglot.elements.addAll(List.of(kt, py, ts));

        GenContext fabKt = Generators.generate(polyglot, Target.FABRIC, McVersion.V1_21_1);
        String ktGradle = fileText(fabKt, "build.gradle");
        assertTrue(ktGradle.contains("org.jetbrains.kotlin.jvm"));
        assertTrue(fabKt.files.has("src/main/kotlin/com/example/featmod/custom/KtBoot.kt"));
        assertTrue(fabKt.files.has("scripts/helper.py"));
        String mainCls = fileText(fabKt, "src/main/java/com/example/featmod/FeatureTestMod.java");
        assertTrue(mainCls.contains("KtBoot.INSTANCE.init()"), "Kotlin object hook must invoke .INSTANCE");

        // Validator warns on non-JVM lifecycle hook
        py.hook = "common_init";
        List<Validator.Problem> probs = Validator.validate(polyglot);
        assertTrue(probs.stream().anyMatch(pr -> pr.severity() == Validator.Severity.WARNING && pr.message().contains("python")));
    }

    @Test
    void granularItemAndBlockCodegenWorkCleanly() {
        Project p = baseProject();
        String texId = p.textures.get(0).id;

        Item elixir = new Item();
        elixir.name = "star_elixir";
        elixir.kind = "food";
        elixir.texture = texId;
        elixir.craftRemainder = "minecraft:glass_bottle";
        elixir.useCooldown = 2.0;
        elixir.consumeAnimation = "drink";
        elixir.useDurationTicks = 20;
        elixir.useReturnItem = "minecraft:glass_bottle";
        elixir.foodEffect = "minecraft:regeneration";
        elixir.foodEffectSeconds = 8;
        Item.ItemAttribute attr = new Item.ItemAttribute();
        attr.attribute = "movement_speed";
        attr.amount = 0.2;
        attr.operation = "add_multiplied_base";
        attr.slot = "mainhand";
        elixir.attributes.add(attr);

        Block sand = new Block();
        sand.name = "mystic_sand";
        sand.texture = texId;
        sand.falling = true;
        sand.hasCollision = false;
        sand.flammability = 15;
        sand.fireSpread = 30;
        sand.instrument = "chime";
        sand.pushReaction = "destroy";
        sand.xpMin = 1;
        sand.xpMax = 4;

        p.elements.addAll(List.of(elixir, sand));

        GenContext fab = Generators.generate(p, Target.FABRIC, McVersion.V1_21_1);
        String items = fileText(fab, "src/main/java/com/example/featmod/ModItems.java");
        assertTrue(items.contains("craftRemainder"));
        assertTrue(items.contains("MOVEMENT_SPEED"));
        String elixirCls = fileText(fab, "src/main/java/com/example/featmod/item/StarElixirItem.java");
        assertTrue(elixirCls.contains("MobEffects.REGENERATION"));
        assertTrue(elixirCls.contains("UseAnim.DRINK"));
        assertTrue(elixirCls.contains("finishUsingItem"));

        GenContext fab26 = Generators.generate(p, Target.FABRIC, McVersion.V26_1);
        String items26 = fileText(fab26, "src/main/java/com/example/featmod/ModItems.java");
        assertTrue(items26.contains("Consumable.builder()"));
        assertTrue(items26.contains("MobEffects.REGENERATION"));

        String blocks = fileText(fab, "src/main/java/com/example/featmod/ModBlocks.java");
        assertTrue(blocks.contains("noCollission()"));
        assertTrue(blocks.contains("NoteBlockInstrument.CHIME"));
        assertTrue(blocks.contains("PushReaction.DESTROY"));

        String sandCls = fileText(fab, "src/main/java/com/example/featmod/block/MysticSandBlock.java");
        assertTrue(sandCls.contains("FallingBlock"));
        assertTrue(sandCls.contains("UniformInt.of(1, 4)"));

        String events = fileText(fab, "src/main/java/com/example/featmod/ModEvents.java");
        assertTrue(events.contains("FlammableBlockRegistry"));
    }

    @Test
    void mcpServerAndBuiltinAiAgentWorkEndToEnd(@TempDir Path tmp) throws Exception {
        Project p = baseProject();
        McpServer ro = new McpServer(tmp.resolve("test.msproj"), p, true);
        McpServer rw = new McpServer(tmp.resolve("test.msproj"), p, false);

        JsonObject init = rw.handleJson("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        assertNotNull(init.getAsJsonObject("result").getAsJsonObject("capabilities").get("tools"));

        JsonObject list = rw.handleJson("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");
        JsonArray tools = list.getAsJsonObject("result").getAsJsonArray("tools");
        assertTrue(tools.size() >= 20, "Expected >= 20 MCP tools, got " + tools.size());

        // Readonly blocks mutation
        JsonObject roMut = ro.handleJson("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"create_armor_set\",\"arguments\":{\"name\":\"blocked\"}}}");
        assertTrue(roMut.getAsJsonObject("result").get("isError").getAsBoolean());

        // Readwrite creates 3D armor set
        JsonObject rwArm = rw.handleJson("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"create_armor_set\",\"arguments\":{\"name\":\"void_knight\",\"create3dModels\":true,\"setBonusEffect\":\"minecraft:night_vision\"}}}");
        assertFalse(rwArm.getAsJsonObject("result").get("isError").getAsBoolean());
        assertNotNull(p.byName(ElementType.ARMOR_MATERIAL, "void_knight"));
        assertNotNull(p.model("void_knight_helmet_3d"));

        // Builtin offline AI client handles Japanese requests via MCP tools
        AiAgentClient ai = new AiAgentClient(new McpToolRegistry());
        AppSettings settings = new AppSettings();
        settings.aiProvider = "builtin";
        McpToolRegistry reg = new McpToolRegistry();
        AiAgentClient.AgentReply reply = ai.ask(settings, p, List.of(), "地表に出ない深層鉱石 mithril を作って",
                (name, args) -> CompletableFuture.completedFuture(reg.execute(p, name, args)));
        assertFalse(reply.executions().isEmpty());
        assertTrue(p.byName(ElementType.BLOCK, "mithril_ore") != null || p.all(Block.class).stream().anyMatch(b -> b.oreGen));

        // Verify no validation errors after AI/MCP mutations
        List<Validator.Problem> probs = Validator.validate(p);
        assertEquals(0, Validator.count(probs, Validator.Severity.ERROR), "Unexpected errors: " + probs);
    }

    @Test
    void allNewI18nKeysPresentInAllFourBundles() throws Exception {
        Map<String, Properties> bundles = new LinkedHashMap<>();
        for (String lang : List.of("en", "ja", "zh", "ko")) {
            Properties props = new Properties();
            try (InputStream in = NewFeaturesTest.class.getResourceAsStream("/modstudio/i18n/messages_" + lang + ".properties")) {
                assertNotNull(in, "Missing bundle: " + lang);
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            bundles.put(lang, props);
        }
        List<String> requiredKeys = List.of(
                "page.ai", "ai.assistant", "ai.provider.builtin", "ai.provider.openai", "ai.provider.gemini", "ai.provider.anthropic",
                "mcp.server_config", "mcp.run_tool", "modelkind.entity", "modeltpl.armor_helmet", "modeltpl.quadruped",
                "model.snap", "model.size", "model.inflate", "model.mirror_x", "model.mirror_z",
                "section.armor_3d", "armor.create_3d_models", "field.worn_model", "field.set_bonus_effect",
                "field.ore_prevent_surface", "field.ore_surface_depth", "field.ore_discard_air", "ore_dist.trapezoid",
                "field.has_collision", "field.falling_block", "field.flammability", "field.instrument", "field.push_reaction",
                "field.xp_min", "field.xp_max", "field.use_cooldown", "field.food_effect", "field.attack_knockback",
                "section.attributes", "item.add_attribute", "translations.preview_lang",
                "field.code_language", "codelang.kotlin", "codelang.scala", "codelang.groovy", "codelang.typescript", "codelang.python",
                "codepreset.kotlin_common", "problem.ore_range", "problem.codefile_hook_lang",
                "preview.hitbox", "preview.anim", "field.base_appearance", "entity_app.biped", "entity_app.quadruped", "entity_app.slime"
        );
        for (var e : bundles.entrySet()) {
            for (String k : requiredKeys) {
                String v = e.getValue().getProperty(k);
                assertTrue(v != null && !v.isBlank(), "Bundle '" + e.getKey() + "' missing or blank key: " + k);
            }
        }
    }

    @Test
    void previewModelsCoverEntitiesMobsArmorAndItems() {
        Project p = baseProject();
        String texId = p.textures.get(0).id;

        Model customModel = new Model();
        customModel.name = "crystal_golem";
        Rig.biped(customModel);
        p.elements.add(customModel);

        // 1. CustomEntity variants
        CustomEntity biped = new CustomEntity();
        biped.name = "knight";
        biped.texture = texId;
        biped.head = "minecraft:iron_helmet";
        biped.mainhand = "minecraft:diamond_sword";
        Model bipedPrev = PreviewModels.forCustomEntity(p, biped);
        assertFalse(bipedPrev.cubes.isEmpty());
        assertTrue(Rig.animated(bipedPrev));
        assertTrue(bipedPrev.cubes.stream().anyMatch(c -> "equipped_head".equals(c.name)));
        assertTrue(bipedPrev.cubes.stream().anyMatch(c -> "equipped_hand".equals(c.name)));
        assertEquals(texId, bipedPrev.textures.get("0"));

        CustomEntity baby = new CustomEntity();
        baby.name = "mini_knight";
        baby.baby = true;
        assertFalse(PreviewModels.forCustomEntity(p, baby).cubes.isEmpty());

        CustomEntity quad = new CustomEntity();
        quad.name = "dire_wolf";
        quad.baseAppearance = "quadruped";
        Model quadPrev = PreviewModels.forCustomEntity(p, quad);
        assertTrue(Rig.animated(quadPrev));
        assertFalse(quadPrev.animations.isEmpty());

        CustomEntity slime = new CustomEntity();
        slime.name = "crystal_slime";
        slime.baseAppearance = "slime";
        Model slimePrev = PreviewModels.forCustomEntity(p, slime);
        assertEquals(2, slimePrev.cubes.size());
        assertEquals(1.2, slimePrev.cubes.get(1).inflate);

        CustomEntity proj = new CustomEntity();
        proj.name = "ice_bolt";
        proj.kind = "projectile";
        assertEquals(4, PreviewModels.forCustomEntity(p, proj).cubes.size());

        // 2. Datapack Systems.Mob
        Mob wolfMob = new Mob();
        wolfMob.name = "alpha_wolf";
        wolfMob.base = "minecraft:wolf";
        Model wolfPrev = PreviewModels.forMob(p, wolfMob);
        assertTrue(Rig.animated(wolfPrev));
        assertTrue(wolfPrev.cubes.stream().anyMatch(c -> "snout".equals(c.name)));

        // 3. Full 3D ArmorMaterial set preview
        Model helm3d = new Model();
        helm3d.name = "knight_helm_3d";
        Models.applyTemplate(helm3d, "armor_helmet");
        p.elements.add(helm3d);

        ArmorMaterial am = new ArmorMaterial();
        am.name = "knight_steel";
        am.layer1 = texId;
        am.helmetModel = "knight_helm_3d";
        Model armorSet = PreviewModels.forArmorSet(p, am);
        assertTrue(armorSet.cubes.size() > 6, "Mannequin + helmet + chest/legs/boots layers expected");

        // 4. Item 3D preview (custom model, worn 3D armor, and fallback 3D plate)
        Item wand = new Item();
        wand.name = "crystal_wand";
        wand.model = "crystal_golem";
        assertFalse(PreviewModels.forItem3d(p, wand).cubes.isEmpty());

        Item sword = new Item();
        sword.name = "plain_sword";
        sword.kind = "sword";
        sword.texture = texId;
        Model swordPrev = PreviewModels.forItem3d(p, sword);
        assertEquals(-45.0, swordPrev.cubes.get(0).rotation);
    }

    @Test
    void importedModJarRestoresItemBlockEntityFunctionalityFromBytecodeAndData(@TempDir Path tmp) throws Exception {
        // Compile minimal fake Minecraft API + realistic mod classes to produce genuine JVM .class bytecode
        Path srcDir = tmp.resolve("src");
        Path clsDir = tmp.resolve("classes");
        Files.createDirectories(srcDir);
        Files.createDirectories(clsDir);

        Map<String, String> files = new LinkedHashMap<>();
        files.put("net/minecraft/world/item/Rarity.java",
                "package net.minecraft.world.item; public enum Rarity { COMMON, UNCOMMON, RARE, EPIC }");
        files.put("net/minecraft/world/item/Tiers.java",
                "package net.minecraft.world.item; public enum Tiers { WOOD, STONE, IRON, DIAMOND, GOLD, NETHERITE }");
        files.put("net/minecraft/world/item/Items.java",
                "package net.minecraft.world.item; public final class Items { public static final Item BUCKET = new Item(new Item.Properties()); }");
        files.put("net/minecraft/world/effect/MobEffect.java",
                "package net.minecraft.world.effect; public class MobEffect {}");
        files.put("net/minecraft/world/effect/MobEffects.java",
                "package net.minecraft.world.effect; public final class MobEffects { public static final MobEffect WITHER = new MobEffect(); public static final MobEffect REGENERATION = new MobEffect(); public static final MobEffect JUMP = new MobEffect(); }");
        files.put("net/minecraft/world/effect/MobEffectInstance.java",
                "package net.minecraft.world.effect; public class MobEffectInstance { public MobEffectInstance(MobEffect e, int d, int a) {} }");
        files.put("net/minecraft/world/food/FoodProperties.java", """
                package net.minecraft.world.food;
                import net.minecraft.world.effect.MobEffectInstance;
                import java.util.function.Supplier;
                public class FoodProperties {
                    public static class Builder {
                        public Builder nutrition(int n) { return this; }
                        public Builder saturationMod(float f) { return this; }
                        public Builder alwaysEat() { return this; }
                        public Builder fast() { return this; }
                        public Builder effect(Supplier<MobEffectInstance> s, float c) { return this; }
                        public FoodProperties build() { return new FoodProperties(); }
                    }
                }
                """);
        files.put("net/minecraft/world/item/Item.java", """
                package net.minecraft.world.item;
                import net.minecraft.world.InteractionHand;
                import net.minecraft.world.entity.LivingEntity;
                import net.minecraft.world.entity.player.Player;
                import net.minecraft.world.food.FoodProperties;
                import net.minecraft.world.level.Level;
                public class Item {
                    public static class Properties {
                        public Properties stacksTo(int n) { return this; }
                        public Properties durability(int n) { return this; }
                        public Properties fireResistant() { return this; }
                        public Properties rarity(Rarity r) { return this; }
                        public Properties craftRemainder(Item i) { return this; }
                        public Properties food(FoodProperties f) { return this; }
                    }
                    public Item(Properties p) {}
                    public Object use(Level l, Player p, InteractionHand h) { return null; }
                    public boolean hurtEnemy(ItemStack s, LivingEntity t, LivingEntity a) { return true; }
                }
                """);
        files.put("net/minecraft/world/item/SwordItem.java",
                "package net.minecraft.world.item; public class SwordItem extends Item { public SwordItem(Tiers t, int d, float s, Properties p) { super(p); } }");
        files.put("net/minecraft/world/item/ItemStack.java",
                "package net.minecraft.world.item; public class ItemStack {}");
        files.put("net/minecraft/world/InteractionHand.java",
                "package net.minecraft.world; public enum InteractionHand { MAIN_HAND }");
        files.put("net/minecraft/world/entity/Entity.java",
                "package net.minecraft.world.entity; public class Entity { public int xpReward; }");
        files.put("net/minecraft/world/entity/LivingEntity.java", """
                package net.minecraft.world.entity;
                import net.minecraft.world.effect.MobEffectInstance;
                public class LivingEntity extends Entity {
                    public boolean addEffect(MobEffectInstance m) { return true; }
                    public void heal(float f) {}
                }
                """);
        files.put("net/minecraft/world/entity/Mob.java",
                "package net.minecraft.world.entity; public class Mob extends LivingEntity { public void registerGoals() {} }");
        files.put("net/minecraft/world/entity/PathfinderMob.java",
                "package net.minecraft.world.entity; public class PathfinderMob extends Mob {}");
        files.put("net/minecraft/world/entity/monster/Monster.java",
                "package net.minecraft.world.entity.monster; import net.minecraft.world.entity.*; public class Monster extends PathfinderMob {}");
        files.put("net/minecraft/world/entity/ai/attributes/Attributes.java",
                "package net.minecraft.world.entity.ai.attributes; public final class Attributes { public static final Object MAX_HEALTH = new Object(); public static final Object ATTACK_DAMAGE = new Object(); }");
        files.put("net/minecraft/world/entity/ai/goal/FloatGoal.java",
                "package net.minecraft.world.entity.ai.goal; public class FloatGoal { public FloatGoal(Object o) {} }");
        files.put("net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java",
                "package net.minecraft.world.entity.ai.goal; public class MeleeAttackGoal { public MeleeAttackGoal(Object o, double s, boolean m) {} }");
        files.put("net/minecraft/world/entity/player/ItemCooldowns.java",
                "package net.minecraft.world.entity.player; import net.minecraft.world.item.Item; public class ItemCooldowns { public void addCooldown(Item i, int t) {} }");
        files.put("net/minecraft/world/entity/player/Player.java", """
                package net.minecraft.world.entity.player;
                import net.minecraft.world.entity.LivingEntity;
                public class Player extends LivingEntity {
                    private final ItemCooldowns cd = new ItemCooldowns();
                    public ItemCooldowns getCooldowns() { return cd; }
                    public double getX() { return 0; } public double getY() { return 0; } public double getZ() { return 0; }
                }
                """);
        files.put("net/minecraft/world/level/Level.java",
                "package net.minecraft.world.level; public class Level { public void explode(Object e, double x, double y, double z, float pow, int mode) {} }");
        files.put("net/minecraft/core/BlockPos.java",
                "package net.minecraft.core; public class BlockPos {}");
        files.put("net/minecraft/world/level/block/SoundType.java",
                "package net.minecraft.world.level.block; public enum SoundType { STONE, AMETHYST, METAL }");
        files.put("net/minecraft/world/level/block/state/BlockBehaviour.java", """
                package net.minecraft.world.level.block.state;
                import net.minecraft.world.level.block.SoundType;
                import java.util.function.ToIntFunction;
                public class BlockBehaviour {
                    public static class Properties {
                        public static Properties of() { return new Properties(); }
                        public Properties strength(float h, float r) { return this; }
                        public Properties requiresCorrectToolForDrops() { return this; }
                        public Properties lightLevel(ToIntFunction<Object> fn) { return this; }
                        public Properties sound(SoundType s) { return this; }
                    }
                }
                """);
        files.put("net/minecraft/world/level/block/Block.java", """
                package net.minecraft.world.level.block;
                import net.minecraft.core.BlockPos;
                import net.minecraft.world.entity.Entity;
                import net.minecraft.world.level.Level;
                import net.minecraft.world.level.block.state.BlockBehaviour;
                public class Block extends BlockBehaviour {
                    public Block(BlockBehaviour.Properties p) {}
                    public void stepOn(Level l, BlockPos pos, Object st, Entity e) {}
                }
                """);
        files.put("net/minecraft/world/level/block/FallingBlock.java",
                "package net.minecraft.world.level.block; import net.minecraft.world.level.block.state.BlockBehaviour; public class FallingBlock extends Block { public FallingBlock(BlockBehaviour.Properties p) { super(p); } }");

        // Actual Mod classes to compile into the jar
        files.put("com/example/mystic/FireWandItem.java", """
                package com.example.mystic;
                import net.minecraft.world.InteractionHand;
                import net.minecraft.world.entity.player.Player;
                import net.minecraft.world.item.Item;
                import net.minecraft.world.item.Rarity;
                import net.minecraft.world.level.Level;
                public class FireWandItem extends Item {
                    public FireWandItem() {
                        super(new Item.Properties().stacksTo(1).durability(250).fireResistant().rarity(Rarity.EPIC));
                    }
                    @Override
                    public Object use(Level level, Player player, InteractionHand hand) {
                        player.heal(6.0F);
                        level.explode(player, player.getX(), player.getY(), player.getZ(), 3.0F, 0);
                        player.getCooldowns().addCooldown(this, 40);
                        return null;
                    }
                }
                """);
        files.put("com/example/mystic/WitherBladeItem.java", """
                package com.example.mystic;
                import net.minecraft.world.effect.MobEffectInstance;
                import net.minecraft.world.effect.MobEffects;
                import net.minecraft.world.entity.LivingEntity;
                import net.minecraft.world.item.*;
                public class WitherBladeItem extends SwordItem {
                    public WitherBladeItem() {
                        super(Tiers.DIAMOND, 4, -2.2F, new Item.Properties().durability(1200));
                    }
                    @Override
                    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
                        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 1));
                        return true;
                    }
                }
                """);
        files.put("com/example/mystic/SpringSandBlock.java", """
                package com.example.mystic;
                import net.minecraft.core.BlockPos;
                import net.minecraft.world.effect.MobEffectInstance;
                import net.minecraft.world.effect.MobEffects;
                import net.minecraft.world.entity.Entity;
                import net.minecraft.world.entity.LivingEntity;
                import net.minecraft.world.level.Level;
                import net.minecraft.world.level.block.FallingBlock;
                import net.minecraft.world.level.block.SoundType;
                public class SpringSandBlock extends FallingBlock {
                    public SpringSandBlock() {
                        super(Properties.of().strength(4.5F, 12.0F).requiresCorrectToolForDrops().lightLevel(s -> 9).sound(SoundType.AMETHYST));
                    }
                    @Override
                    public void stepOn(Level level, BlockPos pos, Object state, Entity entity) {
                        if (entity instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.JUMP, 100, 2));
                    }
                }
                """);
        files.put("com/example/mystic/ShadowGolemEntity.java", """
                package com.example.mystic;
                import net.minecraft.world.entity.ai.attributes.Attributes;
                import net.minecraft.world.entity.ai.goal.*;
                import net.minecraft.world.entity.monster.Monster;
                public class ShadowGolemEntity extends Monster {
                    public ShadowGolemEntity() { this.xpReward = 35; }
                    public static void createAttributes() {
                        Object a = Attributes.MAX_HEALTH; double hp = 85.0;
                        Object b = Attributes.ATTACK_DAMAGE; double dmg = 9.5;
                    }
                    @Override
                    public void registerGoals() {
                        new FloatGoal(this);
                        new MeleeAttackGoal(this, 1.1D, false);
                    }
                }
                """);
        files.put("com/example/mystic/MysticRegistry.java", """
                package com.example.mystic;
                import net.minecraft.world.effect.*;
                import net.minecraft.world.food.FoodProperties;
                import net.minecraft.world.item.*;
                import java.util.function.Supplier;
                public class MysticRegistry {
                    static Object reg(String id, Supplier<?> s) { return s.get(); }
                    static {
                        reg("fire_wand", () -> new FireWandItem());
                        reg("wither_blade", () -> new WitherBladeItem());
                        reg("star_berry", () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                                .nutrition(6).saturationMod(0.8F).alwaysEat()
                                .effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 160, 0), 1.0F).build())));
                        reg("spring_sand", () -> new SpringSandBlock());
                    }
                }
                """);

        List<Path> srcPaths = new ArrayList<>();
        for (var en : files.entrySet()) {
            Path p = srcDir.resolve(en.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, en.getValue(), StandardCharsets.UTF_8);
            srcPaths.add(p);
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertNotNull(javac, "System JavaCompiler must be available under JDK 21");
        List<String> jcArgs = new ArrayList<>();
        jcArgs.add("-d");
        jcArgs.add(clsDir.toString());
        for (Path sp : srcPaths) jcArgs.add(sp.toString());
        assertEquals(0, javac.run(null, null, null, jcArgs.toArray(String[]::new)));

        byte[] png16 = Images.png(TextureTemplates.generate("gem", 0xFF9B59B6), 16, 16);
        byte[] armorLayer = Images.png(TextureTemplates.generate("armor_layer_1", 0xFF9B59B6), 64, 32);
        Path jar = tmp.resolve("mystic-mod-1.0.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            // Pack ONLY com/example/mystic/*.class (like a real mod JAR without vanilla classes)
            try (var walk = Files.walk(clsDir.resolve("com"))) {
                for (Path cf : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    String rel = clsDir.relativize(cf).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(rel));
                    zos.write(Files.readAllBytes(cf));
                    zos.closeEntry();
                }
            }
            zipStr(zos, "fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"mystic\",\"version\":\"1.0.0\",\"name\":\"Mystic Mod\",\"depends\":{\"minecraft\":\"1.21.1\"}}");
            zipStr(zos, "assets/mystic/lang/en_us.json", "{\"item.mystic.fire_wand\":\"Fire Wand\",\"item.mystic.wither_blade\":\"Wither Blade\",\"item.mystic.star_berry\":\"Star Berry\",\"block.mystic.spring_sand\":\"Spring Sand\",\"entity.mystic.shadow_golem\":\"Shadow Golem\"}");
            zipStr(zos, "assets/mystic/blockstates/spring_sand.json", "{\"variants\":{\"\":{\"model\":\"mystic:block/spring_sand\"}}}");
            zipStr(zos, "assets/mystic/models/block/spring_sand.json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"mystic:block/spring_sand\"}}");
            zipStr(zos, "assets/mystic/blockstates/mystic_ore.json", "{\"variants\":{\"\":{\"model\":\"mystic:block/mystic_ore\"}}}");
            zipStr(zos, "assets/mystic/models/block/mystic_ore.json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"mystic:block/mystic_ore\"}}");
            zipStr(zos, "assets/mystic/models/item/fire_wand.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mystic:item/fire_wand\"}}");
            zipStr(zos, "assets/mystic/models/item/wither_blade.json", "{\"parent\":\"minecraft:item/handheld\",\"textures\":{\"layer0\":\"mystic:item/wither_blade\"}}");
            zipStr(zos, "assets/mystic/models/item/star_berry.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mystic:item/star_berry\"}}");
            zipStr(zos, "assets/mystic/models/item/cobalt_helmet.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mystic:item/cobalt_helmet\"}}");
            zipStr(zos, "data/minecraft/tags/block/mineable/pickaxe.json", "{\"values\":[\"mystic:mystic_ore\"]}");
            zipStr(zos, "data/minecraft/tags/block/needs_iron_tool.json", "{\"values\":[\"mystic:mystic_ore\"]}");
            zipStr(zos, "data/mystic/loot_table/blocks/mystic_ore.json", "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"mystic:raw_mystic\",\"functions\":[{\"function\":\"minecraft:set_count\",\"count\":{\"min\":1,\"max\":3}},{\"function\":\"minecraft:apply_bonus\",\"enchantment\":\"minecraft:fortune\"}]}]}]}");
            zipStr(zos, "data/mystic/worldgen/configured_feature/mystic_ore.json", "{\"type\":\"minecraft:ore\",\"config\":{\"size\":9,\"discard_chance_on_air_exposure\":0.6,\"targets\":[{\"target\":{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"minecraft:stone_ore_replaceables\"},\"state\":{\"Name\":\"mystic:mystic_ore\"}}]}}");
            zipStr(zos, "data/mystic/worldgen/placed_feature/mystic_ore.json", "{\"feature\":\"mystic:mystic_ore\",\"placement\":[{\"type\":\"minecraft:count\",\"count\":12},{\"type\":\"minecraft:height_range\",\"height\":{\"type\":\"minecraft:trapezoid\",\"min_inclusive\":{\"absolute\":-48},\"max_inclusive\":{\"absolute\":24}}},{\"type\":\"minecraft:surface_relative_threshold_filter\",\"heightmap\":\"OCEAN_FLOOR_WG\",\"max_inclusive\":-7}]}");

            for (String tp : List.of("assets/mystic/textures/item/fire_wand.png", "assets/mystic/textures/item/wither_blade.png",
                    "assets/mystic/textures/item/star_berry.png", "assets/mystic/textures/item/cobalt_helmet.png",
                    "assets/mystic/textures/block/spring_sand.png", "assets/mystic/textures/block/mystic_ore.png",
                    "assets/mystic/textures/entity/shadow_golem.png")) {
                zos.putNextEntry(new ZipEntry(tp));
                zos.write(png16);
                zos.closeEntry();
            }
            zos.putNextEntry(new ZipEntry("assets/mystic/textures/models/armor/cobalt_layer_1.png"));
            zos.write(armorLayer);
            zos.closeEntry();
        }

        ProjectImporter.Report rep = ProjectImporter.importArtifact(jar);
        Project p = rep.project();

        // Fire Wand capabilities restored from .class
        Item wand = (Item) p.byName(ElementType.ITEM, "fire_wand");
        assertNotNull(wand);
        assertEquals(1, wand.maxStack);
        assertEquals(250, wand.maxDamage);
        assertTrue(wand.fireResistant);
        assertEquals("epic", wand.rarity);
        assertEquals(2.0, wand.useCooldown, 0.01);
        assertTrue(wand.onUse.stream().anyMatch(s -> "heal".equals(s.kind) && "6".equals(s.get("amount"))));
        assertTrue(wand.onUse.stream().anyMatch(s -> "explosion".equals(s.kind) && "3".equals(s.get("power"))));

        // Wither Blade sword + wither onHitEntity
        Item blade = (Item) p.byName(ElementType.ITEM, "wither_blade");
        assertNotNull(blade);
        assertEquals("sword", blade.kind);
        assertEquals("diamond", blade.tier);
        assertTrue(blade.onHitEntity.stream().anyMatch(s -> "effect".equals(s.kind) && "minecraft:wither".equals(s.get("effect")) && "6".equals(s.get("seconds")) && "1".equals(s.get("amplifier"))));

        // Star Berry food properties + MobEffects.REGENERATION
        Item berry = (Item) p.byName(ElementType.ITEM, "star_berry");
        assertNotNull(berry);
        assertEquals("food", berry.kind);
        assertEquals(6, berry.nutrition);
        assertEquals(0.8, berry.saturation, 0.05);
        assertTrue(berry.alwaysEdible);
        assertEquals("minecraft:regeneration", berry.foodEffect);

        // Cobalt Helmet + auto-created ArmorMaterial
        Item helm = (Item) p.byName(ElementType.ITEM, "cobalt_helmet");
        assertNotNull(helm);
        assertEquals("helmet", helm.kind);
        assertEquals("cobalt", helm.armorMaterial);
        ArmorMaterial cobalt = (ArmorMaterial) p.byName(ElementType.ARMOR_MATERIAL, "cobalt");
        assertNotNull(cobalt);
        assertFalse(cobalt.layer1.isBlank());

        // Spring Sand block physical properties + onStep jump boost + FallingBlock
        Block sand = (Block) p.byName(ElementType.BLOCK, "spring_sand");
        assertNotNull(sand);
        assertTrue(sand.falling);
        assertEquals(4.5, sand.hardness, 0.05);
        assertEquals(12.0, sand.resistance, 0.05);
        assertEquals(9, sand.light);
        assertTrue(sand.requiresTool);
        assertEquals("amethyst", sand.sound);
        assertTrue(sand.onStep.stream().anyMatch(s -> "effect".equals(s.kind) && "minecraft:jump_boost".equals(s.get("effect"))));

        // Mystic Ore block connected to mining tags, drops, AND worldgen
        Block ore = (Block) p.byName(ElementType.BLOCK, "mystic_ore");
        assertNotNull(ore);
        assertEquals("pickaxe", ore.tool);
        assertEquals("iron", ore.toolLevel);
        assertTrue(ore.requiresTool);
        assertEquals("item", ore.drop);
        assertEquals("mystic:raw_mystic", ore.dropItem);
        assertEquals(1, ore.dropMin);
        assertEquals(3, ore.dropMax);
        assertTrue(ore.fortune);
        assertTrue(ore.oreGen);
        assertEquals(-48, ore.oreMinY);
        assertEquals(24, ore.oreMaxY);
        assertTrue(ore.orePreventSurface);
        assertEquals(7, ore.oreSurfaceDepth);
        assertEquals(0.6, ore.oreDiscardAir, 0.05);

        // Shadow Golem entity attributes, AI goals & skin texture
        CustomEntity golem = (CustomEntity) p.byName(ElementType.ENTITY, "shadow_golem");
        assertNotNull(golem);
        assertEquals("hostile", golem.kind);
        assertFalse(golem.texture.isBlank());
        assertEquals(85.0, golem.health, 0.1);
        assertEquals(9.5, golem.attackDamage, 0.1);
        assertEquals(35, golem.xpReward);
        assertTrue(golem.aiGoals.contains("float"));
        assertTrue(golem.aiGoals.contains("melee"));

        // Verify re-generation actually emits functioning mod code & procedures with preserved capabilities!
        GenContext fab = Generators.generate(p, Target.FABRIC, McVersion.V1_21_1);
        String fireWandCls = fileText(fab, "src/main/java/com/example/mystic/item/FireWandItem.java");
        assertTrue(fireWandCls.contains("ModProcedures"));
        String wandFn = fileText(fab, "src/main/resources/data/mystic/function/items/fire_wand/on_use.mcfunction");
        assertTrue(wandFn.contains("instant_health") && wandFn.contains("ExplosionRadius:3b"));
        String bladeFn = fileText(fab, "src/main/resources/data/mystic/function/items/wither_blade/on_hit.mcfunction");
        assertTrue(bladeFn.contains("minecraft:wither"));
        assertTrue(fileText(fab, "src/main/java/com/example/mystic/block/SpringSandBlock.java").contains("FallingBlock"));
        assertTrue(fileText(fab, "src/main/resources/data/mystic/function/blocks/spring_sand/on_step.mcfunction").contains("jump_boost"));
    }

    @Test
    void importedBedrockMcaddonRestoresItemBlockEntityFunctionality(@TempDir Path tmp) throws Exception {
        Path addon = tmp.resolve("sky_pack.mcaddon");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(addon))) {
            zipStr(zos, "bp/manifest.json", "{\"format_version\":2,\"header\":{\"name\":\"Sky Addon BP\",\"uuid\":\"11111111-1111-1111-1111-111111111111\",\"version\":[1,0,0]},\"modules\":[{\"type\":\"data\",\"uuid\":\"22222222-2222-2222-2222-222222222222\",\"version\":[1,0,0]}]}");
            zipStr(zos, "bp/items/sky_apple.json", "{\"format_version\":\"1.20.50\",\"minecraft:item\":{\"description\":{\"identifier\":\"sky:sky_apple\"},\"components\":{\"minecraft:max_stack_size\":16,\"minecraft:food\":{\"nutrition\":7,\"saturation_modifier\":0.9,\"can_always_eat\":true}}}}");
            zipStr(zos, "bp/blocks/sky_lamp.json", "{\"format_version\":\"1.20.50\",\"minecraft:block\":{\"description\":{\"identifier\":\"sky:sky_lamp\"},\"components\":{\"minecraft:destructible_by_mining\":{\"seconds_to_destroy\":3.2},\"minecraft:light_emission\":14,\"minecraft:friction\":0.85}}}");
            zipStr(zos, "bp/entities/sky_drake.json", "{\"format_version\":\"1.20.50\",\"minecraft:entity\":{\"description\":{\"identifier\":\"sky:sky_drake\"},\"components\":{\"minecraft:health\":{\"max\":65},\"minecraft:movement\":{\"value\":0.34},\"minecraft:attack\":{\"damage\":8},\"minecraft:collision_box\":{\"width\":1.5,\"height\":2.2},\"minecraft:scale\":{\"value\":1.25},\"minecraft:fire_immune\":{}}}}");
        }
        Project p = ProjectImporter.importArtifact(addon).project();
        Item apple = (Item) p.byName(ElementType.ITEM, "sky_apple");
        assertNotNull(apple);
        assertEquals("food", apple.kind);
        assertEquals(16, apple.maxStack);
        assertEquals(7, apple.nutrition);
        assertEquals(0.9, apple.saturation, 0.01);
        assertTrue(apple.alwaysEdible);

        Block lamp = (Block) p.byName(ElementType.BLOCK, "sky_lamp");
        assertNotNull(lamp);
        assertEquals(3.2, lamp.hardness, 0.05);
        assertEquals(14, lamp.light);
        assertEquals(0.85, lamp.friction, 0.01);

        CustomEntity drake = (CustomEntity) p.byName(ElementType.ENTITY, "sky_drake");
        assertNotNull(drake);
        assertEquals(65.0, drake.health, 0.1);
        assertEquals(0.34, drake.speed, 0.01);
        assertEquals(8.0, drake.attackDamage, 0.1);
        assertEquals(1.5, drake.width, 0.01);
        assertEquals(2.2, drake.height, 0.01);
        assertEquals(1.25, drake.scale, 0.01);
        assertTrue(drake.fireImmune);
    }

    private static void zipStr(ZipOutputStream zos, String path, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(path));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
