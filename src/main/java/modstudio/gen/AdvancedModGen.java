package modstudio.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import modstudio.core.*;
import modstudio.core.ModElements.*;
import modstudio.gen.JavaCommon.Loader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates native mod-loader code for Containers (BlockEntity + Menu + Screen), CustomEntities,
 * ModConfigs, Mixins, Portals, Villager Professions, Custom RecipeTypes, ModNetwork, and Worldgen biome hooks.
 */
final class AdvancedModGen {
    private final GenContext ctx;
    private final JavaCommon jc;
    private final Loader loader;
    private final String res = "src/main/resources/";

    AdvancedModGen(GenContext ctx, JavaCommon jc, Loader loader) {
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

    private String main() {
        return ctx.mainClass;
    }

    private String containerBlockId(Container ct) {
        return ct.block.isBlank() ? ct.name : Ids.path(Ids.full(ct.block));
    }

    /** True when the container generates its own block because no project block claims its id. */
    boolean standaloneContainerBlock(Container ct) {
        String id = containerBlockId(ct);
        return ctx.all(Elements.Block.class).stream().noneMatch(b -> b.name.equals(id));
    }

    Container containerForBlock(String blockName) {
        for (Container ct : ctx.all(Container.class)) {
            if (containerBlockId(ct).equals(blockName)) return ct;
        }
        return null;
    }

    boolean hasConfigs() {
        return !ctx.all(ModConfig.class).isEmpty();
    }

    boolean hasMixins() {
        return ctx.all(MixinSpec.class).stream().anyMatch(mx -> mx.enabled);
    }

    boolean hasContainers() {
        return !ctx.all(Container.class).isEmpty();
    }

    boolean hasEntities() {
        return !ctx.all(CustomEntity.class).isEmpty();
    }

    boolean hasPortals() {
        return !ctx.all(Portal.class).isEmpty();
    }

    boolean hasProfessions() {
        return !ctx.all(Profession.class).isEmpty();
    }

    boolean hasRecipeTypes() {
        return !ctx.all(CustomRecipeType.class).isEmpty();
    }

    boolean hasNetwork() {
        return hasContainers();
    }

    boolean hasClientHooks() {
        return hasContainers() || hasEntities() || ModelGen.hasCustomArmor3d(ctx);
    }

    void generate(boolean forgeEventBus7) {
        if (hasNetwork()) network();
        if (hasConfigs()) configs();
        if (hasMixins()) mixins();
        if (hasContainers()) containers();
        if (hasEntities()) entities();
        new ModelGen(ctx, loader).generate();
        if (hasPortals()) portals();
        if (hasProfessions()) professions();
        if (hasRecipeTypes()) recipeTypes();
        if (hasClientHooks()) clientHooks(forgeEventBus7);
    }

    // ------------------------------------------------------------------ dependencies metadata

    static void fabricDepends(GenContext ctx, JsonObject depends, JsonObject suggests) {
        for (ModDependency dep : ctx.p.meta.dependencies) {
            if (dep.modId == null || dep.modId.isBlank()) continue;
            String id = dep.modId.trim();
            String ver = dep.version == null || dep.version.isBlank() ? "*" : dep.version.trim();
            (dep.required ? depends : suggests).addProperty(id, ver);
        }
    }

    static void quiltDepends(GenContext ctx, JsonArray depends) {
        for (ModDependency dep : ctx.p.meta.dependencies) {
            if (dep.modId == null || dep.modId.isBlank()) continue;
            JsonObject o = J.obj("id", dep.modId.trim(), "versions",
                    dep.version == null || dep.version.isBlank() ? "*" : dep.version.trim());
            if (!dep.required) o.addProperty("optional", true);
            depends.add(o);
        }
    }

    static void appendTomlDepends(GenContext ctx, StringBuilder t, boolean legacyToml) {
        for (ModDependency dep : ctx.p.meta.dependencies) {
            if (dep.modId == null || dep.modId.isBlank()) continue;
            String ver = dep.version == null || dep.version.isBlank() ? "" : dep.version.trim();
            t.append("\n[[dependencies.").append(ctx.ns).append("]]\n");
            t.append("modId=\"").append(dep.modId.trim().replace("\"", "\\\"")).append("\"\n");
            t.append(legacyToml ? "mandatory=" + dep.required : "type=\"" + (dep.required ? "required" : "optional") + "\"").append('\n');
            if (!ver.isEmpty()) t.append("versionRange=\"").append(ver.replace("\"", "\\\"")).append("\"\n");
            t.append("ordering=\"NONE\"\nside=\"BOTH\"\n");
        }
    }

    // ------------------------------------------------------------------ networking

    private void network() {
        Src s = new Src(ctx.pkg);
        s.imp(ctx.rlImport(), "net.minecraft.server.level.ServerPlayer");
        s.l("/** Serverbound action channel shared by containers, UIs and custom mod code. */");
        s.l("public final class ModNetwork {");
        s.l("public static final " + ctx.rlType() + " CHANNEL_ID = " + main() + ".id(\"action\");");
        if (ctx.components()) {
            s.imp("net.minecraft.network.FriendlyByteBuf", "net.minecraft.network.codec.ByteBufCodecs",
                    "net.minecraft.network.codec.StreamCodec", "net.minecraft.network.protocol.common.custom.CustomPacketPayload");
            s.blank();
            s.l("public record ActionPayload(String action) implements CustomPacketPayload {");
            s.l("public static final Type<ActionPayload> TYPE = new Type<>(CHANNEL_ID);");
            s.l("public static final StreamCodec<FriendlyByteBuf, ActionPayload> STREAM_CODEC = StreamCodec.composite(");
            s.l("        ByteBufCodecs.STRING_UTF8, ActionPayload::action, ActionPayload::new);");
            s.l("@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }");
            s.l("}");
        }
        s.blank();
        s.l("private ModNetwork() {").l("}");
        s.blank();
        switch (loader) {
            case FABRIC -> {
                if (ctx.components()) {
                    s.imp("net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry",
                            "net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking");
                    s.l("public static void register() {");
                    s.l("PayloadTypeRegistry.playC2S().register(ActionPayload.TYPE, ActionPayload.STREAM_CODEC);");
                    s.l("ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (payload, context) -> {");
                    s.l("    ServerPlayer player = context.player();");
                    s.l("    context.server().execute(() -> handle(player, payload.action()));");
                    s.l("});");
                    s.l("}");
                } else {
                    s.imp("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking");
                    s.l("public static void register() {");
                    s.l("ServerPlayNetworking.registerGlobalReceiver(CHANNEL_ID, (server, player, handler, buf, responseSender) -> {");
                    s.l("    String action = buf.readUtf();");
                    s.l("    server.execute(() -> handle(player, action));");
                    s.l("});");
                    s.l("}");
                }
            }
            case NEOFORGE -> {
                if (ctx.components()) {
                    s.imp("net.neoforged.bus.api.IEventBus", "net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent",
                            "net.neoforged.neoforge.network.registration.PayloadRegistrar");
                    s.l("public static void register(IEventBus modBus) {");
                    s.l("modBus.addListener((RegisterPayloadHandlersEvent event) -> {");
                    s.l("    PayloadRegistrar reg = event.registrar(" + Src.str(ctx.p.meta.version) + ");");
                    s.l("    reg.playToServer(ActionPayload.TYPE, ActionPayload.STREAM_CODEC, (payload, context) ->");
                    s.l("            context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer sp) handle(sp, payload.action()); }));");
                    s.l("});");
                    s.l("}");
                } else {
                    s.imp("net.neoforged.bus.api.IEventBus");
                    s.l("public static void register(IEventBus modBus) {").l("}");
                }
            }
            case FORGE -> {
                s.l("public static void register(Object modBus) {").l("}");
            }
        }
        s.blank();
        s.l("private static void handle(ServerPlayer player, String action) {");
        s.l("if (action != null && !action.isBlank()) ModProcedures.run(player, action);");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModNetwork.java", s.toString());
    }

    // ------------------------------------------------------------------ configs

    private void configs() {
        Src reg = new Src(ctx.pkg);
        reg.l("public final class ModConfigs {");
        reg.l("private ModConfigs() {").l("}").blank();
        for (ModConfig cfg : ctx.all(ModConfig.class)) {
            String cls = Ids.toPascal(cfg.name) + "Config";
            reg.imp(ctx.pkg + ".config." + cls);
            if (fabric()) fabricConfigClass(cfg, cls);
            else forgeConfigClass(cfg, cls);
        }
        if (fabric()) {
            reg.l("public static void init() {");
            for (ModConfig cfg : ctx.all(ModConfig.class)) {
                reg.l(Ids.toPascal(cfg.name) + "Config.load();");
            }
            reg.l("}");
        }
        reg.l("}");
        ctx.files.text(ctx.javaDir() + "ModConfigs.java", reg.toString());
    }

    private void fabricConfigClass(ModConfig cfg, String cls) {
        String fname = cfg.fileName.isBlank() ? ctx.ns + "-" + cfg.scope + ".json" : cfg.fileName.trim();
        Src s = new Src(ctx.pkg + ".config");
        s.imp("com.google.gson.Gson", "com.google.gson.GsonBuilder", "com.google.gson.JsonObject",
                "net.fabricmc.loader.api.FabricLoader", "java.nio.file.Files", "java.nio.file.Path");
        s.l("public final class " + cls + " {");
        s.l("private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();");
        s.l("private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(" + Src.str(fname) + ");");
        for (ConfigEntry en : cfg.entries) {
            String field = configField(en.key);
            switch (en.kind) {
                case "int" -> s.l("public static int " + field + " = " + parseInt(en.value, 0) + ";");
                case "double" -> s.l("public static double " + field + " = " + parseDouble(en.value, 0.0) + ";");
                case "bool" -> s.l("public static boolean " + field + " = " + Boolean.parseBoolean(en.value) + ";");
                default -> s.l("public static String " + field + " = " + Src.str(en.value) + ";");
            }
        }
        s.blank().l("public static void load() {");
        s.l("try {");
        s.l("    if (Files.isRegularFile(FILE)) {");
        s.l("        JsonObject o = GSON.fromJson(Files.readString(FILE), JsonObject.class);");
        s.l("        if (o != null) {");
        for (ConfigEntry en : cfg.entries) {
            String field = configField(en.key);
            s.l("            if (o.has(" + Src.str(en.key) + ")) {");
            switch (en.kind) {
                case "int" -> s.l("                " + field + " = o.get(" + Src.str(en.key) + ").getAsInt();");
                case "double" -> s.l("                " + field + " = o.get(" + Src.str(en.key) + ").getAsDouble();");
                case "bool" -> s.l("                " + field + " = o.get(" + Src.str(en.key) + ").getAsBoolean();");
                default -> s.l("                " + field + " = o.get(" + Src.str(en.key) + ").getAsString();");
            }
            s.l("            }");
        }
        s.l("        }");
        s.l("    }");
        s.l("    save();");
        s.l("} catch (Exception ignored) { }");
        s.l("}");
        s.blank().l("public static void save() {");
        s.l("try {");
        s.l("    Files.createDirectories(FILE.getParent());");
        s.l("    JsonObject o = new JsonObject();");
        for (ConfigEntry en : cfg.entries) {
            s.l("    o.addProperty(" + Src.str(en.key) + ", " + configField(en.key) + ");");
        }
        s.l("    Files.writeString(FILE, GSON.toJson(o));");
        s.l("} catch (Exception ignored) { }");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "config/" + cls + ".java", s.toString());
    }

    private void forgeConfigClass(ModConfig cfg, String cls) {
        String specCls = neo() || ctx.components() ? (neo() ? "ModConfigSpec" : "ForgeConfigSpec") : "ForgeConfigSpec";
        String specPkg = neo() ? "net.neoforged.neoforge.common.ModConfigSpec" : "net.minecraftforge.common.ForgeConfigSpec";
        Src s = new Src(ctx.pkg + ".config");
        s.imp(specPkg);
        s.l("public final class " + cls + " {");
        s.l("public static final " + specCls + ".Builder BUILDER = new " + specCls + ".Builder();");
        s.l("public static final " + specCls + " SPEC;");
        for (ConfigEntry en : cfg.entries) {
            String field = configField(en.key);
            String type = switch (en.kind) {
                case "int" -> specCls + ".IntValue";
                case "double" -> specCls + ".DoubleValue";
                case "bool" -> specCls + ".BooleanValue";
                default -> specCls + ".ConfigValue<String>";
            };
            s.l("public static final " + type + " " + field + ";");
        }
        s.blank().l("static {");
        s.l("BUILDER.push(" + Src.str(cfg.name) + ");");
        for (ConfigEntry en : cfg.entries) {
            String field = configField(en.key);
            String comment = en.comment == null || en.comment.isBlank() ? "" : ".comment(" + Src.str(en.comment) + ")";
            switch (en.kind) {
                case "int" -> {
                    int v = parseInt(en.value, 0), min = parseInt(en.min, Integer.MIN_VALUE / 2), max = parseInt(en.max, Integer.MAX_VALUE / 2);
                    if (min > max) { int t = min; min = max; max = t; }
                    s.l(field + " = BUILDER" + comment + ".defineInRange(" + Src.str(en.key) + ", " + v + ", " + min + ", " + max + ");");
                }
                case "double" -> {
                    double v = parseDouble(en.value, 0), min = parseDouble(en.min, -1e9), max = parseDouble(en.max, 1e9);
                    if (min > max) { double t = min; min = max; max = t; }
                    s.l(field + " = BUILDER" + comment + ".defineInRange(" + Src.str(en.key) + ", " + v + ", " + min + ", " + max + ");");
                }
                case "bool" -> s.l(field + " = BUILDER" + comment + ".define(" + Src.str(en.key) + ", " + Boolean.parseBoolean(en.value) + ");");
                default -> s.l(field + " = BUILDER" + comment + ".define(" + Src.str(en.key) + ", " + Src.str(en.value) + ");");
            }
        }
        s.l("BUILDER.pop();");
        s.l("SPEC = BUILDER.build();");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "config/" + cls + ".java", s.toString());
    }

    private static String configField(String key) {
        String id = Ids.toConst(key.replace('.', '_').replace('-', '_'));
        return id.isEmpty() ? "VALUE" : id;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }

    // ------------------------------------------------------------------ mixins

    private void mixins() {
        JsonArray common = new JsonArray(), client = new JsonArray(), server = new JsonArray();
        for (MixinSpec mx : ctx.all(MixinSpec.class)) {
            if (!mx.enabled) continue;
            String cls = Ids.toPascal(mx.name) + "Mixin";
            String mixinPkg = ctx.pkg + ".mixin";
            if (mx.rawMode && mx.rawCode != null && !mx.rawCode.isBlank()) {
                String raw = JavaSteps.resolvePlaceholders(mx.rawCode, ctx).replace("{mixin_class}", cls);
                String srcText;
                String relEntry;
                if (raw.contains("@Mixin")) {
                    String fqcn = JavaSteps.extractFqcn(ctx.javaDir() + "mixin/" + cls + ".java", raw, ctx);
                    String simple = fqcn.contains(".") ? fqcn.substring(fqcn.lastIndexOf('.') + 1) : cls;
                    relEntry = fqcn.startsWith(mixinPkg + ".") ? fqcn.substring(mixinPkg.length() + 1) : simple;
                    srcText = raw.trim().startsWith("package ") ? raw : ("package " + mixinPkg + ";\n\n" + raw);
                    cls = simple;
                } else {
                    Src s = new Src(mixinPkg);
                    String target = mx.targetClass == null || mx.targetClass.isBlank() ? "net.minecraft.world.entity.player.Player" : mx.targetClass.trim();
                    s.imp("org.spongepowered.asm.mixin.Mixin");
                    boolean strTarget = mx.useTargetsString || target.contains("$");
                    if (!strTarget) s.imp(target.replace('$', '.'));
                    s.l(strTarget ? "@Mixin(targets = " + Src.str(target) + ")" : "@Mixin(" + mx.simpleTarget() + ".class)");
                    s.l("public abstract class " + cls + " {");
                    for (String line : raw.strip().split("\\R")) s.l(line);
                    s.l("}");
                    srcText = s.toString();
                    relEntry = cls;
                }
                switch (mx.side) {
                    case "client" -> client.add(relEntry);
                    case "server" -> server.add(relEntry);
                    default -> common.add(relEntry);
                }
                ctx.files.text(ctx.javaDir() + "mixin/" + cls + ".java", srcText);
                continue;
            }
            switch (mx.side) {
                case "client" -> client.add(cls);
                case "server" -> server.add(cls);
                default -> common.add(cls);
            }
            Src s = new Src(mixinPkg);
            String target = mx.targetClass == null || mx.targetClass.isBlank() ? "net.minecraft.world.entity.player.Player" : mx.targetClass.trim();
            boolean strTarget = mx.useTargetsString || target.contains("$");
            s.imp("org.spongepowered.asm.mixin.Mixin",
                    "org.spongepowered.asm.mixin.injection.At",
                    "org.spongepowered.asm.mixin.injection.Inject",
                    "org.spongepowered.asm.mixin.injection.callback.CallbackInfo");
            if (!strTarget) s.imp(target.replace('$', '.'));
            String simple = mx.simpleTarget();
            s.l(strTarget ? "@Mixin(targets = " + Src.str(target) + ")" : "@Mixin(" + simple + ".class)");
            s.l("public class " + cls + " {");
            String at = List.of("HEAD", "TAIL", "RETURN").contains(mx.injectAt) ? mx.injectAt : "HEAD";
            s.l("@Inject(method = " + Src.str(mx.methodTarget.trim()) + ", at = @At(" + Src.str(at) + ")"
                    + (mx.cancellable ? ", cancellable = true" : "") + ")");
            s.l("private void " + Ids.toId(mx.name) + "Handler(CallbackInfo ci) {");
            String code = mx.code == null ? "" : JavaSteps.resolvePlaceholders(mx.code.strip(), ctx);
            if (!code.isEmpty()) {
                for (String line : code.split("\\R")) s.l(line.strip());
            } else {
                s.l("// Hook injected into " + simple + "." + mx.methodTarget.trim());
            }
            s.l("}");
            s.l("}");
            ctx.files.text(ctx.javaDir() + "mixin/" + cls + ".java", s.toString());
        }
        if (common.isEmpty() && client.isEmpty() && server.isEmpty()) return;
        JsonObject root = J.obj("required", true, "package", ctx.pkg + ".mixin", "compatibilityLevel", "JAVA_" + ctx.v.java);
        if (!common.isEmpty()) root.add("mixins", common);
        if (!client.isEmpty()) root.add("client", client);
        if (!server.isEmpty()) root.add("server", server);
        root.add("injectors", J.obj("defaultRequire", 1));
        ctx.files.json(res + ctx.ns + ".mixins.json", root);
    }

    // ------------------------------------------------------------------ containers (BlockEntity + Menu + Screen)

    private void containers() {
        Src beReg = new Src(ctx.pkg);
        Src mReg = new Src(ctx.pkg);
        if (fabric()) {
            beReg.imp("net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder",
                    "net.minecraft.core.Registry", "net.minecraft.core.registries.BuiltInRegistries",
                    "net.minecraft.world.level.block.entity.BlockEntityType");
            mReg.imp("net.minecraft.core.Registry", "net.minecraft.core.registries.BuiltInRegistries",
                    "net.minecraft.world.flag.FeatureFlags", "net.minecraft.world.inventory.MenuType");
        } else {
            beReg.imp("net.minecraft.world.level.block.entity.BlockEntityType",
                    neo() ? "net.minecraft.core.registries.Registries" : "net.minecraftforge.registries.ForgeRegistries",
                    neo() ? "net.neoforged.neoforge.registries.DeferredRegister" : "net.minecraftforge.registries.DeferredRegister",
                    neo() ? "net.neoforged.neoforge.registries.DeferredHolder" : "net.minecraftforge.registries.RegistryObject");
            mReg.imp("net.minecraft.world.flag.FeatureFlags", "net.minecraft.world.inventory.MenuType",
                    neo() ? "net.minecraft.core.registries.Registries" : "net.minecraftforge.registries.ForgeRegistries",
                    neo() ? "net.neoforged.neoforge.registries.DeferredRegister" : "net.minecraftforge.registries.DeferredRegister",
                    neo() ? "net.neoforged.neoforge.registries.DeferredHolder" : "net.minecraftforge.registries.RegistryObject");
        }
        beReg.l("public final class ModBlockEntities {");
        mReg.l("public final class ModContainerMenus {");
        if (!fabric()) {
            beReg.l("public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create("
                    + (neo() ? "Registries.BLOCK_ENTITY_TYPE" : "ForgeRegistries.BLOCK_ENTITY_TYPES") + ", " + main() + ".MODID);");
            mReg.l("public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create("
                    + (neo() ? "Registries.MENU" : "ForgeRegistries.MENU_TYPES") + ", " + main() + ".MODID);");
            beReg.blank();
            mReg.blank();
        }
        for (Container ct : ctx.all(Container.class)) {
            String pas = Ids.toPascal(ct.name);
            String c = Ids.toConst(ct.name);
            String blkRef = jc.blockRef(containerBlockId(ct));
            String beCls = pas + "BlockEntity", menuCls = pas + "Menu", scrCls = pas + "Screen";
            beReg.imp(ctx.pkg + ".container." + beCls);
            mReg.imp(ctx.pkg + ".container." + menuCls);
            if (fabric()) {
                beReg.l("public static final BlockEntityType<" + beCls + "> " + c + " = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, "
                        + main() + ".id(" + Src.str(ct.name) + "), FabricBlockEntityTypeBuilder.create(" + beCls + "::new, " + blkRef + ").build());");
                mReg.l("public static final MenuType<" + menuCls + "> " + c + " = Registry.register(BuiltInRegistries.MENU, "
                        + main() + ".id(" + Src.str(ct.name) + "), new MenuType<>(" + menuCls + "::new, FeatureFlags.VANILLA_SET));");
            } else {
                String beT = neo() ? "DeferredHolder<BlockEntityType<?>, BlockEntityType<" + beCls + ">>" : "RegistryObject<BlockEntityType<" + beCls + ">>";
                String mT = neo() ? "DeferredHolder<MenuType<?>, MenuType<" + menuCls + ">>" : "RegistryObject<MenuType<" + menuCls + ">>";
                beReg.l("public static final " + beT + " " + c + " = BLOCK_ENTITIES.register(" + Src.str(ct.name) + ", () -> BlockEntityType.Builder.of("
                        + beCls + "::new, " + blkRef + ").build(null));");
                mReg.l("public static final " + mT + " " + c + " = MENUS.register(" + Src.str(ct.name) + ", () -> new MenuType<>("
                        + menuCls + "::new, FeatureFlags.VANILLA_SET));");
            }
            if (standaloneContainerBlock(ct)) containerBlockClass(ct, pas + "ContainerBlock", beCls);
            containerBlockEntity(ct, beCls, menuCls, c);
            containerMenu(ct, menuCls, c);
            containerScreen(ct, menuCls, scrCls);
        }
        beReg.blank().l("private ModBlockEntities() {").l("}").blank().l("public static void init() {").l("}").l("}");
        mReg.blank().l("private ModContainerMenus() {").l("}").blank().l("public static void init() {").l("}").l("}");
        ctx.files.text(ctx.javaDir() + "ModBlockEntities.java", beReg.toString());
        ctx.files.text(ctx.javaDir() + "ModContainerMenus.java", mReg.toString());
    }

    private void containerBlockClass(Container ct, String cls, String beCls) {
        Src s = new Src(ctx.pkg + ".block");
        s.imp("net.minecraft.core.BlockPos", "net.minecraft.world.InteractionResult",
                "net.minecraft.world.MenuProvider", "net.minecraft.world.entity.player.Player",
                "net.minecraft.world.level.Level", "net.minecraft.world.level.block.Block",
                "net.minecraft.world.level.block.EntityBlock", "net.minecraft.world.level.block.entity.BlockEntity",
                "net.minecraft.world.level.block.entity.BlockEntityTicker",
                "net.minecraft.world.level.block.entity.BlockEntityType",
                "net.minecraft.world.level.block.state.BlockBehaviour",
                "net.minecraft.world.level.block.state.BlockState",
                "net.minecraft.world.phys.BlockHitResult",
                ctx.pkg + ".ModBlockEntities", ctx.pkg + ".container." + beCls);
        String c = Ids.toConst(ct.name);
        s.l("public class " + cls + " extends Block implements EntityBlock {");
        s.l("public " + cls + "(BlockBehaviour.Properties properties) { super(properties); }");
        s.blank().l("@Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new " + beCls + "(pos, state); }");
        containerBlockMethods(s, ct, beCls, c);
        s.l("}");
        ctx.files.text(ctx.javaDir() + "block/" + cls + ".java", s.toString());
    }

    /** Shared EntityBlock methods injected into either standalone or custom Block classes. */
    void containerBlockMethods(Src s, Container ct, String beCls, String c) {
        s.imp("net.minecraft.core.BlockPos", "net.minecraft.world.Containers", "net.minecraft.world.InteractionResult",
                "net.minecraft.world.MenuProvider", "net.minecraft.world.entity.player.Player",
                "net.minecraft.world.level.Level", "net.minecraft.world.level.block.entity.BlockEntity",
                "net.minecraft.world.level.block.entity.BlockEntityTicker", "net.minecraft.world.level.block.entity.BlockEntityType",
                "net.minecraft.world.level.block.state.BlockState", "net.minecraft.world.phys.BlockHitResult",
                ctx.pkg + ".ModBlockEntities", ctx.pkg + ".container." + beCls);
        String typeRef = fabric() ? "ModBlockEntities." + c : "ModBlockEntities." + c + ".get()";
        s.blank().l("@Override");
        s.l("public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {");
        s.l("return level.isClientSide() || type != " + typeRef + " ? null : (lvl, p, st, be) -> { if (be instanceof " + beCls + " c) c.tick(lvl, p, st); };");
        s.l("}");
        if (ct.openOnRightClick) {
            Elements.Block hostBlock = ctx.all(Elements.Block.class).stream()
                    .filter(b -> b.name.equals(containerBlockId(ct))).findFirst().orElse(null);
            boolean runUse = hostBlock != null && !hostBlock.onUse.isEmpty();
            if (runUse) s.imp(ctx.pkg + ".ModProcedures");
            s.blank().l("@Override");
            if (ctx.components()) {
                s.l("protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {");
            } else {
                s.imp("net.minecraft.world.InteractionHand");
                s.l("public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {");
            }
            if (runUse) s.l("if (!level.isClientSide()) ModProcedures.run(player, " + Src.str("blocks/" + hostBlock.name + "/on_use") + ");");
            s.l("if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MenuProvider mp) player.openMenu(mp);");
            s.l("return InteractionResult.SUCCESS;").l("}");
        }
        s.blank().l("@Override");
        s.l("public boolean hasAnalogOutputSignal(BlockState state) { return true; }");
        s.blank().l("@Override");
        s.l("public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos" + (ctx.v.atLeast(McVersion.V1_21_9) ? ", net.minecraft.core.Direction dir" : "") + ") {");
        s.l("if (level.getBlockEntity(pos) instanceof " + beCls + " c) return c.comparatorSignal();");
        s.l("return 0;").l("}");
        if (ctx.v.atLeast(McVersion.V1_21_8)) {
            s.imp("net.minecraft.server.level.ServerLevel");
            s.blank().l("@Override");
            s.l("protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {");
            s.l("super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);");
            s.l("level.updateNeighbourForOutputSignal(pos, this);").l("}");
        } else {
            s.blank().l("@Override");
            s.l("public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {");
            s.l("if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof " + beCls + " c) {");
            s.l("    Containers.dropContents(level, pos, c);");
            s.l("}");
            s.l("super.onRemove(state, level, pos, newState, movedByPiston);").l("}");
        }
    }

    private int containerSlot(Container ct, String kind, int nth, int fallback) {
        int found = 0;
        if (ct.slots != null) {
            for (int i = 0; i < ct.slots.size(); i++) {
                SlotDef sl = ct.slots.get(i);
                if (sl != null && kind.equals(sl.kind) && found++ == nth) {
                    return Math.max(0, Math.min(ct.slotCount() - 1, sl.index > 0 || i == 0 ? sl.index : i));
                }
            }
        }
        return fallback;
    }

    private void containerBlockEntity(Container ct, String beCls, String menuCls, String c) {
        int slots = ct.slotCount();
        int outSlot = containerSlot(ct, "output", 0, slots - 1);
        int fuelSlot = containerSlot(ct, "fuel", 0, -1);
        int inSlot = containerSlot(ct, "input", 0, outSlot == 0 ? (slots > 1 ? 1 : 0) : 0);
        int inSlot2 = containerSlot(ct, "input", 1, -1);
        boolean anyFuel = ct.recipes.stream().anyMatch(cr -> cr.requiresFuel && !cr.input.isBlank() && !cr.output.isBlank());
        if (anyFuel && fuelSlot < 0 && slots >= 3) {
            for (int i = 0; i < slots; i++) {
                if (i != inSlot && i != outSlot) { fuelSlot = i; break; }
            }
        }
        if (inSlot2 < 0 && slots >= 3 && ct.recipes.stream().anyMatch(cr -> !cr.secondInput.isBlank())) {
            for (int i = 0; i < slots; i++) {
                if (i != inSlot && i != outSlot && i != fuelSlot) { inSlot2 = i; break; }
            }
        }
        Src s = new Src(ctx.pkg + ".container");
        s.imp("net.minecraft.core.BlockPos", "net.minecraft.core.Direction", "net.minecraft.core.NonNullList",
                "net.minecraft.network.chat.Component", "net.minecraft.server.level.ServerLevel",
                "net.minecraft.world.ContainerHelper", "net.minecraft.world.MenuProvider",
                "net.minecraft.world.WorldlyContainer", "net.minecraft.world.entity.player.Inventory",
                "net.minecraft.world.entity.player.Player", "net.minecraft.world.inventory.AbstractContainerMenu",
                "net.minecraft.world.inventory.ContainerData", "net.minecraft.world.item.ItemStack",
                "net.minecraft.world.level.Level", "net.minecraft.world.level.block.entity.BlockEntity",
                "net.minecraft.world.level.block.state.BlockState",
                ctx.pkg + ".ModBlockEntities");
        if (anyFuel && fuelSlot >= 0) s.imp("net.minecraft.tags.ItemTags", "net.minecraft.world.item.Items");
        if (!ct.onOpen.isEmpty() || !ct.onClose.isEmpty() || !ct.onCraft.isEmpty()) s.imp(ctx.pkg + ".ModProcedures");
        if (!ct.recipes.isEmpty()) s.imp("net.minecraft.core.registries.BuiltInRegistries", ctx.pkg + ".ModItems", ctx.pkg + ".ModProcedures");
        if (ctx.v.atLeast(McVersion.V1_21_8)) {
            s.imp("net.minecraft.world.level.storage.ValueInput", "net.minecraft.world.level.storage.ValueOutput");
        } else {
            s.imp("net.minecraft.nbt.CompoundTag");
            if (ctx.components()) s.imp("net.minecraft.core.HolderLookup");
        }
        String typeRef = fabric() ? "ModBlockEntities." + c : "ModBlockEntities." + c + ".get()";
        s.l("public class " + beCls + " extends BlockEntity implements WorldlyContainer, MenuProvider {");
        s.l("private final NonNullList<ItemStack> items = NonNullList.withSize(" + slots + ", ItemStack.EMPTY);");
        s.l("private int progress = 0;");
        if (anyFuel && fuelSlot >= 0) s.l("private int burnTime = 0;");
        s.l("private int maxProgress = " + (ct.recipes.isEmpty() ? 100 : Math.max(1, ct.recipes.get(0).processTicks)) + ";");
        s.l("private final ContainerData data = new ContainerData() {");
        s.l("    @Override public int get(int idx) { return idx == 0 ? progress : maxProgress; }");
        s.l("    @Override public void set(int idx, int val) { if (idx == 0) progress = val; else maxProgress = val; }");
        s.l("    @Override public int getCount() { return 2; }");
        s.l("};");
        s.blank();
        s.l("public " + beCls + "(BlockPos pos, BlockState state) { super(" + typeRef + ", pos, state); }");
        s.blank().l("@Override public Component getDisplayName() { return Component.translatable(" + Src.str("container." + ctx.ns + "." + ct.name) + "); }");
        s.blank().l("@Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) { return new " + menuCls + "(id, inv, this, data); }");
        s.blank().l("@Override public int getContainerSize() { return items.size(); }");
        s.l("@Override public boolean isEmpty() { for (ItemStack st : items) if (!st.isEmpty()) return false; return true; }");
        s.l("@Override public ItemStack getItem(int slot) { return items.get(slot); }");
        s.l("@Override public ItemStack removeItem(int slot, int amount) { ItemStack res = ContainerHelper.removeItem(items, slot, amount); setChanged(); return res; }");
        s.l("@Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }");
        s.l("@Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); setChanged(); }");
        s.l("@Override public boolean stillValid(Player player) { return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0; }");
        s.l("@Override public void clearContent() { items.clear(); setChanged(); }");
        s.blank();
        s.l("private static final int[] ALL_SLOTS = java.util.stream.IntStream.range(0, " + slots + ").toArray();");
        s.l("@Override public int[] getSlotsForFace(Direction side) { return ALL_SLOTS; }");
        String placeRule = outSlot >= 0 && slots > 1
                ? "dir != Direction.DOWN && idx != " + outSlot + (anyFuel && fuelSlot >= 0 ? " && (idx != " + fuelSlot + " || fuelTicks(st) > 0)" : "")
                : "dir != Direction.DOWN";
        String takeRule = outSlot >= 0 && slots > 1 ? "dir != Direction.DOWN || idx == " + outSlot : "true";
        s.l("@Override public boolean canPlaceItemThroughFace(int idx, ItemStack st, Direction dir) { return " + placeRule + "; }");
        s.l("@Override public boolean canTakeItemThroughFace(int idx, ItemStack st, Direction dir) { return " + takeRule + "; }");
        if (!ct.onOpen.isEmpty()) {
            s.l("@Override public void startOpen(Player player) { if (!player.level().isClientSide()) ModProcedures.run(player, " + Src.str("containers/" + ct.name + "/on_open") + "); }");
        }
        if (!ct.onClose.isEmpty()) {
            s.l("@Override public void stopOpen(Player player) { if (!player.level().isClientSide()) ModProcedures.run(player, " + Src.str("containers/" + ct.name + "/on_close") + "); }");
        }
        s.blank();
        s.l("public int comparatorSignal() {");
        if (ct.comparatorMode == 1 && !ct.recipes.isEmpty()) {
            s.l("    return maxProgress <= 0 ? 0 : Math.min(15, (progress * 15) / maxProgress);");
        } else {
            s.l("    return AbstractContainerMenu.getRedstoneSignalFromContainer(this);");
        }
        s.l("}");
        s.blank();
        String saveBurn = anyFuel && fuelSlot >= 0 ? (ctx.v.atLeast(McVersion.V1_21_8) ? " out.putInt(\"BurnTime\", burnTime);" : " tag.putInt(\"BurnTime\", burnTime);") : "";
        String loadBurn = anyFuel && fuelSlot >= 0 ? (ctx.v.atLeast(McVersion.V1_21_8) ? " burnTime = in.getIntOr(\"BurnTime\", 0);" : " burnTime = tag.getInt(\"BurnTime\");") : "";
        if (ctx.v.atLeast(McVersion.V1_21_8)) {
            s.l("@Override protected void saveAdditional(ValueOutput out) { super.saveAdditional(out); ContainerHelper.saveAllItems(out, items); out.putInt(\"Progress\", progress);" + saveBurn + " }");
            s.l("@Override protected void loadAdditional(ValueInput in) { super.loadAdditional(in); ContainerHelper.loadAllItems(in, items); progress = in.getIntOr(\"Progress\", 0);" + loadBurn + " }");
        } else if (ctx.components()) {
            s.l("@Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) { super.saveAdditional(tag, reg); ContainerHelper.saveAllItems(tag, items, reg); tag.putInt(\"Progress\", progress);" + saveBurn + " }");
            s.l("@Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) { super.loadAdditional(tag, reg); ContainerHelper.loadAllItems(tag, items, reg); progress = tag.getInt(\"Progress\");" + loadBurn + " }");
        } else {
            s.l("@Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); ContainerHelper.saveAllItems(tag, items); tag.putInt(\"Progress\", progress);" + saveBurn + " }");
            s.l("@Override public void load(CompoundTag tag) { super.load(tag); ContainerHelper.loadAllItems(tag, items); progress = tag.getInt(\"Progress\");" + loadBurn + " }");
        }
        s.blank();
        s.l("public void tick(Level level, BlockPos pos, BlockState state) {");
        if (anyFuel && fuelSlot >= 0) s.l("    if (burnTime > 0) { burnTime--; setChanged(); }");
        if (!ct.recipes.isEmpty() && slots >= 2) {
            s.l("    ItemStack in = getItem(" + inSlot + "), out = getItem(" + outSlot + ");");
            s.l("    if (in.isEmpty()) { if (progress != 0) { progress = 0; setChanged(); } return; }");
            s.l("    String inId = BuiltInRegistries.ITEM.getKey(in.getItem()).toString();");
            String sameStack = ctx.components() ? "ItemStack.isSameItemSameComponents(out, produced)" : "ItemStack.isSameItemSameTags(out, produced)";
            for (ContainerRecipe cr : ct.recipes) {
                if (cr.input.isBlank() || cr.output.isBlank()) continue;
                s.l("    if (inId.equals(" + Src.str(Ids.full(cr.input)) + ")) {");
                if (!cr.secondInput.isBlank() && inSlot2 >= 0) {
                    s.l("        ItemStack in2 = getItem(" + inSlot2 + ");");
                    s.l("        if (in2.isEmpty() || !BuiltInRegistries.ITEM.getKey(in2.getItem()).toString().equals(" + Src.str(Ids.full(cr.secondInput)) + ")) { progress = 0; return; }");
                }
                if (cr.requiresFuel && fuelSlot >= 0) {
                    s.l("        if (burnTime <= 0) {");
                    s.l("            ItemStack fuel = getItem(" + fuelSlot + "); int ft = fuelTicks(fuel);");
                    s.l("            if (ft <= 0) { if (progress > 0) { progress = 0; setChanged(); } return; }");
                    s.l("            burnTime = ft; if (fuel.is(Items.LAVA_BUCKET)) setItem(" + fuelSlot + ", new ItemStack(Items.BUCKET)); else fuel.shrink(1); setChanged();");
                    s.l("        }");
                }
                s.l("        ItemStack produced = new ItemStack(" + jc.anyItemRef(cr.output) + ", " + Math.max(1, cr.outputCount) + ");");
                s.l("        if (out.isEmpty() || (" + sameStack + " && out.getCount() + produced.getCount() <= out.getMaxStackSize())) {");
                s.l("            maxProgress = " + Math.max(1, cr.processTicks) + ";");
                s.l("            if (++progress >= maxProgress) {");
                s.l("                in.shrink(1);");
                if (!cr.secondInput.isBlank() && inSlot2 >= 0) s.l("                getItem(" + inSlot2 + ").shrink(1);");
                s.l("                if (out.isEmpty()) setItem(" + outSlot + ", produced); else out.grow(produced.getCount());");
                s.l("                progress = 0;");
                if (!ct.onCraft.isEmpty()) s.l("                if (level instanceof ServerLevel sl) ModProcedures.run(sl, pos, " + Src.str("containers/" + ct.name + "/on_craft") + ");");
                s.l("                setChanged();");
                s.l("            }");
                s.l("            return;");
                s.l("        }");
                s.l("    }");
            }
            s.l("    if (progress != 0) { progress = 0; setChanged(); }");
        }
        if (anyFuel && fuelSlot >= 0) {
            s.l("}");
            s.blank();
            s.l("private static int fuelTicks(ItemStack st) {");
            s.l("    if (st.isEmpty()) return 0;");
            s.l("    if (st.is(Items.COAL_BLOCK)) return 16000;");
            s.l("    if (st.is(Items.LAVA_BUCKET)) return 20000;");
            s.l("    if (st.is(Items.BLAZE_ROD)) return 2400;");
            s.l("    if (st.is(Items.COAL) || st.is(Items.CHARCOAL)) return 1600;");
            s.l("    if (st.is(ItemTags.LOGS) || st.is(ItemTags.PLANKS)) return 300;");
            s.l("    return st.is(Items.STICK) ? 100 : 0;");
            s.l("}");
            s.l("}");
            ctx.files.text(ctx.javaDir() + "container/" + beCls + ".java", s.toString());
            return;
        }
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "container/" + beCls + ".java", s.toString());
    }

    private void containerMenu(Container ct, String menuCls, String c) {
        int slots = ct.slotCount();
        int rows = Math.max(1, Math.min(6, ct.rows));
        int playerY = ct.useCustomSlots ? 84 : 18 + rows * 18 + 14;
        Src s = new Src(ctx.pkg + ".container");
        s.imp("net.minecraft.world.Container", "net.minecraft.world.SimpleContainer",
                "net.minecraft.world.entity.player.Inventory", "net.minecraft.world.entity.player.Player",
                "net.minecraft.world.inventory.AbstractContainerMenu", "net.minecraft.world.inventory.ContainerData",
                "net.minecraft.world.inventory.SimpleContainerData", "net.minecraft.world.inventory.Slot",
                "net.minecraft.world.item.ItemStack", ctx.pkg + ".ModContainerMenus");
        String typeRef = fabric() ? "ModContainerMenus." + c : "ModContainerMenus." + c + ".get()";
        s.l("public class " + menuCls + " extends AbstractContainerMenu {");
        s.l("private final Container container;");
        s.l("private final ContainerData data;");
        s.blank();
        s.l("public " + menuCls + "(int id, Inventory inv) { this(id, inv, new SimpleContainer(" + slots + "), new SimpleContainerData(2)); }");
        s.blank();
        s.l("public " + menuCls + "(int id, Inventory inv, Container container, ContainerData data) {");
        s.l("super(" + typeRef + ", id);");
        s.l("checkContainerSize(container, " + slots + ");");
        s.l("this.container = container; this.data = data;");
        s.l("container.startOpen(inv.player);");
        if (ct.useCustomSlots) {
            for (int i = 0; i < ct.slots.size(); i++) {
                SlotDef sd = ct.slots.get(i);
                if (sd.kind.equals("output") || sd.kind.equals("readonly")) {
                    s.l("addSlot(new Slot(container, " + i + ", " + sd.x + ", " + sd.y + ") { @Override public boolean mayPlace(ItemStack st) { return false; } });");
                } else {
                    s.l("addSlot(new Slot(container, " + i + ", " + sd.x + ", " + sd.y + "));");
                }
            }
        } else {
            s.l("for (int r = 0; r < " + rows + "; r++) for (int col = 0; col < 9; col++)");
            s.l("    addSlot(new Slot(container, col + r * 9, 8 + col * 18, 18 + r * 18));");
        }
        s.l("for (int r = 0; r < 3; r++) for (int col = 0; col < 9; col++)");
        s.l("    addSlot(new Slot(inv, col + r * 9 + 9, 8 + col * 18, " + playerY + " + r * 18));");
        s.l("for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, " + (playerY + 58) + "));");
        s.l("addDataSlots(data);");
        s.l("}");
        s.blank().l("public int progress() { return data.get(0); }");
        s.l("public int maxProgress() { return Math.max(1, data.get(1)); }");
        s.blank().l("@Override public boolean stillValid(Player player) { return container.stillValid(player); }");
        s.l("@Override public void removed(Player player) { super.removed(player); container.stopOpen(player); }");
        s.blank().l("@Override public ItemStack quickMoveStack(Player player, int index) {");
        s.l("ItemStack copy = ItemStack.EMPTY; Slot slot = slots.get(index);");
        s.l("if (slot != null && slot.hasItem()) {");
        s.l("    ItemStack stack = slot.getItem(); copy = stack.copy();");
        s.l("    if (index < " + slots + ") { if (!moveItemStackTo(stack, " + slots + ", slots.size(), true)) return ItemStack.EMPTY; }");
        s.l("    else if (!moveItemStackTo(stack, 0, " + slots + ", false)) return ItemStack.EMPTY;");
        s.l("    if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();");
        s.l("}");
        s.l("return copy;").l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "container/" + menuCls + ".java", s.toString());
    }

    private void containerScreen(Container ct, String menuCls, String scrCls) {
        int rows = Math.max(1, Math.min(6, ct.rows));
        int h = ct.useCustomSlots ? 166 : 114 + rows * 18;
        Src s = new Src(ctx.pkg + ".container");
        s.imp("net.minecraft.client.gui.GuiGraphics",
                "net.minecraft.client.gui.screens.inventory.AbstractContainerScreen",
                "net.minecraft.network.chat.Component", "net.minecraft.world.entity.player.Inventory",
                "net.minecraft.world.inventory.Slot");
        s.l("public class " + scrCls + " extends AbstractContainerScreen<" + menuCls + "> {");
        s.l("public " + scrCls + "(" + menuCls + " menu, Inventory inv, Component title) {");
        s.l("super(menu, inv, title); imageHeight = " + h + "; inventoryLabelY = imageHeight - 94;");
        s.l("}");
        s.blank().l("@Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {");
        s.l("int x = (width - imageWidth) / 2, y = (height - imageHeight) / 2;");
        s.l("g.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);");
        s.l("g.renderOutline(x, y, imageWidth, imageHeight, 0xFF373737);");
        s.l("for (Slot sl : menu.slots) {");
        s.l("    int sx = x + sl.x - 1, sy = y + sl.y - 1;");
        s.l("    g.fill(sx, sy, sx + 18, sy + 18, 0xFF8B8B8B);");
        s.l("    g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF555555);");
        s.l("}");
        if (!ct.recipes.isEmpty()) {
            s.l("int prog = (menu.progress() * 24) / menu.maxProgress();");
            s.l("g.fill(x + 76, y + 36, x + 100, y + 44, 0xFF555555);");
            s.l("if (prog > 0) g.fill(x + 76, y + 36, x + 76 + Math.min(24, prog), y + 44, 0xFF4CAF50);");
        }
        s.l("}");
        s.blank().l("@Override public void render(GuiGraphics g, int mx, int my, float partial) {");
        s.l("super.render(g, mx, my, partial); renderTooltip(g, mx, my);");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "container/" + scrCls + ".java", s.toString());
    }

    // ------------------------------------------------------------------ custom entities & mobs

    private void entities() {
        Src s = new Src(ctx.pkg);
        if (fabric()) {
            s.imp("net.minecraft.core.Registry", "net.minecraft.core.registries.BuiltInRegistries",
                    "net.minecraft.world.entity.EntityType", "net.minecraft.world.entity.MobCategory");
        } else {
            s.imp("net.minecraft.world.entity.EntityType", "net.minecraft.world.entity.MobCategory",
                    neo() ? "net.minecraft.core.registries.Registries" : "net.minecraftforge.registries.ForgeRegistries",
                    neo() ? "net.neoforged.neoforge.registries.DeferredRegister" : "net.minecraftforge.registries.DeferredRegister",
                    neo() ? "net.neoforged.neoforge.registries.DeferredHolder" : "net.minecraftforge.registries.RegistryObject");
        }
        if (ctx.registryKeys()) s.imp("net.minecraft.core.registries.Registries", "net.minecraft.resources.ResourceKey");
        s.l("public final class ModEntities {");
        if (!fabric()) {
            s.l("public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create("
                    + (neo() ? "Registries.ENTITY_TYPE" : "ForgeRegistries.ENTITY_TYPES") + ", " + main() + ".MODID);").blank();
        }
        for (CustomEntity ce : ctx.all(CustomEntity.class)) {
            String pas = Ids.toPascal(ce.name) + "Entity";
            String c = Ids.toConst(ce.name);
            s.imp(ctx.pkg + ".entity." + pas);
            String cat = "MobCategory." + (ce.isProjectile() ? "MISC" : ce.category.toUpperCase());
            String builder = "EntityType.Builder.<" + pas + ">of(" + pas + "::new, " + cat + ").sized("
                    + Src.f(Math.max(0.2, ce.width)) + ", " + Src.f(Math.max(0.2, ce.height)) + ")";
            if (ce.fireImmune) builder += ".fireImmune()";
            String buildCall = ctx.registryKeys()
                    ? ".build(ResourceKey.create(Registries.ENTITY_TYPE, " + main() + ".id(" + Src.str(ce.name) + ")))"
                    : ".build(" + Src.str(ctx.id(ce.name)) + ")";
            if (fabric()) {
                s.l("public static final EntityType<" + pas + "> " + c + " = Registry.register(BuiltInRegistries.ENTITY_TYPE, "
                        + main() + ".id(" + Src.str(ce.name) + "), " + builder + buildCall + ");");
            } else {
                String t = neo() ? "DeferredHolder<EntityType<?>, EntityType<" + pas + ">>" : "RegistryObject<EntityType<" + pas + ">>";
                s.l("public static final " + t + " " + c + " = ENTITIES.register(" + Src.str(ce.name) + ", () -> " + builder + buildCall + ");");
            }
            entityClass(ce, pas, c);
            if (!ce.isProjectile() && ModelGen.entityModelSpec(ctx.p, ce) == null) entityRenderer(ce, pas);
        }
        s.blank().l("private ModEntities() {").l("}").blank().l("public static void init() {").l("}").l("}");
        ctx.files.text(ctx.javaDir() + "ModEntities.java", s.toString());
        entityBiomeSpawns();
    }

    private void entityClass(CustomEntity ce, String cls, String c) {
        Src s = new Src(ctx.pkg + ".entity");
        if (ce.isProjectile()) {
            String projPkg = ctx.v.atLeast(McVersion.V1_21_11)
                    ? "net.minecraft.world.entity.projectile.throwableitemprojectile." : "net.minecraft.world.entity.projectile.";
            s.imp(projPkg + "ThrowableItemProjectile", "net.minecraft.world.entity.EntityType",
                    "net.minecraft.world.entity.LivingEntity", "net.minecraft.world.item.Item",
                    "net.minecraft.world.item.Items", "net.minecraft.world.level.Level",
                    "net.minecraft.world.phys.EntityHitResult", "net.minecraft.world.phys.HitResult");
            if (ctx.registryKeys()) s.imp("net.minecraft.server.level.ServerLevel");
            if (!ce.onHurt.isEmpty()) s.imp(ctx.pkg + ".ModProcedures");
            s.l("public class " + cls + " extends ThrowableItemProjectile {");
            s.l("public " + cls + "(EntityType<? extends " + cls + "> type, Level level) { super(type, level); }");
            s.l("@Override protected Item getDefaultItem() { return Items.SNOWBALL; }");
            s.l("@Override protected void onHitEntity(EntityHitResult res) {");
            s.l("    super.onHitEntity(res);");
            if (ce.projectileDamage > 0) {
                if (ctx.registryKeys()) s.l("    if (level() instanceof ServerLevel sl) res.getEntity().hurtServer(sl, damageSources().thrown(this, getOwner()), " + Src.f(ce.projectileDamage) + ");");
                else s.l("    res.getEntity().hurt(damageSources().thrown(this, getOwner()), " + Src.f(ce.projectileDamage) + ");");
            }
            if (!ce.onHurt.isEmpty()) s.l("    if (!level().isClientSide()) ModProcedures.run(res.getEntity(), " + Src.str("entities/" + ce.name + "/on_hurt") + ");");
            s.l("}");
            s.l("@Override protected void onHit(HitResult res) { super.onHit(res); if (!level().isClientSide()) discard(); }");
            s.l("}");
            ctx.files.text(ctx.javaDir() + "entity/" + cls + ".java", s.toString());
            return;
        }
        String sup = ce.kind.equals("hostile") ? "Monster" : "PathfinderMob";
        s.imp("net.minecraft.world.entity.EntityType",
                "net.minecraft.world.entity.ai.attributes.AttributeSupplier",
                "net.minecraft.world.entity.ai.attributes.Attributes",
                ce.kind.equals("hostile") ? "net.minecraft.world.entity.monster.Monster" : "net.minecraft.world.entity.PathfinderMob",
                "net.minecraft.world.level.Level");
        if (!ce.aiGoals.isEmpty()) {
            if (ce.aiGoals.contains("float")) s.imp("net.minecraft.world.entity.ai.goal.FloatGoal");
            if (ce.aiGoals.contains("melee")) s.imp("net.minecraft.world.entity.ai.goal.MeleeAttackGoal");
            if (ce.aiGoals.contains("panic")) s.imp("net.minecraft.world.entity.ai.goal.PanicGoal");
            if (ce.aiGoals.contains("stroll")) {
                s.imp(ce.canFly ? "net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal"
                        : "net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal");
            }
            if (ce.aiGoals.contains("swim_random")) s.imp("net.minecraft.world.entity.ai.goal.RandomSwimmingGoal");
            if (ce.aiGoals.contains("look_player")) s.imp("net.minecraft.world.entity.ai.goal.LookAtPlayerGoal", "net.minecraft.world.entity.player.Player");
            if (ce.aiGoals.contains("random_look")) s.imp("net.minecraft.world.entity.ai.goal.RandomLookAroundGoal");
            if (ce.aiGoals.contains("attack_nearest_player")) s.imp("net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal", "net.minecraft.world.entity.player.Player");
        }
        if (ce.canFly) {
            s.imp("net.minecraft.world.entity.ai.control.FlyingMoveControl",
                    "net.minecraft.world.entity.ai.navigation.FlyingPathNavigation",
                    "net.minecraft.world.entity.ai.navigation.PathNavigation");
        }
        if (ce.rideable || ce.tameable || !ce.onInteract.isEmpty()) {
            s.imp("net.minecraft.world.InteractionHand", "net.minecraft.world.InteractionResult", "net.minecraft.world.entity.player.Player");
        }
        if (ce.tameable) {
            s.imp("java.util.UUID", "net.minecraft.world.item.ItemStack", ctx.pkg + ".ModProcedures");
            String tId = Ids.full(ce.tameItem == null || ce.tameItem.isBlank() ? "minecraft:bone" : ce.tameItem);
            if (ctx.p.itemLike(tId) != null) s.imp(ctx.pkg + ".ModItems");
        }
        if (ce.rideable) s.imp("net.minecraft.world.entity.LivingEntity", "net.minecraft.world.phys.Vec3");
        if (!ce.onSpawn.isEmpty() || !ce.onHurt.isEmpty() || !ce.onDeath.isEmpty() || !ce.onInteract.isEmpty()) s.imp(ctx.pkg + ".ModProcedures");
        boolean hasMain = ce.mainhand != null && !ce.mainhand.isBlank();
        boolean hasHead = ce.head != null && !ce.head.isBlank();
        if (hasMain || hasHead) s.imp("net.minecraft.world.entity.EquipmentSlot", "net.minecraft.world.item.ItemStack", ctx.pkg + ".ModItems", ctx.pkg + ".ModProcedures");
        if (ce.canSwim && !ce.aiGoals.contains("float")) s.imp("net.minecraft.world.entity.ai.goal.FloatGoal");
        if (ce.boss) s.imp("net.minecraft.server.level.ServerBossEvent", "net.minecraft.server.level.ServerPlayer", "net.minecraft.world.BossEvent");
        s.l("public class " + cls + " extends " + sup + " {");
        if (ce.boss) {
            String bc = List.of("pink", "blue", "red", "green", "yellow", "purple", "white").contains(ce.bossColor) ? ce.bossColor.toUpperCase(Locale.ROOT) : "PURPLE";
            String bs = switch (ce.bossStyle) {
                case "notched_6" -> "NOTCHED_6";
                case "notched_10" -> "NOTCHED_10";
                case "notched_12" -> "NOTCHED_12";
                case "notched_20" -> "NOTCHED_20";
                default -> "PROGRESS";
            };
            s.l("private final ServerBossEvent bossBar = new ServerBossEvent(this.getDisplayName(), BossEvent.BossBarColor." + bc + ", BossEvent.BossBarOverlay." + bs + ");");
        }
        s.l("public " + cls + "(EntityType<? extends " + cls + "> type, Level level) {");
        s.l("super(type, level);");
        if (ce.xpReward >= 0) s.l("this.xpReward = " + ce.xpReward + ";");
        if (ce.silent) s.l("setSilent(true);");
        if (ce.glowing) s.l("setGlowingTag(true);");
        if (ce.invulnerable) s.l("setInvulnerable(true);");
        if (ce.canFly) s.l("this.moveControl = new FlyingMoveControl(this, 20, true);");
        if (hasMain) s.l("if (!level.isClientSide()) { setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(" + jc.anyItemRef(ce.mainhand) + ")); setDropChance(EquipmentSlot.MAINHAND, 0.0F); }");
        if (hasHead) s.l("if (!level.isClientSide()) { setItemSlot(EquipmentSlot.HEAD, new ItemStack(" + jc.anyItemRef(ce.head) + ")); setDropChance(EquipmentSlot.HEAD, 0.0F); }");
        s.l("}");
        s.blank().l("public static AttributeSupplier.Builder createAttributes() {");
        s.l("return " + sup + ".createMobAttributes()");
        s.l("        .add(Attributes.MAX_HEALTH, " + Src.d(Math.max(1, ce.health)) + ")");
        s.l("        .add(Attributes.MOVEMENT_SPEED, " + Src.d(Math.max(0.05, ce.speed)) + ")");
        if (ce.canFly) s.l("        .add(Attributes.FLYING_SPEED, " + Src.d(Math.max(0.05, ce.speed * 1.6)) + ")");
        s.l("        .add(Attributes.ATTACK_DAMAGE, " + Src.d(Math.max(0, ce.attackDamage)) + ")");
        s.l("        .add(Attributes.FOLLOW_RANGE, " + Src.d(Math.max(1, ce.followRange)) + ")");
        if (ce.armor > 0) s.l("        .add(Attributes.ARMOR, " + Src.d(ce.armor) + ")");
        if (ctx.components() && Math.abs(ce.scale - 1.0) > 0.001) {
            s.l("        .add(Attributes.SCALE, " + Src.d(Math.max(0.2, Math.min(4.0, ce.scale))) + ")");
        }
        if (ctx.components() && Math.abs(ce.stepHeight - 0.6) > 0.05) {
            s.l("        .add(Attributes.STEP_HEIGHT, " + Src.d(Math.max(0.1, ce.stepHeight)) + ")");
        }
        s.l("        .add(Attributes.KNOCKBACK_RESISTANCE, " + Src.d(Math.max(0, Math.min(1, ce.knockbackResistance))) + ");");
        s.l("}");
        if (ce.fireImmune) s.blank().l("@Override public boolean fireImmune() { return true; }");
        if (ce.baby) s.blank().l("@Override public boolean isBaby() { return true; }");
        if (ce.boss) {
            s.blank().l("@Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bossBar.addPlayer(player); }");
            s.blank().l("@Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossBar.removePlayer(player); }");
            s.blank().l("@Override public void tick() { super.tick(); if (!level().isClientSide()) { bossBar.setName(this.getDisplayName()); bossBar.setProgress(this.getHealth() / this.getMaxHealth()); } }");
        }
        if (ce.canFly) {
            s.blank().l("@Override protected PathNavigation createNavigation(Level level) {");
            s.l("FlyingPathNavigation nav = new FlyingPathNavigation(this, level);");
            s.l("nav.setCanOpenDoors(false); nav.setCanFloat(true); return nav; }");
        }
        if (ce.canFly || ce.burnsInDaylight || ce.tameable) {
            s.blank().l("@Override public void aiStep() {");
            if (ce.canFly) s.l("if (!this.onGround()) this.resetFallDistance();");
            if (ce.burnsInDaylight) {
                s.l("if (this.isAlive() && !this.level().isClientSide() && this.isSunBurnTick()) { "
                        + (ctx.components() ? "this.igniteForSeconds(8.0F);" : "this.setSecondsOnFire(8);") + " }");
            }
            if (ce.tameable) {
                s.l("if (!this.level().isClientSide() && this.getTags().contains(\"modstudio_tamed\") && this.tickCount % 10 == 0) {");
                s.l("    for (String tag : this.getTags()) {");
                s.l("        if (!tag.startsWith(\"modstudio_owner:\")) continue;");
                s.l("        try {");
                s.l("            Player owner = this.level().getPlayerByUUID(UUID.fromString(tag.substring(16)));");
                s.l("            if (owner != null) {");
                s.l("                if (this.getTarget() == owner) this.setTarget(null);");
                s.l("                double d = this.distanceToSqr(owner);");
                s.l("                if (d > 36.0 && d < 1024.0) this.getNavigation().moveTo(owner, 1.15);");
                s.l("            }");
                s.l("        } catch (IllegalArgumentException ignored) {}");
                s.l("        break;");
                s.l("    }");
                s.l("}");
            }
            s.l("super.aiStep(); }");
        }
        s.blank().l("@Override protected void registerGoals() {");
        int p = 0;
        if (ce.aiGoals.contains("float") || ce.canSwim) s.l("goalSelector.addGoal(" + (p++) + ", new FloatGoal(this));");
        if (ce.aiGoals.contains("melee")) s.l("goalSelector.addGoal(" + (p++) + ", new MeleeAttackGoal(this, 1.2, false));");
        if (ce.aiGoals.contains("panic")) s.l("goalSelector.addGoal(" + (p++) + ", new PanicGoal(this, 1.4));");
        if (ce.aiGoals.contains("swim_random")) s.l("goalSelector.addGoal(" + (p++) + ", new RandomSwimmingGoal(this, 1.0, 10));");
        if (ce.aiGoals.contains("stroll")) {
            s.l("goalSelector.addGoal(" + (p++) + ", new "
                    + (ce.canFly ? "WaterAvoidingRandomFlyingGoal(this, 1.0));" : "WaterAvoidingRandomStrollGoal(this, 1.0));"));
        }
        if (ce.aiGoals.contains("look_player")) s.l("goalSelector.addGoal(" + (p++) + ", new LookAtPlayerGoal(this, Player.class, 8.0F));");
        if (ce.aiGoals.contains("random_look")) s.l("goalSelector.addGoal(" + (p++) + ", new RandomLookAroundGoal(this));");
        if (ce.aiGoals.contains("attack_nearest_player")) s.l("targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));");
        s.l("}");
        if (!ce.ambientSound.isBlank()) {
            s.imp("net.minecraft.sounds.SoundEvent");
            s.blank().l("@Override protected SoundEvent getAmbientSound() { return " + soundExpr(s, ce.ambientSound, "null") + "; }");
        }
        if (!ce.hurtSound.isBlank()) {
            s.imp("net.minecraft.sounds.SoundEvent", "net.minecraft.world.damagesource.DamageSource");
            s.blank().l("@Override protected SoundEvent getHurtSound(DamageSource source) { return " + soundExpr(s, ce.hurtSound, "super.getHurtSound(source)") + "; }");
        }
        if (!ce.deathSound.isBlank()) {
            s.imp("net.minecraft.sounds.SoundEvent");
            s.blank().l("@Override protected SoundEvent getDeathSound() { return " + soundExpr(s, ce.deathSound, "super.getDeathSound()") + "; }");
        }
        if (!ce.pushable) s.blank().l("@Override public boolean isPushable() { return false; }");
        if (!ce.onSpawn.isEmpty()) {
            s.blank().l("@Override public void onAddedToLevel() { super.onAddedToLevel(); if (!level().isClientSide()) ModProcedures.run(this, " + Src.str("entities/" + ce.name + "/on_spawn") + "); }");
        }
        if (!ce.onHurt.isEmpty()) {
            s.imp("net.minecraft.world.damagesource.DamageSource");
            if (ctx.registryKeys()) {
                s.imp("net.minecraft.server.level.ServerLevel");
                s.blank().l("@Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {");
                s.l("ModProcedures.run(this, " + Src.str("entities/" + ce.name + "/on_hurt") + ");");
                s.l("return super.hurtServer(level, source, amount); }");
            } else {
                s.blank().l("@Override public boolean hurt(DamageSource source, float amount) {");
                s.l("if (!level().isClientSide()) ModProcedures.run(this, " + Src.str("entities/" + ce.name + "/on_hurt") + ");");
                s.l("return super.hurt(source, amount); }");
            }
        }
        if (!ce.onDeath.isEmpty()) {
            s.imp("net.minecraft.world.damagesource.DamageSource");
            s.blank().l("@Override public void die(DamageSource source) { if (!level().isClientSide()) ModProcedures.run(this, "
                    + Src.str("entities/" + ce.name + "/on_death") + "); super.die(source); }");
        }
        if (ce.rideable || ce.tameable || !ce.onInteract.isEmpty()) {
            s.blank().l("@Override public InteractionResult mobInteract(Player player, InteractionHand hand) {");
            if (ce.tameable) {
                String tItem = ce.tameItem == null || ce.tameItem.isBlank() ? "minecraft:bone" : ce.tameItem;
                s.l("ItemStack stack = player.getItemInHand(hand);");
                s.l("if (!this.getTags().contains(\"modstudio_tamed\") && " + jc.stackMatches(tItem) + ") {");
                s.l("    if (!level().isClientSide()) {");
                s.l("        if (!player.getAbilities().instabuild) stack.shrink(1);");
                s.l("        this.addTag(\"modstudio_tamed\");");
                s.l("        this.addTag(\"modstudio_owner:\" + player.getUUID());");
                s.l("        this.heal(this.getMaxHealth());");
                s.l("        this.level().broadcastEntityEvent(this, (byte) 7);");
                s.l("    }");
                s.l("    return InteractionResult.SUCCESS;");
                s.l("}");
            }
            if (!ce.onInteract.isEmpty()) s.l("if (!level().isClientSide()) ModProcedures.run(player, " + Src.str("entities/" + ce.name + "/on_interact") + ");");
            if (ce.rideable) s.l("if (!level().isClientSide() && !isVehicle()) { player.startRiding(this); return InteractionResult.SUCCESS; }");
            s.l("return super.mobInteract(player, hand); }");
        }
        if (ce.rideable) {
            s.blank().l("@Override public LivingEntity getControllingPassenger() { return getFirstPassenger() instanceof LivingEntity le ? le : null; }");
            s.blank().l("@Override public void travel(Vec3 dir) {");
            s.l("LivingEntity driver = getControllingPassenger();");
            s.l("if (isVehicle() && driver != null) {");
            s.l("    setYRot(driver.getYRot()); yRotO = getYRot(); setXRot(driver.getXRot() * 0.5F); setRot(getYRot(), getXRot());");
            s.l("    yBodyRot = getYRot(); yHeadRot = yBodyRot;");
            s.l("    setSpeed((float) getAttributeValue(Attributes.MOVEMENT_SPEED));");
            s.l("    super.travel(new Vec3(driver.xxa * 0.5F, dir.y, driver.zza));");
            s.l("} else super.travel(dir);");
            s.l("}");
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "entity/" + cls + ".java", s.toString());
    }

    private void entityRenderer(CustomEntity ce, String entityCls) {
        String rCls = Ids.toPascal(ce.name) + "Renderer";
        String texExpr = ce.texture.isBlank()
                ? ctx.rlParseExpr("\"minecraft:textures/entity/zombie/zombie.png\"")
                : ctx.rlFrom(main() + ".MODID", Src.str("textures/entity/" + ce.name + ".png"));
        Src s = new Src(ctx.pkg + ".client");
        s.imp("net.minecraft.client.model.HumanoidModel", "net.minecraft.client.model.geom.ModelLayers",
                "net.minecraft.client.renderer.entity.EntityRendererProvider",
                "net.minecraft.client.renderer.entity.HumanoidMobRenderer",
                ctx.rlImport(),
                ctx.pkg + ".entity." + entityCls, ctx.pkg + "." + main());
        if (ctx.registryKeys()) {
            s.imp("net.minecraft.client.renderer.entity.state.HumanoidRenderState");
            s.l("public class " + rCls + " extends HumanoidMobRenderer<" + entityCls + ", HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {");
            s.l("private static final " + ctx.rlType() + " TEX = " + texExpr + ";");
            s.l("public " + rCls + "(EntityRendererProvider.Context ctx) { super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5F); }");
            s.l("@Override public HumanoidRenderState createRenderState() { return new HumanoidRenderState(); }");
            s.l("@Override public " + ctx.rlType() + " getTextureLocation(HumanoidRenderState state) { return TEX; }");
        } else {
            s.l("public class " + rCls + " extends HumanoidMobRenderer<" + entityCls + ", HumanoidModel<" + entityCls + ">> {");
            s.l("private static final " + ctx.rlType() + " TEX = " + texExpr + ";");
            s.l("public " + rCls + "(EntityRendererProvider.Context ctx) { super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5F); }");
            s.l("@Override public " + ctx.rlType() + " getTextureLocation(" + entityCls + " entity) { return TEX; }");
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "client/" + rCls + ".java", s.toString());
    }

    private void entityBiomeSpawns() {
        if (fabric()) return;
        String dir = neo() ? "neoforge/biome_modifier" : "forge/biome_modifier";
        for (CustomEntity ce : ctx.all(CustomEntity.class)) {
            if (!ce.naturalSpawn || ce.isProjectile()) continue;
            JsonObject sp = J.obj("type", ctx.id(ce.name), "weight", Math.max(1, ce.spawnWeight),
                    "minCount", Math.max(1, ce.spawnMin), "maxCount", Math.max(ce.spawnMin, ce.spawnMax));
            JsonObject mod = J.obj("type", neo() ? "neoforge:add_spawns" : "forge:add_spawns",
                    "biomes", ce.spawnBiomes.isBlank() ? "#minecraft:is_overworld" : Ids.full(ce.spawnBiomes),
                    "spawners", J.arr(sp));
            ctx.files.json(res + "data/" + ctx.ns + "/" + dir + "/spawn_" + ce.name + ".json", mod);
        }
        for (WorldFeature wf : ctx.all(WorldFeature.class)) {
            String modType = neo() ? "neoforge:add_features" : "forge:add_features";
            JsonObject mod = J.obj("type", modType,
                    "biomes", wf.biomes.isBlank() ? "#minecraft:is_overworld" : Ids.full(wf.biomes),
                    "features", ctx.id(wf.name),
                    "step", wf.step);
            ctx.files.json(res + "data/" + ctx.ns + "/" + dir + "/feature_" + wf.name + ".json", mod);
        }
    }

    // ------------------------------------------------------------------ portals

    private void portals() {
        for (Portal pt : ctx.all(Portal.class)) {
            String blkCls = Ids.toPascal(pt.name) + "PortalBlock";
            String ignCls = Ids.toPascal(pt.name) + "IgniterItem";
            String dim = Ids.full(pt.targetDimension.isBlank() ? "minecraft:the_nether" : pt.targetDimension);
            String frame = Ids.full(pt.frameBlock.isBlank() ? "minecraft:obsidian" : pt.frameBlock);
            String pbRef = jc.blockRef(pt.name + "_portal");
            Src s = new Src(ctx.pkg + ".block");
            s.imp("net.minecraft.core.BlockPos", "net.minecraft.core.Direction",
                    "net.minecraft.core.particles.ParticleTypes",
                    "net.minecraft.core.registries.BuiltInRegistries",
                    "net.minecraft.core.registries.Registries", "net.minecraft.resources.ResourceKey",
                    ctx.rlImport(), "net.minecraft.sounds.SoundEvents", "net.minecraft.sounds.SoundSource",
                    "net.minecraft.server.level.ServerLevel", "net.minecraft.server.level.ServerPlayer",
                    "net.minecraft.util.RandomSource",
                    "net.minecraft.world.entity.Entity", "net.minecraft.world.level.Level",
                    "net.minecraft.world.level.block.Block", "net.minecraft.world.level.block.state.BlockBehaviour",
                    "net.minecraft.world.level.block.state.BlockState",
                    "net.minecraft.world.level.block.state.StateDefinition",
                    "net.minecraft.world.level.block.state.properties.BlockStateProperties",
                    "net.minecraft.world.level.block.state.properties.EnumProperty",
                    ctx.pkg + ".ModBlocks");
            if (ctx.v.atLeast(McVersion.V1_21_8)) s.imp("net.minecraft.world.entity.InsideBlockEffectApplier");
            if (ctx.registryKeys()) s.imp("net.minecraft.world.level.portal.TeleportTransition", "net.minecraft.world.phys.Vec3", "java.util.Set");
            else if (ctx.singular()) s.imp("net.minecraft.world.level.portal.DimensionTransition", "net.minecraft.world.phys.Vec3");
            s.l("public class " + blkCls + " extends Block {");
            s.l("public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;");
            s.l("private static final ResourceKey<Level> TARGET_DIM = ResourceKey.create(Registries.DIMENSION, " + ctx.rlParseExpr(Src.str(dim)) + ");");
            s.l("public " + blkCls + "(BlockBehaviour.Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X)); }");
            s.l("@Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AXIS); }");
            s.blank().l("public static boolean tryIgnite(Level level, BlockPos pos, Direction face) {");
            s.l("    Block frame = BuiltInRegistries.BLOCK." + (ctx.registryKeys() ? "getValue(" : "get(") + ctx.rlParseExpr(Src.str(frame)) + ");");
            s.l("    if (!level.getBlockState(pos).is(frame)) return false;");
            s.l("    BlockPos target = pos.relative(face);");
            s.l("    if (!level.isEmptyBlock(target)) return false;");
            s.l("    if (!level.isClientSide()) {");
            s.l("        Direction.Axis ax = (face.getAxis() == Direction.Axis.X) ? Direction.Axis.Z : Direction.Axis.X;");
            s.l("        for (int dy = 0; dy < 3; dy++) level.setBlock(target.above(dy), " + pbRef + ".defaultBlockState().setValue(AXIS, ax), 3);");
            s.l("        level.playSound(null, target, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);");
            s.l("    }");
            s.l("    return true;");
            s.l("}");
            s.blank().l("@Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {");
            s.l("    if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.PORTAL, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), (random.nextDouble() - 0.5) * 0.5, -random.nextDouble(), (random.nextDouble() - 0.5) * 0.5);");
            s.l("}");
            if (ctx.v.atLeast(McVersion.V1_21_8)) {
                s.l("@Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier applier, boolean precise) { teleportEntity(level, pos, entity); }");
            } else {
                s.l("@Override public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) { teleportEntity(level, pos, entity); }");
            }
            s.l("private void teleportEntity(Level level, BlockPos pos, Entity entity) {");
            s.l("    if (!(level instanceof ServerLevel sl) || entity.isOnPortalCooldown()) return;");
            s.l("    ResourceKey<Level> destKey = level.dimension() == TARGET_DIM ? Level.OVERWORLD : TARGET_DIM;");
            s.l("    ServerLevel dest = sl.getServer().getLevel(destKey); if (dest == null) return;");
            s.l("    entity.setPortalCooldown(60);");
            if (ctx.registryKeys()) {
                s.l("    Vec3 target = Vec3.atBottomCenterOf(pos);");
                s.l("    entity.teleport(new TeleportTransition(dest, target, Vec3.ZERO, entity.getYRot(), entity.getXRot(), Set.of(), TeleportTransition.DO_NOTHING));");
            } else if (ctx.singular()) {
                s.l("    entity.changeDimension(new DimensionTransition(dest, Vec3.atBottomCenterOf(pos), Vec3.ZERO, entity.getYRot(), entity.getXRot(), DimensionTransition.DO_NOTHING));");
            } else {
                s.l("    if (entity instanceof ServerPlayer sp) sp.teleportTo(dest, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, entity.getYRot(), entity.getXRot());");
                s.l("    else entity.changeDimension(dest);");
            }
            s.l("}");
            s.l("}");
            ctx.files.text(ctx.javaDir() + "block/" + blkCls + ".java", s.toString());

            if (pt.generateIgniter) {
                Src ig = new Src(ctx.pkg + ".item");
                ig.imp(ctx.rlImport(), "net.minecraft.core.BlockPos", "net.minecraft.core.registries.BuiltInRegistries",
                        "net.minecraft.world.InteractionResult", "net.minecraft.world.item.Item",
                        "net.minecraft.world.item.context.UseOnContext", "net.minecraft.world.level.Level",
                        ctx.pkg + "." + main(), ctx.pkg + ".ModBlocks", ctx.pkg + ".block." + blkCls);
                ig.l("public class " + ignCls + " extends Item {");
                ig.l("public " + ignCls + "(Item.Properties properties) { super(properties); }");
                ig.l("@Override public InteractionResult useOn(UseOnContext ctx) {");
                ig.l("    Level level = ctx.getLevel();");
                ig.l("    BlockPos pos = ctx.getClickedPos();");
                ig.l("    " + ctx.rlType() + " portalId = " + main() + ".id(" + Src.str(pt.name + "_portal") + ");");
                ig.l("    if (level.getBlockState(pos).is(" + pbRef + ") || !BuiltInRegistries.BLOCK.containsKey(portalId)) return InteractionResult.PASS;");
                ig.l("    return " + blkCls + ".tryIgnite(level, pos, ctx.getClickedFace()) ? InteractionResult.SUCCESS : InteractionResult.PASS;");
                ig.l("}");
                ig.l("}");
                ctx.files.text(ctx.javaDir() + "item/" + ignCls + ".java", ig.toString());
            }
        }
    }

    // ------------------------------------------------------------------ villager professions

    private void professions() {
        Src s = new Src(ctx.pkg);
        s.imp("com.google.common.collect.ImmutableSet", "net.minecraft.sounds.SoundEvents",
                "net.minecraft.world.entity.ai.village.poi.PoiType",
                "net.minecraft.world.entity.npc.VillagerProfession");
        if (fabric()) {
            s.imp("net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper",
                    "net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper",
                    "net.minecraft.core.Registry", "net.minecraft.core.registries.BuiltInRegistries",
                    "net.minecraft.core.registries.Registries", "net.minecraft.resources.ResourceKey",
                    "net.minecraft.world.item.ItemStack", "net.minecraft.world.item.trading.MerchantOffer");
            if (ctx.components()) s.imp("net.minecraft.world.item.trading.ItemCost");
        } else {
            s.imp("net.minecraft.core.registries.Registries",
                    neo() ? "net.neoforged.neoforge.registries.DeferredRegister" : "net.minecraftforge.registries.DeferredRegister",
                    neo() ? "net.neoforged.neoforge.registries.DeferredHolder" : "net.minecraftforge.registries.RegistryObject");
        }
        s.l("public final class ModProfessions {");
        if (!fabric()) {
            s.l("public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, " + main() + ".MODID);");
            s.l("public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, " + main() + ".MODID);");
            s.blank();
        }
        for (Profession pf : ctx.all(Profession.class)) {
            String c = Ids.toConst(pf.name);
            String blkExpr = stationExpr(s, pf.workstationBlock);
            String workSnd = soundExpr(s, pf.workSound, "SoundEvents.VILLAGER_WORK_TOOLSMITH");
            if (fabric()) {
                s.l("public static final PoiType " + c + "_POI = PointOfInterestHelper.register(" + main() + ".id(" + Src.str(pf.name + "_poi") + "), 1, 1, " + blkExpr + ");");
                s.l("private static final ResourceKey<PoiType> " + c + "_POI_KEY = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, " + main() + ".id(" + Src.str(pf.name + "_poi") + "));");
                s.l("public static final VillagerProfession " + c + " = Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, " + main() + ".id(" + Src.str(pf.name) + "),");
                s.l("    new VillagerProfession(" + Src.str(pf.name) + ", h -> h.is(" + c + "_POI_KEY), h -> h.is(" + c + "_POI_KEY), ImmutableSet.of(), ImmutableSet.of(), " + workSnd + "));");
            } else {
                String pT = neo() ? "DeferredHolder<PoiType, PoiType>" : "RegistryObject<PoiType>";
                String vT = neo() ? "DeferredHolder<VillagerProfession, VillagerProfession>" : "RegistryObject<VillagerProfession>";
                s.l("public static final " + pT + " " + c + "_POI = POI_TYPES.register(" + Src.str(pf.name + "_poi")
                        + ", () -> new PoiType(ImmutableSet.copyOf(" + blkExpr + ".getStateDefinition().getPossibleStates()), 1, 1));");
                s.l("public static final " + vT + " " + c + " = PROFESSIONS.register(" + Src.str(pf.name)
                        + ", () -> new VillagerProfession(" + Src.str(pf.name) + ", h -> h.value() == " + c + "_POI.get(), h -> h.value() == "
                        + c + "_POI.get(), ImmutableSet.of(), ImmutableSet.of(), " + workSnd + "));");
            }
        }
        s.blank().l("private ModProfessions() {").l("}").blank().l("public static void init() {");
        if (fabric()) {
            for (Profession pf : ctx.all(Profession.class)) {
                String c = Ids.toConst(pf.name);
                for (ProfessionTrade tr : pf.trades) {
                    if (tr.costA.isBlank() || tr.result.isBlank()) continue;
                    s.l("TradeOfferHelper.registerVillagerOffers(" + c + ", " + Math.max(1, Math.min(5, tr.level))
                            + ", f -> f.add((e, r) -> " + merchantOfferExpr(s, ctx, jc, tr) + "));");
                }
            }
        }
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModProfessions.java", s.toString());
    }

    static String merchantOfferExpr(Src s, GenContext ctx, JavaCommon jc, ProfessionTrade tr) {
        boolean hasB = tr.costB != null && !tr.costB.isBlank() && tr.countB > 0;
        String resStack = "new ItemStack(" + jc.anyItemRef(tr.result) + ", " + Math.max(1, tr.resultCount) + ")";
        if (ctx.components()) {
            s.imp("net.minecraft.world.item.trading.ItemCost", "java.util.Optional");
            String costA = "new ItemCost(" + jc.anyItemRef(tr.costA) + ", " + Math.max(1, tr.countA) + ")";
            String costB = hasB ? "Optional.of(new ItemCost(" + jc.anyItemRef(tr.costB) + ", " + tr.countB + "))" : "Optional.empty()";
            return "new MerchantOffer(" + costA + ", " + costB + ", " + resStack + ", " + Math.max(1, tr.maxUses) + ", " + Math.max(0, tr.xp) + ", 0.05F)";
        }
        String costA = "new ItemStack(" + jc.anyItemRef(tr.costA) + ", " + Math.max(1, tr.countA) + ")";
        if (hasB) {
            String costB = "new ItemStack(" + jc.anyItemRef(tr.costB) + ", " + tr.countB + ")";
            return "new MerchantOffer(" + costA + ", " + costB + ", " + resStack + ", " + Math.max(1, tr.maxUses) + ", " + Math.max(0, tr.xp) + ", 0.05F)";
        }
        return "new MerchantOffer(" + costA + ", " + resStack + ", " + Math.max(1, tr.maxUses) + ", " + Math.max(0, tr.xp) + ", 0.05F)";
    }

    private String soundExpr(Src s, String rawId, String fallback) {
        if (rawId == null || rawId.isBlank()) return fallback;
        String full = Ids.full(rawId.trim());
        String path = Ids.path(full);
        if (Ids.ns(full).equals(ctx.ns) && ctx.all(Elements.Sound.class).stream().anyMatch(se -> se.name.equals(path))) {
            s.imp(ctx.pkg + ".ModSounds");
            return fabric() ? "ModSounds." + Ids.toConst(path) : "ModSounds." + Ids.toConst(path) + ".get()";
        }
        s.imp("net.minecraft.core.registries.BuiltInRegistries");
        return "BuiltInRegistries.SOUND_EVENT." + (ctx.registryKeys() ? "getValue(" : "get(") + ctx.rlParseExpr(Src.str(full)) + ")";
    }

    private String stationExpr(Src s, String id) {
        String full = Ids.full(id.isBlank() ? "minecraft:crafting_table" : id);
        if (Ids.ns(full).equals(ctx.ns)) return jc.blockRef(Ids.path(full));
        s.imp("net.minecraft.core.registries.BuiltInRegistries");
        return "BuiltInRegistries.BLOCK." + (ctx.registryKeys() ? "getValue(" : "get(") + ctx.rlParseExpr(Src.str(full)) + ")";
    }

    // ------------------------------------------------------------------ custom recipe types

    private void recipeTypes() {
        Src s = new Src(ctx.pkg);
        s.imp("net.minecraft.world.item.crafting.RecipeSerializer", "net.minecraft.world.item.crafting.RecipeType");
        if (fabric()) {
            s.imp("net.minecraft.core.Registry", "net.minecraft.core.registries.BuiltInRegistries");
        } else {
            s.imp("net.minecraft.core.registries.Registries",
                    neo() ? "net.neoforged.neoforge.registries.DeferredRegister" : "net.minecraftforge.registries.DeferredRegister",
                    neo() ? "net.neoforged.neoforge.registries.DeferredHolder" : "net.minecraftforge.registries.RegistryObject");
        }
        s.l("public final class ModRecipeTypes {");
        if (!fabric()) {
            s.l("public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, " + main() + ".MODID);");
            s.l("public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, " + main() + ".MODID);").blank();
        }
        for (CustomRecipeType rt : ctx.all(CustomRecipeType.class)) {
            String pas = Ids.toPascal(rt.name) + "Recipe";
            String c = Ids.toConst(rt.name);
            s.imp(ctx.pkg + ".recipe." + pas);
            if (fabric()) {
                s.l("public static final RecipeType<" + pas + "> " + c + "_TYPE = Registry.register(BuiltInRegistries.RECIPE_TYPE, "
                        + main() + ".id(" + Src.str(rt.name) + "), new RecipeType<" + pas + ">() {});");
                s.l("public static final RecipeSerializer<" + pas + "> " + c + "_SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, "
                        + main() + ".id(" + Src.str(rt.name) + "), new " + pas + ".Serializer());");
            } else {
                String rtT = neo() ? "DeferredHolder<RecipeType<?>, RecipeType<" + pas + ">>" : "RegistryObject<RecipeType<" + pas + ">>";
                String serT = neo() ? "DeferredHolder<RecipeSerializer<?>, RecipeSerializer<" + pas + ">>" : "RegistryObject<RecipeSerializer<" + pas + ">>";
                s.l("public static final " + rtT + " " + c + "_TYPE = TYPES.register(" + Src.str(rt.name) + ", () -> new RecipeType<" + pas + ">() {});");
                s.l("public static final " + serT + " " + c + "_SERIALIZER = SERIALIZERS.register(" + Src.str(rt.name) + ", () -> new " + pas + ".Serializer());");
            }
            recipeTypeClass(rt, pas, c);
        }
        s.blank().l("private ModRecipeTypes() {").l("}").blank().l("public static void init() {").l("}").l("}");
        ctx.files.text(ctx.javaDir() + "ModRecipeTypes.java", s.toString());
    }

    private void recipeTypeClass(CustomRecipeType rt, String cls, String c) {
        Src s = new Src(ctx.pkg + ".recipe");
        s.imp("net.minecraft.world.item.ItemStack", "net.minecraft.world.item.crafting.Ingredient",
                "net.minecraft.world.item.crafting.Recipe", "net.minecraft.world.item.crafting.RecipeSerializer",
                "net.minecraft.world.item.crafting.RecipeType", "net.minecraft.world.level.Level",
                ctx.pkg + ".ModRecipeTypes");
        String typeRef = fabric() ? "ModRecipeTypes." + c + "_TYPE" : "ModRecipeTypes." + c + "_TYPE.get()";
        String serRef = fabric() ? "ModRecipeTypes." + c + "_SERIALIZER" : "ModRecipeTypes." + c + "_SERIALIZER.get()";
        boolean multi = rt.maxInputs > 1;
        if (ctx.singular()) {
            s.imp("com.mojang.serialization.Codec", "com.mojang.serialization.MapCodec", "com.mojang.serialization.codecs.RecordCodecBuilder",
                    "net.minecraft.core.HolderLookup", "net.minecraft.network.RegistryFriendlyByteBuf",
                    "net.minecraft.network.codec.StreamCodec");
            if (multi) {
                s.imp("java.util.List", "net.minecraft.network.codec.ByteBufCodecs", "net.minecraft.world.item.crafting.RecipeInput");
                s.l("public record " + cls + "(List<Ingredient> ingredients, ItemStack result, int processTime) implements Recipe<" + cls + ".Input> {");
                s.l("public record Input(List<ItemStack> items) implements RecipeInput {");
                s.l("    @Override public ItemStack getItem(int idx) { return idx >= 0 && idx < items.size() ? items.get(idx) : ItemStack.EMPTY; }");
                s.l("    @Override public int size() { return items.size(); }");
                s.l("}");
                s.l("@Override public boolean matches(Input in, Level level) {");
                s.l("    if (ingredients.size() > in.size()) return false;");
                s.l("    for (int i = 0; i < ingredients.size(); i++) if (!ingredients.get(i).test(in.getItem(i))) return false;");
                s.l("    return true;");
                s.l("}");
                s.l("@Override public ItemStack assemble(Input in, HolderLookup.Provider reg) { return result.copy(); }");
                if (ctx.registryKeys()) {
                    s.imp("net.minecraft.world.item.crafting.PlacementInfo", "net.minecraft.world.item.crafting.RecipeBookCategories", "net.minecraft.world.item.crafting.RecipeBookCategory");
                    s.l("@Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }");
                    s.l("@Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }");
                } else {
                    s.l("@Override public boolean canCraftInDimensions(int w, int h) { return true; }");
                    s.l("@Override public ItemStack getResultItem(HolderLookup.Provider reg) { return result; }");
                }
                s.l("@Override public RecipeType<" + (ctx.registryKeys() ? "? extends Recipe<Input>" : "?") + "> getType() { return " + typeRef + "; }");
                s.l("@Override public RecipeSerializer<" + (ctx.registryKeys() ? "? extends Recipe<Input>" : "?") + "> getSerializer() { return " + serRef + "; }");
                s.blank().l("public static final class Serializer implements RecipeSerializer<" + cls + "> {");
                String ingCodec = ctx.registryKeys() ? "Ingredient.CODEC" : "Ingredient.CODEC_NONEMPTY";
                s.l("public static final MapCodec<" + cls + "> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(");
                s.l("    " + ingCodec + ".listOf().fieldOf(\"ingredients\").forGetter(" + cls + "::ingredients),");
                s.l("    ItemStack.STRICT_CODEC.fieldOf(\"result\").forGetter(" + cls + "::result),");
                s.l("    Codec.INT.optionalFieldOf(\"process_time\", 100).forGetter(" + cls + "::processTime)).apply(i, " + cls + "::new));");
                s.l("public static final StreamCodec<RegistryFriendlyByteBuf, " + cls + "> STREAM = StreamCodec.composite(");
                s.l("    Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), " + cls + "::ingredients,");
                s.l("    ItemStack.STREAM_CODEC, " + cls + "::result, ByteBufCodecs.VAR_INT, " + cls + "::processTime, " + cls + "::new);");
                s.l("@Override public MapCodec<" + cls + "> codec() { return CODEC; }");
                s.l("@Override public StreamCodec<RegistryFriendlyByteBuf, " + cls + "> streamCodec() { return STREAM; }");
                s.l("}");
            } else {
                s.imp("com.mojang.serialization.Codec", "net.minecraft.network.codec.ByteBufCodecs", "net.minecraft.world.item.crafting.SingleRecipeInput");
                s.l("public record " + cls + "(Ingredient ingredient, ItemStack result, int processTime) implements Recipe<SingleRecipeInput> {");
                s.l("@Override public boolean matches(SingleRecipeInput in, Level level) { return ingredient.test(in.item()); }");
                s.l("@Override public ItemStack assemble(SingleRecipeInput in, HolderLookup.Provider reg) { return result.copy(); }");
                if (ctx.registryKeys()) {
                    s.imp("net.minecraft.world.item.crafting.PlacementInfo", "net.minecraft.world.item.crafting.RecipeBookCategories", "net.minecraft.world.item.crafting.RecipeBookCategory");
                    s.l("@Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }");
                    s.l("@Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }");
                } else {
                    s.l("@Override public boolean canCraftInDimensions(int w, int h) { return true; }");
                    s.l("@Override public ItemStack getResultItem(HolderLookup.Provider reg) { return result; }");
                }
                s.l("@Override public RecipeType<" + (ctx.registryKeys() ? "? extends Recipe<SingleRecipeInput>" : "?") + "> getType() { return " + typeRef + "; }");
                s.l("@Override public RecipeSerializer<" + (ctx.registryKeys() ? "? extends Recipe<SingleRecipeInput>" : "?") + "> getSerializer() { return " + serRef + "; }");
                s.blank().l("public static final class Serializer implements RecipeSerializer<" + cls + "> {");
                String ingCodec = ctx.registryKeys() ? "Ingredient.CODEC" : "Ingredient.CODEC_NONEMPTY";
                s.l("public static final MapCodec<" + cls + "> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(");
                s.l("    " + ingCodec + ".fieldOf(\"ingredient\").forGetter(" + cls + "::ingredient),");
                s.l("    ItemStack.STRICT_CODEC.fieldOf(\"result\").forGetter(" + cls + "::result),");
                s.l("    Codec.INT.optionalFieldOf(\"process_time\", 100).forGetter(" + cls + "::processTime)).apply(i, " + cls + "::new));");
                s.l("public static final StreamCodec<RegistryFriendlyByteBuf, " + cls + "> STREAM = StreamCodec.composite(");
                s.l("    Ingredient.CONTENTS_STREAM_CODEC, " + cls + "::ingredient, ItemStack.STREAM_CODEC, " + cls + "::result, ByteBufCodecs.VAR_INT, " + cls + "::processTime, " + cls + "::new);");
                s.l("@Override public MapCodec<" + cls + "> codec() { return CODEC; }");
                s.l("@Override public StreamCodec<RegistryFriendlyByteBuf, " + cls + "> streamCodec() { return STREAM; }");
                s.l("}");
            }
        } else if (ctx.components()) {
            // 1.20.5/1.20.6: MapCodec + StreamCodec with Container input
            s.imp("com.mojang.serialization.Codec", "com.mojang.serialization.MapCodec", "com.mojang.serialization.codecs.RecordCodecBuilder",
                    "net.minecraft.core.HolderLookup", "net.minecraft.network.RegistryFriendlyByteBuf",
                    "net.minecraft.network.codec.ByteBufCodecs", "net.minecraft.network.codec.StreamCodec",
                    "net.minecraft.world.Container");
            s.l("public record " + cls + "(Ingredient ingredient, ItemStack result, int processTime) implements Recipe<Container> {");
            s.l("@Override public boolean matches(Container in, Level level) { return !in.isEmpty() && ingredient.test(in.getItem(0)); }");
            s.l("@Override public ItemStack assemble(Container in, HolderLookup.Provider reg) { return result.copy(); }");
            s.l("@Override public boolean canCraftInDimensions(int w, int h) { return true; }");
            s.l("@Override public ItemStack getResultItem(HolderLookup.Provider reg) { return result; }");
            s.l("@Override public RecipeType<?> getType() { return " + typeRef + "; }");
            s.l("@Override public RecipeSerializer<?> getSerializer() { return " + serRef + "; }");
            s.blank().l("public static final class Serializer implements RecipeSerializer<" + cls + "> {");
            s.l("public static final MapCodec<" + cls + "> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(");
            s.l("    Ingredient.CODEC_NONEMPTY.fieldOf(\"ingredient\").forGetter(" + cls + "::ingredient),");
            s.l("    ItemStack.STRICT_CODEC.fieldOf(\"result\").forGetter(" + cls + "::result),");
            s.l("    Codec.INT.optionalFieldOf(\"process_time\", 100).forGetter(" + cls + "::processTime)).apply(i, " + cls + "::new));");
            s.l("public static final StreamCodec<RegistryFriendlyByteBuf, " + cls + "> STREAM = StreamCodec.composite(");
            s.l("    Ingredient.CONTENTS_STREAM_CODEC, " + cls + "::ingredient, ItemStack.STREAM_CODEC, " + cls + "::result, ByteBufCodecs.VAR_INT, " + cls + "::processTime, " + cls + "::new);");
            s.l("@Override public MapCodec<" + cls + "> codec() { return CODEC; }");
            s.l("@Override public StreamCodec<RegistryFriendlyByteBuf, " + cls + "> streamCodec() { return STREAM; }");
            s.l("}");
        } else {
            s.imp("com.google.gson.JsonObject", "net.minecraft.network.FriendlyByteBuf",
                    ctx.rlImport(), "net.minecraft.util.GsonHelper",
                    "net.minecraft.world.Container", "net.minecraft.world.item.crafting.ShapedRecipe",
                    "net.minecraft.core.RegistryAccess");
            String rl = ctx.rlType();
            s.l("public final class " + cls + " implements Recipe<Container> {");
            s.l("private final " + rl + " id; private final Ingredient ingredient; private final ItemStack result; private final int processTime;");
            s.l("public " + cls + "(" + rl + " id, Ingredient ingredient, ItemStack result, int processTime) { this.id = id; this.ingredient = ingredient; this.result = result; this.processTime = processTime; }");
            s.l("@Override public boolean matches(Container in, Level level) { return !in.isEmpty() && ingredient.test(in.getItem(0)); }");
            s.l("@Override public ItemStack assemble(Container in, RegistryAccess reg) { return result.copy(); }");
            s.l("@Override public boolean canCraftInDimensions(int w, int h) { return true; }");
            s.l("@Override public ItemStack getResultItem(RegistryAccess reg) { return result; }");
            s.l("@Override public " + rl + " getId() { return id; }");
            s.l("public int processTime() { return processTime; }");
            s.l("@Override public RecipeType<?> getType() { return " + typeRef + "; }");
            s.l("@Override public RecipeSerializer<?> getSerializer() { return " + serRef + "; }");
            s.blank().l("public static final class Serializer implements RecipeSerializer<" + cls + "> {");
            s.l("@Override public " + cls + " fromJson(" + rl + " id, JsonObject json) {");
            s.l("    Ingredient ing = json.has(\"ingredient\") ? Ingredient.fromJson(json.get(\"ingredient\"), false) : Ingredient.EMPTY;");
            s.l("    ItemStack res = json.has(\"result\") && json.get(\"result\").isJsonObject() ? ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, \"result\")) : ItemStack.EMPTY;");
            s.l("    return new " + cls + "(id, ing, res, GsonHelper.getAsInt(json, \"process_time\", 100));");
            s.l("}");
            s.l("@Override public " + cls + " fromNetwork(" + rl + " id, FriendlyByteBuf buf) { return new " + cls + "(id, Ingredient.fromNetwork(buf), buf.readItem(), buf.readVarInt()); }");
            s.l("@Override public void toNetwork(FriendlyByteBuf buf, " + cls + " r) { r.ingredient.toNetwork(buf); buf.writeItem(r.result); buf.writeVarInt(r.processTime); }");
            s.l("}");
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "recipe/" + cls + ".java", s.toString());
    }

    // ------------------------------------------------------------------ client screen & entity renderer registration

    private void clientHooks(boolean forgeEventBus7) {
        ModelGen mg = new ModelGen(ctx, loader);
        Src s = new Src(ctx.pkg);
        s.l("/** Client registrations for container screens, custom entity renderers, and 3D armor/entity models. */");
        s.l("public final class ModClientRegistrations {");
        s.l("private ModClientRegistrations() {").l("}").blank();
        if (fabric()) {
            s.l("public static void init() {");
            emitClientBody(s, true, mg);
            mg.emitArmorRegistrations(s, true, false);
            s.l("}");
        } else if (neo()) {
            s.imp("net.neoforged.bus.api.IEventBus", "net.neoforged.fml.event.lifecycle.FMLClientSetupEvent");
            s.l("public static void init(IEventBus modBus) {");
            if (hasContainers() && ctx.components()) {
                s.imp("net.neoforged.neoforge.client.event.RegisterMenuScreensEvent");
                s.l("modBus.addListener((RegisterMenuScreensEvent ev) -> {");
                for (Container ct : ctx.all(Container.class)) {
                    String pas = Ids.toPascal(ct.name);
                    s.imp(ctx.pkg + ".container." + pas + "Screen");
                    s.l("    ev.register(ModContainerMenus." + Ids.toConst(ct.name) + ".get(), " + pas + "Screen::new);");
                }
                s.l("});");
            } else if (hasContainers()) {
                s.l("modBus.addListener((FMLClientSetupEvent ev) -> ev.enqueueWork(() -> {");
                emitMenuScreens(s);
                s.l("}));");
            }
            mg.emitLayerRegistrations(s, false, true, false);
            if (hasEntities()) {
                s.imp("net.neoforged.neoforge.client.event.EntityRenderersEvent");
                s.l("modBus.addListener((EntityRenderersEvent.RegisterRenderers ev) -> {");
                emitEntityRenderers(s, "ev.registerEntityRenderer");
                s.l("});");
            }
            mg.emitArmorRegistrations(s, false, true);
            s.l("}");
        } else {
            s.imp(forgeEventBus7 ? "net.minecraftforge.eventbus.api.bus.BusGroup" : "net.minecraftforge.eventbus.api.IEventBus",
                    "net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent");
            s.l("public static void init(" + (forgeEventBus7 ? "BusGroup" : "IEventBus") + " modBus) {");
            mg.emitLayerRegistrations(s, false, false, forgeEventBus7);
            if (forgeEventBus7) {
                s.l("FMLClientSetupEvent.getBus(modBus).addListener(ev -> ev.enqueueWork(() -> {");
            } else {
                s.l("modBus.addListener((FMLClientSetupEvent ev) -> ev.enqueueWork(() -> {");
            }
            emitClientBody(s, false, mg);
            s.l("}));");
            s.l("}");
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModClientRegistrations.java", s.toString());
    }

    private void emitClientBody(Src s, boolean isFabric, ModelGen mg) {
        if (hasContainers()) emitMenuScreens(s);
        if (isFabric) mg.emitLayerRegistrations(s, true, false, false);
        if (hasEntities()) {
            if (isFabric) s.imp("net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry");
            else s.imp("net.minecraft.client.renderer.entity.EntityRenderers");
            emitEntityRenderers(s, isFabric ? "EntityRendererRegistry.register" : "EntityRenderers.register");
        }
    }

    private void emitMenuScreens(Src s) {
        s.imp("net.minecraft.client.gui.screens.MenuScreens");
        for (Container ct : ctx.all(Container.class)) {
            String pas = Ids.toPascal(ct.name);
            s.imp(ctx.pkg + ".container." + pas + "Screen");
            String ref = fabric() ? "ModContainerMenus." + Ids.toConst(ct.name) : "ModContainerMenus." + Ids.toConst(ct.name) + ".get()";
            s.l("MenuScreens.register(" + ref + ", " + pas + "Screen::new);");
        }
    }

    private void emitEntityRenderers(Src s, String call) {
        for (CustomEntity ce : ctx.all(CustomEntity.class)) {
            String ref = fabric() ? "ModEntities." + Ids.toConst(ce.name) : "ModEntities." + Ids.toConst(ce.name) + ".get()";
            if (ce.isProjectile() && ModelGen.entityModelSpec(ctx.p, ce) == null) {
                s.imp("net.minecraft.client.renderer.entity.ThrownItemRenderer");
                s.l(call + "(" + ref + ", ThrownItemRenderer::new);");
            } else {
                String rCls = Ids.toPascal(ce.name) + "Renderer";
                s.imp(ctx.pkg + ".client." + rCls);
                s.l(call + "(" + ref + ", " + rCls + "::new);");
            }
        }
    }
}

