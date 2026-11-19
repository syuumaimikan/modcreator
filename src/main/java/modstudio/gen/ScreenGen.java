package modstudio.gen;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import modstudio.core.Element;
import modstudio.core.ElementType;
import modstudio.core.Ids;
import modstudio.core.McVersion;
import modstudio.core.Project;
import modstudio.core.Systems.CustomScreen;
import modstudio.core.Systems.ScreenWidget;
import modstudio.core.TextureAsset;
import modstudio.gen.JavaCommon.Loader;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom screens and HUD overlays for mod loaders. The server opens, shows and hides them with a tellraw whose
 * translation key the client mod recognises and hides (no network channel is needed, and score values travel in
 * the message). Buttons that run a procedure send a hidden command back; real time steps use the same command.
 */
public final class ScreenGen {
    private static final Pattern SCORE = Pattern.compile("\\{score:([A-Za-z0-9_.+-]+)}");
    private final GenContext ctx;

    public ScreenGen(GenContext ctx) {
        this.ctx = ctx;
    }

    public static boolean has(GenContext ctx) {
        return ctx.target.isMod() && !ctx.all(CustomScreen.class).isEmpty();
    }

    /** Whether any step of the project has this kind. */
    public static boolean usesStep(Project p, String kind) {
        Gson gson = new Gson();
        String needle = "\"kind\":\"" + kind + "\"";
        for (Element e : p.elements) if (gson.toJson(e).contains(needle)) return true;
        return false;
    }

    /** Whether the mod needs the server side command (procedure buttons or real time). */
    public static boolean needsCommand(GenContext ctx) {
        return ctx.target.isMod() && (!buttons(ctx).isEmpty() || usesStep(ctx.p, "real_time"));
    }

    public static String command(GenContext ctx) {
        return ctx.ns + "_ui";
    }

    /** Button key (screen.index) to the procedure function it runs. */
    static Map<String, String> buttons(GenContext ctx) {
        Map<String, String> r = new LinkedHashMap<>();
        for (CustomScreen s : ctx.all(CustomScreen.class)) {
            for (int i = 0; i < s.widgets.size(); i++) {
                ScreenWidget w = s.widgets.get(i);
                if (!w.kind.equals("button") || !w.action.equals("procedure")) continue;
                String proc = w.target.trim();
                if (ctx.p.byName(ElementType.PROCEDURE, proc) != null) r.put(s.name + "." + i, "procedures/" + proc);
            }
        }
        return r;
    }

    /** Objectives a screen shows: {score:x} in its texts and score:x bar values. */
    public static List<String> objectives(CustomScreen s) {
        Set<String> r = new LinkedHashSet<>();
        for (ScreenWidget w : s.widgets) {
            for (String t : List.of(w.text, w.tooltip)) {
                Matcher m = SCORE.matcher(t == null ? "" : t);
                while (m.find()) r.add(m.group(1));
            }
            for (String v : List.of(w.value, w.max)) if (v != null && v.startsWith("score:") && v.length() > 6) r.add(v.substring(6).trim());
        }
        return new ArrayList<>(r);
    }

    /** Tag on players that see an overlay, so the server keeps its values up to date. */
    public static String tag(GenContext ctx, CustomScreen s) {
        return ctx.ns + ".ui." + s.name;
    }

    /** Command that sends a screen operation (open, show, hide, update) with the screen's score values. */
    public static String message(GenContext ctx, String sel, CustomScreen s, String op) {
        JsonArray with = new JsonArray();
        with.add(J.obj("text", op));
        with.add(J.obj("text", s.name));
        for (String o : objectives(s)) {
            with.add(J.obj("text", o));
            with.add(J.obj("score", J.obj("name", "@s", "objective", o)));
        }
        String json = J.compact(J.obj("translate", ctx.ns + ".ui", "with", with));
        return sel.equals("@s") ? "tellraw @s " + json : "execute as " + sel + " run tellraw @s " + json;
    }

    public static String closeMessage(GenContext ctx, String sel) {
        JsonArray with = new JsonArray();
        with.add(J.obj("text", "close"));
        with.add(J.obj("text", ""));
        String json = J.compact(J.obj("translate", ctx.ns + ".ui", "with", with));
        return sel.equals("@s") ? "tellraw @s " + json : "execute as " + sel + " run tellraw @s " + json;
    }

    /** Function that resends the values of shown overlays; it schedules itself again. */
    public void refreshFunction(Map<String, List<String>> functions, List<String> load) {
        List<CustomScreen> overlays = ctx.all(CustomScreen.class).stream().filter(CustomScreen::overlay).toList();
        if (overlays.isEmpty() || !ctx.target.isMod()) return;
        int period = Math.max(5, overlays.stream().mapToInt(o -> Math.max(5, o.refresh)).min().orElse(20));
        String fn = ctx.ns + ":screens/_refresh";
        List<String> lines = new ArrayList<>();
        for (CustomScreen o : overlays) lines.add(message(ctx, "@a[tag=" + tag(ctx, o) + "]", o, "update"));
        lines.add("schedule function " + fn + " " + period + "t replace");
        functions.put("screens/_refresh", lines);
        load.add("schedule function " + fn + " " + period + "t replace");
    }

    // ------------------------------------------------------------------ server command

    public void serverClass() {
        Src s = new Src(ctx.pkg);
        s.imp("com.mojang.brigadier.CommandDispatcher", "com.mojang.brigadier.arguments.StringArgumentType",
                "net.minecraft.commands.CommandSourceStack", "net.minecraft.commands.Commands", "java.util.HashMap", "java.util.Map");
        s.l("/** Hidden command: buttons of custom screens run their procedure through it, and real time steps read the clock. */");
        s.l("public final class ModUiCommand {");
        s.l("private static final Map<String, String> BUTTONS = new HashMap<>();");
        s.blank();
        s.l("static {");
        for (var e : buttons(ctx).entrySet()) s.l("BUTTONS.put(" + Src.str(e.getKey()) + ", " + Src.str(e.getValue()) + ");");
        s.l("}");
        s.blank();
        s.l("private ModUiCommand() {").l("}");
        s.blank();
        s.l("public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {");
        if (ctx.permissionSet()) s.imp("net.minecraft.server.permissions.Permissions", "net.minecraft.server.permissions.PermissionSet");
        s.l("dispatcher.register(Commands.literal(" + Src.str(command(ctx)) + ")");
        s.l(".then(Commands.literal(\"click\").then(Commands.argument(\"button\", StringArgumentType.word()).executes(c -> {");
        s.l("String function = BUTTONS.get(StringArgumentType.getString(c, \"button\"));");
        s.l("if (function != null && c.getSource().getEntity() != null) ModProcedures.run(c.getSource(), function);");
        s.l("return 1;");
        s.l("})))");
        s.l(".then(Commands.literal(\"time\").requires(source -> " + ctx.hasPermissionExpr("source", 2, false) + ").then(Commands.argument(\"objective\", StringArgumentType.word()).executes(c -> {");
        s.l("time(c.getSource(), StringArgumentType.getString(c, \"objective\"));");
        s.l("return 1;");
        s.l("}))));");
        s.l("}");
        s.blank();
        s.l("/** Writes the computer's date and time into #year, #month, #day, #hour, #minute, #second, #weekday and #day_of_year. */");
        s.l("private static void time(CommandSourceStack source, String objective) {");
        s.l("java.time.LocalDateTime now = java.time.LocalDateTime.now();");
        s.l("String[] names = {\"#year\", \"#month\", \"#day\", \"#hour\", \"#minute\", \"#second\", \"#weekday\", \"#day_of_year\"};");
        s.l("int[] values = {now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond(), now.getDayOfWeek().getValue(), now.getDayOfYear()};");
        s.l("CommandSourceStack quiet = source." + ctx.withAllPermissions() + ".withSuppressedOutput();");
        s.l("for (int i = 0; i < names.length; i++) {");
        s.l("source.getServer().getCommands().performPrefixedCommand(quiet, \"scoreboard players set \" + names[i] + \" \" + objective + \" \" + values[i]);");
        s.l("}");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModUiCommand.java", s.toString());
    }

    // ------------------------------------------------------------------ client

    /** Lines for ModClient.init that register the screens' client hooks. */
    public static String initCall(Loader loader) {
        return loader == Loader.FABRIC ? "ModScreens.register();" : "ModScreens.register(modBus);";
    }

    public void clientClass(Loader loader, boolean forgeEventBus7) {
        Src s = new Src(ctx.pkg);
        boolean v1201 = !ctx.v.atLeast(McVersion.V1_20_4);
        boolean pipeline = ctx.v.atLeast(McVersion.V1_21_8);
        boolean renderType = !pipeline && ctx.registryKeys();
        boolean layers = loader == Loader.FORGE && ctx.v.atLeast(McVersion.V1_20_6);
        s.imp("net.minecraft.client.Minecraft", "net.minecraft.client.gui.Font", "net.minecraft.client.gui.GuiGraphics",
                "net.minecraft.client.gui.screens.Screen", "net.minecraft.client.gui.screens.inventory.InventoryScreen",
                "net.minecraft.client.resources.sounds.SimpleSoundInstance", "net.minecraft.network.chat.Component",
                "net.minecraft.network.chat.contents.TranslatableContents", ctx.rlImport(),
                "net.minecraft.sounds.SoundEvents", "net.minecraft.world.entity.EntityType", "net.minecraft.world.entity.LivingEntity",
                "net.minecraft.world.entity.player.Player", "net.minecraft.world.item.ItemStack", "net.minecraft.world.level.Level",
                "net.minecraft.Util", "java.io.BufferedReader", "java.io.InputStreamReader", "java.nio.charset.StandardCharsets",
                "java.time.LocalDateTime", "java.util.HashMap", "java.util.List", "java.util.Locale", "java.util.Map",
                "java.util.concurrent.ConcurrentHashMap");
        if (pipeline) s.imp("net.minecraft.client.renderer.RenderPipelines");
        else if (renderType) s.imp("net.minecraft.client.renderer.RenderType");
        else s.imp("com.mojang.blaze3d.systems.RenderSystem");
        if (ctx.registryKeys()) s.imp("net.minecraft.world.entity.EntitySpawnReason");

        if (ctx.v.atLeast(McVersion.V1_21_9)) {
            s.imp("net.minecraft.client.input.CharacterEvent", "net.minecraft.client.input.KeyEvent",
                    "net.minecraft.client.input.MouseButtonEvent");
        }
        s.l("/** Custom screens and HUD overlays, drawn on the client. The server opens them with a chat message that is hidden here. */");
        s.l("public final class ModScreens {");
        s.l("private static final String KEY = " + Src.str(ctx.ns + ".ui") + ";");
        s.l("private static final String MODID = " + Src.str(ctx.ns) + ";");
        s.l("private static final String COMMAND = " + Src.str(command(ctx)) + ";");
        s.l("private static final int PANEL = 0, IMAGE = 1, TEXT = 2, BUTTON = 3, BAR = 4, ITEM = 5, ENTITY = 6, TEXTFIELD = 7, SLIDER = 8, CHECKBOX = 9, SCROLL_LIST = 10, SLOT_DISPLAY = 11;");
        s.blank();
        s.l("/** A widget: kind, anchor (0 to 8, rows of left, middle, right), position, size and look. */");
        s.l("record W(int kind, int anchor, int x, int y, int w, int h, String text, int color, int color2, float alpha, String tex, int texW, int texH,");
        s.l("int frames, int frameTime, String item, String entity, float scale, String value, String max, String action, String target,");
        s.l("String tooltip, String anim, float speed, int align, boolean shadow, double min, double maxVal, boolean checked, String placeholder, String options) {");
        s.l("}");
        s.blank();
        s.l("record S(String name, boolean overlay, String title, boolean dim, boolean pause, W[] widgets) {");
        s.l("}");
        s.blank();
        s.l("private static final Map<String, S> SCREENS = new HashMap<>();");
        s.l("private static final Map<String, String> VALUES = new ConcurrentHashMap<>();");
        s.l("private static final Map<String, Long> OVERLAYS = new ConcurrentHashMap<>();");
        s.l("private static final Map<String, LivingEntity> ENTITIES = new HashMap<>();");
        s.l("private static final Map<String, ItemStack> STACKS = new HashMap<>();");
        s.l("private static final Map<String, " + ctx.rlType() + "> TEXTURES = new HashMap<>();");
        s.l("private static Level entityLevel;");
        s.l("private static volatile String mediaTitle = \"\";");
        s.l("private static volatile String mediaArtist = \"\";");
        s.l("private static boolean mediaStarted;");
        s.blank();
        s.l("static {");
        for (CustomScreen sc : ctx.all(CustomScreen.class)) screenSpec(s, sc);
        s.l("}");
        s.blank();
        s.l("private ModScreens() {").l("}");
        s.blank();
        registration(s, loader, forgeEventBus7, layers);
        boolean modernInput = ctx.v.atLeast(McVersion.V1_21_9);
        boolean scroll4 = ctx.v.atLeast(McVersion.V1_20_4);
        String scrollMethod = scroll4
                ? "@Override public boolean mouseScrolled(double mx, double my, double hx, double vy) { return handleScroll(mx, my, vy) || super.mouseScrolled(mx, my, hx, vy); }"
                : "@Override public boolean mouseScrolled(double mx, double my, double d) { return handleScroll(mx, my, d) || super.mouseScrolled(mx, my, d); }";
        String body = CLIENT_BODY
                .replace("%VIEW_INPUT%", (modernInput
                        ? "@Override public boolean mouseClicked(MouseButtonEvent e, boolean d) { return handleClick(e.x(), e.y(), e.button()) || super.mouseClicked(e, d); }\n"
                        + "@Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return handleDrag(e.x(), e.y(), e.button()) || super.mouseDragged(e, dx, dy); }\n"
                        + "@Override public boolean mouseReleased(MouseButtonEvent e) { draggingSlider = -1; return super.mouseReleased(e); }\n"
                        + "@Override public boolean keyPressed(KeyEvent e) { return handleKey(e.key()) || super.keyPressed(e); }\n"
                        + "@Override public boolean charTyped(CharacterEvent e) { return handleChar((char) e.codepoint()) || super.charTyped(e); }\n"
                        : "@Override public boolean mouseClicked(double mx, double my, int button) { return handleClick(mx, my, button) || super.mouseClicked(mx, my, button); }\n"
                        + "@Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { return handleDrag(mx, my, button) || super.mouseDragged(mx, my, button, dx, dy); }\n"
                        + "@Override public boolean mouseReleased(double mx, double my, int button) { draggingSlider = -1; return super.mouseReleased(mx, my, button); }\n"
                        + "@Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return handleKey(keyCode) || super.keyPressed(keyCode, scanCode, modifiers); }\n"
                        + "@Override public boolean charTyped(char codePoint, int modifiers) { return handleChar(codePoint) || super.charTyped(codePoint, modifiers); }\n") + scrollMethod)
                .replace("%RLTYPE%", ctx.rlType())
                .replace("%RL%", ctx.rlFrom(ctx.mainClass + ".MODID", "\"textures/gui/screen/\" + name + \".png\""))
                .replace("%DIM_PATH%", ctx.unobf() ? "mc.level.dimension().identifier().getPath()" : "mc.level.dimension().location().getPath()")
                .replace("%CREATE%", ctx.registryKeys() ? "type.create(mc.level, EntitySpawnReason.LOAD)" : "type.create(mc.level)")
                .replace("%ENTITY%", v1201
                        ? "InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + w.w() / 2, y + w.h() - 2, size, x + w.w() / 2.0F - lx, y + w.h() / 3.0F - ly, e);"
                        : "InventoryScreen.renderEntityInInventoryFollowsMouse(g, x, y, x + w.w(), y + w.h(), size, 0.0625F, lx, ly, e);")
                .replace("%BLIT%", pipeline
                        ? "g.blit(RenderPipelines.GUI_TEXTURED, rl, x, y, u, v, w, h, uw, vh, tw, th, argb(0xFFFFFF, a));"
                        : renderType
                        ? "g.blit(RenderType::guiTextured, rl, x, y, u, v, w, h, uw, vh, tw, th, argb(0xFFFFFF, a));"
                        : "RenderSystem.enableBlend();\ng.setColor(1.0F, 1.0F, 1.0F, a);\ng.blit(rl, x, y, w, h, u, v, uw, vh, tw, th);\ng.setColor(1.0F, 1.0F, 1.0F, 1.0F);\nRenderSystem.disableBlend();")
                .replace("%PUSH%", pipeline
                        ? "g.pose().pushMatrix();\ng.pose().translate(cx, cy);\ng.pose().scale(k, k);\ng.pose().translate(-cx, -cy);"
                        : "g.pose().pushPose();\ng.pose().translate(cx, cy, 0.0F);\ng.pose().scale(k, k, 1.0F);\ng.pose().translate(-cx, -cy, 0.0F);")
                .replace("%POP%", pipeline ? "g.pose().popMatrix();" : "g.pose().popPose();")
                .replace("%TOOLTIP%", pipeline ? "g.setTooltipForNextFrame(font, Component.literal(text), mx, my);" : "g.renderTooltip(font, Component.literal(text), mx, my);")
                .replace("%VIEW_RENDER%", v1201
                        ? "@Override\npublic void render(GuiGraphics g, int mx, int my, float partialTick) {\nif (spec.dim()) renderBackground(g);\ndraw(g, spec, width, height, mx, my, age(), true);\n}"
                        : "@Override\npublic void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {\nif (spec.dim()) renderTransparentBackground(g);\n}\n\n"
                        + "@Override\npublic void render(GuiGraphics g, int mx, int my, float partialTick) {\nsuper.render(g, mx, my, partialTick);\ndraw(g, spec, width, height, mx, my, age(), true);\n}")
                .replace("%WINDOWS_MEDIA%", Src.str(Base64.getEncoder().encodeToString(WINDOWS_MEDIA.getBytes(StandardCharsets.UTF_16LE))));
        for (String line : body.split("\n", -1)) {
            String t = line.strip();
            if (t.isEmpty()) s.blank();
            else s.l(t);
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModScreens.java", s.toString());
    }

    private void registration(Src s, Loader loader, boolean forgeEventBus7, boolean layers) {
        switch (loader) {
            case FABRIC -> {
                s.imp("net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents", "net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback");
                s.l("public static void register() {");
                s.l("ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !onMessage(message));");
                s.l("HudRenderCallback.EVENT.register((g, tick) -> renderOverlays(g));");
                s.l("}");
            }
            case NEOFORGE -> {
                s.imp("net.neoforged.bus.api.IEventBus", "net.neoforged.neoforge.common.NeoForge", "net.neoforged.neoforge.client.event.ClientChatReceivedEvent",
                        "net.neoforged.neoforge.client.event.RenderGuiEvent");
                s.l("public static void register(IEventBus modBus) {");
                s.l("NeoForge.EVENT_BUS.addListener(ModScreens::onChat);");
                s.l("NeoForge.EVENT_BUS.addListener(ModScreens::onHud);");
                s.l("}");
                s.blank();
                s.l("private static void onChat(ClientChatReceivedEvent.System event) {");
                s.l("if (onMessage(event.getMessage())) event.setCanceled(true);");
                s.l("}");
                s.blank();
                s.l("private static void onHud(RenderGuiEvent.Post event) {");
                s.l("renderOverlays(event.getGuiGraphics());");
                s.l("}");
            }
            case FORGE -> {
                s.imp("net.minecraftforge.client.event.ClientChatReceivedEvent");
                if (forgeEventBus7) {
                    s.imp("net.minecraftforge.eventbus.api.bus.BusGroup", "net.minecraftforge.client.event.AddGuiOverlayLayersEvent");
                    s.l("public static void register(BusGroup modBus) {");
                    s.l("ClientChatReceivedEvent.System.BUS.addListener((java.util.function.Predicate<ClientChatReceivedEvent.System>) ModScreens::onChat);");
                    s.l("AddGuiOverlayLayersEvent.getBus(modBus).addListener(ModScreens::onLayers);");
                    s.l("}");
                    s.blank();
                    s.l("private static boolean onChat(ClientChatReceivedEvent.System event) {");
                    s.l("return onMessage(event.getMessage());");
                    s.l("}");
                } else {
                    s.imp("net.minecraftforge.eventbus.api.IEventBus", "net.minecraftforge.common.MinecraftForge");
                    s.l("public static void register(IEventBus modBus) {");
                    s.l("MinecraftForge.EVENT_BUS.addListener(ModScreens::onChat);");
                    if (layers) s.l("modBus.addListener(ModScreens::onLayers);");
                    else s.l("MinecraftForge.EVENT_BUS.addListener(ModScreens::onHud);");
                    s.l("}");
                    s.blank();
                    s.l("private static void onChat(ClientChatReceivedEvent.System event) {");
                    s.l("if (onMessage(event.getMessage())) event.setCanceled(true);");
                    s.l("}");
                }
                s.blank();
                if (layers) {
                    s.imp("net.minecraftforge.client.event.AddGuiOverlayLayersEvent");
                    s.l("private static void onLayers(AddGuiOverlayLayersEvent event) {");
                    s.l("event.getLayeredDraw().add(" + GenContext.rlNew(ctx.singular(), ctx.mainClass + ".MODID", "\"screens\"") + ", (g, tick) -> renderOverlays(g));");
                    s.l("}");
                } else {
                    s.imp("net.minecraftforge.client.event.RenderGuiEvent");
                    s.l("private static void onHud(RenderGuiEvent.Post event) {");
                    s.l("renderOverlays(event.getGuiGraphics());");
                    s.l("}");
                }
            }
        }
        s.blank();
    }

    private void screenSpec(Src s, CustomScreen sc) {
        s.l("SCREENS.put(" + Src.str(sc.name) + ", new S(" + Src.str(sc.name) + ", " + sc.overlay() + ", " + Src.str(GenContext.legacy(sc.title)) + ", "
                + sc.dim + ", " + sc.pause + ", new W[] {");
        for (int i = 0; i < sc.widgets.size(); i++) {
            ScreenWidget w = sc.widgets.get(i);
            TextureAsset t = ctx.p.texture(w.texture);
            String tex = t == null ? "" : t.name;
            int tw = t == null ? 16 : Math.max(1, t.width), th = t == null ? 16 : Math.max(1, t.height);
            int frames = t == null ? 1 : t.frames(), ft = t == null ? 1 : Math.max(1, t.frameTime);
            String target = w.target == null ? "" : w.target.trim();
            if (w.action.equals("procedure")) target = sc.name + "." + i;
            if (w.action.equals("command") && target.startsWith("/")) target = target.substring(1);
            s.l("new W(" + kind(w.kind) + ", " + anchor(w.anchor) + ", " + w.x + ", " + w.y + ", " + Math.max(1, w.width) + ", " + Math.max(1, w.height) + ", "
                    + Src.str(GenContext.legacy(w.text)) + ", " + rgb(w.color, 0xFFFFFF) + ", " + rgb(w.color2, 0x202020) + ", "
                    + Src.f(Math.max(0, Math.min(100, w.opacity)) / 100.0) + ", " + Src.str(tex) + ", " + tw + ", " + th + ", " + frames + ", " + ft + ", "
                    + Src.str(Ids.full(w.item == null || w.item.isBlank() ? "minecraft:stone" : w.item.trim())) + ", "
                    + Src.str(w.entity == null || w.entity.isBlank() || w.entity.trim().equals("player") ? "player" : Ids.full(w.entity.trim())) + ", "
                    + Src.f(Math.max(0.1, Math.min(10, w.scale))) + ", " + Src.str(w.value == null ? "" : w.value.trim()) + ", "
                    + Src.str(w.max == null ? "" : w.max.trim()) + ", " + Src.str(w.action) + ", " + Src.str(target) + ", " + Src.str(GenContext.legacy(w.tooltip)) + ", "
                    + Src.str(w.animation) + ", " + Src.f(Math.max(0.05, Math.min(20, w.speed))) + ", " + align(w.align) + ", " + w.shadow + ", "
                    + Src.d(w.min) + ", " + Src.d(w.maxVal) + ", " + w.checked + ", " + Src.str(w.placeholder) + ", " + Src.str(w.options) + ")"
                    + (i + 1 < sc.widgets.size() ? "," : ""));
        }
        s.l("}));");
    }

    static int kind(String k) {
        return switch (k) {
            case "panel" -> 0;
            case "image" -> 1;
            case "button" -> 3;
            case "bar" -> 4;
            case "item" -> 5;
            case "entity" -> 6;
            case "textfield" -> 7;
            case "slider" -> 8;
            case "checkbox" -> 9;
            case "scroll_list" -> 10;
            case "slot_display" -> 11;
            default -> 2;
        };
    }

    public static final List<String> ANCHORS = List.of("top_left", "top", "top_right", "left", "center", "right", "bottom_left", "bottom", "bottom_right");

    static int anchor(String a) {
        int i = ANCHORS.indexOf(a);
        return i < 0 ? 4 : i;
    }

    private static int align(String a) {
        return switch (a == null ? "" : a) {
            case "center" -> 1;
            case "right" -> 2;
            default -> 0;
        };
    }

    static String rgb(String hex, int def) {
        try {
            return String.format(Locale.ROOT, "0x%06X", Integer.parseInt(hex.trim().replace("#", ""), 16) & 0xFFFFFF);
        } catch (Exception e) {
            return String.format(Locale.ROOT, "0x%06X", def);
        }
    }

    /**
     * Windows: reads the playing media from the system media controls (what the media keys control), once every two
     * seconds, and stops when the game closes.
     */
    static final String WINDOWS_MEDIA = """
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            $ErrorActionPreference = 'SilentlyContinue'
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } | Select-Object -First 1
            function Await($op, $type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); $null = $t.Wait(-1); $t.Result }
            $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
            $manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
            while ($true) {
              if ($env:MS_PARENT -and -not (Get-Process -Id $env:MS_PARENT)) { exit }
              $line = ''
              $s = $manager.GetCurrentSession()
              if ($s -and $s.GetPlaybackInfo().PlaybackStatus -eq 'Playing') {
                $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                if ($p) { $line = $p.Title + "`t" + $p.Artist }
              }
              try { [Console]::Out.WriteLine($line); [Console]::Out.Flush() } catch { exit }
              Start-Sleep -Seconds 2
            }
            """;

    /** Shared body of ModScreens; %NAME% parts are filled in per version. */
    private static final String CLIENT_BODY = """
            /** Handles the screen messages from the server; true when the message was one (it is then not shown in chat). */
            static boolean onMessage(Component message) {
            if (!(message.getContents() instanceof TranslatableContents tc) || !tc.getKey().equals(KEY)) return false;
            Object[] args = tc.getArgs();
            if (args.length < 2) return true;
            String op = arg(args[0]), name = arg(args[1]);
            for (int i = 2; i + 1 < args.length; i += 2) VALUES.put(arg(args[i]), arg(args[i + 1]));
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> handle(mc, op, name));
            return true;
            }

            private static String arg(Object o) {
            return o instanceof Component c ? c.getString() : String.valueOf(o);
            }

            private static void handle(Minecraft mc, String op, String name) {
            S s = SCREENS.get(name);
            switch (op) {
            case "open" -> {
            if (s != null && !s.overlay()) mc.setScreen(new View(s));
            }
            case "close" -> {
            if (mc.screen instanceof View) mc.setScreen(null);
            }
            case "show", "update" -> {
            if (s != null && s.overlay()) OVERLAYS.putIfAbsent(name, System.nanoTime());
            }
            case "hide" -> OVERLAYS.remove(name);
            default -> {
            }
            }
            }

            /** Draws the shown overlays over the game. */
            static void renderOverlays(GuiGraphics g) {
            if (OVERLAYS.isEmpty()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) return;
            int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
            for (Map.Entry<String, Long> e : OVERLAYS.entrySet()) {
            S s = SCREENS.get(e.getKey());
            if (s != null) draw(g, s, sw, sh, -1, -1, (System.nanoTime() - e.getValue()) / 1.0E9F, false);
            }
            }

            private static String tr(String key, String fallback) {
            net.minecraft.locale.Language lang = net.minecraft.locale.Language.getInstance();
            if (lang.has(key)) {
            return lang.getOrDefault(key, fallback).replaceAll("&([0-9a-fA-Fk-oK-OrR])", "\u00a7$1");
            }
            return fallback;
            }

            private static String trTitle(S s) { return tr("screen." + MODID + "." + s.name() + ".title", s.title()); }
            private static String trText(String sn, int idx, W w) { return tr("screen." + MODID + "." + sn + ".w." + idx + ".text", w.text()); }
            private static String trTooltip(String sn, int idx, W w) { return tr("screen." + MODID + "." + sn + ".w." + idx + ".tooltip", w.tooltip()); }
            private static String trPlaceholder(String sn, int idx, W w) { return tr("screen." + MODID + "." + sn + ".w." + idx + ".placeholder", w.placeholder()); }
            private static String trOptions(String sn, int idx, W w) { return tr("screen." + MODID + "." + sn + ".w." + idx + ".options", w.options()); }

            /** An opened custom screen. */
            static final class View extends Screen {
            private final S spec;
            private final long opened = System.nanoTime();
            private int focusedField = -1;
            private int draggingSlider = -1;

            View(S spec) {
            super(Component.literal(trTitle(spec)));
            this.spec = spec;
            for (int i = 0; i < spec.widgets().length; i++) {
            W w = spec.widgets()[i];
            if (w.kind() == TEXTFIELD) VALUES.putIfAbsent("ui.text." + spec.name() + "." + i, trText(spec.name(), i, w));
            else if (w.kind() == SLIDER) VALUES.putIfAbsent("ui.slider." + spec.name() + "." + i, number((float) ((w.min() + w.maxVal()) / 2.0)));
            else if (w.kind() == CHECKBOX) VALUES.putIfAbsent("ui.check." + spec.name() + "." + i, String.valueOf(w.checked()));
            else if (w.kind() == SCROLL_LIST) {
            VALUES.putIfAbsent("ui.scroll." + spec.name() + "." + i, "0");
            VALUES.putIfAbsent("ui.scroll_sel." + spec.name() + "." + i, "0");
            }
            }
            }

            @Override
            public boolean isPauseScreen() {
            return spec.pause();
            }

            %VIEW_RENDER%

            %VIEW_INPUT%

            private boolean handleClick(double mx, double my, int button) {
            if (button != 0) return false;
            float t = age();
            W[] ws = spec.widgets();
            focusedField = -1;
            for (int i = 0; i < ws.length; i++) {
            W w = ws[i];
            int x = left(w, width) + offX(w, t), y = top(w, height) + offY(w, t);
            if (mx < x || my < y || mx >= x + w.w() || my >= y + w.h()) continue;
            switch (w.kind()) {
            case BUTTON -> { click(w); return true; }
            case TEXTFIELD -> { focusedField = i; return true; }
            case SLIDER -> {
            draggingSlider = i;
            updateSlider(spec.name(), i, w, x, mx);
            if (!w.target().isEmpty()) click(w);
            return true;
            }
            case CHECKBOX -> {
            String key = "ui.check." + spec.name() + "." + i;
            boolean next = !"true".equals(VALUES.getOrDefault(key, String.valueOf(w.checked())));
            VALUES.put(key, String.valueOf(next));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            if (!w.target().isEmpty()) click(w);
            return true;
            }
            case SCROLL_LIST -> {
            int off = Math.round(parse(VALUES.getOrDefault("ui.scroll." + spec.name() + "." + i, "0"), 0));
            int row = off + (int) ((my - y - 2) / 14);
            String[] opts = trOptions(spec.name(), i, w).split("\\\\R");
            if (row >= 0 && row < opts.length) {
            VALUES.put("ui.scroll_sel." + spec.name() + "." + i, String.valueOf(row));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            if (!w.target().isEmpty()) click(w);
            }
            return true;
            }
            default -> { }
            }
            }
            return false;
            }

            private boolean handleDrag(double mx, double my, int button) {
            if (button != 0 || draggingSlider < 0 || draggingSlider >= spec.widgets().length) return false;
            W w = spec.widgets()[draggingSlider];
            int x = left(w, width) + offX(w, age());
            updateSlider(spec.name(), draggingSlider, w, x, mx);
            return true;
            }

            private boolean handleScroll(double mx, double my, double vy) {
            float t = age();
            W[] ws = spec.widgets();
            for (int i = 0; i < ws.length; i++) {
            W w = ws[i];
            if (w.kind() != SCROLL_LIST) continue;
            int x = left(w, width) + offX(w, t), y = top(w, height) + offY(w, t);
            if (mx >= x && my >= y && mx < x + w.w() && my < y + w.h()) {
            int off = Math.round(parse(VALUES.getOrDefault("ui.scroll." + spec.name() + "." + i, "0"), 0));
            int maxOff = Math.max(0, trOptions(spec.name(), i, w).split("\\\\R").length - Math.max(1, (w.h() - 4) / 14));
            off = Math.max(0, Math.min(maxOff, off + (vy > 0 ? -1 : 1)));
            VALUES.put("ui.scroll." + spec.name() + "." + i, String.valueOf(off));
            return true;
            }
            }
            return false;
            }

            private boolean handleKey(int keyCode) {
            if (focusedField < 0 || focusedField >= spec.widgets().length) return false;
            if (keyCode == 259) {
            String key = "ui.text." + spec.name() + "." + focusedField;
            String cur = VALUES.getOrDefault(key, "");
            if (!cur.isEmpty()) VALUES.put(key, cur.substring(0, cur.length() - 1));
            return true;
            }
            return keyCode != 256;
            }

            private boolean handleChar(char ch) {
            if (focusedField < 0 || focusedField >= spec.widgets().length || ch < 32 || ch == 127) return false;
            String key = "ui.text." + spec.name() + "." + focusedField;
            String cur = VALUES.getOrDefault(key, "");
            if (cur.length() < 128) VALUES.put(key, cur + ch);
            return true;
            }

            private static void updateSlider(String sname, int idx, W w, int x, double mx) {
            double ratio = Math.max(0.0, Math.min(1.0, (mx - x) / Math.max(1.0, w.w())));
            double val = w.min() + ratio * (w.maxVal() - w.min());
            VALUES.put("ui.slider." + sname + "." + idx, number((float) val));
            }

            private float age() {
            return (System.nanoTime() - opened) / 1.0E9F;
            }
            }

            private static void click(W w) {
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            switch (w.action()) {
            case "close" -> mc.setScreen(null);
            case "screen" -> {
            S target = SCREENS.get(w.target());
            if (target != null && !target.overlay()) mc.setScreen(new View(target));
            }
            case "procedure" -> {
            if (mc.getConnection() != null) mc.getConnection().sendCommand(COMMAND + " click " + w.target());
            }
            case "url" -> Util.getPlatform().openUri(w.target());
            case "command" -> {
            if (mc.getConnection() != null && !w.target().isEmpty()) mc.getConnection().sendCommand(w.target());
            }
            default -> {
            }
            }
            }

            static void draw(GuiGraphics g, S s, int sw, int sh, int mx, int my, float t, boolean screen) {
            Font font = Minecraft.getInstance().font;
            String tip = null;
            W[] ws = s.widgets();
            for (int i = 0; i < ws.length; i++) {
            W w = ws[i];
            float a = w.alpha() * fade(w, t);
            if (a < 0.03F) continue;
            int x = left(w, sw) + offX(w, t), y = top(w, sh) + offY(w, t);
            float k = w.scale() * grow(w, t);
            boolean over = screen && mx >= x && my >= y && mx < x + w.w() && my < y + w.h();
            boolean scaled = Math.abs(k - 1.0F) > 0.001F && w.kind() != ENTITY;
            if (scaled) push(g, x + w.w() / 2.0F, y + w.h() / 2.0F, k);
            switch (w.kind()) {
            case PANEL -> panel(g, w, x, y, a, t);
            case IMAGE -> image(g, w, x, y, w.w(), w.h(), a, t);
            case TEXT -> text(g, font, w, fill(trText(s.name(), i, w)), x, y, a);
            case BUTTON -> button(g, font, s.name(), i, w, x, y, a, t, over);
            case BAR -> bar(g, font, s.name(), i, w, x, y, a, t);
            case ITEM -> g.renderItem(stack(w.item()), x + (w.w() - 16) / 2, y + (w.h() - 16) / 2);
            case SLOT_DISPLAY -> slotDisplay(g, w, x, y, a);
            case TEXTFIELD -> textfield(g, font, s.name(), i, w, x, y, a, t);
            case SLIDER -> slider(g, font, s.name(), i, w, x, y, a);
            case CHECKBOX -> checkbox(g, font, s.name(), i, w, x, y, a);
            case SCROLL_LIST -> scrollList(g, font, s.name(), i, w, x, y, a);
            case ENTITY -> entity(g, w, x, y, mx, my, t, k);
            default -> {
            }
            }
            if (scaled) pop(g);
            String wtip = trTooltip(s.name(), i, w);
            if (over && !wtip.isEmpty()) tip = fill(wtip);
            }
            if (tip != null) tooltip(g, font, tip, mx, my);
            }

            private static int left(W w, int sw) {
            return (w.anchor() % 3) * sw / 2 + w.x();
            }

            private static int top(W w, int sh) {
            return (w.anchor() / 3) * sh / 2 + w.y();
            }

            private static float progress(W w, float t) {
            return Math.min(1.0F, Math.max(0.0F, t * w.speed() * 2.5F));
            }

            private static float ease(float p) {
            float q = 1.0F - p;
            return 1.0F - q * q * q;
            }

            private static float wave(W w, float t) {
            return (float) Math.sin(t * w.speed() * 6.2831855F);
            }

            private static float fade(W w, float t) {
            return switch (w.anim()) {
            case "fade_in" -> progress(w, t);
            case "blink" -> 0.35F + 0.65F * (0.5F + 0.5F * wave(w, t));
            case "slide_up", "slide_down", "slide_left", "slide_right", "pop" -> Math.min(1.0F, progress(w, t) * 2.0F);
            default -> 1.0F;
            };
            }

            private static int offX(W w, float t) {
            float d = (1.0F - ease(progress(w, t))) * 30.0F;
            return switch (w.anim()) {
            case "slide_left" -> Math.round(d);
            case "slide_right" -> -Math.round(d);
            default -> 0;
            };
            }

            private static int offY(W w, float t) {
            float d = (1.0F - ease(progress(w, t))) * 20.0F;
            return switch (w.anim()) {
            case "slide_up" -> Math.round(d);
            case "slide_down" -> -Math.round(d);
            case "bob" -> Math.round(wave(w, t) * 3.0F);
            default -> 0;
            };
            }

            private static float grow(W w, float t) {
            return switch (w.anim()) {
            case "pop" -> {
            float q = progress(w, t) - 1.0F;
            yield 1.0F + 2.70158F * q * q * q + 1.70158F * q * q;
            }
            case "pulse" -> 1.0F + 0.06F * wave(w, t);
            default -> 1.0F;
            };
            }

            private static int argb(int rgb, float a) {
            return (Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24) | (rgb & 0xFFFFFF);
            }

            private static int lighter(int rgb) {
            int r = Math.min(255, ((rgb >> 16) & 255) + 40), gr = Math.min(255, ((rgb >> 8) & 255) + 40), b = Math.min(255, (rgb & 255) + 40);
            return (r << 16) | (gr << 8) | b;
            }

            private static void panel(GuiGraphics g, W w, int x, int y, float a, float t) {
            if (!w.tex().isEmpty()) {
            image(g, w, x, y, w.w(), w.h(), a, t);
            return;
            }
            g.fill(x, y, x + w.w(), y + w.h(), argb(w.color2(), a));
            g.fill(x + 1, y + 1, x + w.w() - 1, y + w.h() - 1, argb(w.color(), a));
            }

            private static void text(GuiGraphics g, Font font, W w, String s, int x, int y, float a) {
            int ly = y;
            for (String line : s.split("\\n")) {
            int tw = font.width(line);
            int tx = w.align() == 1 ? x + (w.w() - tw) / 2 : w.align() == 2 ? x + w.w() - tw : x;
            g.drawString(font, line, tx, ly, argb(w.color(), a), w.shadow());
            ly += 10;
            }
            }

            private static void slotDisplay(GuiGraphics g, W w, int x, int y, float a) {
            g.fill(x, y, x + w.w(), y + w.h(), argb(0x8B8B8B, a));
            g.fill(x, y, x + w.w() - 1, y + 1, argb(0x373737, a));
            g.fill(x, y, x + 1, y + w.h() - 1, argb(0x373737, a));
            g.renderItem(stack(w.item()), x + (w.w() - 16) / 2, y + (w.h() - 16) / 2);
            }

            private static void textfield(GuiGraphics g, Font font, String sn, int idx, W w, int x, int y, float a, float t) {
            Screen cur = Minecraft.getInstance().screen;
            boolean focused = cur instanceof View v && v.focusedField == idx;
            g.fill(x, y, x + w.w(), y + w.h(), argb(focused ? 0xFFFFFF : 0xA0A0A0, a));
            g.fill(x + 1, y + 1, x + w.w() - 1, y + w.h() - 1, argb(w.color2(), a));
            String txt = VALUES.getOrDefault("ui.text." + sn + "." + idx, trText(sn, idx, w));
            String ph = trPlaceholder(sn, idx, w);
            String shown = txt.isEmpty() ? ph : txt + (focused && ((int) (t * 2.0F) % 2 == 0) ? "_" : "");
            g.drawString(font, shown, x + 4, y + (w.h() - 8) / 2, argb(txt.isEmpty() ? 0x808080 : w.color(), a), w.shadow());
            }

            private static void slider(GuiGraphics g, Font font, String sn, int idx, W w, int x, int y, float a) {
            double val = parse(VALUES.getOrDefault("ui.slider." + sn + "." + idx, number((float) ((w.min() + w.maxVal()) / 2.0))), (float) w.min());
            double span = Math.max(1e-6, w.maxVal() - w.min());
            double ratio = Math.max(0.0, Math.min(1.0, (val - w.min()) / span));
            g.fill(x, y, x + w.w(), y + w.h(), argb(0x000000, a));
            g.fill(x + 1, y + 1, x + w.w() - 1, y + w.h() - 1, argb(w.color2(), a));
            int kx = x + 1 + (int) Math.round((w.w() - 10) * ratio);
            g.fill(kx, y + 1, kx + 8, y + w.h() - 1, argb(0xCCCCCC, a));
            String stxt = trText(sn, idx, w);
            String lbl = (stxt.isEmpty() ? "" : stxt + ": ") + number((float) val);
            g.drawString(font, lbl, x + (w.w() - font.width(lbl)) / 2, y + (w.h() - 8) / 2, argb(w.color(), a), w.shadow());
            }

            private static void checkbox(GuiGraphics g, Font font, String sn, int idx, W w, int x, int y, float a) {
            boolean ch = "true".equals(VALUES.getOrDefault("ui.check." + sn + "." + idx, String.valueOf(w.checked())));
            int b = Math.min(16, Math.max(10, w.h() - 2));
            int by = y + (w.h() - b) / 2;
            g.fill(x, by, x + b, by + b, argb(0xFFFFFF, a));
            g.fill(x + 1, by + 1, x + b - 1, by + b - 1, argb(0x202020, a));
            if (ch) g.drawString(font, "x", x + b / 2 - 2, y + (w.h() - 8) / 2, argb(0x55FF55, a), true);
            g.drawString(font, fill(trText(sn, idx, w)), x + b + 5, y + (w.h() - 8) / 2, argb(w.color(), a), w.shadow());
            }

            private static void scrollList(GuiGraphics g, Font font, String sn, int idx, W w, int x, int y, float a) {
            g.fill(x, y, x + w.w(), y + w.h(), argb(w.color2(), a));
            String[] opts = trOptions(sn, idx, w).split("\\\\R");
            int off = Math.max(0, Math.round(parse(VALUES.getOrDefault("ui.scroll." + sn + "." + idx, "0"), 0)));
            int sel = Math.round(parse(VALUES.getOrDefault("ui.scroll_sel." + sn + "." + idx, "0"), 0));
            for (int r = off, py = 4; r < opts.length && py + 10 <= w.h(); r++, py += 14) {
            if (r == sel) g.fill(x + 2, y + py - 2, x + w.w() - 6, y + py + 10, argb(0x4A76A8, a * 0.6F));
            g.drawString(font, opts[r].trim(), x + 5, y + py, argb(w.color(), a), w.shadow());
            }
            }

            private static void button(GuiGraphics g, Font font, String sn, int idx, W w, int x, int y, float a, float t, boolean over) {
            if (!w.tex().isEmpty()) {
            image(g, w, x, y, w.w(), w.h(), over ? a : a * 0.85F, t);
            } else {
            g.fill(x, y, x + w.w(), y + w.h(), argb(over ? 0xFFFFFF : 0x000000, a));
            g.fill(x + 1, y + 1, x + w.w() - 1, y + w.h() - 1, argb(over ? lighter(w.color2()) : w.color2(), a));
            }
            String label = fill(trText(sn, idx, w));
            g.drawString(font, label, x + (w.w() - font.width(label)) / 2, y + (w.h() - 8) / 2, argb(w.color(), a), w.shadow());
            }

            private static void bar(GuiGraphics g, Font font, String sn, int idx, W w, int x, int y, float a, float t) {
            float[] vm = values(w);
            float ratio = vm[1] <= 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, vm[0] / vm[1]));
            int fw = Math.round((w.w() - 2) * ratio);
            g.fill(x, y, x + w.w(), y + w.h(), argb(w.color2(), a));
            if (fw > 0) {
            if (!w.tex().isEmpty()) {
            int fh = w.texH() / Math.max(1, w.frames());
            int frame = w.frames() > 1 ? (int) (t * 20.0F / w.frameTime()) % w.frames() : 0;
            blit(g, texture(w.tex()), x + 1, y + 1, fw, w.h() - 2, 0.0F, frame * fh, Math.max(1, Math.round(w.texW() * ratio)), fh, w.texW(), w.texH(), a);
            } else {
            g.fill(x + 1, y + 1, x + 1 + fw, y + w.h() - 1, argb(w.color(), a));
            }
            }
            String btxt = trText(sn, idx, w);
            if (!btxt.isEmpty()) {
            String label = fill(btxt).replace("{value}", number(vm[0])).replace("{max}", number(vm[1]));
            g.drawString(font, label, x + (w.w() - font.width(label)) / 2, y + (w.h() - 8) / 2, argb(0xFFFFFF, a), true);
            }
            }

            private static String number(float f) {
            return f == Math.round(f) ? String.valueOf(Math.round(f)) : String.format(Locale.ROOT, "%.1f", f);
            }

            private static float parse(String s, float def) {
            try {
            return Float.parseFloat(s.trim());
            } catch (RuntimeException e) {
            return def;
            }
            }

            private static float[] values(W w) {
            Player p = Minecraft.getInstance().player;
            float v = 0.0F, m = 0.0F;
            if (p != null) {
            switch (w.value()) {
            case "health" -> {
            v = p.getHealth();
            m = p.getMaxHealth();
            }
            case "food" -> {
            v = p.getFoodData().getFoodLevel();
            m = 20.0F;
            }
            case "xp" -> {
            v = p.experienceProgress;
            m = 1.0F;
            }
            case "level" -> {
            v = p.experienceLevel;
            m = 100.0F;
            }
            case "air" -> {
            v = Math.max(0, p.getAirSupply());
            m = p.getMaxAirSupply();
            }
            case "armor" -> {
            v = p.getArmorValue();
            m = 20.0F;
            }
            default -> {
            if (w.value().startsWith("score:")) {
            v = parse(VALUES.getOrDefault(w.value().substring(6), "0"), 0.0F);
            m = 100.0F;
            }
            }
            }
            }
            if (!w.max().isEmpty()) m = w.max().startsWith("score:") ? parse(VALUES.getOrDefault(w.max().substring(6), "0"), m) : parse(w.max(), m);
            return new float[] {v, m};
            }

            private static %RLTYPE% texture(String name) {
            return TEXTURES.computeIfAbsent(name, n -> rl(n));
            }

            private static %RLTYPE% rl(String name) {
            return %RL%;
            }

            private static void image(GuiGraphics g, W w, int x, int y, int dw, int dh, float a, float t) {
            int fh = w.texH() / Math.max(1, w.frames());
            int frame = w.frames() > 1 ? (int) (t * 20.0F / w.frameTime()) % w.frames() : 0;
            blit(g, texture(w.tex()), x, y, dw, dh, 0.0F, frame * fh, w.texW(), fh, w.texW(), w.texH(), a);
            }

            private static void blit(GuiGraphics g, %RLTYPE% rl, int x, int y, int w, int h, float u, float v, int uw, int vh, int tw, int th, float a) {
            %BLIT%
            }

            private static void push(GuiGraphics g, float cx, float cy, float k) {
            %PUSH%
            }

            private static void pop(GuiGraphics g) {
            %POP%
            }

            private static void tooltip(GuiGraphics g, Font font, String text, int mx, int my) {
            %TOOLTIP%
            }

            private static ItemStack stack(String id) {
            return STACKS.computeIfAbsent(id, i -> new ItemStack(ModProcedures.item(i)));
            }

            private static LivingEntity living(String id) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return null;
            if (id.equals("player")) return mc.player;
            if (mc.level != entityLevel) {
            ENTITIES.clear();
            entityLevel = mc.level;
            }
            LivingEntity e = ENTITIES.get(id);
            if (e == null) {
            e = EntityType.byString(id).map(type -> %CREATE%).filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).orElse(null);
            if (e != null) ENTITIES.put(id, e);
            }
            return e;
            }

            private static void entity(GuiGraphics g, W w, int x, int y, int mx, int my, float t, float k) {
            LivingEntity e = living(w.entity());
            if (e == null) return;
            int size = Math.max(4, Math.round(w.h() * 0.85F / Math.max(0.4F, e.getBbHeight()) * k));
            float lx = mx, ly = my;
            if (mx < 0) {
            lx = x + w.w() / 2.0F;
            ly = y + w.h() / 3.0F;
            }
            if (w.anim().equals("look_around")) {
            lx = x + w.w() / 2.0F + (float) Math.sin(t * w.speed() * 1.5F) * 80.0F;
            ly = y + w.h() / 3.0F;
            }
            %ENTITY%
            }

            /** Fills in {score:objective} and the client values: player, health, food, level, x, y, z, fps, time, date, game_time, media and more. */
            static String fill(String s) {
            if (s.indexOf('{') < 0) return s;
            Minecraft mc = Minecraft.getInstance();
            StringBuilder b = new StringBuilder();
            int i = 0;
            while (i < s.length()) {
            int open = s.indexOf('{', i);
            int close = open < 0 ? -1 : s.indexOf('}', open);
            if (open < 0 || close < 0) {
            b.append(s, i, s.length());
            break;
            }
            b.append(s, i, open);
            String v = value(mc, mc.player, s.substring(open + 1, close));
            b.append(v != null ? v : s.substring(open, close + 1));
            i = close + 1;
            }
            return b.toString();
            }

            private static String value(Minecraft mc, Player p, String key) {
            if (key.startsWith("score:")) return VALUES.getOrDefault(key.substring(6), "0");
            LocalDateTime now = LocalDateTime.now();
            switch (key) {
            case "time":
            return String.format(Locale.ROOT, "%02d:%02d", now.getHour(), now.getMinute());
            case "date":
            return now.toLocalDate().toString();
            case "fps":
            return String.valueOf(mc.getFps());
            case "os":
            return System.getProperty("os.name", "");
            case "java":
            return System.getProperty("java.version", "");
            case "memory":
            Runtime r = Runtime.getRuntime();
            return ((r.totalMemory() - r.freeMemory()) >> 20) + " / " + (r.maxMemory() >> 20) + " MB";
            case "media_title":
            startMedia();
            return mediaTitle;
            case "media_artist":
            startMedia();
            return mediaArtist;
            case "media":
            startMedia();
            return mediaArtist.isEmpty() ? mediaTitle : mediaArtist + " - " + mediaTitle;
            default:
            break;
            }
            if (p == null || mc.level == null) return null;
            return switch (key) {
            case "player" -> p.getName().getString();
            case "health" -> String.valueOf(Math.round(p.getHealth()));
            case "max_health" -> String.valueOf(Math.round(p.getMaxHealth()));
            case "food" -> String.valueOf(p.getFoodData().getFoodLevel());
            case "level" -> String.valueOf(p.experienceLevel);
            case "armor" -> String.valueOf(p.getArmorValue());
            case "x" -> String.valueOf(p.getBlockX());
            case "y" -> String.valueOf(p.getBlockY());
            case "z" -> String.valueOf(p.getBlockZ());
            case "dimension" -> %DIM_PATH%;
            case "day" -> String.valueOf(mc.level.getDayTime() / 24000L);
            case "game_time" -> {
            long d = (mc.level.getDayTime() + 6000L) % 24000L;
            yield String.format(Locale.ROOT, "%02d:%02d", d / 1000L, d % 1000L * 60L / 1000L);
            }
            default -> null;
            };
            }

            /** Starts reading the playing media of the computer (only when a screen shows it; nothing leaves the computer). */
            private static synchronized void startMedia() {
            if (mediaStarted) return;
            mediaStarted = true;
            Thread thread = new Thread(ModScreens::mediaLoop, COMMAND + "-media");
            thread.setDaemon(true);
            thread.start();
            }

            private static void mediaLoop() {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            try {
            if (os.contains("win")) {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", WINDOWS_MEDIA);
            pb.environment().put("MS_PARENT", String.valueOf(ProcessHandle.current().pid()));
            Process process = pb.redirectErrorStream(true).start();
            Runtime.getRuntime().addShutdownHook(new Thread(process::destroy));
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) media(line);
            }
            } else {
            List<String> command = os.contains("mac")
            ? List.of("osascript", "-e", "if application \\"Spotify\\" is running then", "-e", "tell application \\"Spotify\\" to if player state is playing then return (name of current track) & tab & (artist of current track)", "-e", "end if", "-e", "if application \\"Music\\" is running then", "-e", "tell application \\"Music\\" to if player state is playing then return (name of current track) & tab & (artist of current track)", "-e", "end if", "-e", "return \\"\\"")
            : List.of("playerctl", "metadata", "--format", "{{title}}\\t{{artist}}");
            while (true) {
            Process process = new ProcessBuilder(command).start();
            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            line = reader.readLine();
            }
            process.waitFor();
            media(line == null || process.exitValue() != 0 ? "" : line);
            Thread.sleep(3000L);
            }
            }
            } catch (Exception e) {
            media("");
            }
            }

            private static void media(String line) {
            String[] parts = line.split("\\t", -1);
            mediaTitle = parts.length > 0 ? parts[0].trim() : "";
            mediaArtist = parts.length > 1 ? parts[1].trim() : "";
            }

            private static final String WINDOWS_MEDIA = %WINDOWS_MEDIA%;
            """;
}
