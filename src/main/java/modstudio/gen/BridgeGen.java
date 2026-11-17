package modstudio.gen;

import modstudio.core.CommandSpec;
import modstudio.core.Elements;
import modstudio.core.Ids;
import modstudio.core.Systems.ApiRequest;
import modstudio.core.Systems.Extract;
import modstudio.core.Systems.Menu;
import modstudio.core.Systems.MenuSlot;
import modstudio.core.Target;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Java side of the interface systems for mods (bridge command, inventory menus, command trees) and Paper (tag
 * polling, inventory menus, command trees), plus the shared web request runtime.
 */
public final class BridgeGen {
    private final GenContext ctx;
    private final String ns;

    public BridgeGen(GenContext ctx) {
        this.ctx = ctx;
        this.ns = ctx.ns;
    }

    public static String template(String name, GenContext ctx) {
        try (InputStream in = BridgeGen.class.getResourceAsStream("/modstudio/runtime/" + name + ".java.txt")) {
            if (in == null) throw new IllegalStateException("missing runtime template " + name);
            String s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return s.replace("{{package}}", ctx.pkg).replace("{{modid}}", ctx.ns).replace("{{macros}}", Boolean.toString(ctx.v.macros()));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean hasMenus() {
        return !ctx.all(Menu.class).isEmpty();
    }

    private boolean hasWeb() {
        return InterfaceGen.needsWeb(ctx.p);
    }

    private boolean hasTrees() {
        return ctx.all(Elements.Command.class).stream().anyMatch(c -> !c.branches.isEmpty());
    }

    // ------------------------------------------------------------------ shared

    private void webFiles() {
        if (!hasWeb()) return;
        ctx.files.text(ctx.javaDir() + "WebBridge.java", template("WebBridge", ctx));
        Src s = new Src(ctx.pkg);
        s.imp("java.util.List");
        s.l("/** Web requests and real-time schedules of this project. */");
        s.l("public final class WebSpecs {");
        s.l("private static boolean done;");
        s.blank();
        s.l("private WebSpecs() {").l("}");
        s.blank();
        s.l("public static synchronized void register() {");
        s.l("if (done) return;");
        s.l("done = true;");
        for (ApiRequest r : ctx.all(ApiRequest.class)) {
            List<String> headers = new ArrayList<>();
            for (String h : r.headers.split("\\R")) if (h.contains(":")) headers.add(Src.str(h.trim()));
            List<String> ex = new ArrayList<>();
            for (Extract x : r.extract) {
                if (x.key.isBlank()) continue;
                ex.add("new WebBridge.Extract(" + Src.str(x.path.trim()) + ", " + Src.str(x.key.trim()) + ", " + x.toScore + ")");
            }
            String method = List.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(r.method) ? r.method : "GET";
            s.l("WebBridge.define(new WebBridge.Spec(" + Src.str(r.name) + ", " + Src.str(method) + ", " + Src.str(r.url.trim()) + ",");
            s.l("        List.of(" + String.join(", ", headers) + "), " + Src.str(r.body) + ", " + Math.max(1, r.timeout) + ", " + Math.max(0, r.retries) + ",");
            s.l("        List.of(" + String.join(", ", ex) + "),");
            s.l("        " + Src.str(r.schedule) + ", " + Math.max(5, r.intervalSeconds) + ", " + Src.str(r.cron.trim()) + "));");
        }
        for (Elements.Procedure p : ctx.all(Elements.Procedure.class)) {
            if (p.trigger.equals("cron")) s.l("WebBridge.cron(" + Src.str(p.triggerValue.trim()) + ", " + Src.str("procedures/" + p.name) + ");");
        }
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "WebSpecs.java", s.toString());
    }

    private static String itemName(MenuSlot sl) {
        return GenContext.legacy(sl.name);
    }

    private static List<String> lore(MenuSlot sl) {
        List<String> r = new ArrayList<>();
        if (!sl.lore.isBlank()) for (String l : sl.lore.split("\\R")) r.add(Src.str(GenContext.legacy("&7" + l)));
        if (sl.cost > 0 && !sl.costObjective.isBlank()) r.add(Src.str(GenContext.legacy("&e" + sl.cost + " " + sl.costObjective)));
        return r;
    }

    private static String fillerOf(Menu m) {
        String f = m.filler == null ? "" : m.filler.trim();
        return f.isEmpty() ? "" : Ids.full(f);
    }

    // ------------------------------------------------------------------ mods

    public boolean modNeeded() {
        return InterfaceGen.needsBridge(ctx.p) || hasTrees();
    }

    /** Writes the Java files for mod loaders; ModCommands calls register(dispatcher). */
    public void mod() {
        if (hasTrees()) ctx.files.text(ctx.javaDir() + "ModArgs.java", template("ModArgs", ctx));
        if (!InterfaceGen.needsBridge(ctx.p)) return;
        webFiles();
        modBridge();
        if (hasMenus()) modMenus();
    }

    private void modBridge() {
        Src s = new Src(ctx.pkg);
        s.imp("com.mojang.brigadier.CommandDispatcher", "com.mojang.brigadier.arguments.StringArgumentType",
                "net.minecraft.commands.CommandSourceStack", "net.minecraft.commands.Commands");
        boolean web = hasWeb(), menus = hasMenus();
        if (web) s.imp("com.mojang.logging.LogUtils", "net.minecraft.server.MinecraftServer", "net.minecraft.world.entity.Entity",
                "java.nio.file.Path", "org.slf4j.Logger");
        if (menus) s.imp("net.minecraft.server.level.ServerPlayer");
        s.l("/** Hidden command used by this project's functions to reach Java: menus and web requests. */");
        s.l("public final class ModBridge" + (web ? " implements WebBridge.Host" : "") + " {");
        if (web) {
            s.l("private static final Logger LOGGER = LogUtils.getLogger();");
            s.l("private static MinecraftServer server;");
            s.blank();
        }
        s.l("private ModBridge() {").l("}");
        s.blank();
        s.l("public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {");
        s.l("dispatcher.register(Commands.literal(" + ctx.mainClass + ".MODID + \"_bridge\").requires(source -> source.hasPermission(2))");
        if (web) {
            s.l(".then(Commands.literal(\"init\").executes(c -> {");
            s.l("init(c.getSource().getServer());");
            s.l("return 1;");
            s.l("}))");
            s.l(".then(Commands.literal(\"http\").then(Commands.argument(\"name\", StringArgumentType.word()).executes(c -> {");
            s.l("Entity e = c.getSource().getEntity();");
            s.l("WebBridge.request(StringArgumentType.getString(c, \"name\"), e == null ? \"\" : e.getStringUUID(), e == null ? \"\" : e.getName().getString());");
            s.l("return 1;");
            s.l("})))");
        }
        if (menus) {
            s.l(".then(Commands.literal(\"menu\").then(Commands.argument(\"name\", StringArgumentType.word()).executes(c -> {");
            s.l("if (c.getSource().getEntity() instanceof ServerPlayer player) ModMenus.open(player, StringArgumentType.getString(c, \"name\"));");
            s.l("return 1;");
            s.l("})))");
            s.l(".then(Commands.literal(\"close\").executes(c -> {");
            s.l("if (c.getSource().getEntity() instanceof ServerPlayer player) player.closeContainer();");
            s.l("return 1;");
            s.l("}))");
        }
        s.l(");");
        s.l("}");
        if (web) {
            s.blank();
            s.l("private static void init(MinecraftServer s) {");
            s.l("server = s;");
            s.l("WebSpecs.register();");
            s.l("WebBridge.start(new ModBridge());");
            s.l("}");
            s.blank();
            s.l("@Override");
            s.l("public void runOnMain(Runnable task) {");
            s.l("MinecraftServer s = server;");
            s.l("if (s != null && s.isRunning()) s.execute(task);");
            s.l("}");
            s.blank();
            s.l("@Override");
            s.l("public void command(String command) {");
            s.l("MinecraftServer s = server;");
            s.l("if (s == null) return;");
            s.l("s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), command);");
            s.l("}");
            s.blank();
            s.l("@Override");
            s.l("public Path secretsFile() {");
            s.l("return Path.of(\"config\", " + ctx.mainClass + ".MODID + \"-secrets.properties\");");
            s.l("}");
            s.blank();
            s.l("@Override");
            s.l("public void log(String message) {");
            s.l("LOGGER.info(\"[\" + " + ctx.mainClass + ".MODID + \"] \" + message);");
            s.l("}");
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModBridge.java", s.toString());
    }

    private void modMenus() {
        Src s = new Src(ctx.pkg);
        s.imp("net.minecraft.network.chat.Component", "net.minecraft.server.level.ServerPlayer", "net.minecraft.world.Container",
                "net.minecraft.world.SimpleContainer", "net.minecraft.world.SimpleMenuProvider", "net.minecraft.world.entity.player.Inventory",
                "net.minecraft.world.entity.player.Player", "net.minecraft.world.inventory.AbstractContainerMenu",
                "net.minecraft.world.inventory.ChestMenu", "net.minecraft.world.inventory.ClickType", "net.minecraft.world.inventory.DispenserMenu",
                "net.minecraft.world.inventory.HopperMenu", "net.minecraft.world.inventory.MenuType", "net.minecraft.world.item.ItemStack",
                "java.util.List");
        if (ctx.components()) {
            s.imp("net.minecraft.core.component.DataComponents", "net.minecraft.world.item.component.ItemLore", "java.util.ArrayList");
        } else {
            s.imp("net.minecraft.nbt.CompoundTag", "net.minecraft.nbt.ListTag", "net.minecraft.nbt.StringTag");
        }
        s.l("/** Inventory menus built in the editor. Clicks never move items; they run the slot functions. */");
        s.l("public final class ModMenus {");
        s.l("private ModMenus() {").l("}");
        s.blank();
        s.l("public static void open(ServerPlayer player, String name) {");
        s.l("switch (name) {");
        for (Menu m : ctx.all(Menu.class)) {
            int size = m.size();
            s.l("case " + Src.str(m.name) + " -> {");
            s.l("SimpleContainer c = new SimpleContainer(" + size + ");");
            for (MenuSlot sl : m.slots) {
                if (sl.slot < 0 || sl.slot >= size || sl.item.isBlank()) continue;
                s.l("c.setItem(" + sl.slot + ", stack(" + Src.str(Ids.full(sl.item)) + ", " + Math.max(1, Math.min(99, sl.count)) + ", "
                        + Src.str(itemName(sl)) + ", List.of(" + String.join(", ", lore(sl)) + "), " + sl.glint + "));");
            }
            if (!fillerOf(m).isEmpty()) s.l("fill(c, " + Src.str(fillerOf(m)) + ");");
            String title = "text(" + Src.str(GenContext.legacy(m.title.isBlank() ? Ids.titleCase(m.name) : m.title)) + ")";
            String ctor = switch (m.layout) {
                case "hopper" -> "new Hopper(id, inv, c, " + Src.str(m.name) + ")";
                case "dispenser" -> "new Dispenser(id, inv, c, " + Src.str(m.name) + ")";
                default -> {
                    int rows = size / 9;
                    yield "new Chest(MenuType.GENERIC_9x" + rows + ", id, inv, c, " + rows + ", " + Src.str(m.name) + ")";
                }
            };
            s.l("player.openMenu(new SimpleMenuProvider((id, inv, p) -> " + ctor + ", " + title + "));");
            s.l("}");
        }
        s.l("default -> {");
        s.l("}");
        s.l("}");
        s.l("}");
        s.blank();
        s.l("static void click(ServerPlayer player, String menu, int slot) {");
        s.l("switch (menu + \"#\" + slot) {");
        for (Menu m : ctx.all(Menu.class)) {
            for (MenuSlot sl : m.slots) {
                if (!InterfaceGen.slotActive(sl)) continue;
                String fn = Src.str("menus/" + m.name + "/slot_" + sl.slot);
                s.l("case " + Src.str(m.name + "#" + sl.slot) + " -> {");
                if (InterfaceGen.javaPermission(ctx.target, sl)) {
                    s.l("if (player.hasPermissions(" + sl.permission.trim() + ")) ModProcedures.run(player, " + fn + ");");
                    s.l("else player.sendSystemMessage(text(" + Src.str(GenContext.legacy(m.denyMessage)) + "));");
                } else {
                    s.l("ModProcedures.run(player, " + fn + ");");
                }
                s.l("}");
            }
        }
        s.l("default -> {");
        s.l("}");
        s.l("}");
        s.l("}");
        s.blank();
        s.l("private static Component text(String s) {");
        s.l("return Component.literal(s).withStyle(style -> style.withItalic(false));");
        s.l("}");
        s.blank();
        s.l("private static void fill(SimpleContainer c, String id) {");
        s.l("for (int i = 0; i < c.getContainerSize(); i++) {");
        s.l("if (c.getItem(i).isEmpty()) c.setItem(i, stack(id, 1, \" \", List.of(), false));");
        s.l("}");
        s.l("}");
        s.blank();
        s.l("private static ItemStack stack(String id, int count, String name, List<String> lore, boolean glint) {");
        s.l("ItemStack s = new ItemStack(ModProcedures.item(id), count);");
        if (ctx.components()) {
            s.l("if (!name.isEmpty()) s.set(DataComponents.CUSTOM_NAME, text(name));");
            s.l("if (!lore.isEmpty()) {");
            s.l("List<Component> lines = new ArrayList<>();");
            s.l("for (String l : lore) lines.add(text(l));");
            s.l("s.set(DataComponents.LORE, new ItemLore(lines));");
            s.l("}");
            s.l("if (glint) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);");
        } else {
            s.l("if (!name.isEmpty()) s.setHoverName(text(name));");
            s.l("if (!lore.isEmpty()) {");
            s.l("ListTag lines = new ListTag();");
            s.l("for (String l : lore) lines.add(StringTag.valueOf(Component.Serializer.toJson(text(l))));");
            s.l("s.getOrCreateTagElement(\"display\").put(\"Lore\", lines);");
            s.l("}");
            s.l("if (glint) {");
            s.l("ListTag ench = new ListTag();");
            s.l("ench.add(new CompoundTag());");
            s.l("s.getOrCreateTag().put(\"Enchantments\", ench);");
            s.l("}");
        }
        s.l("return s;");
        s.l("}");
        s.blank();
        s.l("private static void handle(AbstractContainerMenu menu, String name, int size, int slot, Player player) {");
        s.l("menu.sendAllDataToRemote();");
        s.l("if (slot >= 0 && slot < size && player instanceof ServerPlayer sp) click(sp, name, slot);");
        s.l("}");
        s.blank();
        s.l("static final class Chest extends ChestMenu {");
        s.l("private final String name;");
        s.l("private final int size;");
        s.blank();
        s.l("Chest(MenuType<?> type, int id, Inventory inv, Container c, int rows, String name) {");
        s.l("super(type, id, inv, c, rows);");
        s.l("this.name = name;");
        s.l("this.size = rows * 9;");
        s.l("}");
        menuOverrides(s);
        s.l("}");
        for (String kind : List.of("Hopper", "Dispenser")) {
            s.blank();
            s.l("static final class " + kind + " extends " + kind + "Menu {");
            s.l("private final String name;");
            s.l("private final int size;");
            s.blank();
            s.l(kind + "(int id, Inventory inv, Container c, String name) {");
            s.l("super(id, inv, c);");
            s.l("this.name = name;");
            s.l("this.size = c.getContainerSize();");
            s.l("}");
            menuOverrides(s);
            s.l("}");
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModMenus.java", s.toString());
    }

    private static void menuOverrides(Src s) {
        s.blank();
        s.l("@Override");
        s.l("public void clicked(int slot, int button, ClickType type, Player player) {");
        s.l("handle(this, name, size, slot, player);");
        s.l("}");
        s.blank();
        s.l("@Override");
        s.l("public ItemStack quickMoveStack(Player player, int slot) {");
        s.l("return ItemStack.EMPTY;");
        s.l("}");
    }

    // ------------------------------------------------------------------ Brigadier trees (mods)

    private static final class Node {
        final CommandSpec.Token token;
        final Map<String, Node> children = new LinkedHashMap<>();
        String function;
        int permission;
        List<CommandSpec.Token> path = List.of();

        Node(CommandSpec.Token token) {
            this.token = token;
        }
    }

    private static String key(CommandSpec.Token t) {
        return t.literal() ? "l:" + t.name() : "a:" + t.name() + ":" + t.type() + ":" + t.min() + ":" + t.max() + ":" + t.choices();
    }

    /** Registration statement for a command with branches, as source lines. */
    public List<String> brigadier(Elements.Command c) {
        int base = Math.max(0, Math.min(4, c.permission));
        Node root = new Node(null);
        if (!c.steps.isEmpty() || c.branches.isEmpty()) {
            root.function = "commands/" + c.name;
            root.permission = base;
        }
        List<String> fns = InterfaceGen.branchFunctions(c);
        int min = base;
        for (int i = 0; i < c.branches.size(); i++) {
            Elements.CommandBranch b = c.branches.get(i);
            List<CommandSpec.Token> toks = CommandSpec.tryParse(b.path);
            if (toks == null || fns.get(i) == null) continue;
            int perm = b.permission < 0 ? base : Math.min(4, b.permission);
            min = Math.min(min, perm);
            for (List<CommandSpec.Token> variant : CommandSpec.expansions(toks)) {
                Node n = root;
                for (CommandSpec.Token t : variant) n = n.children.computeIfAbsent(key(t), k -> new Node(t));
                if (n.function == null) {
                    n.function = fns.get(i);
                    n.permission = perm;
                    n.path = variant;
                }
            }
        }
        List<String> out = new ArrayList<>();
        out.add("dispatcher.register(Commands.literal(" + Src.str(c.name) + ").requires(source -> source.hasPermission(" + min + "))");
        if (root.function != null) {
            out.add("        .executes(c -> ModArgs.run(c, " + Src.str(root.function) + ", " + root.permission + "))");
        }
        for (Node child : root.children.values()) emit(child, out, 2);
        out.add("        );");
        return out;
    }

    private void emit(Node n, List<String> out, int depth) {
        String pad = "    ".repeat(depth);
        CommandSpec.Token t = n.token;
        String head = t.literal() ? "Commands.literal(" + Src.str(t.name()) + ")"
                : "Commands.argument(" + Src.str(t.name()) + ", " + argType(t) + ")" + suggests(t);
        out.add(pad + ".then(" + head);
        if (n.function != null) {
            List<String> checks = new ArrayList<>();
            for (CommandSpec.Token p : n.path) {
                if (p.literal() || !p.type().equals("choice")) continue;
                StringBuilder check = new StringBuilder("ModArgs.choice(c, ").append(Src.str(p.name()));
                for (String v : p.choices()) check.append(", ").append(Src.str(v));
                checks.add(check.append(')').toString());
            }
            String run = "ModArgs.run(c, " + Src.str(n.function) + ", " + n.permission + ")";
            out.add(pad + "        .executes(c -> " + (checks.isEmpty() ? run : String.join(" && ", checks) + " ? " + run + " : 0") + ")");
        }
        for (Node child : n.children.values()) emit(child, out, depth + 2);
        out.add(pad + ")");
    }

    private static String num(Double d, boolean integer, String def) {
        if (d == null) return def;
        if (integer) return Long.toString(Math.round(d));
        return Double.toString(d);
    }

    private static String argType(CommandSpec.Token t) {
        return switch (t.type()) {
            case "string" -> "StringArgumentType.string()";
            case "text" -> "StringArgumentType.greedyString()";
            case "int" -> t.min() == null && t.max() == null ? "IntegerArgumentType.integer()"
                    : "IntegerArgumentType.integer(" + num(t.min(), true, "Integer.MIN_VALUE") + ", " + num(t.max(), true, "Integer.MAX_VALUE") + ")";
            case "double" -> t.min() == null && t.max() == null ? "DoubleArgumentType.doubleArg()"
                    : "DoubleArgumentType.doubleArg(" + num(t.min(), false, "-Double.MAX_VALUE") + ", " + num(t.max(), false, "Double.MAX_VALUE") + ")";
            case "bool" -> "BoolArgumentType.bool()";
            case "player" -> "EntityArgument.player()";
            case "players" -> "EntityArgument.players()";
            case "entity" -> "EntityArgument.entity()";
            case "entities" -> "EntityArgument.entities()";
            case "blockpos" -> "BlockPosArgument.blockPos()";
            case "item", "block" -> "ResourceLocationArgument.id()";
            default -> "StringArgumentType.word()";
        };
    }

    private static String suggests(CommandSpec.Token t) {
        return switch (t.type()) {
            case "choice" -> {
                List<String> v = new ArrayList<>();
                for (String c : t.choices()) v.add(Src.str(c));
                yield ".suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{" + String.join(", ", v) + "}, b))";
            }
            case "item" -> ".suggests((c, b) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), b))";
            case "block" -> ".suggests((c, b) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(), b))";
            default -> "";
        };
    }

    public static void treeImports(Src s) {
        s.imp("com.mojang.brigadier.arguments.BoolArgumentType", "com.mojang.brigadier.arguments.DoubleArgumentType",
                "com.mojang.brigadier.arguments.IntegerArgumentType", "com.mojang.brigadier.arguments.StringArgumentType",
                "net.minecraft.commands.SharedSuggestionProvider", "net.minecraft.commands.arguments.EntityArgument",
                "net.minecraft.commands.arguments.ResourceLocationArgument", "net.minecraft.commands.arguments.coordinates.BlockPosArgument",
                "net.minecraft.core.registries.BuiltInRegistries");
    }

    // ------------------------------------------------------------------ Paper

    public boolean paperNeeded() {
        return InterfaceGen.needsBridge(ctx.p);
    }

    /** Writes the Paper files; the main class calls Bridge.start(this) and registers command trees. */
    public void paper(String main) {
        if (hasTrees()) ctx.files.text(ctx.javaDir() + "CommandTree.java", template("CommandTree", ctx));
        if (!InterfaceGen.needsBridge(ctx.p)) return;
        webFiles();
        paperBridge(main);
        if (hasMenus()) paperMenus(main);
    }

    /** Java expression constructing the CommandTree for a command, or null when it has no branches. */
    public String paperTree(Elements.Command c) {
        if (c.branches.isEmpty()) return null;
        int base = Math.max(0, Math.min(4, c.permission));
        List<String> fns = InterfaceGen.branchFunctions(c);
        List<String> branches = new ArrayList<>();
        for (int i = 0; i < c.branches.size(); i++) {
            Elements.CommandBranch b = c.branches.get(i);
            List<CommandSpec.Token> toks = CommandSpec.tryParse(b.path);
            if (toks == null || fns.get(i) == null) continue;
            int perm = b.permission < 0 ? base : Math.min(4, b.permission);
            for (List<CommandSpec.Token> variant : CommandSpec.expansions(toks)) {
                List<String> args = new ArrayList<>();
                for (CommandSpec.Token t : variant) {
                    List<String> ch = new ArrayList<>();
                    for (String x : t.choices()) ch.add(Src.str(x));
                    args.add("new CommandTree.Arg(" + Src.str(t.name()) + ", " + Src.str(t.type()) + ", " + t.literal() + ", "
                            + (t.min() == null ? "null" : t.min() + "d") + ", " + (t.max() == null ? "null" : t.max() + "d") + ", List.of(" + String.join(", ", ch) + "))");
                }
                StringBuilder usage = new StringBuilder();
                for (CommandSpec.Token t : toks) usage.append(usage.isEmpty() ? "" : " ").append(t.display());
                branches.add("new CommandTree.Branch(List.of(" + String.join(", ", args) + "), " + Src.str(fns.get(i)) + ", " + perm + ", " + Src.str(usage.toString()) + ")");
            }
        }
        String baseFn = !c.steps.isEmpty() ? Src.str("commands/" + c.name) : "null";
        return "new CommandTree(" + baseFn + ", " + base + ", List.of(" + String.join(",\n        ", branches) + "))";
    }

    private void paperBridge(String main) {
        boolean web = hasWeb(), menus = hasMenus();
        Src s = new Src(ctx.pkg);
        s.imp("org.bukkit.Bukkit", "org.bukkit.entity.Player", "org.bukkit.plugin.java.JavaPlugin", "java.util.ArrayList");
        if (web) s.imp("org.bukkit.scoreboard.Objective", "org.bukkit.scoreboard.Score", "java.nio.file.Path");
        s.l("/** Picks up menu and web requests that functions leave as player tags and scores. */");
        s.l("public final class Bridge" + (web ? " implements WebBridge.Host" : "") + " {");
        s.l("private static final String PREFIX = " + main + ".MODID + \".q.\";");
        s.l("private final JavaPlugin plugin;");
        s.blank();
        s.l("private Bridge(JavaPlugin plugin) {");
        s.l("this.plugin = plugin;");
        s.l("}");
        s.blank();
        s.l("public static void start(JavaPlugin plugin) {");
        if (menus) s.l("plugin.getServer().getPluginManager().registerEvents(new Menus(), plugin);");
        s.l("plugin.getServer().getScheduler().runTaskTimer(plugin, Bridge::poll, 1L, 1L);");
        if (web) {
            s.l("WebSpecs.register();");
            s.l("WebBridge.start(new Bridge(plugin));");
        }
        s.l("}");
        s.blank();
        s.l("public static void stop() {");
        if (web) s.l("WebBridge.stop();");
        s.l("}");
        s.blank();
        s.l("private static void poll() {");
        s.l("for (Player p : Bukkit.getOnlinePlayers()) {");
        s.l("if (p.getScoreboardTags().isEmpty()) continue;");
        s.l("for (String tag : new ArrayList<>(p.getScoreboardTags())) {");
        s.l("if (!tag.startsWith(PREFIX)) continue;");
        s.l("p.removeScoreboardTag(tag);");
        s.l("String rest = tag.substring(PREFIX.length());");
        s.l("if (rest.equals(\"close\")) p.closeInventory();");
        if (menus) s.l("else if (rest.startsWith(\"menu.\")) Menus.open(p, rest.substring(5));");
        if (web) s.l("else if (rest.startsWith(\"http.\")) WebBridge.request(rest.substring(5), p.getUniqueId().toString(), p.getName());");
        s.l("}");
        s.l("}");
        if (web) {
            s.l("Objective q = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(" + main + ".MODID + \".q\");");
            s.l("if (q == null) return;");
            s.l("for (String name : WebBridge.names()) {");
            s.l("Score score = q.getScore(\"#http.\" + name);");
            s.l("if (!score.isScoreSet() || score.getScore() <= 0) continue;");
            s.l("score.setScore(0);");
            s.l("WebBridge.request(name, \"\", \"\");");
            s.l("}");
        }
        s.l("}");
        if (web) {
            s.blank();
            s.l("@Override");
            s.l("public void runOnMain(Runnable task) {");
            s.l("if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, task);");
            s.l("}");
            s.blank();
            s.l("@Override");
            s.l("public void command(String command) {");
            s.l("Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);");
            s.l("}");
            s.blank();
            s.l("@Override");
            s.l("public Path secretsFile() {");
            s.l("return plugin.getDataFolder().toPath().resolve(\"secrets.properties\");");
            s.l("}");
            s.blank();
            s.l("@Override");
            s.l("public void log(String message) {");
            s.l("plugin.getLogger().info(message);");
            s.l("}");
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "Bridge.java", s.toString());
    }

    private void paperMenus(String main) {
        Src s = new Src(ctx.pkg);
        s.imp("org.bukkit.Bukkit", "org.bukkit.Material", "org.bukkit.entity.Player", "org.bukkit.event.EventHandler",
                "org.bukkit.event.Listener", "org.bukkit.event.inventory.InventoryClickEvent", "org.bukkit.event.inventory.InventoryDragEvent",
                "org.bukkit.event.inventory.InventoryType", "org.bukkit.inventory.Inventory", "org.bukkit.inventory.InventoryHolder",
                "org.bukkit.inventory.ItemStack", "org.bukkit.inventory.meta.ItemMeta", "java.util.List");
        if (!ctx.components()) s.imp("org.bukkit.enchantments.Enchantment", "org.bukkit.inventory.ItemFlag");
        s.l("/** Inventory menus built in the editor. Clicks are cancelled and run the slot functions. */");
        s.l("public final class Menus implements Listener {");
        s.l("static final class Holder implements InventoryHolder {");
        s.l("final String name;");
        s.l("Inventory inventory;");
        s.blank();
        s.l("Holder(String name) {");
        s.l("this.name = name;");
        s.l("}");
        s.blank();
        s.l("@Override");
        s.l("public Inventory getInventory() {");
        s.l("return inventory;");
        s.l("}");
        s.l("}");
        s.blank();
        s.l("public static void open(Player player, String name) {");
        s.l("Holder holder = new Holder(name);");
        s.l("Inventory inv;");
        s.l("switch (name) {");
        for (Menu m : ctx.all(Menu.class)) {
            String title = Src.str(GenContext.legacy(m.title.isBlank() ? Ids.titleCase(m.name) : m.title));
            s.l("case " + Src.str(m.name) + " -> {");
            switch (m.layout) {
                case "hopper" -> s.l("inv = Bukkit.createInventory(holder, InventoryType.HOPPER, " + title + ");");
                case "dispenser" -> s.l("inv = Bukkit.createInventory(holder, InventoryType.DISPENSER, " + title + ");");
                default -> s.l("inv = Bukkit.createInventory(holder, " + m.size() + ", " + title + ");");
            }
            for (MenuSlot sl : m.slots) {
                if (sl.slot < 0 || sl.slot >= m.size() || sl.item.isBlank()) continue;
                s.l("inv.setItem(" + sl.slot + ", item(" + Src.str(Ids.full(sl.item)) + ", " + Math.max(1, Math.min(99, sl.count)) + ", "
                        + Src.str(itemName(sl)) + ", List.of(" + String.join(", ", lore(sl)) + "), " + sl.glint + "));");
            }
            if (!fillerOf(m).isEmpty()) s.l("fill(inv, " + Src.str(fillerOf(m)) + ");");
            s.l("}");
        }
        s.l("default -> {");
        s.l("return;");
        s.l("}");
        s.l("}");
        s.l("holder.inventory = inv;");
        s.l("player.openInventory(inv);");
        s.l("}");
        s.blank();
        s.l("static void click(Player player, String menu, int slot) {");
        s.l("switch (menu + \"#\" + slot) {");
        for (Menu m : ctx.all(Menu.class)) {
            for (MenuSlot sl : m.slots) {
                if (!InterfaceGen.slotActive(sl)) continue;
                String fn = Src.str("menus/" + m.name + "/slot_" + sl.slot);
                s.l("case " + Src.str(m.name + "#" + sl.slot) + " -> {");
                String perm = sl.permission.trim();
                if (InterfaceGen.javaPermission(ctx.target, sl)) {
                    String check = perm.matches("\\d") ? (perm.equals("0") ? "true" : "player.isOp()") : "player.hasPermission(" + Src.str(perm) + ")";
                    s.l("if (" + check + ") Procedures.run(player, " + fn + ");");
                    s.l("else player.sendMessage(" + Src.str(GenContext.legacy(m.denyMessage)) + ");");
                } else {
                    s.l("Procedures.run(player, " + fn + ");");
                }
                s.l("}");
            }
        }
        s.l("default -> {");
        s.l("}");
        s.l("}");
        s.l("}");
        s.blank();
        s.l("@EventHandler");
        s.l("public void onClick(InventoryClickEvent event) {");
        s.l("Inventory top = event.getView().getTopInventory();");
        s.l("if (!(top.getHolder() instanceof Holder holder)) return;");
        s.l("event.setCancelled(true);");
        s.l("if (event.getClickedInventory() != top) return;");
        s.l("if (event.getWhoClicked() instanceof Player player) click(player, holder.name, event.getSlot());");
        s.l("}");
        s.blank();
        s.l("@EventHandler");
        s.l("public void onDrag(InventoryDragEvent event) {");
        s.l("if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);");
        s.l("}");
        s.blank();
        s.l("private static void fill(Inventory inv, String id) {");
        s.l("for (int i = 0; i < inv.getSize(); i++) {");
        s.l("if (inv.getItem(i) == null) inv.setItem(i, item(id, 1, \" \", List.of(), false));");
        s.l("}");
        s.l("}");
        s.blank();
        s.l("private static ItemStack item(String id, int count, String name, List<String> lore, boolean glint) {");
        s.l("ItemStack stack = null;");
        s.l("if (id.startsWith(" + main + ".MODID + \":\")) stack = CustomItems.create(id.substring(" + main + ".MODID.length() + 1));");
        s.l("if (stack == null) {");
        s.l("Material material = Material.matchMaterial(id);");
        s.l("stack = new ItemStack(material == null ? Material.STONE : material);");
        s.l("}");
        s.l("stack.setAmount(count);");
        s.l("ItemMeta meta = stack.getItemMeta();");
        s.l("if (meta == null) return stack;");
        s.l("if (!name.isEmpty()) meta.setDisplayName(name);");
        s.l("if (!lore.isEmpty()) meta.setLore(lore);");
        if (ctx.components()) {
            s.l("if (glint) meta.setEnchantmentGlintOverride(true);");
        } else {
            s.l("if (glint) {");
            s.l("meta.addEnchant(Enchantment.DURABILITY, 1, true);");
            s.l("meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);");
            s.l("}");
        }
        s.l("stack.setItemMeta(meta);");
        s.l("return stack;");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "Menus.java", s.toString());
    }
}
