package modstudio.ui.controls;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import modstudio.core.Ids;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Searchable list dialog for choosing an id. */
public final class Picker {
    private Picker() {}

    public record Entry(String id, String label, Image image, String group) {}

    public static Optional<String> show(Window owner, String title, List<Entry> entries, boolean allowCustom, String current) {
        Dialog<String> d = new Dialog<>();
        Ui.styleDialog(d, owner);
        d.setTitle(title);
        d.setHeaderText(null);

        TextField search = new TextField();
        search.setPromptText(I18n.t("picker.search"));
        HBox.setHgrow(search, Priority.ALWAYS);

        LinkedHashSet<String> groups = new LinkedHashSet<>();
        for (Entry e : entries) groups.add(e.group());
        List<String> groupList = new ArrayList<>();
        groupList.add("");
        groupList.addAll(groups);
        ComboBox<String> group = Ui.combo(groupList, g -> g.isEmpty() ? I18n.t("picker.group_all") : g);
        group.setValue("");

        FilteredList<Entry> filtered = new FilteredList<>(FXCollections.observableArrayList(entries));
        ListView<Entry> list = new ListView<>(filtered);
        list.setPrefSize(520, 420);
        list.setPlaceholder(Ui.muted(I18n.t("picker.none")));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Entry e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Node icon;
                if (e.image() != null) {
                    ImageView iv = new ImageView(e.image());
                    iv.setFitWidth(24);
                    iv.setFitHeight(24);
                    iv.setSmooth(false);
                    icon = iv;
                } else {
                    Label l = Ui.label(e.id().startsWith("#") ? "#" : abbreviation(e.id()), "picker-abbr");
                    icon = l;
                }
                StackPane ib = new StackPane(icon);
                ib.setMinSize(28, 28);
                ib.getStyleClass().add("picker-icon");
                VBox text = new VBox(0, Ui.label(e.label(), "cell-title"), Ui.label(e.id(), "text-muted", "text-small"));
                HBox row = new HBox(10, ib, text, Ui.spacer(), Ui.label(e.group(), "text-muted", "text-small"));
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
                setText(null);
            }
        });

        Runnable filter = () -> {
            String q = search.getText().trim().toLowerCase(Locale.ROOT);
            String g = group.getValue();
            filtered.setPredicate(e -> (g == null || g.isEmpty() || e.group().equals(g))
                    && (q.isEmpty() || e.id().toLowerCase(Locale.ROOT).contains(q) || e.label().toLowerCase(Locale.ROOT).contains(q)));
            if (!filtered.isEmpty()) list.getSelectionModel().selectFirst();
        };
        search.textProperty().addListener((o, a, b) -> filter.run());
        group.valueProperty().addListener((o, a, b) -> filter.run());

        Label custom = Ui.help(allowCustom ? I18n.t("picker.custom_hint") : "");
        HBox top = new HBox(8, search, group);
        VBox box = new VBox(10, top, list, custom);
        box.setPadding(new Insets(4, 0, 0, 0));
        d.getDialogPane().setContent(box);
        ButtonType ok = new ButtonType(I18n.t("action.choose"), ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, new ButtonType(I18n.t("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE));

        if (current != null && !current.isBlank()) {
            for (Entry e : entries) {
                if (e.id().equals(current)) {
                    list.getSelectionModel().select(e);
                    Platform.runLater(() -> list.scrollTo(e));
                    break;
                }
            }
        }

        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) {
                d.setResult(list.getSelectionModel().getSelectedItem().id());
                d.close();
            }
        });
        search.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) {
                list.requestFocus();
                if (list.getSelectionModel().isEmpty()) list.getSelectionModel().selectFirst();
            }
        });

        d.setResultConverter(bt -> {
            if (bt != ok) return null;
            Entry sel = list.getSelectionModel().getSelectedItem();
            String typed = search.getText().trim();
            if (allowCustom && !typed.isEmpty() && (filtered.isEmpty() || sel == null) && Ids.RESOURCE.matcher(Ids.full(typed)).matches())
                return Ids.full(typed);
            return sel == null ? null : sel.id();
        });
        Platform.runLater(search::requestFocus);
        return d.showAndWait();
    }

    /** Short label for ids without an image ("minecraft:iron_ingot" -> "II"). */
    public static String abbreviation(String id) {
        String path = Ids.path(id);
        int slash = path.lastIndexOf('/');
        if (slash >= 0) path = path.substring(slash + 1);
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (!part.isEmpty() && sb.length() < 2) sb.append(Character.toUpperCase(part.charAt(0)));
        }
        if (sb.length() == 1 && path.length() > 1) sb.append(path.charAt(1));
        return sb.toString();
    }
}
