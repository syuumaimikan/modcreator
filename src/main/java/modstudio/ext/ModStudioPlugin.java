package modstudio.ext;

/**
 * Service-provider extension interface for ModStudio.
 * <p>
 * Implementations may be registered either via {@code META-INF/services/modstudio.ext.ModStudioPlugin}
 * or programmatically through {@link ModStudioExtensions#register(ModStudioPlugin)}.
 */
public interface ModStudioPlugin {
    /** Unique machine identifier of this plugin (e.g. {@code "custom_alchemy"}). */
    String id();

    /** Human-readable display name. */
    default String name() {
        return id();
    }

    /** Invoked once when the plugin is loaded into {@link ModStudioExtensions}. */
    void onRegister(ModStudioExtensions registry);
}

