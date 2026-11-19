package modstudio.ext;

import modstudio.core.*;
import modstudio.gen.GenContext;
import modstudio.gen.Generators;
import modstudio.gen.ProcCompiler;
import modstudio.mcp.McpToolRegistry;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Central extensibility facade and {@link ServiceLoader} bootstrap for ModStudio plugins.
 * <p>
 * Exposes safe, cohesive extension hooks across logic DSL steps/conditions/triggers,
 * code templates, localization, 3D previews, project validation/refactoring/upgrading,
 * code generation, mod importing, and MCP tooling.
 */
public final class ModStudioExtensions {
    private static final ModStudioExtensions INSTANCE = new ModStudioExtensions();
    private final List<ModStudioPlugin> plugins = new CopyOnWriteArrayList<>();
    private final Set<String> loadedIds = Collections.synchronizedSet(new LinkedHashSet<>());
    private volatile boolean spiBootstrapped = false;

    private ModStudioExtensions() {}

    /** Returns the singleton extension registry, loading {@link ServiceLoader} plugins once on first access. */
    public static ModStudioExtensions get() {
        INSTANCE.ensureBootstrapped();
        return INSTANCE;
    }

    private void ensureBootstrapped() {
        if (spiBootstrapped) return;
        synchronized (this) {
            if (spiBootstrapped) return;
            spiBootstrapped = true;
            try {
                for (ModStudioPlugin plugin : ServiceLoader.load(ModStudioPlugin.class)) {
                    register(plugin);
                }
            } catch (Throwable ignored) {
                // Broken external plugin on classpath must not crash core startup
            }
        }
    }

    /** Registers a plugin programmatically (idempotent by {@link ModStudioPlugin#id()}). */
    public static void register(ModStudioPlugin plugin) {
        if (plugin == null || plugin.id() == null || plugin.id().isBlank()) return;
        INSTANCE.ensureBootstrapped();
        if (INSTANCE.loadedIds.add(plugin.id())) {
            INSTANCE.plugins.add(plugin);
            plugin.onRegister(INSTANCE);
        }
    }

    /** Returns all registered plugins in registration order. */
    public static List<ModStudioPlugin> plugins() {
        return List.copyOf(get().plugins);
    }

    /** Registers a custom procedure step across DSL, ProcCompiler (Java/Pack/Paper), and optional Bedrock command. */
    public ModStudioExtensions registerStep(
            Logic.Def def,
            boolean modOnly,
            boolean hasBody,
            BiConsumer<Elements.Step, List<String>> javaCompiler,
            Function<Elements.Step, String> bedrockCompiler) {
        Logic.registerStep(def, modOnly, hasBody);
        if (javaCompiler != null) ProcCompiler.registerCustomStep(def.id(), javaCompiler);
        if (bedrockCompiler != null) LogicExtra.registerBedrockCommand(def.id(), bedrockCompiler);
        return this;
    }

    /** Registers a custom procedure condition. */
    public ModStudioExtensions registerCondition(Logic.Def def) {
        Logic.registerCondition(def);
        return this;
    }

    /** Registers a custom procedure trigger and its supported output targets. */
    public ModStudioExtensions registerTrigger(Logic.Def def, Target... targets) {
        Logic.registerTrigger(def, targets);
        return this;
    }

    /** Registers a custom code template preset. */
    public ModStudioExtensions registerCodePreset(CodeTemplates.CodePreset preset) {
        CodeTemplates.registerPreset(preset);
        return this;
    }

    /** Registers an additional project validation rule. */
    public ModStudioExtensions registerValidator(BiConsumer<Project, List<Validator.Problem>> rule) {
        Validator.registerRule(rule);
        return this;
    }

    /** Registers an element or ID upgrade mapping when migrating projects to newer MC versions. */
    public ModStudioExtensions registerIdUpgrade(McVersion minTarget, String oldId, String newId) {
        ProjectUpgrader.registerIdMigration(minTarget, oldId, newId);
        return this;
    }

    /** Registers a custom generator post-processor invoked at the end of {@link Generators#generate}. */
    public ModStudioExtensions registerGenContributor(Consumer<GenContext> contributor) {
        Generators.registerContributor(contributor);
        return this;
    }

    /** Registers an importer archive enrichment hook invoked after core mod import finishes. */
    public ModStudioExtensions registerImportAnalyzer(Consumer<ProjectImporter.ImportHookContext> analyzer) {
        ProjectImporter.registerAnalyzer(analyzer);
        return this;
    }

    /** Registers a global custom MCP tool available to both {@link modstudio.mcp.McpServer} and AI chat. */
    public ModStudioExtensions registerMcpTool(McpToolRegistry.McpTool tool) {
        McpToolRegistry.registerGlobalTool(tool);
        return this;
    }

    /** Registers a custom 3D preview model provider for a specific {@link ElementType}. */
    public ModStudioExtensions registerPreviewProvider(ElementType type, PreviewModels.PreviewProvider provider) {
        PreviewModels.registerProvider(type, provider);
        return this;
    }
}

