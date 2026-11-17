package modstudio.ui;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import modstudio.build.BuildService;
import modstudio.i18n.I18n;

import java.nio.file.Path;
import java.util.List;

/** Application preferences: language, theme, JDK and autosave. */
public final class SettingsDialog {
    private SettingsDialog() {}

    public static boolean show(MainWindow main) {
        AppSettings s = main.settings;
        Dialog<ButtonType> d = new Dialog<>();
        Ui.styleDialog(d, main.stage);
        d.setTitle(I18n.t("settings.title"));
        d.setHeaderText(I18n.t("settings.title"));

        ComboBox<String> lang = Ui.combo(I18n.LANGUAGES.keySet().stream().toList(), I18n.LANGUAGES::get);
        lang.setValue(I18n.language());
        ComboBox<String> theme = Ui.combo(List.of("light", "dark"), t -> I18n.t("settings.theme_" + t));
        theme.setValue(Ui.dark() ? "dark" : "light");
        theme.valueProperty().addListener((o, a, b) -> Ui.applyTheme(b));

        TextField jdk = new TextField(s.jdkPath);
        jdk.setPromptText(I18n.t("settings.jdk_auto"));
        Label detected = Ui.help("");
        Runnable detect = () -> {
            Path home = BuildService.findJavaHome(jdk.getText());
            detected.setText(home == null ? I18n.t("settings.jdk_none")
                    : I18n.t("settings.jdk_found", home, BuildService.javaVersion(home)));
        };
        jdk.textProperty().addListener((o, a, b) -> detect.run());
        detect.run();
        Button browse = Ui.button(I18n.t("action.browse"), "mdi2f-folder-open", () -> {
            DirectoryChooser dc = new DirectoryChooser();
            var f = dc.showDialog(d.getDialogPane().getScene().getWindow());
            if (f != null) jdk.setText(f.getAbsolutePath());
        });
        HBox jdkRow = new HBox(8, jdk, browse);
        HBox.setHgrow(jdk, Priority.ALWAYS);

        CheckBox autosave = new CheckBox(I18n.t("settings.autosave"));
        autosave.setSelected(s.autosave);

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(12);
        g.addRow(0, Ui.label(I18n.t("settings.language"), "form-label"), lang);
        g.addRow(1, Ui.label(I18n.t("settings.theme"), "form-label"), theme);
        g.addRow(2, Ui.label(I18n.t("settings.jdk"), "form-label"), new VBox(4, jdkRow, detected, Ui.help(I18n.t("settings.jdk_help"))));
        g.addRow(3, new Label(), autosave);
        g.getColumnConstraints().addAll(new javafx.scene.layout.ColumnConstraints(130), new javafx.scene.layout.ColumnConstraints(420));

        d.getDialogPane().setContent(g);
        ButtonType ok = new ButtonType(I18n.t("action.ok"), ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, new ButtonType(I18n.t("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE));
        String oldTheme = s.theme;
        boolean accepted = d.showAndWait().orElse(ButtonType.CANCEL) == ok;
        if (accepted) {
            s.language = lang.getValue();
            s.theme = theme.getValue();
            s.jdkPath = jdk.getText().trim();
            s.autosave = autosave.isSelected();
        } else {
            Ui.applyTheme(oldTheme);
        }
        return accepted;
    }
}
