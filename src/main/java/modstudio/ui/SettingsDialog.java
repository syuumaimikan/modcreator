package modstudio.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import modstudio.build.BuildService;
import modstudio.i18n.I18n;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

        ComboBox<String> aiProvider = Ui.combo(List.of("builtin", "openai", "gemini", "anthropic"), p -> I18n.t("ai.provider." + p));
        aiProvider.setValue(s.aiProvider == null || s.aiProvider.isBlank() ? "builtin" : s.aiProvider);
        TextField aiBaseUrl = new TextField(s.aiBaseUrl == null ? "" : s.aiBaseUrl);
        aiBaseUrl.setPromptText("https://api.openai.com/v1  (or http://localhost:11434/v1)");
        TextField aiModel = new TextField(s.aiModel == null ? "" : s.aiModel);
        aiModel.setPromptText("gpt-4o-mini / gemini-2.5-flash / claude-sonnet-4-5");
        PasswordField aiKey = new PasswordField();
        aiKey.setText(s.aiApiKey == null ? "" : s.aiApiKey);
        aiKey.setPromptText(I18n.t("ai.api_key_prompt"));
        TextField aiKeyPlain = new TextField();
        aiKeyPlain.textProperty().bindBidirectional(aiKey.textProperty());
        aiKeyPlain.setPromptText(I18n.t("ai.api_key_prompt"));
        aiKeyPlain.setVisible(false);
        aiKeyPlain.setManaged(false);
        ToggleButton revealKey = new ToggleButton("", Ui.icon("mdi2e-eye-outline"));
        revealKey.setTooltip(new Tooltip(I18n.t("ai.toggle_key")));
        revealKey.selectedProperty().addListener((o, a, show) -> {
            aiKeyPlain.setVisible(show);
            aiKeyPlain.setManaged(show);
            aiKey.setVisible(!show);
            aiKey.setManaged(!show);
            revealKey.setGraphic(Ui.icon(show ? "mdi2e-eye-off-outline" : "mdi2e-eye-outline"));
        });
        StackPane keyStack = new StackPane(aiKey, aiKeyPlain);
        HBox.setHgrow(keyStack, Priority.ALWAYS);
        HBox aiKeyRow = new HBox(6, keyStack, revealKey);
        aiKeyRow.setAlignment(Pos.CENTER_LEFT);

        Label aiTestStatus = Ui.help("");
        Button aiTestBtn = Ui.button(I18n.t("ai.test_connection"), "mdi2l-lan-connect", null);
        aiTestBtn.setOnAction(ev -> testConnection(aiProvider.getValue(), aiBaseUrl.getText(), aiModel.getText(), aiKey.getText(), aiTestBtn, aiTestStatus));

        Runnable updateAiEnabled = () -> {
            boolean builtin = "builtin".equals(aiProvider.getValue());
            aiBaseUrl.setDisable(builtin);
            aiModel.setDisable(builtin);
            aiKey.setDisable(builtin);
            aiKeyPlain.setDisable(builtin);
            revealKey.setDisable(builtin);
            aiTestBtn.setDisable(builtin);
            if (builtin) aiTestStatus.setText(I18n.t("ai.builtin_ready"));
            else aiTestStatus.setText("");
        };
        aiProvider.valueProperty().addListener((o, a, b) -> {
            if ("gemini".equals(b) && (aiModel.getText().isBlank() || aiModel.getText().startsWith("gpt") || aiModel.getText().startsWith("claude"))) {
                aiModel.setText("gemini-2.5-flash");
            } else if ("anthropic".equals(b) && (aiModel.getText().isBlank() || aiModel.getText().startsWith("gpt") || aiModel.getText().startsWith("gemini"))) {
                aiModel.setText("claude-sonnet-4-5");
            } else if ("openai".equals(b) && (aiModel.getText().isBlank() || aiModel.getText().startsWith("gemini") || aiModel.getText().startsWith("claude"))) {
                aiModel.setText("gpt-4o-mini");
            }
            updateAiEnabled.run();
        });
        updateAiEnabled.run();

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(12);
        g.addRow(0, Ui.label(I18n.t("settings.language"), "form-label"), lang);
        g.addRow(1, Ui.label(I18n.t("settings.theme"), "form-label"), theme);
        g.addRow(2, Ui.label(I18n.t("settings.jdk"), "form-label"), new VBox(4, jdkRow, detected, Ui.help(I18n.t("settings.jdk_help"))));
        g.addRow(3, new Label(), autosave);
        aiTestBtn.setMinWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(aiProvider, Priority.ALWAYS);
        aiProvider.setMaxWidth(Double.MAX_VALUE);
        HBox providerRow = Ui.hbox(8, aiProvider, aiTestBtn);
        providerRow.setAlignment(Pos.CENTER_LEFT);
        g.addRow(4, Ui.label(I18n.t("ai.provider"), "form-label"), providerRow);
        g.addRow(5, Ui.label(I18n.t("ai.base_url"), "form-label"), aiBaseUrl);
        g.addRow(6, Ui.label(I18n.t("ai.model"), "form-label"), aiModel);
        g.addRow(7, Ui.label(I18n.t("ai.api_key"), "form-label"), new VBox(4, aiKeyRow, aiTestStatus));
        g.getColumnConstraints().addAll(new javafx.scene.layout.ColumnConstraints(140), new javafx.scene.layout.ColumnConstraints(470));

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
            s.aiProvider = aiProvider.getValue();
            s.aiBaseUrl = aiBaseUrl.getText().trim();
            s.aiModel = aiModel.getText().trim();
            s.aiApiKey = aiKey.getText().trim();
        } else {
            Ui.applyTheme(oldTheme);
        }
        return accepted;
    }

    private static void testConnection(String provider, String rawBaseUrl, String model, String rawKey, Button btn, Label status) {
        String base = rawBaseUrl == null ? "" : rawBaseUrl.trim().replaceAll("/+$", "");
        String key = rawKey == null ? "" : rawKey.trim();
        btn.setDisable(true);
        status.setText(I18n.t("ai.testing"));
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
                HttpRequest.Builder req = HttpRequest.newBuilder().timeout(Duration.ofSeconds(10)).GET();
                String url = switch (provider) {
                    case "gemini" -> {
                        String b = base.isEmpty() ? "https://generativelanguage.googleapis.com/v1beta" : base;
                        yield b + "/models?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
                    }
                    case "anthropic" -> {
                        String b = base.isEmpty() ? "https://api.anthropic.com/v1" : base;
                        req.header("x-api-key", key).header("anthropic-version", "2023-06-01");
                        yield b + "/models";
                    }
                    default -> {
                        String b = base.isEmpty() ? "https://api.openai.com/v1" : base;
                        if (!key.isEmpty()) req.header("Authorization", "Bearer " + key);
                        yield b + "/models";
                    }
                };
                HttpResponse<String> resp = http.send(req.uri(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) return I18n.t("ai.test_ok", resp.statusCode());
                return I18n.t("ai.test_fail", "HTTP " + resp.statusCode());
            } catch (Exception ex) {
                return I18n.t("ai.test_fail", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            }
        }).whenComplete((msg, err) -> Platform.runLater(() -> {
            btn.setDisable("builtin".equals(provider));
            status.setText(err != null ? I18n.t("ai.test_fail", err.getMessage()) : msg);
        }));
    }
}
