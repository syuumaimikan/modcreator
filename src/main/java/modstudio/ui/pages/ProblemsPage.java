package modstudio.ui.pages;

import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import modstudio.core.Element;
import modstudio.core.Validator;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;
import modstudio.ui.WorkspaceView;
import org.kordamp.ikonli.javafx.FontIcon;

/** All validation results; double-click opens the element. */
public final class ProblemsPage extends BorderPane implements WorkspaceView.Page {
    private final WorkspaceView view;
    private final FilteredList<Validator.Problem> filtered;
    private final ToggleButton errors = new ToggleButton();
    private final ToggleButton warnings = new ToggleButton();
    private final ToggleButton infos = new ToggleButton();

    public ProblemsPage(WorkspaceView view) {
        this.view = view;
        filtered = new FilteredList<>(view.ws.problems());
        for (ToggleButton b : new ToggleButton[]{errors, warnings, infos}) {
            b.setSelected(true);
            b.selectedProperty().addListener((o, a, c) -> filter());
        }
        errors.setGraphic(icon(Validator.Severity.ERROR));
        warnings.setGraphic(icon(Validator.Severity.WARNING));
        infos.setGraphic(icon(Validator.Severity.INFO));
        view.ws.problems().addListener((ListChangeListener<Validator.Problem>) c -> updateCounts());
        updateCounts();

        Button revalidate = Ui.button(t("problems.revalidate"), "mdi2r-refresh", view.ws::validate);
        VBox head = new VBox(4, Ui.label(t("page.problems"), "title-2"), Ui.muted(t("problems.subtitle")));
        HBox bar = Ui.hbox(8, head, Ui.spacer(), errors, warnings, infos, revalidate);
        bar.setPadding(new Insets(20, 24, 12, 24));
        setTop(bar);

        ListView<Validator.Problem> list = new ListView<>(filtered);
        list.setPlaceholder(Ui.emptyState("mdi2c-check-circle-outline", t("problems.none_title"), t("problems.none_text")));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Validator.Problem pr, boolean empty) {
                super.updateItem(pr, empty);
                if (empty || pr == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Element e = pr.elementUid() == null ? null : view.ws.project.byUid(pr.elementUid());
                String where = e == null ? t("problems.project") : t("type." + e.type().id) + "  " + e.name;
                Label msg = new Label(pr.message());
                msg.setWrapText(true);
                VBox text = new VBox(2, msg, Ui.label(where, "text-muted", "text-small"));
                HBox row = Ui.hbox(10, icon(pr.severity()), text);
                row.setPadding(new Insets(4, 2, 4, 2));
                setGraphic(row);
                setText(null);
            }
        });
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() != 2) return;
            Validator.Problem pr = list.getSelectionModel().getSelectedItem();
            if (pr == null) return;
            if (pr.elementUid() != null) view.openElement(pr.elementUid());
            else view.show("overview");
        });
        BorderPane.setMargin(list, new Insets(0, 24, 20, 24));
        setCenter(list);
    }

    private static String t(String k, Object... a) {
        return I18n.t(k, a);
    }

    private static FontIcon icon(Validator.Severity s) {
        FontIcon i = Ui.icon(switch (s) {
            case ERROR -> "mdi2a-alert-circle-outline";
            case WARNING -> "mdi2a-alert-outline";
            case INFO -> "mdi2i-information-outline";
        }, 18);
        i.getStyleClass().add(switch (s) {
            case ERROR -> "icon-danger";
            case WARNING -> "icon-warning";
            case INFO -> "icon-accent";
        });
        return i;
    }

    private void filter() {
        filtered.setPredicate(p -> switch (p.severity()) {
            case ERROR -> errors.isSelected();
            case WARNING -> warnings.isSelected();
            case INFO -> infos.isSelected();
        });
    }

    private void updateCounts() {
        var list = view.ws.problems();
        errors.setText(t("problems.errors", Validator.count(list, Validator.Severity.ERROR)));
        warnings.setText(t("problems.warnings", Validator.count(list, Validator.Severity.WARNING)));
        infos.setText(t("problems.infos", Validator.count(list, Validator.Severity.INFO)));
    }

    @Override
    public void onShow() {
        view.ws.validate();
    }
}
