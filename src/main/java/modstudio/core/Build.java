package modstudio.core;

/** One output of a project: a target platform for a Minecraft version. */
public record Build(Target target, McVersion mc) {
    /** Stable key, also used as the output folder name. */
    public String key() {
        return target.versioned() ? target.id + "-" + mc.id : target.id;
    }
}
