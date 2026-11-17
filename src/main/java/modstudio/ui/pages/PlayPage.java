package modstudio.ui.pages;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;
import modstudio.App;
import modstudio.build.BuildService;
import modstudio.build.GameDirs;
import modstudio.core.*;
import modstudio.gen.Exporter;
import modstudio.gen.GenContext;
import modstudio.gen.Generators;
import modstudio.gen.LiveData;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;
import modstudio.ui.WorkspaceView;
import org.gradle.tooling.CancellationTokenSource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Play test: starts the game with the mod (Gradle runClient / runServer), a Paper test server with the plugin,
 * or installs data packs, resource packs and Bedrock add-ons into the local game.
 */
public final class PlayPage extends BorderPane implements WorkspaceView.Page {
    private final WorkspaceView view;
    private final Project p;
    private final ComboBox<Build> build = new ComboBox<>();
    private final ToggleGroup mode = new ToggleGroup();
    private final RadioButton client = new RadioButton();
    private final RadioButton server = new RadioButton();
    private final CheckBox eula = new CheckBox();
    private final TextField gameDir = new TextField();
    private final ComboBox<String> world = new ComboBox<>();
    private final VBox options = new VBox(10);
    private final Label hint = Ui.help("");
    private final Button startB;
    private final Button stopB;
    private final TextArea console = new TextArea();
    private final TextField command = new TextField();
    private final Button sendB;
    private final Label state = Ui.label("", "text-muted");
    private final ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();
    private volatile CancellationTokenSource cancel;
    private volatile OutputStream stdin;
    private volatile boolean running;
    private volatile boolean serverRun;
    // Hot reload: the build of the active run (or of the last data pack install) and what /reload cannot pick up.
    private final CheckBox hot = new CheckBox();
    private final Button reloadNowB;
    private final PauseTransition hotTimer = new PauseTransition(Duration.millis(1500));
    private volatile Build liveBuild;
    private volatile Path liveDatapack;
    private volatile String liveHash = "";
    private volatile boolean hotBusy;

    public PlayPage(WorkspaceView view) {
        this.view = view;
        this.p = view.ws.project;

        VBox head = new VBox(4, Ui.label(t("page.play"), "title-2"), Ui.muted(t("play.subtitle")));
        HBox bar = Ui.hbox(8, head);
        bar.setPadding(new Insets(20, 24, 8, 24));
        setTop(bar);

        build.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Build b) {
                if (b == null) return "";
                return t("target." + b.target().id) + (b.target().versioned() ? "  " + b.mc().id : "");
            }

            @Override
            public Build fromString(String s) {
                return null;
            }
        });
        build.setMaxWidth(Double.MAX_VALUE);
        build.valueProperty().addListener((o, a, b) -> refreshOptions());

        client.setText(t("play.client"));
        server.setText(t("play.server"));
        client.setToggleGroup(mode);
        server.setToggleGroup(mode);
        client.setSelected(true);
        mode.selectedToggleProperty().addListener((o, a, b) -> refreshOptions());

        eula.setText(t("play.eula"));
        eula.setWrapText(true);
        Hyperlink eulaLink = new Hyperlink(t("play.eula_open"));
        eulaLink.setOnAction(e -> App.hosts().showDocument("https://aka.ms/MinecraftEULA"));

        gameDir.setText(GameDirs.javaDefault().toString());
        gameDir.textProperty().addListener((o, a, b) -> refreshWorlds());
        Button browse = Ui.button(t("action.browse"), "mdi2f-folder-open-outline", () -> {
            DirectoryChooser dc = new DirectoryChooser();
            Path cur = Path.of(gameDir.getText().trim());
            if (Files.isDirectory(cur)) dc.setInitialDirectory(cur.toFile());
            var f = dc.showDialog(view.window());
            if (f != null) gameDir.setText(f.getAbsolutePath());
        });
        HBox.setHgrow(gameDir, Priority.ALWAYS);
        world.setMaxWidth(Double.MAX_VALUE);
        Button reload = Ui.iconButton("mdi2r-refresh", t("play.refresh_worlds"), this::refreshWorlds);
        HBox.setHgrow(world, Priority.ALWAYS);

        startB = Ui.accent(t("play.start"), "mdi2p-play", this::start);
        stopB = Ui.button(t("play.stop"), "mdi2s-stop", this::stop);
        stopB.setDisable(true);
        Button openRun = Ui.button(t("play.open_run"), "mdi2f-folder-open", () -> {
            Build b = build.getValue();
            if (b != null) open(Exporter.outputDir(view.ws.dir(), b).resolve(b.target().usesGradle() ? "run" : ""));
        });

        // Options per build kind
        Node modeRow = row(t("play.mode"), Ui.hbox(16, client, server));
        Node eulaRow = new VBox(2, eula, eulaLink);
        Node dirRow = row(t("play.game_dir"), Ui.hbox(6, gameDir, browse));
        Node worldRow = row(t("play.world"), Ui.hbox(6, world, reload));
        modeRow.getProperties().put("kind", "mode");
        eulaRow.getProperties().put("kind", "eula");
        dirRow.getProperties().put("kind", "dir");
        worldRow.getProperties().put("kind", "world");
        options.getChildren().addAll(modeRow, eulaRow, dirRow, worldRow);

        VBox card = Ui.card(t("play.what"), row(t("play.build"), build), options, hint);
        for (Button b : new Button[]{startB, stopB, openRun}) b.setMinWidth(Region.USE_PREF_SIZE);

        hot.setText(t("play.hot_reload"));
        hot.setSelected(true);
        reloadNowB = Ui.button(t("play.reload_now"), "mdi2r-reload", () -> hotReload(true));
        reloadNowB.setMinWidth(Region.USE_PREF_SIZE);
        hotTimer.setOnFinished(e -> hotReload(false));
        view.ws.revisionProperty().addListener((o, a, b) -> {
            if (hot.isSelected() && canHotReload()) hotTimer.playFromStart();
        });
        Label hotTip = Ui.help(t("play.hot_reload_tip"));
        hotTip.setWrapText(true);
        VBox hotBox = new VBox(6, Ui.hbox(10, hot, Ui.spacer(), reloadNowB), hotTip);
        hotBox.getStyleClass().add("card");
        hotBox.setPadding(new Insets(12));

        VBox left = new VBox(12, card, Ui.hbox(8, startB, stopB, openRun), state, hotBox);
        left.setPrefWidth(480);
        left.setMinWidth(400);

        console.setEditable(false);
        console.getStyleClass().addAll("console", "mono");
        console.setWrapText(false);
        console.setPromptText(t("play.console_empty"));
        command.setPromptText(t("play.command_prompt"));
        command.getStyleClass().add("mono");
        sendB = Ui.button(t("play.send"), "mdi2s-send", this::sendCommand);
        command.setOnAction(e -> sendCommand());
        HBox.setHgrow(command, Priority.ALWAYS);
        HBox cmdRow = Ui.hbox(6, command, sendB);
        Button clear = Ui.iconButton("mdi2d-delete-outline", t("build.clear_log"), console::clear);
        VBox right = new VBox(6, Ui.hbox(4, Ui.label(t("play.console"), "card-title"), Ui.spacer(), clear), console, cmdRow);
        VBox.setVgrow(console, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        HBox box = new HBox(16, Ui.scroll(left), right);
        box.setPadding(new Insets(12, 24, 16, 24));
        setCenter(box);

        Timeline flush = new Timeline(new KeyFrame(Duration.millis(120), e -> flushLog()));
        flush.setCycleCount(Timeline.INDEFINITE);
        flush.play();
        updateButtons();
    }

    private static String t(String k, Object... a) {
        return I18n.t(k, a);
    }

    private static Node row(String label, Node control) {
        VBox v = new VBox(4, Ui.label(label, "form-label"), control);
        v.setFillWidth(true);
        return v;
    }

    @Override
    public void onShow() {
        Build keep = build.getValue();
        List<Build> builds = p.builds();
        build.getItems().setAll(builds);
        if (keep != null && builds.contains(keep)) build.setValue(keep);
        else if (!builds.isEmpty()) build.setValue(builds.stream().filter(b -> b.target().isMod()).findFirst().orElse(builds.get(0)));
        refreshOptions();
    }

    // ------------------------------------------------------------------ options

    private void refreshOptions() {
        Build b = build.getValue();
        if (!running && b != null && !b.equals(liveBuild)) {
            liveBuild = null;
            liveDatapack = null;
        }
        Target tg = b == null ? null : b.target();
        boolean mod = tg != null && tg.isMod();
        boolean plugin = tg == Target.PAPER;
        boolean pack = tg == Target.DATAPACK || tg == Target.RESOURCEPACK;
        boolean bedrock = tg == Target.BEDROCK;
        boolean asServer = plugin || (mod && server.isSelected());
        for (Node n : options.getChildren()) {
            String kind = (String) n.getProperties().get("kind");
            boolean show = switch (kind) {
                case "mode" -> mod;
                case "eula" -> asServer;
                case "dir" -> pack;
                case "world" -> tg == Target.DATAPACK;
                default -> false;
            };
            n.setVisible(show);
            n.setManaged(show);
        }
        if (tg == null) hint.setText("");
        else if (mod) hint.setText(t(asServer ? "play.hint_mod_server" : "play.hint_mod_client"));
        else if (plugin) hint.setText(t("play.hint_paper"));
        else if (tg == Target.DATAPACK) hint.setText(t("play.hint_datapack"));
        else if (tg == Target.RESOURCEPACK) hint.setText(t("play.hint_resourcepack"));
        else if (bedrock) {
            Path dir = GameDirs.bedrock();
            hint.setText(dir == null ? t("play.bedrock_missing") : t("play.hint_bedrock", dir));
        }
        if (b != null && asServer) eula.setSelected(GameDirs.eulaAccepted(Exporter.outputDir(view.ws.dir(), b)) || eula.isSelected());
        if (tg == Target.DATAPACK) refreshWorlds();
        startB.setText(t(pack || bedrock ? "play.install" : "play.start"));
        updateButtons();
    }

    private void refreshWorlds() {
        String keep = world.getValue();
        List<String> worlds = GameDirs.worlds(Path.of(gameDir.getText().trim()));
        world.getItems().setAll(worlds);
        if (keep != null && worlds.contains(keep)) world.setValue(keep);
        else if (!worlds.isEmpty()) world.setValue(worlds.get(0));
        world.setPromptText(worlds.isEmpty() ? t("play.no_worlds") : "");
    }

    private void updateButtons() {
        startB.setDisable(running || build.getValue() == null);
        stopB.setDisable(!running);
        build.setDisable(running);
        command.setDisable(!running || !serverRun);
        sendB.setDisable(!running || !serverRun);
        reloadNowB.setDisable(!canHotReload());
    }

    // ------------------------------------------------------------------ hot reload

    private boolean canHotReload() {
        Build b = liveBuild;
        if (b == null) return false;
        if (b.target().usesGradle()) return running;
        return b.target() == Target.DATAPACK && liveDatapack != null && !running;
    }

    /**
     * Regenerates the project and swaps the data part into the running game: a live data pack in the test world
     * plus /reload for test servers, the last played world for test clients, the installed folder for data packs.
     */
    private void hotReload(boolean manual) {
        hotTimer.stop();
        Build b = liveBuild;
        if (!canHotReload()) {
            if (manual) log(t("play.hot_idle"));
            return;
        }
        if (hotBusy) {
            hotTimer.playFromStart();
            return;
        }
        GenContext ctx;
        try {
            ctx = Generators.generate(p, b);
        } catch (Exception ex) {
            log(t("play.hot_failed", ex.getMessage() == null ? ex.toString() : ex.getMessage()));
            return;
        }
        boolean toServer = serverRun;
        Path datapack = liveDatapack;
        Path run = Exporter.outputDir(view.ws.dir(), b).resolve("run");
        hotBusy = true;
        Thread th = new Thread(() -> {
            try {
                if (b.target() == Target.DATAPACK) {
                    Exporter.writeFolder(ctx.files, datapack);
                    log(t("play.hot_datapack", datapack));
                } else if (toServer) {
                    int n = LiveData.write(ctx, LiveData.serverWorld(run));
                    send(b.target() == Target.PAPER ? "minecraft:reload" : "reload");
                    log(t("play.hot_reloaded", n));
                } else {
                    Path world = LiveData.newestSave(run);
                    if (world == null) log(t("play.hot_no_world"));
                    else log(t("play.hot_client", LiveData.write(ctx, world), world.getFileName()));
                }
                if (b.target() != Target.DATAPACK) {
                    String h = LiveData.codeHash(ctx);
                    if (!h.equals(liveHash)) {
                        liveHash = h;
                        log(t("play.restart_needed"));
                    }
                }
            } catch (Throwable ex) {
                log(t("play.hot_failed", ex.getMessage() == null ? ex.toString() : ex.getMessage()));
            } finally {
                hotBusy = false;
            }
        }, "modstudio-hot-reload");
        th.setDaemon(true);
        th.start();
    }

    // ------------------------------------------------------------------ run

    /** Copies the local secrets into the run folder where the generated mod or plugin reads them. */
    private void copySecrets(Target tg, Path out) {
        if (!tg.isMod() && tg != Target.PAPER) return;
        if (modstudio.core.Secrets.referenced(p).isEmpty()) return;
        Path file = tg == Target.PAPER
                ? out.resolve("run").resolve("plugins").resolve(modstudio.gen.PaperGen.pluginName(p)).resolve("secrets.properties")
                : out.resolve("run").resolve("config").resolve(p.meta.modId + "-secrets.properties");
        try {
            new modstudio.core.Secrets(view.ws.dir()).writeRuntime(p, file);
            log(t("play.secrets_copied", file));
        } catch (java.io.IOException e) {
            log(t("play.secrets_failed", e.getMessage()));
        }
    }

    private void start() {
        Build b = build.getValue();
        if (b == null || running) return;
        Target tg = b.target();
        boolean asServer = tg == Target.PAPER || (tg.isMod() && server.isSelected());
        if (asServer && !eula.isSelected()) {
            Ui.info(view.window(), t("play.eula_needed"), t("play.eula_needed_text"));
            return;
        }
        if (tg == Target.PAPER && p.version(Target.PAPER, b.mc(), "api").equalsIgnoreCase("spigot")) {
            Ui.info(view.window(), t("play.paper_only"), t("play.paper_only_text"));
            return;
        }
        Path game = Path.of(gameDir.getText().trim());
        String worldName = world.getValue();
        if (tg == Target.DATAPACK && worldName == null) {
            Ui.info(view.window(), t("play.no_worlds"), t("play.no_worlds_text"));
            return;
        }
        Path bedrockDir = GameDirs.bedrock();
        if (tg == Target.BEDROCK && bedrockDir == null) {
            Ui.info(view.window(), t("play.bedrock_missing"), t("play.bedrock_missing_text"));
            return;
        }
        view.ws.validate();
        GenContext ctx;
        try {
            ctx = Generators.generate(p, b);
        } catch (Exception ex) {
            Ui.error(view.window(), t("build.generate_failed"), ex);
            return;
        }
        view.main.save();
        Path out = Exporter.outputDir(view.ws.dir(), b);
        Path jdk = BuildService.findJavaHome(view.main.settings.jdkPath);
        boolean hotPack = tg == Target.DATAPACK && hot.isSelected();
        hotTimer.stop();
        liveBuild = b;
        liveDatapack = null;
        liveHash = LiveData.codeHash(ctx);
        running = true;
        serverRun = asServer;
        updateButtons();
        state.setText(t("play.state_preparing"));
        log("");
        log("== " + build.getConverter().toString(b) + " ==");

        Thread th = new Thread(() -> {
            boolean ok = true;
            try {
                Exporter.writeFolder(ctx.files, out);
                copySecrets(tg, out);
                if (tg.usesGradle()) {
                    if (jdk == null) throw new IllegalStateException(t("build.jdk_missing", b.mc().java));
                    if (asServer) GameDirs.writeEula(out);
                    LiveData.clear(out.resolve("run"), ctx.ns);
                    String task = asServer ? "runServer" : "runClient";
                    log(t("play.starting", task));
                    state(t(asServer ? "play.state_server" : "play.state_client"));
                    PipedOutputStream pipe = new PipedOutputStream();
                    PipedInputStream in = new PipedInputStream(pipe, 8192);
                    stdin = pipe;
                    cancel = BuildService.newCancellation();
                    BuildService.run(out, jdk, List.of(task), List.of(), this::log, cancel.token(), in);
                } else if (tg == Target.DATAPACK) {
                    // With hot reload the pack is installed as a folder: the game keeps zip packs open (locked on Windows).
                    String zipName = Exporter.packFileName(p, b);
                    Path packs = game.resolve("saves").resolve(worldName).resolve("datapacks");
                    Path zip = packs.resolve(zipName);
                    Path folder = packs.resolve(zipName.endsWith(".zip") ? zipName.substring(0, zipName.length() - 4) : zipName + "_dir");
                    Files.createDirectories(packs);
                    Path dest;
                    if (hotPack) {
                        Files.deleteIfExists(zip);
                        Exporter.writeFolder(ctx.files, folder);
                        dest = folder;
                        liveDatapack = folder;
                    } else {
                        Exporter.deleteTree(folder);
                        Exporter.writeZip(ctx.files, zip);
                        dest = zip;
                    }
                    log(t("play.installed", dest));
                    log(t("play.after_datapack"));
                    if (hotPack) log(t("play.hot_datapack_ready"));
                } else if (tg == Target.RESOURCEPACK) {
                    Path dest = game.resolve("resourcepacks").resolve(Exporter.packFileName(p, b));
                    Files.createDirectories(dest.getParent());
                    Exporter.writeZip(ctx.files, dest);
                    log(t("play.installed", dest));
                    log(t("play.after_resourcepack"));
                } else if (tg == Target.BEDROCK) {
                    installBedrock(out, bedrockDir);
                    log(t("play.after_bedrock"));
                }
            } catch (Throwable ex) {
                ok = false;
                if (ex instanceof org.gradle.tooling.BuildCancelledException) log(t("play.stopped"));
                else {
                    log(t("play.failed", ex.getMessage() == null ? ex.toString() : ex.getMessage()));
                    for (Throwable c = ex.getCause(); c != null; c = c.getCause()) log("  " + c);
                }
            } finally {
                closeStdin();
                boolean fok = ok;
                Platform.runLater(() -> {
                    running = false;
                    serverRun = false;
                    cancel = null;
                    state.setText(fok ? t("play.state_done") : t("play.state_stopped"));
                    updateButtons();
                });
            }
        }, "modstudio-play");
        th.setDaemon(true);
        th.start();
    }

    private void installBedrock(Path out, Path comMojang) throws IOException {
        String ns = p.meta.modId;
        Path bp = out.resolve(ns + "_BP"), rp = out.resolve(ns + "_RP");
        if (Files.isDirectory(bp)) {
            Path dest = comMojang.resolve("development_behavior_packs").resolve(ns + "_BP");
            GameDirs.copyTree(bp, dest);
            log(t("play.installed", dest));
        }
        if (Files.isDirectory(rp)) {
            Path dest = comMojang.resolve("development_resource_packs").resolve(ns + "_RP");
            GameDirs.copyTree(rp, dest);
            log(t("play.installed", dest));
        }
    }

    private void stop() {
        if (!running) return;
        if (serverRun && stdin != null) {
            // Ask the server to save and stop; cancel the run if it does not exit in time.
            send("stop");
            state.setText(t("play.state_stopping"));
            Timeline later = new Timeline(new KeyFrame(Duration.seconds(20), e -> {
                if (running && cancel != null) cancel.cancel();
            }));
            later.play();
        } else if (cancel != null) {
            cancel.cancel();
            state.setText(t("play.state_stopping"));
        }
    }

    private void sendCommand() {
        String c = command.getText().trim();
        if (c.isEmpty()) return;
        if (c.startsWith("/")) c = c.substring(1);
        send(c);
        log("> " + c);
        command.clear();
    }

    private void send(String line) {
        OutputStream s = stdin;
        if (s == null) return;
        try {
            s.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            s.flush();
        } catch (IOException ex) {
            log(t("play.send_failed", ex.getMessage()));
        }
    }

    private void closeStdin() {
        OutputStream s = stdin;
        stdin = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void state(String s) {
        Platform.runLater(() -> state.setText(s));
    }

    private void log(String s) {
        logQueue.add(s);
    }

    private void flushLog() {
        if (logQueue.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        String s;
        while ((s = logQueue.poll()) != null) sb.append(s).append('\n');
        console.appendText(sb.toString());
        if (console.getLength() > 600_000) console.deleteText(0, console.getLength() - 400_000);
    }

    private void open(Path dir) {
        try {
            Files.createDirectories(dir);
            App.hosts().showDocument(dir.toUri().toString());
        } catch (Exception ex) {
            Ui.error(view.window(), t("build.open_failed"), ex);
        }
    }

    /** Stops a running game when the workspace closes. */
    public void dispose() {
        if (running && cancel != null) cancel.cancel();
    }
}
