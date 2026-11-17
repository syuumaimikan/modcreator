package modstudio.ui.editors;

import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import modstudio.core.Elements.AnimKey;
import modstudio.core.Elements.AnimTrack;
import modstudio.core.Elements.Animation;
import modstudio.core.Elements.Bone;
import modstudio.core.Elements.Cube;
import modstudio.core.Elements.Model;
import modstudio.core.Ids;
import modstudio.core.Rig;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;
import modstudio.ui.controls.Form;
import modstudio.ui.controls.ModelView3D;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Bones and keyframe animations of a model: bone list, timeline, key editor, presets and playback in the 3D view. */
final class RigPanel {
    private static final double LABEL_W = 110, HEAD = 18, ROW = 18;

    private final Model e;
    private final Supplier<ModelView3D> view;
    private final IntSupplier selectedCube;
    private final Runnable changed;
    private final Runnable cubesChanged;

    final VBox bonesBox = new VBox(8);
    final VBox animBox = new VBox(10);
    final HBox bar = new HBox(8);

    private final ComboBox<String> barCombo = new ComboBox<>();
    private final Button playBtn;
    private final Slider slider = new Slider(0, 2, 0);
    private final Label timeLabel = Ui.label("", "text-muted");
    private final Canvas timeline = new Canvas(640, 120);
    private final VBox keyBox = new VBox(6);
    private String anim = "";
    private String bone = "";
    private String channel = "rotation";
    private double time;
    private AnimationTimer player;
    private boolean syncing;

    RigPanel(Model e, Supplier<ModelView3D> view, IntSupplier selectedCube, Runnable changed, Runnable cubesChanged) {
        this.e = e;
        this.view = view;
        this.selectedCube = selectedCube;
        this.changed = changed;
        this.cubesChanged = cubesChanged;
        playBtn = Ui.iconButton("mdi2p-play", t("cutscene.play"), this::togglePlay);
        barCombo.valueProperty().addListener((o, a, b) -> {
            if (syncing || b == null) return;
            anim = b;
            time = 0;
            rebuildAnimations();
            updatePose();
        });
        slider.setPrefWidth(260);
        slider.valueProperty().addListener((o, a, b) -> {
            if (syncing) return;
            stop();
            time = b.doubleValue();
            updatePose();
        });
        HBox.setHgrow(slider, Priority.ALWAYS);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getChildren().addAll(Ui.label(t("rig.preview"), "text-muted"), barCombo, playBtn, slider, timeLabel);
        timeline.setOnMouseClicked(ev -> clickTimeline(ev.getX(), ev.getY()));
        if (!e.bones.isEmpty()) bone = e.bones.get(0).name;
    }

    private static String t(String key, Object... args) {
        return I18n.t(key, args);
    }

    Animation current() {
        Animation a = e.animation(anim);
        if (a == null && !e.animations.isEmpty()) {
            a = e.animations.get(0);
            anim = a.name;
        }
        return a;
    }

    void rebuildAll() {
        rebuildBones();
        rebuildAnimations();
        updatePose();
    }

    /** Re-applies the pose for the current time (after any edit or a rebuild of the view). */
    void updatePose() {
        ModelView3D v = view.get();
        Animation a = current();
        if (a != null) time = Math.max(0, Math.min(time, a.length));
        if (v != null) {
            v.setSelectedBone(bone);
            v.setPose(a == null || e.bones.isEmpty() ? null : Rig.pose(e, a, time));
        }
        syncing = true;
        barCombo.getItems().setAll(e.animations.stream().map(x -> x.name).toList());
        barCombo.setValue(a == null ? null : a.name);
        slider.setMax(a == null ? 1 : Math.max(0.05, a.length));
        slider.setValue(time);
        syncing = false;
        boolean show = a != null && !e.bones.isEmpty();
        bar.setVisible(show);
        bar.setManaged(show);
        timeLabel.setText(t("cutscene.time_value", Form.fmt(Math.round(time * 100) / 100.0), a == null ? "0" : Form.fmt(a.length)));
        drawTimeline();
    }

    private void togglePlay() {
        if (player != null) {
            stop();
            return;
        }
        Animation a = current();
        if (a == null) return;
        if (time >= a.length - 1e-6) time = 0;
        playBtn.setGraphic(Ui.icon("mdi2p-pause"));
        long[] last = {0};
        player = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (bar.getScene() == null) {
                    stop();
                    return;
                }
                if (last[0] != 0) {
                    Animation cur = current();
                    if (cur == null) {
                        RigPanel.this.stop();
                        return;
                    }
                    time += (now - last[0]) / 1e9;
                    if (time > cur.length) {
                        if (cur.mode.equals("loop")) time = cur.length <= 0 ? 0 : time % cur.length;
                        else {
                            time = cur.length;
                            updatePose();
                            RigPanel.this.stop();
                            return;
                        }
                    }
                    updatePose();
                }
                last[0] = now;
            }
        };
        player.start();
    }

    private void stop() {
        if (player != null) player.stop();
        player = null;
        playBtn.setGraphic(Ui.icon("mdi2p-play"));
    }

    // ------------------------------------------------------------------ bones

    void rebuildBones() {
        bonesBox.getChildren().clear();
        Button add = Ui.button(t("rig.add_bone"), "mdi2p-plus", () -> {
            Bone b = new Bone();
            b.name = uniqueBone("bone");
            e.bones.add(b);
            bone = b.name;
            structure();
        });
        Button fromCube = Ui.button(t("rig.bone_from_cube"), "mdi2c-cube-outline", this::boneFromCube);
        bonesBox.getChildren().add(Ui.hbox(8, add, fromCube));
        if (e.bones.isEmpty()) {
            bonesBox.getChildren().add(Ui.help(t("rig.no_bones")));
            return;
        }
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        g.addRow(0, Ui.label(t("field.name"), "text-muted"), Ui.label(t("rig.parent"), "text-muted"),
                Ui.label(t("rig.pivot"), "text-muted"), Ui.label(t("rig.cubes"), "text-muted"));
        int r = 1;
        for (Bone b : e.bones) {
            TextField name = new TextField(b.name);
            name.setPrefColumnCount(9);
            Runnable commit = () -> rename(b, name);
            name.setOnAction(ev -> commit.run());
            name.focusedProperty().addListener((o, a, f) -> {
                if (!f) commit.run();
            });
            List<String> parents = new ArrayList<>();
            parents.add("");
            for (Bone o : e.bones) if (o != b) parents.add(o.name);
            ComboBox<String> parent = Ui.combo(parents, x -> x.isEmpty() ? t("rig.no_parent") : x);
            parent.setValue(parents.contains(b.parent) ? b.parent : "");
            parent.valueProperty().addListener((o, a, v) -> {
                if (v == null) return;
                b.parent = v;
                changed.run();
            });
            HBox pivot = Ui.hbox(4);
            for (int i = 0; i < 3; i++) {
                int k = i;
                TextField tf = Form.decimalField(b.pivot[i], x -> {
                    b.pivot[k] = x;
                    changed.run();
                });
                tf.setPrefColumnCount(4);
                pivot.getChildren().addAll(Ui.label(new String[]{"X", "Y", "Z"}[i], "text-muted"), tf);
            }
            long count = e.cubes.stream().filter(cu -> b.name.equals(cu.bone)).count();
            Label cubes = Ui.label(String.valueOf(count), "text-muted");
            ToggleButton pick = new ToggleButton(t("rig.select"));
            pick.setSelected(b.name.equals(bone));
            pick.setOnAction(ev -> {
                bone = b.name;
                rebuildBones();
                rebuildKeys();
                updatePose();
            });
            Button del = Ui.iconButton("mdi2d-delete-outline", t("action.delete"), () -> deleteBone(b));
            g.addRow(r++, name, parent, pivot, cubes, Ui.hbox(4, pick, del));
        }
        bonesBox.getChildren().add(g);
    }

    private String uniqueBone(String base) {
        String b = Ids.toId(base);
        if (b.isEmpty()) b = "bone";
        if (e.bone(b) == null && !b.equals(Rig.ROOT)) return b;
        int i = 2;
        while (e.bone(b + "_" + i) != null) i++;
        return b + "_" + i;
    }

    private void boneFromCube() {
        int i = selectedCube.getAsInt();
        if (i < 0 || i >= e.cubes.size()) return;
        Cube cu = e.cubes.get(i);
        Bone b = new Bone();
        b.name = uniqueBone(cu.name);
        b.pivot = new double[]{(cu.from[0] + cu.to[0]) / 2, (cu.from[1] + cu.to[1]) / 2, (cu.from[2] + cu.to[2]) / 2};
        e.bones.add(b);
        cu.bone = b.name;
        bone = b.name;
        structure();
    }

    private void rename(Bone b, TextField field) {
        String n = Ids.toId(field.getText());
        if (n.equals(b.name)) return;
        if (n.isEmpty() || n.equals(Rig.ROOT) || e.bone(n) != null) {
            field.setText(b.name);
            return;
        }
        String old = b.name;
        b.name = n;
        for (Cube cu : e.cubes) if (old.equals(cu.bone)) cu.bone = n;
        for (Bone o : e.bones) if (old.equals(o.parent)) o.parent = n;
        for (Animation a : e.animations) for (AnimTrack tr : a.tracks) if (old.equals(tr.bone)) tr.bone = n;
        if (bone.equals(old)) bone = n;
        structure();
    }

    private void deleteBone(Bone b) {
        e.bones.remove(b);
        for (Cube cu : e.cubes) if (b.name.equals(cu.bone)) cu.bone = "";
        for (Bone o : e.bones) if (b.name.equals(o.parent)) o.parent = b.parent;
        for (Animation a : e.animations) a.tracks.removeIf(tr -> tr.bone.equals(b.name));
        if (bone.equals(b.name)) bone = e.bones.isEmpty() ? "" : e.bones.get(0).name;
        structure();
    }

    /** Bones or animations were added, removed or renamed. */
    private void structure() {
        rebuildBones();
        rebuildAnimations();
        cubesChanged.run();
        changed.run();
    }

    // ------------------------------------------------------------------ animations

    void rebuildAnimations() {
        animBox.getChildren().clear();
        Animation a = current();
        ComboBox<String> pickAnim = Ui.combo(e.animations.stream().map(x -> x.name).toList(), x -> x);
        pickAnim.setValue(a == null ? null : a.name);
        pickAnim.valueProperty().addListener((o, x, v) -> {
            if (v == null || v.equals(anim)) return;
            anim = v;
            time = 0;
            rebuildAnimations();
            updatePose();
        });
        Button add = Ui.button(t("rig.add_animation"), "mdi2p-plus", () -> {
            Animation n = new Animation();
            n.name = uniqueAnim(e.animations.isEmpty() ? "idle" : "animation");
            e.animations.add(n);
            anim = n.name;
            time = 0;
            structure();
        });
        Button dup = Ui.iconButton("mdi2c-content-copy", t("action.duplicate"), () -> {
            Animation cur = current();
            if (cur == null) return;
            Animation n = copy(cur);
            n.name = uniqueAnim(cur.name);
            e.animations.add(n);
            anim = n.name;
            structure();
        });
        Button del = Ui.iconButton("mdi2d-delete-outline", t("action.delete"), () -> {
            Animation cur = current();
            if (cur == null) return;
            e.animations.remove(cur);
            anim = "";
            structure();
        });
        animBox.getChildren().add(Ui.hbox(8, pickAnim, add, dup, del));
        if (e.bones.isEmpty()) {
            animBox.getChildren().add(Ui.help(t("rig.need_bones")));
        }
        if (a == null) {
            animBox.getChildren().add(Ui.help(t("rig.no_animations")));
            return;
        }

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(8);
        TextField name = new TextField(a.name);
        name.setPrefColumnCount(12);
        Runnable commit = () -> {
            String n = Ids.toId(name.getText());
            if (n.equals(a.name)) return;
            if (n.isEmpty() || e.animation(n) != null) {
                name.setText(a.name);
                return;
            }
            a.name = n;
            anim = n;
            structure();
        };
        name.setOnAction(ev -> commit.run());
        name.focusedProperty().addListener((o, x, f) -> {
            if (!f) commit.run();
        });
        TextField length = Form.decimalField(a.length, x -> {
            a.length = Math.max(0.1, Math.min(600, x));
            changed.run();
        });
        length.setPrefColumnCount(5);
        ComboBox<String> mode = Ui.combo(Rig.MODES, x -> t("animmode." + x));
        mode.setValue(Rig.MODES.contains(a.mode) ? a.mode : "loop");
        mode.valueProperty().addListener((o, x, v) -> {
            if (v == null) return;
            a.mode = v;
            changed.run();
        });
        g.addRow(0, label(t("field.name")), name);
        g.addRow(1, label(t("rig.length")), Ui.hbox(6, length, Ui.label(t("rig.seconds"), "text-muted")));
        g.addRow(2, label(t("rig.mode")), mode);
        animBox.getChildren().add(g);
        animBox.getChildren().add(Ui.label(t("rig.timeline"), "text-muted"));
        animBox.getChildren().add(timeline);
        animBox.getChildren().add(Ui.help(t("rig.timeline_help")));
        animBox.getChildren().add(keyBox);
        rebuildKeys();
        drawTimeline();
    }

    private static Label label(String s) {
        Label l = Ui.label(s, "form-label");
        l.setMinWidth(150);
        return l;
    }

    private String uniqueAnim(String base) {
        String b = Ids.toId(base).replaceAll("_\\d+$", "");
        if (b.isEmpty()) b = "animation";
        if (e.animation(b) == null) return b;
        int i = 2;
        while (e.animation(b + "_" + i) != null) i++;
        return b + "_" + i;
    }

    static Animation copy(Animation a) {
        Animation n = new Animation();
        n.name = a.name;
        n.length = a.length;
        n.mode = a.mode;
        for (AnimTrack tr : a.tracks) {
            AnimTrack c = new AnimTrack();
            c.bone = tr.bone;
            c.channel = tr.channel;
            for (AnimKey k : tr.keys) {
                AnimKey ck = new AnimKey();
                ck.time = k.time;
                ck.x = k.x;
                ck.y = k.y;
                ck.z = k.z;
                ck.easing = k.easing;
                c.keys.add(ck);
            }
            n.tracks.add(c);
        }
        return n;
    }

    private AnimTrack track(Animation a, String b, String ch, boolean create) {
        AnimTrack tr = a.track(b, ch);
        if (tr == null && create) {
            tr = new AnimTrack();
            tr.bone = b;
            tr.channel = ch;
            a.tracks.add(tr);
        }
        return tr;
    }

    private static double[] neutral(String ch) {
        return ch.equals("scale") ? new double[]{1, 1, 1} : new double[]{0, 0, 0};
    }

    /** Track selection, presets and the keys of the selected bone and channel. */
    private void rebuildKeys() {
        keyBox.getChildren().clear();
        Animation a = current();
        if (a == null || e.bones.isEmpty()) return;
        if (e.bone(bone) == null) bone = e.bones.get(0).name;
        ComboBox<String> bonePick = Ui.combo(e.bones.stream().map(b -> b.name).toList(), x -> x);
        bonePick.setValue(bone);
        bonePick.valueProperty().addListener((o, x, v) -> {
            if (v == null) return;
            bone = v;
            rebuildKeys();
            updatePose();
        });
        ToggleGroup tg = new ToggleGroup();
        HBox channels = Ui.hbox(4);
        for (String ch : Rig.CHANNELS) {
            ToggleButton tb = new ToggleButton(t("channel." + ch));
            tb.setToggleGroup(tg);
            tb.setSelected(ch.equals(channel));
            tb.setOnAction(ev -> {
                channel = ch;
                rebuildKeys();
            });
            channels.getChildren().add(tb);
        }
        Button addKey = Ui.button(t("rig.add_key"), "mdi2k-key-plus", () -> {
            AnimTrack tr = track(a, bone, channel, true);
            double[] v = Rig.sample(tr, time, neutral(channel));
            AnimKey k = new AnimKey();
            k.time = Math.round(time * 20) / 20.0;
            tr.keys.removeIf(o -> Math.abs(o.time - k.time) < 0.025);
            k.x = v[0];
            k.y = v[1];
            k.z = v[2];
            k.easing = "smooth";
            tr.keys.add(k);
            tr.keys.sort((p, q) -> Double.compare(p.time, q.time));
            changed.run();
            rebuildKeys();
        });
        ComboBox<String> preset = Ui.combo(Rig.PRESETS, x -> t("animpreset." + x));
        preset.setValue("swing");
        Button apply = Ui.button(t("rig.apply_preset"), "mdi2a-auto-fix", () -> {
            Rig.applyPreset(a, bone, preset.getValue());
            changed.run();
            rebuildKeys();
        });
        keyBox.getChildren().add(Ui.hbox(8, Ui.label(t("rig.bone"), "text-muted"), bonePick, channels, addKey));
        keyBox.getChildren().add(Ui.hbox(8, Ui.label(t("rig.preset"), "text-muted"), preset, apply));

        AnimTrack tr = track(a, bone, channel, false);
        if (tr == null || tr.keys.isEmpty()) {
            keyBox.getChildren().add(Ui.help(t("rig.no_keys")));
            return;
        }
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        g.addRow(0, Ui.label(t("cutscene.time"), "text-muted"), Ui.label("X", "text-muted"), Ui.label("Y", "text-muted"),
                Ui.label("Z", "text-muted"), Ui.label(t("cutscene.easing"), "text-muted"));
        int r = 1;
        for (AnimKey k : Rig.sorted(tr)) {
            TextField tt = field(k.time, v -> k.time = Math.max(0, v));
            TextField x = field(k.x, v -> k.x = v), y = field(k.y, v -> k.y = v), z = field(k.z, v -> k.z = v);
            ComboBox<String> ease = Ui.combo(Rig.EASINGS, s -> t("easing." + s));
            ease.setValue(Rig.EASINGS.contains(k.easing) ? k.easing : "linear");
            ease.valueProperty().addListener((o, p, v) -> {
                if (v == null) return;
                k.easing = v;
                changed.run();
            });
            Button go = Ui.iconButton("mdi2t-target", t("rig.go_to_key"), () -> {
                stop();
                time = k.time;
                updatePose();
            });
            Button del = Ui.iconButton("mdi2d-delete-outline", t("action.delete"), () -> {
                tr.keys.remove(k);
                if (tr.keys.isEmpty()) a.tracks.remove(tr);
                changed.run();
                rebuildKeys();
            });
            g.addRow(r++, tt, x, y, z, ease, Ui.hbox(4, go, del));
        }
        keyBox.getChildren().add(g);
    }

    private TextField field(double v, java.util.function.DoubleConsumer set) {
        TextField tf = Form.decimalField(v, x -> {
            set.accept(x);
            changed.run();
        });
        tf.setPrefColumnCount(5);
        return tf;
    }

    // ------------------------------------------------------------------ timeline

    private void drawTimeline() {
        Animation a = current();
        int rows = Math.max(1, e.bones.size());
        double h = HEAD + rows * ROW + 4;
        if (timeline.getHeight() != h) timeline.setHeight(h);
        GraphicsContext g = timeline.getGraphicsContext2D();
        boolean dark = Ui.dark();
        double w = timeline.getWidth();
        g.setFill(dark ? Color.web("#161b22") : Color.web("#f6f8fa"));
        g.fillRect(0, 0, w, h);
        if (a == null) return;
        double len = Math.max(0.05, a.length);
        double x0 = LABEL_W, span = w - LABEL_W - 8;
        Color text = dark ? Color.web("#c9d1d9") : Color.web("#24292f");
        Color grid = dark ? Color.web("#30363d") : Color.web("#d0d7de");
        g.setFont(javafx.scene.text.Font.font(11));
        // seconds
        double stepS = len <= 2 ? 0.25 : len <= 6 ? 0.5 : len <= 20 ? 1 : 5;
        for (double s = 0; s <= len + 1e-9; s += stepS) {
            double x = x0 + span * s / len;
            g.setStroke(grid);
            g.strokeLine(x, HEAD - 4, x, h);
            g.setFill(text);
            g.fillText(Form.fmt(Math.round(s * 100) / 100.0), x + 2, 12);
        }
        for (int i = 0; i < e.bones.size(); i++) {
            Bone b = e.bones.get(i);
            double y = HEAD + i * ROW;
            if (b.name.equals(bone)) {
                g.setFill(dark ? Color.web("#1f6feb33") : Color.web("#0969da22"));
                g.fillRect(0, y, w, ROW);
            }
            g.setFill(text);
            g.fillText(b.name, 6, y + 13);
            for (String ch : Rig.CHANNELS) {
                AnimTrack tr = a.track(b.name, ch);
                if (tr == null) continue;
                Color c = switch (ch) {
                    case "position" -> Color.web("#2da44e");
                    case "scale" -> Color.web("#d29922");
                    default -> Color.web("#2f81f7");
                };
                double dy = switch (ch) {
                    case "position" -> 0;
                    case "scale" -> 4;
                    default -> -4;
                };
                for (AnimKey k : tr.keys) {
                    double x = x0 + span * Math.min(len, k.time) / len;
                    double cy = y + ROW / 2 + dy;
                    g.setFill(c);
                    g.fillPolygon(new double[]{x, x + 4, x, x - 4}, new double[]{cy - 4, cy, cy + 4, cy}, 4);
                }
            }
        }
        double px = x0 + span * Math.min(len, time) / len;
        g.setStroke(Color.web("#cf222e"));
        g.setLineWidth(1.5);
        g.strokeLine(px, 0, px, h);
        g.setLineWidth(1);
    }

    private void clickTimeline(double x, double y) {
        Animation a = current();
        if (a == null) return;
        int row = (int) ((y - HEAD) / ROW);
        if (y >= HEAD && row >= 0 && row < e.bones.size()) bone = e.bones.get(row).name;
        if (x >= LABEL_W) {
            double span = timeline.getWidth() - LABEL_W - 8;
            stop();
            time = Math.round(Math.max(0, Math.min(1, (x - LABEL_W) / span)) * a.length * 20) / 20.0;
        }
        rebuildKeys();
        updatePose();
    }

    Node previewBar() {
        return bar;
    }
}
