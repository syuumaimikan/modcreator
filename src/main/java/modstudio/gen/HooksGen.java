package modstudio.gen;

import modstudio.core.Elements;
import modstudio.core.Elements.LootTable;
import modstudio.core.Elements.Recipe;
import modstudio.core.Ids;
import modstudio.core.McVersion;
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
        if (hasKeys()) keysClass(forgeEventBus7);
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
                    "net.minecraft.resources.ResourceKey", "net.minecraft.resources.ResourceLocation");
        } else {
            s.imp("net.minecraft.world.level.storage.loot.entries.LootTableReference", "net.minecraft.resources.ResourceLocation");
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
                    + GenContext.rlParse(ctx.singular(), "table") + ")));");
        } else {
            s.l("return LootPool.lootPool().add(LootTableReference.lootTableReference(" + GenContext.rlParse(false, "table") + "));");
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
            if (ctx.components()) {
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
        if (hasKeys()) {
            if (neo()) s.imp("net.neoforged.fml.loading.FMLEnvironment", "net.neoforged.api.distmarker.Dist");
            else s.imp("net.minecraftforge.fml.loading.FMLEnvironment", "net.minecraftforge.api.distmarker.Dist");
            s.l("if (FMLEnvironment.dist == Dist.CLIENT) ModKeys.init(modBus);");
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
    static Elements.StatusEffect projectPotion(GenContext ctx, String id) {
        String f = Ids.full(id.trim());
        if (!Ids.ns(f).equals(ctx.ns)) return null;
        for (Elements.StatusEffect se : ctx.all(Elements.StatusEffect.class)) if (se.potion && se.name.equals(Ids.path(f))) return se;
        return null;
    }

    /** Potion expression: a Holder from 1.20.5 and a Potion before. Null for potions that cannot be referenced. */
    private String potion(String id) {
        String f = Ids.full(id.trim());
        if (Ids.ns(f).equals("minecraft")) return "Potions." + Ids.toConst(Ids.path(f));
        Elements.StatusEffect se = projectPotion(ctx, f);
        if (se == null) return null;
        String c = "ModPotions." + Ids.toConst(se.name);
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

    boolean hasKeys() {
        return !ctx.all(KeyBind.class).isEmpty();
    }

    static String keyName(String ns, KeyBind k) {
        return "key." + ns + "." + k.name;
    }

    static String keyCategory(String ns, KeyBind k) {
        return k.category.equals("mod") || k.category.isBlank() ? "key.categories." + ns : "key.categories." + k.category;
    }

    /** Client-only class: the key mappings, their registration and the tick that sends the trigger command. */
    private void keysClass(boolean forgeEventBus7) {
        Src s = new Src(ctx.pkg);
        s.imp("com.mojang.blaze3d.platform.InputConstants", "net.minecraft.client.KeyMapping", "net.minecraft.client.Minecraft");
        s.l("/** Key bindings. Pressing one sends /trigger " + KeyGen.objective(ctx.ns) + " set <number>; the data pack runs the key's function. */");
        s.l("public final class ModKeys {");
        List<KeyBind> keys = ctx.all(KeyBind.class);
        for (KeyBind k : keys) {
            String key = k.key == null || k.key.isBlank() ? "key.keyboard.unknown" : k.key.trim();
            String type = key.startsWith("key.mouse.") ? "MOUSE" : "KEYSYM";
            s.l("public static final KeyMapping " + Ids.toConst(k.name) + " = new KeyMapping(" + Src.str(keyName(ctx.ns, k)) + ", InputConstants.Type." + type
                    + ", InputConstants.getKey(" + Src.str(key) + ").getValue(), " + Src.str(keyCategory(ctx.ns, k)) + ");");
        }
        s.blank();
        s.l("private ModKeys() {").l("}");
        s.blank();
        switch (loader) {
            case FABRIC -> {
                s.imp("net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper", "net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents");
                s.l("public static void init() {");
                for (KeyBind k : keys) s.l("KeyBindingHelper.registerKeyBinding(" + Ids.toConst(k.name) + ");");
                s.l("ClientTickEvents.END_CLIENT_TICK.register(client -> tick());");
                s.l("}");
            }
            case NEOFORGE -> {
                s.imp("net.neoforged.bus.api.IEventBus", "net.neoforged.neoforge.common.NeoForge", "net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent");
                s.l("public static void init(IEventBus modBus) {");
                s.l("modBus.addListener(ModKeys::register);");
                s.l("NeoForge.EVENT_BUS.addListener(ModKeys::onTick);");
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
                s.imp("net.minecraftforge.client.event.RegisterKeyMappingsEvent", "net.minecraftforge.event.TickEvent");
                if (forgeEventBus7) {
                    s.imp("net.minecraftforge.eventbus.api.bus.BusGroup");
                    s.l("public static void init(BusGroup modBus) {");
                    s.l("RegisterKeyMappingsEvent.getBus(modBus).addListener(ModKeys::register);");
                    s.l("TickEvent.ClientTickEvent.Post.BUS.addListener(ModKeys::onTick);");
                } else {
                    s.imp("net.minecraftforge.eventbus.api.IEventBus", "net.minecraftforge.common.MinecraftForge");
                    s.l("public static void init(IEventBus modBus) {");
                    s.l("modBus.addListener(ModKeys::register);");
                    s.l("MinecraftForge.EVENT_BUS.addListener(ModKeys::onTick);");
                }
                s.l("}");
                s.blank();
                if (ctx.v.atLeast(McVersion.V1_20_4)) {
                    s.l("private static void onTick(TickEvent.ClientTickEvent.Post event) {").l("tick();").l("}");
                } else {
                    s.l("private static void onTick(TickEvent.ClientTickEvent event) {").l("if (event.phase == TickEvent.Phase.END) tick();").l("}");
                }
            }
        }
        if (!fabric()) {
            s.blank();
            s.l("private static void register(RegisterKeyMappingsEvent event) {");
            for (KeyBind k : keys) s.l("event.register(" + Ids.toConst(k.name) + ");");
            s.l("}");
        }
        s.blank();
        s.l("private static void tick() {");
        s.l("Minecraft mc = Minecraft.getInstance();");
        for (int i = 0; i < keys.size(); i++) s.l("while (" + Ids.toConst(keys.get(i).name) + ".consumeClick()) press(mc, " + (i + 1) + ");");
        s.l("}");
        s.blank();
        s.l("private static void press(Minecraft mc, int key) {");
        s.l("if (mc.player != null && mc.getConnection() != null) mc.getConnection().sendCommand(" + Src.str("trigger " + KeyGen.objective(ctx.ns) + " set ") + " + key);");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModKeys.java", s.toString());
    }
}
