package modstudio.ui.pages;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import modstudio.core.Compat;
import modstudio.core.Compat.Level;
import modstudio.core.Target;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;
import modstudio.ui.WorkspaceView;

import java.util.List;
import java.util.Map;

/**
 * Compatibility layer overview: which elements work natively on each export target, which use a fallback that
 * ModStudio generates instead, and which are left out, with the reason for each.
 */
public final class CompatPage extends BorderPane implements WorkspaceView.Page {
    private final WorkspaceView view;
    private final GridPane grid = new GridPane();
    private final FlowPane summary = new FlowPane(8, 8);
    private final VBox details = new VBox(6);
    private final CheckBox onlyIssues = new CheckBox(I18n.t("compat.only_issues"));

    public CompatPage(WorkspaceView view) {
        this.view = view;
        VBox body = new VBox(14);
        body.getStyleClass().add("page");
        body.setPadding(new Insets(20, 24, 24, 24));
        grid.setHgap(6);
        grid.setVgap(4);
        grid.getStyleClass().add("compat-grid");
        onlyIssues.setSelected(true);
        onlyIssues.selectedProperty().addListener((o, a, b) -> rebuild());
        ScrollPane gridScroll = new ScrollPane(grid);
        gridScroll.setFitToHeight(true);
        gridScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        gridScroll.getStyleClass().add("edge-to-edge");
        VBox card = new VBox(10, Ui.hbox(12, onlyIssues, legend()), gridScroll);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14));
        details.getStyleClass().add("card");
        details.setPadding(new Insets(14));
        VBox shims = new VBox(6, Ui.label(I18n.t("compat.shims_title"), "title-4"));
        for (String k : List.of("macro", "random", "sidebar", "menu", "command", "http", "items", "branch", "machine")) {
            shims.getChildren().add(Ui.help("- " + I18n.t("compat.shim." + k)));
        }
        shims.getStyleClass().add("card");
        shims.setPadding(new Insets(14));
        body.getChildren().addAll(Ui.label(I18n.t("page.compat"), "title-2"), Ui.help(I18n.t("compat.intro")), summary, card, details, shims);
        setCenter(Ui.scroll(body));
    }

    private static Node legend() {
        return Ui.hbox(6, badge(Level.FULL), badge(Level.FALLBACK), badge(Level.NONE));
    }

    static Label badge(Level l) {
        Label b = new Label(I18n.t("compat.level." + l.name().toLowerCase()));
        b.getStyleClass().addAll("compat-badge", "compat-" + l.name().toLowerCase());
        return b;
    }

    @Override
    public void onShow() {
        rebuild();
    }

    private void rebuild() {
        List<Compat.Row> rows = Compat.analyze(view.ws.project);
        List<Target> cols = Compat.columns(view.ws.project);
        // summary per target
        summary.getChildren().clear();
        for (Target t : cols) {
            int fb = 0, none = 0;
            for (Compat.Row r : rows) {
                Level l = r.cells().get(t).level();
                if (l == Level.FALLBACK) fb++;
                if (l == Level.NONE) none++;
            }
            Label chip = Ui.label(I18n.t("target." + t.id) + "  " + I18n.t("compat.summary", rows.size() - fb - none, fb, none), "chip");
            summary.getChildren().add(chip);
        }
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        ColumnConstraints first = new ColumnConstraints();
        first.setMinWidth(220);
        grid.getColumnConstraints().add(first);
        grid.add(Ui.label(I18n.t("compat.element"), "form-label"), 0, 0);
        for (int i = 0; i < cols.size(); i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setMinWidth(96);
            cc.setHalignment(javafx.geometry.HPos.CENTER);
            grid.getColumnConstraints().add(cc);
            grid.add(Ui.label(I18n.t("target." + cols.get(i).id), "form-label"), i + 1, 0);
        }
        int y = 1;
        for (Compat.Row r : rows) {
            if (onlyIssues.isSelected() && r.worst() == Level.FULL) continue;
            Label name = new Label(r.element().name);
            name.getStyleClass().add("compat-name");
            Label type = Ui.label(I18n.t("type." + r.element().type().id), "text-muted");
            VBox nameBox = new VBox(0, name, type);
            nameBox.setOnMouseClicked(e -> showDetails(r));
            nameBox.getStyleClass().add("compat-row");
            grid.add(nameBox, 0, y);
            int x = 1;
            for (Map.Entry<Target, Compat.Cell> c : r.cells().entrySet()) {
                Label b = badge(c.getValue().level());
                if (!c.getValue().notes().isEmpty()) Tooltip.install(b, new Tooltip(String.join("\n", c.getValue().notes())));
                b.setOnMouseClicked(e -> showDetails(r));
                grid.add(b, x++, y);
            }
            y++;
        }
        if (y == 1) grid.add(Ui.help(I18n.t("compat.all_full")), 0, 1, cols.size() + 1, 1);
        details.getChildren().setAll(Ui.help(I18n.t("compat.click_hint")));
    }

    private void showDetails(Compat.Row r) {
        details.getChildren().clear();
        Label title = Ui.label(r.element().name + "  (" + I18n.t("type." + r.element().type().id) + ")", "title-4");
        javafx.scene.control.Button open = Ui.button(I18n.t("compat.open_element"), "mdi2p-pencil-outline", () -> view.openElement(r.element().uid));
        details.getChildren().add(Ui.hbox(10, title, Ui.spacer(), open));
        for (Map.Entry<Target, Compat.Cell> c : r.cells().entrySet()) {
            HBox line = Ui.hbox(8, Ui.label(I18n.t("target." + c.getKey().id), "form-label"), badge(c.getValue().level()));
            line.setAlignment(Pos.CENTER_LEFT);
            details.getChildren().add(line);
            for (String n : c.getValue().notes()) {
                Label l = Ui.help(n);
                l.setPadding(new Insets(0, 0, 0, 16));
                details.getChildren().add(l);
            }
        }
    }
}
