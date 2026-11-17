package modstudio.gen;

import modstudio.core.Systems.KeyBind;

import java.util.ArrayList;
import java.util.List;

/**
 * Data pack side of key bindings. The mod's client sends /trigger &lt;ns&gt;.key set &lt;number&gt; when a key is pressed,
 * and a tick function runs the key's function for that number. The trigger also works typed by hand, so a key can be
 * used without the mod. On data packs and plugins a key can instead start on sneaking or jumping.
 */
public final class KeyGen {
    private KeyGen() {
    }

    public static String objective(String ns) {
        return ns + ".key";
    }

    public static void generate(GenContext ctx, ProcCompiler pc, List<String> load, List<String> tick) {
        List<KeyBind> keys = ctx.all(KeyBind.class);
        if (keys.isEmpty()) return;
        String ns = ctx.ns, obj = objective(ns);
        List<String> press = new ArrayList<>(), sneak = new ArrayList<>(), jump = new ArrayList<>();
        boolean fallbacks = !ctx.target.isMod();
        for (int i = 0; i < keys.size(); i++) {
            KeyBind k = keys.get(i);
            String fn = "keys/" + k.name;
            pc.compile(fn, k.steps, List.of(), List.of());
            press.add("execute if score @s " + obj + " matches " + (i + 1) + " run function " + ns + ":" + fn);
            if (!fallbacks) continue;
            if (k.fallback.equals("sneak")) sneak.add("function " + ns + ":" + fn);
            if (k.fallback.equals("jump")) jump.add("function " + ns + ":" + fn);
        }
        load.add("scoreboard objectives add " + obj + " trigger");
        tick.add("scoreboard players enable @a " + obj);
        tick.add("execute as @a[scores={" + obj + "=1..}] at @s run function " + ns + ":keys/_press");
        press.add("scoreboard players set @s " + obj + " 0");
        pc.functions.put("keys/_press", press);

        if (!sneak.isEmpty()) {
            // the statistic counts up every tick while sneaking; the tag marks players already handled
            String s = ns + ".ksn";
            load.add("scoreboard objectives add " + s + " minecraft.custom:minecraft.sneak_time");
            tick.add("execute as @a[scores={" + s + "=1..},tag=!" + s + "] at @s run function " + ns + ":keys/_sneak");
            tick.add("tag @a[tag=" + s + ",scores={" + s + "=0}] remove " + s);
            tick.add("scoreboard players set @a " + s + " 0");
            sneak.add("tag @s add " + s);
            pc.functions.put("keys/_sneak", sneak);
        }
        if (!jump.isEmpty()) {
            String j = ns + ".kjp";
            load.add("scoreboard objectives add " + j + " minecraft.custom:minecraft.jump");
            tick.add("execute as @a[scores={" + j + "=1..}] at @s run function " + ns + ":keys/_jump");
            tick.add("scoreboard players set @a[scores={" + j + "=1..}] " + j + " 0");
            pc.functions.put("keys/_jump", jump);
        }
    }
}
