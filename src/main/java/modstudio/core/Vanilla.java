package modstudio.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Vanilla identifiers used by pickers and validation. Not exhaustive; any id may also be typed manually. */
public final class Vanilla {
    private Vanilla() {}

    private static List<String> split(String s) {
        List<String> r = new ArrayList<>();
        for (String x : s.trim().split("\\s+")) if (!x.isEmpty()) r.add("minecraft:" + x);
        return r;
    }

    public static final List<String> BLOCKS = split("""
        stone granite polished_granite diorite polished_diorite andesite polished_andesite deepslate cobbled_deepslate
        polished_deepslate deepslate_bricks deepslate_tiles calcite tuff dripstone_block grass_block dirt coarse_dirt podzol
        rooted_dirt mud cobblestone oak_planks spruce_planks birch_planks jungle_planks acacia_planks dark_oak_planks
        mangrove_planks cherry_planks bamboo_planks crimson_planks warped_planks bedrock sand red_sand gravel
        coal_ore deepslate_coal_ore iron_ore deepslate_iron_ore copper_ore deepslate_copper_ore gold_ore deepslate_gold_ore
        redstone_ore deepslate_redstone_ore emerald_ore deepslate_emerald_ore lapis_ore deepslate_lapis_ore diamond_ore
        deepslate_diamond_ore nether_gold_ore nether_quartz_ore ancient_debris coal_block raw_iron_block raw_copper_block
        raw_gold_block amethyst_block iron_block copper_block gold_block diamond_block emerald_block lapis_block
        redstone_block netherite_block oak_log spruce_log birch_log jungle_log acacia_log dark_oak_log mangrove_log
        cherry_log crimson_stem warped_stem stripped_oak_log oak_wood oak_leaves spruce_leaves birch_leaves
        sponge glass tinted_glass white_wool orange_wool magenta_wool light_blue_wool yellow_wool lime_wool pink_wool
        gray_wool light_gray_wool cyan_wool purple_wool blue_wool brown_wool green_wool red_wool black_wool
        bricks bookshelf mossy_cobblestone obsidian crying_obsidian torch chest crafting_table furnace blast_furnace
        smoker campfire anvil enchanting_table ladder rail snow ice packed_ice blue_ice snow_block clay pumpkin
        jack_o_lantern melon netherrack soul_sand soul_soil basalt glowstone stone_bricks mossy_stone_bricks
        cracked_stone_bricks end_stone end_stone_bricks purpur_block prismarine sea_lantern hay_block terracotta
        white_concrete black_concrete red_concrete blue_concrete magma_block nether_bricks bone_block quartz_block
        slime_block honey_block target lodestone respawn_anchor sculk barrier spawner beacon tnt water lava air
        dandelion poppy blue_orchid allium oxeye_daisy cornflower lily_of_the_valley sunflower short_grass fern
        dead_bush cactus sugar_cane bamboo vine brown_mushroom red_mushroom lantern soul_lantern chain bell
        """);

    public static final List<String> ITEMS = split("""
        stick coal charcoal diamond emerald lapis_lazuli quartz amethyst_shard raw_iron raw_copper raw_gold
        iron_ingot copper_ingot gold_ingot netherite_ingot netherite_scrap iron_nugget gold_nugget redstone glowstone_dust
        string feather gunpowder leather rabbit_hide paper book writable_book enchanted_book flint bone bone_meal
        slime_ball ender_pearl ender_eye blaze_rod blaze_powder ghast_tear magma_cream nether_star nether_wart
        prismarine_shard prismarine_crystals shulker_shell phantom_membrane echo_shard heart_of_the_sea nautilus_shell
        honeycomb honey_bottle clay_ball brick nether_brick glass_bottle experience_bottle snowball egg arrow
        spectral_arrow totem_of_undying name_tag lead saddle compass recovery_compass clock spyglass map filled_map
        bucket water_bucket lava_bucket milk_bucket powder_snow_bucket fishing_rod carrot_on_a_stick
        warped_fungus_on_a_stick flint_and_steel shears shield bow crossbow trident elytra firework_rocket
        wooden_sword stone_sword iron_sword golden_sword diamond_sword netherite_sword
        wooden_pickaxe stone_pickaxe iron_pickaxe golden_pickaxe diamond_pickaxe netherite_pickaxe
        wooden_axe stone_axe iron_axe golden_axe diamond_axe netherite_axe
        wooden_shovel stone_shovel iron_shovel golden_shovel diamond_shovel netherite_shovel
        wooden_hoe stone_hoe iron_hoe golden_hoe diamond_hoe netherite_hoe
        leather_helmet leather_chestplate leather_leggings leather_boots iron_helmet iron_chestplate iron_leggings iron_boots
        golden_helmet golden_chestplate golden_leggings golden_boots diamond_helmet diamond_chestplate diamond_leggings
        diamond_boots netherite_helmet netherite_chestplate netherite_leggings netherite_boots turtle_helmet
        apple golden_apple enchanted_golden_apple bread carrot golden_carrot potato baked_potato poisonous_potato beetroot
        beetroot_soup melon_slice glistering_melon_slice sweet_berries glow_berries chorus_fruit cookie cake pumpkin_pie
        mushroom_stew rabbit_stew suspicious_stew dried_kelp beef cooked_beef porkchop cooked_porkchop chicken
        cooked_chicken mutton cooked_mutton rabbit cooked_rabbit cod cooked_cod salmon cooked_salmon tropical_fish
        pufferfish rotten_flesh spider_eye fermented_spider_eye wheat wheat_seeds pumpkin_seeds melon_seeds
        beetroot_seeds cocoa_beans sugar kelp white_dye orange_dye magenta_dye light_blue_dye yellow_dye lime_dye
        pink_dye gray_dye light_gray_dye cyan_dye purple_dye blue_dye brown_dye green_dye red_dye black_dye ink_sac
        glow_ink_sac potion splash_potion lingering_potion music_disc_13 music_disc_cat armor_stand item_frame painting
        minecart boat oak_boat chest_minecart netherite_upgrade_smithing_template
        """);

    public static final List<String> ENTITIES = split("""
        zombie skeleton creeper spider cave_spider enderman witch slime magma_cube blaze ghast piglin zombified_piglin
        hoglin zoglin wither_skeleton stray husk drowned phantom pillager vindicator evoker ravager vex guardian
        elder_guardian shulker silverfish endermite warden wither ender_dragon pig cow sheep chicken rabbit horse donkey
        mule llama wolf cat ocelot fox panda polar_bear bee goat axolotl frog turtle dolphin squid glow_squid cod salmon
        parrot bat villager wandering_trader iron_golem snow_golem allay sniffer camel armadillo breeze player
        item experience_orb arrow lightning_bolt tnt firework_rocket armor_stand boat minecart falling_block
        """);

    public static final List<String> EFFECTS = split("""
        speed slowness haste mining_fatigue strength instant_health instant_damage jump_boost nausea regeneration
        resistance fire_resistance water_breathing invisibility blindness night_vision hunger weakness poison wither
        health_boost absorption saturation glowing levitation luck unluck slow_falling conduit_power dolphins_grace
        bad_omen hero_of_the_village darkness
        """);

    /** Potion ids; the last four exist from 1.20.5 (NEW_POTIONS, paths only). */
    public static final List<String> POTIONS = split("""
        water mundane thick awkward night_vision long_night_vision invisibility long_invisibility leaping long_leaping
        strong_leaping fire_resistance long_fire_resistance swiftness long_swiftness strong_swiftness slowness
        long_slowness strong_slowness turtle_master long_turtle_master strong_turtle_master water_breathing
        long_water_breathing healing strong_healing harming strong_harming poison long_poison strong_poison regeneration
        long_regeneration strong_regeneration strength long_strength strong_strength weakness long_weakness luck
        slow_falling long_slow_falling wind_charged weaving oozing infested
        """);

    public static final List<String> NEW_POTIONS = List.of("wind_charged", "weaving", "oozing", "infested");

    public static final List<String> PARTICLES = split("""
        heart happy_villager angry_villager flame soul_fire_flame smoke large_smoke cloud crit enchanted_hit enchant
        end_rod portal reverse_portal totem_of_undying note explosion firework witch dragon_breath bubble splash
        dripping_water dripping_lava lava ash white_ash snowflake glow electric_spark scrape wax_on wax_off cherry_leaves
        sonic_boom sculk_soul soul composter instant_effect effect
        """);

    public static final List<String> SOUNDS = split("""
        entity.experience_orb.pickup entity.player.levelup entity.item.pickup block.note_block.pling block.note_block.bell
        block.note_block.chime block.note_block.harp block.amethyst_block.chime block.anvil.use block.anvil.land
        block.chest.open block.chest.close block.beacon.activate block.beacon.power_select block.portal.travel
        block.enchantment_table.use entity.ender_dragon.growl entity.wither.spawn entity.lightning_bolt.thunder
        entity.generic.explode entity.firework_rocket.launch entity.firework_rocket.blast entity.villager.yes
        entity.villager.no entity.cat.ambient entity.wolf.howl entity.blaze.shoot entity.ghast.shoot ui.button.click
        ui.toast.challenge_complete item.totem.use item.trident.thunder item.armor.equip_diamond block.bell.use
        """);

    public static final List<String> DAMAGE_TYPES = split("""
            generic magic indirect_magic in_fire on_fire lava hot_floor lightning_bolt freeze drown starve cactus fall
            fly_into_wall out_of_world wither dragon_breath sweet_berry_bush sting thorns player_attack mob_attack arrow
            trident fireball explosion player_explosion sonic_boom falling_block falling_anvil cramming generic_kill""");

    public static final List<String> ENCHANTMENTS = split("""
        protection fire_protection feather_falling blast_protection projectile_protection respiration aqua_affinity
        thorns depth_strider frost_walker binding_curse soul_speed swift_sneak sharpness smite bane_of_arthropods
        knockback fire_aspect looting sweeping_edge efficiency silk_touch unbreaking fortune power punch flame infinity
        luck_of_the_sea lure loyalty impaling riptide channeling multishot quick_charge piercing mending vanishing_curse
        """);

    public static final List<String> ITEM_TAGS = List.of(
            "#minecraft:planks", "#minecraft:logs", "#minecraft:wool", "#minecraft:stone_crafting_materials",
            "#minecraft:coals", "#minecraft:fishes", "#minecraft:arrows", "#minecraft:saplings", "#minecraft:leaves",
            "#minecraft:sand", "#minecraft:music_discs", "#minecraft:boats", "#minecraft:swords", "#minecraft:pickaxes",
            "#minecraft:axes", "#minecraft:shovels", "#minecraft:hoes", "#minecraft:beacon_payment_items",
            "#minecraft:piglin_loved", "#minecraft:trim_materials", "#c:ingots", "#c:gems", "#c:nuggets", "#c:dusts",
            "#c:ores", "#c:storage_blocks", "#c:rods/wooden");

    public static final List<String> BLOCK_TAGS = List.of(
            "#minecraft:mineable/pickaxe", "#minecraft:mineable/axe", "#minecraft:mineable/shovel",
            "#minecraft:mineable/hoe", "#minecraft:needs_stone_tool", "#minecraft:needs_iron_tool",
            "#minecraft:needs_diamond_tool", "#minecraft:logs", "#minecraft:planks", "#minecraft:leaves",
            "#minecraft:beacon_base_blocks", "#minecraft:stone_ore_replaceables", "#minecraft:deepslate_ore_replaceables",
            "#minecraft:base_stone_overworld", "#minecraft:base_stone_nether", "#minecraft:dirt",
            "#minecraft:wither_immune", "#minecraft:dragon_immune", "#c:ores", "#c:storage_blocks");

    public static final List<String> DIMENSIONS = List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");

    /** Vanilla creative tabs (resource keys) and their constant names in CreativeModeTabs. */
    public static final Map<String, String> CREATIVE_TABS = new LinkedHashMap<>();
    static {
        CREATIVE_TABS.put("minecraft:building_blocks", "BUILDING_BLOCKS");
        CREATIVE_TABS.put("minecraft:colored_blocks", "COLORED_BLOCKS");
        CREATIVE_TABS.put("minecraft:natural_blocks", "NATURAL_BLOCKS");
        CREATIVE_TABS.put("minecraft:functional_blocks", "FUNCTIONAL_BLOCKS");
        CREATIVE_TABS.put("minecraft:redstone_blocks", "REDSTONE_BLOCKS");
        CREATIVE_TABS.put("minecraft:tools_and_utilities", "TOOLS_AND_UTILITIES");
        CREATIVE_TABS.put("minecraft:combat", "COMBAT");
        CREATIVE_TABS.put("minecraft:food_and_drinks", "FOOD_AND_DRINKS");
        CREATIVE_TABS.put("minecraft:ingredients", "INGREDIENTS");
        CREATIVE_TABS.put("minecraft:spawn_eggs", "SPAWN_EGGS");
    }

    /** Vanilla items usable as the base of custom-model-data items (datapacks and plugins), with their item model parent. */
    public static final Map<String, String> CMD_BASES = new LinkedHashMap<>();
    static {
        for (String s : List.of("paper", "stick", "iron_ingot", "gold_ingot", "diamond", "emerald", "amethyst_shard",
                "flint", "bone", "feather", "string", "slime_ball", "ender_pearl", "apple", "bread", "cookie", "carrot",
                "potato", "baked_potato", "beef", "cooked_beef", "golden_apple", "melon_slice", "sweet_berries", "book",
                "map", "clay_ball", "brick", "nether_star", "sugar")) {
            CMD_BASES.put("minecraft:" + s, "minecraft:item/generated");
        }
        for (String s : List.of("wooden_sword", "stone_sword", "iron_sword", "golden_sword", "diamond_sword",
                "netherite_sword", "iron_pickaxe", "diamond_pickaxe", "iron_axe", "diamond_axe", "iron_shovel",
                "diamond_shovel", "iron_hoe", "diamond_hoe", "blaze_rod")) {
            CMD_BASES.put("minecraft:" + s, "minecraft:item/handheld");
        }
        for (String s : List.of("leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots")) {
            CMD_BASES.put("minecraft:" + s, "minecraft:item/generated");
        }
        for (String s : List.of("13", "cat", "blocks", "chirp", "far", "mall", "mellohi", "stal", "strad", "ward", "11", "wait",
                "otherside", "5", "pigstep", "relic")) {
            CMD_BASES.put("minecraft:music_disc_" + s, "minecraft:item/template_music_disc");
        }
        CMD_BASES.put("minecraft:carrot_on_a_stick", "minecraft:item/handheld_rod");
        CMD_BASES.put("minecraft:warped_fungus_on_a_stick", "minecraft:item/handheld_rod");
    }

    public static final List<String> SOUND_TYPES = Arrays.asList("stone", "wood", "gravel", "grass", "metal", "glass",
            "wool", "sand", "snow", "amethyst", "deepslate", "copper", "netherite", "chain", "lantern", "bone", "slime", "crop");

    public static List<String> allItems() {
        List<String> r = new ArrayList<>(ITEMS);
        r.addAll(BLOCKS);
        return r;
    }
}
