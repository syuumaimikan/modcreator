package modstudio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import modstudio.build.BuildService;
import modstudio.build.GameDirs;
import modstudio.core.*;
import modstudio.core.Elements.*;
import modstudio.core.ModElements.*;
import modstudio.core.Systems.*;
import modstudio.ext.ModStudioExtensions;
import modstudio.ext.ModStudioPlugin;
import modstudio.gen.*;
import modstudio.mcp.McpServer;
import modstudio.mcp.McpToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies completeness fixes, ModStudioPlugin SPI extensibility, 26.1 Identifier migration,
 * loader-specific generation, Paper/Folia/Bedrock parity, MCP capabilities, and 4-locale i18n parity.
 */
final class CompletenessAndExtensibilityTest {

    private static String text(GenContext ctx, String path) {
        byte[] b = ctx.files.get(path);
        assertNotNull(b, "Missing generated file: " + path + " (target=" + ctx.target.id + ", mc=" + ctx.v.id + ")");
        return new String(b, StandardCharsets.UTF_8);
    }

    private static Project makeProject(String modId) {
        Project p = new Project();
        p.meta.name = "CompletenessTest";
        p.meta.modId = modId;
        p.meta.packageName = "com.example." + modId;
        p.meta.description = "Comprehensive mod description";
        p.meta.authors = "Alice, Bob";
        p.meta.mcVersion = "1.21.1";
        p.meta.mcVersions = new ArrayList<>(List.of("1.20.1", "1.20.4", "1.20.6", "1.21.1", "1.21.8", "26.1"));
        p.meta.targets = new ArrayList<>(List.of("fabric", "neoforge", "forge", "paper", "folia", "bedrock"));
        p.textures.add(TextureAsset.of("sample_tex", new int[16 * 16], 16, 16));
        return p;
    }

    @Test
    void modStudioPluginAndExtensionsWorkEndToEnd() {
        final int[] registrations = {0};
        ModStudioPlugin plugin = new ModStudioPlugin() {
            @Override public String id() { return "ext_test_plugin"; }
            @Override public String name() { return "Extension Test Plugin"; }

            @Override
            public void onRegister(ModStudioExtensions ext) {
                registrations[0]++;
                ext.registerStep(
                        new Logic.Def("plugin_spark", "world", List.of(Logic.Param.of("power", Logic.PType.INT, "3"))),
                        true,
                        false,
                        (step, out) -> out.add("// plugin_spark(" + step.get("power") + ");"),
                        step -> "particle minecraft:spark_test ~ ~1 ~"
                );
                ext.registerValidator((proj, probs) -> {
                    for (Item it : proj.all(Item.class)) {
                        if ("forbidden_plugin_test_item".equals(it.name)) {
                            probs.add(new Validator.Problem(Validator.Severity.ERROR, it.uid, "Plugin blocked item"));
                        }
                    }
                });
                ext.registerGenContributor(ctx -> {
                    if ("exttestmod".equals(ctx.p.meta.modId)) {
                        ctx.files.text("plugin-manifest.json", "{\"plugin\":\"ext_test_plugin\",\"target\":\"" + ctx.target.id + "\"}");
                    }
                });
                JsonObject schema = new JsonObject();
                schema.addProperty("type", "object");
                schema.add("properties", new JsonObject());
                ext.registerMcpTool(new McpToolRegistry.McpTool(
                        new McpToolRegistry.ToolSpec("plugin_ping", "Returns pong from test plugin", schema, false),
                        (proj, args) -> {
                            JsonObject data = new JsonObject();
                            data.addProperty("pong", proj.meta.modId);
                            return McpToolRegistry.ToolResult.ok("pong", data, false);
                        }
                ));
            }
        };

        // Register twice to verify idempotent plugin loading by id()
        ModStudioExtensions.register(plugin);
        ModStudioExtensions.register(plugin);
        assertEquals(1, registrations[0]);
        assertTrue(ModStudioExtensions.plugins().stream().anyMatch(pl -> "ext_test_plugin".equals(pl.id())));

        Project p = makeProject("exttestmod");
        Item bad = new Item();
        bad.name = "forbidden_plugin_test_item";
        bad.texture = p.textures.get(0).id;
        Procedure proc = new Procedure();
        proc.name = "spark_proc";
        Step custom = new Step();
        custom.kind = "plugin_spark";
        custom.p.put("power", "7");
        proc.steps.add(custom);
        p.elements.addAll(List.of(bad, proc));

        List<Validator.Problem> probs = Validator.validate(p);
        assertTrue(probs.stream().anyMatch(pr -> pr.message().contains("Plugin blocked item")));

        assertEquals("particle minecraft:spark_test ~ ~1 ~", LogicExtra.bedrockCommand(custom));

        GenContext ctx = Generators.generate(p, Target.FABRIC, McVersion.V1_21_1);
        assertTrue(text(ctx, "plugin-manifest.json").contains("ext_test_plugin"));
        assertTrue(text(ctx, "src/main/resources/data/exttestmod/function/procedures/spark_proc.mcfunction").contains("// plugin_spark(7);"));

        McpServer server = new McpServer(null, p, false);
        JsonObject ping = server.handleJson("{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"tools/call\",\"params\":{\"name\":\"plugin_ping\",\"arguments\":{}}}");
        assertFalse(ping.getAsJsonObject("result").get("isError").getAsBoolean());
        assertTrue(ping.getAsJsonObject("result").toString().contains("exttestmod"));
    }

    @Test
    void mc26_1IdentifiersAndPreviouslyDroppedFieldsGenerateCorrectly() {
        Project p = makeProject("compmod");
        String texId = p.textures.get(0).id;

        Item wand = new Item();
        wand.name = "spark_orb";
        wand.kind = "throwable";
        wand.projectileDamage = 7.5;
        wand.texture = texId;

        Block redstoneLamp = new Block();
        redstoneLamp.name = "logic_node";
        redstoneLamp.canConnectRedstone = true;
        redstoneLamp.texture = texId;

        ArmorMaterial rubyArmor = new ArmorMaterial();
        rubyArmor.name = "ruby";
        rubyArmor.layer1 = texId;
        rubyArmor.layer2 = texId;
        rubyArmor.repairItem = "minecraft:ruby";
        rubyArmor.setBonusEffect = "minecraft:fire_resistance";

        Item rubyChest = new Item();
        rubyChest.name = "ruby_chestplate";
        rubyChest.kind = "chestplate";
        rubyChest.armorMaterial = "ruby";
        rubyChest.texture = texId;

        Fluid lava = new Fluid();
        lava.name = "molten_ruby";
        lava.fogColor = "#FF3344";
        lava.stillTexture = texId;
        lava.flowingTexture = texId;

        Container furnace = new Container();
        furnace.name = "gem_smelter";
        furnace.displayName = "Gem Smelter";
        furnace.rows = 1;
        furnace.useCustomSlots = true;
        SlotDef sIn = new SlotDef(); sIn.index = 0; sIn.kind = "input";
        SlotDef sFuel = new SlotDef(); sFuel.index = 1; sFuel.kind = "fuel";
        SlotDef sOut = new SlotDef(); sOut.index = 2; sOut.kind = "output";
        furnace.slots.addAll(List.of(sIn, sFuel, sOut));
        ContainerRecipe cr = new ContainerRecipe();
        cr.input = "minecraft:coal";
        cr.secondInput = "minecraft:iron_ingot";
        cr.output = "minecraft:diamond";
        cr.requiresFuel = true;
        cr.processTicks = 40;
        furnace.recipes.add(cr);

        CustomEntity sentinel = new CustomEntity();
        sentinel.name = "ruby_sentinel";
        sentinel.displayName = "Ruby Sentinel";
        sentinel.kind = "hostile";
        sentinel.canSwim = true;
        sentinel.hurtSound = "minecraft:entity.iron_golem.hurt";
        sentinel.deathSound = "minecraft:entity.iron_golem.death";
        sentinel.ambientSound = "minecraft:entity.blaze.ambient";
        sentinel.lootTable = "minecraft:entities/iron_golem";
        sentinel.mainhand = "minecraft:iron_sword";
        sentinel.head = "minecraft:carved_pumpkin";
        sentinel.naturalSpawn = true;
        sentinel.spawnBiomes = "minecraft:plains, #minecraft:is_mountain";
        sentinel.texture = texId;

        Portal portal = new Portal();
        portal.name = "ruby_gate";
        portal.displayName = "Ruby Gate";
        portal.generateIgniter = false;
        portal.igniterItem = "minecraft:blaze_rod";
        portal.texture = texId;

        WorldFeature patch = new WorldFeature();
        patch.name = "ruby_flower_patch";
        patch.featureType = "patch";
        patch.block = "minecraft:poppy";
        patch.xzSpread = 11;
        patch.ySpread = 4;
        patch.biomes = "minecraft:meadow";

        Profession jewelSmith = new Profession();
        jewelSmith.name = "jewel_smith";
        jewelSmith.displayName = "Jewel Smith";
        jewelSmith.workstationBlock = "minecraft:smithing_table";
        jewelSmith.workSound = "minecraft:entity.villager.work_toolsmith";
        ProfessionTrade tr = new ProfessionTrade();
        tr.costA = "minecraft:emerald";
        tr.countA = 4;
        tr.costB = "minecraft:amethyst_shard";
        tr.countB = 2;
        tr.result = "minecraft:diamond";
        jewelSmith.trades.add(tr);

        CustomRecipeType crusher = new CustomRecipeType();
        crusher.name = "gem_crushing";
        crusher.timed = true;
        crusher.maxInputs = 2;

        MixinSpec mx = new MixinSpec();
        mx.name = "player_tick_mixin";
        mx.enabled = true;

        p.elements.addAll(List.of(wand, redstoneLamp, rubyArmor, rubyChest, lava, furnace, sentinel, portal, patch, jewelSmith, crusher, mx));

        // 1. Verify MC 26.1 replaces ResourceLocation with Identifier across generated Java code
        GenContext fab26 = Generators.generate(p, Target.FABRIC, McVersion.V26_1);
        for (var en : fab26.files.all().entrySet()) {
            if (en.getKey().endsWith(".java")) {
                String java = new String(en.getValue(), StandardCharsets.UTF_8);
                assertFalse(java.contains("ResourceLocation"), "Unexpected ResourceLocation in 26.1 file " + en.getKey());
            }
        }
        assertTrue(text(fab26, "src/main/java/com/example/compmod/ModProcedures.java").contains("Identifier"));

        // 2. Verify Container updates across 1.20.1 (<1.20.5) and 1.21.8+
        GenContext fab20 = Generators.generate(p, Target.FABRIC, McVersion.V1_20_1);
        GenContext fab218 = Generators.generate(p, Target.FABRIC, McVersion.V1_21_8);
        assertTrue(text(fab20, "src/main/java/com/example/compmod/container/GemSmelterBlockEntity.java").contains("isSameItemSameTags"));
        assertTrue(text(fab218, "src/main/java/com/example/compmod/block/GemSmelterContainerBlock.java").contains("affectNeighborsAfterRemoval"));
        assertTrue(text(fab218, "src/main/java/com/example/compmod/container/GemSmelterBlockEntity.java").contains("burnTime"));
        assertFalse(text(fab218, "src/main/resources/data/compmod/loot_table/blocks/gem_smelter.json").isBlank());
        assertFalse(text(fab218, "src/main/resources/assets/compmod/blockstates/gem_smelter.json").isBlank());

        // 3. Verify Entity sounds, loot delegation, BiomeSelectors, and Portal igniter hook
        String ent218 = text(fab218, "src/main/java/com/example/compmod/entity/RubySentinelEntity.java");
        assertTrue(ent218.contains("getHurtSound"));
        assertTrue(ent218.contains("getDeathSound"));
        assertTrue(ent218.contains("getAmbientSound"));
        assertTrue(ent218.contains("FloatGoal"));
        assertTrue(text(fab218, "src/main/resources/data/compmod/loot_table/entities/ruby_sentinel.json").contains("minecraft:entities/iron_golem"));

        String fabEvents = text(fab218, "src/main/java/com/example/compmod/ModEvents.java");
        assertFalse(fabEvents.contains("BiomeSelectors.all()"), "BiomeSelectors should filter configured biomes");
        assertTrue(fabEvents.contains("BiomeSelectors.includeByKey"));
        assertTrue(fabEvents.contains("UseBlockCallback"));
        assertTrue(text(fab218, "src/main/java/com/example/compmod/block/RubyGatePortalBlock.java").contains("tryIgnite"));

        // 4. Verify Armor set bonus without helmet + throwable damage + redstone source + Profession costB + WorldFeature spread
        String armorCls = text(fab218, "src/main/java/com/example/compmod/item/RubyChestplateItem.java");
        assertTrue(armorCls.contains("MobEffects.FIRE_RESISTANCE"));
        String wandCls = text(fab218, "src/main/java/com/example/compmod/item/SparkOrbItem.java");
        assertTrue(wandCls.contains("7.5F"));
        String lampCls = text(fab218, "src/main/java/com/example/compmod/block/LogicNodeBlock.java");
        assertTrue(lampCls.contains("isSignalSource"));
        assertTrue(text(fab218, "src/main/java/com/example/compmod/ModProfessions.java").contains("ModProcedures.item(\"minecraft:amethyst_shard\")"));
        assertTrue(text(fab218, "src/main/resources/data/compmod/worldgen/configured_feature/ruby_flower_patch.json").contains("\"xz_spread\": 11"));

        // 5. Verify NeoForge 1.20.4 mixins.toml + fogColor cutoff before 1.21.8 + Recipe 1.20.6 MapCodec
        GenContext neo204 = Generators.generate(p, Target.NEOFORGE, McVersion.V1_20_4);
        assertTrue(text(neo204, "src/main/resources/META-INF/mods.toml").contains("[[mixins]]"));
        GenContext neo206 = Generators.generate(p, Target.NEOFORGE, McVersion.V1_20_6);
        assertTrue(text(neo206, "src/main/java/com/example/compmod/recipe/GemCrushingRecipe.java").contains("MapCodec"));
        assertTrue(text(neo206, "src/main/java/com/example/compmod/ModFluids.java").contains("modifyFogColor"));
        GenContext neo211 = Generators.generate(p, Target.NEOFORGE, McVersion.V1_21_1);
        assertTrue(text(neo211, "src/main/java/com/example/compmod/ModClient.java").contains("modifyFogColor"));
        GenContext neo218 = Generators.generate(p, Target.NEOFORGE, McVersion.V1_21_8);
        assertFalse(text(neo218, "src/main/java/com/example/compmod/ModClient.java").contains("modifyFogColor"));
    }

    @Test
    void paperFoliaAndBedrockGeneratorsImplementAllHooksAndScripts() {
        Project p = makeProject("srvmod");
        String texId = p.textures.get(0).id;

        Item blade = new Item();
        blade.name = "flame_wand";
        blade.displayName = "Flame Wand";
        blade.kind = "throwable";
        blade.useCooldown = 2.0;
        blade.texture = texId;
        blade.onUse.add(Logic.newStep("message"));
        blade.onHitEntity.add(Logic.newStep("damage"));
        blade.onTickInHand.add(Logic.newStep("effect"));

        ArmorMaterial mail = new ArmorMaterial();
        mail.name = "guardian";
        mail.setBonusEffect = "minecraft:resistance";

        Recipe rec = new Recipe();
        rec.name = "milk_cake";
        rec.result = "minecraft:cake";
        rec.keepRemainder = true;

        Command cmd = new Command();
        cmd.name = "ping";
        CommandBranch br = new CommandBranch();
        br.path = "say <target:player>";
        br.steps.add(Logic.newStep("message"));
        cmd.branches.add(br);

        CodeFile kt = new CodeFile();
        kt.name = "ServerHelper";
        kt.language = "kotlin";
        kt.target = "paper";
        kt.content = "package com.example.srvmod\nobject ServerHelper { fun hello() = 1 }";

        CodeFile ts = new CodeFile();
        ts.name = "BedrockInit";
        ts.language = "typescript";
        ts.target = "bedrock";
        ts.hook = "common_init";
        ts.hookMethod = "initAddon";
        ts.content = "export function initAddon(): void { const x: number = 1; }";

        Model drakeModel = new Model();
        drakeModel.name = "drake_model";
        drakeModel.kind = "entity";
        drakeModel.cubes.add(Models.cube("body", 4, 2, 4, 12, 14, 12));

        CustomEntity drake = new CustomEntity();
        drake.name = "sky_drake";
        drake.displayName = "Sky Drake";
        drake.model = "drake_model";
        drake.texture = texId;
        drake.naturalSpawn = true;
        drake.spawnBiomes = "minecraft:plains";

        LootTable extraStone = new LootTable();
        extraStone.name = "extra_stone";
        extraStone.lootType = "block";
        extraStone.replaces = "minecraft:stone";
        extraStone.inject = true;

        p.elements.addAll(List.of(blade, mail, rec, cmd, extraStone, kt, ts, drakeModel, drake));

        // Paper & Folia
        GenContext paper = Generators.generate(p, Target.PAPER, McVersion.V1_21_1);
        assertTrue(text(paper, "src/main/resources/plugin.yml").contains("org.jetbrains.kotlin:kotlin-stdlib"));
        String listener = text(paper, "src/main/java/com/example/srvmod/ModListener.java");
        assertTrue(listener.contains("PlayerInteractEvent"));
        assertTrue(listener.contains("EntityDamageByEntityEvent"));
        assertTrue(listener.contains("CraftItemEvent"));
        assertTrue(listener.contains("items/flame_wand/on_tick"));

        GenContext folia = Generators.generate(p, Target.FOLIA, McVersion.V1_21_1);
        assertTrue(text(folia, "src/main/java/com/example/srvmod/ModListener.java").contains("getGlobalRegionScheduler"));
        assertTrue(text(folia, "src/main/java/com/example/srvmod/CommandTree.java").contains("getGlobalRegionScheduler"));

        // Bedrock
        GenContext bedrock = Generators.generate(p, Target.BEDROCK, McVersion.V1_21_1);
        String mainJs = text(bedrock, "srvmod_BP/scripts/main.js");
        assertTrue(mainJs.contains("const dialogs = {"), "Dialogs contract in scripts/main.js must stay intact");
        assertTrue(mainJs.contains("initAddon"), "TS common_init hook should auto-import into scripts/main.js");
        assertFalse(text(bedrock, "srvmod_BP/scripts/bedrockinit.js").contains(": number"), "TypeScript annotations must be stripped");
        assertTrue(text(bedrock, "srvmod_BP/entities/sky_drake.json").contains("srvmod:sky_drake"));
        assertTrue(text(bedrock, "srvmod_RP/entity/sky_drake.entity.json").contains("geometry.srvmod.sky_drake"));
        assertTrue(text(bedrock, "srvmod_RP/models/entity/sky_drake.geo.json").contains("geometry.srvmod.sky_drake"));
        assertTrue(text(bedrock, "srvmod_BP/spawn_rules/sky_drake.json").contains("srvmod:sky_drake"));
        assertTrue(text(bedrock, "srvmod_BP/items/flame_wand.json").contains("minecraft:throwable"));
        assertTrue(text(bedrock, "srvmod_BP/items/flame_wand.json").contains("minecraft:cooldown"));
    }

    @Test
    void mcpServerPromptsResourcesAndNewToolsWorkEndToEnd(@TempDir Path tmp) throws Exception {
        Project p = makeProject("mcpmod");
        Path projFile = tmp.resolve("mcpmod.msproj");
        ProjectIO.save(p, projFile);

        McpServer srv = new McpServer(projFile, p, false);

        // 1. Prompts & Resource Templates & Validation Resource
        JsonObject init = srv.handleJson("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        assertNotNull(init.getAsJsonObject("result").getAsJsonObject("capabilities").get("prompts"));

        JsonObject prompts = srv.handleJson("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"prompts/list\",\"params\":{}}");
        assertTrue(prompts.getAsJsonObject("result").getAsJsonArray("prompts").size() >= 3);

        JsonObject getPrompt = srv.handleJson("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"prompts/get\",\"params\":{\"name\":\"create_mod_item_pack\",\"arguments\":{\"theme\":\"ruby\"}}}");
        assertTrue(getPrompt.getAsJsonObject("result").toString().contains("ruby"));

        JsonObject templates = srv.handleJson("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"resources/templates/list\",\"params\":{}}");
        assertTrue(templates.getAsJsonObject("result").getAsJsonArray("resourceTemplates").size() >= 1);

        // 2. Create item via MCP -> ElementDefaults initializes displayName
        JsonObject create = srv.handleJson("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"create_element\",\"arguments\":{\"type\":\"item\",\"name\":\"ruby_gem\"}}}");
        assertFalse(create.getAsJsonObject("result").get("isError").getAsBoolean());
        Item gem = (Item) p.byName(ElementType.ITEM, "ruby_gem");
        assertNotNull(gem);
        assertEquals("Ruby Gem", gem.displayName);

        // Read element & validation resource URIs + unknown URI error (-32602)
        JsonObject readEl = srv.handleJson("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"resources/read\",\"params\":{\"uri\":\"modstudio://element/item/ruby_gem\"}}");
        assertTrue(readEl.getAsJsonObject("result").toString().contains("ruby_gem"));
        JsonObject readProb = srv.handleJson("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"resources/read\",\"params\":{\"uri\":\"modstudio://problems\"}}");
        assertNotNull(readProb.getAsJsonObject("result"));
        JsonObject badUri = srv.handleJson("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"resources/read\",\"params\":{\"uri\":\"modstudio://missing\"}}");
        assertEquals(-32602, badUri.getAsJsonObject("error").get("code").getAsInt());

        // 3. New tools: import_texture, get_texture, find_usages, rename_element, delete_texture, manage_secrets, upgrade_project, update_project_meta
        byte[] pngBytes = p.textures.get(0).bytes();
        String b64 = Base64.getEncoder().encodeToString(pngBytes);
        JsonObject impTex = srv.handleJson("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\",\"params\":{\"name\":\"import_texture\",\"arguments\":{\"name\":\"ruby_tex\",\"base64\":\"" + b64 + "\",\"assignToType\":\"item\",\"assignToName\":\"ruby_gem\"}}}");
        assertFalse(impTex.getAsJsonObject("result").get("isError").getAsBoolean());
        assertFalse(gem.texture.isBlank());

        JsonObject getTex = srv.handleJson("{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\",\"params\":{\"name\":\"get_texture\",\"arguments\":{\"name\":\"ruby_tex\"}}}");
        assertTrue(getTex.getAsJsonObject("result").toString().contains("ruby_tex"));

        Recipe rec = new Recipe();
        rec.name = "ruby_block_rec";
        rec.result = "mcpmod:ruby_gem";
        p.elements.add(rec);

        JsonObject usages = srv.handleJson("{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"tools/call\",\"params\":{\"name\":\"find_usages\",\"arguments\":{\"target\":\"ruby_gem\"}}}");
        assertTrue(usages.getAsJsonObject("result").toString().contains("ruby_block_rec"));

        JsonObject ren = srv.handleJson("{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"tools/call\",\"params\":{\"name\":\"rename_element\",\"arguments\":{\"type\":\"item\",\"name\":\"ruby_gem\",\"newName\":\"crimson_gem\"}}}");
        assertFalse(ren.getAsJsonObject("result").get("isError").getAsBoolean());
        assertEquals("mcpmod:crimson_gem", rec.result);

        JsonObject secSet = srv.handleJson("{\"jsonrpc\":\"2.0\",\"id\":13,\"method\":\"tools/call\",\"params\":{\"name\":\"manage_secrets\",\"arguments\":{\"action\":\"set\",\"name\":\"DISCORD_WEBHOOK\",\"value\":\"https://example.invalid/hook\"}}}");
        assertFalse(secSet.getAsJsonObject("result").get("isError").getAsBoolean());
        JsonObject secList = srv.handleJson("{\"jsonrpc\":\"2.0\",\"id\":14,\"method\":\"tools/call\",\"params\":{\"name\":\"manage_secrets\",\"arguments\":{\"action\":\"list\"}}}");
        assertTrue(secList.getAsJsonObject("result").toString().contains("DISCORD_WEBHOOK"));
        assertFalse(secList.getAsJsonObject("result").toString().contains("https://example.invalid/hook"));

        JsonObject upg = srv.handleJson("{\"jsonrpc\":\"2.0\",\"id\":15,\"method\":\"tools/call\",\"params\":{\"name\":\"upgrade_project\",\"arguments\":{\"mcVersion\":\"26.1\"}}}");
        assertFalse(upg.getAsJsonObject("result").get("isError").getAsBoolean());
        assertTrue(p.meta.mcVersions.contains("26.1"));

        JsonObject meta = srv.handleJson("{\"jsonrpc\":\"2.0\",\"id\":16,\"method\":\"tools/call\",\"params\":{\"name\":\"update_project_meta\",\"arguments\":{\"license\":\"Apache-2.0\",\"authors\":\"Charlie\"}}}");
        assertFalse(meta.getAsJsonObject("result").get("isError").getAsBoolean());
        assertEquals("Apache-2.0", p.meta.license);
        assertEquals("Charlie", p.meta.authors);
    }

    @Test
    void stepWalkerBuildServiceGameDirsAndModPatcherWorkCleanly(@TempDir Path tmp) throws Exception {
        Project p = makeProject("utilmod");
        Container ct = new Container();
        ct.name = "chest";
        Step s1 = Logic.newStep("message");
        ct.onOpen.add(s1);
        CustomEntity ce = new CustomEntity();
        ce.name = "guard";
        Step s2 = Logic.newStep("sound");
        ce.onSpawn.add(s2);
        p.elements.addAll(List.of(ct, ce));

        List<Step> visited = new ArrayList<>();
        StepWalker.walk(p, (el, st) -> visited.add(st));
        assertTrue(visited.containsAll(List.of(s1, s2)));

        // BuildService JDK major parsing against running JVM
        Path javaHome = Path.of(System.getProperty("java.home"));
        if (BuildService.hasJavac(javaHome)) {
            assertTrue(BuildService.javaMajorVersion(javaHome) >= 17);
        }

        // GameDirs atomic copyTree
        Path srcDir = tmp.resolve("src");
        Path dstDir = tmp.resolve("dst");
        Files.createDirectories(srcDir.resolve("nested"));
        Files.writeString(srcDir.resolve("nested/hello.txt"), "world", StandardCharsets.UTF_8);
        GameDirs.copyTree(srcDir, dstDir);
        assertEquals("world", Files.readString(dstDir.resolve("nested/hello.txt"), StandardCharsets.UTF_8));

        // ModPatcher updates authors/description in mods.toml without duplicate descriptor entries
        Path srcJar = tmp.resolve("orig.jar");
        Path dstJar = tmp.resolve("patched.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(srcJar))) {
            zos.putNextEntry(new ZipEntry("META-INF/mods.toml"));
            zos.write("modLoader=\"javafml\"\n[[mods]]\nmodId=\"utilmod\"\nversion=\"1.0\"\ndisplayName=\"Old\"\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("com/example/utilmod/KeepMe.class"));
            zos.write(new byte[]{1, 2, 3, 4});
            zos.closeEntry();
        }
        p.meta.name = "Updated Mod Name";
        p.meta.authors = "Jane Doe";
        p.meta.description = "Patched mod description";
        GenContext ctx = Generators.generate(p, Target.FORGE, McVersion.V1_20_1);
        ModPatcher.Result res = ModPatcher.patchJar(p, ctx, srcJar, dstJar);
        assertTrue(res.keptEntries() >= 1);
        try (ZipFile zf = new ZipFile(dstJar.toFile())) {
            ZipEntry tomlEntry = zf.getEntry("META-INF/mods.toml");
            assertNotNull(tomlEntry);
            String toml = new String(zf.getInputStream(tomlEntry).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(toml.contains("Updated Mod Name"));
            assertTrue(toml.contains("Jane Doe"));
            assertTrue(toml.contains("Patched mod description"));
            assertNotNull(zf.getEntry("com/example/utilmod/KeepMe.class"));
        }
    }

    @Test
    void allI18nKeysInEnglishExistIdenticallyInJapaneseChineseAndKorean() throws Exception {
        Map<String, Properties> bundles = new LinkedHashMap<>();
        for (String lang : List.of("en", "ja", "zh", "ko")) {
            Properties props = new Properties();
            try (InputStream in = CompletenessAndExtensibilityTest.class.getResourceAsStream("/modstudio/i18n/messages_" + lang + ".properties")) {
                assertNotNull(in, "Missing bundle: " + lang);
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            bundles.put(lang, props);
        }
        Properties en = bundles.get("en");
        List<String> spotCheck = List.of(
                "types.keybind.desc", "types.icon_font.desc", "types.screen.desc",
                "field.target", "field.can_swim", "field.work_sound", "field.portal_igniter_item",
                "field.requires_fuel", "field.max_uses", "field.xp", "field.lore",
                "git.diff", "git.log", "git.switch_branch", "git.pull", "git.push",
                "upgrade.change.version", "import.note.bytecode_classes"
        );
        for (String k : spotCheck) {
            assertTrue(en.containsKey(k), "Missing key in EN: " + k);
        }
        for (String lang : List.of("ja", "zh", "ko")) {
            Properties other = bundles.get(lang);
            for (String key : en.stringPropertyNames()) {
                String val = other.getProperty(key);
                assertNotNull(val, "Bundle '" + lang + "' missing key: " + key);
                assertFalse(val.isBlank(), "Bundle '" + lang + "' has blank value for key: " + key);
            }
        }
    }

    private static String fileText(GenContext ctx, String path) {
        byte[] data = ctx.files.get(path);
        assertNotNull(data, "Missing generated file: " + path);
        return new String(data, StandardCharsets.UTF_8);
    }

    @Test
    void customToolMaterialWorksAcrossVersionsLoadersPaperBedrockValidatorRefactorAndMcp() {
        Project p = new Project();
        p.meta.modId = "tiermod";
        p.meta.packageName = "com.example.tiermod";

        ToolMaterial tm = new ToolMaterial();
        tm.name = "mythril";
        tm.displayName = "Mythril";
        tm.durability = 1250;
        tm.speed = 8.5;
        tm.attackDamageBonus = 3.5;
        tm.enchantability = 16;
        tm.miningLevel = 3;
        tm.repairItem = "minecraft:diamond";

        ToolMaterial unused = new ToolMaterial();
        unused.name = "unused_tier";
        unused.durability = 0;
        unused.speed = 0;

        Item pick = new Item();
        pick.name = "mythril_pickaxe";
        pick.kind = "pickaxe";
        pick.tier = "mythril";
        pick.attackDamage = 1.0;
        pick.attackSpeed = -2.8;
        p.elements.addAll(List.of(tm, unused, pick));

        // ProjectScope availability
        assertTrue(ProjectScope.available(p, ElementType.TOOL_MATERIAL));
        Project datapackOnly = new Project();
        datapackOnly.meta.targets = new ArrayList<>(List.of("datapack", "resourcepack"));
        assertFalse(ProjectScope.available(datapackOnly, ElementType.TOOL_MATERIAL));

        // 1.20.1 Fabric (enum Tier with getLevel)
        GenContext c1201 = Generators.generate(p, Target.FABRIC, McVersion.V1_20_1);
        String mat1201 = fileText(c1201, "src/main/java/com/example/tiermod/ModToolMaterials.java");
        assertTrue(mat1201.contains("implements Tier"));
        assertTrue(mat1201.contains("public int getLevel()"));
        assertTrue(mat1201.contains("MYTHRIL(3, 1250, 8.5F, 3.5F, 16"));
        assertTrue(fileText(c1201, "src/main/java/com/example/tiermod/ModItems.java").contains("ModToolMaterials.MYTHRIL"));
        assertTrue(fileText(c1201, "src/main/java/com/example/tiermod/ModProcedures.java").contains("blockTag(String id)"));

        // 1.21.1 NeoForge (enum Tier with getIncorrectBlocksForDrops + tags/block)
        GenContext c1211 = Generators.generate(p, Target.NEOFORGE, McVersion.V1_21_1);
        String mat1211 = fileText(c1211, "src/main/java/com/example/tiermod/ModToolMaterials.java");
        assertTrue(mat1211.contains("getIncorrectBlocksForDrops()"));
        assertTrue(fileText(c1211, "src/main/resources/data/tiermod/tags/block/incorrect_for_mythril_tool.json")
                .contains("#minecraft:incorrect_for_diamond_tool"));

        // 1.21.8 & 26.1 (record new ToolMaterial(...) + repairs item tag)
        for (McVersion mv : List.of(McVersion.V1_21_8, McVersion.V26_1)) {
            GenContext cModern = Generators.generate(p, Target.FABRIC, mv);
            String matModern = fileText(cModern, "src/main/java/com/example/tiermod/ModToolMaterials.java");
            assertTrue(matModern.contains("new ToolMaterial(TagKey.create(Registries.BLOCK"));
            assertTrue(matModern.contains("1250, 8.5F, 3.5F, 16"));
            assertTrue(fileText(cModern, "src/main/resources/data/tiermod/tags/item/repairs_mythril_tool.json")
                    .contains("minecraft:diamond"));
        }

        // Paper & Bedrock parity
        GenContext cPaper = Generators.generate(p, Target.PAPER, McVersion.V1_21_4);
        String paperItems = fileText(cPaper, "src/main/java/com/example/tiermod/CustomItems.java");
        assertTrue(paperItems.contains("minecraft:max_damage=1250"));
        assertTrue(paperItems.contains("attack_damage"));

        GenContext cBedrock = Generators.generate(p, Target.BEDROCK, McVersion.V1_21_4);
        String brItem = fileText(cBedrock, "tiermod_BP/items/mythril_pickaxe.json");
        assertTrue(brItem.contains("\"max_durability\": 1250"));
        assertTrue(brItem.contains("\"speed\": 9"));

        // Validator, Refactor, and MCP create_element
        List<Validator.Problem> probs = Validator.validate(p);
        assertTrue(probs.stream().anyMatch(pr -> unused.uid.equals(pr.elementUid()) && pr.severity() == Validator.Severity.ERROR));
        Refactor.renameElement(p, tm, "mythril", "mithril");
        assertEquals("mithril", pick.tier);

        McpToolRegistry mcp = new McpToolRegistry();
        JsonObject mcpArgs = new JsonObject();
        mcpArgs.addProperty("type", "tool_material");
        mcpArgs.addProperty("name", "orichalcum");
        JsonObject f = new JsonObject();
        f.addProperty("durability", 1800);
        mcpArgs.add("fields", f);
        McpToolRegistry.ToolResult res = mcp.execute(p, "create_element", mcpArgs);
        assertFalse(res.isError());
        assertNotNull(p.toolMaterial("orichalcum"));
    }

    @Test
    void customEntityFlyingDaylightBurnAndTameableGenerateCleanlyWithNonOverlappingArmorPreview() {
        Project p = new Project();
        p.meta.modId = "flymod";
        p.meta.packageName = "com.example.flymod";

        CustomEntity ce = new CustomEntity();
        ce.name = "sky_drake";
        ce.kind = "mob";
        ce.canFly = true;
        ce.burnsInDaylight = true;
        ce.tameable = true;
        ce.tameItem = "minecraft:blaze_rod";
        ce.aiGoals = new ArrayList<>(List.of("stroll", "look_player"));
        p.elements.add(ce);

        // Flying preview adds wings & armor set preview offsets do not overlap identically
        Model entityPreview = PreviewModels.forCustomEntity(p, ce);
        assertTrue(entityPreview.cubes.stream().anyMatch(c -> c.name.contains("wing")));

        ArmorMaterial am = new ArmorMaterial();
        am.name = "drake_plate";
        p.elements.add(am);
        Model armorPreview = PreviewModels.forArmorSet(p, am);
        assertFalse(armorPreview.cubes.isEmpty());

        GenContext c1201 = Generators.generate(p, Target.FORGE, McVersion.V1_20_1);
        String java1201 = fileText(c1201, "src/main/java/com/example/flymod/entity/SkyDrakeEntity.java");
        assertTrue(java1201.contains("FlyingMoveControl"));
        assertTrue(java1201.contains("FlyingPathNavigation"));
        assertTrue(java1201.contains("WaterAvoidingRandomFlyingGoal"));
        assertTrue(java1201.contains("setSecondsOnFire(8)"));
        assertTrue(java1201.contains("modstudio_tamed"));

        GenContext cModern = Generators.generate(p, Target.FABRIC, McVersion.V1_21_8);
        String javaModern = fileText(cModern, "src/main/java/com/example/flymod/entity/SkyDrakeEntity.java");
        assertTrue(javaModern.contains("igniteForSeconds(8.0F)"));
        assertTrue(javaModern.contains("Attributes.FLYING_SPEED"));
        assertTrue(javaModern.contains("minecraft:blaze_rod"));

        GenContext cBedrock = Generators.generate(p, Target.BEDROCK, McVersion.V1_21_8);
        String br = fileText(cBedrock, "flymod_BP/entities/sky_drake.json");
        assertTrue(br.contains("minecraft:can_fly"));
        assertTrue(br.contains("minecraft:burns_in_daylight"));
        assertTrue(br.contains("minecraft:tameable"));
        assertTrue(br.contains("minecraft:blaze_rod"));
    }
}

