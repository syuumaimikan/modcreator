package modstudio.ui.editors;

import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import modstudio.core.CameraPath;
import modstudio.core.Logic;
import modstudio.core.Systems.CameraEvent;
import modstudio.core.Systems.CameraKey;
import modstudio.core.Systems.Cutscene;
import modstudio.ui.Ui;
import modstudio.ui.controls.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

/** Cutscene: camera keys with a path preview (top and side views) and a timeline, plus timed titles and sounds. */
final class CutsceneEditor extends EditorBase<Cutscene> {
    static final List<String> EVENT_KINDS = List.of("title", "subtitle", "actionbar", "sound", "fade", "command");
    private final VBox keyRows = new VBox(6);
    private final VBox eventRows = new VBox(6);
    private final Canvas canvas = new Canvas(640, 230);
    private final Slider time = new Slider(0, 1, 0);
    private final Label timeLabel = Ui.muted("");
    private AnimationTimer player;
    private long playStart;
    private double playFrom;

    CutsceneEditor(Ctx c, Cutscene e) {
        super(c, e);
    }

    @Override
    protected void fill() {
        Button play = Ui.button(t("cutscene.play"), "mdi2p-play", this::togglePlay);
        time.valueProperty().addListener((o, a, b) -> draw());
        HBox.setHgrow(time, Priority.ALWAYS);
        HBox bar = new HBox(10, play, time, timeLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        VBox preview = new VBox(8, canvas, bar);
        preview.getStyleClass().add("preview-strip");
        f.addTop(preview);

        f.section(t("section.general"), t("help.cutscene"));
        nameRow();
        f.choice(t("field.cutscene_space"), List.of("relative", "absolute"), x -> t("cutscene.space." + x), () -> e.space, v -> e.space = v,
                t("help.cutscene_space"));
        f.check(t("field.cutscene_skippable"), () -> e.skippable, v -> e.skippable = v, t("help.cutscene_skippable"));
        f.check(t("field.cutscene_return"), () -> e.returnToStart, v -> e.returnToStart = v);

        f.section(t("section.camera_keys"), t("help.camera_keys"));
        f.add(keyRows);
        f.add(Ui.button(t("cutscene.add_key"), "mdi2p-plus", () -> {
            CameraKey k = new CameraKey();
            List<CameraKey> sorted = CameraPath.sorted(e);
            if (!sorted.isEmpty()) {
                CameraKey last = sorted.get(sorted.size() - 1);
                k.time = last.time + 2;
                k.x = last.x + 4;
                k.y = last.y;
                k.z = last.z;
                k.yaw = last.yaw;
                k.pitch = last.pitch;
                k.lookAt = last.lookAt;
                k.tx = last.tx;
                k.ty = last.ty;
                k.tz = last.tz;
            }
            e.keys.add(k);
            rebuildKeys();
            f.changed();
        }));

        f.section(t("section.camera_events"), t("help.camera_events"));
        f.add(eventRows);
        f.add(Ui.button(t("cutscene.add_event"), "mdi2p-plus", () -> {
            CameraEvent ev = new CameraEvent();
            ev.time = Math.round(time.getValue() * 10) / 10.0;
            e.events.add(ev);
            rebuildEvents();
            f.changed();
        }));

        SystemEditors.logic(this, t("section.cutscene_end"), t("help.cutscene_end"), e.onEnd, () -> t("cutscene.start.end"));
        rebuildKeys();
        rebuildEvents();
    }

    private TextField num(double v, DoubleConsumer set, int cols) {
        TextField tf = Form.decimalField(v, x -> {
            set.accept(x);
            f.changed();
        });
        tf.setPrefColumnCount(cols);
        return tf;
    }

    private static Label small(String s) {
        Label l = Ui.muted(s);
        l.setMinWidth(javafx.scene.control.Control.USE_PREF_SIZE);
        return l;
    }

    private void rebuildKeys() {
        keyRows.getChildren().clear();
        List<CameraKey> keys = new ArrayList<>(e.keys);
        keys.sort((a, b) -> Double.compare(a.time, b.time));
        int n = 1;
        for (CameraKey k : keys) {
            Label no = Ui.label("#" + n++, "text-bold");
            no.setMinWidth(30);
            HBox row1 = new HBox(6, no, small(t("cutscene.time")), num(k.time, v -> k.time = Math.max(0, v), 4),
                    small("X"), num(k.x, v -> k.x = v, 5), small("Y"), num(k.y, v -> k.y = v, 5), small("Z"), num(k.z, v -> k.z = v, 5));
            ComboBox<String> easing = Ui.combo(CameraPath.EASINGS, x -> t("easing." + x));
            easing.setValue(k.easing);
            easing.valueProperty().addListener((o, a, b) -> {
                if (b != null) k.easing = b;
                f.changed();
            });
            row1.getChildren().addAll(small(t("cutscene.easing")), easing, Ui.iconButton("mdi2c-close", t("action.remove"), () -> {
                e.keys.remove(k);
                rebuildKeys();
                f.changed();
            }));
            row1.setAlignment(Pos.CENTER_LEFT);
            CheckBox look = new CheckBox(t("cutscene.look_at"));
            look.setSelected(k.lookAt);
            HBox rot = new HBox(6, small(t("cutscene.yaw")), num(k.yaw, v -> k.yaw = v, 5), small(t("cutscene.pitch")), num(k.pitch, v -> k.pitch = v, 5));
            HBox target = new HBox(6, small("X"), num(k.tx, v -> k.tx = v, 5), small("Y"), num(k.ty, v -> k.ty = v, 5), small("Z"), num(k.tz, v -> k.tz = v, 5));
            rot.setAlignment(Pos.CENTER_LEFT);
            target.setAlignment(Pos.CENTER_LEFT);
            rot.setVisible(!k.lookAt);
            rot.setManaged(!k.lookAt);
            target.setVisible(k.lookAt);
            target.setManaged(k.lookAt);
            look.selectedProperty().addListener((o, a, b) -> {
                k.lookAt = b;
                rot.setVisible(!b);
                rot.setManaged(!b);
                target.setVisible(b);
                target.setManaged(b);
                f.changed();
            });
            Label spacer = new Label();
            spacer.setMinWidth(30);
            HBox row2 = new HBox(10, spacer, look, rot, target);
            row2.setAlignment(Pos.CENTER_LEFT);
            VBox box = new VBox(6, row1, row2);
            box.getStyleClass().add("list-row");
            keyRows.getChildren().add(box);
        }
        if (keys.isEmpty()) keyRows.getChildren().add(Ui.muted(t("cutscene.no_keys")));
        draw();
    }

    private void rebuildEvents() {
        eventRows.getChildren().clear();
        List<CameraEvent> events = new ArrayList<>(e.events);
        events.sort((a, b) -> Double.compare(a.time, b.time));
        for (CameraEvent ev : events) {
            ComboBox<String> kind = Ui.combo(EVENT_KINDS, x -> t("camera_event." + x));
            kind.setValue(ev.kind);
            TextField text = new TextField(ev.text);
            text.setPromptText(t("camera_event.hint." + ev.kind));
            text.textProperty().addListener((o, a, b) -> {
                ev.text = b;
                f.changed();
            });
            HBox.setHgrow(text, Priority.ALWAYS);
            ComboBox<String> color = Ui.combo(List.of(Logic.COLORS), x -> t("choice." + x));
            color.setValue(ev.color);
            color.valueProperty().addListener((o, a, b) -> {
                if (b != null) ev.color = b;
                f.changed();
            });
            boolean colored = ev.kind.equals("title") || ev.kind.equals("subtitle") || ev.kind.equals("actionbar");
            color.setVisible(colored);
            color.setManaged(colored);
            kind.valueProperty().addListener((o, a, b) -> {
                if (b != null) ev.kind = b;
                f.changed();
                rebuildEvents();
            });
            HBox row = new HBox(6, small(t("cutscene.time")), num(ev.time, v -> ev.time = Math.max(0, v), 4), kind, text, color,
                    small(t("cutscene.duration")), num(ev.duration, v -> ev.duration = Math.max(0.05, v), 3),
                    Ui.iconButton("mdi2c-close", t("action.remove"), () -> {
                        e.events.remove(ev);
                        rebuildEvents();
                        f.changed();
                    }));
            row.setAlignment(Pos.CENTER_LEFT);
            eventRows.getChildren().add(row);
        }
        if (events.isEmpty()) eventRows.getChildren().add(Ui.muted(t("cutscene.no_events")));
    }

    @Override
    protected void refresh() {
        double len = Math.max(0.05, e.length());
        time.setMax(len);
        if (time.getValue() > len) time.setValue(len);
        draw();
    }

    private void togglePlay() {
        if (player != null) {
            player.stop();
            player = null;
            return;
        }
        playStart = System.nanoTime();
        playFrom = time.getValue() >= time.getMax() - 0.01 ? 0 : time.getValue();
        player = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double tt = playFrom + (now - playStart) / 1e9;
                if (tt >= time.getMax()) {
                    time.setValue(time.getMax());
                    stop();
                    player = null;
                    return;
                }
                time.setValue(tt);
            }
        };
        player.start();
    }

    /** Top view (X right, Z down) on the left and side view (X right, Y up) on the right. */
    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth(), h = canvas.getHeight();
        boolean dark = Ui.dark();
        g.setFill(dark ? Color.web("#161b22") : Color.web("#f6f8fa"));
        g.fillRect(0, 0, w, h);
        double len = e.length();
        timeLabel.setText(t("cutscene.time_value", String.format("%.2f", time.getValue()), String.format("%.2f", len)));
        List<CameraPath.Pose> path = new ArrayList<>();
        int samples = Math.max(2, (int) (len * 20));
        for (int i = 0; i <= samples; i++) path.add(CameraPath.at(e, len * i / samples));
        double minX = -4, maxX = 4, minZ = -4, maxZ = 4, minY = 0, maxY = 4;
        for (CameraPath.Pose p : path) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z());
            maxZ = Math.max(maxZ, p.z());
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
        }
        for (CameraKey k : e.keys) {
            if (!k.lookAt) continue;
            minX = Math.min(minX, k.tx);
            maxX = Math.max(maxX, k.tx);
            minZ = Math.min(minZ, k.tz);
            maxZ = Math.max(maxZ, k.tz);
            minY = Math.min(minY, k.ty);
            maxY = Math.max(maxY, k.ty);
        }
        double pw = (w - 30) / 2, ph = h - 30;
        view(g, 10, 10, pw, ph, path, minX, maxX, minZ, maxZ, false, dark);
        view(g, 20 + pw, 10, pw, ph, path, minX, maxX, minY, maxY, true, dark);
    }

    private void view(GraphicsContext g, double ox, double oy, double pw, double ph, List<CameraPath.Pose> path,
                      double minA, double maxA, double minB, double maxB, boolean side, boolean dark) {
        g.setStroke(dark ? Color.web("#30363d") : Color.web("#d0d7de"));
        g.setLineWidth(1);
        g.strokeRect(ox, oy, pw, ph);
        double span = Math.max(maxA - minA, maxB - minB) * 1.15 + 1;
        double scale = Math.min(pw, ph) / span;
        double ca = (minA + maxA) / 2, cb = (minB + maxB) / 2;
        java.util.function.DoubleUnaryOperator sx = a -> ox + pw / 2 + (a - ca) * scale;
        java.util.function.DoubleUnaryOperator sy = b -> side ? oy + ph / 2 - (b - cb) * scale : oy + ph / 2 + (b - cb) * scale;
        g.setFill(dark ? Color.web("#8b949e") : Color.web("#57606a"));
        g.fillText(t(side ? "cutscene.view_side" : "cutscene.view_top"), ox + 6, oy + 14);
        // origin (start position for relative cutscenes)
        g.setStroke(dark ? Color.web("#484f58") : Color.web("#afb8c1"));
        double zx = sx.applyAsDouble(0), zy = sy.applyAsDouble(0);
        g.strokeLine(zx - 6, zy, zx + 6, zy);
        g.strokeLine(zx, zy - 6, zx, zy + 6);
        // path
        g.setStroke(Color.web("#2f81f7"));
        g.setLineWidth(2);
        for (int i = 1; i < path.size(); i++) {
            CameraPath.Pose a = path.get(i - 1), b = path.get(i);
            g.strokeLine(sx.applyAsDouble(a.x()), sy.applyAsDouble(side ? a.y() : a.z()), sx.applyAsDouble(b.x()), sy.applyAsDouble(side ? b.y() : b.z()));
        }
        // keys and look targets
        int n = 1;
        for (CameraKey k : CameraPath.sorted(e)) {
            double kx = sx.applyAsDouble(k.x), ky = sy.applyAsDouble(side ? k.y : k.z);
            if (k.lookAt) {
                g.setStroke(Color.web("#d29922", 0.6));
                g.setLineWidth(1);
                g.strokeLine(kx, ky, sx.applyAsDouble(k.tx), sy.applyAsDouble(side ? k.ty : k.tz));
                g.setFill(Color.web("#d29922"));
                g.fillRect(sx.applyAsDouble(k.tx) - 3, sy.applyAsDouble(side ? k.ty : k.tz) - 3, 6, 6);
            }
            g.setFill(dark ? Color.web("#e6edf3") : Color.web("#1f2328"));
            g.fillOval(kx - 4, ky - 4, 8, 8);
            g.fillText(String.valueOf(n++), kx + 6, ky - 6);
        }
        // camera now
        CameraPath.Pose p = CameraPath.at(e, time.getValue());
        double px = sx.applyAsDouble(p.x()), py = sy.applyAsDouble(side ? p.y() : p.z());
        double yaw = Math.toRadians(p.yaw()), pitch = Math.toRadians(p.pitch());
        double dx = -Math.sin(yaw) * Math.cos(pitch), dz = Math.cos(yaw) * Math.cos(pitch), dy = -Math.sin(pitch);
        double ax = dx, ay = side ? -dy : dz;
        double al = Math.hypot(ax, ay);
        g.setStroke(Color.web("#cf222e"));
        g.setLineWidth(2);
        if (al > 0.05) g.strokeLine(px, py, px + ax / al * 22, py + ay / al * 22);
        g.setFill(Color.web("#cf222e"));
        g.fillOval(px - 5, py - 5, 10, 10);
    }
}
