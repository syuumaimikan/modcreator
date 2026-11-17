package modstudio.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import modstudio.core.Target;
import modstudio.i18n.I18n;

import java.nio.file.Files;
import java.nio.file.Path;

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
        Button sampleB = Ui.button(I18n.t("action.open_sample"), "mdi2b-book-open-variant", main::openSample);
        for (Button b : new Button[]{newB, openB, sampleB}) {
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

        VBox left = new VBox(14, title, tagline, new Region(), newB, openB, sampleB, Ui.spacer(), langRow, settingsB);
        left.setPadding(new Insets(40, 32, 28, 40));
        left.setPrefWidth(340);
        left.setMinWidth(300);
        left.getStyleClass().add("home-left");

        // Right column: recent projects and platforms
        Label recentTitle = Ui.label(I18n.t("home.recent"), "title-4");
        ListView<String> recent = new ListView<>();
        recent.getItems().setAll(main.settings.recent);
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
                Label path = Ui.label(exists ? p.getParent().toString() : I18n.t("home.missing", p.getParent()), "text-muted", "text-small");
                VBox box = new VBox(2, n, path);
                HBox row = Ui.hbox(12, Ui.icon("mdi2f-folder-outline", 20), box);
                row.setPadding(new Insets(6, 4, 6, 4));
                setGraphic(row);
                setText(null);
                setDisable(false);
                setOpacity(exists ? 1 : 0.55);
                MenuItem remove = new MenuItem(I18n.t("home.remove_recent"));
                remove.setOnAction(e -> {
                    main.settings.recent.remove(s);
                    main.settings.save();
                    recent.getItems().remove(s);
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

        VBox right = new VBox(12, recentTitle, recent, platformsTitle, platforms, hint);
        right.setPadding(new Insets(40, 40, 28, 32));
        HBox.setHgrow(right, Priority.ALWAYS);

        setLeft(left);
        setCenter(right);
    }

    private void openSelected(ListView<String> list) {
        String s = list.getSelectionModel().getSelectedItem();
        if (s == null) return;
        Path p = Path.of(s);
        if (Files.exists(p)) main.open(p);
    }
}
