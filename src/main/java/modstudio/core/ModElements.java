package modstudio.core;

import modstudio.core.Elements.Step;

import java.util.ArrayList;
import java.util.List;

/**
 * Extended mod-loader, worldgen, and data-driven registry element definitions.
 * Serialized into .msproj via Gson alongside {@link Elements} and {@link Systems}.
 */
public final class ModElements {
    private ModElements() {}

    /** Declared mod or plugin dependency stored on {@link Project.Meta}. */
    public static final class ModDependency {
        public String modId = "";
        public String version = "*";
        public boolean required = true;
    }

    /** Custom Gradle library / dependency or local JAR wired into generated builds. */
    public static final class GradleDep {
        /** implementation, compileOnly, runtimeOnly, modImplementation, modCompileOnly, jarJar, localJar */
        public String scope = "implementation";
        public String coordinate = "";
        /** all, mods, forge, neoforge, fabric, quilt, paper, folia */
        public String target = "all";
    }

    // ------------------------------------------------------------------ containers & tile entities

    public static final class SlotDef {
        public int index;
        public int x = 8;
        public int y = 18;
        /** generic, input, fuel, output, readonly */
        public String kind = "generic";
    }

    public static final class ContainerRecipe {
        public String input = "";
        public String secondInput = "";
        public String output = "";
        public int outputCount = 1;
        public int processTicks = 100;
        public boolean requiresFuel = false;
    }

    /** Block entity + inventory menu + screen + optional hopper/processing recipes (mods only). */
    public static final class Container extends Element {
        public String displayName = "";
        /** Project block id or full id that hosts this container. */
        public String block = "";
        public int rows = 3;
        public boolean useCustomSlots = false;
        public List<SlotDef> slots = new ArrayList<>();
        public List<ContainerRecipe> recipes = new ArrayList<>();
        /** 0: fill level, 1: processing progress */
        public int comparatorMode = 0;
        public boolean openOnRightClick = true;
        public List<Step> onOpen = new ArrayList<>();
        public List<Step> onClose = new ArrayList<>();
        public List<Step> onCraft = new ArrayList<>();

        @Override public ElementType type() { return ElementType.CONTAINER; }
        @Override public String subtitle() {
            return (useCustomSlots ? slots.size() : rows * 9) + " slots" + (block.isBlank() ? "" : "  " + Ids.path(block));
        }

        public int slotCount() {
            return useCustomSlots ? Math.max(1, slots.size()) : Math.max(1, Math.min(6, rows)) * 9;
        }
    }

    // ------------------------------------------------------------------ native entities & mobs

    /** Native EntityType (living mob, projectile or rideable vehicle) with attributes, AI goals and biome spawns. */
    public static final class CustomEntity extends Element {
        public String displayName = "";
        /** mob, passive, hostile, projectile, vehicle */
        public String kind = "mob";
        /** creature, monster, ambient, water_creature, misc */
        public String category = "creature";
        public double width = 0.6;
        public double height = 1.8;
        public double health = 20.0;
        public double speed = 0.3;
        public double attackDamage = 4.0;
        public double followRange = 16.0;
        public double knockbackResistance = 0.0;
        public boolean fireImmune = false;
        public boolean pushable = true;
        public boolean canSwim = true;
        public boolean canFly = false;
        public boolean burnsInDaylight = false;
        public boolean tameable = false;
        public String tameItem = "minecraft:bone";
        public boolean spawnEgg = true;
        public String eggPrimary = "#3F76E4";
        public String eggSecondary = "#1E3A8A";
        public String creativeTab = "minecraft:spawn_eggs";
        public String texture = "";
        /** Optional Model element id; blank uses baseAppearance. */
        public String model = "";
        /** biped, quadruped, slime */
        public String baseAppearance = "biped";
        public List<String> aiGoals = new ArrayList<>(List.of("float", "stroll", "look_player", "random_look"));
        public String hurtSound = "";
        public String deathSound = "";
        public String ambientSound = "";
        public String lootTable = "";
        public boolean naturalSpawn = false;
        public String spawnBiomes = "#minecraft:is_overworld";
        public int spawnWeight = 10;
        public int spawnMin = 1;
        public int spawnMax = 3;
        public double projectileDamage = 6.0;
        public boolean rideable = false;
        /** Natural armor points (0..30). */
        public double armor = 0.0;
        /** Visual & attribute scale multiplier (0.2..4.0). */
        public double scale = 1.0;
        /** Step height in blocks (e.g., 0.6 normal, 1.0 horse/boss). */
        public double stepHeight = 0.6;
        /** XP dropped on death (-1 = category default). */
        public int xpReward = -1;
        public boolean glowing;
        public boolean silent;
        public boolean baby;
        public boolean invulnerable;
        public boolean boss;
        public String bossColor = "purple";
        public String bossStyle = "progress";
        /** Optional held mainhand item or #block ID and worn head item ID. */
        public String mainhand = "";
        public String head = "";
        /** Optional animations on the linked Model element. */
        public String idleAnim = "idle";
        public String walkAnim = "walk";
        public String attackAnim = "attack";
        public List<Step> onSpawn = new ArrayList<>();
        public List<Step> onHurt = new ArrayList<>();
        public List<Step> onDeath = new ArrayList<>();
        public List<Step> onInteract = new ArrayList<>();

        public static final List<String> KINDS = List.of("mob", "passive", "hostile", "projectile", "vehicle");
        public static final List<String> CATEGORIES = List.of("creature", "monster", "ambient", "water_creature", "misc");
        public static final List<String> AI_GOALS = List.of("float", "melee", "panic", "stroll", "look_player",
                "random_look", "attack_nearest_player", "swim_random");

        @Override public ElementType type() { return ElementType.ENTITY; }
        @Override public String subtitle() { return displayName.isBlank() ? kind : displayName + " (" + kind + ")"; }

        public boolean isProjectile() { return "projectile".equals(kind); }
        public boolean isLivingMob() { return !isProjectile(); }
    }

    // ------------------------------------------------------------------ configs & mixins

    public static final class ConfigEntry {
        public String key = "enabled";
        public String comment = "";
        /** int, double, bool, string */
        public String kind = "bool";
        public String value = "true";
        public String min = "0";
        public String max = "100";
    }

    /** Cross-loader config specification (TOML on Forge/NeoForge, JSON on Fabric/Quilt). */
    public static final class ModConfig extends Element {
        public String displayName = "";
        /** common, client, server */
        public String scope = "common";
        public String fileName = "";
        public List<ConfigEntry> entries = new ArrayList<>();

        @Override public ElementType type() { return ElementType.MOD_CONFIG; }
        @Override public String subtitle() { return scope + " (" + entries.size() + ")"; }
    }

    /** SpongePowered Mixin definition registered in mixins.json and loader metadata. */
    public static final class MixinSpec extends Element {
        public String displayName = "";
        public boolean enabled = true;
        /** common, client, server */
        public String side = "common";
        public String targetClass = "net.minecraft.world.entity.player.Player";
        public boolean useTargetsString = false;
        public String methodTarget = "tick";
        /** HEAD, TAIL, RETURN */
        public String injectAt = "HEAD";
        public boolean cancellable = false;
        public String code = "";
        /** When true, rawCode is emitted verbatim as the Mixin Java class (supports @Shadow, @Accessor, @Redirect, etc.). */
        public boolean rawMode = false;
        public String rawCode = "";

        @Override public ElementType type() { return ElementType.MIXIN; }
        @Override public String subtitle() {
            String prefix = (enabled ? "" : "[disabled] ") + side + " -> ";
            return prefix + (rawMode ? "Java Mixin" : simpleTarget() + "." + methodTarget);
        }

        public String simpleTarget() {
            String t = targetClass == null ? "" : targetClass.replace('$', '.');
            int i = t.lastIndexOf('.');
            return i >= 0 ? t.substring(i + 1) : t;
        }
    }

    // ------------------------------------------------------------------ portals, worldgen, villagers & registries

    /** Custom Nether-style portal block + frame + igniter item linking dimensions (mods only). */
    public static final class Portal extends Element {
        public String displayName = "";
        public String targetDimension = "minecraft:the_nether";
        public String frameBlock = "minecraft:obsidian";
        public String igniterItem = "";
        public boolean generateIgniter = true;
        public String creativeTab = "minecraft:tools_and_utilities";
        public String texture = "";
        public String igniterTexture = "";
        public String tintColor = "#9933FF";

        @Override public ElementType type() { return ElementType.PORTAL; }
        @Override public String subtitle() { return displayName.isBlank() ? targetDimension : displayName + " -> " + targetDimension; }
    }

    /** Data-driven configured + placed worldgen feature (ore, patch, tree, crystal spike). */
    public static final class WorldFeature extends Element {
        public String displayName = "";
        /** ore, patch, tree, spike */
        public String featureType = "ore";
        public String block = "minecraft:diamond_ore";
        public String replaceTarget = "#minecraft:stone_ore_replaceables";
        public String trunkBlock = "minecraft:oak_log";
        public String leavesBlock = "minecraft:oak_leaves";
        public String biomes = "#minecraft:is_overworld";
        /** underground_ores, vegetal_decoration, surface_structures, underground_decoration */
        public String step = "underground_ores";
        public int count = 8;
        public int size = 6;
        public int minY = -64;
        public int maxY = 64;
        public int trunkHeight = 5;
        public int foliageRadius = 2;
        public boolean rareFilter = false;

        public static final List<String> TYPES = List.of("ore", "patch", "tree", "spike");
        public static final List<String> STEPS = List.of("underground_ores", "vegetal_decoration", "surface_structures", "underground_decoration");

        /** trapezoid or uniform height distribution for ores. */
        public String distribution = "trapezoid";
        /** Chance (0..1) to discard ore blocks exposed to air. */
        public double discardAir = 0.5;
        /** Keep ores buried beneath OCEAN_FLOOR_WG using surface_relative_threshold_filter. */
        public boolean preventSurface = true;
        public int surfaceDepth = 5;
        /** Patch scatter spread (1..16 blocks). */
        public int xzSpread = 7;
        public int ySpread = 3;

        @Override public ElementType type() { return ElementType.WORLD_FEATURE; }
        @Override public String subtitle() { return featureType + "  " + Ids.path(featureType.equals("tree") ? trunkBlock : block); }
    }

    public static final class ProfessionTrade {
        public int level = 1;
        public String costA = "minecraft:emerald";
        public int countA = 1;
        public String costB = "";
        public int countB = 0;
        public String result = "minecraft:diamond";
        public int resultCount = 1;
        public int maxUses = 12;
        public int xp = 5;
    }

    /** Custom VillagerProfession + PointOfInterestType bound to a workstation block (mods only). */
    public static final class Profession extends Element {
        public String displayName = "";
        public String workstationBlock = "minecraft:crafting_table";
        public String workSound = "minecraft:entity.villager.work_toolsmith";
        public List<ProfessionTrade> trades = new ArrayList<>();

        @Override public ElementType type() { return ElementType.PROFESSION; }
        @Override public String subtitle() { return Ids.path(workstationBlock) + " (" + trades.size() + ")"; }
    }

    /** Data-driven armor trim pattern (1.20+) and smithing template item reference. */
    public static final class ArmorTrim extends Element {
        public String displayName = "";
        public String item = "";
        public boolean generateTemplate = true;
        public String creativeTab = "minecraft:ingredients";
        public String texture = "";
        public boolean decal = false;

        @Override public ElementType type() { return ElementType.ARMOR_TRIM; }
        @Override public String subtitle() { return displayName.isBlank() ? item : displayName; }
    }

    /** Data-driven loom banner & shield pattern (1.20.5+). */
    public static final class BannerPattern extends Element {
        public String displayName = "";
        public String texture = "";

        @Override public ElementType type() { return ElementType.BANNER_PATTERN; }
        @Override public String subtitle() { return displayName; }
    }

    /** Custom RecipeType & RecipeSerializer + JSON recipe entries (mods only). */
    public static final class CustomRecipeType extends Element {
        public String displayName = "";
        public int maxInputs = 1;
        public boolean timed = true;

        @Override public ElementType type() { return ElementType.RECIPE_TYPE; }
        @Override public String subtitle() { return displayName.isBlank() ? maxInputs + " inputs" : displayName; }
    }
}

