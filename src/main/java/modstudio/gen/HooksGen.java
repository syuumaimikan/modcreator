package modstudio.gen;

import modstudio.core.Elements;
import modstudio.core.Elements.LootTable;
import modstudio.core.Elements.Recipe;
import modstudio.core.Ids;
import modstudio.core.McVersion;
import modstudio.core.Systems.Cutscene;
import modstudio.core.Systems.KeyBind;
import modstudio.gen.JavaCommon.Loader;

import java.util.ArrayList;
import java.util.List;

/**
 * Mod code for what data packs cannot do: adding the project's loot to existing loot tables, brewing stand recipes
 * and key bindings. Every loader and version has its own events for these, so this class keeps them together.
 */
final class HooksGen {
    private final GenContext ctx;
    private final JavaCommon jc;
    private final Loader loader;

    HooksGen(GenContext ctx, JavaCommon jc, Loader loader) {
        this.ctx = ctx;
        this.jc = jc;
        this.loader = loader;
    }

    private boolean neo() {
        return loader == Loader.NEOFORGE;
    }

    private boolean fabric() {
        return loader == Loader.FABRIC;
    }

    /** Writes the helper classes this project needs. */
    void classes(boolean forgeEventBus7) {
        if (hasLoot()) lootClass();
        if (hasBrewing()) brewingClass();
        if (hasClient()) clientClass(forgeEventBus7);
        if (ScreenGen.has(ctx)) new ScreenGen(ctx).clientClass(loader, forgeEventBus7);
    }

    // ------------------------------------------------------------------ loot tables

    /** Loot tables whose rolls are added to an existing table instead of replacing it. */
    static List<LootTable> injects(GenContext ctx) {
        List<LootTable> r = new ArrayList<>();
        for (LootTable lt : ctx.all(LootTable.class)) if (lt.inject && !lt.replaces.isBlank()) r.add(lt);
        return r;
    }

    /** Id of the project's own table that is added to the existing one. */
    static String injectId(String ns, LootTable lt) {
        return ns + ":inject/" + lt.name;
    }

    boolean hasLoot() {
        return !injects(ctx).isEmpty();
    }

    private void lootClass() {
        Src s = new Src(ctx.pkg);
        s.imp("net.minecraft.world.level.storage.loot.LootPool", "java.util.ArrayList", "java.util.HashMap", "java.util.List", "java.util.Map");
        if (ctx.components()) {
            s.imp("net.minecraft.world.level.storage.loot.entries.NestedLootTable", "net.minecraft.core.registries.Registries",
                    "net.minecraft.resources.ResourceKey", ctx.rlImport());
        } else {
            s.imp("net.minecraft.world.level.storage.loot.entries.LootTableReference", ctx.rlImport());
        }
        s.l("/** Project loot tables added to existing ones: existing table id -> project tables rolled with it. */");
        s.l("public final class ModLoot {");
        s.l("private static final Map<String, List<String>> INJECTS = new HashMap<>();");
        s.blank();
        s.l("static {");
        for (LootTable lt : injects(ctx)) s.l("add(" + Src.str(Ids.full(lt.replaces.trim())) + ", " + Src.str(injectId(ctx.ns, lt)) + ");");
        s.l("}");
        s.blank();
        s.l("private ModLoot() {").l("}");
        s.blank();
        s.l("private static void add(String target, String table) {");
        s.l("INJECTS.computeIfAbsent(target, k -> new ArrayList<>()).add(table);");
        s.l("}");
        s.blank();
        s.l("public static List<String> injected(String target) {");
        s.l("return INJECTS.getOrDefault(target, List.of());");
        s.l("}");
        s.blank();
        s.l("/** A pool that rolls the given table once. */");
        s.l("public static LootPool.Builder pool(String table) {");
        if (ctx.components()) {
            s.l("return LootPool.lootPool().add(NestedLootTable.lootTableReference(ResourceKey.create(Registries.LOOT_TABLE, "
                    + ctx.rlParseExpr("table") + ")));");
        } else {
            s.l("return LootPool.lootPool().add(LootTableReference.lootTableReference(" + ctx.rlParseExpr("table") + "));");
        }
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModLoot.java", s.toString());
    }

    /** Fabric: lines for ModEvents.init(). */
    void fabricInit(Src s) {
        if (hasLoot()) {
            if (ctx.v.atLeast(McVersion.V1_21_1)) {
                s.imp("net.fabricmc.fabric.api.loot.v3.LootTableEvents");
                s.l("LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {");
                s.l("for (String table : ModLoot.injected(key.location().toString())) tableBuilder.withPool(ModLoot.pool(table));");
            } else if (ctx.components()) {
                s.imp("net.fabricmc.fabric.api.loot.v2.LootTableEvents");
                s.l("LootTableEvents.MODIFY.register((key, tableBuilder, source) -> {");
                s.l("for (String table : ModLoot.injected(key.location().toString())) tableBuilder.withPool(ModLoot.pool(table));");
            } else {
                s.imp("net.fabricmc.fabric.api.loot.v2.LootTableEvents");
                s.l("LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {");
                s.l("for (String table : ModLoot.injected(id.toString())) tableBuilder.withPool(ModLoot.pool(table));");
            }
            s.l("});");
        }
        if (hasBrewing()) {
            if (ctx.unobf()) {
                s.imp("net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder");
                s.l("FabricPotionBrewingBuilder.BUILD.register(ModBrewing::register);");
            } else if (ctx.components()) {
                s.imp("net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder");
                s.l("FabricBrewingRecipeRegistryBuilder.BUILD.register(ModBrewing::register);");
            } else {
                s.l("ModBrewing.register();");
            }
        }
    }

    /** Forge and NeoForge: @SubscribeEvent methods for ModEvents (registered on the game bus). */
    void forgeEvents(Src s, String evPkg) {
        if (hasLoot()) {
            s.imp(evPkg + ".LootTableLoadEvent");
            s.blank().l("@SubscribeEvent").l("public static void onLootTableLoad(LootTableLoadEvent event) {");
            s.l("for (String table : ModLoot.injected(event.getName().toString())) event.getTable().addPool(ModLoot.pool(table).name(table).build());");
            s.l("}");
        }
        if (hasBrewing() && ctx.components()) {
            String ev = neo() ? "RegisterBrewingRecipesEvent" : "BrewingRecipeRegisterEvent";
            s.imp(evPkg + ".brewing." + ev);
            s.blank().l("@SubscribeEvent").l("public static void onBrewingRecipes(" + ev + " event) {");
            s.l("ModBrewing.register(event.getBuilder());");
            s.l("}");
        }
    }

    /** Forge and NeoForge: lines for the mod constructor, where the mod bus is called modBus. */
    void forgeConstructor(Src s) {
        if (hasBrewing() && !ctx.components()) s.l("modBus.addListener(ModBrewing::setup);");
        if (hasClient()) {
            if (neo()) s.imp("net.neoforged.fml.loading.FMLEnvironment", "net.neoforged.api.distmarker.Dist");
            else s.imp("net.minecraftforge.fml.loading.FMLEnvironment", "net.minecraftforge.api.distmarker.Dist");
            String distExpr = (neo() && ctx.v.atLeast(McVersion.V1_21_9)) ? "FMLEnvironment.getDist()" : "FMLEnvironment.dist";
            s.l("if (" + distExpr + " == Dist.CLIENT) ModClient.init(modBus);");
        }
    }

    // ------------------------------------------------------------------ brewing

    static List<Recipe> brewing(GenContext ctx) {
        List<Recipe> r = new ArrayList<>();
        for (Recipe rc : ctx.all(Recipe.class)) {
            if (rc.recipeType.equals("brewing") && !rc.input.isBlank() && !rc.addition.isBlank() && !rc.result.isBlank()) r.add(rc);
        }
        return r;
    }

    private List<Recipe> usableBrewing() {
        List<Recipe> r = new ArrayList<>();
        for (Recipe rc : brewing(ctx)) {
            if (potion(rc.input) != null && potion(rc.result) != null && !Ids.isTag(Ids.full(rc.addition))) r.add(rc);
            else ctx.note("brewing recipe " + rc.name + " skipped: unknown potion or a tag as ingredient");
        }
        return r;
    }

    boolean hasBrewing() {
        return !brewing(ctx).isEmpty();
    }

    /** Project potion for an id in this namespace, or null. */
    static String projectPotion(GenContext ctx, String id) {
        String f = Ids.full(id.trim());
        if (!Ids.ns(f).equals(ctx.ns)) return null;
        String n = Ids.path(f);
        for (Elements.StatusEffect se : ctx.all(Elements.StatusEffect.class)) if (se.potion && se.name.equals(n)) return n;
        for (Elements.Potion po : ctx.all(Elements.Potion.class)) if (po.name.equals(n)) return n;
        return null;
    }

    /** Potion expression: a Holder from 1.20.5 and a Potion before. Null for potions that cannot be referenced. */
    private String potion(String id) {
        String f = Ids.full(id.trim());
        if (Ids.ns(f).equals("minecraft")) return "Potions." + Ids.toConst(Ids.path(f));
        String n = projectPotion(ctx, f);
        if (n == null) return null;
        String c = "ModPotions." + Ids.toConst(n);
        if (!ctx.components()) return fabric() ? c : c + ".get()";
        return switch (loader) {
            case FABRIC -> "BuiltInRegistries.POTION.wrapAsHolder(" + c + ")";
            case NEOFORGE -> c;
            case FORGE -> c + ".getHolder().orElseThrow()";
        };
    }

    private void brewingClass() {
        Src s = new Src(ctx.pkg);
        s.imp("net.minecraft.world.item.alchemy.Potions");
        s.l("/** Brewing stand recipes: base potion + ingredient -> potion, for drinkable, splash and lingering bottles. */");
        s.l("public final class ModBrewing {");
        s.l("private ModBrewing() {").l("}");
        s.blank();
        List<Recipe> list = usableBrewing();
        if (ctx.components()) {
            s.imp("net.minecraft.world.item.alchemy.PotionBrewing");
            if (fabric()) s.imp("net.minecraft.core.registries.BuiltInRegistries");
            s.l("public static void register(PotionBrewing.Builder builder) {");
            for (Recipe r : list) s.l("builder.addMix(" + potion(r.input) + ", " + jc.anyItemRef(r.addition) + ", " + potion(r.result) + ");");
            s.l("}");
        } else if (fabric()) {
            s.imp("net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistry", "net.minecraft.world.item.crafting.Ingredient");
            s.l("public static void register() {");
            for (Recipe r : list) {
                s.l("FabricBrewingRecipeRegistry.registerPotionRecipe(" + potion(r.input) + ", Ingredient.of(" + jc.anyItemRef(r.addition) + "), " + potion(r.result) + ");");
            }
            s.l("}");
        } else {
            // before 1.20.5 Forge and NeoForge add recipes through their own registry
            String base = neo() ? "net.neoforged.neoforge.common.brewing." : "net.minecraftforge.common.brewing.";
            s.imp(base + "BrewingRecipeRegistry", base + "IBrewingRecipe",
                    (neo() ? "net.neoforged.fml.event.lifecycle." : "net.minecraftforge.fml.event.lifecycle.") + "FMLCommonSetupEvent",
                    "net.minecraft.world.item.Item", "net.minecraft.world.item.ItemStack", "net.minecraft.world.item.Items",
                    "net.minecraft.world.item.alchemy.Potion", "net.minecraft.world.item.alchemy.PotionUtils", "java.util.function.Supplier");
            s.l("public static void setup(FMLCommonSetupEvent event) {");
            s.l("event.enqueueWork(ModBrewing::register);");
            s.l("}");
            s.blank();
            s.l("private static void register() {");
            for (Recipe r : list) {
                s.l("BrewingRecipeRegistry.addRecipe(new Mix(() -> " + potion(r.input) + ", () -> " + jc.anyItemRef(r.addition) + ", () -> " + potion(r.result) + "));");
            }
            s.l("}");
            s.blank();
            s.l("private record Mix(Supplier<Potion> from, Supplier<Item> ingredient, Supplier<Potion> to) implements IBrewingRecipe {");
            s.l("@Override");
            s.l("public boolean isInput(ItemStack input) {");
            s.l("return (input.is(Items.POTION) || input.is(Items.SPLASH_POTION) || input.is(Items.LINGERING_POTION)) && PotionUtils.getPotion(input) == from.get();");
            s.l("}");
            s.blank().l("@Override");
            s.l("public boolean isIngredient(ItemStack stack) {");
            s.l("return stack.is(ingredient.get());");
            s.l("}");
            s.blank().l("@Override");
            s.l("public ItemStack getOutput(ItemStack input, ItemStack stack) {");
            s.l("if (!isInput(input) || !isIngredient(stack)) return ItemStack.EMPTY;");
            s.l("return PotionUtils.setPotion(new ItemStack(input.getItem()), to.get());");
            s.l("}");
            s.l("}");
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModBrewing.java", s.toString());
    }

    // ------------------------------------------------------------------ key bindings

    /** Cutscene cameras follow the exact path on mod clients; display entities interpolate their moves from 1.20.2. */
    boolean hasCutscenes() {
        return ctx.v.atLeast(McVersion.V1_20_4) && !ctx.all(Cutscene.class).isEmpty();
    }

    boolean hasFluidExtensions() {
        return neo() && ctx.singular() && !ctx.all(Elements.Fluid.class).isEmpty();
    }

    /** Whether the mod has client-only code (ModClient). */
    boolean hasClient() {
        return hasKeys() || hasCutscenes() || !tooltipColorItems().isEmpty() || !screenButtons().isEmpty() || ScreenGen.has(ctx) || hasFluidExtensions();
    }

    /** Buttons that UI themes add to the title and pause screens. */
    List<modstudio.core.Systems.ScreenButton> screenButtons() {
        List<modstudio.core.Systems.ScreenButton> r = new ArrayList<>();
        for (modstudio.core.Systems.Theme th : ctx.all(modstudio.core.Systems.Theme.class)) {
            for (modstudio.core.Systems.ScreenButton b : th.buttons) if (!b.label.isBlank() && !b.value.isBlank()) r.add(b);
        }
        return r;
    }

    /** Items whose tooltip colours come from the loader's tooltip event (Forge and NeoForge before tooltip_style). */
    List<Elements.Item> tooltipColorItems() {
        if (fabric() || ctx.v.atLeast(McVersion.V1_21_4)) return List.of();
        return ctx.all(Elements.Item.class).stream().filter(it -> it.customTooltip).toList();
    }

    boolean hasKeys() {
        return !ctx.all(KeyBind.class).isEmpty();
    }

    static String keyName(String ns, KeyBind k) {
        return "key." + ns + "." + k.name;
    }

    static String keyCategory(String ns, KeyBind k) {
        return k.category.equals("mod") || k.category.isBlank() ? "key.categories." + ns : "key.categories." + k.category;
    }

    /**
     * Client-only class: the key mappings with the tick that sends their trigger command, and the cutscene camera,
     * whose position and angles are set from the exact path every tick.
     */
    private void clientClass(boolean forgeEventBus7) {
        Src s = new Src(ctx.pkg);
        s.imp("net.minecraft.client.Minecraft");
        List<KeyBind> keys = hasKeys() ? ctx.all(KeyBind.class) : List.of();
        List<Cutscene> scenes = hasCutscenes() ? ctx.all(Cutscene.class) : List.of();
        s.l("/** Client-only hooks: key bindings (they send /trigger " + KeyGen.objective(ctx.ns) + " set <number>) and the cutscene camera. */");
        s.l("public final class ModClient {");
        if (!keys.isEmpty()) s.imp("com.mojang.blaze3d.platform.InputConstants", "net.minecraft.client.KeyMapping");
        for (KeyBind k : keys) {
            String key = k.key == null || k.key.isBlank() ? "key.keyboard.unknown" : k.key.trim();
            String type = key.startsWith("key.mouse.") ? "MOUSE" : "KEYSYM";
            s.l("public static final KeyMapping " + Ids.toConst(k.name) + " = new KeyMapping(" + Src.str(keyName(ctx.ns, k)) + ", InputConstants.Type." + type
                    + ", InputConstants.getKey(" + Src.str(key) + ").getValue(), " + Src.str(keyCategory(ctx.ns, k)) + ");");
        }
        if (!scenes.isEmpty()) cutsceneFields(s, scenes);
        s.blank();
        s.l("private ModClient() {").l("}");
        s.blank();
        switch (loader) {
            case FABRIC -> {
                s.imp("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents");
                s.l("public static void init() {");
                if (!keys.isEmpty()) s.imp("net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper");
                for (KeyBind k : keys) s.l("KeyBindingHelper.registerKeyBinding(" + Ids.toConst(k.name) + ");");
                s.l("ClientTickEvents.END_CLIENT_TICK.register(client -> tick());");
                if (!screenButtons().isEmpty()) {
                    s.imp("net.fabricmc.fabric.api.client.screen.v1.ScreenEvents", "net.fabricmc.fabric.api.client.screen.v1.Screens");
                    s.l("ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> addButtons(screen, Screens.getButtons(screen)::add));");
                }
                if (ScreenGen.has(ctx)) s.l(ScreenGen.initCall(loader));
                s.l("}");
            }
            case NEOFORGE -> {
                s.imp("net.neoforged.bus.api.IEventBus", "net.neoforged.neoforge.common.NeoForge");
                s.l("public static void init(IEventBus modBus) {");
                if (!keys.isEmpty()) {
                    s.imp("net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent");
                    s.l("modBus.addListener(ModClient::register);");
                }
                s.l("NeoForge.EVENT_BUS.addListener(ModClient::onTick);");
                if (!tooltipColorItems().isEmpty()) {
                    s.imp("net.neoforged.neoforge.client.event.RenderTooltipEvent");
                    s.l("NeoForge.EVENT_BUS.addListener(ModClient::onTooltipColor);");
                }
                if (!screenButtons().isEmpty()) {
                    s.imp("net.neoforged.neoforge.client.event.ScreenEvent");
                    s.l("NeoForge.EVENT_BUS.addListener(ModClient::onScreenInit);");
                }
                if (ScreenGen.has(ctx)) s.l(ScreenGen.initCall(loader));
                if (hasFluidExtensions()) {
                    s.imp("net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent");
                    s.l("modBus.addListener(ModClient::registerFluidExtensions);");
                }
                s.l("}");
                s.blank();
                if (ctx.components()) {
                    s.imp("net.neoforged.neoforge.client.event.ClientTickEvent");
                    s.l("private static void onTick(ClientTickEvent.Post event) {").l("tick();").l("}");
                } else {
                    s.imp("net.neoforged.neoforge.event.TickEvent");
                    s.l("private static void onTick(TickEvent.ClientTickEvent event) {").l("if (event.phase == TickEvent.Phase.END) tick();").l("}");
                }
            }
            case FORGE -> {
                s.imp("net.minecraftforge.event.TickEvent");
                if (!keys.isEmpty()) s.imp("net.minecraftforge.client.event.RegisterKeyMappingsEvent");
                if (forgeEventBus7) {
                    s.imp("net.minecraftforge.eventbus.api.bus.BusGroup");
                    s.l("public static void init(BusGroup modBus) {");
                    if (!keys.isEmpty()) s.l("RegisterKeyMappingsEvent.getBus(modBus).addListener(ModClient::register);");
                    s.l("TickEvent.ClientTickEvent.Post.BUS.addListener(ModClient::onTick);");
                    if (!screenButtons().isEmpty()) {
                        s.imp("net.minecraftforge.client.event.ScreenEvent");
                        s.l("ScreenEvent.Init.Post.BUS.addListener(ModClient::onScreenInit);");
                    }
                } else {
                    s.imp("net.minecraftforge.eventbus.api.IEventBus", "net.minecraftforge.common.MinecraftForge");
                    s.l("public static void init(IEventBus modBus) {");
                    if (!keys.isEmpty()) s.l("modBus.addListener(ModClient::register);");
                    s.l("MinecraftForge.EVENT_BUS.addListener(ModClient::onTick);");
                    if (!tooltipColorItems().isEmpty()) {
                        s.imp("net.minecraftforge.client.event.RenderTooltipEvent");
                        s.l("MinecraftForge.EVENT_BUS.addListener(ModClient::onTooltipColor);");
                    }
                    if (!screenButtons().isEmpty()) {
                        s.imp("net.minecraftforge.client.event.ScreenEvent");
                        s.l("MinecraftForge.EVENT_BUS.addListener(ModClient::onScreenInit);");
                    }
                }
                if (ScreenGen.has(ctx)) s.l(ScreenGen.initCall(loader));
                s.l("}");
                s.blank();
                if (ctx.v.atLeast(McVersion.V1_20_4)) {
                    s.l("private static void onTick(TickEvent.ClientTickEvent.Post event) {").l("tick();").l("}");
                } else {
                    s.l("private static void onTick(TickEvent.ClientTickEvent event) {").l("if (event.phase == TickEvent.Phase.END) tick();").l("}");
                }
            }
        }
        if (!fabric() && !keys.isEmpty()) {
            s.blank();
            s.l("private static void register(RegisterKeyMappingsEvent event) {");
            for (KeyBind k : keys) s.l("event.register(" + Ids.toConst(k.name) + ");");
            s.l("}");
        }
        s.blank();
        s.l("private static void tick() {");
        s.l("Minecraft mc = Minecraft.getInstance();");
        for (int i = 0; i < keys.size(); i++) s.l("while (" + Ids.toConst(keys.get(i).name) + ".consumeClick()) press(mc, " + (i + 1) + ");");
        if (!scenes.isEmpty()) s.l("camera(mc);");
        s.l("}");
        if (!keys.isEmpty()) {
            s.blank();
            s.l("private static void press(Minecraft mc, int key) {");
            s.l("if (mc.player != null && mc.getConnection() != null) mc.getConnection().sendCommand(" + Src.str("trigger " + KeyGen.objective(ctx.ns) + " set ") + " + key);");
            s.l("}");
        }
        if (!scenes.isEmpty()) cutsceneCamera(s);
        if (!screenButtons().isEmpty()) screenButtonCode(s);
        if (hasFluidExtensions()) {
            s.imp(ctx.rlImport(),
                    "net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions",
                    "net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent");
            s.blank().l("private static void registerFluidExtensions(RegisterClientExtensionsEvent event) {");
            for (Elements.Fluid fl : ctx.all(Elements.Fluid.class)) {
                String c = Ids.toConst(fl.name);
                int rgb = JavaCommon.rgbInt(fl.tintColor, 0x3F76E4);
                String stillTex = ctx.rlParseExpr(Src.str(jc.fluidTextureId(fl, false)));
                String flowTex = ctx.rlParseExpr(Src.str(jc.fluidTextureId(fl, true)));
                s.l("event.registerFluidType(new IClientFluidTypeExtensions() {");
                s.l("    @Override public " + ctx.rlType() + " getStillTexture() { return " + stillTex + "; }");
                s.l("    @Override public " + ctx.rlType() + " getFlowingTexture() { return " + flowTex + "; }");
                s.l("    @Override public int getTintColor() { return 0xFF" + String.format("%06X", rgb) + "; }");
                if (fl.fogColor != null && !fl.fogColor.isBlank() && !ctx.v.atLeast(McVersion.V1_21_8)) {
                    int fog = JavaCommon.rgbInt(fl.fogColor, rgb);
                    s.imp("net.minecraft.client.Camera", "net.minecraft.client.multiplayer.ClientLevel", "org.joml.Vector3f");
                    s.l("    @Override public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor) {");
                    s.l("        return new Vector3f(" + Src.f(((fog >> 16) & 0xFF) / 255.0f) + ", " + Src.f(((fog >> 8) & 0xFF) / 255.0f) + ", " + Src.f((fog & 0xFF) / 255.0f) + ");");
                    s.l("    }");
                }
                s.l("}, ModFluids." + c + "_TYPE.get());");
            }
            s.l("}");
        }
        if (!tooltipColorItems().isEmpty()) {
            s.imp("net.minecraft.world.item.ItemStack");
            s.blank();
            s.l("/** Tooltip box colours of the items with their own tooltip look. */");
            s.l("private static void onTooltipColor(RenderTooltipEvent.Color event) {");
            s.l("ItemStack stack = event.getItemStack();");
            boolean first = true;
            for (Elements.Item it : tooltipColorItems()) {
                int bg = modstudio.core.TooltipArt.argb(it.tooltipBackground, it.tooltipBackgroundOpacity);
                int top = modstudio.core.TooltipArt.argb(it.tooltipBorderTop, it.tooltipBorderOpacity);
                int bottom = modstudio.core.TooltipArt.argb(it.tooltipBorderBottom, it.tooltipBorderOpacity);
                s.l((first ? "if" : "} else if") + " (stack.is(" + jc.itemRef(it.name) + ")) {");
                s.l("event.setBackground(" + hex(bg) + ");");
                s.l("event.setBorderStart(" + hex(top) + ");");
                s.l("event.setBorderEnd(" + hex(bottom) + ");");
                first = false;
            }
            s.l("}");
            s.l("}");
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModClient.java", s.toString());
    }

    /** Adds the theme buttons to the title and pause screens, stacked in their corners. */
    private void screenButtonCode(Src s) {
        s.imp("net.minecraft.client.gui.screens.Screen", "net.minecraft.client.gui.screens.TitleScreen", "net.minecraft.client.gui.screens.PauseScreen",
                "net.minecraft.client.gui.components.Button", "net.minecraft.network.chat.Component", "net.minecraft.Util");
        if (!fabric()) {
            s.blank();
            s.l("private static void onScreenInit(ScreenEvent.Init.Post event) {");
            s.l("addButtons(event.getScreen(), event::addListener);");
            s.l("}");
        }
        s.blank();
        s.l("private static void addButtons(Screen screen, java.util.function.Consumer<Button> add) {");
        s.l("int w = screen.width, h = screen.height;");
        for (String screen : List.of("title", "pause")) {
            List<modstudio.core.Systems.ScreenButton> list = screenButtons().stream().filter(b -> b.screen.equals(screen)).toList();
            if (list.isEmpty()) continue;
            s.l("if (screen instanceof " + (screen.equals("title") ? "TitleScreen" : "PauseScreen") + ") {");
            java.util.Map<String, Integer> used = new java.util.HashMap<>();
            for (modstudio.core.Systems.ScreenButton b : list) {
                int i = used.merge(b.corner, 1, Integer::sum) - 1;
                String x = b.corner.endsWith("left") ? "4" : "w - 102";
                String y = b.corner.startsWith("top") ? String.valueOf(4 + 24 * i) : "h - " + (24 + 24 * i);
                String run;
                if (b.action.equals("command")) {
                    String cmd = b.value.trim().startsWith("/") ? b.value.trim().substring(1) : b.value.trim();
                    run = "{ net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance(); if (mc.getConnection() != null) mc.getConnection().sendCommand("
                            + Src.str(cmd) + "); mc.setScreen(null); }";
                } else {
                    run = "Util.getPlatform().openUri(" + Src.str(b.value.trim()) + ")";
                }
                s.l("add.accept(Button.builder(Component.literal(" + Src.str(b.label) + "), b -> " + run + ").bounds(" + x + ", " + y + ", 98, 20).build());");
            }
            s.l("}");
        }
        s.l("}");
    }

    private static String hex(int argb) {
        return String.format("0x%08X", argb);
    }

    private void cutsceneFields(Src s, List<Cutscene> scenes) {
        s.blank();
        s.l("// Cutscene paths sampled per tick (x y z yaw pitch). The data pack moves the camera entity too, but entity");
        s.l("// packets round angles to 1.4 degrees, so the client follows the exact path instead.");
        StringBuilder names = new StringBuilder(), rel = new StringBuilder();
        for (Cutscene c : scenes) {
            if (names.length() > 0) {
                names.append(", ");
                rel.append(", ");
            }
            names.append(Src.str(CutsceneGen.cameraName(ctx.ns, c)));
            rel.append(!"absolute".equals(c.space));
        }
        s.l("private static final String[] CUTSCENES = {" + names + "};");
        s.l("private static final boolean[] RELATIVE = {" + rel + "};");
        s.l("private static final String[][] PATHS = {");
        for (Cutscene c : scenes) {
            s.l("{");
            for (String chunk : CutsceneGen.samples(c)) s.l(Src.str(chunk) + ",");
            s.l("},");
        }
        s.l("};");
        s.l("private static final double[][] DECODED = new double[CUTSCENES.length][];");
        s.l("private static int cameraId = -1;");
        s.l("private static double anchorX, anchorY, anchorZ;");
    }

    private void cutsceneCamera(Src s) {
        s.imp("net.minecraft.world.entity.Entity", "net.minecraft.world.entity.Display", "net.minecraft.util.Mth");
        s.blank();
        s.l("private static double[] path(int index) {");
        s.l("if (DECODED[index] == null) {");
        s.l("String[] parts = String.join(\" \", PATHS[index]).trim().split(\" \");");
        s.l("double[] d = new double[parts.length];");
        s.l("for (int i = 0; i < parts.length; i++) d[i] = Double.parseDouble(parts[i]);");
        s.l("DECODED[index] = d;");
        s.l("}");
        s.l("return DECODED[index];");
        s.l("}");
        s.blank();
        s.l("/** Sets the spectated cutscene camera to the path at this tick and the one before, so frames blend between exact poses. */");
        s.l("private static void camera(Minecraft mc) {");
        s.l("Entity cam = mc.getCameraEntity();");
        s.l("if (cam == null || cam == mc.player || !(cam instanceof Display) || cam.getCustomName() == null) {");
        s.l("cameraId = -1;");
        s.l("return;");
        s.l("}");
        s.l("int index = java.util.Arrays.asList(CUTSCENES).indexOf(cam.getCustomName().getString());");
        s.l("if (index < 0) return;");
        s.l("double[] d = path(index);");
        s.l("int n = d.length / 5;");
        s.l("int t = Math.max(0, Math.min(n - 1, cam.tickCount - 1));");
        s.l("int p = Math.max(0, t - 1);");
        s.l("if (cameraId != cam.getId()) {");
        s.l("// a relative cutscene starts where the camera appeared");
        s.l("cameraId = cam.getId();");
        s.l("boolean rel = RELATIVE[index];");
        s.l("anchorX = rel ? cam.getX() - d[t * 5] : 0;");
        s.l("anchorY = rel ? cam.getY() - d[t * 5 + 1] : 0;");
        s.l("anchorZ = rel ? cam.getZ() - d[t * 5 + 2] : 0;");
        s.l("}");
        s.l("cam.xo = anchorX + d[p * 5];");
        s.l("cam.yo = anchorY + d[p * 5 + 1];");
        s.l("cam.zo = anchorZ + d[p * 5 + 2];");
        s.l("cam.setPos(anchorX + d[t * 5], anchorY + d[t * 5 + 1], anchorZ + d[t * 5 + 2]);");
        s.l("float yawO = (float) d[p * 5 + 3];");
        s.l("cam.yRotO = yawO;");
        s.l("cam.setYRot(yawO + Mth.wrapDegrees((float) d[t * 5 + 3] - yawO));");
        s.l("cam.xRotO = (float) d[p * 5 + 4];");
        s.l("cam.setXRot((float) d[t * 5 + 4]);");
        s.l("}");
    }
}
