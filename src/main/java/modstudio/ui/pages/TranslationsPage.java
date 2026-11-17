package modstudio.ui.pages;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import modstudio.core.*;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;
import modstudio.ui.WorkspaceView;

import java.util.*;

/** Spreadsheet of every translatable text in the project, one column per game language. */
public final class TranslationsPage extends BorderPane implements WorkspaceView.Page {
    private final WorkspaceView view;
    private final Project p;
    private final TableView<Row> table = new TableView<>();
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final ComboBox<String> addLang = new ComboBox<>();
    private final Label summary = Ui.help("");

    /** A translation key; element is null for custom keys. */
    private record Row(String key, Localization.Entry entry) {}

    public TranslationsPage(WorkspaceView view) {
        this.view = view;
        this.p = view.ws.project;

        addLang.setPromptText(t("translations.add_language"));
        addLang.setPrefWidth(200);
        Button add = Ui.button(t("action.add"), "mdi2p-plus", () -> {
            String l = addLang.getValue();
            if (l == null || l.isBlank()) return;
            l = l.trim().toLowerCase(Locale.ROOT);
            if (!l.matches("[a-z]{2,3}_[a-z]{2,3}") || l.equals("en_us") || p.meta.languages.contains(l)) return;
            p.meta.languages.add(l);
            view.ws.changed();
            view.invalidatePages("item", "block", "creative_tab", "sound", "advancement");
            rebuild();
        });
        addLang.setEditable(true);
        Button addKey = Ui.button(t("translations.add_key"), "mdi2k-key-plus", this::addKey);
        VBox head = new VBox(4, Ui.label(t("page.translations"), "title-2"), Ui.muted(t("translations.subtitle")));
        HBox bar = Ui.hbox(8, head, Ui.spacer(), addLang, add, addKey);
        bar.setPadding(new Insets(20, 24, 12, 24));
        setTop(bar);

        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Ui.muted(t("translations.empty")));
        table.getStyleClass().add("translations-table");
        VBox center = new VBox(8, table, summary);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        center.setPadding(new Insets(0, 24, 16, 24));
        setCenter(center);
        rebuild();
    }

    private static String t(String k, Object... a) {
        return I18n.t(k, a);
    }

    @Override
    public void onShow() {
        rebuild();
    }

    private List<String> langs() {
        List<String> r = new ArrayList<>();
        r.add("en_us");
        for (String l : p.meta.languages) if (!r.contains(l)) r.add(l);
        for (String l : p.translations.keySet()) if (!r.contains(l)) r.add(l);
        return r;
    }

    private void rebuild() {
        List<String> langs = langs();
        List<String> available = new ArrayList<>(Localization.GAME_LANGUAGES);
        available.removeAll(langs);
        addLang.getItems().setAll(available);

        rows.clear();
        for (Localization.Entry e : Localization.entries(p)) rows.add(new Row(e.key(), e));
        TreeSet<String> custom = new TreeSet<>();
        for (Map<String, String> m : p.translations.values()) custom.addAll(m.keySet());
        for (String k : custom) rows.add(new Row(k, null));

        table.getColumns().clear();
        TableColumn<Row, String> keyCol = new TableColumn<>(t("translations.key"));
        keyCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().key()));
        keyCol.setPrefWidth(280);
        keyCol.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty ? null : s);
                getStyleClass().remove("mono-small");
                if (!empty) getStyleClass().add("mono-small");
                setContextMenu(null);
                if (!empty && getTableRow() != null && getTableRow().getItem() != null && getTableRow().getItem().entry() == null) {
                    MenuItem del = new MenuItem(t("action.delete"));
                    del.setOnAction(e -> {
                        for (Map<String, String> m : p.translations.values()) m.remove(s);
                        p.translations.values().removeIf(Map::isEmpty);
                        view.ws.changed();
                        rebuild();
                    });
                    setContextMenu(new ContextMenu(del));
                }
            }
        });
        table.getColumns().add(keyCol);

        StringBuilder sum = new StringBuilder();
        for (String lang : langs) {
            TableColumn<Row, String> col = new TableColumn<>(lang.equals("en_us") ? "en_us (" + t("translations.source") + ")" : lang);
            col.setCellValueFactory(cd -> new SimpleStringProperty(get(cd.getValue(), lang)));
            col.setCellFactory(c -> new EditCell(lang));
            col.setPrefWidth(220);
            col.setSortable(false);
            if (!lang.equals("en_us")) {
                MenuItem remove = new MenuItem(t("translations.remove_language", lang));
                remove.setOnAction(e -> {
                    if (!Ui.confirm(view.window(), t("translations.remove_language", lang), t("translations.remove_language_text"))) return;
                    p.meta.languages.remove(lang);
                    p.translations.remove(lang);
                    for (Element el : p.elements) {
                        for (Map<String, String> m : el.loc.values()) m.remove(lang);
                        el.loc.values().removeIf(Map::isEmpty);
                    }
                    view.ws.changed();
                    view.invalidatePages("item", "block", "creative_tab", "sound", "advancement");
                    rebuild();
                });
                col.setContextMenu(new ContextMenu(remove));
                long missing = rows.stream().filter(r -> get(r, lang).isBlank()).count();
                if (sum.length() > 0) sum.append("   ");
                sum.append(t("translations.missing", lang, missing));
            }
            table.getColumns().add(col);
        }
        summary.setText(sum.length() == 0 ? t("translations.hint") : sum + "\n" + t("translations.hint"));
    }

    private String get(Row r, String lang) {
        if (r.entry() != null) {
            if (lang.equals("en_us")) return source(r.entry());
            return r.entry().element().loc(r.entry().field(), lang);
        }
        return p.translations.getOrDefault(lang, Map.of()).getOrDefault(r.key(), "");
    }

    private String source(Localization.Entry e) {
        return switch (e.element()) {
            case Elements.Item it -> it.displayName;
            case Elements.Block b -> b.displayName;
            case Elements.CreativeTab tab -> tab.displayName;
            case Elements.Sound s -> s.subtitle;
            case Elements.Advancement a -> e.field().equals("title") ? a.title : a.description;
            default -> e.english();
        };
    }

    private void set(Row r, String lang, String v) {
        if (r.entry() != null) {
            Element el = r.entry().element();
            if (lang.equals("en_us")) {
                switch (el) {
                    case Elements.Item it -> it.displayName = v;
                    case Elements.Block b -> b.displayName = v;
                    case Elements.CreativeTab tab -> tab.displayName = v;
                    case Elements.Sound s -> s.subtitle = v;
                    case Elements.Advancement a -> {
                        if (r.entry().field().equals("title")) a.title = v;
                        else a.description = v;
                    }
                    default -> {
                    }
                }
            } else {
                el.setLoc(r.entry().field(), lang, v);
            }
        } else {
            Map<String, String> m = p.translations.computeIfAbsent(lang, k -> new LinkedHashMap<>());
            m.put(r.key(), v);
        }
        view.ws.changed();
        view.invalidatePages(r.entry() == null ? "" : r.entry().element().type().id);
    }

    private void addKey() {
        Ui.prompt(view.window(), t("translations.add_key"), t("translations.key_prompt"), p.meta.modId + ".message.hello",
                s -> s.matches("[A-Za-z0-9_.\\-]+") ? null : t("translations.key_invalid")).ifPresent(k -> {
            p.translations.computeIfAbsent("en_us", x -> new LinkedHashMap<>()).put(k, "");
            view.ws.changed();
            rebuild();
        });
    }

    private final class EditCell extends TableCell<Row, String> {
        private final TextField tf = new TextField();
        private final String lang;
        private boolean updating;

        EditCell(String lang) {
            this.lang = lang;
            tf.getStyleClass().add("table-field");
            tf.textProperty().addListener((o, a, b) -> {
                if (updating) return;
                Row r = getTableRow() == null ? null : getTableRow().getItem();
                if (r == null) return;
                set(r, lang, b);
                markMissing(b);
            });
        }

        private void markMissing(String v) {
            tf.getStyleClass().remove("missing");
            if (!lang.equals("en_us") && (v == null || v.isBlank())) tf.getStyleClass().add("missing");
        }

        @Override
        protected void updateItem(String s, boolean empty) {
            super.updateItem(s, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
                return;
            }
            updating = true;
            tf.setText(s == null ? "" : s);
            Row r = getTableRow().getItem();
            tf.setPromptText(lang.equals("en_us") ? "" : get(r, "en_us"));
            updating = false;
            markMissing(s);
            setGraphic(tf);
        }
    }
}
