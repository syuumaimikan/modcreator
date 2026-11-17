package modstudio.core;

import modstudio.core.Elements.Step;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Elements for game systems: mobs and bosses, particle effects, structures, biomes, dimensions, inventory menus,
 * HUD, web requests, resource networks, machines and multiblocks. They compile to data pack functions and
 * worldgen files shared by every Java target; mod loaders and Paper add native code where a data pack cannot reach.
 */
public final class Systems {
    private Systems() {}

    // ------------------------------------------------------------------ mobs and bosses

    public static final class Mob extends Element {
        public String base = "minecraft:zombie";
        public String displayName = "";
        public boolean showName = true;
        public double health = 20, damage = 3, speed = 0.25, armor = 0, knockbackResistance = 0, followRange = 32, scale = 1;
        public String mainhand = "", offhand = "", head = "", chest = "", legs = "", feet = "";
        public boolean dropEquipment;
        public boolean glowing, silent, noAi, invulnerable, fireImmune, baby;
        public boolean persistent = true;
        /** Item shown on an item display riding the mob (custom 3D look); the base mob becomes invisible. */
        public String appearance = "";
        public double appearanceScale = 1;
        /** Animated model (a model with bones) shown instead of the base mob, and the animations it plays. */
        public String rig = "";
        public double rigScale = 1;
        public String idleAnim = "", walkAnim = "", hurtAnim = "", attackAnim = "", deathAnim = "";
        public String lootTable = "";
        public boolean naturalSpawn;
        public String spawnBiome = "";
        public double spawnChance = 10;
        public int spawnInterval = 30, spawnMax = 3;
        public boolean spawnAtNight;
        public List<Step> onSpawn = new ArrayList<>();
        public List<Step> onTick = new ArrayList<>();
        public int tickInterval = 20;
        public List<Step> onHurt = new ArrayList<>();
        public List<Step> onDeath = new ArrayList<>();
        public List<Step> onInteract = new ArrayList<>();
        public boolean boss;
        public String bossColor = "purple";
        public String bossStyle = "progress";
        public int bossRadius = 48;
        public List<BossPhase> phases = new ArrayList<>();

        @Override public ElementType type() { return ElementType.MOB; }
        @Override public String subtitle() { return base + (boss ? "  boss" : ""); }
    }

    /** Runs once when the boss health falls to the given percentage. */
    public static final class BossPhase {
        public int health = 50;
        public String message = "";
        public List<Step> steps = new ArrayList<>();
    }

    // ------------------------------------------------------------------ particle effects

    public static final class Effect extends Element {
        /** point, circle, sphere, helix, line, spiral, square, cone, star, wave */
        public String shape = "circle";
        public String particle = "minecraft:flame";
        public String color = "#E0115F";
        public double size = 1;
        public double radius = 1.5, height = 2, length = 8, turns = 2;
        public int points = 24;
        /** horizontal, vertical, facing */
        public String plane = "horizontal";
        public int count = 1;
        public double spread = 0, speed = 0, yOffset = 0;
        public boolean force;

        @Override public ElementType type() { return ElementType.EFFECT; }
        @Override public String subtitle() { return shape + "  " + particle; }
    }

    // ------------------------------------------------------------------ voxel volumes

    /** A box of block indices into a palette; index -1 means "keep / any". Stored y-major: (y * sz + z) * sx + x. */
    public abstract static class Volume extends Element {
        public int sx = 5, sy = 4, sz = 5;
        public List<String> palette = new ArrayList<>(List.of("minecraft:stone_bricks", "minecraft:oak_planks", "minecraft:glass", "minecraft:air"));
        public int[] cells = filled(5 * 4 * 5);

        public static int[] filled(int n) {
            int[] r = new int[n];
            Arrays.fill(r, -1);
            return r;
        }

        public int get(int x, int y, int z) {
            int i = (y * sz + z) * sx + x;
            return cells == null || i < 0 || i >= cells.length ? -1 : cells[i];
        }

        public void set(int x, int y, int z, int v) {
            ensure();
            cells[(y * sz + z) * sx + x] = v;
        }

        public void ensure() {
            if (cells == null || cells.length != sx * sy * sz) cells = filled(sx * sy * sz);
        }

        /** Changes the size, keeping the blocks that still fit. */
        public void resize(int nx, int ny, int nz) {
            int[] n = filled(nx * ny * nz);
            for (int y = 0; y < Math.min(sy, ny); y++)
                for (int z = 0; z < Math.min(sz, nz); z++)
                    for (int x = 0; x < Math.min(sx, nx); x++) n[(y * nz + z) * nx + x] = get(x, y, z);
            sx = nx;
            sy = ny;
            sz = nz;
            cells = n;
        }

        public int count() {
            int n = 0;
            if (cells != null) for (int c : cells) if (c >= 0) n++;
            return n;
        }
    }

    public static final class Structure extends Volume {
        public boolean generate = true;
        public String biomes = "#minecraft:is_overworld";
        public int spacing = 32, separation = 8;
        public String step = "surface_structures";
        /** none, beard_thin, beard_box, bury, encapsulate */
        public String terrain = "beard_thin";
        public int yOffset = 0;
        public String chestLoot = "";

        @Override public ElementType type() { return ElementType.STRUCTURE; }
        @Override public String subtitle() { return sx + "x" + sy + "x" + sz + (generate ? "  worldgen" : ""); }
    }

    /** One processing recipe of an assembled multiblock machine. */
    public static final class MultiblockRecipe {
        public String input = "minecraft:iron_ore";
        public int inputCount = 1;
        public String output = "minecraft:raw_iron";
        public int outputCount = 2;
        /** Extra output with a chance in percent (a byproduct), or empty. */
        public String secondary = "";
        public int secondaryCount = 1;
        public int secondaryChance = 0;
        /** Ticks one operation takes. */
        public int time = 100;
        /** Amount of the machine's resource one operation uses. */
        public int cost = 0;
    }

    public static final class Multiblock extends Volume {
        /** Position of the core block (the block the player uses or the machine block). */
        public int coreX = 1, coreY = 0, coreZ = 1;
        public boolean rotations = true;

        /** Assembly like Immersive Engineering: right-click the core with the tool to turn the blocks into one machine. */
        public boolean assemble = false;
        public String tool = "";
        public String incompleteMessage = "The structure is not complete.";
        /** Model shown in place of the blocks once assembled (the blocks turn into barriers); empty keeps the blocks. */
        public String model = "";
        public double modelScale = 1;
        public String idleAnimation = "";
        public String workAnimation = "";
        /** Input port: items thrown onto this position are processed (pattern coordinates, may lie outside). */
        public int inX = 1, inY = 1, inZ = 1;
        /** Output port: results go into a container here or drop out here (pattern coordinates, may lie outside). */
        public int outX = 1, outY = 0, outZ = -1;
        /** Buffer resource the machine runs on (filled by resource networks), or empty. */
        public String resource = "";
        public int capacity = 1000;
        public List<MultiblockRecipe> recipes = new ArrayList<>();
        public String workParticle = "minecraft:smoke";
        public String workSound = "";
        public String assembleSound = "minecraft:block.anvil.use";
        /** Runs as the player who assembled the machine, at the core. */
        public List<Step> onAssemble = new ArrayList<>();
        /** Runs as the player who right-clicks the assembled machine; empty shows its status. */
        public List<Step> onUse = new ArrayList<>();
        /** Runs at the machine after each finished operation. */
        public List<Step> onFinish = new ArrayList<>();
        /** Runs at the machine when it is taken apart or broken. */
        public List<Step> onDisassemble = new ArrayList<>();

        public Multiblock() {
            sx = 3;
            sy = 2;
            sz = 3;
            palette = new ArrayList<>(List.of("minecraft:gold_block", "minecraft:amethyst_block", "minecraft:lodestone"));
            cells = filled(18);
        }

        @Override public ElementType type() { return ElementType.MULTIBLOCK; }
        @Override public String subtitle() { return sx + "x" + sy + "x" + sz + (assemble ? " / " + recipes.size() : ""); }
    }

    // ------------------------------------------------------------------ worldgen

    public static final class MobSpawn {
        public String entity = "minecraft:sheep";
        public String category = "creature";
        public int weight = 10, min = 1, max = 4;
    }

    public static final class Biome extends Element {
        public double temperature = 0.8, downfall = 0.4;
        public boolean precipitation = true;
        public String sky = "#78A7FF", fog = "#C0D8FF", water = "#3F76E4", waterFog = "#050533", grass = "", foliage = "";
        public String ambientParticle = "";
        public double particleChance = 0.01;
        public String music = "";
        public String ambientSound = "";
        /** Vanilla feature preset: plains, forest, desert, snowy, jungle, swamp, badlands, mushroom, barren */
        public String features = "plains";
        public List<MobSpawn> spawns = new ArrayList<>();

        @Override public ElementType type() { return ElementType.BIOME; }
        @Override public String subtitle() { return features; }
    }

    public static final class Dimension extends Element {
        /** noise, flat, void */
        public String generator = "noise";
        public String noise = "minecraft:overworld";
        /** single, checkerboard, multi_noise */
        public String biomeSource = "single";
        public List<String> biomes = new ArrayList<>(List.of("minecraft:plains"));
        public String flatLayers = "minecraft:bedrock*1\nminecraft:dirt*2\nminecraft:grass_block*1";
        public String effects = "minecraft:overworld";
        public double ambientLight = 0, coordinateScale = 1;
        public boolean hasSkylight = true, hasCeiling, ultrawarm, natural = true, bedWorks = true, respawnAnchorWorks, piglinSafe, hasRaids = true;
        public int fixedTime = -1;
        public int minY = -64, height = 384;

        @Override public ElementType type() { return ElementType.DIMENSION; }
        @Override public String subtitle() { return generator + "  " + biomeSource; }
    }

    // ------------------------------------------------------------------ interface

    public static final class MenuSlot {
        public int slot;
        public String item = "minecraft:stone";
        public int count = 1;
        public String name = "";
        public String lore = "";
        public boolean glint;
        /** none, close, menu, run, command */
        public String action = "run";
        public String target = "";
        public List<Step> steps = new ArrayList<>();
        /** Required tag (data pack) or permission node (Paper); blank for everyone. */
        public String permission = "";
        public String costObjective = "";
        public int cost;
        public boolean closeAfter = true;
        /** Other items shown in turn with the slot item (an animated slot); empty for a still slot. */
        public List<String> frames = new ArrayList<>();
        public int frameTicks = 10;
    }

    public static final class Menu extends Element {
        public String title = "";
        /** chest, hopper, dispenser */
        public String layout = "chest";
        public int rows = 3;
        public String filler = "minecraft:gray_stained_glass_pane";
        public List<MenuSlot> slots = new ArrayList<>();
        /** Shown when a slot's cost or permission check fails. */
        public String denyMessage = "&cYou cannot use this.";
        /** none, panel (drawn from the colours) or texture; chest menus only. */
        public String background = "none";
        public String backgroundTexture = "";
        public String panelColor = "#c6c6c6";
        public String frameColor = "#373737";
        /** Title colour as #rrggbb; blank keeps the default dark grey. */
        public String titleColor = "";
        /** How the items appear when the menu opens: none, slots, rows, columns, center */
        public String openAnimation = "none";
        /** Ticks between two steps of the opening animation. */
        public int animationSpeed = 2;
        public String openSound = "";
        public String clickSound = "";

        public boolean animated() {
            return !openAnimation.equals("none") || slots.stream().anyMatch(s -> !s.frames.isEmpty());
        }

        public int size() {
            return switch (layout) {
                case "hopper" -> 5;
                case "dispenser" -> 9;
                default -> Math.max(1, Math.min(6, rows)) * 9;
            };
        }

        @Override public ElementType type() { return ElementType.MENU; }
        @Override public String subtitle() { return layout + "  " + slots.size(); }
    }

    public static final class Hud extends Element {
        /** sidebar, bossbar, actionbar */
        public String kind = "sidebar";
        public String title = "";
        /** One line each; {score:objective} is replaced with the viewer's score, {timer} with the remaining seconds. */
        public List<String> lines = new ArrayList<>();
        public String color = "yellow";
        public String style = "progress";
        public boolean timer;
        public int seconds = 60;
        public List<Step> onFinish = new ArrayList<>();
        public String players = "@a";
        public boolean autoStart;
        public int refresh = 10;

        @Override public ElementType type() { return ElementType.HUD; }
        @Override public String subtitle() { return kind + (timer ? "  " + seconds + "s" : ""); }
    }

    // ------------------------------------------------------------------ web requests

    public static final class Extract {
        /** Dot path into the JSON response, for example player.stats.level or items[0].name */
        public String path = "";
        /** Key in the data storage {ns}:api (and score name when toScore). */
        public String key = "";
        public boolean toScore;
    }

    public static final class ApiRequest extends Element {
        public String method = "GET";
        /** May contain {secret:NAME} and {player} placeholders. */
        public String url = "https://";
        public String headers = "";
        public String body = "";
        public int timeout = 10, retries = 1;
        public List<Extract> extract = new ArrayList<>();
        public List<Step> onSuccess = new ArrayList<>();
        public List<Step> onClientError = new ArrayList<>();
        public List<Step> onServerError = new ArrayList<>();
        public List<Step> onFailure = new ArrayList<>();
        /** manual, interval, cron */
        public String schedule = "manual";
        public int intervalSeconds = 300;
        public String cron = "*/5 * * * *";

        @Override public ElementType type() { return ElementType.API_REQUEST; }
        @Override public String subtitle() { return method + "  " + url; }
    }

    // ------------------------------------------------------------------ shaders

    /** A GLSL shader in the resource pack: replaces a vanilla core shader program or provides an include file. */
    public static final class Shader extends Element {
        /** core: vertex and fragment program of a vanilla core shader. include: a .glsl file for #moj_import. */
        public String kind = "core";
        public String program = "position_tex";
        public String vertex = "";
        public String fragment = "";
        /** Exported only for these Minecraft versions; empty means no limit (shader sources change between versions). */
        public String minVersion = "";
        public String maxVersion = "";

        @Override public ElementType type() { return ElementType.SHADER; }
        @Override public String subtitle() { return kind + "  " + program; }
    }

    // ------------------------------------------------------------------ cutscenes

    /** A camera path the viewer watches from spectator mode, with titles, sounds and commands at set times. */
    public static final class Cutscene extends Element {
        /** relative: positions are offsets from where the viewer stands when it starts. absolute: world coordinates. */
        public String space = "relative";
        public List<CameraKey> keys = new ArrayList<>();
        public List<CameraEvent> events = new ArrayList<>();
        /** Sneaking skips to the end. */
        public boolean skippable = true;
        /** Puts the viewer back where the cutscene started. */
        public boolean returnToStart = true;
        public List<Step> onEnd = new ArrayList<>();

        public double length() {
            double m = 0;
            for (CameraKey k : keys) m = Math.max(m, k.time);
            for (CameraEvent e : events) m = Math.max(m, e.time);
            return m;
        }

        @Override public ElementType type() { return ElementType.CUTSCENE; }
        @Override public String subtitle() { return keys.size() + " keys  " + Math.round(length() * 10) / 10.0 + "s"; }
    }

    public static final class CameraKey {
        /** Seconds from the start. */
        public double time;
        public double x, y = 2, z;
        public double yaw, pitch;
        /** Look at the target point instead of using yaw and pitch. */
        public boolean lookAt;
        public double tx, ty, tz;
        /** How the camera moves towards this key: smooth (curved path), linear, ease_in, ease_out, ease_in_out, cut. */
        public String easing = "smooth";
    }

    public static final class CameraEvent {
        public double time;
        /** title, subtitle, actionbar, sound, fade, command */
        public String kind = "title";
        public String text = "";
        public String color = "white";
        public double duration = 2;
    }

    // ------------------------------------------------------------------ machines

    public static final class Resource extends Element {
        public String displayName = "";
        public String unit = "FE";
        public String color = "#F5C542";
        /** buffer: stored amounts flow between machines (energy, mana, aspects). stress: capacity versus demand (kinetic). */
        public String model = "buffer";
        public int capacity = 10000;
        public boolean pollution;
        public int pollutionLimit = 100;
        public List<Step> onPollution = new ArrayList<>();

        @Override public ElementType type() { return ElementType.RESOURCE; }
        @Override public String subtitle() { return model + "  " + unit; }
    }

    public static final class MachineIO {
        public String resource = "";
        /** produce, consume, store */
        public String mode = "produce";
        public int amount = 10;
        public int capacity = 1000;
    }

    public static final class Machine extends Element {
        public String block = "";
        public int interval = 20;
        public List<MachineIO> io = new ArrayList<>();
        /** none, adjacent, radius, beam */
        public String transfer = "radius";
        public double range = 8;
        public int transferRate = 50;
        public String beamParticle = "minecraft:end_rod";
        public String multiblock = "";
        /** none, sky, night, day, moon_full */
        public String celestial = "none";
        public int rpm = 16;
        /** stop, break, explode */
        public String overload = "stop";
        public List<Step> onCycle = new ArrayList<>();
        public List<Step> onPlace = new ArrayList<>();
        public List<Step> onBreak = new ArrayList<>();
        public List<Step> onOverload = new ArrayList<>();
        public String particle = "";

        @Override public ElementType type() { return ElementType.MACHINE; }
        @Override public String subtitle() { return block.isBlank() ? "" : block; }
    }
}
