package modstudio.ui.pages;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;
import modstudio.App;
import modstudio.build.BuildService;
import modstudio.core.*;
import modstudio.gen.Exporter;
import modstudio.gen.FileMap;
import modstudio.gen.GenContext;
import modstudio.gen.Generators;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;
import modstudio.ui.WorkspaceView;
import modstudio.ui.controls.CodeEditor;
import org.gradle.tooling.CancellationTokenSource;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Export generated sources, build jars and packs, and browse the generated files. */
public final class BuildPage extends BorderPane implements WorkspaceView.Page {
    private final WorkspaceView view;
    private final Project p;
    private final VBox targetsBox = new VBox(8);
    private final Map<Build, CheckBox> checks = new LinkedHashMap<>();
    private final Map<Build, Label> status = new LinkedHashMap<>();
    private final CheckBox runGradle = new CheckBox();
    private final TextArea console = new TextArea();
    private final Label jdkLabel = Ui.help("");
    private final Button buildB;
    private final Button exportB;
    private final Button cancelB;
    private final ProgressBar progress = new ProgressBar(0);
    private final ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();
    private volatile CancellationTokenSource cancel;
    private volatile boolean running;

    // Preview tab
    private final ComboBox<Build> previewTarget = new ComboBox<>();
    private final TreeView<String> tree = new TreeView<>();
    private final StackPane viewer = new StackPane();
    private final Label previewInfo = Ui.help("");
    private FileMap previewFiles;

    public BuildPage(WorkspaceView view) {
        this.view = view;
        this.p = view.ws.project;

        VBox head = new VBox(4, Ui.label(t("page.build"), "title-2"), Ui.muted(t("build.subtitle")));
        HBox bar = Ui.hbox(8, head);
        bar.setPadding(new Insets(20, 24, 8, 24));
        setTop(bar);

        exportB = Ui.button(t("build.export"), "mdi2e-export-variant", () -> start(false));
        buildB = Ui.accent(t("build.build"), "mdi2h-hammer", () -> start(true));
        cancelB = Ui.button(t("action.cancel"), "mdi2s-stop", () -> {
            if (cancel != null) cancel.cancel();
        });
        cancelB.setDisable(true);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab buildTab = new Tab(t("build.tab_build"), buildTab());
        Tab previewTab = new Tab(t("build.tab_preview"), previewTab());
        previewTab.setOnSelectionChanged(e -> {
            if (previewTab.isSelected()) refreshPreview();
        });
        tabs.getTabs().addAll(buildTab, previewTab);
        BorderPane.setMargin(tabs, new Insets(0, 16, 12, 16));
        setCenter(tabs);

        Timeline flush = new Timeline(new KeyFrame(Duration.millis(120), e -> flushLog()));
        flush.setCycleCount(Timeline.INDEFINITE);
        flush.play();
    }

    private static String t(String k, Object... a) {
        return I18n.t(k, a);
    }

    @Override
    public void onShow() {
        rebuildTargets();
        updateJdk();
    }

    // ------------------------------------------------------------------ build tab

    private Node buildTab() {
        runGradle.setText(t("build.run_gradle"));
        runGradle.setSelected(true);
        Button openOut = Ui.button(t("build.open_output"), "mdi2f-folder-open", () -> open(view.ws.dir().resolve("output")));
        Button openDist = Ui.button(t("build.open_dist"), "mdi2f-folder-open", () -> open(Exporter.distDir(view.ws.dir())));
        Button jdk = Ui.button(t("build.change_jdk"), "mdi2c-cog-outline", () -> {
            view.main.openSettings();
            updateJdk();
        });
        jdk.getStyleClass().add("flat");

        VBox targetsCard = Ui.card(t("build.targets"), targetsBox, new Separator(), runGradle, Ui.hbox(8, jdkLabel, jdk),
                Ui.help(t("build.gradle_help")));
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setVisible(false);
        for (Button b : new Button[]{exportB, buildB, cancelB, openOut, openDist}) b.setMinWidth(Region.USE_PREF_SIZE);
        VBox actions = new VBox(8, Ui.hbox(8, buildB, exportB, cancelB), Ui.hbox(8, openOut, openDist));
        VBox left = new VBox(12, targetsCard, actions, progress);
        left.setPrefWidth(520);
        left.setMinWidth(420);

        console.setEditable(false);
        console.getStyleClass().addAll("console", "mono");
        console.setWrapText(false);
        console.setPromptText(t("build.console_empty"));
        Button clear = Ui.iconButton("mdi2d-delete-outline", t("build.clear_log"), console::clear);
        Button copy = Ui.iconButton("mdi2c-content-copy", t("build.copy_log"), () -> {
            var cc = new javafx.scene.input.ClipboardContent();
            cc.putString(console.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
        });
        VBox right = new VBox(6, Ui.hbox(4, Ui.label(t("build.log"), "card-title"), Ui.spacer(), copy, clear), console);
        VBox.setVgrow(console, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        HBox box = new HBox(16, Ui.scroll(left), right);
        box.setPadding(new Insets(12, 8, 8, 8));
        rebuildTargets();
        updateJdk();
        return box;
    }

    private void rebuildTargets() {
        if (running) return;
        targetsBox.getChildren().clear();
        Map<Build, Boolean> prev = new HashMap<>();
        checks.forEach((k, v) -> prev.put(k, v.isSelected()));
        checks.clear();
        List<Build> builds = p.builds();
        if (builds.isEmpty()) targetsBox.getChildren().add(Ui.help(t("problem.no_targets")));
        for (Build b : builds) {
            Target tg = b.target();
            CheckBox cb = new CheckBox(t("target." + tg.id));
            cb.setSelected(prev.getOrDefault(b, true));
            checks.put(b, cb);
            HBox title = Ui.hbox(8, cb);
            if (tg.versioned()) title.getChildren().add(Ui.label(b.mc().id, "chip"));
            Label out = Ui.label(t("build.produces_" + tg.kind.name().toLowerCase()), "text-muted", "text-small");
            Label st = status.computeIfAbsent(b, k -> Ui.label(t("build.status_idle"), "text-small"));
            VBox text = new VBox(2, title, out);
            HBox row = Ui.hbox(8, text, Ui.spacer(), st);
            targetsBox.getChildren().add(row);
        }
    }

    private void updateJdk() {
        Path home = BuildService.findJavaHome(view.main.settings.jdkPath);
        int need = requiredJava();
        if (home == null) {
            jdkLabel.setText(t("build.jdk_missing", need));
            jdkLabel.getStyleClass().add("warning-text");
        } else {
            String v = BuildService.javaVersion(home);
            jdkLabel.setText(t("build.jdk", v, home));
            jdkLabel.getStyleClass().remove("warning-text");
        }
    }

    /** Highest Java version needed by the selected versions. */
    private int requiredJava() {
        int need = 17;
        for (McVersion v : p.mcVersions()) need = Math.max(need, v.java);
        return need;
    }

    private void setStatus(Build tg, String text, String icon, String style) {
        Platform.runLater(() -> {
            Label l = status.get(tg);
            if (l == null) return;
            l.setText(text);
            FontIcon i = icon == null ? null : Ui.icon(icon);
            if (i != null && style != null) i.getStyleClass().add(style);
            l.setGraphic(i);
        });
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

    private void start(boolean build) {
        if (running) return;
        view.ws.validate();
        long errors = Validator.count(view.ws.problems(), Validator.Severity.ERROR);
        if (errors > 0 && !Ui.confirm(view.window(), t("build.has_errors", errors), t("build.has_errors_text"))) return;
        List<Build> selected = new ArrayList<>();
        checks.forEach((k, v) -> {
            if (v.isSelected()) selected.add(k);
        });
        if (selected.isEmpty()) return;

        // Generate on the UI thread so the model is not read while it is being edited.
        Map<Build, GenContext> contexts = new LinkedHashMap<>();
        for (Build tg : selected) {
            try {
                contexts.put(tg, Generators.generate(p, tg));
            } catch (Exception ex) {
                log("[" + tg.key() + "] " + t("build.generate_failed") + ": " + ex);
                setStatus(tg, t("build.status_failed"), "mdi2a-alert-circle-outline", "icon-danger");
            }
        }
        view.main.save();
        Path dir = view.ws.dir();
        Path jdk = BuildService.findJavaHome(view.main.settings.jdkPath);
        boolean gradle = build && runGradle.isSelected();

        running = true;
        exportB.setDisable(true);
        buildB.setDisable(true);
        cancelB.setDisable(false);
        progress.setVisible(true);
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        for (Build tg : contexts.keySet()) setStatus(tg, t("build.status_waiting"), "mdi2c-clock-outline", null);

        Thread th = new Thread(() -> {
            int ok = 0, failed = 0;
            for (var en : contexts.entrySet()) {
                Build tg = en.getKey();
                GenContext ctx = en.getValue();
                log("");
                log("== " + t("target." + tg.target().id) + (tg.target().versioned() ? " " + tg.mc().id : "") + " ==");
                setStatus(tg, t("build.status_running"), "mdi2p-progress-clock", "icon-accent");
                try {
                    Path out = Exporter.outputDir(dir, tg);
                    Exporter.writeFolder(ctx.files, out);
                    log(t("build.wrote", ctx.files.size(), out));
                    for (String n : ctx.notes) log("  " + t("build.note") + ": " + n);
                    Path dist = Exporter.distDir(dir);
                    if (!tg.target().usesGradle()) {
                        Path zip = dist.resolve(Exporter.packFileName(p, tg));
                        Exporter.writeZip(ctx.files, zip);
                        log(t("build.packed", zip));
                        setStatus(tg, zip.getFileName().toString(), "mdi2c-check-circle-outline", "icon-success");
                    } else if (gradle) {
                        if (jdk == null) throw new IllegalStateException(t("build.jdk_missing", tg.mc().java));
                        log(t("build.gradle_start", out));
                        cancel = BuildService.newCancellation();
                        BuildService.run(out, jdk, List.of("build"), List.of(), this::log, cancel.token());
                        Path jar = BuildService.findJar(out);
                        if (jar == null) throw new IllegalStateException(t("build.no_jar"));
                        Files.createDirectories(dist);
                        Path target = dist.resolve(Exporter.jarFileName(p, tg));
                        Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING);
                        log(t("build.jar", target));
                        setStatus(tg, target.getFileName().toString(), "mdi2c-check-circle-outline", "icon-success");
                    } else {
                        setStatus(tg, t("build.status_exported"), "mdi2c-check-circle-outline", "icon-success");
                    }
                    ok++;
                } catch (Throwable ex) {
                    failed++;
                    String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                    log(t("build.failed", msg));
                    Throwable c = ex.getCause();
                    while (c != null) {
                        log("  " + c);
                        c = c.getCause();
                    }
                    setStatus(tg, t("build.status_failed"), "mdi2a-alert-circle-outline", "icon-danger");
                    if (ex instanceof org.gradle.tooling.BuildCancelledException) break;
                }
            }
            log("");
            log(t("build.done", ok, failed));
            int fok = ok, ffail = failed;
            Platform.runLater(() -> {
                running = false;
                cancel = null;
                exportB.setDisable(false);
                buildB.setDisable(false);
                cancelB.setDisable(true);
                progress.setVisible(false);
                if (ffail == 0 && fok > 0) progress.setProgress(1);
            });
        }, "modstudio-build");
        th.setDaemon(true);
        th.start();
    }

    // ------------------------------------------------------------------ preview tab

    private Node previewTab() {
        previewTarget.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Build tg) {
                if (tg == null) return "";
                return t("target." + tg.target().id) + (tg.target().versioned() ? "  " + tg.mc().id : "");
            }

            @Override
            public Build fromString(String s) {
                return null;
            }
        });
        previewTarget.valueProperty().addListener((o, a, b) -> generatePreview());
        Button refresh = Ui.button(t("build.refresh"), "mdi2r-refresh", this::generatePreview);
        tree.setShowRoot(false);
        tree.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> showFile(b));
        tree.setPrefWidth(340);
        viewer.getChildren().setAll(Ui.emptyState("mdi2f-file-tree-outline", t("build.preview_title"), t("build.preview_text")));
        SplitPane split = new SplitPane(tree, viewer);
        split.setDividerPositions(0.3);
        VBox.setVgrow(split, Priority.ALWAYS);
        VBox box = new VBox(8, Ui.hbox(8, previewTarget, refresh, previewInfo), split);
        box.setPadding(new Insets(12, 8, 8, 8));
        return box;
    }

    private void refreshPreview() {
        List<Build> ts = p.builds();
        Build cur = previewTarget.getValue();
        previewTarget.getItems().setAll(ts);
        if (cur != null && ts.contains(cur)) {
            previewTarget.setValue(cur);
            generatePreview();
        } else if (!ts.isEmpty()) previewTarget.setValue(ts.get(0));
    }

    private void generatePreview() {
        Build tg = previewTarget.getValue();
        if (tg == null) return;
        try {
            GenContext ctx = Generators.generate(p, tg);
            previewFiles = ctx.files;
            TreeItem<String> root = new TreeItem<>("");
            Map<String, TreeItem<String>> dirs = new HashMap<>();
            for (String path : ctx.files.paths()) {
                String[] parts = path.split("/");
                TreeItem<String> parent = root;
                StringBuilder acc = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    acc.append(parts[i]).append('/');
                    TreeItem<String> pp = parent;
                    parent = dirs.computeIfAbsent(acc.toString(), k -> {
                        TreeItem<String> d = new TreeItem<>(k, Ui.icon("mdi2f-folder-outline"));
                        pp.getChildren().add(d);
                        return d;
                    });
                }
                parent.getChildren().add(new TreeItem<>(path, Ui.icon("mdi2f-file-outline")));
            }
            tree.setCellFactory(tv -> new TreeCell<>() {
                @Override
                protected void updateItem(String s, boolean empty) {
                    super.updateItem(s, empty);
                    if (empty || s == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    String n = s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
                    setText(n.substring(n.lastIndexOf('/') + 1));
                    setGraphic(getTreeItem() == null ? null : getTreeItem().getGraphic());
                }
            });
            expand(root, 3);
            tree.setRoot(root);
            String notes = ctx.notes.isEmpty() ? "" : "   |   " + String.join("  ", ctx.notes);
            previewInfo.setText(t("build.preview_count", ctx.files.size()) + notes);
        } catch (Exception ex) {
            previewInfo.setText(t("build.generate_failed") + ": " + ex.getMessage());
        }
    }

    private static void expand(TreeItem<String> item, int depth) {
        if (depth <= 0) return;
        item.setExpanded(true);
        for (TreeItem<String> c : item.getChildren()) expand(c, depth - 1);
    }

    private void showFile(TreeItem<String> item) {
        if (item == null || previewFiles == null || item.getValue().endsWith("/")) return;
        String path = item.getValue();
        byte[] data = previewFiles.get(path);
        if (data == null) return;
        if (path.endsWith(".png")) {
            try {
                int[] wh = new int[2];
                int[] px = Images.read(data, wh);
                ImageView iv = new ImageView(Ui.pixelImage(px, wh[0], wh[1], 256));
                iv.setSmooth(false);
                viewer.getChildren().setAll(new StackPane(iv));
            } catch (Exception ex) {
                viewer.getChildren().setAll(Ui.muted(ex.getMessage()));
            }
            return;
        }
        if (path.endsWith(".ogg") || path.endsWith(".jar")) {
            viewer.getChildren().setAll(Ui.muted(t("build.binary", data.length)));
            return;
        }
        String lang = path.endsWith(".java") ? "java" : path.endsWith(".json") || path.endsWith(".mcmeta") ? "json"
                : path.endsWith(".mcfunction") ? "mcfunction" : path.endsWith(".js") ? "js" : "plain";
        CodeEditor ed = new CodeEditor(lang);
        ed.setText(new String(data, StandardCharsets.UTF_8));
        ed.setEditable(false);
        viewer.getChildren().setAll(ed);
    }
}
