package modstudio.ui;

import javafx.scene.Scene;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import modstudio.core.*;
import modstudio.i18n.I18n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Top level window: shows the home screen or an open project. */
public final class MainWindow {
    public final Stage stage;
    public final AppSettings settings;
    private final BorderPane root = new BorderPane();
    private final Scene scene;
    private Workspace ws;
    private WorkspaceView wsView;

    public MainWindow(Stage stage, AppSettings settings) {
        this.stage = stage;
        this.settings = settings;
        scene = new Scene(root, settings.width, settings.height);
        Ui.style(scene);
        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(620);
        stage.setMaximized(settings.maximized);
        stage.getIcons().add(Ui.pixelImage(TextureTemplates.generate("gem", 0xFF3FA34D), 16, 16, 64));
        stage.setOnCloseRequest(e -> {
            if (!closeProject()) {
                e.consume();
                return;
            }
            settings.maximized = stage.isMaximized();
            if (!stage.isMaximized()) {
                settings.width = stage.getWidth();
                settings.height = stage.getHeight();
            }
            settings.save();
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), this::save);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), this::newProject);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), this::openDialog);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN), () -> {
            if (wsView != null) wsView.openGlobalSearch();
        });
        KeyCombination undoKey = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
        KeyCombination redoKey1 = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
        KeyCombination redoKey2 = new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN);
        root.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (ws == null || wsView == null) return;
            var focus = scene.getFocusOwner();
            if (focus instanceof TextInputControl || (focus != null && "CodeArea".equals(focus.getClass().getSimpleName()))) return;
            if (undoKey.match(e)) {
                if (ws.undo()) e.consume();
            } else if (redoKey1.match(e) || redoKey2.match(e)) {
                if (ws.redo()) e.consume();
            }
        });
        showHome();
    }

    public void show() {
        stage.show();
    }

    public Scene scene() {
        return scene;
    }

    public Workspace workspace() {
        return ws;
    }

    public WorkspaceView workspaceView() {
        return wsView;
    }

    public void showHome() {
        ws = null;
        wsView = null;
        root.setCenter(new HomeView(this));
        stage.setTitle("ModStudio");
    }

    public void updateTitle() {
        if (ws == null) {
            stage.setTitle("ModStudio");
            return;
        }
        stage.setTitle((ws.dirtyProperty().get() ? "* " : "") + ws.project.meta.name + " - ModStudio");
    }

    public void newProject() {
        if (!closeProject()) return;
        NewProjectDialog.show(this).ifPresent(r -> {
            try {
                Files.createDirectories(r.file().getParent());
                ProjectIO.save(r.project(), r.file());
                openWorkspace(r.project(), r.file());
            } catch (IOException ex) {
                Ui.error(stage, I18n.t("error.create_project"), ex);
            }
        });
    }

    public void openDialog() {
        if (!closeProject()) return;
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.t("action.open_project"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.t("file.project"), "*.msproj"));
        Path dir = AppSettings.projectsDir();
        if (Files.isDirectory(dir)) fc.setInitialDirectory(dir.toFile());
        var f = fc.showOpenDialog(stage);
        if (f != null) open(f.toPath());
    }

    public void open(Path file) {
        if (!closeProject()) return;
        try {
            Project p = ProjectIO.load(file);
            openWorkspace(p, file);
        } catch (Exception ex) {
            Ui.error(stage, I18n.t("error.open_project", file.getFileName()), ex);
        }
    }

    /** Prompts to choose an existing mod jar or pack zip/folder and imports it as an editable project. */
    public void importArtifact() {
        if (!closeProject()) return;
        Alert mode = new Alert(Alert.AlertType.CONFIRMATION);
        Ui.styleDialog(mode, stage);
        mode.setTitle(I18n.t("import.title"));
        mode.setHeaderText(I18n.t("import.choose_source"));
        mode.setContentText(I18n.t("import.choose_hint"));
        ButtonType pickFile = new ButtonType(I18n.t("import.pick_file"), ButtonBar.ButtonData.YES);
        ButtonType pickFolder = new ButtonType(I18n.t("import.pick_folder"), ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType(I18n.t("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        mode.getButtonTypes().setAll(pickFile, pickFolder, cancel);
        ButtonType chosen = mode.showAndWait().orElse(cancel);
        if (chosen == cancel) return;
        Path path = null;
        if (chosen == pickFile) {
            FileChooser fc = new FileChooser();
            fc.setTitle(I18n.t("import.title"));
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter(I18n.t("import.filter"), "*.jar", "*.zip", "*.mcaddon", "*.mcpack"),
                    new FileChooser.ExtensionFilter("*", "*.*"));
            var f = fc.showOpenDialog(stage);
            if (f != null) path = f.toPath();
        } else {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle(I18n.t("import.pick_folder"));
            var f = dc.showDialog(stage);
            if (f != null) path = f.toPath();
        }
        if (path != null) importArtifact(path);
    }

    /** Imports an existing mod jar, plugin jar, data/resource pack or Bedrock add-on into a new workspace. */
    public void importArtifact(Path source) {
        if (!closeProject()) return;
        ProjectImporter.Report report;
        try {
            report = ProjectImporter.importArtifact(source);
        } catch (Exception ex) {
            Ui.error(stage, I18n.t("import.failed", source.getFileName()), ex);
            return;
        }
        Project p = report.project();
        Dialog<Path> d = new Dialog<>();
        Ui.styleDialog(d, stage);
        d.setTitle(I18n.t("import.title"));
        d.setHeaderText(I18n.t("import.summary_header", source.getFileName()));
        ButtonType ok = new ButtonType(I18n.t("action.create"), ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, new ButtonType(I18n.t("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE));

        TextField name = new TextField(p.meta.name);
        TextField modId = new TextField(p.meta.modId);
        Path base = AppSettings.projectsDir();
        String folder = p.meta.modId;
        int idx = 2;
        while (Files.exists(base.resolve(folder))) folder = p.meta.modId + "_" + idx++;
        TextField destDir = new TextField(base.resolve(folder).toString());

        StringBuilder summary = new StringBuilder();
        String kindKey = switch (ProjectScope.kind(p)) {
            case MOD -> "mod";
            case PLUGIN -> "plugin";
            case DATAPACK -> "datapack";
            case RESOURCEPACK -> "resourcepack";
            case BEDROCK -> "bedrock";
            case MIXED -> "custom";
        };
        summary.append(I18n.t("newproject.kind")).append(": ").append(I18n.t("newproject.kind." + kindKey)).append('\n');
        summary.append(I18n.t("field.targets")).append(": ").append(String.join(", ", p.meta.targets)).append('\n');
        if (!p.meta.sourceMcVersion.isBlank() && !p.meta.sourceMcVersion.equals(p.meta.mcVersion)) {
            summary.append(I18n.t("import.source_version")).append(": ").append(p.meta.sourceMcVersion).append('\n');
        }
        summary.append(I18n.t("field.mc_versions")).append(": ").append(String.join(", ", p.meta.mcVersions)).append("\n\n");
        if (report.counts().isEmpty()) {
            summary.append(I18n.t("import.empty")).append('\n');
        } else {
            for (var en : report.counts().entrySet()) {
                String label = en.getKey().equals("textures") ? I18n.t("page.textures") : I18n.t("types." + en.getKey());
                summary.append("  ").append(label).append(": ").append(en.getValue()).append('\n');
            }
        }
        if (!report.notes().isEmpty()) {
            summary.append('\n');
            for (String n : report.notes()) summary.append("* ").append(n).append('\n');
        }

        TextArea log = new TextArea(summary.toString());
        log.setEditable(false);
        log.setPrefRowCount(10);
        log.setPrefColumnCount(52);

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.addRow(0, new Label(I18n.t("field.project_name")), name);
        g.addRow(1, new Label(I18n.t("field.mod_id")), modId);
        var browseDest = Ui.button(I18n.t("action.browse"), "mdi2f-folder-open", () -> {
            DirectoryChooser dc = new DirectoryChooser();
            var f = dc.showDialog(stage);
            if (f != null) destDir.setText(f.getAbsolutePath());
        });
        HBox destRow = new HBox(6, destDir, browseDest);
        HBox.setHgrow(destDir, Priority.ALWAYS);
        g.addRow(2, new Label(I18n.t("field.location")), destRow);
        CheckBox upgradeCheck = new CheckBox(I18n.t("import.upgrade_check"));
        boolean isLegacy = !p.meta.sourceMcVersion.isBlank() && McVersion.orNull(p.meta.sourceMcVersion) == null;
        upgradeCheck.setSelected(isLegacy);
        ComboBox<McVersion> upgradeMc = new ComboBox<>();
        upgradeMc.getItems().setAll(McVersion.values());
        upgradeMc.setValue(McVersion.V1_21_1);
        upgradeMc.disableProperty().bind(upgradeCheck.selectedProperty().not());
        HBox upgRow = new HBox(8, upgradeCheck, upgradeMc);
        upgRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        g.addRow(3, new Label(I18n.t("import.upgrade_label")), upgRow);

        VBox box = new VBox(10, g, log);
        box.setPadding(new Insets(4));
        d.getDialogPane().setContent(box);

        var okBtn = d.getDialogPane().lookupButton(ok);
        modId.textProperty().addListener((o, a, b) -> {
            boolean bad = !Ids.MODID.matcher(b.trim()).matches() || name.getText().isBlank();
            Ui.setDanger(modId, !Ids.MODID.matcher(b.trim()).matches());
            okBtn.setDisable(bad);
        });
        name.textProperty().addListener((o, a, b) -> okBtn.setDisable(b.isBlank() || !Ids.MODID.matcher(modId.getText().trim()).matches()));
        d.setResultConverter(bt -> {
            if (bt != ok) return null;
            p.meta.name = name.getText().trim();
            String nid = modId.getText().trim();
            if (!nid.equals(p.meta.modId)) {
                Refactor.renameNamespace(p, p.meta.modId, nid);
                p.meta.modId = nid;
            }
            if (upgradeCheck.isSelected() && upgradeMc.getValue() != null) {
                ProjectUpgrader.upgrade(p, upgradeMc.getValue(), null);
            }
            return Path.of(destDir.getText().trim()).resolve(p.meta.modId + ".msproj");
        });
        d.showAndWait().ifPresent(file -> {
            try {
                if (!settings.transientMode) {
                    Files.createDirectories(file.getParent());
                    ProjectIO.save(p, file);
                }
                openWorkspace(p, file);
            } catch (IOException ex) {
                Ui.error(stage, I18n.t("error.create_project"), ex);
            }
        });
    }

    /** Copies the sample project into the projects folder and opens it. */
    public void openSample() {
        openGeneratedSample(SampleProject.create());
    }

    /** Copies the flagship Action-RPG sample project into the projects folder and opens it. */
    public void openRpgSample() {
        openGeneratedSample(RpgSampleProject.create());
    }

    private void openGeneratedSample(Project p) {
        if (!closeProject()) return;
        Path base = AppSettings.projectsDir();
        String folder = p.meta.modId;
        int i = 2;
        while (Files.exists(base.resolve(folder))) folder = p.meta.modId + "_" + i++;
        Path file = base.resolve(folder).resolve(p.meta.modId + ".msproj");
        try {
            if (!settings.transientMode) {
                Files.createDirectories(file.getParent());
                ProjectIO.save(p, file);
            }
            openWorkspace(p, file);
        } catch (IOException ex) {
            Ui.error(stage, I18n.t("error.create_project"), ex);
        }
    }

    public void openWorkspace(Project p, Path file) {
        ws = new Workspace(p, file, settings);
        wsView = new WorkspaceView(this, ws);
        root.setCenter(wsView);
        ws.dirtyProperty().addListener((o, a, b) -> updateTitle());
        updateTitle();
        if (!settings.transientMode) settings.addRecent(file);
    }

    public void save() {
        if (ws == null) return;
        try {
            ws.save();
        } catch (IOException ex) {
            Ui.error(stage, I18n.t("error.save"), ex);
        }
    }

    /** Returns false when the user cancelled. */
    public boolean closeProject() {
        if (ws == null) return true;
        if (ws.dirtyProperty().get()) {
            if (settings.autosave) {
                save();
            } else {
                Alert a = new Alert(Alert.AlertType.CONFIRMATION);
                Ui.styleDialog(a, stage);
                a.setTitle("ModStudio");
                a.setHeaderText(I18n.t("dialog.unsaved", ws.project.meta.name));
                ButtonType saveB = new ButtonType(I18n.t("action.save"), ButtonBar.ButtonData.YES);
                ButtonType discard = new ButtonType(I18n.t("action.discard"), ButtonBar.ButtonData.NO);
                ButtonType cancel = new ButtonType(I18n.t("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
                a.getButtonTypes().setAll(saveB, discard, cancel);
                ButtonType r = a.showAndWait().orElse(cancel);
                if (r == cancel) return false;
                if (r == saveB) save();
            }
        }
        if (wsView != null) wsView.dispose();
        ws = null;
        wsView = null;
        return true;
    }

    public void closeToHome() {
        if (closeProject()) showHome();
    }

    /** Rebuilds the views, e.g. after the UI language changed. */
    public void reloadUi() {
        Ui.styleRoot(root);
        if (ws != null) {
            String page = wsView.currentPage();
            wsView.dispose();
            wsView = new WorkspaceView(this, ws);
            root.setCenter(wsView);
            wsView.show(page);
        } else {
            showHome();
        }
    }

    public void openSettings() {
        String oldLang = settings.language;
        String oldTheme = settings.theme;
        if (SettingsDialog.show(this)) {
            settings.save();
            boolean langChanged = !oldLang.equals(settings.language);
            if (langChanged) I18n.setLanguage(settings.language);
            if (langChanged || !oldTheme.equals(settings.theme)) reloadUi();
        }
    }
}
