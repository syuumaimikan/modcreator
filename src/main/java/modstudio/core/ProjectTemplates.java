package modstudio.core;

import modstudio.core.Elements.*;
import modstudio.core.ModElements.*;
import modstudio.core.Systems.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Starter templates tailored to each project kind (mod, datapack, resourcepack, plugin, bedrock). */
public final class ProjectTemplates {
    private ProjectTemplates() {}

    public static final List<String> IDS = List.of(
            "empty", "rpg_aetheria", "ore_gear", "world_mob", "tech_container", "pack_starter",
            "dp_starter", "rp_starter", "plugin_starter", "bedrock_starter"
    );

    /** Templates appropriate for the selected project kind. */
    public static List<String> forKind(ProjectScope.Kind kind) {
        return switch (kind) {
            case DATAPACK -> List.of("empty", "dp_starter", "pack_starter");
            case RESOURCEPACK -> List.of("empty", "rp_starter");
            case PLUGIN -> List.of("empty", "plugin_starter", "dp_starter");
            case BEDROCK -> List.of("empty", "bedrock_starter");
            default -> List.of("empty", "rpg_aetheria", "ore_gear", "world_mob", "tech_container", "pack_starter");
        };
    }

    /** Applies the chosen starter template to a newly initialized project. */
    public static void apply(Project p, String templateId) {
        if (p == null || templateId == null || templateId.equals("empty")) return;
        switch (templateId) {
            case "rpg_aetheria" -> RpgSampleProject.applyInto(p);
            case "ore_gear" -> applyOreGear(p);
            case "world_mob" -> applyWorldMob(p);
            case "tech_container" -> applyTechContainer(p);
            case "pack_starter" -> applyPackStarter(p);
            case "dp_starter" -> applyDatapackStarter(p);
            case "rp_starter" -> applyResourcepackStarter(p);
            case "plugin_starter" -> applyPluginStarter(p);
            case "bedrock_starter" -> applyBedrockStarter(p);
        }
    }

    private static void applyOreGear(Project p) {
        String ns = p.meta.modId;
        int teal = 0xFF1EB8A6;
        TextureAsset gemTex = TextureAsset.of("mythril_gem", TextureTemplates.generate("gem", teal), 16, 16);
        TextureAsset oreTex = TextureAsset.of("mythril_ore", TextureTemplates.generate("ore", teal), 16, 16);
        TextureAsset blockTex = TextureAsset.of("mythril_block", TextureTemplates.generate("block", teal), 16, 16);
        TextureAsset swordTex = TextureAsset.of("mythril_sword", TextureTemplates.generate("sword", teal), 16, 16);
        TextureAsset pickTex = TextureAsset.of("mythril_pickaxe", TextureTemplates.generate("pickaxe", teal), 16, 16);
        TextureAsset helmTex = TextureAsset.of("mythril_helmet", TextureTemplates.generate("helmet", teal), 16, 16);
        TextureAsset layer1 = TextureAsset.of("mythril_armor_1", TextureTemplates.generate("armor_layer1", teal), 64, 32);
        TextureAsset layer2 = TextureAsset.of("mythril_armor_2", TextureTemplates.generate("armor_layer2", teal), 64, 32);
        p.textures.addAll(List.of(gemTex, oreTex, blockTex, swordTex, pickTex, helmTex, layer1, layer2));
        if (p.meta.icon.isBlank()) p.meta.icon = gemTex.id;

        CreativeTab tab = new CreativeTab();
        tab.name = "starter_tab";
        tab.displayName = p.meta.name;
        tab.icon = ns + ":mythril_gem";

        Item gem = new Item();
        gem.name = "mythril_gem";
        gem.displayName = "Mythril Gem";
        gem.texture = gemTex.id;
        gem.creativeTab = "tab:" + tab.name;

        Block ore = new Block();
        ore.name = "mythril_ore";
        ore.displayName = "Mythril Ore";
        ore.texture = oreTex.id;
        ore.hardness = 3.5;
        ore.toolLevel = "iron";
        ore.drop = "item";
        ore.dropItem = ns + ":mythril_gem";
        ore.dropMax = 2;
        ore.oreGen = true;
        ore.creativeTab = "tab:" + tab.name;

        Block blk = new Block();
        blk.name = "mythril_block";
        blk.displayName = "Block of Mythril";
        blk.texture = blockTex.id;
        blk.hardness = 5.0;
        blk.resistance = 6.0;
        blk.sound = "metal";
        blk.toolLevel = "iron";
        blk.creativeTab = "tab:" + tab.name;

        Item sword = new Item();
        sword.name = "mythril_sword";
        sword.displayName = "Mythril Sword";
        sword.kind = "sword";
        sword.tier = "diamond";
        sword.attackDamage = 4;
        sword.texture = swordTex.id;
        sword.creativeTab = "tab:" + tab.name;

        Item pick = new Item();
        pick.name = "mythril_pickaxe";
        pick.displayName = "Mythril Pickaxe";
        pick.kind = "pickaxe";
        pick.tier = "diamond";
        pick.texture = pickTex.id;
        pick.creativeTab = "tab:" + tab.name;

        ArmorMaterial mat = new ArmorMaterial();
        mat.name = "mythril";
        mat.durability = 28;
        mat.helmet = 3;
        mat.chestplate = 7;
        mat.leggings = 6;
        mat.boots = 3;
        mat.toughness = 1.5;
        mat.repairItem = ns + ":mythril_gem";
        mat.layer1 = layer1.id;
        mat.layer2 = layer2.id;

        Item helmet = new Item();
        helmet.name = "mythril_helmet";
        helmet.displayName = "Mythril Helmet";
        helmet.kind = "helmet";
        helmet.armorMaterial = "mythril";
        helmet.texture = helmTex.id;
        helmet.creativeTab = "tab:" + tab.name;

        Recipe smelt = new Recipe();
        smelt.name = "smelt_mythril_ore";
        smelt.recipeType = "smelting";
        smelt.input = ns + ":mythril_ore";
        smelt.result = ns + ":mythril_gem";
        smelt.experience = 1.0;

        Recipe rSword = new Recipe();
        rSword.name = "mythril_sword_craft";
        rSword.recipeType = "shaped";
        rSword.grid = new ArrayList<>(Arrays.asList("", ns + ":mythril_gem", "", "", ns + ":mythril_gem", "", "", "minecraft:stick", ""));
        rSword.result = ns + ":mythril_sword";

        p.elements.addAll(List.of(tab, gem, ore, blk, sword, pick, mat, helmet, smelt, rSword));
    }

    private static void applyWorldMob(Project p) {
        String ns = p.meta.modId;
        int violet = 0xFF8C42F5;
        TextureAsset fluidStill = TextureAsset.of("arcane_still", TextureTemplates.generate("block", violet), 16, 16);
        TextureAsset mobTex = TextureAsset.of("guardian_skin", TextureTemplates.generate("gem", violet), 16, 16);
        TextureAsset frameTex = TextureAsset.of("rift_portal", TextureTemplates.generate("ore", violet), 16, 16);
        p.textures.addAll(List.of(fluidStill, mobTex, frameTex));
        if (p.meta.icon.isBlank()) p.meta.icon = fluidStill.id;

        Fluid fluid = new Fluid();
        fluid.name = "arcane_fluid";
        fluid.displayName = "Arcane Spring";
        fluid.stillTexture = fluidStill.id;
        fluid.flowingTexture = fluidStill.id;
        fluid.tintColor = "#8C42F5";
        fluid.luminance = 10;

        CustomEntity guardian = new CustomEntity();
        guardian.name = "rift_guardian";
        guardian.displayName = "Rift Guardian";
        guardian.kind = "hostile";
        guardian.category = "monster";
        guardian.health = 40;
        guardian.attackDamage = 6;
        guardian.texture = mobTex.id;
        guardian.naturalSpawn = true;
        guardian.aiGoals = new ArrayList<>(List.of("float", "melee", "stroll", "look_player", "attack_nearest_player"));

        Portal portal = new Portal();
        portal.name = "arcane_portal";
        portal.displayName = "Arcane Portal";
        portal.frameBlock = "minecraft:crying_obsidian";
        portal.targetDimension = ns + ":arcane_wilds";
        portal.texture = frameTex.id;
        portal.tintColor = "#8C42F5";

        Biome biome = new Biome();
        biome.name = "arcane_wilds_biome";
        biome.sky = "#2A154A";
        biome.fog = "#512889";
        biome.grass = "#6F3BC2";

        Dimension dim = new Dimension();
        dim.name = "arcane_wilds";
        dim.biomes = new ArrayList<>(List.of(ns + ":arcane_wilds_biome"));

        p.elements.addAll(List.of(fluid, guardian, portal, biome, dim));
    }

    private static void applyTechContainer(Project p) {
        String ns = p.meta.modId;
        int amber = 0xFFD98A2B;
        TextureAsset casingTex = TextureAsset.of("alloy_smelter", TextureTemplates.generate("block", amber), 16, 16);
        TextureAsset ingotTex = TextureAsset.of("bronze_ingot", TextureTemplates.generate("ingot", amber), 16, 16);
        p.textures.addAll(List.of(casingTex, ingotTex));
        if (p.meta.icon.isBlank()) p.meta.icon = ingotTex.id;

        Item ingot = new Item();
        ingot.name = "bronze_ingot";
        ingot.displayName = "Bronze Ingot";
        ingot.texture = ingotTex.id;

        Block blk = new Block();
        blk.name = "alloy_smelter_block";
        blk.displayName = "Alloy Smelter";
        blk.texture = casingTex.id;
        blk.sound = "metal";
        blk.rotatable = true;

        Container c = new Container();
        c.name = "alloy_smelter";
        c.displayName = "Alloy Smelter";
        c.block = ns + ":alloy_smelter_block";
        c.rows = 3;
        c.useCustomSlots = true;
        SlotDef s0 = new SlotDef();
        s0.index = 0; s0.x = 56; s0.y = 17; s0.kind = "input";
        SlotDef s1 = new SlotDef();
        s1.index = 1; s1.x = 56; s1.y = 53; s1.kind = "fuel";
        SlotDef s2 = new SlotDef();
        s2.index = 2; s2.x = 116; s2.y = 35; s2.kind = "output";
        c.slots.addAll(List.of(s0, s1, s2));
        ContainerRecipe cr = new ContainerRecipe();
        cr.input = "minecraft:copper_ingot";
        cr.output = ns + ":bronze_ingot";
        cr.processTicks = 80;
        cr.requiresFuel = true;
        c.recipes.add(cr);

        ModConfig cfg = new ModConfig();
        cfg.name = "tech_settings";
        cfg.displayName = "Tech Settings";
        ConfigEntry ce = new ConfigEntry();
        ce.key = "smelt_ticks";
        ce.kind = "int";
        ce.value = "80";
        ce.min = "10";
        ce.max = "600";
        ce.comment = "Default processing time in ticks";
        cfg.entries.add(ce);

        p.elements.addAll(List.of(ingot, blk, c, cfg));
    }

    private static void applyPackStarter(Project p) {
        Function fn = new Function();
        fn.name = "welcome";
        fn.code = "tellraw @s {\"text\":\"Welcome to " + p.meta.name + "!\",\"color\":\"aqua\"}";
        fn.onLoad = true;

        Advancement adv = new Advancement();
        adv.name = "first_steps";
        adv.title = p.meta.name;
        adv.description = "Begin your adventure";
        adv.icon = "minecraft:compass";
        adv.trigger = "tick";

        Dialog dlg = new Dialog();
        dlg.name = "guide_book";
        dlg.kind = "notice";
        dlg.title = p.meta.name;
        dlg.body = "Welcome! Choose where to start.";
        DialogButton ok = new DialogButton();
        ok.label = "OK";
        dlg.buttons.add(ok);

        p.elements.addAll(List.of(fn, adv, dlg));
        if (!p.has(Target.DATAPACK) || p.has(Target.RESOURCEPACK) || ProjectScope.hasCode(p.targets())) {
            Shader sh = new Shader();
            sh.name = "warm_tint";
            sh.kind = "post";
            sh.program = "phosphor";
            sh.fragment = "#version 150\nuniform sampler2D DiffuseSampler;\nin vec2 texCoord;\nout vec4 fragColor;\nvoid main(){\n    vec4 c = texture(DiffuseSampler, texCoord);\n    fragColor = vec4(c.rgb * vec3(1.06, 1.0, 0.92), c.a);\n}\n";
            p.elements.add(sh);
        }
    }

    private static void applyDatapackStarter(Project p) {
        Function fn = new Function();
        fn.name = "init";
        fn.onLoad = true;
        fn.code = "tellraw @a {\"text\":\"" + p.meta.name + " loaded!\",\"color\":\"green\"}";

        Recipe craft = new Recipe();
        craft.name = "craftable_notch_apple";
        craft.recipeType = "shaped";
        craft.grid = new ArrayList<>(Arrays.asList(
                "minecraft:gold_block", "minecraft:gold_block", "minecraft:gold_block",
                "minecraft:gold_block", "minecraft:apple", "minecraft:gold_block",
                "minecraft:gold_block", "minecraft:gold_block", "minecraft:gold_block"));
        craft.result = "minecraft:enchanted_golden_apple";

        Advancement adv = new Advancement();
        adv.name = "welcome";
        adv.title = p.meta.name;
        adv.description = "Data Pack active in your world";
        adv.icon = "minecraft:writable_book";
        adv.trigger = "tick";

        LootTable loot = new LootTable();
        loot.name = "chests/bonus_stash";
        loot.lootType = "chest";
        LootPool pool = new LootPool();
        LootEntry e1 = new LootEntry();
        e1.name = "minecraft:emerald";
        e1.countMin = 1;
        e1.countMax = 4;
        pool.entries.add(e1);
        loot.pools.add(pool);

        Command cmd = new Command();
        cmd.name = p.meta.modId;
        cmd.description = p.meta.name + " status command";
        Step msg = new Step();
        msg.kind = "message";
        msg.p.put("text", p.meta.name + " is running!");
        cmd.steps.add(msg);

        p.elements.addAll(List.of(fn, craft, adv, loot, cmd));
    }

    private static void applyResourcepackStarter(Project p) {
        TextureAsset icon = TextureAsset.of("pack_emblem", TextureTemplates.generate("gem", 0xFF4BA3E3), 16, 16);
        p.textures.add(icon);
        if (p.meta.icon.isBlank()) p.meta.icon = icon.id;

        Model crystal = new Model();
        crystal.name = "custom_crystal";
        Models.applyTemplate(crystal, "crystal");
        crystal.textures.put("0", icon.id);

        Shader sh = new Shader();
        sh.name = "warm_post";
        sh.kind = "post";
        sh.program = "phosphor";
        sh.mode = "nodes";
        sh.nodes.addAll(ShaderNodes.starter());

        Theme th = new Theme();
        th.name = "custom_theme";
        th.splashes = p.meta.name + "!\nPack ready!";

        IconFont ic = new IconFont();
        ic.name = "emblem";
        Icon ico = new Icon();
        ico.name = "emblem";
        ico.texture = icon.id;
        ic.icons.add(ico);

        p.elements.addAll(List.of(crystal, sh, th, ic));
    }

    private static void applyPluginStarter(Project p) {
        String ns = p.meta.modId;
        TextureAsset badgeTex = TextureAsset.of("server_badge", TextureTemplates.generate("gem", 0xFF3FB950), 16, 16);
        p.textures.add(badgeTex);
        if (p.meta.icon.isBlank()) p.meta.icon = badgeTex.id;

        Item ticket = new Item();
        ticket.name = "vip_token";
        ticket.displayName = "Server Token";
        ticket.baseItem = "minecraft:paper";
        ticket.texture = badgeTex.id;

        Command cmd = new Command();
        cmd.name = ns + "menu";
        cmd.description = "Opens the server menu";
        Step s = new Step();
        s.kind = "message";
        s.p.put("text", "&aWelcome to " + p.meta.name + "!");
        cmd.steps.add(s);

        Dialog welcome = new Dialog();
        welcome.name = "server_rules";
        welcome.kind = "notice";
        welcome.title = p.meta.name;
        welcome.body = "Enjoy your stay on the server!";
        DialogButton ok = new DialogButton();
        ok.label = "OK";
        welcome.buttons.add(ok);

        p.elements.addAll(List.of(ticket, cmd, welcome));
    }

    private static void applyBedrockStarter(Project p) {
        String ns = p.meta.modId;
        TextureAsset gemTex = TextureAsset.of("addon_gem", TextureTemplates.generate("gem", 0xFF2FA4FF), 16, 16);
        TextureAsset blkTex = TextureAsset.of("addon_block", TextureTemplates.generate("block", 0xFF2FA4FF), 16, 16);
        p.textures.addAll(List.of(gemTex, blkTex));
        if (p.meta.icon.isBlank()) p.meta.icon = gemTex.id;

        Item item = new Item();
        item.name = "sky_crystal";
        item.displayName = "Sky Crystal";
        item.texture = gemTex.id;

        Block blk = new Block();
        blk.name = "sky_block";
        blk.displayName = "Sky Block";
        blk.texture = blkTex.id;

        Function fn = new Function();
        fn.name = "welcome";
        fn.onLoad = true;
        fn.code = "say " + p.meta.name + " Add-on ready!";

        Dialog dlg = new Dialog();
        dlg.name = "addon_guide";
        dlg.kind = "notice";
        dlg.title = p.meta.name;
        dlg.body = "Thanks for installing " + ns + "!";
        DialogButton ok = new DialogButton();
        ok.label = "OK";
        dlg.buttons.add(ok);

        p.elements.addAll(List.of(item, blk, fn, dlg));
    }
}
