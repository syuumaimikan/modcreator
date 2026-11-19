package modstudio.ui.controls;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Line;
import modstudio.core.Elements.Step;
import modstudio.core.Logic;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;

import java.util.*;
import java.util.function.Supplier;

/**
 * Node graph view of procedure steps. Steps are nodes connected by execution wires; "if" splits into
 * then / else branches and blocks with a body (repeat, for each, run later) open an indented lane.
 * The "+" points on wires insert steps, nodes can be dragged onto any "+" point to move them, and the
 * selected node is edited in the side panel. The graph edits the same step tree as the block and script views.
 */
public final class FlowGraph extends BorderPane {
    private static final double W = 224, H = 74, START_H = 56, GAP = 30, HGAP = 36, ADD = 22, INDENT = 36;

    private final IdField.Env env;
    private final List<Step> steps;
    private final Runnable onChange;
    private final Supplier<String> start;
    private final Pane canvas = new Pane();
    private final Group zoomGroup = new Group(canvas);
    private final ScrollPane scroll = new ScrollPane(zoomGroup);
    private final VBox inspector = new VBox(10);
    private final Map<Step, Label> summaries = new IdentityHashMap<>();
    private final Map<Step, Region> nodes = new IdentityHashMap<>();
    private Step selected;
    private Step dragged;
    private List<Step> draggedFrom;
    private double zoom = 1;

    public FlowGraph(IdField.Env env, List<Step> steps, Runnable onChange, Supplier<String> start) {
        this.env = env;
        this.steps = steps;
        this.onChange = onChange;
        this.start = start;
        getStyleClass().add("flow-graph");
        canvas.getStyleClass().add("flow-canvas");
        scroll.setPannable(true);
        scroll.getStyleClass().add("flow-scroll");
        scroll.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (!e.isControlDown()) return;
            if (e.getDeltaY() != 0) setZoom(zoom * (e.getDeltaY() > 0 ? 1.1 : 1 / 1.1));
            e.consume();
        });
        Button in = Ui.iconButton("mdi2m-magnify-plus-outline", I18n.t("graph.zoom_in"), () -> setZoom(zoom * 1.15));
        Button out = Ui.iconButton("mdi2m-magnify-minus-outline", I18n.t("graph.zoom_out"), () -> setZoom(zoom / 1.15));
        Button reset = Ui.iconButton("mdi2f-fit-to-screen-outline", I18n.t("graph.zoom_reset"), () -> setZoom(1));
        HBox tools = Ui.hbox(4, Ui.help(I18n.t("graph.help")), Ui.spacer(), out, reset, in);
        tools.setPadding(new Insets(0, 0, 6, 0));
        setTop(tools);
        setCenter(scroll);
        inspector.setPadding(new Insets(10));
        inspector.getStyleClass().add("flow-inspector");
        ScrollPane side = new ScrollPane(inspector);
        side.setFitToWidth(true);
        side.setPrefWidth(270);
        side.setMinWidth(230);
        side.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setRight(side);
        BorderPane.setMargin(side, new Insets(0, 0, 0, 8));
        setPrefHeight(560);
        setMinHeight(380);
        rebuild();
    }

    private void setZoom(double z) {
        zoom = Math.max(0.4, Math.min(2.0, z));
        canvas.setScaleX(zoom);
        canvas.setScaleY(zoom);
    }

    private void changed() {
        onChange.run();
    }

    /** Re-lays out the whole graph (after structural edits). */
    public void rebuild() {
        canvas.getChildren().clear();
        summaries.clear();
        nodes.clear();
        Region startNode = startNode();
        startNode.relocate(20, 16);
        canvas.getChildren().add(startNode);
        double[] port = {20 + W / 2, 16 + START_H};
        End end = list(steps, 20, 16 + START_H + GAP, port);
        canvas.setPrefSize(Math.max(W + 60, end.w + 60), end.h + 120);
        if (selected != null && !contains(steps, selected)) selected = null;
        showInspector();
    }

    private record End(double w, double h, double ex, double ey) {}

    /** Lays out a step list with its first "+" point at (x + W/2, y); wires start at the given port. */
    private End list(List<Step> list, double x, double y, double[] from) {
        double cy = y, width = W;
        double[] prev = from;
        for (int i = 0; ; i++) {
            Button add = addPoint(list, i);
            add.relocate(x + W / 2 - ADD / 2, cy);
            canvas.getChildren().add(add);
            wire(prev[0], prev[1], x + W / 2, cy);
            prev = new double[]{x + W / 2, cy + ADD};
            cy += ADD + GAP / 2;
            if (i == list.size()) break;
            Step s = list.get(i);
            Region n = node(s, list);
            n.relocate(x, cy);
            canvas.getChildren().add(n);
            wire(prev[0], prev[1], x + W / 2, cy);
            cy += H;
            if (s.kind.equals("if")) {
                double by = cy + GAP;
                End t = list(s.then, x, by, new double[]{x + W * 0.3, cy});
                double ex = x + t.w + HGAP;
                End e = list(s.orElse, ex, by, new double[]{x + W * 0.7, cy});
                label(I18n.t("steps.then"), x + W * 0.3 - 46, cy + 4);
                label(I18n.t("steps.else"), x + W * 0.7 + 8, cy + 4);
                width = Math.max(width, t.w + HGAP + e.w);
                double my = by + Math.max(t.h, e.h) + GAP / 2;
                wire(t.ex, t.ey, x + W / 2, my);
                wire(e.ex, e.ey, x + W / 2, my);
                prev = new double[]{x + W / 2, my};
                cy = my + GAP / 2;
            } else if (Logic.hasBody(s.kind)) {
                double by = cy + GAP;
                End b = list(s.then, x + INDENT, by, new double[]{x + INDENT / 2, cy});
                label(I18n.t("steps.body"), x + 6, cy + 4);
                Line bracket = new Line(x + INDENT / 2, cy, x + INDENT / 2, by + b.h);
                bracket.getStyleClass().add("flow-bracket");
                canvas.getChildren().add(0, bracket);
                width = Math.max(width, INDENT + b.w);
                double my = by + b.h + GAP / 2;
                wire(x + W / 2, cy, x + W / 2, my);
                prev = new double[]{x + W / 2, my};
                cy = my + GAP / 2;
            } else {
                prev = new double[]{x + W / 2, cy};
                cy += GAP / 2;
            }
        }
        return new End(width, cy - y, prev[0], prev[1]);
    }

    private void wire(double x1, double y1, double x2, double y2) {
        double d = Math.max(14, Math.abs(y2 - y1) / 2);
        CubicCurve c = new CubicCurve(x1, y1, x1, y1 + d, x2, y2 - d, x2, y2);
        c.getStyleClass().add("flow-wire");
        canvas.getChildren().add(0, c);
    }

    private void label(String text, double x, double y) {
        Label l = Ui.label(text, "flow-branch-label");
        l.relocate(x, y);
        canvas.getChildren().add(l);
    }

    private Region startNode() {
        Label t = Ui.label(I18n.t("graph.start"), "flow-node-title");
        t.setGraphic(Ui.icon("mdi2p-play-circle-outline"));
        Label s = Ui.label(start.get(), "flow-node-summary");
        s.setTextOverrun(OverrunStyle.ELLIPSIS);
        VBox box = new VBox(3, t, s);
        box.getStyleClass().addAll("flow-node", "flow-start");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPrefSize(W, START_H);
        box.setMinSize(W, START_H);
        box.setMaxSize(W, START_H);
        box.setPadding(new Insets(6, 12, 6, 12));
        Tooltip.install(box, new Tooltip(I18n.t("graph.start") + "\n" + start.get()));
        return box;
    }

    private Region node(Step s, List<Step> owner) {
        Logic.Def def = Logic.STEPS.get(s.kind);
        String cat = def == null ? "logic" : def.category();
        Label title = Ui.label(I18n.tOr("step." + s.kind, s.kind), "flow-node-title");
        title.setGraphic(Ui.icon(StepsEditor.categoryIcon(cat)));
        Button del = Ui.iconButton("mdi2c-close", I18n.t("action.delete"), () -> {
            owner.remove(s);
            if (selected == s) selected = null;
            changed();
            rebuild();
        });
        del.getStyleClass().add("flow-node-button");
        HBox head = Ui.hbox(4, title, Ui.spacer(), del);
        head.setAlignment(Pos.CENTER_LEFT);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setMinWidth(0);
        HBox.setHgrow(title, Priority.ALWAYS);
        Label sum = Ui.label(summary(s), "flow-node-summary");
        // two lines of summary fit; longer text ends with an ellipsis and shows in full on hover
        sum.setWrapText(true);
        sum.setTextOverrun(OverrunStyle.ELLIPSIS);
        sum.setMinHeight(0);
        sum.setMaxHeight(H - 38);
        summaries.put(s, sum);
        VBox box = new VBox(2, head, sum);
        Tooltip tip = new Tooltip();
        tip.textProperty().bind(title.textProperty().concat("\n").concat(sum.textProperty()));
        tip.setWrapText(true);
        tip.setMaxWidth(420);
        Tooltip.install(box, tip);
        box.getStyleClass().addAll("flow-node", "step-" + cat);
        if (s == selected) box.getStyleClass().add("flow-selected");
        box.setPrefSize(W, H);
        box.setMinSize(W, H);
        box.setMaxSize(W, H);
        box.setPadding(new Insets(6, 6, 6, 12));
        box.setOnMouseClicked(e -> {
            selected = s;
            nodes.values().forEach(n -> n.getStyleClass().remove("flow-selected"));
            box.getStyleClass().add("flow-selected");
            showInspector();
            e.consume();
        });
        box.setOnMousePressed(e -> e.consume());
        box.setOnMouseDragged(e -> e.consume());
        box.setOnDragDetected(e -> {
            dragged = s;
            draggedFrom = owner;
            var db = box.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent cc = new ClipboardContent();
            cc.putString("flow-step");
            db.setContent(cc);
            e.consume();
        });
        box.setOnDragDone(e -> {
            dragged = null;
            draggedFrom = null;
        });
        nodes.put(s, box);
        return box;
    }

    /** "+" point: click to insert a step, or drop a dragged node here to move it. */
    private Button addPoint(List<Step> list, int index) {
        Button b = new Button();
        b.setGraphic(Ui.icon("mdi2p-plus"));
        b.getStyleClass().add("flow-add");
        b.setPrefSize(ADD, ADD);
        b.setMinSize(ADD, ADD);
        b.setMaxSize(ADD, ADD);
        b.setTooltip(new Tooltip(I18n.t("graph.add_here")));
        b.setOnAction(e -> {
            ContextMenu menu = new ContextMenu();
            menu.getItems().addAll(StepsEditor.stepMenus(kind -> {
                Step n = Logic.newStep(kind);
                list.add(index, n);
                selected = n;
                changed();
                rebuild();
            }));
            menu.show(b, javafx.geometry.Side.RIGHT, 4, 0);
        });
        b.setOnDragOver(e -> {
            if (dragged != null && !list.isEmpty() && inside(dragged, list)) return;
            if (dragged != null && !(inside(dragged, list))) {
                e.acceptTransferModes(TransferMode.MOVE);
                b.getStyleClass().add("flow-add-target");
            }
            e.consume();
        });
        b.setOnDragExited(e -> b.getStyleClass().remove("flow-add-target"));
        b.setOnDragDropped(e -> {
            boolean ok = false;
            if (dragged != null && draggedFrom != null && !inside(dragged, list)) {
                int from = indexOf(draggedFrom, dragged);
                int to = index;
                if (draggedFrom == list && from < to) to--;
                if (from >= 0) {
                    draggedFrom.remove(from);
                    list.add(Math.min(to, list.size()), dragged);
                    ok = true;
                }
            }
            e.setDropCompleted(ok);
            e.consume();
            if (ok) {
                changed();
                rebuild();
            }
        });
        return b;
    }

    private static int indexOf(List<Step> list, Step s) {
        for (int i = 0; i < list.size(); i++) if (list.get(i) == s) return i;
        return -1;
    }

    /** True when the list belongs to the step itself or to one of its nested steps. */
    private static boolean inside(Step s, List<Step> list) {
        if (s.then == list || s.orElse == list) return true;
        for (Step c : s.then) if (inside(c, list)) return true;
        for (Step c : s.orElse) if (inside(c, list)) return true;
        return false;
    }

    private static boolean contains(List<Step> list, Step s) {
        for (Step c : list) {
            if (c == s || contains(c.then, s) || contains(c.orElse, s)) return true;
        }
        return false;
    }

    /** Short text of the step parameters shown on the node. */
    static String summary(Step s) {
        if (s.kind.equals("if") && s.cond != null) {
            StringBuilder sb = new StringBuilder();
            if (s.cond.negate) sb.append(I18n.t("graph.not")).append(' ');
            sb.append(I18n.tOr("cond." + s.cond.kind, s.cond.kind));
            String p = values(Logic.CONDITIONS.get(s.cond.kind), s.cond.p);
            if (!p.isEmpty()) sb.append(": ").append(p);
            return sb.toString();
        }
        return values(Logic.STEPS.get(s.kind), s.p);
    }

    private static String values(Logic.Def d, Map<String, String> p) {
        if (d == null) return "";
        List<String> parts = new ArrayList<>();
        for (Logic.Param prm : d.params()) {
            String v = p.getOrDefault(prm.key(), prm.def());
            if (v == null || v.isBlank()) continue;
            if (prm.type() == Logic.PType.CHOICE) v = I18n.tOr("choice." + v, v);
            parts.add(v.replace('\n', ' '));
            if (parts.size() == 3) break;
        }
        String r = String.join("  |  ", parts);
        return r.length() > 48 ? r.substring(0, 46) + "..." : r;
    }

    private void showInspector() {
        inspector.getChildren().clear();
        if (selected == null) {
            inspector.getChildren().addAll(Ui.label(I18n.t("graph.inspector"), "card-title"), Ui.help(I18n.t("graph.no_selection")));
            return;
        }
        Step s = selected;
        Logic.Def def = Logic.STEPS.get(s.kind);
        Label title = Ui.label(I18n.tOr("step." + s.kind, s.kind), "title-4");
        Runnable edited = () -> {
            Label l = summaries.get(s);
            if (l != null) l.setText(summary(s));
            changed();
        };
        inspector.getChildren().add(title);
        if (def != null && !def.params().isEmpty()) inspector.getChildren().add(StepsEditor.paramFields(env, def.params(), s.p, edited));
        if (s.kind.equals("if")) inspector.getChildren().add(StepsEditor.conditionFields(env, s, edited));
        if (def != null && def.params().isEmpty() && !s.kind.equals("if")) inspector.getChildren().add(Ui.help(I18n.t("graph.no_params")));
        Button dup = Ui.button(I18n.t("action.duplicate"), "mdi2c-content-copy", () -> {
            List<Step> owner = ownerOf(steps, s);
            if (owner == null) return;
            Step c = StepsEditor.copy(s);
            owner.add(indexOf(owner, s) + 1, c);
            selected = c;
            changed();
            rebuild();
        });
        Button del = Ui.button(I18n.t("action.delete"), "mdi2d-delete-outline", () -> {
            List<Step> owner = ownerOf(steps, s);
            if (owner == null) return;
            owner.remove(s);
            selected = null;
            changed();
            rebuild();
        });
        inspector.getChildren().addAll(new Separator(), Ui.hbox(8, dup, del));
    }

    private static List<Step> ownerOf(List<Step> list, Step s) {
        for (Step c : list) if (c == s) return list;
        for (Step c : list) {
            List<Step> r = ownerOf(c.then, s);
            if (r == null) r = ownerOf(c.orElse, s);
            if (r != null) return r;
        }
        return null;
    }
}
