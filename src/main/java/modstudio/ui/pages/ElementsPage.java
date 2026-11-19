package modstudio.ui.pages;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import modstudio.core.*;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;
import modstudio.ui.WorkspaceView;
import modstudio.ui.editors.Ctx;
import modstudio.ui.editors.Editors;

import java.util.Locale;

/** List of elements of one type with the editor of the selected element. */
public final class ElementsPage extends SplitPane implements WorkspaceView.Page {
    private final WorkspaceView view;
    private final ElementType type;
    private final ObservableList<Element> items = FXCollections.observableArrayList();
    private final FilteredList<Element> filtered = new FilteredList<>(items);
    private final ListView<Element> list = new ListView<>(filtered);
    private final StackPane editorHost = new StackPane();
    private final TextField search = new TextField();
    private Element current;
    private boolean reloading;

    public ElementsPage(WorkspaceView view, ElementType type) {
        this.view = view;
        this.type = type;
        getStyleClass().add("elements-page");

        Label title = Ui.label(I18n.t("types." + type.id), "title-4");
        Button add = Ui.iconButton("mdi2p-plus", I18n.t("action.add"), this::add);
        add.getStyleClass().remove("flat");
        add.getStyleClass().add("accent");
        HBox header = Ui.hbox(8, Ui.icon(Ui.typeIcon(type), 18), title, Ui.spacer(), add);
        header.setPadding(new Insets(12, 12, 8, 12));

        search.setPromptText(I18n.t("action.search"));
        search.textProperty().addListener((o, a, b) -> {
            String q = b.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(e -> q.isEmpty() || e.name.toLowerCase(Locale.ROOT).contains(q)
                    || subtitle(e).toLowerCase(Locale.ROOT).contains(q) || e.notes.toLowerCase(Locale.ROOT).contains(q));
        });
        VBox.setMargin(search, new Insets(0, 12, 8, 12));

        list.setCellFactory(lv -> new ElementCell());
        list.setPlaceholder(Ui.muted(I18n.t("elements.empty_list")));
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        list.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (!reloading) showEditor(b);
        });
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) delete();
        });
        list.getStyleClass().add("element-list");
        VBox.setVgrow(list, Priority.ALWAYS);

        Button dup = Ui.iconButton("mdi2c-content-copy", I18n.t("action.duplicate"), this::duplicate);
        Button del = Ui.iconButton("mdi2d-delete-outline", I18n.t("action.delete"), this::delete);
        Button up = Ui.iconButton("mdi2a-arrow-up", I18n.t("action.move_up"), () -> move(-1));
        Button down = Ui.iconButton("mdi2a-arrow-down", I18n.t("action.move_down"), () -> move(1));
        HBox tools = Ui.hbox(2, dup, del, Ui.spacer(), up, down);
        tools.setPadding(new Insets(6, 8, 6, 8));
        tools.getStyleClass().add("list-tools");
        for (Button b : new Button[]{dup, del}) b.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        for (Button b : new Button[]{up, down}) b.disableProperty().bind(Bindings.size(list.getSelectionModel().getSelectedItems()).isNotEqualTo(1));

        VBox left = new VBox(header, search, list, tools);
        left.getStyleClass().add("element-list-pane");
        left.setMinWidth(220);
        left.setMaxWidth(380);

        editorHost.getStyleClass().add("editor-host");
        getItems().addAll(left, editorHost);
        setDividerPositions(0.24);
        SplitPane.setResizableWithParent(left, false);

        view.ws.onStructureChanged(this::reload);
        reload();
        showEditor(null);
    }

    @Override
    public void onShow() {
        list.refresh();
        if (current != null && view.ws.project.byUid(current.uid) == null) showEditor(null);
    }

    private void reload() {
        Element sel = current;
        reloading = true;
        try {
            items.setAll(view.ws.project.ofType(type));
            if (sel != null && items.contains(sel)) list.getSelectionModel().select(sel);
            else list.getSelectionModel().clearSelection();
        } finally {
            reloading = false;
        }
        list.refresh();
        Element now = list.getSelectionModel().getSelectedItem();
        if (now != current) showEditor(now);
    }

    public String selectedUid() {
        return current == null ? null : current.uid;
    }

    public void select(Element e) {
        search.clear();
        list.getSelectionModel().clearAndSelect(items.indexOf(e));
        list.scrollTo(e);
    }

    private void showEditor(Element e) {
        current = e;
        if (e == null) {
            Button add = Ui.accent(I18n.t("elements.add", I18n.t("type." + type.id)), "mdi2p-plus", this::add);
            editorHost.getChildren().setAll(Ui.emptyState(Ui.typeIcon(type), I18n.t("types." + type.id),
                    I18n.t("types." + type.id + ".desc"), add));
            return;
        }
        Ctx ctx = new Ctx(view, () -> list.refresh());
        Node editor = Editors.create(ctx, e);
        editorHost.getChildren().setAll(editor);
    }

    // ------------------------------------------------------------------ actions

    private String nameError(String n, Element self) {
        if (n.isEmpty()) return I18n.t("problem.name_empty", I18n.t("type." + type.id));
        if (type != ElementType.CODE_FILE && !(type.allowsPath() ? Ids.PATH : Ids.NAME).matcher(n).matches())
            return I18n.t("problem.name_invalid", n);
        for (Element o : view.ws.project.elements) {
            if (o != self && o.type().registryGroup().equals(type.registryGroup()) && o.name.equals(n))
                return I18n.t("problem.name_duplicate", n);
        }
        return null;
    }

    private void add() {
        String def = view.ws.project.uniqueName(type, "new_" + type.id);
        Ui.prompt(view.window(), I18n.t("elements.add", I18n.t("type." + type.id)), I18n.t("elements.name_prompt"), def,
                n -> nameError(n, null)).ifPresent(n -> {
            Element e = type.create();
            e.name = n;
            Editors.initDefaults(view.ws, e);
            view.ws.project.elements.add(e);
            view.ws.structureChanged();
            select(e);
        });
    }

    private void duplicate() {
        var sel = java.util.List.copyOf(list.getSelectionModel().getSelectedItems());
        if (sel.isEmpty()) return;
        Element lastCopy = null;
        for (Element e : sel) {
            Element c = ProjectIO.copy(e);
            c.name = view.ws.project.uniqueName(type, e.name.replace('/', '_') + "_copy");
            if (type.allowsPath() && e.name.contains("/")) {
                String dir = e.name.substring(0, e.name.lastIndexOf('/') + 1);
                String base = e.name.substring(e.name.lastIndexOf('/') + 1) + "_copy";
                String n = dir + base;
                int i = 2;
                while (view.ws.project.nameTaken(type, n)) n = dir + base + "_" + i++;
                c.name = n;
            }
            int idx = view.ws.project.elements.indexOf(e);
            view.ws.project.elements.add(idx + 1, c);
            lastCopy = c;
        }
        view.ws.structureChanged();
        if (lastCopy != null) select(lastCopy);
    }

    private void delete() {
        var sel = java.util.List.copyOf(list.getSelectionModel().getSelectedItems());
        if (sel.isEmpty()) return;
        String header = sel.size() == 1
                ? I18n.t("elements.delete_title", sel.get(0).name)
                : I18n.t("elements.delete_multi_title", sel.size());
        if (!Ui.confirm(view.window(), header, I18n.t("elements.delete_text"))) return;
        view.ws.project.elements.removeAll(sel);
        view.ws.structureChanged();
    }

    private void move(int dir) {
        Element e = list.getSelectionModel().getSelectedItem();
        if (e == null) return;
        int i = items.indexOf(e);
        int j = i + dir;
        if (j < 0 || j >= items.size()) return;
        Element other = items.get(j);
        var all = view.ws.project.elements;
        int a = all.indexOf(e), b = all.indexOf(other);
        all.set(a, other);
        all.set(b, e);
        view.ws.structureChanged();
        list.getSelectionModel().select(e);
    }

    // ------------------------------------------------------------------ cells

    private final class ElementCell extends ListCell<Element> {
        @Override
        protected void updateItem(Element e, boolean empty) {
            super.updateItem(e, empty);
            if (empty || e == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Node icon;
            Image img = view.ws.thumbnails().of(e, 64);
            if (img != null) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(36);
                iv.setFitHeight(36);
                iv.setPreserveRatio(true);
                iv.setSmooth(false);
                icon = iv;
            } else {
                icon = Ui.icon(Ui.typeIcon(e.type()), 20);
            }
            StackPane iconBox = new StackPane(icon);
            iconBox.getStyleClass().add("element-thumb");
            iconBox.setMinSize(40, 40);
            iconBox.setPrefSize(40, 40);
            iconBox.setMaxSize(40, 40);
            Label name = Ui.label(e.name, "cell-title");
            String sub = subtitle(e);
            VBox text = new VBox(1, name);
            if (sub != null && !sub.isBlank()) text.getChildren().add(Ui.label(sub, "text-muted", "text-small"));
            if (e.notes != null && !e.notes.isBlank()) {
                Label note = Ui.label(e.notes.lines().findFirst().orElse(""), "text-muted", "text-small", "element-note");
                note.setMaxWidth(260);
                text.getChildren().add(note);
                Tooltip tip = new Tooltip(e.name + "\n" + e.notes);
                tip.setWrapText(true);
                tip.setMaxWidth(420);
                setTooltip(tip);
            } else {
                setTooltip(null);
            }
            boolean hasError = view.ws.problems().stream().anyMatch(p -> e.uid.equals(p.elementUid()) && p.severity() == Validator.Severity.ERROR);
            HBox row = new HBox(10, iconBox, text);
            row.setAlignment(Pos.CENTER_LEFT);
            if (hasError) {
                row.getChildren().addAll(Ui.spacer(), Ui.icon("mdi2a-alert-circle-outline"));
                row.getChildren().get(row.getChildren().size() - 1).getStyleClass().add("icon-danger");
            }
            setGraphic(row);
            setText(null);
        }
    }

    /** Short second line in the list, translated where the raw value is an internal id. */
    static String subtitle(Element e) {
        if (e instanceof Elements.Procedure pr) return I18n.tOr("trigger." + pr.trigger, pr.trigger);
        if (e instanceof Elements.Function fn) return fn.onTick ? I18n.t("trigger.tick") : fn.onLoad ? I18n.t("trigger.load") : "";
        if (e instanceof Elements.Model m) return I18n.t("model.cubes", m.cubes.size())
                + (m.animations.isEmpty() ? "" : "  " + I18n.t("model.animations", m.animations.size()));
        if (e instanceof Elements.Tag tg) return I18n.tOr("registry." + tg.registry, tg.registry);
        if (e instanceof Elements.LootTable lt && lt.replaces.isBlank()) return I18n.tOr("loottype." + lt.lootType, lt.lootType);
        String s = e.subtitle();
        return s == null ? "" : s;
    }
}
