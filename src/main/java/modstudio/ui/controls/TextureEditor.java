package modstudio.ui.controls;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import modstudio.core.Ids;
import modstudio.core.Images;
import modstudio.core.TextureAsset;
import modstudio.core.TextureTemplates;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;

import java.nio.file.Files;
import java.util.*;

/** Pixel editor for project textures. */
public final class TextureEditor {
    private enum Tool { PENCIL, ERASER, FILL, PICKER, LINE, RECT }

    private static final int[] PALETTE = {
            0xFF000000, 0xFF3F3F3F, 0xFF7F7F7F, 0xFFBFBFBF, 0xFFFFFFFF, 0xFF6B3E1E, 0xFF9C6B3A, 0xFFD8B27A,
            0xFFD8263F, 0xFFF0782A, 0xFFF5D547, 0xFF5DBB45, 0xFF2E8B57, 0xFF3FB8D8, 0xFF2F5FD0, 0xFF8E44C9};

    private final Stage stage = new Stage();
    private final TextureAsset asset;
    private int w, h;
    private int[] px;
    private final Deque<int[]> undo = new ArrayDeque<>();
    private final Deque<int[]> redo = new ArrayDeque<>();
    private Tool tool = Tool.PENCIL;
    private final ColorPicker color = new ColorPicker(Color.web("#D8263F"));
    private final Canvas canvas = new Canvas(512, 512);
    private final ImageView preview1 = new ImageView();
    private final ImageView preview2 = new ImageView();
    private final FlowPane shades = new FlowPane(4, 4);
    private final FlowPane recent = new FlowPane(4, 4);
    private final List<Integer> recentColors = new ArrayList<>();
    private final TextField name = new TextField();
    private final Map<Tool, ToggleButton> toolButtons = new EnumMap<>(Tool.class);
    private final Label status = Ui.label("", "text-muted", "text-small");
    private int hoverX = -1, hoverY = -1;
    private int startX, startY, lastX, lastY;
    private boolean dragging;
    private int[] shapeBase;
    private boolean saved;
    private boolean grid = true;
    // Animation: frames are square and stacked from top to bottom, like the game expects them.
    private int fcount = 1;
    private int frame;
    private int frameTime;
    private boolean interp;
    private boolean onion = true;
    private Label frameLabel;
    private final Spinner<Integer> framesSpin = new Spinner<>(1, 64, 1);
    private final List<Node> animOnly = new ArrayList<>();
    private boolean updatingFrames;
    private javafx.animation.Timeline animTimer;
    private final ImageView animView = new ImageView();

    private TextureEditor(Window owner, TextureAsset asset, String templateHint, java.util.function.Function<String, String> nameCheck) {
        this.asset = asset;
        this.px = asset.pixels();
        this.w = asset.width;
        this.h = asset.height;
        if (px.length != w * h) {
            w = 16;
            h = 16;
            px = new int[256];
        }
        fcount = asset.frames();
        frameTime = asset.frameTime;
        interp = asset.interpolate;
        stage.initOwner(owner);
        stage.setOnHidden(e -> {
            if (animTimer != null) animTimer.stop();
        });
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(I18n.t("texture.editor_title"));

        name.setText(asset.name);
        name.setPrefColumnCount(18);
        Label nameErr = Ui.label("", "error-text", "text-small");
        name.textProperty().addListener((o, a, b) -> {
            String msg = nameCheck.apply(b.trim());
            nameErr.setText(msg == null ? "" : msg);
            Ui.setDanger(name, msg != null);
        });

        // Tools
        ToggleGroup tg = new ToggleGroup();
        VBox tools = new VBox(4);
        tools.getChildren().add(Ui.label(I18n.t("texture.tools"), "form-label"));
        addTool(tools, tg, Tool.PENCIL, "mdi2p-pencil", "B");
        addTool(tools, tg, Tool.ERASER, "mdi2e-eraser", "E");
        addTool(tools, tg, Tool.FILL, "mdi2f-format-color-fill", "G");
        addTool(tools, tg, Tool.PICKER, "mdi2e-eyedropper", "I");
        addTool(tools, tg, Tool.LINE, "mdi2v-vector-line", "L");
        addTool(tools, tg, Tool.RECT, "mdi2r-rectangle-outline", "R");
        toolButtons.get(Tool.PENCIL).setSelected(true);

        Button undoB = Ui.iconButton("mdi2u-undo", I18n.t("texture.undo") + " (Ctrl+Z)", this::undo);
        Button redoB = Ui.iconButton("mdi2r-redo", I18n.t("texture.redo") + " (Ctrl+Y)", this::redo);
        Button flipH = Ui.iconButton("mdi2f-flip-horizontal", I18n.t("texture.flip_h"), () -> transform(0));
        Button flipV = Ui.iconButton("mdi2f-flip-vertical", I18n.t("texture.flip_v"), () -> transform(1));
        Button rot = Ui.iconButton("mdi2r-rotate-right", I18n.t("texture.rotate"), () -> transform(2));
        Button clear = Ui.iconButton("mdi2d-delete-outline", I18n.t("texture.clear"), () -> {
            push();
            Arrays.fill(px, y0() * w, (y0() + fh()) * w, 0);
            redraw();
        });
        ToggleButton gridB = new ToggleButton();
        gridB.setGraphic(Ui.icon("mdi2g-grid"));
        gridB.setTooltip(new Tooltip(I18n.t("texture.grid")));
        gridB.setSelected(true);
        gridB.getStyleClass().add("button-icon");
        gridB.selectedProperty().addListener((o, a, b) -> {
            grid = b;
            redraw();
        });
        FlowPane edits = new FlowPane(2, 2, undoB, redoB, flipH, flipV, rot, gridB, clear);
        edits.setPrefWrapLength(120);
        tools.getChildren().addAll(new Separator(), edits);
        tools.setPrefWidth(130);

        // Canvas
        canvas.setOnMousePressed(this::pressed);
        canvas.setOnMouseDragged(this::dragged);
        canvas.setOnMouseReleased(this::released);
        canvas.setOnMouseMoved(e -> {
            hoverX = cx(e);
            hoverY = cy(e);
            status.setText(inside(hoverX, hoverY) ? hoverX + ", " + (hoverY - y0()) + (fcount > 1 ? "  |  " + I18n.t("texture.frame_of", frame + 1, fcount) : "") : "");
            redraw();
        });
        canvas.setOnMouseExited(e -> {
            hoverX = hoverY = -1;
            redraw();
        });
        StackPane canvasBox = new StackPane(canvas);
        canvasBox.getStyleClass().add("canvas-box");
        canvasBox.setPadding(new Insets(8));

        // Colors
        color.valueProperty().addListener((o, a, b) -> updateShades());
        color.setMaxWidth(Double.MAX_VALUE);
        FlowPane palette = new FlowPane(4, 4);
        for (int c : PALETTE) palette.getChildren().add(swatch(c));
        palette.setPrefWrapLength(200);
        shades.setPrefWrapLength(200);
        recent.setPrefWrapLength(200);

        ComboBox<String> template = Ui.combo(Arrays.asList(TextureTemplates.names()), t -> I18n.tOr("template." + t, Ids.titleCase(t)));
        template.setValue(templateHint != null && Arrays.asList(TextureTemplates.names()).contains(templateHint) ? templateHint : "gem");
        template.setMaxWidth(Double.MAX_VALUE);
        Button gen = Ui.button(I18n.t("texture.generate"), "mdi2a-auto-fix", () -> {
            push();
            int[] g = TextureTemplates.generate(template.getValue(), argb(color.getValue()));
            int[] gs = TextureTemplates.size(template.getValue());
            int fh = fh();
            int[] f = (w == gs[0] && fh == gs[1]) ? g : Images.resize(g, gs[0], gs[1], w, fh);
            System.arraycopy(f, 0, px, y0() * w, f.length);
            redraw();
        });
        gen.setMaxWidth(Double.MAX_VALUE);

        ComboBox<Integer> size = Ui.combo(List.of(16, 32, 64), s -> s + " x " + s);
        size.setValue(w == fh() && List.of(16, 32, 64).contains(w) ? w : null);
        size.valueProperty().addListener((o, a, b) -> {
            if (b == null || (b == w && b == fh())) return;
            push();
            px = Images.resize(px, w, h, b, b * fcount);
            w = b;
            h = b * fcount;
            redraw();
        });

        preview1.setSmooth(false);
        preview2.setSmooth(false);
        HBox previews = Ui.hbox(12, framed(preview1), framed(preview2));

        // Animation
        framesSpin.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 64, fcount));
        framesSpin.setEditable(true);
        framesSpin.setPrefWidth(90);
        framesSpin.valueProperty().addListener((o, a, b) -> {
            if (!updatingFrames && b != null) setFrameCount(b);
        });
        frameLabel = Ui.label("", "text-small");
        Button prevF = Ui.iconButton("mdi2c-chevron-left", I18n.t("texture.prev_frame") + " (,)", () -> goFrame(-1));
        Button nextF = Ui.iconButton("mdi2c-chevron-right", I18n.t("texture.next_frame") + " (.)", () -> goFrame(1));
        Button dupF = Ui.iconButton("mdi2c-content-duplicate", I18n.t("texture.dup_frame"), this::duplicateFrame);
        Button delF = Ui.iconButton("mdi2d-delete-outline", I18n.t("texture.delete_frame"), this::deleteFrame);
        Spinner<Integer> timeSpin = new Spinner<>(1, 200, Math.max(1, frameTime == 0 ? 2 : frameTime));
        timeSpin.setEditable(true);
        timeSpin.setPrefWidth(90);
        timeSpin.valueProperty().addListener((o, a, b) -> {
            if (b == null) return;
            frameTime = b;
            restartAnimation();
        });
        CheckBox interpB = new CheckBox(I18n.t("texture.interpolate"));
        interpB.setSelected(interp);
        interpB.selectedProperty().addListener((o, a, b) -> interp = b);
        CheckBox onionB = new CheckBox(I18n.t("texture.onion"));
        onionB.setSelected(onion);
        onionB.selectedProperty().addListener((o, a, b) -> {
            onion = b;
            redraw();
        });
        animView.setSmooth(false);
        animOnly.addAll(List.of(prevF, nextF, delF, timeSpin, interpB, onionB));
        VBox anim = new VBox(6,
                Ui.hbox(6, Ui.label(I18n.t("texture.frames"), "text-small"), Ui.spacer(), framesSpin),
                Ui.hbox(2, prevF, frameLabel, nextF, Ui.spacer(), dupF, delF),
                Ui.hbox(6, Ui.label(I18n.t("texture.frame_time"), "text-small"), Ui.spacer(), timeSpin),
                interpB, onionB, framed(animView));

        Button importB = Ui.button(I18n.t("texture.import"), "mdi2i-import", this::importPng);
        Button exportB = Ui.button(I18n.t("texture.export"), "mdi2e-export-variant", this::exportPng);
        importB.setMaxWidth(Double.MAX_VALUE);
        exportB.setMaxWidth(Double.MAX_VALUE);

        VBox right = new VBox(8,
                Ui.label(I18n.t("texture.color"), "form-label"), color,
                Ui.label(I18n.t("texture.palette"), "form-label"), palette,
                Ui.label(I18n.t("texture.shades"), "form-label"), shades,
                Ui.label(I18n.t("texture.recent"), "form-label"), recent,
                new Separator(),
                Ui.label(I18n.t("texture.template"), "form-label"), template, gen,
                new Separator(),
                Ui.label(I18n.t("texture.size"), "form-label"), size,
                Ui.label(I18n.t("texture.preview"), "form-label"), previews,
                new Separator(),
                Ui.label(I18n.t("texture.animation"), "form-label"), anim,
                new Separator(), importB, exportB);
        right.setPrefWidth(220);
        ScrollPane rightScroll = Ui.scroll(right);
        rightScroll.setPrefWidth(244);
        rightScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        HBox top = Ui.hbox(10, Ui.label(I18n.t("texture.name"), "form-label"), name, nameErr, Ui.spacer(), status);
        Button cancel = Ui.button(I18n.t("action.cancel"), null, stage::close);
        Button save = Ui.accent(I18n.t("action.save"), "mdi2c-check", () -> {
            if (nameCheck.apply(name.getText().trim()) != null) return;
            asset.name = name.getText().trim();
            asset.width = w;
            asset.height = h;
            asset.png = Images.encode(px, w, h);
            asset.frameTime = fcount > 1 ? Math.max(1, frameTime == 0 ? 2 : frameTime) : 0;
            asset.interpolate = fcount > 1 && interp;
            saved = true;
            stage.close();
        });
        save.setDefaultButton(false);
        HBox bottom = Ui.hbox(8, Ui.help(I18n.t("texture.hint")), Ui.spacer(), cancel, save);

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setLeft(tools);
        root.setCenter(canvasBox);
        root.setRight(rightScroll);
        root.setBottom(bottom);
        BorderPane.setMargin(top, new Insets(0, 0, 10, 0));
        BorderPane.setMargin(bottom, new Insets(10, 0, 0, 0));
        BorderPane.setMargin(tools, new Insets(0, 10, 0, 0));
        BorderPane.setMargin(rightScroll, new Insets(0, 0, 0, 10));
        root.setPadding(new Insets(14));
        root.getStyleClass().add("texture-editor");

        Scene scene = new Scene(root, 960, 680);
        Ui.style(scene);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), this::undo);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN), this::redo);
        scene.setOnKeyPressed(e -> {
            if (name.isFocused()) return;
            switch (e.getCode()) {
                case B -> selectTool(Tool.PENCIL);
                case E -> selectTool(Tool.ERASER);
                case G -> selectTool(Tool.FILL);
                case I -> selectTool(Tool.PICKER);
                case L -> selectTool(Tool.LINE);
                case R -> selectTool(Tool.RECT);
                case COMMA -> goFrame(-1);
                case PERIOD -> goFrame(1);
                default -> {
                }
            }
        });
        stage.setScene(scene);
        updateShades();
        redraw();
    }

    /** Opens the editor; returns true when the user saved. */
    public static boolean edit(Window owner, TextureAsset asset, String templateHint, java.util.function.Function<String, String> nameCheck) {
        TextureEditor ed = new TextureEditor(owner, asset, templateHint, nameCheck);
        ed.stage.showAndWait();
        return ed.saved;
    }

    /** Creates the editor without showing it (used for screenshots). */
    public static Scene sceneFor(Window owner, TextureAsset asset) {
        return new TextureEditor(owner, asset, "gem", n -> null).stage.getScene();
    }

    // ------------------------------------------------------------------ UI parts

    private void addTool(VBox box, ToggleGroup tg, Tool t, String icon, String key) {
        ToggleButton b = new ToggleButton(I18n.t("texture.tool_" + t.name().toLowerCase()));
        b.setGraphic(Ui.icon(icon));
        b.setToggleGroup(tg);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setTooltip(new Tooltip(I18n.t("texture.tool_" + t.name().toLowerCase()) + " (" + key + ")"));
        b.setOnAction(e -> {
            tool = t;
            if (!b.isSelected()) b.setSelected(true);
        });
        toolButtons.put(t, b);
        box.getChildren().add(b);
    }

    private void selectTool(Tool t) {
        tool = t;
        toolButtons.get(t).setSelected(true);
    }

    private StackPane framed(ImageView iv) {
        StackPane sp = new StackPane(iv);
        sp.getStyleClass().add("preview-frame");
        sp.setPadding(new Insets(6));
        return sp;
    }

    private Region swatch(int argb) {
        Region r = new Region();
        r.setPrefSize(20, 20);
        r.setMinSize(20, 20);
        r.getStyleClass().add("swatch");
        r.setStyle("-fx-background-color: " + web(argb) + ";");
        Tooltip.install(r, new Tooltip(web(argb)));
        r.setOnMouseClicked(e -> color.setValue(fx(argb)));
        return r;
    }

    private void updateShades() {
        shades.getChildren().clear();
        for (int c : TextureTemplates.shades(argb(color.getValue()))) shades.getChildren().add(swatch(c));
    }

    private void rememberColor(int argb) {
        if ((argb >>> 24) == 0) return;
        recentColors.remove(Integer.valueOf(argb));
        recentColors.add(0, argb);
        while (recentColors.size() > 16) recentColors.remove(recentColors.size() - 1);
        recent.getChildren().clear();
        for (int c : recentColors) recent.getChildren().add(swatch(c));
    }

    // ------------------------------------------------------------------ painting

    // The pixels are one strip of square frames stacked from top to bottom; the canvas shows the current frame.

    /** Height of one frame. */
    private int fh() {
        return fcount > 1 ? w : h;
    }

    /** First row of the current frame in the strip. */
    private int y0() {
        return frame * fh();
    }

    private double zoom() {
        return Math.floor(Math.min(canvas.getWidth() / w, canvas.getHeight() / fh()));
    }

    private int cx(MouseEvent e) {
        return (int) Math.floor((e.getX() - offX()) / zoom());
    }

    private int cy(MouseEvent e) {
        return (int) Math.floor((e.getY() - offY()) / zoom()) + y0();
    }

    private double offX() {
        return (canvas.getWidth() - zoom() * w) / 2;
    }

    private double offY() {
        return (canvas.getHeight() - zoom() * fh()) / 2;
    }

    private boolean inside(int x, int y) {
        return x >= 0 && x < w && y >= y0() && y < y0() + fh() && y < h;
    }

    private int paintColor(MouseEvent e) {
        if (tool == Tool.ERASER || e.getButton() == MouseButton.SECONDARY) return 0;
        return argb(color.getValue());
    }

    private void pressed(MouseEvent e) {
        int x = cx(e), y = cy(e);
        if (!inside(x, y)) return;
        if (tool == Tool.PICKER) {
            int c = px[y * w + x];
            if ((c >>> 24) != 0) color.setValue(fx(c));
            selectTool(Tool.PENCIL);
            return;
        }
        push();
        int c = paintColor(e);
        switch (tool) {
            case PENCIL, ERASER -> set(x, y, c);
            case FILL -> fill(x, y, c);
            case LINE, RECT -> shapeBase = px.clone();
            default -> {
            }
        }
        if (c != 0) rememberColor(c);
        startX = lastX = x;
        startY = lastY = y;
        dragging = true;
        redraw();
    }

    private void dragged(MouseEvent e) {
        if (!dragging) return;
        int x = Math.max(0, Math.min(w - 1, cx(e))), y = Math.max(y0(), Math.min(y0() + fh() - 1, cy(e)));
        hoverX = x;
        hoverY = y;
        int c = paintColor(e);
        switch (tool) {
            case PENCIL, ERASER -> line(lastX, lastY, x, y, c);
            case LINE -> {
                px = shapeBase.clone();
                line(startX, startY, x, y, c);
            }
            case RECT -> {
                px = shapeBase.clone();
                rect(startX, startY, x, y, c);
            }
            default -> {
            }
        }
        lastX = x;
        lastY = y;
        redraw();
    }

    private void released(MouseEvent e) {
        dragging = false;
        shapeBase = null;
        redraw();
    }

    private void set(int x, int y, int c) {
        if (inside(x, y)) px[y * w + x] = c;
    }

    private void line(int x0, int y0, int x1, int y1, int c) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            set(x0, y0, c);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private void rect(int x0, int y0, int x1, int y1, int c) {
        line(x0, y0, x1, y0, c);
        line(x1, y0, x1, y1, c);
        line(x1, y1, x0, y1, c);
        line(x0, y1, x0, y0, c);
    }

    private void fill(int x, int y, int c) {
        int target = px[y * w + x];
        if (target == c) return;
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{x, y});
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            int a = p[0], b = p[1];
            if (!inside(a, b) || px[b * w + a] != target) continue;
            px[b * w + a] = c;
            stack.push(new int[]{a + 1, b});
            stack.push(new int[]{a - 1, b});
            stack.push(new int[]{a, b + 1});
            stack.push(new int[]{a, b - 1});
        }
    }

    /** Flips or rotates the current frame (or the whole texture when it is not animated). */
    private void transform(int kind) {
        push();
        int fw = w, fh = fh(), base = y0() * w;
        int[] f = Arrays.copyOfRange(px, base, base + fw * fh);
        int[] r = new int[f.length];
        for (int y = 0; y < fh; y++) {
            for (int x = 0; x < fw; x++) {
                int c = f[y * fw + x];
                switch (kind) {
                    case 0 -> r[y * fw + (fw - 1 - x)] = c;
                    case 1 -> r[(fh - 1 - y) * fw + x] = c;
                    default -> r[x * fh + (fh - 1 - y)] = c;
                }
            }
        }
        if (fcount > 1) {
            System.arraycopy(r, 0, px, base, r.length);
        } else {
            px = r;
            if (kind == 2) {
                w = fh;
                h = fw;
            }
        }
        redraw();
    }

    private void push() {
        undo.push(snapshot());
        if (undo.size() > 100) undo.removeLast();
        redo.clear();
    }

    private int[] snapshot() {
        int[] s = new int[px.length + 4];
        s[0] = w;
        s[1] = h;
        s[2] = fcount;
        s[3] = frame;
        System.arraycopy(px, 0, s, 4, px.length);
        return s;
    }

    private void restore(int[] s) {
        w = s[0];
        h = s[1];
        fcount = s[2];
        frame = s[3];
        px = Arrays.copyOfRange(s, 4, s.length);
    }

    private void undo() {
        if (undo.isEmpty()) return;
        redo.push(snapshot());
        restore(undo.pop());
        redraw();
    }

    private void redo() {
        if (redo.isEmpty()) return;
        undo.push(snapshot());
        restore(redo.pop());
        redraw();
    }

    private void redraw() {
        if (frame >= fcount) frame = fcount - 1;
        GraphicsContext g = canvas.getGraphicsContext2D();
        int fh = fh(), y0 = y0();
        double z = zoom(), ox = offX(), oy = offY();
        g.setFill(Ui.dark() ? Color.web("#161b22") : Color.web("#f6f8fa"));
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        boolean onionSkin = onion && fcount > 1;
        int prev = (frame + fcount - 1) % fcount;
        // checkerboard for transparency
        for (int y = 0; y < fh; y++) {
            for (int x = 0; x < w; x++) {
                boolean light = ((x + y) & 1) == 0;
                g.setFill(light ? Color.web("#d9d9d9") : Color.web("#bfbfbf"));
                g.fillRect(ox + x * z, oy + y * z, z, z);
                int c = px[(y0 + y) * w + x];
                if ((c >>> 24) != 0) {
                    g.setFill(fx(c));
                    g.fillRect(ox + x * z, oy + y * z, z, z);
                } else if (onionSkin) {
                    int o = px[(prev * fh + y) * w + x];
                    if ((o >>> 24) != 0) {
                        Color oc = fx(o);
                        g.setFill(Color.color(oc.getRed(), oc.getGreen(), oc.getBlue(), 0.3));
                        g.fillRect(ox + x * z, oy + y * z, z, z);
                    }
                }
            }
        }
        if (grid && z >= 6) {
            g.setStroke(Color.rgb(0, 0, 0, 0.16));
            g.setLineWidth(1);
            for (int x = 0; x <= w; x++) g.strokeLine(ox + x * z + 0.5, oy, ox + x * z + 0.5, oy + fh * z);
            for (int y = 0; y <= fh; y++) g.strokeLine(ox, oy + y * z + 0.5, ox + w * z, oy + y * z + 0.5);
        }
        if (inside(hoverX, hoverY)) {
            g.setStroke(Color.web("#2f81f7"));
            g.setLineWidth(2);
            g.strokeRect(ox + hoverX * z + 1, oy + (hoverY - y0) * z + 1, z - 2, z - 2);
        }
        int[] cur = Arrays.copyOfRange(px, y0 * w, (y0 + fh) * w);
        preview1.setImage(Ui.pixelImage(cur, w, fh, Math.max(w, fh)));
        preview2.setImage(Ui.pixelImage(cur, w, fh, Math.max(w, fh) * (w <= 16 ? 4 : 2)));
        updateFrameControls();
    }

    // ------------------------------------------------------------------ animation

    private void updateFrameControls() {
        if (frameLabel == null) return;
        frameLabel.setText(I18n.t("texture.frame_of", frame + 1, fcount));
        boolean anim = fcount > 1;
        for (Node n : animOnly) n.setDisable(!anim);
        if (framesSpin.getValue() != fcount) {
            updatingFrames = true;
            framesSpin.getValueFactory().setValue(fcount);
            updatingFrames = false;
        }
        restartAnimation();
    }

    /** Changes the number of frames: new frames copy the last one, removed frames are dropped from the end. */
    private void setFrameCount(int n) {
        if (n == fcount || n < 1) return;
        push();
        if (fcount == 1 && h != w) {
            px = Images.resize(px, w, h, w, w);
            h = w;
        }
        int size = w * w;
        int[] r = new int[size * n];
        for (int i = 0; i < n; i++) System.arraycopy(px, Math.min(i, fcount - 1) * size, r, i * size, size);
        px = r;
        h = w * n;
        fcount = n;
        if (n > 1 && frameTime <= 0) frameTime = 2;
        frame = Math.min(frame, n - 1);
        redraw();
    }

    private void duplicateFrame() {
        if (fcount == 1 && h != w) return;
        push();
        int size = w * w;
        int[] r = new int[px.length + size];
        System.arraycopy(px, 0, r, 0, (frame + 1) * size);
        System.arraycopy(px, frame * size, r, (frame + 1) * size, size);
        System.arraycopy(px, (frame + 1) * size, r, (frame + 2) * size, px.length - (frame + 1) * size);
        px = r;
        fcount++;
        h = w * fcount;
        if (frameTime <= 0) frameTime = 2;
        frame++;
        redraw();
    }

    private void deleteFrame() {
        if (fcount <= 1) return;
        push();
        int size = w * w;
        int[] r = new int[px.length - size];
        System.arraycopy(px, 0, r, 0, frame * size);
        System.arraycopy(px, (frame + 1) * size, r, frame * size, px.length - (frame + 1) * size);
        px = r;
        fcount--;
        h = w * fcount;
        frame = Math.min(frame, fcount - 1);
        redraw();
    }

    private void goFrame(int d) {
        frame = ((frame + d) % fcount + fcount) % fcount;
        redraw();
    }

    /** Plays the frames in the preview at the game speed (20 ticks per second). */
    private void restartAnimation() {
        if (animTimer != null) animTimer.stop();
        if (fcount <= 1) {
            animView.setImage(preview1.getImage());
            return;
        }
        int size = w * w;
        List<javafx.scene.image.Image> imgs = new ArrayList<>();
        for (int i = 0; i < fcount; i++) {
            imgs.add(Ui.pixelImage(Arrays.copyOfRange(px, i * size, (i + 1) * size), w, w, w * (w <= 16 ? 4 : 2)));
        }
        int[] i = {0};
        animView.setImage(imgs.get(0));
        animTimer = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(Math.max(1, frameTime) * 50.0), e -> {
            i[0] = (i[0] + 1) % imgs.size();
            animView.setImage(imgs.get(i[0]));
        }));
        animTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        animTimer.play();
    }

    // ------------------------------------------------------------------ files

    private void importPng() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        var f = fc.showOpenDialog(stage);
        if (f == null) return;
        try {
            int[] wh = new int[2];
            int[] data = Images.read(Files.readAllBytes(f.toPath()), wh);
            push();
            boolean strip = wh[1] > wh[0] && wh[1] % wh[0] == 0;
            if (strip && wh[0] > 256) {
                int n = wh[1] / wh[0];
                data = Images.resize(data, wh[0], wh[1], 64, 64 * n);
                wh[0] = 64;
                wh[1] = 64 * n;
            } else if (!strip && (wh[0] > 256 || wh[1] > 256)) {
                int s = 64;
                data = Images.resize(data, wh[0], wh[1], s, s);
                wh[0] = wh[1] = s;
            }
            px = data;
            w = wh[0];
            h = wh[1];
            // A vertical strip of square frames is an animation, as in the game's own textures.
            fcount = h > w && h % w == 0 ? h / w : 1;
            frame = 0;
            if (fcount > 1 && frameTime <= 0) frameTime = 2;
            redraw();
        } catch (Exception ex) {
            Ui.error(stage, I18n.t("texture.import_failed"), ex);
        }
    }

    private void exportPng() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        fc.setInitialFileName(name.getText().trim() + ".png");
        var f = fc.showSaveDialog(stage);
        if (f == null) return;
        try {
            Files.write(f.toPath(), Images.png(px, w, h));
        } catch (Exception ex) {
            Ui.error(stage, I18n.t("texture.export_failed"), ex);
        }
    }

    // ------------------------------------------------------------------ colors

    public static int argb(Color c) {
        int a = (int) Math.round(c.getOpacity() * 255);
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static Color fx(int argb) {
        return Color.rgb((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, ((argb >>> 24) & 255) / 255.0);
    }

    private static String web(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }
}
