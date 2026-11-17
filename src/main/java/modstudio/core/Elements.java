package modstudio.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** All element kinds and their nested data structures. */
public final class Elements {
    private Elements() {}

    public static final class Item extends Element {
        public String displayName = "";
        public String texture = "";
        public String kind = "basic"; // basic, food, sword, pickaxe, axe, shovel, hoe, helmet, chestplate, leggings, boots
        public String creativeTab = "minecraft:ingredients";
        public int maxStack = 64;
        public String rarity = "common";
        public boolean fireResistant;
        public boolean glint;
        public String tooltip = "";
        /** Own tooltip box: colours (or textures) of the background and the frame. */
        public boolean customTooltip;
        public String tooltipBackground = "#100010";
        public int tooltipBackgroundOpacity = 94;
        public String tooltipBorderTop = "#5000FF";
        public String tooltipBorderBottom = "#28007F";
        public int tooltipBorderOpacity = 31;
        /** Optional 100 x 100 nine-slice pictures that replace the drawn box (1.21.2+). */
        public String tooltipBackgroundTexture = "";
        public String tooltipFrameTexture = "";
        /** Extra lines shown while Shift is held (mod loaders); other platforms show them after the tooltip. */
        public String shiftTooltip = "";
        public int nutrition = 4;
        public double saturation = 0.3;
        public boolean alwaysEdible;
        public boolean fastFood;
        public String tier = "iron";
        public double attackDamage = 3;
        public double attackSpeed = -2.4;
        public int burnTime = 0;
        public String baseItem = "minecraft:paper";
        public int customModelData = 0;
        /** Name of a project model; empty uses the flat texture. */
        public String model = "";
        /** Armor material element used by helmet, chestplate, leggings and boots. */
        public String armorMaterial = "";
        /** Jukebox song element: the item plays it in a jukebox (1.21+). */
        public String song = "";

        @Override public ElementType type() { return ElementType.ITEM; }
        @Override public String subtitle() { return displayName; }
        public boolean isTool() { return List.of("sword", "pickaxe", "axe", "shovel", "hoe").contains(kind); }
        public boolean isArmor() { return ARMOR_KINDS.contains(kind); }
        /** Tools and armor have durability and never stack. */
        public boolean damageable() { return isTool() || isArmor(); }
        public static final List<String> ARMOR_KINDS = List.of("helmet", "chestplate", "leggings", "boots");
    }

    /** Shared stats and worn textures of an armor set; the pieces are items of kind helmet..boots. */
    public static final class ArmorMaterial extends Element {
        /** Durability multiplier: helmet 11, chestplate 16, leggings 15, boots 13 times this value (iron 15, diamond 33). */
        public int durability = 15;
        public int helmet = 2, chestplate = 6, leggings = 5, boots = 2;
        public double toughness = 0;
        public double knockbackResistance = 0;
        public int enchantability = 9;
        /** leather, chain, iron, gold, diamond, netherite, turtle, generic */
        public String equipSound = "iron";
        /** Item or #tag that repairs the pieces in an anvil. */
        public String repairItem = "";
        /** 64x32 worn texture for helmet, chestplate and boots (layer 1) and for leggings (layer 2). */
        public String layer1 = "";
        public String layer2 = "";

        @Override public ElementType type() { return ElementType.ARMOR_MATERIAL; }
        @Override public String subtitle() { return helmet + " / " + chestplate + " / " + leggings + " / " + boots; }

        public int defense(String kind) {
            return switch (kind) {
                case "helmet" -> helmet;
                case "chestplate" -> chestplate;
                case "leggings" -> leggings;
                default -> boots;
            };
        }

        /** Vanilla durability of a piece for this multiplier. */
        public int durabilityOf(String kind) {
            int unit = switch (kind) {
                case "helmet" -> 11;
                case "chestplate" -> 16;
                case "leggings" -> 15;
                default -> 13;
            };
            return unit * Math.max(1, durability);
        }
    }

    public static final class Block extends Element {
        public String displayName = "";
        public String shape = "cube"; // cube, cube_bottom_top, pillar, cross, model, crop
        /** Name of a project model used when shape is "model". */
        public String model = "";
        /** Collision and outline from the model cuboids instead of a full cube. */
        public boolean modelCollision = true;
        /** Horizontal facing chosen from the player when placed. */
        public boolean rotatable;

        /** Needs the cutout render layer and no occlusion (see-through pixels or a non-full shape). */
        public boolean cutout() { return transparent || shape.equals("cross") || shape.equals("model") || isCrop(); }
        /** A farmland crop: the block item is the seed, it grows through ages 0 to 7 and drops the produce when ripe. */
        public boolean isCrop() { return shape.equals("crop"); }
        /** Growth stage textures from sprout to ripe (1 to 8); the 8 ages are spread over them. */
        public List<String> stageTextures = new ArrayList<>();
        /** Item dropped by a ripe crop; empty drops more seeds. */
        public String cropProduce = "";
        public int cropProduceMin = 1;
        public int cropProduceMax = 3;
        /** Ripe crops also drop 0 to 3 extra seeds (fortune adds more). */
        public boolean cropSeedBonus = true;

        /** Stage texture index used at an age (0..7) when the crop has the given number of stages. */
        public static int stageOf(int age, int stages) {
            return stages <= 0 ? 0 : Math.min(stages - 1, age * stages / 8);
        }
        public String texture = "";
        public String textureTop = "";
        public String textureBottom = "";
        public String textureSide = "";
        public String sound = "stone";
        public double hardness = 3;
        public double resistance = 3;
        public int light = 0;
        public String tool = "pickaxe"; // none, pickaxe, axe, shovel, hoe
        public String toolLevel = "wood"; // wood, stone, iron, diamond
        public boolean requiresTool = true;
        public boolean transparent;
        public double friction = 0.6;
        public double speedFactor = 1.0;
        public double jumpFactor = 1.0;
        public String creativeTab = "minecraft:building_blocks";
        public String drop = "self"; // self, item, none
        public String dropItem = "";
        public int dropMin = 1;
        public int dropMax = 1;
        public boolean silkTouch = true;
        public boolean fortune = true;
        public boolean oreGen;
        public String oreDimension = "overworld";
        public int oreSize = 8;
        public int oreCount = 10;
        public int oreMinY = -64;
        public int oreMaxY = 64;

        @Override public ElementType type() { return ElementType.BLOCK; }
        @Override public String subtitle() { return displayName; }

        /** Texture asset id for a face: top, bottom, side, all. */
        public String face(String f) {
            switch (shape) {
                case "cube_bottom_top":
                    if (f.equals("top")) return textureTop;
                    if (f.equals("bottom")) return textureBottom;
                    return textureSide;
                case "pillar":
                    if (f.equals("top") || f.equals("bottom")) return textureTop;
                    return textureSide;
                default:
                    return texture;
            }
        }
    }

    // ------------------------------------------------------------------ effects, enchantments and registry data

    /** A custom status effect (mob effect) with attribute changes, a tick procedure and an optional potion. */
    public static final class StatusEffect extends Element {
        public String displayName = "";
        public String color = "#E0115F";
        /** beneficial, harmful, neutral */
        public String category = "beneficial";
        /** 18 x 18 (or 16 x 16) icon shown in the inventory and the HUD. */
        public String icon = "";
        public List<EffectModifier> modifiers = new ArrayList<>();
        public List<Step> onTick = new ArrayList<>();
        public int tickInterval = 20;
        public boolean potion = true;
        public String potionName = "";
        public int potionSeconds = 180;
        public int potionAmplifier = 0;

        @Override public ElementType type() { return ElementType.STATUS_EFFECT; }
        @Override public String subtitle() { return displayName; }
    }

    /** A brewable potion with any number of vanilla or project effects. */
    public static final class Potion extends Element {
        public String displayName = "";
        /** Names of the other bottles; blank means the display name with a prefix. */
        public String splashName = "";
        public String lingeringName = "";
        public String arrowName = "";
        /** Liquid colour for data packs and plugins; mods mix the colours of the effects like vanilla. */
        public String color = "#3F76E4";
        public List<PotionEffect> effects = new ArrayList<>();

        @Override public ElementType type() { return ElementType.POTION; }
        @Override public String subtitle() { return displayName; }
    }

    public static final class PotionEffect {
        public String effect = "minecraft:speed";
        public int seconds = 180;
        public int amplifier = 0;
        public boolean particles = true;
        public boolean icon = true;
    }

    /** Attribute change while an effect is active; amounts grow with the level like vanilla effects. */
    public static final class EffectModifier {
        public String attribute = "movement_speed";
        public double amount = 0.2;
        /** add_value, add_multiplied_base, add_multiplied_total */
        public String operation = "add_multiplied_total";
    }

    /** A data driven enchantment (Minecraft 1.21 and later). */
    public static final class Enchantment extends Element {
        public String displayName = "";
        /** Item tag or item the enchantment can be applied to. */
        public String supportedItems = "#minecraft:enchantable/weapon";
        /** Items it appears on in the enchanting table; empty uses the supported items. */
        public String primaryItems = "";
        /** any, mainhand, offhand, hand, head, chest, legs, feet, armor, body */
        public String slot = "mainhand";
        public int maxLevel = 3;
        public int weight = 5;
        public int anvilCost = 2;
        public int minCostBase = 5, minCostPerLevel = 8;
        public int maxCostBase = 25, maxCostPerLevel = 8;
        /** Only from loot and trading, not the enchanting table. */
        public boolean treasure;
        public boolean curse;
        /** Enchantment tag or id that cannot be combined with this one. */
        public String exclusiveWith = "";
        public double damagePerLevel = 0;
        public double protectionPerLevel = 0;
        public String attribute = "";
        public double attributePerLevel = 0;
        public String attributeOperation = "add_value";
        /** Runs as the hit entity after an attack with the enchanted item. */
        public List<Step> onHit = new ArrayList<>();
        /** Runs every tick as the entity wearing or holding the enchanted item. */
        public List<Step> onTick = new ArrayList<>();

        @Override public ElementType type() { return ElementType.ENCHANTMENT; }
        @Override public String subtitle() { return displayName; }
    }

    /** A damage type with its own death message, usable by the damage step and in damage type tags. */
    public static final class DamageType extends Element {
        /** English death message: %1$s is the victim. */
        public String deathMessage = "";
        /** Death message when another entity caused the damage: %2$s is the attacker. */
        public String deathMessageAttacker = "";
        public double exhaustion = 0.1;
        /** never, when_caused_by_living_non_player, always */
        public String scaling = "when_caused_by_living_non_player";
        /** hurt, thorns, drowning, burning, poking, freezing */
        public String effects = "hurt";
        public boolean bypassesArmor, bypassesShield, fire, projectile, explosion, noKnockback, magic;

        @Override public ElementType type() { return ElementType.DAMAGE_TYPE; }
        @Override public String subtitle() { return effects; }
    }

    /** A painting variant: a texture placed on walls by the painting item. */
    public static final class Painting extends Element {
        public String title = "";
        public String author = "";
        public String texture = "";
        /** Size in blocks (1 to 16); the texture is 16 pixels per block. */
        public int width = 1, height = 1;
        /** Can be picked when a player places a painting (minecraft:placeable tag). */
        public boolean placeable = true;

        @Override public ElementType type() { return ElementType.PAINTING; }
        @Override public String subtitle() { return width + " x " + height; }
    }

    /** A jukebox song (1.21+): items that point at it become music discs. */
    public static final class Song extends Element {
        public String description = "";
        /** Project sound name or a sound event id. */
        public String sound = "";
        public double lengthSeconds = 120;
        public int comparatorOutput = 1;

        @Override public ElementType type() { return ElementType.SONG; }
        @Override public String subtitle() { return description; }
    }

    public static final class CreativeTab extends Element {
        public String displayName = "";
        public String icon = "minecraft:diamond";

        @Override public ElementType type() { return ElementType.CREATIVE_TAB; }
        @Override public String subtitle() { return displayName; }
    }

    public static final class SoundFile {
        public String fileName = "";
        public String data = ""; // base64 ogg
    }

    public static final class Sound extends Element {
        public String subtitle = "";
        public String category = "neutral";
        public boolean stream;
        public List<SoundFile> files = new ArrayList<>();

        @Override public ElementType type() { return ElementType.SOUND; }
        @Override public String subtitle() { return subtitle; }
    }

    public static final class Recipe extends Element {
        /** shaped, shapeless, smelting, blasting, smoking, campfire, stonecutting, smithing, brewing (input potion + addition -> result potion) */
        public String recipeType = "shaped";
        public List<String> grid = new ArrayList<>(Arrays.asList("", "", "", "", "", "", "", "", ""));
        public String input = "";
        public String result = "";
        public int count = 1;
        public double experience = 0.1;
        public int cookTime = 200;
        public String template = "";
        public String base = "";
        public String addition = "";
        public String group = "";

        @Override public ElementType type() { return ElementType.RECIPE; }
        @Override public String subtitle() { return result; }

        public boolean isCooking() {
            return List.of("smelting", "blasting", "smoking", "campfire").contains(recipeType);
        }
    }

    public static final class LootEntry {
        public String kind = "item"; // item, tag, loot_table, empty
        public String name = "";
        public int weight = 1;
        public int countMin = 1;
        public int countMax = 1;
        public boolean fortune;
        public boolean enchant;
    }

    public static final class LootPool {
        public int rollsMin = 1;
        public int rollsMax = 1;
        public double bonusRolls = 0;
        public double chance = 1.0;
        public boolean killedByPlayer;
        /** Name of a project predicate (or a namespaced id) the pool also needs. */
        public String predicate = "";
        public List<LootEntry> entries = new ArrayList<>();
    }

    public static final class LootTable extends Element {
        public String lootType = "chest"; // block, entity, chest, gift, fishing, generic
        public String replaces = "";
        /** Adds the pools to the table named in replaces instead of overwriting it (mods and plugins). */
        public boolean inject;
        public List<LootPool> pools = new ArrayList<>();

        @Override public ElementType type() { return ElementType.LOOT_TABLE; }
        @Override public String subtitle() { return replaces.isBlank() ? lootType : (inject ? "+ " : "") + replaces; }
    }

    public static final class Advancement extends Element {
        public String title = "";
        public String description = "";
        public String icon = "minecraft:diamond";
        public String frame = "task";
        public String parent = "";
        public String background = "minecraft:textures/block/stone.png";
        public boolean showToast = true;
        public boolean announce = true;
        public boolean hidden;
        public String trigger = "inventory_changed";
        public String triggerValue = "";
        public int rewardXp = 0;
        public String rewardFunction = "";
        public String rewardLoot = "";

        @Override public ElementType type() { return ElementType.ADVANCEMENT; }
        @Override public String subtitle() { return title; }
    }

    public static final class Tag extends Element {
        public String registry = "item"; // item, block, entity_type, function, fluid
        public String namespace = "";
        public boolean replace;
        public List<String> values = new ArrayList<>();

        @Override public ElementType type() { return ElementType.TAG; }
        @Override public String subtitle() { return registry; }
    }

    public static final class Function extends Element {
        public String code = "";
        public boolean onLoad;
        public boolean onTick;

        @Override public ElementType type() { return ElementType.FUNCTION; }
        @Override public String subtitle() { return onTick ? "tick" : onLoad ? "load" : ""; }
    }

    /** One test of a predicate: a kind from {@link Logic#PREDICATE_TERMS} with its values. */
    public static final class PredicateTerm {
        public String kind = "random_chance";
        public Map<String, String> p = new LinkedHashMap<>();
        /** Passes when the test fails. */
        public boolean invert;

        public String get(String k) { return p.getOrDefault(k, ""); }
    }

    /** A reusable condition file (data/ns/predicate): procedures, loot pools and commands can all test it. */
    public static final class Predicate extends Element {
        /** all: every term has to pass. any: one passing term is enough. */
        public String combine = "all";
        public List<PredicateTerm> terms = new ArrayList<>();

        @Override public ElementType type() { return ElementType.PREDICATE; }
        @Override public String subtitle() {
            StringBuilder sb = new StringBuilder();
            for (PredicateTerm t : terms) {
                if (!sb.isEmpty()) sb.append(combine.equals("any") ? " | " : " & ");
                if (t.invert) sb.append('!');
                sb.append(t.kind);
            }
            return sb.toString();
        }
    }

    /** One step of an item modifier: a kind from {@link Logic#MODIFIER_FUNCTIONS} with its values. */
    public static final class ModifierFunction {
        public String kind = "set_count";
        public Map<String, String> p = new LinkedHashMap<>();

        public String get(String k) { return p.getOrDefault(k, ""); }
    }

    /** Changes to an item stack (data/ns/item_modifier), applied with the modify item step or /item modify. */
    public static final class ItemModifier extends Element {
        public List<ModifierFunction> functions = new ArrayList<>();

        @Override public ElementType type() { return ElementType.ITEM_MODIFIER; }
        @Override public String subtitle() {
            StringBuilder sb = new StringBuilder();
            for (ModifierFunction f : functions) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(f.kind);
            }
            return sb.toString();
        }
    }

    public static final class Condition {
        public String kind = "chance";
        public Map<String, String> p = new LinkedHashMap<>();
        public boolean negate;

        public String get(String k) { return p.getOrDefault(k, ""); }
    }

    public static final class Step {
        public String kind = "message";
        public Map<String, String> p = new LinkedHashMap<>();
        public Condition cond;
        public List<Step> then = new ArrayList<>();
        public List<Step> orElse = new ArrayList<>();

        public String get(String k) { return p.getOrDefault(k, ""); }
    }

    public static final class Procedure extends Element {
        public String trigger = "player_join";
        public String triggerValue = "";
        public int interval = 20;
        public List<Step> steps = new ArrayList<>();

        @Override public ElementType type() { return ElementType.PROCEDURE; }
        @Override public String subtitle() { return trigger; }
    }

    public static final class Command extends Element {
        public int permission = 0;
        public String description = "";
        public List<Step> steps = new ArrayList<>();
        /** Sub commands with typed arguments, for example "give <target:player> <amount:int(1,64)>". */
        public List<CommandBranch> branches = new ArrayList<>();

        @Override public ElementType type() { return ElementType.COMMAND; }
        @Override public String subtitle() { return "/" + name; }
    }

    public static final class CommandBranch {
        public String path = "";
        public String description = "";
        /** Required permission level 0-4 on mods, op on Paper when above 0; -1 uses the command's level. */
        public int permission = -1;
        public List<Step> steps = new ArrayList<>();
    }

    public static final class CodeFile extends Element {
        public String target = "fabric";
        public String path = "src/main/java/{package}/custom/MyClass.java";
        public String content = "";

        @Override public ElementType type() { return ElementType.CODE_FILE; }
        @Override public String subtitle() { return path; }
    }

    // ------------------------------------------------------------------ 3D models

    /** A Java Edition block/item model made of cuboids, usable by blocks and items. */
    public static final class Model extends Element {
        public String kind = "block"; // block, item
        /** Texture variables: key ("0", "1", "particle") -> project texture asset id or a resource id like minecraft:block/stone. */
        public Map<String, String> textures = new LinkedHashMap<>();
        public List<Cube> cubes = new ArrayList<>();
        public boolean ambientOcclusion = true;
        /** Display transforms by context (gui, ground, fixed, head, firstperson_righthand, thirdperson_righthand). */
        public Map<String, Display> display = new LinkedHashMap<>();
        public int textureWidth = 16;
        public int textureHeight = 16;
        /** Bones for animation: each cube follows the bone named in Cube.bone (none follows the model itself). */
        public List<Bone> bones = new ArrayList<>();
        public List<Animation> animations = new ArrayList<>();

        public Bone bone(String name) {
            for (Bone b : bones) if (b.name.equals(name)) return b;
            return null;
        }

        public Animation animation(String name) {
            for (Animation a : animations) if (a.name.equals(name)) return a;
            return null;
        }

        @Override public ElementType type() { return ElementType.MODEL; }
        @Override public String subtitle() {
            return cubes.size() + " cubes" + (bones.isEmpty() ? "" : "  " + bones.size() + " bones")
                    + (animations.isEmpty() ? "" : "  " + animations.size() + " animations");
        }
    }

    /** A joint of an animated model; children move with their parent. The pivot is in model units. */
    public static final class Bone {
        public String name = "bone";
        public String parent = "";
        public double[] pivot = {8, 0, 8};
    }

    /** A named animation: keyframed rotation, position and scale of bones over time. */
    public static final class Animation {
        public String name = "idle";
        public double length = 2;
        /** loop, once (then back to the default animation), hold (stay on the last frame) */
        public String mode = "loop";
        public List<AnimTrack> tracks = new ArrayList<>();

        public AnimTrack track(String bone, String channel) {
            for (AnimTrack t : tracks) if (t.bone.equals(bone) && t.channel.equals(channel)) return t;
            return null;
        }
    }

    public static final class AnimTrack {
        public String bone = "";
        /** rotation (degrees), position (model units), scale (factor) */
        public String channel = "rotation";
        public List<AnimKey> keys = new ArrayList<>();
    }

    public static final class AnimKey {
        public double time;
        public double x, y, z;
        /** linear, smooth, step, ease_in, ease_out, ease_in_out */
        public String easing = "linear";
    }

    public static final class Cube {
        public String name = "cube";
        /** Bone this cube belongs to in an animated model; empty for none. */
        public String bone = "";
        public double[] from = {0, 0, 0};
        public double[] to = {16, 16, 16};
        public double rotation = 0; // -45, -22.5, 0, 22.5, 45
        public String axis = "y";
        public double[] origin = {8, 8, 8};
        public boolean rescale;
        public boolean shade = true;
        public Map<String, Face> faces = new LinkedHashMap<>();

        public Cube() {
            for (String f : FACES) faces.put(f, new Face());
        }

        public Face face(String dir) {
            return faces.computeIfAbsent(dir, k -> new Face());
        }

        public static final List<String> FACES = List.of("north", "east", "south", "west", "up", "down");
    }

    public static final class Face {
        public boolean enabled = true;
        public String texture = "0";
        /** u1, v1, u2, v2 in 0..16; null means automatic from the cube position. */
        public double[] uv;
        public int rotation;
        public String cullface = "";
        public int tintIndex = -1;
    }

    public static final class Display {
        public double[] rotation = {0, 0, 0};
        public double[] translation = {0, 0, 0};
        public double[] scale = {1, 1, 1};
    }
}
