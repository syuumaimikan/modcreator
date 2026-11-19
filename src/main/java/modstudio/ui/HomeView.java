package modstudio.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import modstudio.App;
import modstudio.core.Target;
import modstudio.i18n.I18n;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Start screen: create, open and recent projects. */
public final class HomeView extends BorderPane {
    private final MainWindow main;

    public HomeView(MainWindow main) {
        this.main = main;
        getStyleClass().add("home");

        // Left column: identity and main actions
        Label title = Ui.label("ModStudio", "home-title");
        Label tagline = Ui.muted(I18n.t("home.tagline"));
        tagline.setMaxWidth(300);

        Button newB = Ui.accent(I18n.t("action.new_project"), "mdi2f-folder-plus-outline", main::newProject);
        Button openB = Ui.button(I18n.t("action.open_project"), "mdi2f-folder-open", main::openDialog);
        Button importB = Ui.button(I18n.t("action.import_artifact"), "mdi2i-import", main::importArtifact);
        Button sampleB = Ui.button(I18n.t("action.open_sample"), "mdi2b-book-open-variant", main::openSample);
        Button rpgSampleB = Ui.button(I18n.t("action.open_rpg_sample"), "mdi2s-shield-star-outline", main::openRpgSample);
        for (Button b : new Button[]{newB, openB, importB, sampleB, rpgSampleB}) {
            b.setMaxWidth(Double.MAX_VALUE);
            b.setAlignment(Pos.CENTER_LEFT);
        }
        Button settingsB = Ui.button(I18n.t("action.settings"), "mdi2c-cog-outline", main::openSettings);
        settingsB.getStyleClass().add("flat");

        ComboBox<String> lang = Ui.combo(I18n.LANGUAGES.keySet().stream().toList(), I18n.LANGUAGES::get);
        lang.setValue(I18n.language());
        lang.valueProperty().addListener((o, a, b) -> {
            if (b == null || b.equals(I18n.language())) return;
            main.settings.language = b;
            main.settings.save();
            I18n.setLanguage(b);
            main.reloadUi();
        });
        HBox langRow = Ui.hbox(8, Ui.icon("mdi2t-translate"), lang);

        VBox left = new VBox(14, title, tagline, new Region(), newB, openB, importB, sampleB, rpgSampleB, Ui.spacer(), langRow, settingsB);
        left.setPadding(new Insets(40, 32, 28, 40));
        left.setPrefWidth(340);
        left.setMinWidth(300);
        left.getStyleClass().add("home-left");

        // Right column: recent projects and platforms
        Label recentTitle = Ui.label(I18n.t("home.recent"), "title-4");
        TextField recentFilter = new TextField();
        recentFilter.setPromptText(I18n.t("home.filter_recent"));
        recentFilter.setPrefWidth(220);
        HBox recentHeader = Ui.hbox(10, recentTitle, Ui.spacer(), recentFilter);
        recentHeader.setAlignment(Pos.CENTER_LEFT);

        ListView<String> recent = new ListView<>();
        Map<String, String> metaSummaryCache = new HashMap<>();
        Runnable refreshRecent = () -> {
            String q = recentFilter.getText() == null ? "" : recentFilter.getText().trim().toLowerCase(Locale.ROOT);
            recent.getItems().setAll(main.settings.recent.stream()
                    .filter(s -> q.isEmpty() || s.toLowerCase(Locale.ROOT).contains(q)
                            || recentMeta(s, metaSummaryCache).toLowerCase(Locale.ROOT).contains(q))
                    .toList());
        };
        recentFilter.textProperty().addListener((o, a, b) -> refreshRecent.run());
        refreshRecent.run();

        recent.setPlaceholder(Ui.muted(I18n.t("home.no_recent")));
        recent.getStyleClass().add("recent-list");
        recent.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setGraphic(null);
                    setText(null);
                    setContextMenu(null);
                    return;
                }
                Path p = Path.of(s);
                boolean exists = Files.exists(p);
                String name = p.getFileName().toString().replaceFirst("\\.msproj$", "");
                Label n = Ui.label(name, "recent-name");
                String metaText = exists ? recentMeta(s, metaSummaryCache) : "";
                Label meta = metaText.isEmpty() ? null : Ui.label(metaText, "chip");
                Label path = Ui.label(exists ? p.getParent().toString() : I18n.t("home.missing", p.getParent()), "text-muted", "text-small");
                VBox box = new VBox(2, meta == null ? n : Ui.hbox(8, n, meta), path);
                HBox.setHgrow(box, Priority.ALWAYS);

                Button openDir = Ui.iconButton("mdi2f-folder-open-outline", I18n.t("home.open_folder"), () -> {
                    if (p.getParent() != null) App.hosts().showDocument(p.getParent().toUri().toString());
                });
                openDir.setDisable(!exists || p.getParent() == null);
                Button removeBtn = Ui.iconButton("mdi2c-close", I18n.t("home.remove_recent"), () -> {
                    main.settings.recent.remove(s);
                    main.settings.save();
                    refreshRecent.run();
                });

                HBox row = Ui.hbox(10, Ui.icon("mdi2f-folder-outline", 20), box, openDir, removeBtn);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 4, 6, 4));
                setGraphic(row);
                setText(null);
                setDisable(false);
                setOpacity(exists ? 1 : 0.55);
                MenuItem remove = new MenuItem(I18n.t("home.remove_recent"));
                remove.setOnAction(e -> {
                    main.settings.recent.remove(s);
                    main.settings.save();
                    refreshRecent.run();
                });
                setContextMenu(new ContextMenu(remove));
            }
        });
        recent.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) openSelected(recent);
        });
        recent.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) openSelected(recent);
        });
        VBox.setVgrow(recent, Priority.ALWAYS);

        Label platformsTitle = Ui.label(I18n.t("home.platforms"), "title-4");
        FlowPane platforms = new FlowPane(8, 8);
        for (Target t : Target.values()) {
            Label chip = Ui.label(I18n.t("target." + t.id), "chip");
            chip.setTooltip(new Tooltip(I18n.t("target." + t.id + ".desc")));
            platforms.getChildren().add(chip);
        }
        Label hint = Ui.help(I18n.t("home.hint"));

        VBox right = new VBox(12, recentHeader, recent, platformsTitle, platforms, hint);
        right.setPadding(new Insets(40, 40, 28, 32));
        HBox.setHgrow(right, Priority.ALWAYS);

        setLeft(left);
        setCenter(right);
        setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        setOnDragDropped(e -> {
            var db = e.getDragboard();
            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                File f = db.getFiles().get(0);
                if (f.getName().endsWith(".msproj")) main.open(f.toPath());
                else main.importArtifact(f.toPath());
                e.setDropCompleted(true);
            }
            e.consume();
        });
    }

    private void openSelected(ListView<String> list) {
        String s = list.getSelectionModel().getSelectedItem();
        if (s == null) return;
        Path p = Path.of(s);
        if (Files.exists(p)) main.open(p);
    }

    private static String recentMeta(String filePath, Map<String, String> cache) {
        return cache.computeIfAbsent(filePath, fp -> {
            try {
                Path p = Path.of(fp);
                if (!Files.exists(p) || Files.size(p) > 4_000_000) return "";
                JsonElement root = JsonParser.parseString(Files.readString(p));
                if (!root.isJsonObject()) return "";
                JsonObject meta = root.getAsJsonObject().getAsJsonObject("meta");
                if (meta == null) return "";
                String ver = meta.has("mcVersion") ? meta.get("mcVersion").getAsString() : "";
                String modId = meta.has("modId") ? meta.get("modId").getAsString() : "";
                return modId.isEmpty() ? ver : ver.isEmpty() ? modId : modId + " · MC " + ver;
            } catch (Exception ex) {
                return "";
            }
        });
    }
}
