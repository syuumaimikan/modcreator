package modstudio.core;

import modstudio.core.Elements.CodeFile;
import modstudio.core.ModElements.MixinSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Built-in Java, Mixin, AccessTransformer, and AccessWidener starter templates for unlimited mod authoring. */
public final class CodeTemplates {
    private CodeTemplates() {}

    public record CodePreset(String id, String target, String path, String hook, String hookMethod, String content) {}

    public static final List<CodePreset> CODE_PRESETS = List.of(
            new CodePreset("utility", "mods", "src/main/java/{package}/custom/{ClassName}.java", "none", "init", """
                    package {package_name}.custom;

                    public final class {ClassName} {
                        private {ClassName}() {}

                        public static void run() {
                            System.out.println("[{modid}] Custom Java helper executed!");
                        }
                    }
                    """),
            new CodePreset("common_init", "mods", "src/main/java/{package}/custom/{ClassName}.java", "common_init", "init", """
                    package {package_name}.custom;

                    import com.mojang.logging.LogUtils;
                    import org.slf4j.Logger;

                    public final class {ClassName} {
                        private static final Logger LOGGER = LogUtils.getLogger();

                        private {ClassName}() {}

                        public static void init() {
                            LOGGER.info("[{modid}] Custom Java common initializer ready.");
                        }
                    }
                    """),
            new CodePreset("client_init", "mods", "src/main/java/{package}/custom/{ClassName}.java", "client_init", "init", """
                    package {package_name}.custom;

                    import net.minecraft.client.Minecraft;

                    public final class {ClassName} {
                        private {ClassName}() {}

                        public static void init() {
                            Minecraft mc = Minecraft.getInstance();
                            System.out.println("[{modid}] Client Java hook initialized (window=" + mc.getWindow() + ")");
                        }
                    }
                    """),
            new CodePreset("event_neoforge", "neoforge", "src/main/java/{package}/custom/{ClassName}.java", "event_bus", "init", """
                    package {package_name}.custom;

                    import net.minecraft.network.chat.Component;
                    import net.neoforged.bus.api.SubscribeEvent;
                    import net.neoforged.neoforge.event.entity.player.PlayerEvent;

                    public final class {ClassName} {
                        private {ClassName}() {}

                        @SubscribeEvent
                        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
                            event.getEntity().displayClientMessage(Component.literal("[{modid}] Custom NeoForge Java event!"), false);
                        }
                    }
                    """),
            new CodePreset("event_forge", "forge", "src/main/java/{package}/custom/{ClassName}.java", "event_bus", "init", """
                    package {package_name}.custom;

                    import net.minecraft.network.chat.Component;
                    import net.minecraftforge.event.entity.player.PlayerEvent;
                    import net.minecraftforge.eventbus.api.SubscribeEvent;

                    public final class {ClassName} {
                        private {ClassName}() {}

                        @SubscribeEvent
                        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
                            event.getEntity().displayClientMessage(Component.literal("[{modid}] Custom Forge Java event!"), false);
                        }
                    }
                    """),
            new CodePreset("event_fabric", "fabric", "src/main/java/{package}/custom/{ClassName}.java", "event_bus", "init", """
                    package {package_name}.custom;

                    import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
                    import net.minecraft.network.chat.Component;

                    public final class {ClassName} {
                        private {ClassName}() {}

                        public static void init() {
                            ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                                    handler.getPlayer().displayClientMessage(Component.literal("[{modid}] Custom Fabric Java event!"), false));
                        }
                    }
                    """),
            new CodePreset("command_tree", "mods", "src/main/java/{package}/custom/{ClassName}.java", "register_commands", "register", """
                    package {package_name}.custom;

                    import com.mojang.brigadier.CommandDispatcher;
                    import com.mojang.brigadier.arguments.StringArgumentType;
                    import net.minecraft.commands.CommandSourceStack;
                    import net.minecraft.commands.Commands;
                    import net.minecraft.network.chat.Component;

                    public final class {ClassName} {
                        private {ClassName}() {}

                        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
                            dispatcher.register(Commands.literal("{modid}_java")
                                    .then(Commands.argument("msg", StringArgumentType.greedyString())
                                            .executes(ctx -> {
                                                String msg = StringArgumentType.getString(ctx, "msg");
                                                ctx.getSource().sendSuccess(() -> Component.literal("[Java] " + msg), false);
                                                return 1;
                                            })));
                        }
                    }
                    """),
            new CodePreset("server_tick", "mods", "src/main/java/{package}/custom/{ClassName}.java", "server_tick", "tick", """
                    package {package_name}.custom;

                    import net.minecraft.server.MinecraftServer;

                    public final class {ClassName} {
                        private {ClassName}() {}

                        public static void tick(MinecraftServer server) {
                            if (server.getTickCount() % 1200 == 0) {
                                // Runs every 60 seconds cleanly in pure Java
                            }
                        }
                    }
                    """),
            new CodePreset("async_http", "mods", "src/main/java/{package}/custom/{ClassName}.java", "common_init", "init", """
                    package {package_name}.custom;

                    import java.net.URI;
                    import java.net.http.HttpClient;
                    import java.net.http.HttpRequest;
                    import java.net.http.HttpResponse;
                    import java.time.Duration;
                    import java.util.concurrent.CompletableFuture;

                    public final class {ClassName} {
                        private static final HttpClient CLIENT = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(10))
                                .build();

                        private {ClassName}() {}

                        public static void init() {
                            // Asynchronous HTTP client ready for APIs, webhooks, or external data
                        }

                        public static CompletableFuture<String> fetchText(String url) {
                            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
                            return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body);
                        }
                    }
                    """),
            new CodePreset("paper_listener", "paper", "src/main/java/{package}/custom/{ClassName}.java", "event_bus", "init", """
                    package {package_name}.custom;

                    import org.bukkit.event.EventHandler;
                    import org.bukkit.event.Listener;
                    import org.bukkit.event.player.PlayerJoinEvent;

                    public final class {ClassName} implements Listener {
                        @EventHandler
                        public void onJoin(PlayerJoinEvent event) {
                            event.getPlayer().sendMessage("[{modid}] Custom Java Paper listener active!");
                        }
                    }
                    """),
            new CodePreset("access_transformer", "mods", "src/main/resources/META-INF/accesstransformer.cfg", "none", "init", """
                    # Forge / NeoForge Access Transformer (Mojang names on 1.20.4+ / NeoForge)
                    # Example:
                    # public net.minecraft.world.entity.LivingEntity jumping
                    """),
            new CodePreset("access_widener", "fabric", "src/main/resources/{modid}.accesswidener", "none", "init", """
                    accessWidener v2 named
                    # Fabric / Quilt Access Widener (official Mojang mappings)
                    # Example:
                    # accessible field net/minecraft/world/entity/LivingEntity jumping Z
                    """),
            new CodePreset("kotlin_common", "mods", "src/main/kotlin/{package}/custom/{ClassName}.kt", "common_init", "init", """
                    package {package_name}.custom

                    import org.slf4j.LoggerFactory

                    object {ClassName} {
                        private val logger = LoggerFactory.getLogger("{modid}")

                        @JvmStatic
                        fun init() {
                            logger.info("[{modid}] Custom Kotlin module ready!")
                        }
                    }
                    """),
            new CodePreset("kotlin_command", "mods", "src/main/kotlin/{package}/custom/{ClassName}.kt", "register_commands", "register", """
                    package {package_name}.custom

                    import com.mojang.brigadier.CommandDispatcher
                    import com.mojang.brigadier.arguments.StringArgumentType
                    import net.minecraft.commands.CommandSourceStack
                    import net.minecraft.commands.Commands
                    import net.minecraft.network.chat.Component

                    object {ClassName} {
                        @JvmStatic
                        fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
                            dispatcher.register(
                                Commands.literal("{modid}_kt")
                                    .then(Commands.argument("msg", StringArgumentType.greedyString())
                                        .executes { ctx ->
                                            val msg = StringArgumentType.getString(ctx, "msg")
                                            ctx.source.sendSuccess({ Component.literal("[Kotlin] $msg") }, false)
                                            1
                                        })
                            )
                        }
                    }
                    """),
            new CodePreset("kotlin_fabric_event", "fabric", "src/main/kotlin/{package}/custom/{ClassName}.kt", "event_bus", "init", """
                    package {package_name}.custom

                    import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
                    import net.minecraft.network.chat.Component

                    object {ClassName} {
                        @JvmStatic
                        fun init() {
                            ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
                                handler.player.displayClientMessage(Component.literal("[{modid}] Kotlin Fabric event!"), false)
                            }
                        }
                    }
                    """),
            new CodePreset("groovy_handler", "mods", "src/main/groovy/{package}/custom/{ClassName}.groovy", "common_init", "init", """
                    package {package_name}.custom

                    class {ClassName} {
                        static void init() {
                            println "[{modid}] Custom Groovy module initialized!"
                        }
                    }
                    """),
            new CodePreset("scala_handler", "mods", "src/main/scala/{package}/custom/{ClassName}.scala", "common_init", "init", """
                    package {package_name}.custom

                    object {ClassName}:
                      def init(): Unit =
                        println("[{modid}] Custom Scala 3 module initialized!")
                    """),
            new CodePreset("typescript_bedrock", "bedrock", "behavior_packs/{modid}/scripts/{ClassName}.ts", "none", "init", """
                    import { world, system } from "@minecraft/server";

                    world.afterEvents.playerSpawn.subscribe((ev) => {
                        if (ev.initialSpawn) {
                            ev.player.sendMessage("[{modid}] TypeScript script active!");
                        }
                    });
                    """),
            new CodePreset("javascript_bedrock", "bedrock", "behavior_packs/{modid}/scripts/{ClassName}.js", "none", "init", """
                    import { world, system } from "@minecraft/server";

                    world.afterEvents.playerSpawn.subscribe((ev) => {
                        if (ev.initialSpawn) {
                            ev.player.sendMessage("[{modid}] JavaScript script active!");
                        }
                    });
                    """),
            new CodePreset("python_helper", "all", "scripts/{ClassName}.py", "none", "init", """
                    #!/usr/bin/env python3
                    \"\"\"Build/data helper script for {modid}.\"\"\"

                    def main() -> None:
                        print("[{modid}] Python helper script")

                    if __name__ == "__main__":
                        main()
                    """),
            new CodePreset("lua_script", "all", "scripts/{ClassName}.lua", "none", "init", """
                    -- Custom Lua script for {modid}
                    local M = {}

                    function M.init()
                        print("[{modid}] Lua script loaded")
                    end

                    return M
                    """),
            new CodePreset("toml_config", "all", "src/main/resources/{modid}.toml", "none", "init", """
                    # Custom TOML configuration for {modid}
                    [general]
                    enabled = true
                    scale = 1.0
                    """),
            new CodePreset("yaml_config", "all", "src/main/resources/{modid}.yml", "none", "init", """
                    # Custom YAML configuration for {modid}
                    modid: {modid}
                    enabled: true
                    features: []
                    """)
    );

    private static final List<CodePreset> CUSTOM_PRESETS = new CopyOnWriteArrayList<>();

    /** Registers a custom code preset available to editors and MCP tools. */
    public static void registerPreset(CodePreset preset) {
        if (preset == null || preset.id() == null || preset.id().isBlank()) return;
        CUSTOM_PRESETS.removeIf(p -> p.id().equals(preset.id()));
        CUSTOM_PRESETS.add(preset);
    }

    /** Returns built-in plus plugin-registered code presets. */
    public static List<CodePreset> allPresets() {
        if (CUSTOM_PRESETS.isEmpty()) return CODE_PRESETS;
        List<CodePreset> merged = new ArrayList<>(CODE_PRESETS);
        merged.addAll(CUSTOM_PRESETS);
        return merged;
    }

    public static CodePreset codePreset(String id) {
        for (CodePreset p : CUSTOM_PRESETS) if (p.id().equals(id)) return p;
        for (CodePreset p : CODE_PRESETS) if (p.id().equals(id)) return p;
        return CODE_PRESETS.get(0);
    }

    public static void applyCodePreset(CodeFile cf, CodePreset preset) {
        String cls = Ids.toPascal(cf.name);
        if (cls.isEmpty()) cls = "CustomJava";
        cf.target = preset.target();
        cf.path = preset.path().replace("{ClassName}", cls);
        cf.hook = preset.hook();
        cf.hookMethod = preset.hookMethod();
        cf.content = preset.content().replace("{ClassName}", cls);
    }

    public static final List<String> MIXIN_PRESETS = List.of("inject_head", "inject_returnable", "redirect", "modify_var", "accessor_interface");

    public static String mixinPreset(String id, MixinSpec mx) {
        String target = mx.targetClass == null || mx.targetClass.isBlank() ? "net.minecraft.world.entity.player.Player" : mx.targetClass.trim();
        boolean strTarget = mx.useTargetsString || target.contains("$");
        String impLine = !strTarget && target.contains(".") ? "import " + target.replace('$', '.') + ";\n" : "";
        String simple = mx.simpleTarget();
        String annot = strTarget ? "@Mixin(targets = \"" + target + "\")" : "@Mixin(" + simple + ".class)";
        String method = mx.methodTarget == null || mx.methodTarget.isBlank() ? "tick" : mx.methodTarget.trim();
        String atRaw = (mx.injectAt == null || mx.injectAt.isBlank()) ? "HEAD" : mx.injectAt.trim().toUpperCase(java.util.Locale.ROOT);
        String atSpec = atRaw.contains("\"") ? atRaw : "\"" + atRaw + "\"";
        return switch (id) {
            case "accessor_interface" -> "package {package_name}.mixin;\n\n"
                    + impLine
                    + "import org.spongepowered.asm.mixin.Mixin;\n"
                    + "import org.spongepowered.asm.mixin.gen.Accessor;\n"
                    + "import org.spongepowered.asm.mixin.gen.Invoker;\n\n"
                    + annot + "\n"
                    + "public interface {mixin_class} {\n"
                    + "    // @Accessor(\"fieldName\")\n"
                    + "    // int getSomeField();\n\n"
                    + "    // @Invoker(\"methodName\")\n"
                    + "    // void invokeSomeMethod();\n"
                    + "}\n";
            case "inject_returnable" -> "package {package_name}.mixin;\n\n"
                    + impLine
                    + "import org.spongepowered.asm.mixin.Mixin;\n"
                    + "import org.spongepowered.asm.mixin.injection.At;\n"
                    + "import org.spongepowered.asm.mixin.injection.Inject;\n"
                    + "import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;\n\n"
                    + annot + "\n"
                    + "public abstract class {mixin_class} {\n"
                    + "    @Inject(method = \"" + method + "\", at = @At(" + atSpec + "), cancellable = true)\n"
                    + "    private void modstudio$hook(CallbackInfoReturnable<Boolean> cir) {\n"
                    + "        // cir.setReturnValue(true);\n"
                    + "    }\n"
                    + "}\n";
            case "redirect" -> "package {package_name}.mixin;\n\n"
                    + impLine
                    + "import org.spongepowered.asm.mixin.Mixin;\n"
                    + "import org.spongepowered.asm.mixin.Unique;\n\n"
                    + annot + "\n"
                    + "public abstract class {mixin_class} {\n"
                    + "    @Unique\n"
                    + "    private int modstudio$state = 0;\n\n"
                    + "    // Add @Redirect / @ModifyArg / @Shadow methods freely:\n"
                    + "    // @Shadow protected int someField;\n"
                    + "}\n";
            case "modify_var" -> "package {package_name}.mixin;\n\n"
                    + impLine
                    + "import org.spongepowered.asm.mixin.Mixin;\n"
                    + "import org.spongepowered.asm.mixin.injection.At;\n"
                    + "import org.spongepowered.asm.mixin.injection.ModifyVariable;\n\n"
                    + annot + "\n"
                    + "public abstract class {mixin_class} {\n"
                    + "    @ModifyVariable(method = \"" + method + "\", at = @At(" + atSpec + "), argsOnly = true, ordinal = 0)\n"
                    + "    private float modstudio$modifyArg(float value) {\n"
                    + "        return value;\n"
                    + "    }\n"
                    + "}\n";
            default -> "package {package_name}.mixin;\n\n"
                    + impLine
                    + "import org.spongepowered.asm.mixin.Mixin;\n"
                    + "import org.spongepowered.asm.mixin.injection.At;\n"
                    + "import org.spongepowered.asm.mixin.injection.Inject;\n"
                    + "import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;\n\n"
                    + annot + "\n"
                    + "public abstract class {mixin_class} {\n"
                    + "    @Inject(method = \"" + method + "\", at = @At(" + atSpec + "), cancellable = " + mx.cancellable + ")\n"
                    + "    private void modstudio$handler(CallbackInfo ci) {\n"
                    + ((mx.code == null || mx.code.isBlank()) ? "        // Custom Mixin logic\n" : "        " + mx.code.strip().replace("\n", "\n        ") + "\n")
                    + "    }\n"
                    + "}\n";
        };
    }
}

