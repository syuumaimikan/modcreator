package modstudio.ui.controls;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import modstudio.core.ShaderNodes;
import modstudio.core.ShaderNodes.Def;
import modstudio.core.ShaderNodes.Port;
import modstudio.core.ShaderNodes.VT;
import modstudio.core.Systems.ShaderNode;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Node editor for shader graphs. Nodes are dragged by their title; a wire is drawn by dragging from an output (right
 * side) onto an input (left side). Clicking a connected input removes its wire, and inputs without a wire take the
 * value typed next to them. Delete removes the selected node.
 */
public final class ShaderGraphView extends BorderPane {
    private static final double W = 184, HEAD = 28, ROW = 28, R = 6;

    private final List<ShaderNode> nodes;
    private final Runnable onChange;
    private final Pane canvas = new Pane();
    private final Pane wires = new Pane();
    private final Group zoomGroup = new Group(canvas);
    private final ScrollPane scroll = new ScrollPane(zoomGroup);
    private final Map<ShaderNode, Region> views = new IdentityHashMap<>();
    private ShaderNode selected;
    private CubicCurve dragWire;
    private String dragFrom;
    private double zoom = 1;
    private boolean fitted;
    private double pressX, pressY, nodeX, nodeY;

    public ShaderGraphView(List<ShaderNode> nodes, Runnable onChange) {
        this.nodes = nodes;
        this.onChange = onChange;
        getStyleClass().add("shader-graph");
        canvas.getStyleClass().add("flow-canvas");
        canvas.setPrefSize(2400, 1400);
        canvas.setMinSize(2400, 1400);
        wires.setMouseTransparent(true);
        wires.setPrefSize(2400, 1400);
        scroll.setPannable(true);
        scroll.getStyleClass().add("flow-scroll");
        scroll.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (!e.isControlDown()) return;
            setZoom(zoom * (e.getDeltaY() > 0 ? 1.1 : 1 / 1.1));
            e.consume();
        });
        canvas.setFocusTraversable(true);
        canvas.setOnKeyPressed(e -> {
            if ((e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) && selected != null && !selected.type.equals("output")) {
                remove(selected);
                e.consume();
            }
        });
        canvas.setOnMousePressed(e -> {
            if (e.getTarget() == canvas) select(null);
        });

        MenuButton add = new MenuButton(I18n.t("shader.add_node"), Ui.icon("mdi2p-plus"));
        for (String cat : ShaderNodes.CATEGORIES) {
            if (cat.equals("output")) continue;
            Menu m = new Menu(I18n.t("shader_cat." + cat));
            for (Def d : ShaderNodes.DEFS.values()) {
                if (!d.category().equals(cat)) continue;
                MenuItem mi = new MenuItem(I18n.t("shader_node." + d.id()));
                mi.setOnAction(ev -> addNode(d.id()));
                m.getItems().add(mi);
            }
            add.getItems().add(m);
        }
        Button in = Ui.iconButton("mdi2m-magnify-plus-outline", I18n.t("graph.zoom_in"), () -> setZoom(zoom * 1.15));
        Button out = Ui.iconButton("mdi2m-magnify-minus-outline", I18n.t("graph.zoom_out"), () -> setZoom(zoom / 1.15));
        Button reset = Ui.iconButton("mdi2f-fit-to-screen-outline", I18n.t("graph.fit"), this::fit);
        Label help = Ui.help(I18n.t("shader.graph_help"));
        HBox.setHgrow(help, Priority.ALWAYS);
        HBox tools = Ui.hbox(6, add, help, out, reset, in);
        tools.setAlignment(Pos.CENTER_LEFT);
        tools.setPadding(new Insets(0, 0, 6, 0));
        setTop(tools);
        setCenter(scroll);
        setPrefHeight(520);
        setMinHeight(360);
        rebuild();
        // the whole graph is in view when the editor opens
        scroll.viewportBoundsProperty().addListener((o, a, b) -> {
            if (!fitted && b.getWidth() > 50 && b.getHeight() > 50) {
                fitted = true;
                fit();
            }
        });
    }

    /** Zooms out (down to 60 %) until every node is in view and scrolls to the top left. */
    public void fit() {
        double maxX = 0, maxY = 0;
        for (ShaderNode n : nodes) {
            Region r = views.get(n);
            maxX = Math.max(maxX, n.x + W);
            maxY = Math.max(maxY, n.y + (r == null ? 120 : r.getPrefHeight()));
        }
        double vw = scroll.getViewportBounds().getWidth(), vh = scroll.getViewportBounds().getHeight();
        if (vw <= 0 || vh <= 0) return;
        setZoom(Math.max(0.6, Math.min(1, Math.min(vw / (maxX + 24), vh / (maxY + 24)))));
        scroll.setHvalue(0);
        scroll.setVvalue(0);
    }

    private void setZoom(double z) {
        zoom = Math.max(0.4, Math.min(2.0, z));
        canvas.setScaleX(zoom);
        canvas.setScaleY(zoom);
    }

    private void changed() {
        onChange.run();
    }

    // ------------------------------------------------------------------ structure

    private String nextId() {
        int i = 1;
        while (true) {
            String id = "n" + i;
            if (ShaderNodes.byId(nodes, id) == null) return id;
            i++;
        }
    }

    private void addNode(String type) {
        Point2D c = visibleCenter();
        ShaderNode n = ShaderNodes.create(type, nextId(), Math.max(10, c.getX() - W / 2), Math.max(10, c.getY() - 60));
        nodes.add(n);
        selected = n;
        rebuild();
        changed();
    }

    private Point2D visibleCenter() {
        double vw = scroll.getViewportBounds().getWidth(), vh = scroll.getViewportBounds().getHeight();
        double cw = canvas.getWidth() * zoom, ch = canvas.getHeight() * zoom;
        double x = (Math.max(0, cw - vw) * scroll.getHvalue() + vw / 2) / zoom;
        double y = (Math.max(0, ch - vh) * scroll.getVvalue() + vh / 2) / zoom;
        return new Point2D(x, y);
    }

    private void remove(ShaderNode n) {
        nodes.remove(n);
        for (ShaderNode o : nodes) o.links.values().removeIf(l -> l.startsWith(n.id + ":"));
        if (selected == n) selected = null;
        rebuild();
        changed();
    }

    private void select(ShaderNode n) {
        if (selected == n) return;
        ShaderNode old = selected;
        selected = n;
        if (old != null && views.get(old) != null) views.get(old).getStyleClass().remove("selected");
        if (n != null && views.get(n) != null) views.get(n).getStyleClass().add("selected");
    }

    public void rebuild() {
        canvas.getChildren().clear();
        views.clear();
        canvas.getChildren().add(wires);
        for (ShaderNode n : nodes) {
            Region r = nodeView(n);
            views.put(n, r);
            canvas.getChildren().add(r);
        }
        drawWires();
    }

    // ------------------------------------------------------------------ geometry

    private static Point2D inPos(ShaderNode n, int index) {
        return new Point2D(n.x, n.y + HEAD + index * ROW + ROW / 2);
    }

    private static Point2D outPos(ShaderNode n, Def d, int index) {
        return new Point2D(n.x + W, n.y + HEAD + (d.ins().size() + index) * ROW + ROW / 2);
    }

    private static Color typeColor(VT t) {
        return switch (t) {
            case F -> Color.web("#9aa4b2");
            case V2 -> Color.web("#4cc38a");
            case V3 -> Color.web("#4da3ff");
            case V4 -> Color.web("#ff7a59");
        };
    }

    private static String categoryColor(String cat) {
        return switch (cat) {
            case "input" -> "#3b6fd8";
            case "math" -> "#6b6f80";
            case "vector" -> "#2f9c6b";
            case "color" -> "#c2477a";
            case "pattern" -> "#b8822a";
            default -> "#7a52c7";
        };
    }

    private void drawWires() {
        wires.getChildren().clear();
        for (ShaderNode n : nodes) {
            Def d = ShaderNodes.def(n);
            if (d == null) continue;
            for (int k = 0; k < d.ins().size(); k++) {
                String link = n.links.get(d.ins().get(k).key());
                if (link == null) continue;
                int c = link.indexOf(':');
                ShaderNode src = ShaderNodes.byId(nodes, c < 0 ? link : link.substring(0, c));
                Def sd = src == null ? null : ShaderNodes.def(src);
                if (sd == null) continue;
                int oi = 0;
                for (int j = 0; j < sd.outs().size(); j++) if (sd.outs().get(j).key().equals(c < 0 ? "out" : link.substring(c + 1))) oi = j;
                if (sd.outs().isEmpty()) continue;
                Point2D a = outPos(src, sd, oi), b = inPos(n, k);
                wires.getChildren().add(curve(a, b, typeColor(sd.outs().get(oi).type())));
            }
        }
        if (dragWire != null) wires.getChildren().add(dragWire);
    }

    private static CubicCurve curve(Point2D a, Point2D b, Color color) {
        CubicCurve cc = new CubicCurve();
        set(cc, a, b);
        cc.setFill(null);
        cc.setStroke(color);
        cc.setStrokeWidth(2.4);
        return cc;
    }

    private static void set(CubicCurve cc, Point2D a, Point2D b) {
        double dx = Math.max(40, Math.abs(b.getX() - a.getX()) * 0.5);
        cc.setStartX(a.getX());
        cc.setStartY(a.getY());
        cc.setControlX1(a.getX() + dx);
        cc.setControlY1(a.getY());
        cc.setControlX2(b.getX() - dx);
        cc.setControlY2(b.getY());
        cc.setEndX(b.getX());
        cc.setEndY(b.getY());
    }

    // ------------------------------------------------------------------ node view

    private Region nodeView(ShaderNode n) {
        Def d = ShaderNodes.def(n);
        Pane box = new Pane();
        box.getStyleClass().add("shader-node");
        if (n == selected) box.getStyleClass().add("selected");
        int rows = d == null ? 0 : d.ins().size() + d.outs().size();
        double h = HEAD + rows * ROW + 6;
        box.setPrefSize(W, h);
        box.setMinSize(W, h);
        box.setMaxSize(W, h);
        box.relocate(n.x, n.y);

        Label title = new Label(d == null ? n.type : I18n.t("shader_node." + d.id()));
        title.getStyleClass().add("shader-node-title");
        title.setStyle("-fx-background-color: " + categoryColor(d == null ? "" : d.category()) + ";");
        title.setPrefSize(W, HEAD);
        title.setMinSize(W, HEAD);
        title.setPadding(new Insets(0, 8, 0, 10));
        box.getChildren().add(title);
        if (d != null && !d.id().equals("output")) {
            Button close = Ui.iconButton("mdi2c-close", I18n.t("action.remove"), () -> remove(n));
            close.getStyleClass().add("shader-node-close");
            close.relocate(W - 26, 2);
            box.getChildren().add(close);
        }
        title.setOnMousePressed(e -> {
            select(n);
            canvas.requestFocus();
            pressX = e.getSceneX();
            pressY = e.getSceneY();
            nodeX = n.x;
            nodeY = n.y;
            e.consume();
        });
        title.setOnMouseDragged(e -> {
            n.x = Math.max(0, nodeX + (e.getSceneX() - pressX) / zoom);
            n.y = Math.max(0, nodeY + (e.getSceneY() - pressY) / zoom);
            box.relocate(n.x, n.y);
            drawWires();
            e.consume();
        });
        title.setOnMouseReleased(e -> {
            if (n.x != nodeX || n.y != nodeY) changed();
            e.consume();
        });
        box.setOnMousePressed(e -> {
            select(n);
            e.consume();
        });
        if (d == null) return box;

        for (int k = 0; k < d.ins().size(); k++) {
            Port p = d.ins().get(k);
            double y = HEAD + k * ROW;
            boolean linked = n.links.containsKey(p.key());
            Label name = Ui.label(I18n.t("shader_port." + p.key()), "text-small");
            name.relocate(12, y + 6);
            box.getChildren().add(name);
            if (!p.fixed()) {
                Circle c = port(p.type(), linked);
                c.setCenterX(0);
                c.setCenterY(y + ROW / 2);
                Tooltip.install(c, new Tooltip(p.type().glsl + (linked ? "  " + I18n.t("shader.port_unlink") : "")));
                c.setOnMousePressed(e -> {
                    if (n.links.remove(p.key()) != null) {
                        rebuild();
                        changed();
                    }
                    e.consume();
                });
                box.getChildren().add(c);
            }
            if (!linked) {
                javafx.scene.Node editor = editor(n, p);
                editor.relocate(W - 8 - 84, y + 3);
                box.getChildren().add(editor);
            }
        }
        for (int j = 0; j < d.outs().size(); j++) {
            Port p = d.outs().get(j);
            double y = HEAD + (d.ins().size() + j) * ROW;
            Label name = Ui.label(I18n.t("shader_port." + p.key()) + "  " + p.type().glsl, "text-small");
            name.setPrefWidth(W - 16);
            name.setAlignment(Pos.CENTER_RIGHT);
            name.relocate(0, y + 6);
            box.getChildren().add(name);
            Circle c = port(p.type(), true);
            c.setCenterX(W);
            c.setCenterY(y + ROW / 2);
            String from = n.id + ":" + p.key();
            Point2D start = outPos(n, d, j);
            c.setOnMousePressed(e -> {
                dragFrom = from;
                dragWire = curve(start, start, typeColor(p.type()));
                dragWire.getStrokeDashArray().addAll(6.0, 4.0);
                drawWires();
                e.consume();
            });
            c.setOnMouseDragged(e -> {
                if (dragWire == null) return;
                Point2D m = canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
                set(dragWire, outPos(n, d, indexOf(d, p)), m);
                e.consume();
            });
            c.setOnMouseReleased(e -> {
                Point2D m = canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
                dragWire = null;
                connect(from, m);
                e.consume();
            });
            box.getChildren().add(c);
        }
        return box;
    }

    private static int indexOf(Def d, Port p) {
        return d.outs().indexOf(p);
    }

    private static Circle port(VT type, boolean filled) {
        Circle c = new Circle(R);
        c.setStroke(typeColor(type));
        c.setStrokeWidth(2);
        c.setFill(filled ? typeColor(type) : Color.web("#1e1e1e"));
        c.getStyleClass().add("shader-port");
        return c;
    }

    /** Links the output to the input nearest to the point, unless that makes a loop. */
    private void connect(String from, Point2D at) {
        ShaderNode src = ShaderNodes.byId(nodes, from.substring(0, from.indexOf(':')));
        ShaderNode best = null;
        String bestKey = null;
        double bestD = 16;
        for (ShaderNode n : nodes) {
            Def d = ShaderNodes.def(n);
            if (d == null || n == src) continue;
            for (int k = 0; k < d.ins().size(); k++) {
                if (d.ins().get(k).fixed()) continue;
                double dist = inPos(n, k).distance(at);
                if (dist < bestD) {
                    bestD = dist;
                    best = n;
                    bestKey = d.ins().get(k).key();
                }
            }
        }
        if (best != null && src != null && !ShaderNodes.wouldLoop(nodes, src, best)) {
            best.links.put(bestKey, from);
            rebuild();
            changed();
        } else {
            drawWires();
        }
    }

    /** The value of an input without a wire: a colour picker for colours, a text field for numbers and vectors. */
    private javafx.scene.Node editor(ShaderNode n, Port p) {
        String v = ShaderNodes.value(n, p);
        if (v.startsWith("@")) {
            Label l = Ui.label("= " + I18n.t("shader_value." + v.substring(1)), "text-muted", "text-small");
            l.setPrefWidth(84);
            l.setAlignment(Pos.CENTER_RIGHT);
            l.setPadding(new Insets(3, 0, 0, 0));
            return l;
        }
        if (p.type() == VT.V4 && v.startsWith("#")) {
            ColorPicker cp = new ColorPicker(safeColor(v));
            cp.getStyleClass().add("button");
            cp.setPrefWidth(84);
            cp.setMaxHeight(ROW - 6);
            cp.valueProperty().addListener((o, a, b) -> {
                n.values.put(p.key(), hex(b));
                changed();
            });
            return cp;
        }
        TextField tf = new TextField(v);
        tf.setPrefWidth(84);
        tf.setPrefHeight(ROW - 6);
        tf.getStyleClass().add("shader-value");
        tf.textProperty().addListener((o, a, b) -> {
            n.values.put(p.key(), b);
            changed();
        });
        tf.setOnMousePressed(MouseEvent::consume);
        return tf;
    }

    private static Color safeColor(String v) {
        try {
            return Color.web(v);
        } catch (RuntimeException e) {
            return Color.WHITE;
        }
    }

    private static String hex(Color c) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
    }

    /** Nodes in the graph, for tests and snapshots. */
    public List<ShaderNode> nodes() {
        return new ArrayList<>(nodes);
    }
}
