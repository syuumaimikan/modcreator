package modstudio.ui.pages;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import modstudio.core.Ids;
import modstudio.core.Secrets;
import modstudio.gen.PaperGen;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;
import modstudio.ui.WorkspaceView;

import java.io.IOException;
import java.util.*;

/**
 * API keys, tokens and webhook URLs used by web requests. Values live in a local file next to the project, are
 * masked in the editor and never become part of an export.
 */
public final class SecretsPage extends VBox implements WorkspaceView.Page {
    private final WorkspaceView view;
    private final VBox rows = new VBox(8);
    private final Label where = Ui.help("");
    private final Label saved = Ui.label("", "text-muted");
    private Secrets secrets;

    public SecretsPage(WorkspaceView view) {
        this.view = view;
        getStyleClass().add("page");
        setSpacing(14);
        setPadding(new Insets(20, 24, 24, 24));
        Label title = Ui.label(I18n.t("page.secrets"), "title-2");
        Label intro = Ui.help(I18n.t("secrets.intro"));
        TextField name = new TextField();
        name.setPromptText(I18n.t("secrets.new_name"));
        Button add = Ui.button(I18n.t("secrets.add"), "mdi2p-plus", () -> {
            String n = name.getText().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_.-]", "_");
            if (n.isEmpty()) return;
            if (!secrets.all().containsKey(n)) secrets.put(n, "");
            name.clear();
            save();
            rebuild();
        });
        VBox card = new VBox(10, rows, Ui.hbox(8, name, add), saved);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14));
        Label runtime = Ui.help(I18n.t("secrets.runtime", view.ws.project.meta.modId, PaperGen.pluginName(view.ws.project)));
        getChildren().addAll(title, intro, card, where, runtime);
    }

    @Override
    public void onShow() {
        secrets = new Secrets(view.ws.dir());
        where.setText(I18n.t("secrets.where", secrets.file()));
        rebuild();
    }

    private void rebuild() {
        rows.getChildren().clear();
        Set<String> used = Secrets.referenced(view.ws.project);
        Set<String> names = new TreeSet<>(used);
        names.addAll(secrets.all().keySet());
        if (names.isEmpty()) rows.getChildren().add(Ui.help(I18n.t("secrets.none")));
        for (String n : names) rows.getChildren().add(row(n, used.contains(n)));
    }

    private HBox row(String n, boolean used) {
        Label label = Ui.label(n, "mono");
        label.setMinWidth(200);
        PasswordField masked = new PasswordField();
        TextField plain = new TextField();
        masked.setText(secrets.get(n));
        plain.setText(secrets.get(n));
        plain.setVisible(false);
        plain.setManaged(false);
        masked.textProperty().addListener((o, a, b) -> {
            if (!plain.getText().equals(b)) plain.setText(b);
            secrets.put(n, b);
            save();
        });
        plain.textProperty().addListener((o, a, b) -> {
            if (!masked.getText().equals(b)) masked.setText(b);
        });
        HBox.setHgrow(masked, Priority.ALWAYS);
        HBox.setHgrow(plain, Priority.ALWAYS);
        ToggleButton reveal = new ToggleButton(I18n.t("secrets.reveal"));
        reveal.selectedProperty().addListener((o, a, b) -> {
            plain.setVisible(b);
            plain.setManaged(b);
            masked.setVisible(!b);
            masked.setManaged(!b);
        });
        Label state = Ui.label(used ? (secrets.get(n).isEmpty() ? I18n.t("secrets.missing") : I18n.t("secrets.used")) : I18n.t("secrets.unused"), "text-muted");
        state.setMinWidth(90);
        Button remove = Ui.button("", "mdi2d-delete-outline", () -> {
            secrets.remove(n);
            save();
            rebuild();
        });
        remove.setTooltip(new Tooltip(I18n.t("action.delete")));
        HBox row = Ui.hbox(8, label, masked, plain, reveal, state, remove);
        row.setPadding(new Insets(2, 0, 2, 0));
        return row;
    }

    private void save() {
        try {
            secrets.save();
            saved.setText(I18n.t("secrets.saved"));
        } catch (IOException e) {
            saved.setText(I18n.t("secrets.save_failed", e.getMessage()));
        }
    }

    static String safe(String s) {
        return Ids.toId(s);
    }
}
