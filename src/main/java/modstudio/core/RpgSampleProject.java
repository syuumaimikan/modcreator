package modstudio.core;

import modstudio.core.Elements.*;
import modstudio.core.ModElements.*;
import modstudio.core.Systems.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Flagship Action-RPG sample mod: "Aetheria: Astral Resonance".
 * Blends Hypixel Skyblock-style RPG stat gear, chest menus, coins & slayer progression,
 * Genshin Impact-style elemental skills/bursts, resin, waypoint shrines & resonance,
 * and Hytale-inspired floating sky realms, 3D weapons, fluted temples & rigged multi-phase bosses.
 */
public final class RpgSampleProject {
    private RpgSampleProject() {}

    public static Project create() {
        Project p = new Project();
        p.meta.name = "Aetheria: Astral Resonance";
        p.meta.modId = "aetheria";
        p.meta.version = "1.0.0";
        p.meta.packageName = "com.aetheria.rpg";
        p.meta.authors = "Aetheria Studio";
        p.meta.description = "Action-RPG expansion inspired by Hypixel Skyblock, Genshin Impact, and Hytale: elemental abilities, mana & combat leveling, rigged 3D weapons & bosses, floating sky realms, waypoint shrines, relic anvil forging, NPC quests, cutscenes, and custom HUDs.";
        p.meta.mcVersions = new ArrayList<>(List.of("1.21.1"));
        p.normalizeVersions();
        p.meta.targets = new ArrayList<>(List.of("neoforge", "fabric"));
        applyInto(p);
        return p;
    }

    /** Populates any newly initialized mod project with the complete Aetheria Action-RPG suite. */
    public static void applyInto(Project p) {
        final String ns = p.meta.modId;
        if (!p.meta.languages.contains("ja_jp")) p.meta.languages.add("ja_jp");
        RpgSampleArt.addTextures(p);
        p.elements.addAll(RpgSampleArt.buildModels(p));

        CreativeTab combatTab = new CreativeTab();
        combatTab.name = "aetheria_combat";
        combatTab.displayName = "Aetheria: Weapons & Armor";
        combatTab.setLoc("name", "ja_jp", "Aetheria：武器と防具");
        combatTab.icon = ns + ":astral_greatsword";

        CreativeTab worldTab = new CreativeTab();
        worldTab.name = "aetheria_world";
        worldTab.displayName = "Aetheria: Realms & Relics";
        worldTab.setLoc("name", "ja_jp", "Aetheria：領域と聖遺物");
        worldTab.icon = ns + ":waypoint_shrine";
        p.elements.addAll(List.of(combatTab, worldTab));

        gameplayAndMagic(p, ns, combatTab.name);
        itemsAndGear(p, ns, combatTab.name, worldTab.name);
        blocksAndWorld(p, ns, worldTab.name);
        mobsAndBoss(p, ns, worldTab.name);
        progressionAndEconomy(p, ns);
        interfaceAndAbilities(p, ns);
        visualsAndConfig(p, ns);
    }

    // ------------------------------------------------------------------ 1. Magic, Enchantments, Potions, Effects & Media

    private static void gameplayAndMagic(Project p, String ns, String tab) {
        StatusEffect surge = new StatusEffect();
        surge.name = "starlight_surge";
        surge.displayName = "Starlight Surge";
        surge.setLoc("name", "ja_jp", "星光の奔流");
        surge.color = "#56D8FF";
        surge.icon = texId(p, "starlight_surge_icon");
        surge.modifiers.add(effMod("attack_damage", 2.0, "add_value"));
        surge.modifiers.add(effMod("movement_speed", 0.20, "add_multiplied_total"));
        surge.tickInterval = 15;
        surge.onTick.add(step("particle", "particle", "minecraft:end_rod", "count", "4", "dy", "0.7"));
        surge.potionName = "Potion of Starlight Surge";
        surge.setLoc("potion", "ja_jp", "星光奔流のポーション");
        surge.potionSeconds = 180;

        StatusEffect anemo = new StatusEffect();
        anemo.name = "anemo_blessing";
        anemo.displayName = "Anemo Blessing";
        anemo.setLoc("name", "ja_jp", "翠風の加護");
        anemo.color = "#2ED8B6";
        anemo.icon = texId(p, "anemo_blessing_icon");
        anemo.modifiers.add(effMod("movement_speed", 0.25, "add_multiplied_total"));
        anemo.potion = false;

        Potion elixir = new Potion();
        elixir.name = "elixir_of_ascension";
        elixir.displayName = "Elixir of Ascension";
        elixir.setLoc("name", "ja_jp", "突破の秘薬");
        elixir.color = "#56D8FF";
        elixir.effects.add(potEff(ns + ":starlight_surge", 150, 1));
        elixir.effects.add(potEff("minecraft:regeneration", 30, 1));
        elixir.effects.add(potEff("minecraft:absorption", 120, 1));

        Enchantment resonance = new Enchantment();
        resonance.name = "astral_resonance";
        resonance.displayName = "Astral Resonance";
        resonance.setLoc("name", "ja_jp", "星辰共鳴");
        resonance.supportedItems = "#minecraft:enchantable/sword";
        resonance.maxLevel = 5;
        resonance.damagePerLevel = 1.5;
        resonance.onHit.add(step("particle", "particle", "minecraft:end_rod", "count", "10", "dy", "0.5"));
        resonance.onHit.add(step("effect", "effect", "minecraft:glowing", "seconds", "4"));

        Enchantment ward = new Enchantment();
        ward.name = "gale_ward";
        ward.displayName = "Gale Ward";
        ward.setLoc("name", "ja_jp", "翠風の守護");
        ward.supportedItems = "#minecraft:enchantable/armor";
        ward.slot = "armor";
        ward.maxLevel = 4;
        ward.protectionPerLevel = 1.25;

        DamageType cleaveDmg = new DamageType();
        cleaveDmg.name = "astral_cleave";
        cleaveDmg.deathMessage = "%1$s was erased by astral resonance";
        cleaveDmg.deathMessageAttacker = "%1$s fell to %2$s's Starfall Cleave";
        cleaveDmg.setLoc("death", "ja_jp", "%1$s は星辰の共鳴に呑まれた");
        cleaveDmg.setLoc("death_attacker", "ja_jp", "%1$s は %2$s の星落の一閃に散った");
        cleaveDmg.magic = true;

        Painting vista = new Painting();
        vista.name = "celestia_vista";
        vista.title = "Celestia Horizon";
        vista.setLoc("title", "ja_jp", "空島の地平線");
        vista.author = "Aetheria Guild";
        vista.texture = texId(p, "celestia_vista");
        vista.width = 2;
        vista.height = 1;

        Song song = new Song();
        song.name = "song_of_celestia";
        song.description = "Hymn of the Astral Sanctuary";
        song.setLoc("description", "ja_jp", "星辰聖域の讃歌");
        song.sound = "minecraft:music_disc.otherside";
        song.lengthSeconds = 192;
        song.comparatorOutput = 8;

        Item disc = new Item();
        disc.name = "disc_celestia";
        disc.displayName = "Astral Resonance Disc";
        disc.setLoc("name", "ja_jp", "星辰共鳴のレコード");
        disc.texture = texId(p, "disc_celestia");
        disc.maxStack = 1;
        disc.rarity = "rare";
        disc.song = song.name;
        disc.baseItem = "minecraft:music_disc_otherside";
        disc.creativeTab = "tab:" + tab;

        p.elements.addAll(List.of(surge, anemo, elixir, resonance, ward, cleaveDmg, vista, song, disc));
    }

    // ------------------------------------------------------------------ 2. Weapons, Relics, Armor & Items

    private static void itemsAndGear(Project p, String ns, String combatTab, String worldTab) {
        // Crafting Materials & Currencies
        Item shard = simpleItem(p, "astral_shard", "Astral Shard", "星辰の欠片", "rare", "tab:" + worldTab,
                "&9✦ Rare Catalyst", "&7Crystallized starlight mined deep beneath Celestia.");
        Item pyro = simpleItem(p, "pyro_core", "Pyro Ember Core", "炎核の宝玉", "rare", "tab:" + worldTab,
                "&c✦ Elemental Core [Pyro]", "&7Pulses with dormant solar flame.");
        Item feather = simpleItem(p, "anemo_feather", "Anemo Zephyr Plume", "翠風の羽根", "uncommon", "tab:" + worldTab,
                "&a✦ Elemental Relic [Anemo]", "&7Light as drifting cloudstone wind.");

        Item essence = simpleItem(p, "primordial_essence", "Primordial Essence", "原初の星精", "epic", "tab:" + worldTab,
                "&5✧ MYTHIC BOSS DROP", "&7Extracted from defeated Astral Wardens.");
        essence.glint = true;

        Item coin = simpleItem(p, "celestial_coin", "Celestial Coin", "星界の金貨", "uncommon", "tab:" + worldTab,
                "&6✪ Guild Currency (+50 Coins)", "&eRIGHT CLICK &7to deposit into your pouch.");
        coin.onUse.add(step("score", "op", "add", "objective", "aetheria_coins", "value", "50"));
        coin.onUse.add(step("actionbar", "text", "&6+50 Celestial Coins deposited!", "color", "gold"));
        coin.onUse.add(step("sound", "sound", "minecraft:entity.experience_orb.pickup", "pitch", "1.35"));
        coin.onUse.add(step("clear", "item", ns + ":celestial_coin", "count", "1"));

        Item resin = simpleItem(p, "starlight_resin", "Condensed Starlight Resin", "濃縮星光樹脂", "rare", "tab:" + worldTab,
                "&b☽ Domain Treasure Key", "&eRIGHT CLICK &7to claim &b+60 Mana &7and Dungeon Loot.");
        resin.maxStack = 16;
        resin.glint = true;
        resin.onUse.add(step("score", "op", "add", "objective", "aetheria_mana", "value", "60"));
        resin.onUse.add(step("score_clamp", "objective", "aetheria_mana", "min", "0", "max", "100"));
        resin.onUse.add(step("give_loot", "target", "@s", "table", "chests/celestia_shrine"));
        resin.onUse.add(step("particle_burst", "particle", "minecraft:end_rod", "count", "24", "speed", "0.35"));
        resin.onUse.add(step("sound", "sound", "minecraft:block.amethyst_cluster.chime", "pitch", "1.2"));
        resin.onUse.add(step("clear", "item", ns + ":starlight_resin", "count", "1"));

        // Mythic Weapons with 3D Models & Abilities
        Item greatsword = new Item();
        greatsword.name = "astral_greatsword";
        greatsword.displayName = "Astral Greatsword";
        greatsword.setLoc("name", "ja_jp", "星落の大剣");
        greatsword.kind = "sword";
        greatsword.tier = "netherite";
        greatsword.attackDamage = 9;
        greatsword.attackSpeed = -2.2;
        greatsword.rarity = "epic";
        greatsword.fireResistant = true;
        greatsword.glint = true;
        greatsword.texture = texId(p, "astral_greatsword");
        greatsword.model = "astral_greatsword";
        greatsword.baseItem = "minecraft:netherite_sword";
        greatsword.creativeTab = "tab:" + combatTab;
        greatsword.tooltip = "&5★★★★★ &dMYTHIC GREATSWORD\n&7Attack Damage: &c+13 &7| Mana Regen: &b+10/hit\n&eItem Ability: Starfall Cleave &6&lRIGHT CLICK\n&7Costs &b25 Mana&7 to shatter nearby foes.";
        greatsword.shiftTooltip = "&8Forged from fallen comets.\n&7Restores Mana on strike and triggers &bStarlight Surge&7.";
        Step slashCheck = Logic.newStep("if");
        slashCheck.cond = Logic.newCondition("score");
        slashCheck.cond.p.put("objective", "aetheria_mana");
        slashCheck.cond.p.put("min", "25");
        slashCheck.then.add(step("score", "op", "remove", "objective", "aetheria_mana", "value", "25"));
        slashCheck.then.add(step("effect", "effect", ns + ":starlight_surge", "seconds", "8", "amplifier", "1"));
        slashCheck.then.add(step("particle_circle", "particle", "minecraft:end_rod", "radius", "3.5", "points", "28", "dy", "0.8"));
        Step slashHit = step("as_entities", "selector", "@e[type=!minecraft:player,distance=..5.5]");
        slashHit.then.add(step("damage", "target", "@s", "amount", "8", "damageType", ns + ":astral_cleave"));
        slashHit.then.add(step("push_away", "target", "@s", "strength", "1.1"));
        slashCheck.then.add(slashHit);
        slashCheck.then.add(step("sound", "sound", "minecraft:entity.player.attack.sweep", "pitch", "0.75"));
        slashCheck.then.add(step("actionbar", "text", "&b✦ Starfall Cleave activated! &7(-25 Mana)", "color", "aqua"));
        slashCheck.orElse.add(step("actionbar", "text", "&cInsufficient Mana (requires 25 Mana)", "color", "red"));
        greatsword.onUse.add(slashCheck);
        greatsword.onHitEntity.add(step("score", "op", "add", "objective", "aetheria_mana", "value", "10"));
        greatsword.onHitEntity.add(step("score_clamp", "objective", "aetheria_mana", "min", "0", "max", "100"));

        Item galeStaff = new Item();
        galeStaff.name = "gale_staff";
        galeStaff.displayName = "Skyforged Gale Staff";
        galeStaff.setLoc("name", "ja_jp", "天翔の翠風杖");
        galeStaff.maxStack = 1;
        galeStaff.rarity = "rare";
        galeStaff.texture = texId(p, "gale_staff");
        galeStaff.model = "gale_staff";
        galeStaff.baseItem = "minecraft:blaze_rod";
        galeStaff.creativeTab = "tab:" + combatTab;
        galeStaff.tooltip = "&b★★★★☆ &3ANEMO CATALYST\n&eItem Ability: Zephyr Updraft &6&lRIGHT CLICK\n&7Calls an Anemo cyclone & boosts agility &7(&b15 Mana&7).";
        Step galeCheck = Logic.newStep("if");
        galeCheck.cond = Logic.newCondition("score");
        galeCheck.cond.p.put("objective", "aetheria_mana");
        galeCheck.cond.p.put("min", "15");
        galeCheck.then.add(step("score", "op", "remove", "objective", "aetheria_mana", "value", "15"));
        galeCheck.then.add(step("effect", "effect", ns + ":anemo_blessing", "seconds", "8", "amplifier", "1"));
        galeCheck.then.add(step("effect", "effect", "minecraft:slow_falling", "seconds", "6", "amplifier", "0"));
        galeCheck.then.add(step("push", "target", "@s", "dx", "0", "dy", "0.65", "dz", "0"));
        galeCheck.then.add(step("particle_circle", "particle", "minecraft:cloud", "radius", "2.5", "points", "20", "dy", "0.3"));
        galeCheck.then.add(step("sound", "sound", "minecraft:entity.illusioner.cast_spell", "pitch", "1.4"));
        galeCheck.orElse.add(step("actionbar", "text", "&cInsufficient Mana (requires 15 Mana)", "color", "red"));
        galeStaff.onUse.add(galeCheck);

        Item emberBow = new Item();
        emberBow.name = "ember_bow";
        emberBow.displayName = "Solaris Ember Bow";
        emberBow.setLoc("name", "ja_jp", "太陽の熾火弓");
        emberBow.kind = "bow";
        emberBow.durability = 1200;
        emberBow.rarity = "rare";
        emberBow.fireResistant = true;
        emberBow.texture = texId(p, "ember_bow");
        emberBow.baseItem = "minecraft:bow";
        emberBow.creativeTab = "tab:" + combatTab;
        emberBow.tooltip = "&c★★★★☆ PYRO LONGBOW\n&7Channel solar embers along drawn arrows.";
        emberBow.onTickInHand.add(step("particle", "particle", "minecraft:flame", "count", "2", "dy", "0.9"));

        Item scythe = new Item();
        scythe.name = "void_scythe";
        scythe.displayName = "Abyssal Void Scythe";
        scythe.setLoc("name", "ja_jp", "深淵の虚無鎌");
        scythe.kind = "sword";
        scythe.tier = "diamond";
        scythe.attackDamage = 7;
        scythe.attackSpeed = -2.0;
        scythe.rarity = "epic";
        scythe.texture = texId(p, "void_scythe");
        scythe.baseItem = "minecraft:diamond_sword";
        scythe.creativeTab = "tab:" + combatTab;
        scythe.tooltip = "&5★★★★★ VOID REAPER\n&7Drains &c+2 HP &7and weakens enemies on hit.";
        scythe.onHitEntity.add(step("heal", "target", "@s", "amount", "2"));
        scythe.onHitEntity.add(step("particle", "particle", "minecraft:witch", "count", "10", "dy", "0.7"));

        Item shield = new Item();
        shield.name = "chrono_shield";
        shield.displayName = "Aegis of Chronos";
        shield.setLoc("name", "ja_jp", "刻の時盾アイギス");
        shield.kind = "shield";
        shield.durability = 950;
        shield.rarity = "rare";
        shield.texture = texId(p, "chrono_shield");
        shield.baseItem = "minecraft:shield";
        shield.creativeTab = "tab:" + combatTab;
        shield.tooltip = "&9★★★★☆ RELIC BULWARK\n&7Grants momentary Resistance when raised.";

        Item pickaxe = new Item();
        pickaxe.name = "aetheria_pickaxe";
        pickaxe.displayName = "Starforged Breaker";
        pickaxe.setLoc("name", "ja_jp", "星鍛の採掘鎬");
        pickaxe.kind = "pickaxe";
        pickaxe.tier = "netherite";
        pickaxe.attackDamage = 4;
        pickaxe.attackSpeed = -2.6;
        pickaxe.rarity = "rare";
        pickaxe.texture = texId(p, "aetheria_pickaxe");
        pickaxe.baseItem = "minecraft:netherite_pickaxe";
        pickaxe.creativeTab = "tab:" + combatTab;
        pickaxe.tooltip = "&b✦ Mining Fortune &7+50  |  &7Breaks Celestite rapidly.";

        // Valkyrie Armor Set + Trim + Banner
        ArmorMaterial valkyrie = new ArmorMaterial();
        valkyrie.name = "valkyrie";
        valkyrie.durability = 36;
        valkyrie.helmet = 4;
        valkyrie.chestplate = 9;
        valkyrie.leggings = 7;
        valkyrie.boots = 4;
        valkyrie.toughness = 2.5;
        valkyrie.knockbackResistance = 0.1;
        valkyrie.enchantability = 20;
        valkyrie.equipSound = "netherite";
        valkyrie.repairItem = ns + ":astral_shard";
        valkyrie.layer1 = texId(p, "valkyrie_layer_1");
        valkyrie.layer2 = texId(p, "valkyrie_layer_2");
        p.elements.add(valkyrie);

        String[][] armorDefs = {
                {"relic_crown", "helmet", "Valkyrie Relic Crown", "ワルキューレの聖冠", "relic_crown"},
                {"relic_cuirass", "chestplate", "Valkyrie Relic Cuirass", "ワルキューレの胸甲", "relic_cuirass"},
                {"relic_tassets", "leggings", "Valkyrie Relic Tassets", "ワルキューレの脚甲", "relic_tassets"},
                {"relic_greaves", "boots", "Valkyrie Relic Greaves", "ワルキューレの具足", "relic_greaves"}
        };
        for (String[] a : armorDefs) {
            Item pc = new Item();
            pc.name = a[0];
            pc.kind = a[1];
            pc.armorMaterial = valkyrie.name;
            pc.displayName = a[2];
            pc.setLoc("name", "ja_jp", a[3]);
            pc.rarity = "epic";
            pc.fireResistant = true;
            pc.maxStack = 1;
            pc.texture = texId(p, a[4]);
            pc.creativeTab = "tab:" + combatTab;
            pc.tooltip = "&5✦ VALKYRIE RELIC SET\n&7Mana Efficiency & Starlight Resilience.";
            p.elements.add(pc);
        }

        ArmorTrim crestTrim = new ArmorTrim();
        crestTrim.name = "celestial_crest";
        crestTrim.displayName = "Celestial Crest";
        crestTrim.generateTemplate = true;
        crestTrim.texture = texId(p, "celestial_crest_trim");

        BannerPattern banner = new BannerPattern();
        banner.name = "astral_banner";
        banner.displayName = "Astral Crest";
        banner.texture = texId(p, "astral_banner_pattern");

        // Fantasy Foods (Genshin recovery dishes)
        Item tart = new Item();
        tart.name = "sun_nectar_tart";
        tart.displayName = "Golden Nectar Tart";
        tart.setLoc("name", "ja_jp", "黄金蜜のタルト");
        tart.kind = "food";
        tart.nutrition = 8;
        tart.saturation = 0.9;
        tart.alwaysEdible = true;
        tart.rarity = "rare";
        tart.texture = texId(p, "sun_nectar_tart");
        tart.baseItem = "minecraft:golden_carrot";
        tart.creativeTab = "tab:" + worldTab;
        tart.tooltip = "&6★★★✩ Delicious Dish\n&7Restores hunger, Stamina & &b+30 Mana&7.";

        Item berry = new Item();
        berry.name = "celestial_fruit";
        berry.displayName = "Celestial Cloudberry";
        berry.setLoc("name", "ja_jp", "天界の雲実");
        berry.kind = "food";
        berry.nutrition = 4;
        berry.saturation = 0.55;
        berry.alwaysEdible = true;
        berry.texture = texId(p, "celestial_fruit");
        berry.baseItem = "minecraft:sweet_berries";
        berry.creativeTab = "tab:" + worldTab;

        p.elements.addAll(List.of(shard, pyro, feather, essence, coin, resin,
                greatsword, galeStaff, emberBow, scythe, shield, pickaxe,
                crestTrim, banner, tart, berry));
    }

    // ------------------------------------------------------------------ 3. Blocks, Fluids, 3D Models, Biomes, Dimension & Portal

    private static void blocksAndWorld(Project p, String ns, String worldTab) {
        Block ore = new Block();
        ore.name = "celestite_ore";
        ore.displayName = "Celestite Ore";
        ore.setLoc("name", "ja_jp", "天青石鉱石");
        ore.texture = texId(p, "celestite_ore");
        ore.hardness = 3.5;
        ore.resistance = 5.0;
        ore.tool = "pickaxe";
        ore.toolLevel = "iron";
        ore.requiresTool = true;
        ore.light = 4;
        ore.drop = "item";
        ore.dropItem = ns + ":astral_shard";
        ore.dropMin = 1;
        ore.dropMax = 3;
        ore.oreGen = true;
        ore.oreSize = 7;
        ore.oreCount = 8;
        ore.oreMinY = -48;
        ore.oreMaxY = 48;
        ore.creativeTab = "tab:" + worldTab;

        Block marble = new Block();
        marble.name = "runed_marble";
        marble.displayName = "Runed Temple Marble";
        marble.setLoc("name", "ja_jp", "ルーン彫りの大理石");
        marble.texture = texId(p, "runed_marble");
        marble.hardness = 2.5;
        marble.resistance = 8.0;
        marble.tool = "pickaxe";
        marble.toolLevel = "iron";
        marble.requiresTool = true;
        marble.light = 6;
        marble.creativeTab = "tab:" + worldTab;

        Block pillar = new Block();
        pillar.name = "celestial_pillar";
        pillar.displayName = "Celestial Sanctuary Pillar";
        pillar.setLoc("name", "ja_jp", "聖域の円柱");
        pillar.shape = "pillar";
        pillar.textureTop = texId(p, "celestial_pillar_top");
        pillar.textureSide = texId(p, "celestial_pillar_side");
        pillar.hardness = 2.5;
        pillar.resistance = 7.0;
        pillar.tool = "pickaxe";
        pillar.toolLevel = "iron";
        pillar.creativeTab = "tab:" + worldTab;

        // Genshin-style Teleport Waypoint Shrine (3D Model Block + Heal/Refill on right-click!)
        Block waypoint = new Block();
        waypoint.name = "waypoint_shrine";
        waypoint.displayName = "Astral Waypoint Shrine";
        waypoint.setLoc("name", "ja_jp", "星辰のワープポイント");
        waypoint.shape = "model";
        waypoint.model = "waypoint_shrine";
        waypoint.rotatable = true;
        waypoint.hardness = 4.0;
        waypoint.resistance = 12.0;
        waypoint.light = 15;
        waypoint.tool = "pickaxe";
        waypoint.toolLevel = "iron";
        waypoint.creativeTab = "tab:" + worldTab;
        waypoint.onUse.add(step("heal", "target", "@s", "amount", "20"));
        waypoint.onUse.add(step("score", "op", "set", "objective", "aetheria_mana", "value", "100"));
        waypoint.onUse.add(step("spawnpoint", "target", "@s"));
        waypoint.onUse.add(step("particle_circle", "particle", "minecraft:end_rod", "radius", "2.0", "points", "24", "dy", "0.5"));
        waypoint.onUse.add(step("sound", "sound", "minecraft:block.beacon.activate", "pitch", "1.3"));
        waypoint.onUse.add(step("actionbar", "text", "&b✦ Waypoint Harmonized: HP & Mana Restored, Respawn Set!", "color", "aqua"));
        waypoint.onUse.add(step("open_menu", "menu", "astral_hub", "target", "@s"));

        // Hypixel/RPG Relic Forge (Container Block + Custom Recipes!)
        Block forgeBlock = new Block();
        forgeBlock.name = "relic_forge";
        forgeBlock.displayName = "Astral Relic Forge";
        forgeBlock.setLoc("name", "ja_jp", "星霊の遺物鍛冶台");
        forgeBlock.shape = "cube_bottom_top";
        forgeBlock.textureTop = texId(p, "relic_forge_top");
        forgeBlock.textureSide = texId(p, "relic_forge_side");
        forgeBlock.textureBottom = texId(p, "relic_forge_bottom");
        forgeBlock.hardness = 4.0;
        forgeBlock.resistance = 12.0;
        forgeBlock.light = 9;
        forgeBlock.sound = "anvil";
        forgeBlock.tool = "pickaxe";
        forgeBlock.toolLevel = "iron";
        forgeBlock.creativeTab = "tab:" + worldTab;

        Container forge = new Container();
        forge.name = "relic_forge";
        forge.displayName = "Astral Relic Forge";
        forge.setLoc("name", "ja_jp", "星霊の遺物鍛冶台");
        forge.block = ns + ":relic_forge";
        forge.rows = 3;
        forge.useCustomSlots = true;
        forge.slots.add(slotDef(0, 44, 22, "input"));
        forge.slots.add(slotDef(1, 44, 48, "fuel"));
        forge.slots.add(slotDef(2, 116, 35, "output"));
        forge.recipes.add(cRecipe("minecraft:netherite_ingot", ns + ":astral_shard", ns + ":primordial_essence", 1, 80));
        forge.recipes.add(cRecipe(ns + ":astral_shard", ns + ":pyro_core", ns + ":starlight_resin", 2, 60));
        forge.recipes.add(cRecipe(ns + ":primordial_essence", "minecraft:diamond_sword", ns + ":astral_greatsword", 1, 100));
        forge.onCraft.add(step("sound", "sound", "minecraft:block.anvil.use", "pitch", "1.2"));

        CustomRecipeType infusionType = new CustomRecipeType();
        infusionType.name = "arcane_infusion";
        infusionType.displayName = "Arcane Infusion";

        // 4-Stage Celestial Crop
        Block blossom = new Block();
        blossom.name = "celestial_blossom";
        blossom.displayName = "Celestial Blossom Seeds";
        blossom.setLoc("name", "ja_jp", "天界花の種");
        blossom.shape = "crop";
        blossom.tool = "none";
        blossom.requiresTool = false;
        blossom.hardness = 0;
        blossom.resistance = 0;
        blossom.sound = "crop";
        blossom.light = 6;
        blossom.texture = texId(p, "celestial_blossom_seeds");
        blossom.creativeTab = "tab:" + worldTab;
        for (int i = 0; i < 4; i++) blossom.stageTextures.add(texId(p, "celestial_blossom_stage_" + i));
        blossom.cropProduce = ns + ":celestial_fruit";
        blossom.cropProduceMin = 1;
        blossom.cropProduceMax = 3;

        // Mana Spring Fluid
        Fluid manaFluid = new Fluid();
        manaFluid.name = "mana_spring";
        manaFluid.displayName = "Luminous Mana Spring";
        manaFluid.setLoc("name", "ja_jp", "星光の霊泉");
        manaFluid.stillTexture = texId(p, "mana_spring_still");
        manaFluid.flowingTexture = texId(p, "mana_spring_flowing");
        manaFluid.tintColor = "#38C8FF";
        manaFluid.luminance = 12;

        // Biomes, Dimension & Portal (Hytale Orbis + Genshin Celestia)
        Biome meadows = new Biome();
        meadows.name = "celestial_meadows";
        meadows.features = "forest";
        meadows.temperature = 0.7;
        meadows.downfall = 0.5;
        meadows.sky = "#639BFF";
        meadows.fog = "#BBE9FF";
        meadows.water = "#24C8E8";
        meadows.waterFog = "#0B2C52";
        meadows.grass = "#3BC996";
        meadows.foliage = "#2BA888";
        meadows.ambientParticle = "minecraft:end_rod";
        meadows.particleChance = 0.015;
        MobSpawn allaySpawn = new MobSpawn();
        allaySpawn.entity = "minecraft:allay";
        allaySpawn.category = "creature";
        allaySpawn.weight = 15;
        meadows.spawns.add(allaySpawn);

        Biome rift = new Biome();
        rift.name = "abyssal_rift";
        rift.features = "barren";
        rift.temperature = 0.4;
        rift.downfall = 0.1;
        rift.precipitation = false;
        rift.sky = "#1E1242";
        rift.fog = "#4C2B87";
        rift.water = "#6D3DF5";
        rift.waterFog = "#190B36";
        rift.grass = "#6F43B8";
        rift.foliage = "#583299";
        rift.ambientParticle = "minecraft:crimson_spore";
        rift.particleChance = 0.025;
        MobSpawn endSpawn = new MobSpawn();
        endSpawn.entity = "minecraft:enderman";
        endSpawn.category = "monster";
        endSpawn.weight = 40;
        rift.spawns.add(endSpawn);

        Dimension reach = new Dimension();
        reach.name = "celestia_reach";
        reach.generator = "noise";
        reach.biomeSource = "checkerboard";
        reach.biomes = new ArrayList<>(List.of(ns + ":celestial_meadows", ns + ":abyssal_rift"));
        reach.ambientLight = 0.2;
        reach.natural = true;

        Portal portal = new Portal();
        portal.name = "celestia_portal";
        portal.displayName = "Celestia Gate";
        portal.setLoc("name", "ja_jp", "セレスティアの門");
        portal.frameBlock = ns + ":runed_marble";
        portal.targetDimension = ns + ":celestia_reach";
        portal.texture = texId(p, "starlight_portal");
        portal.igniterTexture = texId(p, "portal_igniter");
        portal.tintColor = "#52E5FF";

        // Voxel Sanctuary Structure & WorldFeature
        Structure shrineStruct = new Structure();
        shrineStruct.name = "ancient_waypoint_shrine";
        shrineStruct.palette = new ArrayList<>(List.of(
                ns + ":runed_marble",
                ns + ":celestial_pillar",
                ns + ":waypoint_shrine",
                "minecraft:chest[facing=south]",
                "minecraft:air",
                "minecraft:lantern[hanging=false]"));
        shrineStruct.resize(5, 4, 5);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                shrineStruct.set(x, 0, z, 0);
                boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
                for (int y = 1; y < 4; y++) {
                    shrineStruct.set(x, y, z, corner ? (y == 3 ? 5 : 1) : 4);
                }
            }
        }
        shrineStruct.set(2, 1, 2, 2);
        shrineStruct.set(2, 1, 1, 3);
        shrineStruct.chestLoot = ns + ":chests/celestia_shrine";
        shrineStruct.spacing = 28;
        shrineStruct.separation = 10;

        WorldFeature spire = new WorldFeature();
        spire.name = "starlight_spire";
        spire.featureType = "patch";
        spire.block = ns + ":celestite_ore";
        spire.biomes = "#minecraft:is_overworld";
        spire.minY = 32;
        spire.maxY = 128;

        // Mana Resource Network & Animated Resonance Beacon Multiblock
        Systems.Resource ether = new Systems.Resource();
        ether.name = "astral_ether";
        ether.displayName = "Astral Ether";
        ether.unit = "Ether";
        ether.color = "#52E5FF";
        ether.capacity = 10000;
        ether.pollution = true;
        ether.pollutionLimit = 500;
        ether.onPollution.add(step("broadcast", "text", "Ambient Astral Ether surges across the sanctuary!", "color", "aqua"));

        Multiblock beaconShape = new Multiblock();
        beaconShape.name = "resonance_beacon";
        beaconShape.palette = new ArrayList<>(List.of(ns + ":runed_marble", "minecraft:gold_block", ns + ":waypoint_shrine"));
        beaconShape.resize(3, 2, 3);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                beaconShape.set(x, 0, z, (x == 1 && z == 1) ? 1 : 0);
            }
        }
        beaconShape.set(1, 1, 1, 2);
        beaconShape.coreX = 1;
        beaconShape.coreY = 0;
        beaconShape.coreZ = 1;
        beaconShape.assemble = true;
        beaconShape.tool = ns + ":gale_staff";
        beaconShape.model = "resonance_beacon";
        beaconShape.workAnimation = "work";
        beaconShape.resource = "astral_ether";
        beaconShape.capacity = 3000;
        beaconShape.recipes.add(mbRecipe("minecraft:amethyst_shard", 2, ns + ":astral_shard", 2, ns + ":primordial_essence", 15, 60, 40));
        beaconShape.onAssemble.add(step("message", "text", "&b✦ Resonance Beacon awakened!", "color", "aqua"));

        Machine condenser = new Machine();
        condenser.name = "starlight_condenser";
        condenser.block = "minecraft:daylight_detector";
        condenser.interval = 20;
        condenser.celestial = "sky";
        condenser.io.add(machineIo("astral_ether", "produce", 25, 500));
        condenser.transfer = "beam";
        condenser.range = 10;
        condenser.transferRate = 40;
        condenser.beamParticle = "minecraft:end_rod";

        Machine nexus = new Machine();
        nexus.name = "ether_nexus";
        nexus.block = ns + ":waypoint_shrine";
        nexus.interval = 40;
        nexus.multiblock = "resonance_beacon";
        nexus.io.add(machineIo("astral_ether", "consume", 50, 1000));
        nexus.transfer = "radius";
        nexus.range = 6;
        nexus.particle = "minecraft:enchant";
        nexus.onCycle.add(step("give", "target", "@p[distance=..8]", "item", ns + ":celestial_coin", "count", "1"));

        p.elements.addAll(List.of(ore, marble, pillar, waypoint, forgeBlock, forge, infusionType, blossom, manaFluid,
                meadows, rift, reach, portal, shrineStruct, spire, ether, beaconShape, condenser, nexus));
    }

    // ------------------------------------------------------------------ 4. Custom Mobs, Rigged Multi-Phase Boss & NPCs

    private static void mobsAndBoss(Project p, String ns, String worldTab) {
        Effect starNova = new Effect();
        starNova.name = "starfall_nova_ring";
        starNova.shape = "circle";
        starNova.particle = "minecraft:dust";
        starNova.color = "#56D8FF";
        starNova.radius = 2.6;
        starNova.points = 28;
        starNova.yOffset = 0.2;

        Effect astralHelix = new Effect();
        astralHelix.name = "astral_ascension_helix";
        astralHelix.shape = "helix";
        astralHelix.particle = "minecraft:end_rod";
        astralHelix.radius = 1.6;
        astralHelix.height = 4.0;
        astralHelix.turns = 3;
        astralHelix.points = 54;

        // Flagship Rigged Multi-Phase Boss
        Mob warden = new Mob();
        warden.name = "astral_warden";
        warden.base = "minecraft:wither_skeleton";
        warden.displayName = "Astral Warden, Sovereign of Stars";
        warden.setLoc("name", "ja_jp", "星辰の守護者・ソヴリン");
        warden.health = 450;
        warden.damage = 12;
        warden.speed = 0.29;
        warden.armor = 14;
        warden.knockbackResistance = 0.85;
        warden.scale = 1.45;
        warden.rig = "astral_warden";
        warden.idleAnim = "idle";
        warden.walkAnim = "walk";
        warden.hurtAnim = "hurt";
        warden.attackAnim = "attack";
        warden.deathAnim = "death";
        warden.fireImmune = true;
        warden.boss = true;
        warden.bossColor = "purple";
        warden.bossStyle = "notched_10";
        warden.bossRadius = 56;
        warden.tickInterval = 15;

        warden.onSpawn.add(step("play_effect", "effect", "astral_ascension_helix"));
        warden.onSpawn.add(step("sound", "sound", "minecraft:entity.wither.spawn", "target", "@a[distance=..56]", "pitch", "0.85"));
        warden.onTick.add(step("play_effect", "effect", "starfall_nova_ring"));
        warden.onHurt.add(step("particle", "particle", "minecraft:crit", "count", "10"));

        BossPhase p65 = new BossPhase();
        p65.health = 65;
        p65.message = "The Astral Warden resonates with the constellations!";
        p65.steps.add(step("play_animation", "animation", "supernova"));
        p65.steps.add(step("effect", "effect", "minecraft:speed", "seconds", "999", "amplifier", "1"));
        p65.steps.add(step("summon", "entity", "minecraft:vex"));
        p65.steps.add(step("summon", "entity", "minecraft:vex"));

        BossPhase p25 = new BossPhase();
        p25.health = 25;
        p25.message = "STARFALL SUPERNOVA IMMINENT!";
        p25.steps.add(step("play_animation", "animation", "supernova"));
        Step blast = step("for_each", "selector", "@a[distance=..18]");
        blast.then.add(step("damage", "amount", "5", "damageType", ns + ":astral_cleave"));
        blast.then.add(step("lightning", "y", "~"));
        p25.steps.add(blast);

        warden.phases.addAll(List.of(p65, p25));
        warden.onDeath.add(step("broadcast", "text", "&6★ The Astral Warden has fallen! Glory to the Sanctum!", "color", "gold", "bold", "true"));
        warden.onDeath.add(step("give", "target", "@p[distance=..48]", "item", ns + ":primordial_essence", "count", "3"));
        warden.onDeath.add(step("give", "target", "@p[distance=..48]", "item", ns + ":celestial_coin", "count", "5"));
        warden.onDeath.add(step("score", "op", "add", "target", "@p[distance=..48]", "objective", "aetheria_combat_xp", "value", "150"));
        warden.onDeath.add(step("sound", "sound", "minecraft:ui.toast.challenge_complete", "target", "@a[distance=..56]"));

        // Native Mod-Loader Custom Entities
        CustomEntity stalker = new CustomEntity();
        stalker.name = "sky_stalker";
        stalker.displayName = "Skybound Stalker";
        stalker.setLoc("name", "ja_jp", "空駆ける影狼");
        stalker.kind = "hostile";
        stalker.category = "monster";
        stalker.health = 45;
        stalker.attackDamage = 7;
        stalker.speed = 0.28;
        stalker.texture = texId(p, "sky_stalker_skin");
        stalker.creativeTab = "tab:" + worldTab;
        stalker.naturalSpawn = true;
        stalker.spawnWeight = 18;
        stalker.spawnMin = 1;
        stalker.spawnMax = 2;
        stalker.aiGoals = new ArrayList<>(List.of("float", "melee", "stroll", "look_player", "attack_nearest_player"));
        stalker.onDeath.add(step("drop_item", "item", ns + ":anemo_feather", "count", "1"));

        CustomEntity wisp = new CustomEntity();
        wisp.name = "star_wisp";
        wisp.displayName = "Luminous Star Wisp";
        wisp.setLoc("name", "ja_jp", "星の妖精ウィスプ");
        wisp.kind = "passive";
        wisp.category = "creature";
        wisp.health = 20;
        wisp.speed = 0.25;
        wisp.texture = texId(p, "star_wisp_skin");
        wisp.creativeTab = "tab:" + worldTab;
        wisp.aiGoals = new ArrayList<>(List.of("float", "panic", "stroll", "look_player", "random_look"));
        wisp.onInteract.add(step("score", "op", "add", "objective", "aetheria_mana", "value", "20"));
        wisp.onInteract.add(step("actionbar", "text", "&b✦ The Star Wisp blesses you with +20 Mana!", "color", "aqua"));

        CustomEntity bolt = new CustomEntity();
        bolt.name = "ember_bolt";
        bolt.displayName = "Solaris Ember Bolt";
        bolt.kind = "projectile";
        bolt.projectileDamage = 7;
        bolt.fireImmune = true;

        // NPC Guild Master Trader & Villager Profession
        Trader quartermaster = new Trader();
        quartermaster.name = "guild_quartermaster";
        quartermaster.profession = "cleric";
        quartermaster.level = 5;
        quartermaster.displayName = "Astral Guild Quartermaster";
        quartermaster.nameColor = "aqua";
        quartermaster.trades.add(trade(ns + ":celestial_coin", 4, "", 1, ns + ":astral_shard", 2, 16));
        quartermaster.trades.add(trade(ns + ":celestial_coin", 8, ns + ":astral_shard", 4, ns + ":sun_nectar_tart", 2, 12));
        quartermaster.trades.add(trade(ns + ":celestial_coin", 12, ns + ":anemo_feather", 4, ns + ":gale_staff", 1, 4));
        quartermaster.trades.add(trade(ns + ":celestial_coin", 24, ns + ":primordial_essence", 2, ns + ":astral_greatsword", 1, 2));
        quartermaster.trades.add(trade(ns + ":astral_shard", 6, "minecraft:amethyst_shard", 6, ns + ":celestial_crest_armor_trim_smithing_template", 1, 4));

        Profession arcanist = new Profession();
        arcanist.name = "arcanist";
        arcanist.displayName = "Astral Arcanist";
        arcanist.setLoc("name", "ja_jp", "星辰の魔導師");
        arcanist.workstationBlock = ns + ":relic_forge";
        ProfessionTrade t1 = new ProfessionTrade();
        t1.level = 1;
        t1.costA = "minecraft:emerald";
        t1.countA = 4;
        t1.result = ns + ":astral_shard";
        t1.resultCount = 1;
        ProfessionTrade t2 = new ProfessionTrade();
        t2.level = 2;
        t2.costA = ns + ":celestial_coin";
        t2.countA = 6;
        t2.result = ns + ":starlight_resin";
        t2.resultCount = 1;
        arcanist.trades.addAll(List.of(t1, t2));

        p.elements.addAll(List.of(starNova, astralHelix, warden, stalker, wisp, bolt, quartermaster, arcanist));
    }

    // ------------------------------------------------------------------ 5. Quests, Recipes, Loot Tables & Tags

    private static void progressionAndEconomy(Project p, String ns) {
        // Multi-chapter Advancement Tree (zero orphan parents!)
        Advancement root = adv("root", "", ns + ":astral_shard", "Aetheria: Astral Awakening",
                "Touch starlight crystals and awaken your Elemental Resonance.", "task",
                "tick", "", 50, ns + ":textures/block/runed_marble.png");
        root.setLoc("title", "ja_jp", "Aetheria：星辰の覚醒");
        root.setLoc("description", "ja_jp", "星光の欠片を手に取り、元素共鳴を目覚めさせよ。");

        Advancement mineShard = adv("mine_celestite", "root", ns + ":celestite_ore", "Starstone Miner",
                "Obtain an Astral Shard from deep Celestite Ore.", "task",
                "inventory_changed", ns + ":astral_shard", 75, "");
        mineShard.setLoc("title", "ja_jp", "星石の採掘者");
        mineShard.setLoc("description", "ja_jp", "天青石鉱石から星辰の欠片を獲得する。");

        Advancement forgeBlade = adv("forge_relics", "mine_celestite", ns + ":astral_greatsword", "Blade of the Cosmos",
                "Craft or forge the Astral Greatsword.", "goal",
                "inventory_changed", ns + ":astral_greatsword", 150, "");
        forgeBlade.setLoc("title", "ja_jp", "星海を裂く大剣");
        forgeBlade.setLoc("description", "ja_jp", "星落の大剣を鍛造する。");

        Advancement armorUp = adv("valkyrie_set", "mine_celestite", ns + ":relic_cuirass", "Sworn of the Valkyrie",
                "Forge a piece of Valkyrie Relic Armor.", "goal",
                "inventory_changed", ns + ":relic_cuirass", 150, "");
        armorUp.setLoc("title", "ja_jp", "ワルキューレの誓い");
        armorUp.setLoc("description", "ja_jp", "聖遺物の鎧を纏う。");

        Advancement reachDim = adv("enter_celestia", "root", ns + ":waypoint_shrine", "Beyond the Sky Veil",
                "Enter Celestia Reach through a Celestia Gate or Sanctuary Warp.", "goal",
                "changed_dimension", ns + ":celestia_reach", 200, "");
        reachDim.setLoc("title", "ja_jp", "天界のベールを越えて");
        reachDim.setLoc("description", "ja_jp", "浮遊空域セレスティアへ到達する。");

        Advancement slayWarden = adv("slay_warden", "enter_celestia", ns + ":primordial_essence", "Sovereign Fallen",
                "Claim Primordial Essence by defeating the Astral Warden.", "challenge",
                "inventory_changed", ns + ":primordial_essence", 500, "");
        slayWarden.setLoc("title", "ja_jp", "星辰王の討伐");
        slayWarden.setLoc("description", "ja_jp", "星辰の守護者を打ち倒し原初の星精を手に入れる。");

        p.elements.addAll(List.of(root, mineShard, forgeBlade, armorUp, reachDim, slayWarden));

        // Recipes
        Recipe smeltOre = new Recipe();
        smeltOre.name = "smelt_celestite_ore";
        smeltOre.recipeType = "smelting";
        smeltOre.input = ns + ":celestite_ore";
        smeltOre.result = ns + ":astral_shard";
        smeltOre.experience = 1.5;

        Recipe craftGreatsword = shaped("craft_astral_greatsword", ns + ":astral_greatsword", 1,
                "", ns + ":primordial_essence", "",
                "", ns + ":astral_shard", "",
                "", "minecraft:netherite_sword", "");

        Recipe craftStaff = shaped("craft_gale_staff", ns + ":gale_staff", 1,
                "", ns + ":anemo_feather", ns + ":astral_shard",
                "", "minecraft:gold_ingot", ns + ":anemo_feather",
                "minecraft:stick", "", "");

        Recipe craftBow = shaped("craft_ember_bow", ns + ":ember_bow", 1,
                "", ns + ":pyro_core", "minecraft:string",
                "minecraft:blaze_rod", "", "minecraft:string",
                "", ns + ":pyro_core", "minecraft:string");

        Recipe craftScythe = shaped("craft_void_scythe", ns + ":void_scythe", 1,
                ns + ":astral_shard", ns + ":primordial_essence", "minecraft:amethyst_shard",
                "", "minecraft:stick", "",
                "", "minecraft:stick", "");

        Recipe craftPick = shaped("craft_aetheria_pickaxe", ns + ":aetheria_pickaxe", 1,
                ns + ":astral_shard", ns + ":astral_shard", ns + ":astral_shard",
                "", "minecraft:stick", "",
                "", "minecraft:stick", "");

        Recipe craftShield = shaped("craft_chrono_shield", ns + ":chrono_shield", 1,
                ns + ":astral_shard", "minecraft:gold_ingot", ns + ":astral_shard",
                ns + ":astral_shard", "minecraft:shield", ns + ":astral_shard",
                "", ns + ":astral_shard", "");

        Recipe craftMarble = shaped("craft_runed_marble", ns + ":runed_marble", 4,
                "minecraft:quartz_block", ns + ":astral_shard", "minecraft:quartz_block",
                ns + ":astral_shard", "minecraft:gold_ingot", ns + ":astral_shard",
                "minecraft:quartz_block", ns + ":astral_shard", "minecraft:quartz_block");

        Recipe craftWaypoint = shaped("craft_waypoint_shrine", ns + ":waypoint_shrine", 1,
                "", ns + ":astral_shard", "",
                ns + ":runed_marble", "minecraft:ender_pearl", ns + ":runed_marble",
                ns + ":runed_marble", ns + ":celestial_coin", ns + ":runed_marble");

        Recipe craftForge = shaped("craft_relic_forge", ns + ":relic_forge", 1,
                ns + ":astral_shard", "minecraft:anvil", ns + ":astral_shard",
                ns + ":runed_marble", ns + ":pyro_core", ns + ":runed_marble",
                ns + ":runed_marble", ns + ":runed_marble", ns + ":runed_marble");

        Recipe craftCrown = shaped("craft_relic_crown", ns + ":relic_crown", 1,
                ns + ":astral_shard", "minecraft:gold_ingot", ns + ":astral_shard",
                ns + ":astral_shard", "", ns + ":astral_shard",
                "", "", "");
        Recipe craftCuirass = shaped("craft_relic_cuirass", ns + ":relic_cuirass", 1,
                ns + ":astral_shard", "", ns + ":astral_shard",
                ns + ":astral_shard", ns + ":primordial_essence", ns + ":astral_shard",
                ns + ":astral_shard", ns + ":astral_shard", ns + ":astral_shard");
        Recipe craftTassets = shaped("craft_relic_tassets", ns + ":relic_tassets", 1,
                ns + ":astral_shard", "minecraft:gold_ingot", ns + ":astral_shard",
                ns + ":astral_shard", "", ns + ":astral_shard",
                ns + ":astral_shard", "", ns + ":astral_shard");
        Recipe craftGreaves = shaped("craft_relic_greaves", ns + ":relic_greaves", 1,
                ns + ":astral_shard", "", ns + ":astral_shard",
                "minecraft:gold_ingot", "", "minecraft:gold_ingot",
                "", "", "");

        Recipe craftTart = shaped("craft_sun_nectar_tart", ns + ":sun_nectar_tart", 2,
                "", "minecraft:honey_bottle", "",
                "minecraft:sweet_berries", "minecraft:wheat", "minecraft:egg",
                "", ns + ":celestial_fruit", "");

        Recipe brewSurge = new Recipe();
        brewSurge.name = "brew_starlight_surge";
        brewSurge.recipeType = "brewing";
        brewSurge.input = "minecraft:awkward";
        brewSurge.addition = ns + ":astral_shard";
        brewSurge.result = ns + ":starlight_surge";

        Recipe brewAscension = new Recipe();
        brewAscension.name = "brew_elixir_ascension";
        brewAscension.recipeType = "brewing";
        brewAscension.input = ns + ":starlight_surge";
        brewAscension.addition = ns + ":primordial_essence";
        brewAscension.result = ns + ":elixir_of_ascension";

        p.elements.addAll(List.of(smeltOre, craftGreatsword, craftStaff, craftBow, craftScythe, craftPick, craftShield,
                craftMarble, craftWaypoint, craftForge, craftCrown, craftCuirass, craftTassets, craftGreaves, craftTart,
                brewSurge, brewAscension));

        // Loot Tables (Custom Shrine Treasure + Vanilla Dungeon/Mob Injection)
        LootTable shrineChest = new LootTable();
        shrineChest.name = "chests/celestia_shrine";
        shrineChest.lootType = "chest";
        LootPool sp = new LootPool();
        sp.rollsMin = 2;
        sp.rollsMax = 4;
        sp.entries.addAll(List.of(
                lootItem(ns + ":astral_shard", 5, 2, 5),
                lootItem(ns + ":celestial_coin", 4, 2, 6),
                lootItem(ns + ":anemo_feather", 3, 1, 3),
                lootItem(ns + ":pyro_core", 3, 1, 2),
                lootItem(ns + ":starlight_resin", 2, 1, 1),
                lootItem(ns + ":sun_nectar_tart", 3, 1, 2)));
        shrineChest.pools.add(sp);

        LootTable dungeonHook = new LootTable();
        dungeonHook.name = "dungeon_aetheria_relics";
        dungeonHook.lootType = "chest";
        dungeonHook.replaces = "minecraft:chests/simple_dungeon";
        dungeonHook.inject = true;
        LootPool dp = new LootPool();
        dp.entries.addAll(List.of(
                lootItem(ns + ":astral_shard", 4, 1, 3),
                lootItem(ns + ":celestial_coin", 4, 1, 4),
                lootItem(ns + ":disc_celestia", 1, 1, 1)));
        dungeonHook.pools.add(dp);

        p.elements.addAll(List.of(shrineChest, dungeonHook));

        // Tags (Tools, Harvestable Blocks, Relics)
        Tag mineablePickaxe = tag("block", "minecraft", "mineable/pickaxe",
                ns + ":celestite_ore", ns + ":runed_marble", ns + ":celestial_pillar", ns + ":waypoint_shrine", ns + ":relic_forge");
        Tag needsIron = tag("block", "minecraft", "needs_iron_tool",
                ns + ":celestite_ore", ns + ":runed_marble", ns + ":waypoint_shrine", ns + ":relic_forge");
        Tag swords = tag("item", "minecraft", "swords", ns + ":astral_greatsword", ns + ":void_scythe");
        Tag pickaxes = tag("item", "minecraft", "pickaxes", ns + ":aetheria_pickaxe");
        Tag relics = tag("item", ns, "astral_relics",
                ns + ":astral_greatsword", ns + ":gale_staff", ns + ":ember_bow", ns + ":void_scythe", ns + ":chrono_shield");

        p.elements.addAll(List.of(mineablePickaxe, needsIron, swords, pickaxes, relics));
    }

    // ------------------------------------------------------------------ 6. RPG Engine: Objectives, Skills, Menus, Screens, Dialogs, Cutscene, Command

    private static void interfaceAndAbilities(Project p, String ns) {
        // IconFont Badges
        IconFont icons = new IconFont();
        icons.name = "astral_icons";
        icons.icons.add(icon("astral", texId(p, "icon_astral"), 9, 8));
        icons.icons.add(icon("coin", texId(p, "icon_coin"), 9, 8));
        icons.icons.add(icon("mana", texId(p, "icon_mana"), 9, 8));
        icons.icons.add(icon("pyro", texId(p, "icon_pyro"), 9, 8));
        icons.icons.add(icon("anemo", texId(p, "icon_anemo"), 9, 8));

        // RPG Objectives Init + Player Join Welcome + Mana Regen & Combat Level-Up Tick
        Procedure initScores = new Procedure();
        initScores.name = "rpg_init";
        initScores.trigger = "load";
        initScores.steps.add(step("objective_add", "objective", "aetheria_mana", "name", "Mana"));
        initScores.steps.add(step("objective_add", "objective", "aetheria_coins", "name", "Celestial Coins"));
        initScores.steps.add(step("objective_add", "objective", "aetheria_combat_xp", "name", "Combat XP"));
        initScores.steps.add(step("objective_add", "objective", "aetheria_level", "name", "Combat Level"));
        initScores.steps.add(step("objective_add", "objective", "aetheria_kills", "name", "Slayer Kills"));

        Procedure onJoin = new Procedure();
        onJoin.name = "rpg_player_join";
        onJoin.trigger = "player_join";
        onJoin.steps.add(step("score", "op", "add", "objective", "aetheria_mana", "value", "100"));
        onJoin.steps.add(step("score_clamp", "objective", "aetheria_mana", "min", "0", "max", "100"));
        onJoin.steps.add(step("score", "op", "add", "objective", "aetheria_level", "value", "1"));
        onJoin.steps.add(step("score", "op", "add", "objective", "aetheria_coins", "value", "100"));
        onJoin.steps.add(step("message", "text", ":astral: &bWelcome to &6Aetheria: Astral Resonance&b! Use &e/aetheria &bor press &a[H] &bfor the RPG Hub.", "color", "aqua"));

        // Regenerates +4 Mana / sec and levels up Combat Level every 100 Combat XP
        Procedure manaTick = new Procedure();
        manaTick.name = "rpg_mana_regen";
        manaTick.trigger = "interval";
        manaTick.interval = 20;
        Step eachPlayer = step("for_each", "selector", "@a");
        eachPlayer.then.add(step("score", "op", "add", "objective", "aetheria_mana", "value", "4"));
        eachPlayer.then.add(step("score_clamp", "objective", "aetheria_mana", "min", "0", "max", "100"));
        Step lvlCheck = Logic.newStep("if");
        lvlCheck.cond = Logic.newCondition("score");
        lvlCheck.cond.p.put("objective", "aetheria_combat_xp");
        lvlCheck.cond.p.put("min", "100");
        lvlCheck.then.add(step("score", "op", "remove", "objective", "aetheria_combat_xp", "value", "100"));
        lvlCheck.then.add(step("score", "op", "add", "objective", "aetheria_level", "value", "1"));
        lvlCheck.then.add(step("heal", "target", "@s", "amount", "8"));
        lvlCheck.then.add(step("title", "title", "&6LEVEL UP!", "subtitle", "&bCombat Mastery Ascended", "color", "gold"));
        lvlCheck.then.add(step("sound", "sound", "minecraft:entity.player.levelup", "pitch", "1.2"));
        eachPlayer.then.add(lvlCheck);
        manaTick.steps.add(eachPlayer);

        // Combat XP & Slayer Kill Counter
        Procedure slayerTick = new Procedure();
        slayerTick.name = "rpg_slayer_reward";
        slayerTick.trigger = "kill_entity";
        slayerTick.steps.add(step("score", "op", "add", "objective", "aetheria_kills", "value", "1"));
        slayerTick.steps.add(step("score", "op", "add", "objective", "aetheria_combat_xp", "value", "15"));
        slayerTick.steps.add(step("score", "op", "add", "objective", "aetheria_coins", "value", "5"));
        slayerTick.steps.add(step("actionbar", "text", "&a+15 Combat XP  &6+5 Coins  &7| :mana: &b{score:aetheria_mana}", "color", "green"));

        // Blessing Action Procedure (called from Codex CustomScreen button)
        Procedure astralBlessing = new Procedure();
        astralBlessing.name = "astral_blessing";
        astralBlessing.trigger = "manual";
        astralBlessing.steps.add(step("effect", "effect", ns + ":starlight_surge", "seconds", "30", "amplifier", "0"));
        astralBlessing.steps.add(step("score", "op", "set", "objective", "aetheria_mana", "value", "100"));
        astralBlessing.steps.add(step("particle_circle", "particle", "minecraft:end_rod", "radius", "2.2", "points", "24", "dy", "0.6"));
        astralBlessing.steps.add(step("sound", "sound", "minecraft:block.beacon.power_select", "pitch", "1.2"));
        astralBlessing.steps.add(step("message", "text", ":astral: &bCelestia grants you Starlight Surge and full Mana!", "color", "aqua"));

        // Hypixel Skyblock Chest Menus
        Systems.Menu arcaneShop = new Systems.Menu();
        arcaneShop.name = "arcane_shop";
        arcaneShop.title = "&1&lArcane Guild Armory";
        arcaneShop.rows = 3;
        arcaneShop.background = "panel";
        arcaneShop.panelColor = "#1B223C";
        arcaneShop.frameColor = "#F4C55A";
        arcaneShop.titleColor = "#F4C55A";
        arcaneShop.openAnimation = "rows";
        arcaneShop.animationSpeed = 1;
        arcaneShop.openSound = "minecraft:block.ender_chest.open";
        arcaneShop.clickSound = "minecraft:ui.button.click";

        arcaneShop.slots.add(shopSlot(10, ns + ":astral_shard", "&bAstral Shard &7(x2)", 30,
                List.of("&7Cost: &630 Celestial Coins", "&eClick to purchase"), ns + ":astral_shard", 2));
        arcaneShop.slots.add(shopSlot(12, ns + ":sun_nectar_tart", "&6Golden Nectar Tart", 40,
                List.of("&7Cost: &640 Celestial Coins", "&eRestores hunger & Mana"), ns + ":sun_nectar_tart", 1));
        arcaneShop.slots.add(shopSlot(14, ns + ":gale_staff", "&aSkyforged Gale Staff", 150,
                List.of("&7Cost: &6150 Celestial Coins", "&eAnemo mobility & updraft"), ns + ":gale_staff", 1));
        arcaneShop.slots.add(shopSlot(16, ns + ":astral_greatsword", "&5Astral Greatsword", 400,
                List.of("&7Cost: &6400 Celestial Coins", "&dMythic Starfall Cleave blade"), ns + ":astral_greatsword", 1));
        Systems.MenuSlot backHub = menuSlot(22, "minecraft:arrow", "&eReturn to Astral Hub", "menu", "astral_hub");
        arcaneShop.slots.add(backHub);

        Systems.Menu astralHub = new Systems.Menu();
        astralHub.name = "astral_hub";
        astralHub.title = "&6&lAstral Sanctuary Hub";
        astralHub.rows = 6;
        astralHub.background = "texture";
        astralHub.backgroundTexture = texId(p, "astral_hub_bg");
        astralHub.titleColor = "#F4C55A";
        astralHub.openAnimation = "center";
        astralHub.animationSpeed = 1;
        astralHub.openSound = "minecraft:block.amethyst_block.chime";
        astralHub.clickSound = "minecraft:ui.button.click";

        Systems.MenuSlot showcase = menuSlot(4, ns + ":astral_shard", "&6★ Aetheria Armory Showcase ★", "none", "");
        showcase.lore = "Legendary relics forged in Celestia";
        showcase.frames.addAll(List.of(ns + ":astral_greatsword", ns + ":gale_staff", ns + ":void_scythe", ns + ":primordial_essence"));
        showcase.frameTicks = 15;

        Systems.MenuSlot warpReach = menuSlot(20, "minecraft:ender_eye", "&bWarp to Celestia Reach", "run", "");
        warpReach.lore = "&7Enter the floating sky dimension\n&eClick to ascend";
        warpReach.glint = true;
        warpReach.steps.add(step("teleport_dimension", "target", "@s", "dimension", ns + ":celestia_reach", "y", "110", "safe", "true"));
        warpReach.steps.add(step("play_effect", "effect", "astral_ascension_helix"));

        Systems.MenuSlot openShop = menuSlot(22, "minecraft:emerald", "&6Open Arcane Guild Armory", "menu", "arcane_shop");
        openShop.lore = "&7Spend &6Celestial Coins &7on relics";
        openShop.closeAfter = false;

        Systems.MenuSlot openCodexSlot = menuSlot(24, ns + ":starlight_resin", "&dOpen Astral Character Codex", "run", "");
        openCodexSlot.lore = "&7Inspect stats, Level & cast Starlight Blessing";
        openCodexSlot.steps.add(step("open_screen", "screen", "astral_codex", "target", "@s"));

        Systems.MenuSlot talkElder = menuSlot(30, "minecraft:writable_book", "&eGuild Quests & Lore", "run", "");
        talkElder.lore = "&7Consult Elder Orion of the Astral Guild";
        talkElder.steps.add(step("show_dialog", "dialog", "elder_orion", "target", "@s"));

        Systems.MenuSlot challengeBoss = menuSlot(32, ns + ":primordial_essence", "&cSummon Astral Warden [BOSS]", "run", "");
        challengeBoss.lore = "&7Begins a multi-phase raid encounter";
        challengeBoss.glint = true;
        challengeBoss.permission = "2";
        challengeBoss.steps.add(step("spawn_mob", "mob", "astral_warden", "z", "~6"));
        challengeBoss.steps.add(step("hud_show", "hud", "boss_trial_timer", "target", "@a"));
        challengeBoss.steps.add(step("timer_start", "hud", "boss_trial_timer", "seconds", "180"));

        Systems.MenuSlot closeHub = menuSlot(49, "minecraft:barrier", "&cClose Menu", "close", "");
        astralHub.slots.addAll(List.of(showcase, warpReach, openShop, openCodexSlot, talkElder, challengeBoss, closeHub));

        // Interactive CustomScreens (Genshin/Skyblock Codex + Top-Left Status HUD Overlay)
        CustomScreen codex = new CustomScreen();
        codex.name = "astral_codex";
        codex.title = "Astral Character Codex";
        codex.widgets.add(widget("panel", "center", -125, -85, 250, 170, "color", "#11162B", "color2", "#F4C55A", "opacity", "94", "animation", "pop"));
        codex.widgets.add(widget("text", "center", -125, -75, 250, 12, "text", ":astral: &6&lAETHERIA CHARACTER CODEX :astral:", "align", "center", "animation", "fade_in"));
        codex.widgets.add(widget("entity", "center", -114, -54, 52, 82, "entity", "player"));
        codex.widgets.add(widget("text", "center", -52, -52, 165, 36, "text",
                "&fTraveler: &e{player}\n:astral: Combat Lv.&b{score:aetheria_level}  &7(XP {score:aetheria_combat_xp}/100)\n:coin: Coins: &6{score:aetheria_coins}   &7Kills: &c{score:aetheria_kills}",
                "animation", "slide_left"));
        codex.widgets.add(widget("bar", "center", -52, -10, 160, 8, "value", "score:aetheria_mana", "max", "100",
                "color", "#39C5FF", "color2", "#162A4A", "text", ":mana: Mana {value}/{max}"));
        codex.widgets.add(widget("bar", "center", -52, 4, 160, 8, "value", "health",
                "color", "#4EE08A", "color2", "#1B3B2A", "text", "HP {value}/{max}"));
        codex.widgets.add(widget("item", "center", 96, -75, 16, 16, "item", ns + ":astral_greatsword", "animation", "bob", "speed", "0.6"));
        codex.widgets.add(widget("button", "center", -114, 38, 74, 20, "text", ":astral: Blessing", "color2", "#1D4A75",
                "action", "procedure", "target", astralBlessing.name, "tooltip", "Restore Mana & activate Starlight Surge"));
        codex.widgets.add(widget("button", "center", -35, 38, 74, 20, "text", ":coin: Hub Menu", "color2", "#6A4E16",
                "action", "command", "target", "aetheria", "tooltip", "Open the Skyblock Chest Hub"));
        codex.widgets.add(widget("button", "center", 44, 38, 70, 20, "text", "Awakening", "color2", "#4B267A",
                "action", "command", "target", "aetheria cutscene", "tooltip", "Play the Celestia Awakening Cutscene"));
        codex.widgets.add(widget("button", "center", -35, 62, 70, 14, "text", "Close", "color2", "#343A52", "action", "close"));

        CustomScreen overlay = new CustomScreen();
        overlay.name = "rpg_overlay";
        overlay.mode = "overlay";
        overlay.refresh = 10;
        overlay.widgets.add(widget("panel", "top_left", 6, 6, 128, 42, "color", "#0D1126", "color2", "#F4C55A", "opacity", "78", "animation", "slide_right"));
        overlay.widgets.add(widget("text", "top_left", 12, 10, 116, 10, "text", ":astral: &eLv.{score:aetheria_level}  :coin: &6{score:aetheria_coins}"));
        overlay.widgets.add(widget("bar", "top_left", 12, 23, 116, 6, "value", "score:aetheria_mana", "max", "100",
                "color", "#39C5FF", "color2", "#182848", "text", ":mana: {value}/{max}"));
        overlay.widgets.add(widget("text", "top_left", 12, 33, 116, 10, "text", "&7XP {score:aetheria_combat_xp}/100  | {fps}fps"));

        // Server-compatible Scoreboard & Bossbar HUDs
        Hud sidebar = new Hud();
        sidebar.name = "aetheria_sidebar";
        sidebar.kind = "sidebar";
        sidebar.title = "&6&l✦ AETHERIA ✦";
        sidebar.refresh = 15;
        sidebar.lines.addAll(List.of(
                "&7Realm: &bCelestia",
                "",
                ":coin: &fCoins: &6{score:aetheria_coins}",
                ":mana: &fMana: &b{score:aetheria_mana}&7/100",
                "&fCombat Lv: &a{score:aetheria_level}",
                "&fSlayer Kills: &c{score:aetheria_kills}",
                "",
                "&e/aetheria &7to open Hub"));

        Hud bossTimer = new Hud();
        bossTimer.name = "boss_trial_timer";
        bossTimer.kind = "bossbar";
        bossTimer.title = "&5&lAstral Trial Remaining";
        bossTimer.lines.add("&5&lAstral Trial &7- &f{min}:{sec}");
        bossTimer.color = "purple";
        bossTimer.style = "notched_10";
        bossTimer.timer = true;
        bossTimer.seconds = 180;
        bossTimer.onFinish.add(step("broadcast", "text", "&dThe Astral Trial time has expired.", "color", "light_purple"));
        bossTimer.onFinish.add(step("hud_hide", "hud", "boss_trial_timer", "target", "@a"));

        // Genshin-style KeyBinds (Elemental Skill [V], Elemental Burst [B], Codex Hub [H])
        KeyBind skillV = new KeyBind();
        skillV.name = "elemental_skill";
        skillV.displayName = "Elemental Skill: Gale Surge";
        skillV.setLoc("name", "ja_jp", "元素スキル：翠風烈破");
        skillV.key = "key.keyboard.v";
        skillV.fallback = "sneak";
        Step skillCheck = Logic.newStep("if");
        skillCheck.cond = Logic.newCondition("score");
        skillCheck.cond.p.put("objective", "aetheria_mana");
        skillCheck.cond.p.put("min", "20");
        skillCheck.then.add(step("score", "op", "remove", "objective", "aetheria_mana", "value", "20"));
        skillCheck.then.add(step("effect", "effect", ns + ":anemo_blessing", "seconds", "5", "amplifier", "1"));
        skillCheck.then.add(step("teleport_forward", "distance", "4"));
        skillCheck.then.add(step("particle_circle", "particle", "minecraft:happy_villager", "radius", "2.2", "points", "20", "dy", "0.5"));
        skillCheck.then.add(step("sound", "sound", "minecraft:entity.player.attack.sweep", "pitch", "1.4"));
        skillCheck.then.add(step("actionbar", "text", ":anemo: &aGale Surge! &7(-20 Mana)", "color", "green"));
        skillCheck.orElse.add(step("actionbar", "text", "&cNeed 20 Mana for Gale Surge!", "color", "red"));
        skillV.steps.add(skillCheck);

        KeyBind burstB = new KeyBind();
        burstB.name = "elemental_burst";
        burstB.displayName = "Elemental Burst: Starfall Nova";
        burstB.setLoc("name", "ja_jp", "元素爆発：星落ノヴァ");
        burstB.key = "key.keyboard.b";
        burstB.fallback = "jump";
        Step burstCheck = Logic.newStep("if");
        burstCheck.cond = Logic.newCondition("score");
        burstCheck.cond.p.put("objective", "aetheria_mana");
        burstCheck.cond.p.put("min", "60");
        burstCheck.then.add(step("score", "op", "remove", "objective", "aetheria_mana", "value", "60"));
        burstCheck.then.add(step("effect", "effect", ns + ":starlight_surge", "seconds", "12", "amplifier", "2"));
        burstCheck.then.add(step("heal", "target", "@s", "amount", "8"));
        burstCheck.then.add(step("particle_burst", "particle", "minecraft:totem_of_undying", "count", "45", "speed", "0.6"));
        Step novaDmg = step("as_entities", "selector", "@e[type=!minecraft:player,distance=..8]");
        novaDmg.then.add(step("damage", "target", "@s", "amount", "14", "damageType", ns + ":astral_cleave"));
        novaDmg.then.add(step("lightning", "y", "~"));
        burstCheck.then.add(novaDmg);
        burstCheck.then.add(step("title", "title", "&b✧ STARFALL NOVA ✧", "subtitle", "&6Elemental Burst Unleashed!", "color", "aqua"));
        burstCheck.then.add(step("sound", "sound", "minecraft:entity.ender_dragon.growl", "pitch", "1.35"));
        burstCheck.orElse.add(step("actionbar", "text", "&cNeed 60 Mana for Starfall Nova!", "color", "red"));
        burstB.steps.add(burstCheck);

        KeyBind codexH = new KeyBind();
        codexH.name = "open_hub_key";
        codexH.displayName = "Open Astral Codex";
        codexH.setLoc("name", "ja_jp", "星辰コデックスを開く");
        codexH.key = "key.keyboard.h";
        codexH.steps.add(step("open_screen", "screen", "astral_codex", "target", "@s"));
        codexH.steps.add(step("show_overlay", "screen", "rpg_overlay", "target", "@s"));

        // Branching NPC Quests (Dialog)
        Systems.Dialog lore = new Systems.Dialog();
        lore.name = "lore_celestia";
        lore.kind = "notice";
        lore.title = "&bChronicles of Celestia Reach";
        lore.body = "Long ago, Orbis shattered above the clouds into gleaming sky islands.\n\nPress [V] for Anemo Gale Surge, [B] for Starfall Nova, and [H] to inspect your Codex.";
        DialogButton gotIt = new DialogButton();
        gotIt.label = "Back to Adventure";
        lore.buttons.add(gotIt);

        Systems.Dialog orion = new Systems.Dialog();
        orion.name = "elder_orion";
        orion.kind = "multi_action";
        orion.title = "&6Elder Orion - Astral Guild";
        orion.body = "Welcome, Traveler. Ready to sharpen your blade against the Astral Warden?";
        orion.columns = 2;
        orion.openSound = "minecraft:block.amethyst_block.chime";
        orion.buttons.add(dButton("Claim Guild Ration", "Grants coins & resin", "run", "",
                step("give", "target", "@s", "item", ns + ":starlight_resin", "count", "1"),
                step("score", "op", "add", "objective", "aetheria_coins", "value", "50")));
        orion.buttons.add(dButton("Summon Quartermaster", "Calls the Astral Merchant nearby", "run", "",
                step("spawn_trader", "trader", "guild_quartermaster", "x", "~2", "y", "~", "z", "~")));
        orion.buttons.add(dButton("Read Celestia Lore", "", "dialog", "lore_celestia"));
        orion.buttons.add(dButton("Close", "", "close", ""));

        // Cutscene: Celestia Awakening
        Cutscene awakening = new Cutscene();
        awakening.name = "celestia_awakening";
        double[][] pts = {{0, 4, -12, 0}, {10, 6, -5, 3}, {8, 9, 8, 6}, {-8, 11, 7, 9}, {0, 3, -4, 12}};
        for (double[] q : pts) {
            CameraKey k = new CameraKey();
            k.x = q[0]; k.y = q[1]; k.z = q[2]; k.time = q[3];
            k.lookAt = true; k.ty = 1.6;
            awakening.keys.add(k);
        }
        awakening.keys.get(4).easing = "ease_out";
        awakening.events.add(camEv(0.5, "title", "&bAetheria: Astral Resonance", "aqua", 3.0));
        awakening.events.add(camEv(0.5, "sound", "minecraft:ui.toast.challenge_complete", "white", 1.0));
        awakening.events.add(camEv(5.0, "subtitle", "&6Forge Mythic Relics & Conquer Celestia Reach", "gold", 3.5));
        awakening.events.add(camEv(10.5, "fade", "", "white", 1.5));
        awakening.onEnd.add(step("open_screen", "screen", "astral_codex", "target", "@s"));

        // Master /aetheria Command Tree
        Elements.Command cmd = new Elements.Command();
        cmd.name = "aetheria";
        cmd.description = "Aetheria Action-RPG Hub, Quests & Admin Commands";
        cmd.steps.add(step("open_menu", "menu", "astral_hub", "target", "@s"));

        cmd.branches.add(branch("codex", 0,
                step("open_screen", "screen", "astral_codex", "target", "@s"),
                step("show_overlay", "screen", "rpg_overlay", "target", "@s")));
        cmd.branches.add(branch("shop", 0, step("open_menu", "menu", "arcane_shop", "target", "@s")));
        cmd.branches.add(branch("overlay hide", 0, step("hide_overlay", "screen", "rpg_overlay", "target", "@s")));
        cmd.branches.add(branch("hud", 0, step("hud_show", "hud", "aetheria_sidebar", "target", "@s")));
        cmd.branches.add(branch("quest", 0, step("show_dialog", "dialog", "elder_orion", "target", "@s")));
        cmd.branches.add(branch("cutscene", 0, step("cutscene_play", "cutscene", "celestia_awakening", "target", "@s")));
        cmd.branches.add(branch("warp", 2, step("teleport_dimension", "target", "@s", "dimension", ns + ":celestia_reach", "y", "110", "safe", "true")));
        cmd.branches.add(branch("boss", 2, step("spawn_mob", "mob", "astral_warden", "z", "~6")));
        cmd.branches.add(branch("shrine", 2, step("place_structure", "structure", "ancient_waypoint_shrine", "z", "~4")));
        cmd.branches.add(branch("kit", 2,
                step("give", "target", "@s", "item", ns + ":astral_greatsword", "count", "1"),
                step("give", "target", "@s", "item", ns + ":gale_staff", "count", "1"),
                step("give", "target", "@s", "item", ns + ":relic_crown", "count", "1"),
                step("give", "target", "@s", "item", ns + ":relic_cuirass", "count", "1"),
                step("give", "target", "@s", "item", ns + ":celestial_coin", "count", "16"),
                step("message", "text", "&6Granted the Starter Astral Champion Kit!", "color", "gold")));
        cmd.branches.add(branch("coins <amount:int(1,10000)>", 2,
                step("score", "op", "add", "objective", "aetheria_coins", "value", "$(amount)"),
                step("message", "text", "&6Added $(amount) Celestial Coins.", "color", "gold")));

        p.elements.addAll(List.of(icons, initScores, onJoin, manaTick, slayerTick, astralBlessing,
                arcaneShop, astralHub, codex, overlay, sidebar, bossTimer,
                skillV, burstB, codexH, lore, orion, awakening, cmd));
    }

    // ------------------------------------------------------------------ 7. Shaders, UI Theme & Mod Config

    private static void visualsAndConfig(Project p, String ns) {
        Shader aurora = new Shader();
        aurora.name = "astral_aurora";
        aurora.kind = "include";
        aurora.program = "astral_aurora";
        aurora.mode = "nodes";
        ShaderNode in = ShaderNodes.create("input_color", "n1", 16, 16);
        ShaderNode scroll = ShaderNodes.create("uv_scroll", "n2", 16, 150);
        scroll.values.put("speed", "0.04, 0.08");
        ShaderNode noise = ShaderNodes.create("smooth_noise", "n3", 220, 150);
        noise.links.put("uv", "n2:out");
        noise.values.put("scale", "4");
        ShaderNode pulse = ShaderNodes.create("pulse", "n4", 220, 290);
        pulse.values.put("speed", "0.6");
        ShaderNode mul = ShaderNodes.create("multiply", "n5", 430, 210);
        mul.links.put("a", "n3:out");
        mul.links.put("b", "n4:out");
        ShaderNode glow = ShaderNodes.create("emissive", "n6", 640, 16);
        glow.links.put("color", "n1:out");
        glow.values.put("glow", "#39C5FF");
        glow.links.put("strength", "n5:out");
        ShaderNode out = ShaderNodes.create("output", "out", 850, 16);
        out.links.put("color", "n6:out");
        aurora.nodes.addAll(List.of(in, scroll, noise, pulse, mul, glow, out));

        Theme theme = new Theme();
        theme.name = "aetheria_theme";
        theme.glint = "color";
        theme.glintColor = "#42C8FF";
        theme.glintPattern = "aurora";
        theme.splashes = "Ascend to Celestia Reach!\nPress [V] for Gale Surge!\nPress [B] for Starfall Nova!\nAetheria: Astral Resonance";
        theme.tooltip = true;
        theme.tooltipBackground = "#0D1126";
        theme.tooltipBackgroundOpacity = 94;
        theme.tooltipBorderTop = "#F4C55A";
        theme.tooltipBorderBottom = "#39C5FF";
        theme.tooltipBorderOpacity = 90;
        ScreenButton codexBtn = new ScreenButton();
        codexBtn.screen = "pause";
        codexBtn.label = "Astral Hub";
        codexBtn.action = "command";
        codexBtn.value = "aetheria";
        theme.buttons.add(codexBtn);

        ModConfig cfg = new ModConfig();
        cfg.name = "aetheria_rpg";
        cfg.displayName = "Aetheria RPG Balance";
        cfg.entries.add(cfgEntry("mana_regen_per_sec", "int", "4", "1", "25", "Mana regenerated per second."));
        cfg.entries.add(cfgEntry("starting_coins", "int", "100", "0", "10000", "Coins granted to new adventurers."));
        cfg.entries.add(cfgEntry("boss_difficulty_scale", "double", "1.0", "0.5", "5.0", "Health/damage scaling multiplier for Astral Warden."));
        cfg.entries.add(cfgEntry("celestia_ambience", "bool", "true", "", "", "Enable drifting starlight particles in Celestia biomes."));

        p.elements.addAll(List.of(aurora, theme, cfg));
    }

    // ------------------------------------------------------------------ builder utilities

    private static String texId(Project p, String name) {
        return RpgSampleArt.findTex(p, name).id;
    }

    private static Step step(String kind, String... kv) {
        Step s = Logic.newStep(kind);
        for (int i = 0; i + 1 < kv.length; i += 2) s.p.put(kv[i], kv[i + 1]);
        return s;
    }

    private static Item simpleItem(Project p, String name, String display, String ja, String rarity, String tab, String tip, String shift) {
        Item it = new Item();
        it.name = name;
        it.displayName = display;
        it.setLoc("name", "ja_jp", ja);
        it.rarity = rarity;
        it.texture = texId(p, name);
        it.creativeTab = tab;
        it.tooltip = tip;
        it.shiftTooltip = shift;
        return it;
    }

    private static EffectModifier effMod(String attr, double amt, String op) {
        EffectModifier m = new EffectModifier();
        m.attribute = attr;
        m.amount = amt;
        m.operation = op;
        return m;
    }

    private static PotionEffect potEff(String eff, int sec, int amp) {
        PotionEffect pe = new PotionEffect();
        pe.effect = eff;
        pe.seconds = sec;
        pe.amplifier = amp;
        return pe;
    }

    private static SlotDef slotDef(int idx, int x, int y, String kind) {
        SlotDef s = new SlotDef();
        s.index = idx; s.x = x; s.y = y; s.kind = kind;
        return s;
    }

    private static ContainerRecipe cRecipe(String in1, String in2, String out, int count, int ticks) {
        ContainerRecipe r = new ContainerRecipe();
        r.input = in1; r.secondInput = in2; r.output = out; r.outputCount = count; r.processTicks = ticks;
        return r;
    }

    private static MultiblockRecipe mbRecipe(String in, int inCount, String out, int outCount, String sec, int chance, int time, int cost) {
        MultiblockRecipe r = new MultiblockRecipe();
        r.input = in; r.inputCount = inCount; r.output = out; r.outputCount = outCount;
        r.secondary = sec; r.secondaryChance = chance; r.time = time; r.cost = cost;
        return r;
    }

    private static MachineIO machineIo(String res, String mode, int amt, int cap) {
        MachineIO m = new MachineIO();
        m.resource = res; m.mode = mode; m.amount = amt; m.capacity = cap;
        return m;
    }

    private static Trade trade(String buy, int buyC, String buyB, int buyBC, String sell, int sellC, int maxUses) {
        Trade t = new Trade();
        t.buy = buy; t.buyCount = buyC; t.buyB = buyB; t.buyBCount = Math.max(1, buyBC);
        t.sell = sell; t.sellCount = sellC; t.maxUses = maxUses;
        return t;
    }

    private static Advancement adv(String name, String parent, String icon, String title, String desc, String frame,
                                   String trigger, String triggerVal, int xp, String bg) {
        Advancement a = new Advancement();
        a.name = name; a.parent = parent; a.icon = icon; a.title = title; a.description = desc; a.frame = frame;
        a.trigger = trigger; a.triggerValue = triggerVal; a.rewardXp = xp;
        if (!bg.isBlank()) a.background = bg;
        return a;
    }

    private static Recipe shaped(String name, String result, int count, String... cells) {
        Recipe r = new Recipe();
        r.name = name;
        r.recipeType = "shaped";
        r.grid = new ArrayList<>(Arrays.asList(cells));
        r.result = result;
        r.count = count;
        return r;
    }

    private static LootEntry lootItem(String id, int weight, int min, int max) {
        LootEntry e = new LootEntry();
        e.name = id; e.weight = weight; e.countMin = min; e.countMax = max;
        return e;
    }

    private static Tag tag(String reg, String ns, String name, String... values) {
        Tag t = new Tag();
        t.registry = reg; t.namespace = ns; t.name = name;
        t.values.addAll(List.of(values));
        return t;
    }

    private static Systems.Icon icon(String name, String texId, int h, int asc) {
        Systems.Icon i = new Systems.Icon();
        i.name = name; i.texture = texId; i.height = h; i.ascent = asc;
        return i;
    }

    private static Systems.MenuSlot menuSlot(int slot, String item, String name, String action, String target) {
        Systems.MenuSlot s = new Systems.MenuSlot();
        s.slot = slot; s.item = item; s.name = name; s.action = action; s.target = target;
        return s;
    }

    private static Systems.MenuSlot shopSlot(int slot, String item, String name, int cost, List<String> lore, String giveItem, int count) {
        Systems.MenuSlot s = menuSlot(slot, item, name, "run", "");
        s.costObjective = "aetheria_coins";
        s.cost = cost;
        s.lore = String.join("\n", lore);
        s.steps.add(step("give", "target", "@s", "item", giveItem, "count", String.valueOf(count)));
        s.steps.add(step("sound", "sound", "minecraft:entity.experience_orb.pickup", "pitch", "1.3"));
        return s;
    }

    private static ScreenWidget widget(String kind, String anchor, int x, int y, int w, int h, String... kv) {
        ScreenWidget sw = new ScreenWidget();
        sw.kind = kind; sw.anchor = anchor; sw.x = x; sw.y = y; sw.width = w; sw.height = h;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String v = kv[i + 1];
            switch (kv[i]) {
                case "text" -> sw.text = v;
                case "color" -> sw.color = v;
                case "color2" -> sw.color2 = v;
                case "texture" -> sw.texture = v;
                case "item" -> sw.item = v;
                case "entity" -> sw.entity = v;
                case "value" -> sw.value = v;
                case "max" -> sw.max = v;
                case "action" -> sw.action = v;
                case "target" -> sw.target = v;
                case "tooltip" -> sw.tooltip = v;
                case "animation" -> sw.animation = v;
                case "align" -> sw.align = v;
                case "opacity" -> sw.opacity = Integer.parseInt(v);
                case "speed" -> sw.speed = Double.parseDouble(v);
                default -> throw new IllegalArgumentException(kv[i]);
            }
        }
        return sw;
    }

    private static DialogButton dButton(String label, String tooltip, String action, String target, Step... steps) {
        DialogButton b = new DialogButton();
        b.label = label; b.tooltip = tooltip; b.action = action; b.target = target;
        b.steps.addAll(List.of(steps));
        return b;
    }

    private static CameraEvent camEv(double t, String kind, String text, String color, double dur) {
        CameraEvent e = new CameraEvent();
        e.time = t; e.kind = kind; e.text = text; e.color = color; e.duration = dur;
        return e;
    }

    private static CommandBranch branch(String path, int perm, Step... steps) {
        CommandBranch b = new CommandBranch();
        b.path = path;
        b.permission = perm == 0 ? -1 : perm;
        b.steps.addAll(List.of(steps));
        return b;
    }

    private static ConfigEntry cfgEntry(String key, String kind, String val, String min, String max, String comment) {
        ConfigEntry e = new ConfigEntry();
        e.key = key; e.kind = kind; e.value = val; e.min = min; e.max = max; e.comment = comment;
        return e;
    }
}

