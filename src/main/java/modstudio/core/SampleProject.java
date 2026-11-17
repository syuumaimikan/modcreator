package modstudio.core;

import modstudio.core.Elements.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A small but complete example project used for onboarding and tests. */
public final class SampleProject {
    private SampleProject() {}

    public static Project create() {
        Project p = new Project();
        p.meta.name = "Ruby Mod";
        p.meta.modId = "rubymod";
        p.meta.packageName = "com.example.rubymod";
        p.meta.description = "Rubies, ruby tools and a few tricks.";
        p.meta.authors = "ModStudio";
        p.meta.mcVersion = "1.21.1";
        p.meta.mcVersions = new java.util.ArrayList<>(java.util.List.of("1.21.1"));
        p.meta.targets = new ArrayList<>(List.of("neoforge", "fabric", "paper", "datapack", "resourcepack"));
        p.meta.languages = new ArrayList<>(List.of("ja_jp"));

        int red = 0xFFD8263F;
        TextureAsset ruby = TextureAsset.of("ruby", TextureTemplates.generate("gem", red), 16, 16);
        TextureAsset rubyBlock = TextureAsset.of("ruby_block", TextureTemplates.generate("block", red), 16, 16);
        TextureAsset rubyOre = TextureAsset.of("ruby_ore", TextureTemplates.generate("ore", red), 16, 16);
        TextureAsset sword = TextureAsset.of("ruby_sword", TextureTemplates.generate("sword", red), 16, 16);
        TextureAsset apple = TextureAsset.of("ruby_apple", TextureTemplates.generate("apple", red), 16, 16);
        TextureAsset glow = glowStrip(TextureTemplates.generate("block", red));
        p.textures.addAll(List.of(ruby, rubyBlock, rubyOre, sword, apple, glow));
        p.meta.icon = ruby.id;

        CreativeTab tab = new CreativeTab();
        tab.name = "rubies";
        tab.displayName = "Rubies";
        tab.icon = "rubymod:ruby";
        tab.setLoc("name", "ja_jp", "ルビー");

        Item rubyItem = new Item();
        rubyItem.name = "ruby";
        rubyItem.displayName = "Ruby";
        rubyItem.texture = ruby.id;
        rubyItem.creativeTab = "tab:rubies";
        rubyItem.rarity = "uncommon";
        rubyItem.tooltip = "A sparkling red gem";
        rubyItem.baseItem = "minecraft:emerald";
        rubyItem.setLoc("name", "ja_jp", "ルビー");

        Item swordItem = new Item();
        swordItem.name = "ruby_sword";
        swordItem.tooltip = "&cBurns with an inner fire";
        swordItem.shiftTooltip = "&7Forged from rubies in the Ruby Realm.\n&7Deals extra damage to the Ruby Golem.";
        swordItem.displayName = "Ruby Sword";
        swordItem.customTooltip = true;
        swordItem.tooltipBackground = "#1A0508";
        swordItem.tooltipBorderTop = "#FF4060";
        swordItem.tooltipBorderBottom = "#7A0F24";
        swordItem.tooltipBorderOpacity = 90;
        swordItem.kind = "sword";
        swordItem.tier = "diamond";
        swordItem.attackDamage = 4;
        swordItem.attackSpeed = -2.2;
        swordItem.texture = sword.id;
        swordItem.creativeTab = "tab:rubies";
        swordItem.baseItem = "minecraft:diamond_sword";
        swordItem.setLoc("name", "ja_jp", "ルビーの剣");

        Item appleItem = new Item();
        appleItem.name = "ruby_apple";
        appleItem.displayName = "Ruby Apple";
        appleItem.kind = "food";
        appleItem.nutrition = 6;
        appleItem.saturation = 1.2;
        appleItem.alwaysEdible = true;
        appleItem.glint = true;
        appleItem.rarity = "rare";
        appleItem.texture = apple.id;
        appleItem.creativeTab = "tab:rubies";
        appleItem.baseItem = "minecraft:golden_apple";
        appleItem.setLoc("name", "ja_jp", "ルビーのリンゴ");

        Block block = new Block();
        block.name = "ruby_block";
        block.displayName = "Block of Ruby";
        block.texture = rubyBlock.id;
        block.sound = "metal";
        block.hardness = 5;
        block.resistance = 6;
        block.toolLevel = "iron";
        block.creativeTab = "tab:rubies";
        block.setLoc("name", "ja_jp", "ルビーブロック");

        Block ore = new Block();
        ore.name = "ruby_ore";
        ore.displayName = "Ruby Ore";
        ore.texture = rubyOre.id;
        ore.hardness = 3;
        ore.toolLevel = "iron";
        ore.drop = "item";
        ore.dropItem = "rubymod:ruby";
        ore.dropMin = 1;
        ore.dropMax = 2;
        ore.oreGen = true;
        ore.oreSize = 6;
        ore.oreCount = 8;
        ore.oreMinY = -48;
        ore.oreMaxY = 32;
        ore.creativeTab = "tab:rubies";
        ore.setLoc("name", "ja_jp", "ルビー鉱石");

        Recipe toBlock = new Recipe();
        toBlock.name = "ruby_block";
        toBlock.recipeType = "shaped";
        toBlock.grid = new ArrayList<>(Arrays.asList("rubymod:ruby", "rubymod:ruby", "rubymod:ruby", "rubymod:ruby", "rubymod:ruby", "rubymod:ruby", "rubymod:ruby", "rubymod:ruby", "rubymod:ruby"));
        toBlock.result = "rubymod:ruby_block";

        Recipe fromBlock = new Recipe();
        fromBlock.name = "ruby_from_block";
        fromBlock.recipeType = "shapeless";
        fromBlock.grid = new ArrayList<>(Arrays.asList("rubymod:ruby_block", "", "", "", "", "", "", "", ""));
        fromBlock.result = "rubymod:ruby";
        fromBlock.count = 9;

        Recipe swordRecipe = new Recipe();
        swordRecipe.name = "ruby_sword";
        swordRecipe.recipeType = "shaped";
        swordRecipe.grid = new ArrayList<>(Arrays.asList("", "rubymod:ruby", "", "", "rubymod:ruby", "", "", "minecraft:stick", ""));
        swordRecipe.result = "rubymod:ruby_sword";

        Recipe appleRecipe = new Recipe();
        appleRecipe.name = "ruby_apple";
        appleRecipe.recipeType = "shaped";
        appleRecipe.grid = new ArrayList<>(Arrays.asList("rubymod:ruby", "rubymod:ruby", "rubymod:ruby", "rubymod:ruby", "minecraft:apple", "rubymod:ruby", "rubymod:ruby", "rubymod:ruby", "rubymod:ruby"));
        appleRecipe.result = "rubymod:ruby_apple";

        Recipe smelt = new Recipe();
        smelt.name = "ruby_from_smelting";
        smelt.recipeType = "smelting";
        smelt.input = "rubymod:ruby_ore";
        smelt.result = "rubymod:ruby";
        smelt.experience = 1.0;

        Advancement adv = new Advancement();
        adv.name = "get_ruby";
        adv.title = "Red Shine";
        adv.description = "Obtain a ruby";
        adv.icon = "rubymod:ruby";
        adv.trigger = "inventory_changed";
        adv.triggerValue = "rubymod:ruby";
        adv.background = "minecraft:textures/block/red_terracotta.png";
        adv.rewardXp = 10;
        adv.setLoc("title", "ja_jp", "赤い輝き");
        adv.setLoc("description", "ja_jp", "ルビーを手に入れる");

        LootTable loot = new LootTable();
        loot.name = "chests/ruby_cache";
        loot.lootType = "chest";
        LootPool pool = new LootPool();
        pool.rollsMin = 2;
        pool.rollsMax = 4;
        LootEntry e1 = new LootEntry();
        e1.name = "rubymod:ruby";
        e1.weight = 5;
        e1.countMin = 1;
        e1.countMax = 3;
        LootEntry e2 = new LootEntry();
        e2.name = "minecraft:gold_ingot";
        e2.weight = 3;
        e2.countMax = 2;
        LootEntry e3 = new LootEntry();
        e3.name = "rubymod:ruby_apple";
        e3.weight = 1;
        pool.entries.addAll(List.of(e1, e2, e3));
        loot.pools.add(pool);

        Tag gems = new Tag();
        gems.name = "gems";
        gems.namespace = "c";
        gems.registry = "item";
        gems.values.add("rubymod:ruby");

        Function hello = new Function();
        hello.name = "hello";
        hello.code = "tellraw @a {\"text\":\"Ruby Mod loaded\",\"color\":\"red\"}";
        hello.onLoad = true;

        Procedure welcome = new Procedure();
        welcome.name = "welcome";
        welcome.trigger = "player_join";
        Step msg = Logic.newStep("message");
        msg.p.put("text", "Welcome! Find rubies deep underground.");
        msg.p.put("color", "red");
        Step snd = Logic.newStep("sound");
        welcome.steps.addAll(List.of(msg, snd));

        Procedure power = new Procedure();
        power.name = "ruby_power";
        power.trigger = "item_right_click";
        power.triggerValue = "rubymod:ruby";
        Step cond = Logic.newStep("if");
        cond.cond = Logic.newCondition("chance");
        cond.cond.p.put("percent", "25");
        Step eff = Logic.newStep("effect");
        eff.p.put("effect", "minecraft:strength");
        eff.p.put("seconds", "15");
        Step part = Logic.newStep("particle");
        part.p.put("particle", "minecraft:flame");
        cond.then.addAll(List.of(eff, part));
        Step miss = Logic.newStep("actionbar");
        miss.p.put("text", "Nothing happened");
        miss.p.put("color", "gray");
        cond.orElse.add(miss);
        power.steps.add(cond);

        Elements.Command cmd = new Elements.Command();
        cmd.name = "rubyinfo";
        cmd.description = "Shows information about rubies";
        Step info = Logic.newStep("message");
        info.p.put("text", "Rubies spawn between Y -48 and Y 32.");
        info.p.put("color", "gold");
        cmd.steps.add(info);
        // Compile-time branch: each export keeps only the matching side, so one project can say where it runs.
        Step where = Logic.newStep("if");
        where.cond = Logic.newCondition("platform");
        where.cond.p.put("platform", "mod");
        Step asMod = Logic.newStep("message");
        asMod.p.put("text", "Running as a mod: ruby blocks and ore are available.");
        asMod.p.put("color", "green");
        where.then.add(asMod);
        Step asOther = Logic.newStep("message");
        asOther.p.put("text", "Running without a mod: use /function rubymod:give/ruby for ruby items.");
        asOther.p.put("color", "yellow");
        where.orElse.add(asOther);
        cmd.steps.add(where);
        Step newer = Logic.newStep("if");
        newer.cond = Logic.newCondition("mc_version");
        newer.cond.p.put("min", "1.21.4");
        newer.cond.p.put("max", "any");
        Step itemModels = Logic.newStep("message");
        itemModels.p.put("text", "This version picks ruby models with item model definitions.");
        itemModels.p.put("color", "gray");
        newer.then.add(itemModels);
        cmd.steps.add(newer);

        // 3D models: a chair that faces the player, a lamp and a crystal item.
        Elements.Model chairModel = new Elements.Model();
        chairModel.name = "ruby_chair";
        Models.applyTemplate(chairModel, "chair");
        chairModel.textures.put("0", rubyBlock.id);
        Block chair = new Block();
        chair.name = "ruby_chair";
        chair.displayName = "Ruby Chair";
        chair.shape = "model";
        chair.model = chairModel.name;
        chair.rotatable = true;
        chair.hardness = 2;
        chair.tool = "pickaxe";
        chair.creativeTab = "tab:rubies";
        chair.setLoc("name", "ja_jp", "ルビーの椅子");

        Elements.Model lampModel = new Elements.Model();
        lampModel.name = "ruby_lamp";
        Models.applyTemplate(lampModel, "lamp");
        lampModel.textures.put("0", glow.id);
        Block lamp = new Block();
        lamp.name = "ruby_lamp";
        lamp.displayName = "Ruby Lamp";
        lamp.shape = "model";
        lamp.model = lampModel.name;
        lamp.light = 15;
        lamp.hardness = 1;
        lamp.creativeTab = "tab:rubies";
        lamp.setLoc("name", "ja_jp", "ルビーのランプ");

        Elements.Model crystalModel = new Elements.Model();
        crystalModel.name = "ruby_crystal";
        crystalModel.kind = "item";
        Models.applyTemplate(crystalModel, "crystal");
        crystalModel.textures.put("0", ruby.id);
        Item crystal = new Item();
        crystal.name = "ruby_crystal";
        crystal.displayName = "Ruby Crystal";
        crystal.texture = ruby.id;
        crystal.model = crystalModel.name;
        crystal.rarity = "rare";
        crystal.creativeTab = "tab:rubies";
        crystal.setLoc("name", "ja_jp", "ルビーの結晶");

        p.elements.addAll(List.of(tab, rubyItem, swordItem, appleItem, block, ore, toBlock, fromBlock, swordRecipe,
                appleRecipe, smelt, adv, loot, gems, hello, welcome, power, cmd));
        p.elements.addAll(List.of(chairModel, lampModel, crystalModel, chair, lamp, crystal));

        // Shader include with helper functions; core shaders of any version can #moj_import it.
        Systems.Shader effects = new Systems.Shader();
        effects.name = "ms_effects";
        effects.kind = "include";
        effects.program = "ms_effects";
        StringBuilder glsl = new StringBuilder("// Ruby Mod shader helpers. Import with #moj_import <ms_effects.glsl> (1.21.4+: <minecraft:ms_effects.glsl>).\n\n");
        for (String s : List.of("grayscale", "hue_shift", "tint", "pulse", "emissive")) glsl.append(Shaders.SNIPPETS.get(s)).append('\n');
        effects.fragment = glsl.toString();
        p.elements.add(effects);
        p.elements.add(shimmer());
        armor(p, red);
        gameplay(p, red);
        crop(p, red);
        cutscene(p);
        systems(p);
        return p;
    }

    /** A pulsing four frame animation of a 16x16 texture (frames stacked from top to bottom). */
    private static TextureAsset glowStrip(int[] base) {
        double[] light = {1.0, 1.18, 1.36, 1.18};
        int[] strip = new int[256 * light.length];
        for (int f = 0; f < light.length; f++) {
            for (int i = 0; i < 256; i++) {
                int c = base[i];
                int r = Math.min(255, (int) (((c >> 16) & 255) * light[f]));
                int g = Math.min(255, (int) (((c >> 8) & 255) * light[f]));
                int b = Math.min(255, (int) ((c & 255) * light[f]));
                strip[f * 256 + i] = (c & 0xFF000000) | (r << 16) | (g << 8) | b;
            }
        }
        TextureAsset t = TextureAsset.of("ruby_glow", strip, 16, 16 * light.length);
        t.frameTime = 4;
        t.interpolate = true;
        return t;
    }

    private static Step step(String kind, String... kv) {
        Step s = Logic.newStep(kind);
        for (int i = 0; i + 1 < kv.length; i += 2) s.p.put(kv[i], kv[i + 1]);
        return s;
    }

    /** Mobs, effects, worldgen, interface and machines built from the same building blocks. */
    private static void systems(Project p) {
        Systems.Effect ring = new Systems.Effect();
        ring.name = "ruby_ring";
        ring.shape = "circle";
        ring.particle = "minecraft:dust";
        ring.color = "#E0115F";
        ring.radius = 1.5;
        ring.points = 24;
        ring.yOffset = 0.1;

        Systems.Effect spiral = new Systems.Effect();
        spiral.name = "ruby_helix";
        spiral.shape = "helix";
        spiral.particle = "minecraft:end_rod";
        spiral.radius = 1.2;
        spiral.height = 3;
        spiral.turns = 3;
        spiral.points = 48;

        // An animated model for the golem: bones, the standard animations and a roar for its rage phase.
        Elements.Model golemModel = new Elements.Model();
        golemModel.name = "ruby_golem";
        Rig.biped(golemModel);
        p.textures.stream().filter(t -> t.name.equals("ruby_block")).findFirst().ifPresent(t -> golemModel.textures.put("0", t.id));
        Elements.Animation roar = new Elements.Animation();
        roar.name = "roar";
        roar.length = 1.2;
        roar.mode = "once";
        double[][] arms = {{0, 0, 0, 0}, {0.3, 160, 0, 0}, {0.9, 160, 0, 0}, {1.2, 0, 0, 0}};
        Rig.key(roar, "right_arm", "rotation", "ease_out", arms);
        Rig.key(roar, "left_arm", "rotation", "ease_out", arms);
        Rig.key(roar, "head", "rotation", "ease_out", new double[][]{{0, 0, 0, 0}, {0.3, -25, 0, 0}, {0.9, -25, 0, 0}, {1.2, 0, 0, 0}});
        golemModel.animations.add(roar);
        p.elements.add(golemModel);

        Systems.Mob golem = new Systems.Mob();
        golem.name = "ruby_golem";
        golem.base = "minecraft:zombie";
        golem.displayName = "Ruby Golem";
        golem.setLoc("name", "ja_jp", "ルビーゴーレム");
        golem.health = 200;
        golem.damage = 9;
        golem.speed = 0.28;
        golem.armor = 10;
        golem.knockbackResistance = 0.8;
        golem.scale = 1.6;
        golem.rig = "ruby_golem";
        golem.idleAnim = "idle";
        golem.walkAnim = "walk";
        golem.hurtAnim = "hurt";
        golem.attackAnim = "attack";
        golem.deathAnim = "death";
        golem.fireImmune = true;
        golem.boss = true;
        golem.bossColor = "red";
        golem.bossStyle = "notched_10";
        golem.onSpawn.add(step("play_effect", "effect", "ruby_helix", "y", "~"));
        golem.onSpawn.add(step("sound", "sound", "minecraft:entity.wither.spawn", "target", "@a[distance=..48]"));
        golem.onTick.add(step("play_effect", "effect", "ruby_ring", "y", "~"));
        golem.tickInterval = 10;
        Systems.BossPhase half = new Systems.BossPhase();
        half.health = 50;
        half.message = "The Ruby Golem is enraged!";
        half.steps.add(step("effect", "effect", "minecraft:speed", "seconds", "9999", "amplifier", "1"));
        half.steps.add(step("for_each", "selector", "@a[distance=..16]"));
        half.steps.get(1).then.add(step("damage", "amount", "4", "damageType", "minecraft:magic"));
        half.steps.add(step("play_animation", "animation", "roar"));
        Systems.BossPhase last = new Systems.BossPhase();
        last.health = 20;
        last.message = "The Ruby Golem calls for help!";
        last.steps.add(step("summon", "entity", "minecraft:vex"));
        last.steps.add(step("summon", "entity", "minecraft:vex"));
        golem.phases.add(half);
        golem.phases.add(last);
        golem.onHurt.add(step("particle", "particle", "minecraft:crit", "count", "8"));
        golem.onDeath.add(step("broadcast", "text", "The Ruby Golem has fallen!", "color", "gold"));
        golem.onDeath.add(step("give", "item", "rubymod:ruby", "count", "8"));

        Elements.Command boss = new Elements.Command();
        boss.name = "rubyboss";
        boss.description = "Summons the Ruby Golem";
        boss.permission = 2;
        boss.steps.add(step("spawn_mob", "mob", "ruby_golem", "z", "~5"));
        boss.steps.add(step("beam", "target", "@e[tag=rubymod.mob.ruby_golem,limit=1,sort=nearest]", "particle", "minecraft:end_rod"));

        Systems.Structure shrine = new Systems.Structure();
        shrine.name = "ruby_shrine";
        shrine.palette = new ArrayList<>(List.of("minecraft:polished_blackstone_bricks", "minecraft:gold_block", "minecraft:chest[facing=south]",
                "minecraft:air", "minecraft:lantern[hanging=false]"));
        shrine.resize(5, 4, 5);
        for (int x = 0; x < 5; x++)
            for (int z = 0; z < 5; z++) {
                shrine.set(x, 0, z, 0);
                boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
                for (int y = 1; y < 4; y++) shrine.set(x, y, z, corner ? (y == 3 ? 4 : 0) : 3);
            }
        shrine.set(2, 0, 2, 1);
        shrine.set(2, 1, 2, 2);
        shrine.chestLoot = "rubymod:blocks/ruby_ore";
        shrine.spacing = 40;
        shrine.separation = 12;

        Systems.Biome wastes = new Systems.Biome();
        wastes.name = "ruby_wastes";
        wastes.features = "badlands";
        wastes.temperature = 1.5;
        wastes.downfall = 0;
        wastes.precipitation = false;
        wastes.sky = "#FF8FA3";
        wastes.fog = "#F2B8C6";
        wastes.water = "#C2185B";
        wastes.waterFog = "#4A0A1E";
        wastes.grass = "#B5475E";
        wastes.foliage = "#9C2A44";
        wastes.ambientParticle = "minecraft:crimson_spore";
        wastes.particleChance = 0.02;
        Systems.MobSpawn husk = new Systems.MobSpawn();
        husk.entity = "minecraft:husk";
        husk.category = "monster";
        husk.weight = 80;
        wastes.spawns.add(husk);

        Systems.Dimension realm = new Systems.Dimension();
        realm.name = "ruby_realm";
        realm.biomes = new ArrayList<>(List.of("ruby_wastes"));
        realm.ambientLight = 0.1;
        realm.fixedTime = 13000;
        realm.bedWorks = false;

        Elements.Command travel = new Elements.Command();
        travel.name = "rubyrealm";
        travel.description = "Travels to the Ruby Realm";
        travel.permission = 2;
        travel.steps.add(step("teleport_dimension", "dimension", "ruby_realm", "x", "~", "y", "100", "z", "~", "safe", "true"));
        Step there = step("for_each", "selector", "@s");
        there.then.add(step("place_structure", "structure", "ruby_shrine", "z", "~4"));
        travel.steps.add(there);

        p.elements.addAll(List.of(ring, spiral, golem, boss, shrine, wastes, realm, travel));
        golem.onDeath.add(step("http_request", "request", "discord_notify"));
        serverFeatures(p);
    }

    private static Systems.MenuSlot slot(int index, String item, String name, String action) {
        Systems.MenuSlot s = new Systems.MenuSlot();
        s.slot = index;
        s.item = item;
        s.name = name;
        s.action = action;
        return s;
    }

    private static Systems.Extract extract(String path, String key, boolean score) {
        Systems.Extract x = new Systems.Extract();
        x.path = path;
        x.key = key;
        x.toScore = score;
        return x;
    }

    /** Inventory GUI, HUD timers, a command tree and web requests (Hypixel API, Discord webhook, cron). */
    private static void serverFeatures(Project p) {
        Systems.Menu shop = new Systems.Menu();
        shop.name = "ruby_shop";
        shop.title = "&lRuby Shop";
        shop.rows = 3;
        Systems.MenuSlot sword = slot(11, "rubymod:ruby_sword", "&cRuby Sword", "run");
        sword.lore = "A blade that burns with an inner fire";
        sword.costObjective = "coins";
        sword.cost = 50;
        sword.glint = true;
        sword.steps.add(step("give", "item", "rubymod:ruby_sword", "count", "1"));
        sword.steps.add(step("sound", "sound", "minecraft:entity.player.levelup"));
        Systems.MenuSlot realmTrip = slot(13, "minecraft:ender_pearl", "&dTravel to the Ruby Realm", "command");
        realmTrip.target = "rubyrealm";
        realmTrip.lore = "Opens a portal to another dimension";
        realmTrip.permission = "2";
        Systems.MenuSlot stats = slot(15, "minecraft:book", "&bMy Hypixel stats", "run");
        stats.lore = "Looks up your Bed Wars wins";
        stats.steps.add(step("http_request", "request", "hypixel_player"));
        Systems.MenuSlot close = slot(22, "minecraft:barrier", "&cClose", "close");
        // a shining showcase slot that cycles through the ruby gear
        Systems.MenuSlot showcase = slot(4, "rubymod:ruby", "&6Ruby Collection", "none");
        showcase.frames.addAll(List.of("rubymod:ruby_sword", "rubymod:ruby_block"));
        showcase.frameTicks = 15;
        shop.slots.addAll(List.of(sword, realmTrip, stats, close, showcase));
        shop.background = "panel";
        shop.panelColor = "#f4d3da";
        shop.frameColor = "#e0115f";
        shop.titleColor = "#7a0a2e";
        shop.openAnimation = "center";
        shop.animationSpeed = 2;
        shop.openSound = "minecraft:block.chest.open";
        shop.clickSound = "minecraft:ui.button.click";

        Systems.Hud arena = new Systems.Hud();
        arena.name = "arena_timer";
        arena.kind = "bossbar";
        arena.title = "&c&lArena";
        arena.lines.add("&c&lArena &7- &f{min}:{sec}");
        arena.color = "red";
        arena.style = "notched_10";
        arena.timer = true;
        arena.seconds = 120;
        arena.onFinish.add(step("broadcast", "text", "Time is up! The arena is closed.", "color", "red"));
        arena.onFinish.add(step("hud_hide", "hud", "arena_timer", "target", "@a"));

        Systems.Hud board = new Systems.Hud();
        board.name = "ruby_board";
        board.kind = "sidebar";
        board.title = "&c&lRuby Mod";
        board.lines.addAll(List.of("&7Arena: &f{score:arena:#global}", "&7Hypixel players: &a{score:hypixel_online:#status_ping}", "", "&eplay.example.net"));

        Systems.Hud coins = new Systems.Hud();
        coins.name = "coin_bar";
        coins.kind = "actionbar";
        coins.lines.add("&6Coins: &e{score:coins}  &7|  &f{player}");
        coins.autoStart = true;
        coins.refresh = 20;

        Systems.ApiRequest hypixel = new Systems.ApiRequest();
        hypixel.name = "hypixel_player";
        hypixel.url = "https://api.hypixel.net/v2/player?uuid={uuid_plain}";
        hypixel.headers = "API-Key: {secret:HYPIXEL_KEY}";
        hypixel.extract.addAll(List.of(extract("player.displayname", "name", false),
                extract("player.stats.Bedwars.wins_bedwars", "bw_wins", true),
                extract("player.networkExp", "network_exp", true)));
        hypixel.onSuccess.add(step("command", "command",
                "tellraw @s [{\"text\":\"Bed Wars wins: \",\"color\":\"gray\"},{\"score\":{\"name\":\"@s\",\"objective\":\"bw_wins\"},\"color\":\"gold\"}]"));
        hypixel.onClientError.add(step("message", "text", "Hypixel rejected the request. Check HYPIXEL_KEY on the Secrets page.", "color", "red"));
        hypixel.onFailure.add(step("message", "text", "Could not reach Hypixel right now.", "color", "red"));

        Systems.ApiRequest discord = new Systems.ApiRequest();
        discord.name = "discord_notify";
        discord.method = "POST";
        discord.url = "{secret:DISCORD_WEBHOOK}";
        discord.body = "{\"content\": \"**{player}** defeated the Ruby Golem!\"}";
        discord.retries = 2;

        Systems.ApiRequest ping = new Systems.ApiRequest();
        ping.name = "status_ping";
        ping.url = "https://api.mcsrvstat.us/3/mc.hypixel.net";
        ping.schedule = "cron";
        ping.cron = "*/10 * * * *";
        ping.extract.add(extract("players.online", "hypixel_online", true));

        Elements.Command ruby = new Elements.Command();
        ruby.name = "ruby";
        ruby.description = "Ruby Mod commands";
        ruby.steps.add(step("open_menu", "menu", "ruby_shop"));
        ruby.branches.add(branch("shop", 0, step("open_menu", "menu", "ruby_shop")));
        ruby.branches.add(branch("stats", 0, step("http_request", "request", "hypixel_player")));
        Step pay = step("if");
        pay.cond = Logic.newCondition("score");
        pay.cond.p.put("objective", "coins");
        pay.cond.p.put("min", "$(amount)");
        pay.then.add(step("command", "command", "scoreboard players remove @s coins $(amount)"));
        pay.then.add(step("command", "command", "scoreboard players add $(target) coins $(amount)"));
        pay.then.add(step("message", "text", "Sent $(amount) coins to $(target)", "color", "green"));
        pay.orElse.add(step("message", "text", "You do not have $(amount) coins.", "color", "red"));
        ruby.branches.add(branch("pay <target:player> <amount:int(1,1000)>", 0, pay));
        ruby.branches.add(branch("arena start", 2,
                step("hud_show", "hud", "arena_timer"),
                step("timer_start", "hud", "arena_timer"),
                step("command", "command", "scoreboard players set #global arena 1")));
        ruby.branches.add(branch("arena start <seconds:int(10,3600)>", 2,
                step("hud_show", "hud", "arena_timer"),
                step("timer_start", "hud", "arena_timer", "seconds", "$(seconds)"),
                step("command", "command", "scoreboard players set #global arena 1")));
        ruby.branches.add(branch("arena stop", 2, step("timer_stop", "hud", "arena_timer"), step("hud_hide", "hud", "arena_timer"),
                step("command", "command", "scoreboard players set #global arena 0")));
        ruby.branches.add(branch("board", 2, step("hud_show", "hud", "ruby_board")));
        ruby.branches.add(branch("difficulty <level:choice(peaceful|easy|normal|hard)>", 2,
                step("command", "command", "difficulty $(level)"),
                step("broadcast", "text", "Difficulty is now $(level)", "color", "yellow")));

        Procedure hourly = new Procedure();
        hourly.name = "hourly_reminder";
        hourly.trigger = "cron";
        hourly.triggerValue = "0 * * * *";
        hourly.steps.add(step("broadcast", "text", "Visit /ruby shop for new items!", "color", "gold"));

        Procedure economy = new Procedure();
        economy.name = "setup_economy";
        economy.trigger = "load";
        economy.steps.add(step("command", "command", "scoreboard objectives add coins dummy\nscoreboard objectives add arena dummy"));

        p.elements.addAll(List.of(shop, arena, board, coins, hypixel, discord, ping, ruby, hourly, economy));
        machines(p, ruby);
    }

    /** Mana network with a celestial altar, a kinetic network that can overload, and a moving lift. */
    private static void machines(Project p, Elements.Command ruby) {
        Systems.Resource mana = new Systems.Resource();
        mana.name = "mana";
        mana.displayName = "Mana";
        mana.unit = "Mana";
        mana.color = "#4FC3F7";
        mana.pollution = true;
        mana.pollutionLimit = 400;
        mana.onPollution.add(step("broadcast", "text", "The air grows thick with wild mana...", "color", "dark_purple"));
        mana.onPollution.add(step("command", "command", "summon minecraft:vex ~ ~1 ~"));

        Systems.Resource stress = new Systems.Resource();
        stress.name = "stress";
        stress.displayName = "Stress";
        stress.unit = "SU";
        stress.color = "#B0BEC5";
        stress.model = "stress";

        Systems.Multiblock altarShape = new Systems.Multiblock();
        altarShape.name = "moon_altar";
        altarShape.palette = new ArrayList<>(List.of("minecraft:quartz_block", "minecraft:sea_lantern", "minecraft:enchanting_table"));
        altarShape.resize(3, 2, 3);
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 3; z++) altarShape.set(x, 0, z, x == 1 && z == 1 ? 2 : 0);
        for (int x = 0; x < 3; x += 2)
            for (int z = 0; z < 3; z += 2) altarShape.set(x, 1, z, 1);

        Systems.Multiblock lift = new Systems.Multiblock();
        lift.name = "lift_platform";
        lift.palette = new ArrayList<>(List.of("minecraft:oak_planks", "minecraft:lodestone"));
        lift.resize(3, 1, 3);
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 3; z++) lift.set(x, 0, z, x == 1 && z == 1 ? 1 : 0);
        lift.rotations = false;

        Systems.Machine collector = new Systems.Machine();
        collector.name = "sun_collector";
        collector.block = "minecraft:daylight_detector";
        collector.interval = 20;
        collector.celestial = "day";
        collector.io.add(io("mana", "produce", 5, 200));
        collector.transfer = "beam";
        collector.range = 8;
        collector.transferRate = 20;
        collector.beamParticle = "minecraft:end_rod";

        Systems.Machine pool = new Systems.Machine();
        pool.name = "mana_pool";
        pool.block = "minecraft:light_blue_glazed_terracotta";
        pool.interval = 10;
        pool.io.add(io("mana", "store", 0, 5000));
        pool.transfer = "radius";
        pool.range = 6;
        pool.transferRate = 50;
        pool.particle = "minecraft:glow";

        Systems.Machine altar = new Systems.Machine();
        altar.name = "moon_altar";
        altar.block = "minecraft:enchanting_table";
        altar.interval = 100;
        altar.multiblock = "moon_altar";
        altar.celestial = "night";
        altar.io.add(io("mana", "consume", 100, 1000));
        altar.transfer = "none";
        altar.particle = "minecraft:enchant";
        altar.onCycle.add(step("play_effect", "effect", "ruby_helix"));
        altar.onCycle.add(step("command", "command", "give @p[distance=..8] minecraft:amethyst_shard 1"));
        altar.onPlace.add(step("message", "text", "Build a quartz ring with sea lanterns on the corners and wait for the night.", "color", "aqua"));

        Systems.Machine engine = new Systems.Machine();
        engine.name = "steam_engine";
        engine.block = "minecraft:blast_furnace";
        engine.interval = 20;
        engine.rpm = 32;
        engine.io.add(io("stress", "produce", 256, 0));
        engine.transfer = "adjacent";
        engine.particle = "minecraft:cloud";

        Systems.Machine mill = new Systems.Machine();
        mill.name = "millstone";
        mill.block = "minecraft:grindstone";
        mill.interval = 40;
        mill.rpm = 16;
        mill.io.add(io("stress", "consume", 128, 0));
        mill.transfer = "adjacent";
        mill.overload = "break";
        mill.particle = "minecraft:crit";
        mill.onCycle.add(step("command", "command", "give @p[distance=..8] minecraft:bread 1"));
        mill.onOverload.add(step("broadcast", "text", "A millstone cracked under too much stress!", "color", "red"));

        ruby.branches.add(branch("lift", 2,
                step("contraption_move", "multiblock", "lift_platform", "y", "~-1", "dy", "4", "ticks", "40")));
        ruby.branches.add(branch("machines", 2,
                step("multiblock_build", "multiblock", "moon_altar", "x", "~3", "z", "~3"),
                step("machine_place", "machine", "moon_altar", "x", "~3", "z", "~3"),
                step("machine_place", "machine", "mana_pool", "x", "~6", "z", "~3"),
                step("machine_place", "machine", "sun_collector", "x", "~9", "z", "~3"),
                step("machine_place", "machine", "steam_engine", "x", "~3", "z", "~-3"),
                step("machine_place", "machine", "millstone", "x", "~4", "z", "~-3"),
                step("message", "text", "Placed a mana network and a kinetic line next to you.", "color", "green")));

        p.elements.addAll(List.of(mana, stress, altarShape, lift, collector, pool, altar, engine, mill));
        crusher(p, ruby);
        dialogs(p, ruby);
        hooks(p);
        for (Element e : p.elements) {
            if (e instanceof Elements.Item && e.name.equals("ruby")) e.notes = "Main material. Drops from ruby ore and buys everything in the ruby shop.";
            if (e instanceof Systems.Multiblock && e.name.equals("ruby_crusher")) e.notes = "Crushes ores into extra raw metal. Build it with an iron block core.";
        }
    }

    /**
     * Mod hooks: rubies added to dungeon chests and zombie drops without replacing those tables, two brewing stand
     * recipes (one for the ruby rush potion) and two key bindings.
     */
    private static void hooks(Project p) {
        LootTable dungeon = new LootTable();
        dungeon.name = "dungeon_rubies";
        dungeon.lootType = "chest";
        dungeon.replaces = "minecraft:chests/simple_dungeon";
        dungeon.inject = true;
        LootPool dp = new LootPool();
        LootEntry rubies = new LootEntry();
        rubies.name = "rubymod:ruby";
        rubies.weight = 3;
        rubies.countMin = 1;
        rubies.countMax = 3;
        LootEntry nothing = new LootEntry();
        nothing.kind = "empty";
        nothing.weight = 2;
        dp.entries.addAll(List.of(rubies, nothing));
        dungeon.pools.add(dp);

        LootTable zombie = new LootTable();
        zombie.name = "zombie_rubies";
        zombie.lootType = "entity";
        zombie.replaces = "minecraft:entities/zombie";
        zombie.inject = true;
        LootPool zp = new LootPool();
        zp.chance = 0.1;
        zp.killedByPlayer = true;
        LootEntry one = new LootEntry();
        one.name = "rubymod:ruby";
        zp.entries.add(one);
        zombie.pools.add(zp);

        Recipe rush = new Recipe();
        rush.name = "ruby_rush_brewing";
        rush.recipeType = "brewing";
        rush.input = "minecraft:awkward";
        rush.addition = "rubymod:ruby";
        rush.result = "rubymod:ruby_rush";
        Recipe strength = new Recipe();
        strength.name = "ruby_strength_brewing";
        strength.recipeType = "brewing";
        strength.input = "minecraft:mundane";
        strength.addition = "rubymod:ruby";
        strength.result = "minecraft:strength";
        Recipe vigorBrew = new Recipe();
        vigorBrew.name = "ruby_vigor_brewing";
        vigorBrew.recipeType = "brewing";
        vigorBrew.input = "rubymod:ruby_rush";
        vigorBrew.addition = "minecraft:glow_berries";
        vigorBrew.result = "rubymod:ruby_vigor";

        Systems.KeyBind dash = new Systems.KeyBind();
        dash.name = "ruby_dash";
        dash.displayName = "Ruby Dash";
        dash.setLoc("name", "ja_jp", "ルビーダッシュ");
        dash.key = "key.keyboard.r";
        dash.fallback = "sneak";
        dash.steps.add(step("effect", "target", "@s", "effect", "minecraft:speed", "seconds", "2", "amplifier", "3", "hide", "true"));
        dash.steps.add(step("particle", "particle", "minecraft:crit", "count", "12", "dy", "0.4"));
        dash.steps.add(step("sound", "sound", "minecraft:entity.player.attack.sweep", "target", "@s", "pitch", "1.4"));
        Systems.KeyBind menu = new Systems.KeyBind();
        menu.name = "ruby_menu";
        menu.displayName = "Ruby Menu";
        menu.setLoc("name", "ja_jp", "ルビーメニュー");
        menu.key = "key.keyboard.g";
        menu.steps.add(step("show_dialog", "dialog", "ruby_welcome", "target", "@s"));
        p.elements.addAll(List.of(dungeon, zombie, rush, strength, vigorBrew, dash, menu));
    }

    /**
     * Data pack building blocks: a predicate (night while holding a ruby), an item modifier that blesses the held
     * weapon, a welcome dialog with a rules dialog behind it, and a ruby merchant to spawn.
     */
    private static void dialogs(Project p, Elements.Command ruby) {
        Predicate night = new Predicate();
        night.name = "ruby_night";
        night.terms.add(term("time", "min", "13000", "max", "23000"));
        night.terms.add(term("equipment", "slot", "mainhand", "item", "rubymod:ruby"));

        ItemModifier bless = new ItemModifier();
        bless.name = "ruby_blessing";
        bless.functions.add(function("set_name", "text", "&cBlessed Blade", "color", "red"));
        bless.functions.add(function("set_lore", "text", "&7Blessed at the ruby shrine", "mode", "append"));
        bless.functions.add(function("enchant", "enchantment", "minecraft:sharpness", "level", "3", "add", "true"));
        bless.functions.add(function("glint"));

        Systems.Trader merchant = new Systems.Trader();
        merchant.name = "ruby_merchant";
        merchant.profession = "cleric";
        merchant.level = 3;
        merchant.displayName = "Ruby Merchant";
        merchant.nameColor = "red";
        merchant.trades.add(trade("minecraft:emerald", 3, "", 0, "rubymod:ruby", 1, 16));
        merchant.trades.add(trade("rubymod:ruby", 4, "", 0, "rubymod:ruby_hammer", 1, 4));
        merchant.trades.add(trade("rubymod:ruby", 2, "minecraft:iron_ingot", 8, "minecraft:diamond", 1, 8));

        Systems.Dialog rules = new Systems.Dialog();
        rules.name = "ruby_rules";
        rules.kind = "notice";
        rules.title = "&cRuby World Rules";
        rules.body = "Be kind to other players.\n\nRubies glow at night: hold one after sunset for a surprise.";
        Systems.DialogButton gotIt = new Systems.DialogButton();
        gotIt.label = "Got it";
        rules.buttons.add(gotIt);

        Systems.Dialog welcome = new Systems.Dialog();
        welcome.name = "ruby_welcome";
        welcome.kind = "multi_action";
        welcome.title = "&cWelcome to Ruby World";
        welcome.body = "Pick a gift to start with.\n\nYou can open this window again with /ruby welcome.";
        welcome.columns = 2;
        welcome.openSound = "minecraft:block.amethyst_block.chime";
        welcome.buttons.add(button("Bless my weapon", "Names and enchants the item in your hand", "run", "",
                step("modify_item", "target", "@s", "slot", "weapon.mainhand", "modifier", "ruby_blessing"),
                step("message", "text", "Your weapon glows with ruby light.", "color", "red")));
        welcome.buttons.add(button("Ruby cache", "Rolls the ruby cache loot table", "run", "",
                step("give_loot", "target", "@s", "table", "chests/ruby_cache")));
        welcome.buttons.add(button("Call the merchant", "A ruby merchant appears next to you", "run", "",
                step("spawn_trader", "trader", "ruby_merchant", "x", "~2", "y", "~", "z", "~")));
        welcome.buttons.add(button("Rules", "", "dialog", "ruby_rules"));
        welcome.buttons.add(button("Minecraft Wiki", "Opens the wiki in your browser", "url", "https://minecraft.wiki"));
        welcome.buttons.add(button("Close", "", "close", ""));

        Procedure glow = new Procedure();
        glow.name = "ruby_night_glow";
        glow.trigger = "interval";
        glow.interval = 40;
        Step check = Logic.newStep("if");
        check.cond = Logic.newCondition("predicate");
        check.cond.p.put("predicate", "ruby_night");
        check.then.add(step("effect", "target", "@s", "effect", "minecraft:glowing", "seconds", "3", "amplifier", "0", "hide", "true"));
        glow.steps.add(step("for_each", "selector", "@a"));
        glow.steps.get(0).then.add(check);

        ruby.branches.add(branch("welcome", 0, step("show_dialog", "dialog", "ruby_welcome", "target", "@s")));
        ruby.branches.add(branch("merchant", 2, step("spawn_trader", "trader", "ruby_merchant", "x", "~", "y", "~", "z", "~2")));
        Systems.Theme theme = new Systems.Theme();
        theme.name = "ruby_theme";
        theme.notes = "Ruby red enchantment glint, ruby splash texts and a wiki button on the pause screen.";
        theme.glint = "color";
        theme.glintColor = "#FF2050";
        theme.glintPattern = "waves";
        theme.splashes = "Now with more rubies!\nShiny and red!\nAlso try the Ruby Realm!";
        Systems.ScreenButton wiki = new Systems.ScreenButton();
        wiki.screen = "pause";
        wiki.label = "Ruby Wiki";
        wiki.action = "url";
        wiki.value = "https://example.com/ruby-wiki";
        theme.buttons.add(wiki);
        // A shrine that shows off the larger step set: chat links, item editing, displays, teams, score math and bodies.
        Procedure blessing = new Procedure();
        blessing.name = "shrine_blessing";
        blessing.trigger = "manual";
        blessing.notes = "Blesses the player's sword and decorates the shrine.";
        blessing.steps.add(step("message_link", "text", "Read about the shrine", "url", "https://example.com/ruby-wiki", "color", "aqua"));
        blessing.steps.add(step("message_hover", "text", "[Blessing]", "hover", "Your sword glows red for a while"));
        Step blessed = Logic.newStep("if");
        blessed.cond = Logic.newCondition("wearing");
        blessed.cond.p.put("slot", "armor.head");
        blessed.cond.p.put("item", "minecraft:golden_helmet");
        blessed.then.add(step("item_rename", "text", "Shrine Blade", "color", "red"));
        blessed.then.add(step("item_lore", "text", "Blessed at the ruby shrine", "color", "gray"));
        blessed.then.add(step("item_glint", "on", "true"));
        blessed.orElse.add(step("title_times", "fade_in", "5", "stay", "40", "fade_out", "10"));
        blessed.orElse.add(step("actionbar_score", "text", "Shrine visits: ", "objective", "shrine_visits", "color", "gold"));
        blessing.steps.add(blessed);
        blessing.steps.add(step("particle_circle", "particle", "minecraft:flame", "radius", "2", "points", "16", "dy", "0.2"));
        blessing.steps.add(step("firework", "y", "~1", "rgb", "#E0115F", "shape", "star", "flight", "1"));
        blessing.steps.add(step("text_display", "text", "Ruby Shrine", "y", "~2.5", "color", "red", "billboard", "center", "tag", "shrine"));
        blessing.steps.add(step("score_random", "objective", "shrine_luck", "min", "1", "max", "6"));
        blessing.steps.add(step("score_operation", "objective", "shrine_visits", "op", "+=", "source", "@s", "source_objective", "shrine_luck"));
        blessing.steps.add(step("score_clamp", "objective", "shrine_visits", "min", "0", "max", "99"));
        blessing.steps.add(step("team_join", "team", "ruby"));
        blessing.steps.add(step("team_color", "team", "ruby", "color", "red"));
        Step nearby = step("as_entities", "selector", "@e[type=minecraft:zombie,distance=..12]");
        nearby.then.add(step("glowing", "target", "@s", "on", "true"));
        nearby.then.add(step("push_away", "target", "@s", "strength", "1.5"));
        blessing.steps.add(nearby);
        Step dark = Logic.newStep("if");
        dark.cond = Logic.newCondition("light");
        dark.cond.p.put("max", "6");
        dark.then.add(step("summon_named", "entity", "minecraft:allay", "name", "Shrine Spirit", "color", "light_purple"));
        blessing.steps.add(dark);
        blessing.steps.add(step("modifier_add", "attribute", "movement_speed", "name", "shrine_speed", "amount", "0.1", "operation", "add_multiplied_total"));

        p.elements.addAll(List.of(night, bless, merchant, rules, welcome, glow, theme, blessing));
        screens(p, ruby, blessing);
    }

    private static TextureAsset texture(Project p, String name) {
        return p.textures.stream().filter(t -> t.name.equals(name)).findFirst().orElse(null);
    }

    private static Systems.ScreenWidget widget(String kind, String anchor, int x, int y, int w, int h, String... kv) {
        Systems.ScreenWidget sw = new Systems.ScreenWidget();
        sw.kind = kind;
        sw.anchor = anchor;
        sw.x = x;
        sw.y = y;
        sw.width = w;
        sw.height = h;
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
                case "shadow" -> sw.shadow = Boolean.parseBoolean(v);
                case "speed" -> sw.speed = Double.parseDouble(v);
                default -> throw new IllegalArgumentException(kv[i]);
            }
        }
        return sw;
    }

    /** Icon font, a shrine menu screen and a HUD overlay with live values. */
    private static void screens(Project p, Elements.Command ruby, Procedure blessing) {
        String ns = p.meta.modId;
        TextureAsset gem = texture(p, "ruby");
        TextureAsset glowTex = texture(p, "ruby_glow");
        Systems.IconFont icons = new Systems.IconFont();
        icons.name = "ruby_icons";
        icons.notes = "Write :ruby: or :spark: in any text.";
        Systems.Icon rubyIcon = new Systems.Icon();
        rubyIcon.name = "ruby";
        rubyIcon.texture = gem == null ? "" : gem.id;
        Systems.Icon spark = new Systems.Icon();
        spark.name = "spark";
        spark.texture = glowTex == null ? "" : glowTex.id;
        spark.height = 7;
        spark.ascent = 7;
        icons.icons.addAll(List.of(rubyIcon, spark));

        Systems.CustomScreen menu = new Systems.CustomScreen();
        menu.name = "ruby_menu";
        menu.title = "Ruby Shrine";
        menu.notes = "Opened with /ruby menu. Buttons run the blessing, open the wiki or close it.";
        menu.widgets.add(widget("panel", "center", -110, -75, 220, 150, "color", "#2B0A12", "color2", "#E0115F", "opacity", "92", "animation", "pop"));
        menu.widgets.add(widget("text", "center", -110, -66, 220, 10, "text", ":ruby: &cRuby Shrine :ruby:", "align", "center", "animation", "fade_in"));
        menu.widgets.add(widget("entity", "center", -100, -50, 50, 76, "entity", "player"));
        menu.widgets.add(widget("text", "center", -44, -48, 150, 30, "text", "Welcome, &e{player}&r!\nDay {day}, {game_time}\n&7Playing: {media}",
                "animation", "slide_left"));
        menu.widgets.add(widget("item", "center", -44, -14, 16, 16, "item", ns + ":ruby", "tooltip", "Ruby", "animation", "bob", "speed", "0.5"));
        menu.widgets.add(widget("bar", "center", -22, -10, 120, 8, "value", "health", "color", "#E04050", "color2", "#30101A", "text", "{value}/{max}"));
        menu.widgets.add(widget("image", "center", 84, -66, 16, 16, "texture", glowTex == null ? "" : glowTex.id, "animation", "pulse"));
        menu.widgets.add(widget("button", "center", -100, 32, 96, 20, "text", ":spark: Blessing", "color2", "#7A1A2E", "action", "procedure",
                "target", blessing.name, "tooltip", "Blesses the sword in your hand", "animation", "slide_up"));
        menu.widgets.add(widget("button", "center", 4, 32, 96, 20, "text", "Wiki", "color2", "#3A3A3A", "action", "url",
                "target", "https://example.com/ruby-wiki", "animation", "slide_up"));
        menu.widgets.add(widget("button", "center", -30, 56, 60, 14, "text", "Close", "color2", "#3A3A3A", "action", "close"));

        Systems.CustomScreen hud = new Systems.CustomScreen();
        hud.name = "ruby_hud";
        hud.mode = "overlay";
        hud.refresh = 10;
        hud.notes = "Shown with /ruby menu; hidden by the shrine's own command.";
        hud.widgets.add(widget("panel", "top_left", 4, 4, 112, 38, "color", "#000000", "color2", "#E0115F", "opacity", "60", "animation", "slide_right"));
        hud.widgets.add(widget("text", "top_left", 9, 9, 100, 10, "text", ":ruby: &f{score:shrine_visits} &7visits"));
        hud.widgets.add(widget("text", "top_left", 9, 20, 100, 10, "text", "&7{time}  {fps} fps"));
        hud.widgets.add(widget("bar", "top_left", 9, 32, 100, 5, "value", "score:shrine_luck", "max", "6", "color", "#FFAA00", "color2", "#402000"));

        Procedure open = new Procedure();
        open.name = "open_ruby_menu";
        open.trigger = "manual";
        open.notes = "Opens the shrine menu, shows the HUD and stores the computer clock.";
        open.steps.add(step("real_time", "objective", "ruby_clock"));
        open.steps.add(step("open_screen", "screen", menu.name, "target", "@s"));
        open.steps.add(step("show_overlay", "screen", hud.name, "target", "@s"));
        open.steps.add(step("message", "text", ":ruby: The shrine opens.", "color", "red"));
        ruby.branches.add(branch("menu", 0, step("function", "function", ns + ":procedures/" + open.name)));
        ruby.branches.add(branch("hide", 0, step("hide_overlay", "screen", hud.name, "target", "@s"), step("close_screen", "target", "@s")));
        p.elements.addAll(List.of(icons, menu, hud, open));
    }

    private static PredicateTerm term(String kind, String... kv) {
        PredicateTerm t = new PredicateTerm();
        t.kind = kind;
        for (int i = 0; i + 1 < kv.length; i += 2) t.p.put(kv[i], kv[i + 1]);
        Logic.defaults(Logic.PREDICATE_TERMS.get(kind), t.p);
        return t;
    }

    private static ModifierFunction function(String kind, String... kv) {
        ModifierFunction f = new ModifierFunction();
        f.kind = kind;
        for (int i = 0; i + 1 < kv.length; i += 2) f.p.put(kv[i], kv[i + 1]);
        Logic.defaults(Logic.MODIFIER_FUNCTIONS.get(kind), f.p);
        return f;
    }

    private static Systems.Trade trade(String buy, int buyCount, String buyB, int buyBCount, String sell, int sellCount, int maxUses) {
        Systems.Trade t = new Systems.Trade();
        t.buy = buy;
        t.buyCount = buyCount;
        t.buyB = buyB;
        t.buyBCount = Math.max(1, buyBCount);
        t.sell = sell;
        t.sellCount = sellCount;
        t.maxUses = maxUses;
        return t;
    }

    private static Systems.DialogButton button(String label, String tooltip, String action, String target, Step... steps) {
        Systems.DialogButton b = new Systems.DialogButton();
        b.label = label;
        b.tooltip = tooltip;
        b.action = action;
        b.target = target;
        b.steps.addAll(List.of(steps));
        return b;
    }

    /**
     * A machine assembled like the Immersive Engineering crusher: build the frame, right-click the redstone core with
     * the ruby hammer, then throw ore on top. It runs on mana and its rollers turn while it works.
     */
    private static void crusher(Project p, Elements.Command ruby) {
        TextureAsset hammerTex = TextureAsset.of("ruby_hammer", TextureTemplates.generate("hammer", 0xFFD8263F), 16, 16);
        p.textures.add(hammerTex);
        Item hammer = new Item();
        hammer.name = "ruby_hammer";
        hammer.displayName = "Ruby Hammer";
        hammer.texture = hammerTex.id;
        hammer.baseItem = "minecraft:carrot_on_a_stick";
        hammer.maxStack = 1;
        hammer.creativeTab = "tab:rubies";
        hammer.tooltip = "&7Right-click the core of a machine to assemble it";
        hammer.setLoc("name", "ja_jp", "ルビーのハンマー");

        Elements.Model model = new Elements.Model();
        model.name = "ruby_crusher";
        model.kind = "item";
        model.textures.put("0", "minecraft:block/iron_block");
        p.textures.stream().filter(t -> t.name.equals("ruby_block")).findFirst().ifPresent(t -> model.textures.put("1", t.id));
        String[][] bones = {{"frame", "", "8", "0", "24"}, {"roller_left", "frame", "2", "20", "24"}, {"roller_right", "frame", "14", "20", "24"}};
        for (String[] b : bones) {
            Elements.Bone bone = new Elements.Bone();
            bone.name = b[0];
            bone.parent = b[1];
            bone.pivot = new double[]{Double.parseDouble(b[2]), Double.parseDouble(b[3]), Double.parseDouble(b[4])};
            model.bones.add(bone);
        }
        Object[][] cubes = {{"frame", "base", -16, 0, 0, 32, 8, 48}, {"frame", "left_wall", -16, 8, 0, -8, 30, 48},
                {"frame", "right_wall", 24, 8, 0, 32, 30, 48}, {"frame", "back_wall", -8, 8, 40, 24, 26, 48},
                {"frame", "front_wall", -8, 8, 0, 24, 12, 8}, {"roller_left", "roller_left", -4, 14, 8, 8, 26, 40},
                {"roller_right", "roller_right", 8, 14, 8, 20, 26, 40}};
        for (Object[] c : cubes) {
            Elements.Cube cu = Models.cube((String) c[1], (int) c[2], (int) c[3], (int) c[4], (int) c[5], (int) c[6], (int) c[7]);
            cu.bone = (String) c[0];
            for (String dir : Elements.Cube.FACES) {
                cu.face(dir).uv = new double[]{0, 0, 16, 16};
                if (cu.bone.startsWith("roller")) cu.face(dir).texture = "1";
            }
            model.cubes.add(cu);
        }
        Elements.Animation work = new Elements.Animation();
        work.name = "work";
        work.length = 1;
        work.mode = "loop";
        Rig.key(work, "roller_left", "rotation", "linear", new double[][]{{0, 0, 0, 0}, {1, 0, 0, 360}});
        Rig.key(work, "roller_right", "rotation", "linear", new double[][]{{0, 0, 0, 0}, {1, 0, 0, -360}});
        model.animations.add(work);

        Systems.Multiblock crusher = new Systems.Multiblock();
        crusher.name = "ruby_crusher";
        crusher.palette = new ArrayList<>(List.of("minecraft:iron_block", "minecraft:redstone_block", "minecraft:smooth_stone", "minecraft:grindstone"));
        crusher.resize(3, 2, 3);
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 3; z++) {
                crusher.set(x, 0, z, x == 1 && z == 0 ? 1 : 0);
                crusher.set(x, 1, z, x == 1 && z == 1 ? 3 : 2);
            }
        crusher.coreX = 1;
        crusher.coreY = 0;
        crusher.coreZ = 0;
        crusher.assemble = true;
        crusher.tool = "rubymod:ruby_hammer";
        crusher.model = "ruby_crusher";
        crusher.workAnimation = "work";
        crusher.inX = 1;
        crusher.inY = 1;
        crusher.inZ = 1;
        crusher.outX = 1;
        crusher.outY = 0;
        crusher.outZ = -1;
        crusher.resource = "mana";
        crusher.capacity = 2000;
        crusher.workSound = "minecraft:block.grindstone.use";
        crusher.recipes.add(recipe("minecraft:iron_ore", 1, "minecraft:raw_iron", 2, "rubymod:ruby", 10, 60, 20));
        crusher.recipes.add(recipe("minecraft:cobblestone", 1, "minecraft:gravel", 1, "", 0, 30, 5));
        crusher.recipes.add(recipe("rubymod:ruby", 1, "minecraft:redstone", 3, "", 0, 40, 10));
        crusher.onAssemble.add(step("message", "text", "The Ruby Crusher is ready. Throw ore on top of it.", "color", "green"));

        ruby.branches.add(branch("crusher", 2,
                step("multiblock_build", "multiblock", "ruby_crusher", "x", "~-4", "z", "~4"),
                step("give", "item", "rubymod:ruby_hammer", "count", "1"),
                step("message", "text", "Right-click the redstone block with the hammer to assemble the crusher.", "color", "green")));
        p.elements.addAll(List.of(hammer, model, crusher));
    }

    private static Systems.MultiblockRecipe recipe(String in, int inCount, String out, int outCount, String extra, int chance, int time, int cost) {
        Systems.MultiblockRecipe r = new Systems.MultiblockRecipe();
        r.input = in;
        r.inputCount = inCount;
        r.output = out;
        r.outputCount = outCount;
        r.secondary = extra;
        r.secondaryChance = chance;
        r.time = time;
        r.cost = cost;
        return r;
    }

    private static Systems.MachineIO io(String resource, String mode, int amount, int capacity) {
        Systems.MachineIO io = new Systems.MachineIO();
        io.resource = resource;
        io.mode = mode;
        io.amount = amount;
        io.capacity = capacity;
        return io;
    }

    private static Elements.CommandBranch branch(String path, int permission, Step... steps) {
        Elements.CommandBranch b = new Elements.CommandBranch();
        b.path = path;
        b.permission = permission == 0 ? -1 : permission;
        b.steps.addAll(List.of(steps));
        return b;
    }

    /** A ruby armor set: the material with generated worn textures and the four pieces. */
    /**
     * An include built from nodes: drifting noise times a slow pulse makes the colour glow red. It needs no original
     * source, so it exports for every version; core shaders call ms_ruby_shimmer(color, uv, time, screen).
     */
    private static Systems.Shader shimmer() {
        Systems.Shader s = new Systems.Shader();
        s.name = "ruby_shimmer";
        s.kind = "include";
        s.program = "ruby_shimmer";
        s.mode = "nodes";
        Systems.ShaderNode in = ShaderNodes.create("input_color", "n1", 16, 16);
        Systems.ShaderNode scroll = ShaderNodes.create("uv_scroll", "n2", 16, 150);
        scroll.values.put("speed", "0.05, 0.12");
        Systems.ShaderNode noise = ShaderNodes.create("smooth_noise", "n3", 226, 150);
        noise.links.put("uv", "n2:out");
        noise.values.put("scale", "5");
        Systems.ShaderNode pulse = ShaderNodes.create("pulse", "n4", 226, 300);
        pulse.values.put("speed", "0.5");
        Systems.ShaderNode mul = ShaderNodes.create("multiply", "n5", 436, 210);
        mul.links.put("a", "n3:out");
        mul.links.put("b", "n4:out");
        Systems.ShaderNode glow = ShaderNodes.create("emissive", "n6", 646, 16);
        glow.links.put("color", "n1:out");
        glow.values.put("glow", "#99132F");
        glow.links.put("strength", "n5:out");
        Systems.ShaderNode out = ShaderNodes.create("output", "out", 856, 16);
        out.links.put("color", "n6:out");
        s.nodes.addAll(List.of(in, scroll, noise, pulse, mul, glow, out));
        return s;
    }

    private static void armor(Project p, int color) {
        ArmorMaterial m = new ArmorMaterial();
        m.name = "ruby";
        m.durability = 25;
        m.helmet = 3;
        m.chestplate = 7;
        m.leggings = 6;
        m.boots = 3;
        m.toughness = 1;
        m.enchantability = 15;
        m.equipSound = "diamond";
        m.repairItem = "rubymod:ruby";
        TextureAsset l1 = TextureAsset.of("ruby_layer_1", TextureTemplates.generate("armor_layer_1", color), 64, 32);
        TextureAsset l2 = TextureAsset.of("ruby_layer_2", TextureTemplates.generate("armor_layer_2", color), 64, 32);
        p.textures.addAll(List.of(l1, l2));
        m.layer1 = l1.id;
        m.layer2 = l2.id;
        p.elements.add(m);
        String[][] names = {{"helmet", "Ruby Helmet", "ルビーのヘルメット"}, {"chestplate", "Ruby Chestplate", "ルビーのチェストプレート"},
                {"leggings", "Ruby Leggings", "ルビーのレギンス"}, {"boots", "Ruby Boots", "ルビーのブーツ"}};
        for (String[] n : names) {
            TextureAsset icon = TextureAsset.of("ruby_" + n[0], TextureTemplates.generate(n[0], color), 16, 16);
            p.textures.add(icon);
            Item it = new Item();
            it.name = "ruby_" + n[0];
            it.kind = n[0];
            it.armorMaterial = m.name;
            it.displayName = n[1];
            it.maxStack = 1;
            it.texture = icon.id;
            it.creativeTab = "tab:rubies";
            it.setLoc("name", "ja_jp", n[2]);
            p.elements.add(it);
        }
    }

    /** A status effect with a potion, an enchantment, a damage type, a painting, a music disc and a projectile procedure. */
    private static void gameplay(Project p, int color) {
        TextureAsset rushIcon = TextureAsset.of("ruby_rush_icon", TextureTemplates.generate("dust", color), 16, 16);
        p.textures.add(rushIcon);
        StatusEffect rush = new StatusEffect();
        rush.name = "ruby_rush";
        rush.displayName = "Ruby Rush";
        rush.setLoc("name", "ja_jp", "ルビーラッシュ");
        rush.color = "#E0115F";
        rush.icon = rushIcon.id;
        EffectModifier speed = new EffectModifier();
        speed.attribute = "movement_speed";
        speed.amount = 0.2;
        rush.modifiers.add(speed);
        EffectModifier dmg = new EffectModifier();
        dmg.attribute = "attack_damage";
        dmg.amount = 1;
        dmg.operation = "add_value";
        rush.modifiers.add(dmg);
        rush.tickInterval = 20;
        rush.onTick.add(step("particle", "particle", "minecraft:crit", "count", "6", "dy", "0.6"));
        rush.potionName = "Potion of Ruby Rush";
        rush.setLoc("potion", "ja_jp", "ルビーラッシュのポーション");
        rush.potionSeconds = 120;

        Enchantment edge = new Enchantment();
        edge.name = "ruby_edge";
        edge.displayName = "Ruby Edge";
        edge.setLoc("name", "ja_jp", "ルビーの刃");
        edge.supportedItems = "#minecraft:enchantable/sword";
        edge.maxLevel = 3;
        edge.damagePerLevel = 1.5;
        edge.onHit.add(step("particle", "particle", "minecraft:crimson_spore", "count", "12"));
        edge.onHit.add(step("effect", "effect", "minecraft:glowing", "seconds", "3"));

        DamageType shard = new DamageType();
        shard.name = "ruby_shard";
        shard.deathMessage = "%1$s was pierced by ruby shards";
        shard.deathMessageAttacker = "%1$s was pierced by ruby shards from %2$s";
        shard.setLoc("death", "ja_jp", "%1$s はルビーの破片に貫かれた");
        shard.setLoc("death_attacker", "ja_jp", "%1$s は %2$s のルビーの破片に貫かれた");
        shard.projectile = true;

        int[] px = new int[32 * 16];
        int[] shades = TextureTemplates.shades(color);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 32; x++) {
                boolean frame = x == 0 || y == 0 || x == 31 || y == 15;
                int sky = y < 6 ? 0xFF2B1B3D : y < 10 ? shades[2] : 0xFF3A2A1A;
                boolean sun = (x - 16) * (x - 16) + (y - 9) * (y - 9) * 4 < 30 && y < 10;
                px[y * 32 + x] = frame ? 0xFF5E3F22 : sun ? shades[4] : sky;
            }
        }
        TextureAsset sunsetTex = TextureAsset.of("ruby_sunset", px, 32, 16);
        p.textures.add(sunsetTex);
        Painting sunset = new Painting();
        sunset.name = "ruby_sunset";
        sunset.title = "Ruby Sunset";
        sunset.setLoc("title", "ja_jp", "ルビーの夕焼け");
        sunset.author = "Mod Studio";
        sunset.texture = sunsetTex.id;
        sunset.width = 2;
        sunset.height = 1;

        Song waltz = new Song();
        waltz.name = "ruby_waltz";
        waltz.description = "Ruby Waltz";
        waltz.setLoc("description", "ja_jp", "ルビーのワルツ");
        waltz.sound = "minecraft:music_disc.cat";
        waltz.lengthSeconds = 185;
        waltz.comparatorOutput = 3;
        TextureAsset discTex = TextureAsset.of("ruby_disc", TextureTemplates.generate("nugget", color), 16, 16);
        p.textures.add(discTex);
        Item disc = new Item();
        disc.name = "ruby_disc";
        disc.displayName = "Ruby Music Disc";
        disc.setLoc("name", "ja_jp", "ルビーのレコード");
        disc.texture = discTex.id;
        disc.maxStack = 1;
        disc.rarity = "rare";
        disc.song = waltz.name;
        disc.creativeTab = "tab:rubies";
        disc.baseItem = "minecraft:music_disc_cat";

        Procedure volley = new Procedure();
        volley.name = "ruby_volley";
        volley.trigger = "item_right_click";
        volley.triggerValue = "rubymod:ruby_sword";
        volley.steps.add(step("shoot", "entity", "minecraft:arrow", "speed", "2"));
        volley.steps.add(step("effect", "effect", "rubymod:ruby_rush", "seconds", "5"));
        volley.steps.add(step("sound", "sound", "minecraft:entity.arrow.shoot"));

        Potion vigor = new Potion();
        vigor.name = "ruby_vigor";
        vigor.displayName = "Potion of Ruby Vigor";
        vigor.setLoc("name", "ja_jp", "ルビーの活力のポーション");
        vigor.notes = "Brewed from Ruby Rush: the rush plus jump boost and regeneration.";
        vigor.color = "#C2185B";
        PotionEffect v1 = new PotionEffect();
        v1.effect = "rubymod:ruby_rush";
        v1.seconds = 90;
        PotionEffect v2 = new PotionEffect();
        v2.effect = "minecraft:jump_boost";
        v2.seconds = 90;
        v2.amplifier = 1;
        PotionEffect v3 = new PotionEffect();
        v3.effect = "minecraft:regeneration";
        v3.seconds = 20;
        vigor.effects.addAll(List.of(v1, v2, v3));
        p.elements.addAll(List.of(rush, vigor, edge, shard, sunset, waltz, disc, volley));
    }

    /** A four-stage crop whose seeds are planted on farmland and drop ruby berries when ripe. */
    private static void crop(Project p, int color) {
        Block crop = new Block();
        crop.name = "ruby_berry_bush";
        crop.displayName = "Ruby Berry Seeds";
        crop.setLoc("name", "ja_jp", "ルビーベリーの種");
        crop.shape = "crop";
        crop.tool = "none";
        crop.requiresTool = false;
        crop.hardness = 0;
        crop.resistance = 0;
        crop.sound = "crop";
        crop.creativeTab = "tab:rubies";
        for (int i = 0; i < 4; i++) {
            TextureAsset tex = TextureAsset.of("ruby_berry_bush_stage" + i, TextureTemplates.cropStage(i, 4, color), 16, 16);
            p.textures.add(tex);
            crop.stageTextures.add(tex.id);
        }
        TextureAsset seeds = TextureAsset.of("ruby_berry_seeds", TextureTemplates.generate("seeds", 0xFFB08850), 16, 16);
        p.textures.add(seeds);
        crop.texture = seeds.id;

        TextureAsset berryTex = TextureAsset.of("ruby_berry", TextureTemplates.generate("gem", color), 16, 16);
        p.textures.add(berryTex);
        Item berry = new Item();
        berry.name = "ruby_berry";
        berry.displayName = "Ruby Berry";
        berry.setLoc("name", "ja_jp", "ルビーベリー");
        berry.kind = "food";
        berry.nutrition = 3;
        berry.saturation = 0.4;
        berry.texture = berryTex.id;
        berry.creativeTab = "tab:rubies";
        crop.cropProduce = "rubymod:ruby_berry";
        crop.cropProduceMin = 1;
        crop.cropProduceMax = 3;
        p.elements.addAll(List.of(crop, berry));
    }

    /** A cutscene that circles the player and ends with a title, started by /ruby_intro. */
    private static void cutscene(Project p) {
        Systems.Cutscene intro = new Systems.Cutscene();
        intro.name = "ruby_intro";
        double[][] pts = {{0, 3, -10, 0}, {9, 5, -4, 3}, {9, 7, 6, 6}, {-6, 9, 8, 9}, {0, 2.5, -3, 12}};
        for (double[] q : pts) {
            Systems.CameraKey k = new Systems.CameraKey();
            k.x = q[0];
            k.y = q[1];
            k.z = q[2];
            k.time = q[3];
            k.lookAt = true;
            k.ty = 1.5;
            intro.keys.add(k);
        }
        intro.keys.get(4).easing = "ease_out";
        String[][] ev = {{"0.5", "title", "Ruby Mod", "red", "3"}, {"0.5", "sound", "minecraft:music_disc.cat", "white", "1"},
                {"5", "subtitle", "Find rubies deep underground", "gold", "3"}, {"10.5", "fade", "", "white", "1.5"}};
        for (String[] e : ev) {
            Systems.CameraEvent ce = new Systems.CameraEvent();
            ce.time = Double.parseDouble(e[0]);
            ce.kind = e[1];
            ce.text = e[2];
            ce.color = e[3];
            ce.duration = Double.parseDouble(e[4]);
            intro.events.add(ce);
        }
        intro.onEnd.add(step("message", "text", "Welcome to the Ruby Mod!", "color", "red"));
        Elements.Command cmd = new Elements.Command();
        cmd.name = "ruby_intro";
        cmd.description = "Plays the Ruby Mod intro cutscene";
        cmd.steps.add(step("cutscene_play", "cutscene", "ruby_intro", "target", "@s"));
        p.elements.addAll(List.of(intro, cmd));
    }
}
