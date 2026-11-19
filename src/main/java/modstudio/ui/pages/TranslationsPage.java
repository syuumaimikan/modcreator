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
    private final javafx.collections.transformation.FilteredList<Row> filteredRows = new javafx.collections.transformation.FilteredList<>(rows);
    private final TextField search = new TextField();
    private final ComboBox<String> addLang = new ComboBox<>();
    private final Label summary = Ui.help("");

    /** A translation key; element is null for custom keys. */
    private record Row(String key, Localization.Entry entry) {}

    public TranslationsPage(WorkspaceView view) {
        this.view = view;
        this.p = view.ws.project;

        search.setPromptText(t("action.search"));
        search.setPrefWidth(200);
        search.textProperty().addListener((o, a, b) -> applyFilter());

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
        HBox bar = Ui.hbox(8, head, Ui.spacer(), search, addLang, add, addKey);
        bar.setPadding(new Insets(20, 24, 12, 24));
        setTop(bar);

        table.setItems(filteredRows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Ui.muted(t("translations.empty")));
        table.getStyleClass().add("translations-table");
        VBox center = new VBox(8, table, summary);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        center.setPadding(new Insets(0, 24, 16, 24));
        setCenter(center);
        rebuild();
    }

    private void applyFilter() {
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            filteredRows.setPredicate(null);
            return;
        }
        List<String> ls = langs();
        filteredRows.setPredicate(r -> {
            if (r.key().toLowerCase(Locale.ROOT).contains(q)) return true;
            for (String lang : ls) {
                if (get(r, lang).toLowerCase(Locale.ROOT).contains(q)) return true;
            }
            return false;
        });
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
            case Elements.Item it -> switch (e.field()) {
                case "tooltip" -> it.tooltip;
                case "shift_tooltip" -> it.shiftTooltip;
                default -> it.displayName;
            };
            case Elements.Block b -> b.displayName;
            case Elements.Fluid fl -> e.field().equals("bucket") ? e.english() : fl.displayName;
            case Elements.CreativeTab tab -> tab.displayName;
            case Elements.Sound s -> s.subtitle;
            case Systems.Mob m -> m.displayName;
            case Elements.StatusEffect se -> e.field().equals("potion")
                    ? (se.potionName.isBlank() ? e.english() : se.potionName) : se.displayName;
            case Elements.Potion po -> switch (e.field()) {
                case "splash" -> po.splashName.isBlank() ? e.english() : po.splashName;
                case "lingering" -> po.lingeringName.isBlank() ? e.english() : po.lingeringName;
                case "arrow" -> po.arrowName.isBlank() ? e.english() : po.arrowName;
                default -> po.displayName;
            };
            case Elements.Enchantment en -> en.displayName;
            case Elements.DamageType d -> e.field().equals("death_attacker") ? d.deathMessageAttacker : d.deathMessage;
            case Elements.Painting pa -> e.field().equals("author") ? pa.author : pa.title;
            case Elements.Song so -> so.description;
            case Systems.KeyBind k -> e.field().equals("category") ? e.english() : k.displayName;
            case Elements.Advancement a -> e.field().equals("title") ? a.title : a.description;
            case Systems.Dialog d -> e.field().equals("body") ? d.body : d.title;
            case Systems.Menu mn -> e.field().equals("deny") ? mn.denyMessage : mn.title;
            case Systems.Hud h -> e.field().startsWith("line.") ? e.english() : h.title;
            case Systems.CustomScreen sc -> e.field().startsWith("w.") ? e.english() : sc.title;
            case Systems.Trader tr -> tr.displayName;
            case Systems.Resource res -> res.displayName;
            case ModElements.Container ct -> ct.displayName;
            case ModElements.CustomEntity ce -> e.field().equals("spawn_egg") ? e.english() : ce.displayName;
            case ModElements.Portal pt -> e.field().equals("igniter") ? e.english() : pt.displayName;
            case ModElements.Profession pf -> pf.displayName;
            case ModElements.ArmorTrim at -> e.field().equals("template") ? e.english() : at.displayName;
            case ModElements.BannerPattern bp -> bp.displayName;
            default -> e.english();
        };
    }

    private void set(Row r, String lang, String v) {
        if (r.entry() != null) {
            Element el = r.entry().element();
            String f = r.entry().field();
            if (lang.equals("en_us")) {
                switch (el) {
                    case Elements.Item it -> {
                        switch (f) {
                            case "tooltip" -> it.tooltip = v;
                            case "shift_tooltip" -> it.shiftTooltip = v;
                            default -> it.displayName = v;
                        }
                    }
                    case Elements.Block b -> b.displayName = v;
                    case Elements.Fluid fl -> {
                        if (!f.equals("bucket")) fl.displayName = v;
                    }
                    case Elements.CreativeTab tab -> tab.displayName = v;
                    case Elements.Sound s -> s.subtitle = v;
                    case Systems.Mob m -> m.displayName = v;
                    case Elements.StatusEffect se -> {
                        if (f.equals("potion")) se.potionName = v;
                        else se.displayName = v;
                    }
                    case Elements.Potion po -> {
                        switch (f) {
                            case "splash" -> po.splashName = v;
                            case "lingering" -> po.lingeringName = v;
                            case "arrow" -> po.arrowName = v;
                            default -> po.displayName = v;
                        }
                    }
                    case Elements.Enchantment en -> en.displayName = v;
                    case Elements.DamageType d -> {
                        if (f.equals("death_attacker")) d.deathMessageAttacker = v;
                        else d.deathMessage = v;
                    }
                    case Elements.Painting pa -> {
                        if (f.equals("author")) pa.author = v;
                        else pa.title = v;
                    }
                    case Elements.Song so -> so.description = v;
                    case Systems.KeyBind k -> {
                        if (!f.equals("category")) k.displayName = v;
                    }
                    case Elements.Advancement a -> {
                        if (f.equals("title")) a.title = v;
                        else a.description = v;
                    }
                    case Systems.Dialog d -> {
                        if (f.equals("body")) d.body = v;
                        else d.title = v;
                    }
                    case Systems.Menu mn -> {
                        if (f.equals("deny")) mn.denyMessage = v;
                        else mn.title = v;
                    }
                    case Systems.Hud h -> {
                        if (f.startsWith("line.")) {
                            int idx = parseLineIndex(f.substring(5));
                            if (idx >= 0 && idx < h.lines.size()) h.lines.set(idx, v);
                        } else h.title = v;
                    }
                    case Systems.CustomScreen sc -> {
                        if (f.startsWith("w.")) updateWidgetField(sc, f, v);
                        else sc.title = v;
                    }
                    case Systems.Trader tr -> tr.displayName = v;
                    case Systems.Resource res -> res.displayName = v;
                    case ModElements.Container ct -> ct.displayName = v;
                    case ModElements.CustomEntity ce -> {
                        if (!f.equals("spawn_egg")) ce.displayName = v;
                    }
                    case ModElements.Portal pt -> {
                        if (!f.equals("igniter")) pt.displayName = v;
                    }
                    case ModElements.Profession pf -> pf.displayName = v;
                    case ModElements.ArmorTrim at -> {
                        if (!f.equals("template")) at.displayName = v;
                    }
                    case ModElements.BannerPattern bp -> bp.displayName = v;
                    default -> {
                    }
                }
            } else {
                el.setLoc(f, lang, v);
            }
        } else {
            Map<String, String> m = p.translations.computeIfAbsent(lang, k -> new LinkedHashMap<>());
            m.put(r.key(), v);
        }
        view.ws.changed();
        view.invalidatePages(r.entry() == null ? "" : r.entry().element().type().id);
    }

    private static int parseLineIndex(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static void updateWidgetField(Systems.CustomScreen sc, String field, String v) {
        int dot = field.indexOf('.', 2);
        if (dot <= 2) return;
        int idx = parseLineIndex(field.substring(2, dot));
        if (idx < 0 || idx >= sc.widgets.size()) return;
        Systems.ScreenWidget w = sc.widgets.get(idx);
        switch (field.substring(dot + 1)) {
            case "text" -> w.text = v;
            case "tooltip" -> w.tooltip = v;
            case "placeholder" -> w.placeholder = v;
            case "options" -> w.options = v;
            default -> {
            }
        }
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
