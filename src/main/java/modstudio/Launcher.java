package modstudio;

/** Entry point that does not extend Application, so the app also starts from a plain classpath. */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        if (args.length > 0 && Cli.handles(args[0])) {
            System.exit(Cli.run(args));
            return;
        }
        App.launchApp(args);
    }
}
