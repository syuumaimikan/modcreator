package modstudio.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import modstudio.core.Ids;
import modstudio.core.Logic;
import modstudio.core.Elements.Step;
import modstudio.core.Systems.Dialog;
import modstudio.core.Systems.DialogButton;
import modstudio.core.Systems.Trader;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialogs and traders. A dialog is a registry file shown with /dialog on 1.21.6 and later and a block of clickable
 * chat lines before that; in both forms buttons that run steps send /trigger, which every player may use, and a tick
 * function dispatches the number to the button's function. Traders get one summon function each.
 */
public final class DialogGen {
    private final GenContext ctx;
    private final ProcCompiler pc;
    private final List<String> load, tick;
    private final String ns;

    public DialogGen(GenContext ctx, ProcCompiler pc, List<String> load, List<String> tick) {
        this.ctx = ctx;
        this.pc = pc;
        this.load = load;
        this.tick = tick;
        this.ns = ctx.ns;
    }

    public static String showFunction(String ns, String dialog) {
        return ns + ":dialogs/" + dialog + "/show";
    }

    public void generate() {
        dialogs();
        for (Trader t : ctx.all(Trader.class)) pc.functions.put("traders/" + t.name, List.of(TraderGen.summon(ctx, t, pc.customItems())));
    }

    private String obj() {
        return ns + ".dlg";
    }

    private static int buttonId(int dialogIndex, int button) {
        return (dialogIndex + 1) * 100 + button + 1;
    }

    /** True when the button needs the trigger: it runs steps, or it opens another dialog through chat. */
    private boolean triggered(DialogButton b) {
        return b.action.equals("run") || (b.action.equals("dialog") && !ctx.v.dialogs());
    }

    private void dialogs() {
        List<Dialog> all = ctx.all(Dialog.class);
        if (all.isEmpty()) return;
        List<String> click = new ArrayList<>();
        for (int di = 0; di < all.size(); di++) {
            Dialog d = all.get(di);
            List<DialogButton> buttons = d.effectiveButtons();
            for (int bi = 0; bi < buttons.size(); bi++) {
                DialogButton b = buttons.get(bi);
                if (!triggered(b)) continue;
                String fn = "dialogs/" + d.name + "/button_" + (bi + 1);
                List<Step> steps = new ArrayList<>();
                if (b.action.equals("run")) steps.addAll(b.steps);
                else {
                    Step s = Logic.newStep("show_dialog");
                    s.p.put("dialog", b.target.trim());
                    s.p.put("target", "@s");
                    steps.add(s);
                }
                pc.compile(fn, steps, List.of(), List.of());
                click.add("execute if score @s " + obj() + " matches " + buttonId(di, bi) + " run function " + ns + ":" + fn);
            }
            List<String> show = new ArrayList<>();
            if (!d.openSound.isBlank()) show.add("playsound " + Ids.full(d.openSound.trim()) + " master @s ~ ~ ~ 1 1");
            if (ctx.v.dialogs()) {
                pc.dataJson.put("dialog/" + d.name, nativeDialog(d, di));
                show.add("dialog show @s " + ns + ":" + d.name);
            } else show.addAll(chat(d, di));
            pc.functions.put("dialogs/" + d.name + "/show", show);
        }
        if (!click.isEmpty()) {
            load.add("scoreboard objectives add " + obj() + " trigger");
            tick.add("scoreboard players enable @a " + obj());
            tick.add("execute as @a[scores={" + obj() + "=1..}] at @s run function " + ns + ":dialogs/_click");
            click.add("scoreboard players set @s " + obj() + " 0");
            pc.functions.put("dialogs/_click", click);
        }
    }

    // ------------------------------------------------------------------ 1.21.6+ dialog files

    private JsonObject nativeDialog(Dialog d, int di) {
        String title = d.title.isBlank() ? Ids.titleCase(d.name) : d.title;
        JsonObject o = J.obj("type", "minecraft:" + d.kind, "title", InterfaceGen.rich(title, null));
        if (!d.body.isBlank()) {
            JsonArray body = new JsonArray();
            for (String para : d.body.split("\\R\\s*\\R")) {
                body.add(J.obj("type", "minecraft:plain_message", "contents", InterfaceGen.rich(para.strip(), null)));
            }
            o.add("body", body);
        }
        o.addProperty("can_close_with_escape", d.canClose);
        o.addProperty("pause", d.pause);
        List<DialogButton> buttons = d.effectiveButtons();
        switch (d.kind) {
            case "notice" -> o.add("action", button(buttons.get(0), di, 0));
            case "confirmation" -> {
                o.add("yes", button(buttons.get(0), di, 0));
                o.add("no", button(buttons.get(1), di, 1));
            }
            default -> {
                JsonArray actions = new JsonArray();
                for (int i = 0; i < buttons.size(); i++) actions.add(button(buttons.get(i), di, i));
                o.add("actions", actions);
                o.addProperty("columns", Math.max(1, d.columns));
            }
        }
        return o;
    }

    private JsonObject button(DialogButton b, int di, int bi) {
        JsonObject o = J.obj("label", InterfaceGen.rich(b.label.isBlank() ? "OK" : b.label, null));
        if (!b.tooltip.isBlank()) o.add("tooltip", InterfaceGen.rich(b.tooltip, null));
        if (b.width != 150) o.addProperty("width", Math.max(1, Math.min(1024, b.width)));
        String target = b.target.trim();
        JsonObject action = switch (b.action) {
            case "run" -> J.obj("type", "minecraft:run_command", "command", "trigger " + obj() + " set " + buttonId(di, bi));
            case "dialog" -> J.obj("type", "minecraft:show_dialog", "dialog", target.contains(":") ? target : ns + ":" + target);
            case "url" -> J.obj("type", "minecraft:open_url", "url", target);
            case "suggest" -> J.obj("type", "minecraft:suggest_command", "command", target.startsWith("/") ? target : "/" + target);
            case "copy" -> J.obj("type", "minecraft:copy_to_clipboard", "value", target);
            default -> null;
        };
        if (action != null) o.add("action", action);
        return o;
    }

    // ------------------------------------------------------------------ chat form before 1.21.6

    private List<String> chat(Dialog d, int di) {
        List<String> out = new ArrayList<>();
        String title = d.title.isBlank() ? Ids.titleCase(d.name) : d.title;
        out.add("tellraw @s " + J.compact(InterfaceGen.rich("&6&l" + title, null)));
        if (!d.body.isBlank()) {
            for (String line : d.body.split("\\R")) out.add("tellraw @s " + J.compact(InterfaceGen.rich(line.isEmpty() ? " " : line, null)));
        }
        JsonArray row = new JsonArray();
        row.add("");
        List<DialogButton> buttons = d.effectiveButtons();
        boolean any = false;
        for (int i = 0; i < buttons.size(); i++) {
            DialogButton b = buttons.get(i);
            JsonObject c = chatButton(b, di, i);
            if (c == null) continue;
            if (any) row.add("  ");
            row.add(c);
            any = true;
        }
        if (any) out.add("tellraw @s " + J.compact(row));
        return out;
    }

    private JsonObject chatButton(DialogButton b, int di, int bi) {
        String target = b.target.trim();
        String action, value;
        switch (b.action) {
            case "run", "dialog" -> {
                action = "run_command";
                value = "/trigger " + obj() + " set " + buttonId(di, bi);
            }
            case "url" -> {
                action = "open_url";
                value = target;
            }
            case "suggest" -> {
                action = "suggest_command";
                value = target.startsWith("/") ? target : "/" + target;
            }
            case "copy" -> {
                action = "copy_to_clipboard";
                value = target;
            }
            default -> {
                return null;
            }
        }
        JsonObject c = J.obj("text", "[", "color", "aqua");
        JsonArray extra = InterfaceGen.rich(b.label.isBlank() ? "OK" : b.label, null);
        extra.add("]");
        c.add("extra", extra);
        boolean modern = ctx.v.snbtText();
        String key = switch (action) {
            case "open_url" -> "url";
            case "run_command", "suggest_command" -> "command";
            default -> "value";
        };
        if (modern) c.add("click_event", J.obj("action", action, key, value));
        else c.add("clickEvent", J.obj("action", action, "value", value));
        if (!b.tooltip.isBlank()) {
            if (modern) c.add("hover_event", J.obj("action", "show_text", "value", InterfaceGen.rich(b.tooltip, null)));
            else c.add("hoverEvent", J.obj("action", "show_text", "contents", InterfaceGen.rich(b.tooltip, null)));
        }
        return c;
    }
}
