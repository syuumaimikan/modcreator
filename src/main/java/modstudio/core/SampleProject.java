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
        shop.title = "&4Ruby Shop";
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
        shop.slots.addAll(List.of(sword, realmTrip, stats, close));

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

        p.elements.addAll(List.of(rush, edge, shard, sunset, waltz, disc, volley));
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
