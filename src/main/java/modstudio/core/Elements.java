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
        public String kind = "basic"; // basic, food, sword, pickaxe, axe, shovel, hoe
        public String creativeTab = "minecraft:ingredients";
        public int maxStack = 64;
        public String rarity = "common";
        public boolean fireResistant;
        public boolean glint;
        public String tooltip = "";
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

        @Override public ElementType type() { return ElementType.ITEM; }
        @Override public String subtitle() { return displayName; }
        public boolean isTool() { return List.of("sword", "pickaxe", "axe", "shovel", "hoe").contains(kind); }
    }

    public static final class Block extends Element {
        public String displayName = "";
        public String shape = "cube"; // cube, cube_bottom_top, pillar, cross, model
        /** Name of a project model used when shape is "model". */
        public String model = "";
        /** Collision and outline from the model cuboids instead of a full cube. */
        public boolean modelCollision = true;
        /** Horizontal facing chosen from the player when placed. */
        public boolean rotatable;

        /** Needs the cutout render layer and no occlusion (see-through pixels or a non-full shape). */
        public boolean cutout() { return transparent || shape.equals("cross") || shape.equals("model"); }
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
        public String recipeType = "shaped"; // shaped, shapeless, smelting, blasting, smoking, campfire, stonecutting, smithing
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
        public List<LootEntry> entries = new ArrayList<>();
    }

    public static final class LootTable extends Element {
        public String lootType = "chest"; // block, entity, chest, gift, fishing, generic
        public String replaces = "";
        public List<LootPool> pools = new ArrayList<>();

        @Override public ElementType type() { return ElementType.LOOT_TABLE; }
        @Override public String subtitle() { return replaces.isBlank() ? lootType : replaces; }
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

        @Override public ElementType type() { return ElementType.MODEL; }
        @Override public String subtitle() { return cubes.size() + " cubes"; }
    }

    public static final class Cube {
        public String name = "cube";
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
