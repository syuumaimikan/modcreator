package modstudio.ui;

import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
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

    /** Copies the sample project into the projects folder and opens it. */
    public void openSample() {
        if (!closeProject()) return;
        Project p = SampleProject.create();
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
