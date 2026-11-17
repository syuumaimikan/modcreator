package modstudio.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import modstudio.core.*;
import modstudio.core.Elements.Bone;
import modstudio.core.Elements.Model;
import modstudio.core.Elements.Procedure;
import modstudio.core.Elements.Step;
import modstudio.core.Systems.Multiblock;
import modstudio.core.Systems.MultiblockRecipe;

import java.util.*;

/**
 * Multiblock machines that are assembled like Immersive Engineering machines: the player builds the structure and
 * right-clicks its core block with a tool. A marker entity at the core (turned to the structure's rotation, so local
 * coordinates follow the pattern) then runs the machine: items thrown onto the input port are processed by recipes
 * into the output port, using a resource that resource networks deliver to the marker. With a model the blocks turn
 * into barriers and the model is shown by item displays, one per bone, which play idle and work animations.
 * Left-clicking the core with the tool takes the machine apart and puts the blocks back.
 *
 * <p>Everything is data pack functions, so it runs on every Java target. The tool's right-click comes from an
 * item_right_click procedure added while generating (see {@link #toolProcedures}).
 */
final class AssemblyGen {
    private final GenContext ctx;
    private final ProcCompiler pc;
    private final String ns, t, root;
    private final boolean realItems;
    private final List<String> tick;
    private final String mbq, mbt, mbc, anim, at;

    AssemblyGen(GenContext ctx, ProcCompiler pc, String root, boolean realItems, List<String> tick) {
        this.ctx = ctx;
        this.pc = pc;
        this.ns = ctx.ns;
        this.t = pc.tmp();
        this.root = root;
        this.realItems = realItems;
        this.tick = tick;
        mbq = ns + ".mbq";
        mbt = ns + ".mbt";
        mbc = ns + ".mbc";
        anim = ns + ".anim";
        at = ns + ".at";
    }

    // ------------------------------------------------------------------ shared helpers

    /** The block state of the core cell, or null when the core is empty or air. */
    static String coreState(Multiblock mb) {
        mb.ensure();
        int idx = mb.get(mb.coreX, mb.coreY, mb.coreZ);
        if (idx < 0 || idx >= mb.palette.size()) return null;
        String s = mb.palette.get(idx);
        return s == null || s.isBlank() || MachineGen.isAir(s) ? null : s;
    }

    /** True when the multiblock is assembled with a tool and can be: a tool and a solid core are set. */
    static boolean active(Multiblock mb) {
        return mb.assemble && mb.tool != null && !mb.tool.isBlank() && coreState(mb) != null;
    }

    /** The model shown by an assembled multiblock, or null. */
    static Model model(Project p, Multiblock mb) {
        if (!active(mb) || mb.model == null || mb.model.isBlank()) return null;
        Model md = p.model(mb.model.trim());
        return md == null || md.cubes.isEmpty() ? null : md;
    }

    /** Assembled multiblocks grouped by their tool (full item id), in project order. */
    static Map<String, List<Multiblock>> byTool(Project p) {
        Map<String, List<Multiblock>> r = new LinkedHashMap<>();
        for (Multiblock mb : p.all(Multiblock.class)) {
            if (active(mb)) r.computeIfAbsent(Ids.full(mb.tool.trim()), k -> new ArrayList<>()).add(mb);
        }
        return r;
    }

    /** One right-click procedure per assembly tool; each runs multiblock/_tool_&lt;n&gt;. */
    static List<Procedure> toolProcedures(Project p, String ns) {
        List<Procedure> r = new ArrayList<>();
        int i = 0;
        for (String tool : byTool(p).keySet()) {
            Procedure pr = new Procedure();
            pr.name = "_assembly_tool_" + i;
            pr.trigger = "item_right_click";
            pr.triggerValue = tool;
            Step s = Logic.newStep("command");
            s.p.put("command", "function " + ns + ":multiblock/_tool_" + i);
            pr.steps.add(s);
            r.add(pr);
            i++;
        }
        return r;
    }

    private static String loc(int dx, int dy, int dz) {
        return "^" + (dx == 0 ? "" : dx) + " ^" + (dy == 0 ? "" : dy) + " ^" + (dz == 0 ? "" : dz);
    }

    /** Pattern cells that hold a block (not empty, not air), as {dx, dy, dz, palette index} from the core. */
    private static List<int[]> cells(Multiblock mb) {
        List<int[]> r = new ArrayList<>();
        mb.ensure();
        for (int y = 0; y < mb.sy; y++)
            for (int z = 0; z < mb.sz; z++)
                for (int x = 0; x < mb.sx; x++) {
                    int idx = mb.get(x, y, z);
                    if (idx < 0 || idx >= mb.palette.size()) continue;
                    String s = mb.palette.get(idx);
                    if (s == null || s.isBlank() || MachineGen.isAir(s)) continue;
                    r.add(new int[]{x - mb.coreX, y - mb.coreY, z - mb.coreZ, idx});
                }
        return r;
    }

    private Elements.Item projectItem(String full) {
        return !realItems && ctx.p.itemLike(full) instanceof Elements.Item it ? it : null;
    }

    private boolean projectBlock(String full) {
        return !realItems && full.startsWith(ns + ":") && ctx.p.itemLike(full) != null && projectItem(full) == null;
    }

    /** Condition (run as an item entity) that its stack is the given item, or null when this target cannot tell. */
    private String itemCondition(String id) {
        String f = Ids.full(id.trim());
        if (projectBlock(f)) return null;
        Elements.Item it = projectItem(f);
        if (ctx.components()) return "if items entity @s contents " + (it != null ? ItemSpec.match(ctx, it) : f);
        if (Ids.isTag(f)) return null;
        String nbt = it != null ? "{Item:{id:\"" + ItemSpec.base(it) + "\",tag:" + ItemSpec.nbtTag(ctx, it) + "}}" : "{Item:{id:\"" + f + "\"}}";
        return "if entity @s[nbt=" + nbt + "]";
    }

    /** Loot entry for an item, turning project items into their custom item on data pack targets. */
    private JsonObject lootItem(String id, int count) {
        String f = Ids.full(id.trim());
        JsonObject e = J.obj("type", "minecraft:item", "name", f);
        JsonArray fns = new JsonArray();
        Elements.Item it = projectItem(f);
        if (it != null) {
            e.addProperty("name", ItemSpec.base(it));
            if (ctx.components()) fns.add(J.obj("function", "minecraft:set_components", "components", ItemSpec.components(ctx, it)));
            else fns.add(J.obj("function", "minecraft:set_nbt", "tag", ItemSpec.give(ctx, it).substring(ItemSpec.base(it).length())));
        }
        if (count != 1) fns.add(J.obj("function", "minecraft:set_count", "count", Math.max(1, Math.min(99, count))));
        if (!fns.isEmpty()) e.add("functions", fns);
        return e;
    }

    private String countPath() {
        return ctx.components() ? "Item.count" : "Item.Count";
    }

    private String sound(String id) {
        return "playsound " + Ids.full(id.trim()) + " block @a ~ ~ ~ 1 1";
    }

    // ------------------------------------------------------------------ generation

    void generate() {
        List<Multiblock> list = new ArrayList<>();
        for (Multiblock mb : ctx.all(Multiblock.class)) {
            if (!active(mb)) continue;
            String core = MachineGen.blockId(coreState(mb));
            if (!realItems && core.startsWith(ns + ":")) {
                ctx.note("multiblock " + mb.name + ": the core block " + core + " is not available on this target; use a vanilla block");
                continue;
            }
            list.add(mb);
        }
        if (list.isEmpty()) return;
        for (String o : List.of(t, mbq, mbt, mbc, anim, at)) pc.objectives.add(o);

        // the tools: a ray from the eyes to the core block that was clicked
        int i = 0;
        for (var e : byTool(ctx.p).entrySet()) {
            List<Multiblock> mine = e.getValue().stream().filter(list::contains).toList();
            String dir = "multiblock/_tool_" + i;
            pc.functions.put(dir, List.of(
                    "scoreboard players set #ray " + t + " 0",
                    "execute anchored eyes positioned ^ ^ ^ run function " + ns + ":" + dir + "_ray"));
            List<String> ray = new ArrayList<>();
            for (Multiblock mb : mine) {
                ray.add("execute if score #ray " + t + " matches 0.. if block ~ ~ ~ " + MachineGen.fullState(coreState(mb))
                        + " align xyz positioned ~.5 ~.5 ~.5 run function " + ns + ":multiblock/" + mb.name + "/try");
            }
            ray.add("execute if score #ray " + t + " matches 0.. unless block ~ ~ ~ minecraft:air unless block ~ ~ ~ minecraft:cave_air run scoreboard players set #ray "
                    + t + " -1000");
            ray.add("scoreboard players add #ray " + t + " 1");
            ray.add("execute if score #ray " + t + " matches 1..60 positioned ^ ^ ^0.1 run function " + ns + ":" + dir + "_ray");
            pc.functions.put(dir + "_ray", ray);
            i++;
        }

        List<String> dispatch = new ArrayList<>();
        for (Multiblock mb : list) {
            machine(mb);
            dispatch.add("execute if entity @s[tag=" + ns + ".mb." + mb.name + "] run function " + ns + ":multiblock/" + mb.name + "/tick");
        }
        pc.functions.put("multiblock/_tick", dispatch);
        tick.add("execute as @e[type=minecraft:marker,tag=" + ns + ".mbm] at @s run function " + ns + ":multiblock/_tick");
        // displays and click boxes whose machine is gone (killed by a command) are removed every two seconds
        tick.add("scoreboard players add #mbclock " + t + " 1");
        tick.add("execute if score #mbclock " + t + " matches 40.. run function " + ns + ":multiblock/_clean");
        pc.functions.put("multiblock/_clean", List.of(
                "scoreboard players set #mbclock " + t + " 0",
                "execute as @e[type=minecraft:item_display,tag=" + ns + ".mbd] at @s positioned ~ ~0.5 ~ unless entity @e[type=minecraft:marker,tag="
                        + ns + ".mbm,distance=..0.05] run kill @s",
                "execute as @e[type=minecraft:interaction,tag=" + ns + ".mbi] at @s positioned ~ ~0.51 ~ unless entity @e[type=minecraft:marker,tag="
                        + ns + ".mbm,distance=..0.05] run kill @s"));
    }

    private void machine(Multiblock mb) {
        String dir = "multiblock/" + mb.name, fn = ns + ":" + dir + "/";
        String check = MachineGen.ensureMultiblock(ctx, pc, mb);
        Model md = model(ctx.p, mb);
        List<int[]> cells = cells(mb);
        String res = mb.resource == null ? "" : mb.resource.trim();
        if (!res.isEmpty() && !(ctx.p.byName(ElementType.RESOURCE, res) instanceof Systems.Resource r && !r.model.equals("stress"))) res = "";
        String amt = res.isEmpty() ? null : MachineGen.amountObj(ns, res);

        // try: run at the clicked core block as the player
        pc.functions.put(dir + "/try", List.of(
                "scoreboard players set #ray " + t + " -1000",
                "execute unless entity @e[type=minecraft:marker,tag=" + ns + ".mbm,distance=..0.5] run function " + fn + "try_new"));
        List<String> tryNew = new ArrayList<>();
        tryNew.add("function " + check);
        tryNew.add("execute if score #mb " + t + " matches 1 run function " + fn + "assemble");
        if (!mb.incompleteMessage.isBlank()) {
            tryNew.add("execute if score #mb " + t + " matches 0 run title @s actionbar " + J.compact(J.obj("text", mb.incompleteMessage, "color", "red")));
        }
        pc.functions.put(dir + "/try_new", tryNew);

        // assemble: a marker turned like the pattern, then the player's steps
        List<String> asm = new ArrayList<>();
        int rots = mb.rotations ? 4 : 1;
        for (int r = 0; r < rots; r++) {
            asm.add("execute if score #mbr " + t + " matches " + r + " run summon minecraft:marker ~ ~ ~ {Tags:[\"" + ns + ".mbm\",\"" + ns + ".mb." + mb.name
                    + "\",\"" + ns + ".mbnew\"],Rotation:[" + (90 * r) + "f,0f]}");
        }
        asm.add("execute as @e[type=minecraft:marker,tag=" + ns + ".mbnew] at @s run function " + fn + "setup");
        if (!mb.assembleSound.isBlank()) asm.add(sound(mb.assembleSound));
        asm.add("particle minecraft:cloud ~ ~ ~ 0.8 0.8 0.8 0.02 24");
        pc.compile(dir + "/assemble", mb.onAssemble, asm, List.of());

        // setup: as the new marker
        List<String> setup = new ArrayList<>();
        setup.add("tag @s remove " + ns + ".mbnew");
        for (String o : List.of(mbq, mbt, mbc, anim, at)) setup.add("scoreboard players set @s " + o + " 0");
        if (amt != null) {
            pc.objectives.add(amt);
            pc.objectives.add(MachineGen.capObj(ns, res));
            for (String tg : List.of(MachineGen.hasTag(ns, res), ns + ".in." + res, ns + ".use." + res)) setup.add("tag @s add " + tg);
            setup.add("scoreboard players set @s " + MachineGen.capObj(ns, res) + " " + Math.max(1, mb.capacity));
        }
        if (md != null) {
            for (int[] c : cells) setup.add("setblock " + loc(c[0], c[1], c[2]) + " minecraft:barrier");
            RigGen rig = new RigGen(ctx, pc);
            Map<String, double[]> rest = Rig.pose(md, null, 0);
            double scale = Math.max(0.05, mb.modelScale);
            for (Bone b : RigGen.shown(md)) {
                setup.add("execute positioned ~ ~-0.5 ~ run summon minecraft:item_display ~ ~ ~ {Tags:[\"" + ns + ".mbd\",\"" + RigGen.boneTag(ns, b)
                        + "\",\"" + ns + ".newd\"],item:" + rig.itemSnbt(md, b) + ",interpolation_duration:" + RigGen.STEP + ",transformation:"
                        + RigGen.matrix(Rig.display(b, rest.get(b.name), scale, 0)) + "}");
            }
            // the displays face the other way round: the bone matrices turn the model by half a turn
            for (int r = 0; r < rots; r++) {
                setup.add("execute if score #mbr " + t + " matches " + r + " as @e[type=minecraft:item_display,tag=" + ns
                        + ".newd] run data modify entity @s Rotation[0] set value " + ((90 * r + 180) % 360) + "f");
            }
            setup.add("tag @e[type=minecraft:item_display,tag=" + ns + ".newd] remove " + ns + ".newd");
            rig.animations(md, dir + "/rig", tag -> "execute positioned ~ ~-0.5 ~ as @e[type=minecraft:item_display,tag=" + tag + ",distance=..0.01] run ",
                    (b, w) -> Rig.display(b, w, scale, 0));
        }
        setup.add("execute positioned ~ ~-0.51 ~ run summon minecraft:interaction ~ ~ ~ {Tags:[\"" + ns + ".mbi\"],width:1.02f,height:1.02f,response:1b}");
        pc.functions.put(dir + "/setup", setup);

        // tick: clicks, then the machine itself
        pc.functions.put(dir + "/tick", List.of(
                "scoreboard players set #dis " + t + " 0",
                "execute positioned ~ ~-0.51 ~ as @e[type=minecraft:interaction,tag=" + ns + ".mbi,distance=..0.05,limit=1] run function " + fn + "clicks",
                "execute if score #dis " + t + " matches 1 run function " + fn + "disassemble",
                "execute if score #dis " + t + " matches 0 run function " + fn + "live"));
        Elements.Condition holding = Logic.newCondition("holding");
        holding.p.put("item", mb.tool.trim());
        String holds = pc.condition(holding);
        pc.functions.put(dir + "/clicks", List.of(
                "execute if data entity @s attack on attacker " + (holds == null ? "" : holds + " ") + "run scoreboard players set #dis " + t + " 1",
                "execute if data entity @s interaction on target run function " + fn + "use",
                "data remove entity @s attack",
                "data remove entity @s interaction"));
        pc.compile(dir + "/use", mb.onUse.isEmpty() ? List.of() : mb.onUse, mb.onUse.isEmpty() ? List.of(status(mb, res)) : List.of(), List.of());

        List<String> live = new ArrayList<>();
        live.add("scoreboard players add @s " + mbc + " 1");
        live.add("execute if score @s " + mbc + " matches 20.. run function " + fn + "verify");
        live.add("execute if score #dis " + t + " matches 1 run function " + fn + "disassemble");
        if (!mb.recipes.isEmpty()) {
            live.add("execute if score #dis " + t + " matches 0 if score @s " + mbq + " matches 0 if score @s " + mbc + " matches 5 run function " + fn + "scan");
            live.add("execute if score #dis " + t + " matches 0 if score @s " + mbq + " matches 0 if score @s " + mbc + " matches 15 run function " + fn + "scan");
            live.add("execute if score #dis " + t + " matches 0 if score @s " + mbq + " matches 1.. run function " + fn + "work");
        }
        if (md != null && !md.animations.isEmpty()) {
            int idle = RigGen.id(md, mb.idleAnimation), work = RigGen.id(md, mb.workAnimation);
            live.add("scoreboard players set #want " + t + " " + idle);
            if (work > 0) live.add("execute if score @s " + mbq + " matches 1.. run scoreboard players set #want " + t + " " + work);
            live.add("execute if score #dis " + t + " matches 0 unless score @s " + anim + " = #want " + t + " if score #want " + t + " matches 0 run function " + fn + "rig/rest");
            live.add("execute unless score @s " + anim + " = #want " + t + " run scoreboard players set @s " + at + " 0");
            live.add("scoreboard players operation @s " + anim + " = #want " + t);
            live.add("execute if score #dis " + t + " matches 0 run function " + fn + "rig/anim");
        }
        pc.functions.put(dir + "/live", live);

        // verify every second: are all the blocks (or barriers) still there?
        List<String> verify = new ArrayList<>();
        verify.add("scoreboard players set @s " + mbc + " 0");
        for (int[] c : cells) {
            String state = md != null ? "minecraft:barrier" : MachineGen.fullState(mb.palette.get(c[3]));
            verify.add("execute unless block " + loc(c[0], c[1], c[2]) + " " + state + " run scoreboard players set #dis " + t + " 1");
        }
        pc.functions.put(dir + "/verify", verify);

        // disassemble: blocks back, displays and click box away
        List<String> dis = new ArrayList<>();
        if (md != null) {
            for (int[] c : cells) {
                dis.add("execute if block " + loc(c[0], c[1], c[2]) + " minecraft:barrier run setblock " + loc(c[0], c[1], c[2]) + " "
                        + MachineGen.fullState(mb.palette.get(c[3])));
            }
        }
        dis.add("execute positioned ~ ~-0.5 ~ run kill @e[type=minecraft:item_display,tag=" + ns + ".mbd,distance=..0.01]");
        dis.add("execute positioned ~ ~-0.51 ~ run kill @e[type=minecraft:interaction,tag=" + ns + ".mbi,distance=..0.05]");
        dis.add("particle minecraft:cloud ~ ~ ~ 0.8 0.8 0.8 0.02 16");
        pc.compile(dir + "/disassemble", mb.onDisassemble, dis, List.of("kill @s"));

        if (!mb.recipes.isEmpty()) recipes(mb, dir, fn, amt);
    }

    /** Status line shown when the machine is right-clicked and has no use steps. */
    private String status(Multiblock mb, String res) {
        String me = "@e[type=minecraft:marker,tag=" + ns + ".mbm,sort=nearest,limit=1]";
        JsonArray arr = new JsonArray();
        arr.add("");
        arr.add(J.obj("text", Ids.titleCase(mb.name) + "  ", "color", "gold"));
        arr.add(J.obj("text", "progress ", "color", "gray"));
        arr.add(J.obj("score", J.obj("name", me, "objective", mbt), "color", "white"));
        arr.add(J.obj("text", " ticks", "color", "gray"));
        if (!res.isEmpty() && ctx.p.byName(ElementType.RESOURCE, res) instanceof Systems.Resource r) {
            String label = r.displayName.isBlank() ? Ids.titleCase(r.name) : r.displayName;
            arr.add(J.obj("text", "  " + label + " ", "color", "gray"));
            arr.add(J.obj("score", J.obj("name", me, "objective", MachineGen.amountObj(ns, res)), "color", "white"));
            arr.add(J.obj("text", " / ", "color", "gray"));
            arr.add(J.obj("score", J.obj("name", me, "objective", MachineGen.capObj(ns, res)), "color", "white"));
            arr.add(J.obj("text", " " + r.unit, "color", "gray"));
        }
        return "tellraw @s " + J.compact(arr);
    }

    private void recipes(Multiblock mb, String dir, String fn, String amt) {
        int ix = mb.inX - mb.coreX, iy = mb.inY - mb.coreY, iz = mb.inZ - mb.coreZ;
        int ox = mb.outX - mb.coreX, oy = mb.outY - mb.coreY, oz = mb.outZ - mb.coreZ;
        String cur = "@e[type=minecraft:marker,tag=" + ns + ".mbcur,limit=1]";

        // scan: look for input items on the input port
        pc.functions.put(dir + "/scan", List.of(
                "tag @s add " + ns + ".mbcur",
                "scoreboard players set #got " + t + " 0",
                "execute positioned " + loc(ix, iy, iz) + " as @e[type=minecraft:item,distance=..1.1] if score #got " + t + " matches 0 run function " + fn + "take",
                "tag @s remove " + ns + ".mbcur"));
        List<String> take = new ArrayList<>();
        take.add("execute store result score #c " + t + " run data get entity @s " + countPath());
        List<String> work = new ArrayList<>();
        work.add("scoreboard players add @s " + mbt + " 1");
        if (!mb.workParticle.isBlank()) {
            work.add("particle " + SystemsGen.particle(ctx, mb.workParticle, "#FFFFFF", 1) + " ~ ~0.6 ~ 0.4 0.4 0.4 0.01 1");
        }
        if (!mb.workSound.isBlank()) work.add("execute if score @s " + mbt + " matches 1 run " + sound(mb.workSound));
        for (int i = 0; i < mb.recipes.size(); i++) {
            MultiblockRecipe rc = mb.recipes.get(i);
            int id = i + 1, n = Math.max(1, Math.min(99, rc.inputCount));
            String cond = rc.input == null || rc.input.isBlank() ? null : itemCondition(rc.input);
            if (cond == null) {
                ctx.note("multiblock " + mb.name + ": recipe " + id + " has an input this target cannot match");
                continue;
            }
            String cost = amt != null && rc.cost > 0 ? " if score " + cur + " " + amt + " matches " + rc.cost + ".." : "";
            take.add("execute if score #got " + t + " matches 0 if score #c " + t + " matches " + n + ".." + cost + " " + cond + " run function " + fn + "start_" + id);
            List<String> start = new ArrayList<>(List.of(
                    "scoreboard players set #got " + t + " 1",
                    "scoreboard players remove #c " + t + " " + n,
                    "execute if score #c " + t + " matches 1.. store result entity @s " + countPath() + " " + (ctx.components() ? "int" : "byte")
                            + " 1 run scoreboard players get #c " + t,
                    "execute if score #c " + t + " matches ..0 run kill @s",
                    "scoreboard players set " + cur + " " + mbq + " " + id,
                    "scoreboard players set " + cur + " " + mbt + " 0"));
            if (!cost.isEmpty()) start.add("scoreboard players remove " + cur + " " + amt + " " + rc.cost);
            pc.functions.put(dir + "/start_" + id, start);

            // output: a loot table with the result and the byproduct
            JsonArray pools = new JsonArray();
            if (rc.output != null && !rc.output.isBlank() && !projectBlock(Ids.full(rc.output.trim()))) {
                pools.add(J.obj("rolls", 1, "entries", J.arr(lootItem(rc.output, rc.outputCount))));
            }
            if (rc.secondary != null && !rc.secondary.isBlank() && rc.secondaryChance > 0 && !projectBlock(Ids.full(rc.secondary.trim()))) {
                JsonObject pool = J.obj("rolls", 1, "entries", J.arr(lootItem(rc.secondary, rc.secondaryCount)));
                if (rc.secondaryChance < 100) {
                    pool.add("conditions", J.arr(J.obj("condition", "minecraft:random_chance", "chance", Math.min(100, rc.secondaryChance) / 100.0)));
                }
                pools.add(pool);
            }
            String table = dir + "/recipe_" + id;
            ctx.files.json(root + "data/" + ns + "/" + ctx.lootDir() + "/" + table + ".json", J.obj("pools", pools));
            String out = loc(ox, oy, oz);
            pc.compile(dir + "/finish_" + id, mb.onFinish, List.of(
                    "scoreboard players set @s " + mbq + " 0",
                    "scoreboard players set @s " + mbt + " 0",
                    "execute positioned " + out + " store success score #box " + t + " run loot insert ~ ~ ~ loot " + ns + ":" + table,
                    "execute if score #box " + t + " matches 0 positioned " + out + " run loot spawn ~ ~ ~ loot " + ns + ":" + table), List.of());
            work.add("execute if score @s " + mbq + " matches " + id + " if score @s " + mbt + " matches " + Math.max(1, rc.time) + ".. run function " + fn + "finish_" + id);
        }
        pc.functions.put(dir + "/take", take);
        pc.functions.put(dir + "/work", work);
    }
}
