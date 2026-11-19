package modstudio.ui.editors;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import modstudio.core.*;
import modstudio.core.Elements.Cube;
import modstudio.core.Elements.Display;
import modstudio.core.Elements.Face;
import modstudio.core.Elements.Model;
import modstudio.gen.J;
import modstudio.ui.Ui;
import modstudio.ui.controls.Form;
import modstudio.ui.controls.ModelView3D;
import modstudio.ui.controls.TextureField;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

/** Cuboid model editor with a live 3D view, per-face UV settings, Blockbench import and JSON export. */
final class ModelEditor extends EditorBase<Model> {
    private ModelView3D view3d;
    private final ListView<Cube> cubeList = new ListView<>();
    private final VBox cubeBox = new VBox(10);
    private final VBox faceBox = new VBox(6);
    private final VBox textureBox = new VBox(8);
    private final VBox displayBox = new VBox(8);
    private int sel = -1;
    private boolean syncing;
    private String displayContext = "gui";
    private RigPanel rig;
    private double snapStep = 0.25;

    /** Model names for pickers, with "" (none) first. */
    static java.util.List<String> modelChoices(Ctx c) {
        java.util.List<String> r = new java.util.ArrayList<>();
        r.add("");
        for (Model m : c.ws.project.all(Model.class)) r.add(m.name);
        return r;
    }

    ModelEditor(Ctx c, Model e) {
        super(c, e);
    }

    @Override
    protected void fill() {
        f.section(t("section.general"));
        nameRow();
        f.choice(t("field.model_kind"), List.of("block", "item", "entity"), x -> t("modelkind." + x), () -> e.kind, v -> e.kind = v, t("help.model_kind"));
        f.check(t("field.ambient_occlusion"), () -> e.ambientOcclusion, v -> e.ambientOcclusion = v);
        Spinner<Integer> tw = Form.intSpinner(8, 512, Math.max(8, e.textureWidth), v -> {
            e.textureWidth = v;
            f.changed();
        });
        Spinner<Integer> th = Form.intSpinner(8, 512, Math.max(8, e.textureHeight), v -> {
            e.textureHeight = v;
            f.changed();
        });
        tw.setPrefWidth(88);
        th.setPrefWidth(88);
        f.row(t("field.texture_size"), Ui.hbox(8, Ui.label("W", "text-muted"), tw, Ui.label("H", "text-muted"), th), t("help.texture_size"));

        ComboBox<String> template = Ui.combo(Models.TEMPLATES, x -> t("modeltpl." + x));
        template.setValue("block");
        Button apply = Ui.button(t("model.apply_template"), "mdi2s-shape-plus", () -> {
            if (!e.cubes.isEmpty() && !Ui.confirm(c.window(), t("model.apply_template"), t("model.apply_template_confirm"))) return;
            Models.applyTemplate(e, template.getValue());
            sel = e.cubes.isEmpty() ? -1 : 0;
            rig.rebuildAll();
            structureChanged();
        });
        Button imp = Ui.button(t("model.import"), "mdi2i-import", this::importModel);
        Button exp = Ui.button(t("model.export"), "mdi2e-export-variant", this::exportModel);
        f.row(t("field.template"), Ui.hbox(8, template, apply), t("help.model_template"));
        f.row(t("model.files"), Ui.hbox(8, imp, exp), t("help.model_import"));

        rig = new RigPanel(e, () -> view3d, () -> sel, f::changed, this::rebuildCube);
        f.section(t("section.model_view"), t("help.model_view"));
        f.add(workbench());

        f.section(t("section.cube"));
        f.add(cubeBox);
        f.section(t("section.faces"), t("help.faces"));
        f.add(faceBox);
        f.section(t("section.bones"), t("help.bones"));
        f.add(rig.bonesBox);
        f.section(t("section.animations"), t("help.animations"));
        f.add(rig.animBox);
        f.section(t("section.model_textures"), t("help.model_textures"));
        f.add(textureBox);
        f.section(t("section.display"), t("help.display"));
        f.add(displayBox);

        if (e.textures.isEmpty()) e.textures.put("0", "");
        sel = e.cubes.isEmpty() ? -1 : 0;
        reloadList();
        rebuildCube();
        rebuildTextures();
        rebuildDisplay();
        rig.rebuildAll();
    }

    private Node workbench() {
        view3d = new ModelView3D(c.ws, 560, 420, true);
        view3d.setOnSelect(i -> select(i));
        view3d.setOnTransform(committed -> {
            edited();
            if (committed) rebuildCube();
        });
        cubeList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Cube cu, boolean empty) {
                super.updateItem(cu, empty);
                if (empty || cu == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(cu.name);
                setGraphic(Ui.icon(cu.rotation != 0 ? "mdi2r-rotate-right" : "mdi2c-cube-outline"));
            }
        });
        cubeList.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
            if (syncing) return;
            select(b.intValue());
        });
        cubeList.setPrefWidth(200);
        Button add = Ui.iconButton("mdi2p-plus", t("model.add_cube"), () -> {
            Cube cu = Models.cube(uniqueCubeName("cube"), 4, 0, 4, 12, 8, 12);
            e.cubes.add(cu);
            sel = e.cubes.size() - 1;
            structureChanged();
        });
        Button dup = Ui.iconButton("mdi2c-content-copy", t("action.duplicate"), () -> {
            if (sel < 0) return;
            Cube cu = Models.copy(e.cubes.get(sel));
            cu.name = uniqueCubeName(cu.name);
            e.cubes.add(sel + 1, cu);
            sel++;
            structureChanged();
        });
        Button del = Ui.iconButton("mdi2d-delete-outline", t("action.delete"), () -> {
            if (sel < 0) return;
            e.cubes.remove(sel);
            sel = Math.min(sel, e.cubes.size() - 1);
            structureChanged();
        });
        Button up = Ui.iconButton("mdi2c-chevron-up", t("action.move_up"), () -> move(-1));
        Button down = Ui.iconButton("mdi2c-chevron-down", t("action.move_down"), () -> move(1));
        Button mirX = Ui.iconButton("mdi2f-flip-horizontal", t("model.mirror_x"), () -> mirrorSelected(0));
        Button mirZ = Ui.iconButton("mdi2f-flip-vertical", t("model.mirror_z"), () -> mirrorSelected(2));
        HBox listTools = Ui.hbox(2, add, dup, mirX, mirZ, del, Ui.spacer(), up, down);
        VBox left = new VBox(6, cubeList, listTools);
        VBox.setVgrow(cubeList, Priority.ALWAYS);

        ToggleGroup views = new ToggleGroup();
        HBox viewBar = Ui.hbox(4);
        for (String v : List.of("iso", "front", "side", "top")) {
            ToggleButton tb = new ToggleButton(t("view." + v));
            tb.setToggleGroup(views);
            tb.setOnAction(ev -> view3d.view(v));
            if (v.equals("iso")) tb.setSelected(true);
            viewBar.getChildren().add(tb);
        }
        ComboBox<Double> snapBox = new ComboBox<>();
        snapBox.getItems().setAll(1.0, 0.5, 0.25, 0.1);
        snapBox.setValue(snapStep);
        snapBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Double d) {
                return d == null ? "" : Form.fmt(d);
            }

            @Override
            public Double fromString(String s) {
                return null;
            }
        });
        snapBox.valueProperty().addListener((o, a, b) -> {
            if (b == null) return;
            snapStep = b;
            view3d.setSnapStep(b);
        });
        CheckBox grid = new CheckBox(t("model.grid"));
        grid.setSelected(true);
        grid.selectedProperty().addListener((o, a, b) -> view3d.setShowGrid(b));
        viewBar.getChildren().addAll(Ui.spacer(), Ui.label(t("model.snap"), "text-muted"), snapBox, grid);
        Label hint = Ui.help(t("model.mouse_help"));
        VBox center = new VBox(6, viewBar, view3d, rig.previewBar(), hint);
        VBox.setVgrow(view3d, Priority.ALWAYS);
        HBox.setHgrow(center, Priority.ALWAYS);
        HBox box = new HBox(12, left, center);
        box.setPrefHeight(500);
        box.setMinHeight(440);
        return box;
    }

    private String uniqueCubeName(String base) {
        String b = base.replaceAll("_\\d+$", "");
        List<String> names = e.cubes.stream().map(x -> x.name).toList();
        if (!names.contains(b)) return b;
        int i = 2;
        while (names.contains(b + "_" + i)) i++;
        return b + "_" + i;
    }

    private void move(int d) {
        int j = sel + d;
        if (sel < 0 || j < 0 || j >= e.cubes.size()) return;
        java.util.Collections.swap(e.cubes, sel, j);
        sel = j;
        structureChanged();
    }

    private void mirrorSelected(int axis) {
        if (sel < 0 || sel >= e.cubes.size()) return;
        Cube mirrored = Models.mirrorCube(e.cubes.get(sel), axis);
        mirrored.name = uniqueCubeName(mirrored.name);
        e.cubes.add(sel + 1, mirrored);
        sel++;
        structureChanged();
    }

    private void select(int i) {
        sel = i;
        syncing = true;
        if (i >= 0 && i < e.cubes.size()) cubeList.getSelectionModel().select(i);
        else cubeList.getSelectionModel().clearSelection();
        syncing = false;
        view3d.setSelected(sel);
        rebuildCube();
    }

    /** Cubes were added, removed or reordered. */
    private void structureChanged() {
        reloadList();
        rebuildCube();
        f.changed();
    }

    private void reloadList() {
        syncing = true;
        cubeList.getItems().setAll(e.cubes);
        if (sel >= 0 && sel < e.cubes.size()) cubeList.getSelectionModel().select(sel);
        syncing = false;
        if (view3d != null) {
            view3d.setModel(e);
            view3d.setSelected(sel);
        }
    }

    /** A value changed: redraw without rebuilding the property panels. */
    private void edited() {
        cubeList.refresh();
        f.changed();
    }

    @Override
    protected void refresh() {
        if (view3d == null) return;
        view3d.setModel(e);
        view3d.setSelected(sel);
        if (rig != null) rig.updatePose();
    }

    // ------------------------------------------------------------------ cube properties

    private TextField num(double v, DoubleConsumer set) {
        TextField tf = Form.decimalField(v, x -> {
            set.accept(x);
            edited();
        });
        tf.setPrefColumnCount(5);
        return tf;
    }

    private Node xyz(double[] arr, boolean allowOutside) {
        HBox box = Ui.hbox(6);
        String[] axes = {"X", "Y", "Z"};
        for (int i = 0; i < 3; i++) {
            int k = i;
            Label l = Ui.label(axes[i], "text-muted");
            TextField tf = num(arr[i], x -> arr[k] = allowOutside ? Models.clamp(x, -16, 32) : Models.clamp(x, 0, 16));
            box.getChildren().addAll(l, tf);
        }
        return box;
    }

    private void rebuildCube() {
        cubeBox.getChildren().clear();
        faceBox.getChildren().clear();
        if (sel < 0 || sel >= e.cubes.size()) {
            cubeBox.getChildren().add(Ui.help(t("model.no_selection")));
            faceBox.getChildren().add(Ui.help(t("model.no_selection")));
            return;
        }
        Cube cu = e.cubes.get(sel);
        GridPane g = grid();
        int r = 0;
        TextField name = new TextField(cu.name);
        name.textProperty().addListener((o, a, b) -> {
            cu.name = b.trim().isEmpty() ? "cube" : b.trim();
            edited();
        });
        g.addRow(r++, label(t("field.name")), name);
        if (!e.bones.isEmpty()) {
            List<String> boneNames = new ArrayList<>();
            boneNames.add("");
            for (var b : e.bones) boneNames.add(b.name);
            ComboBox<String> boneBox = Ui.combo(boneNames, x -> x.isEmpty() ? t("rig.no_bone") : x);
            boneBox.setValue(boneNames.contains(cu.bone) ? cu.bone : "");
            boneBox.valueProperty().addListener((o, a, b) -> {
                if (b == null) return;
                cu.bone = b;
                edited();
                rig.rebuildBones();
            });
            g.addRow(r++, label(t("rig.bone")), boneBox);
        }
        final boolean[] syncingBounds = {false};
        TextField[] fromFields = new TextField[3], toFields = new TextField[3], sizeFields = new TextField[3];
        HBox fromBox = Ui.hbox(6), toBox = Ui.hbox(6), sizeBox = Ui.hbox(6);
        String[] posAxes = {"X", "Y", "Z"}, dimAxes = {"W", "H", "D"};
        for (int i = 0; i < 3; i++) {
            int k = i;
            fromFields[i] = num(cu.from[i], x -> {
                if (syncingBounds[0]) return;
                syncingBounds[0] = true;
                cu.from[k] = Models.clamp(x, -16, 32);
                sizeFields[k].setText(Form.fmt(Math.round((cu.to[k] - cu.from[k]) * 1000.0) / 1000.0));
                syncingBounds[0] = false;
            });
            toFields[i] = num(cu.to[i], x -> {
                if (syncingBounds[0]) return;
                syncingBounds[0] = true;
                cu.to[k] = Models.clamp(x, -16, 32);
                sizeFields[k].setText(Form.fmt(Math.round((cu.to[k] - cu.from[k]) * 1000.0) / 1000.0));
                syncingBounds[0] = false;
            });
            sizeFields[i] = num(Math.round((cu.to[i] - cu.from[i]) * 1000.0) / 1000.0, x -> {
                if (syncingBounds[0]) return;
                syncingBounds[0] = true;
                cu.to[k] = Models.clamp(cu.from[k] + x, -16, 32);
                toFields[k].setText(Form.fmt(Math.round(cu.to[k] * 1000.0) / 1000.0));
                syncingBounds[0] = false;
            });
            fromBox.getChildren().addAll(Ui.label(posAxes[i], "text-muted"), fromFields[i]);
            toBox.getChildren().addAll(Ui.label(posAxes[i], "text-muted"), toFields[i]);
            sizeBox.getChildren().addAll(Ui.label(dimAxes[i], "text-muted"), sizeFields[i]);
        }
        g.addRow(r++, label(t("model.from")), fromBox);
        g.addRow(r++, label(t("model.to")), toBox);
        g.addRow(r++, label(t("model.size")), sizeBox);

        HBox nudge = Ui.hbox(4);
        for (String ax : List.of("x", "y", "z")) {
            int k = ax.equals("x") ? 0 : ax.equals("y") ? 1 : 2;
            nudge.getChildren().add(Ui.label(ax.toUpperCase(), "text-muted"));
            nudge.getChildren().add(Ui.iconButton("mdi2m-minus", t("model.move_minus", ax.toUpperCase()), () -> shift(cu, k, -snapStep)));
            nudge.getChildren().add(Ui.iconButton("mdi2p-plus", t("model.move_plus", ax.toUpperCase()), () -> shift(cu, k, snapStep)));
        }
        g.addRow(r++, label(t("model.move")), nudge);

        ComboBox<Double> angle = new ComboBox<>();
        angle.getItems().setAll(Models.ANGLES);
        angle.setValue(Models.snapAngle(cu.rotation));
        angle.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Double d) {
                return d == null ? "" : Form.fmt(d) + "°";
            }

            @Override
            public Double fromString(String s) {
                return null;
            }
        });
        angle.valueProperty().addListener((o, a, b) -> {
            if (b == null) return;
            cu.rotation = b;
            edited();
        });
        ComboBox<String> axis = Ui.combo(List.of("x", "y", "z"), x -> x.toUpperCase());
        axis.setValue(cu.axis);
        axis.valueProperty().addListener((o, a, b) -> {
            if (b == null) return;
            cu.axis = b;
            edited();
        });
        g.addRow(r++, label(t("model.rotation")), Ui.hbox(8, angle, Ui.label(t("model.axis"), "text-muted"), axis));
        g.addRow(r++, label(t("model.origin")), xyz(cu.origin, true));
        TextField inflate = num(cu.inflate, x -> cu.inflate = Models.clamp(x, -4, 4));
        g.addRow(r++, label(t("model.inflate")), Ui.hbox(8, inflate, Ui.help(t("help.model_inflate"))));
        CheckBox shade = new CheckBox(t("model.shade"));
        shade.setSelected(cu.shade);
        shade.selectedProperty().addListener((o, a, b) -> {
            cu.shade = b;
            edited();
        });
        CheckBox rescale = new CheckBox(t("model.rescale"));
        rescale.setSelected(cu.rescale);
        rescale.selectedProperty().addListener((o, a, b) -> {
            cu.rescale = b;
            edited();
        });
        g.addRow(r++, new Label(), Ui.hbox(16, shade, rescale));
        cubeBox.getChildren().add(g);

        // Faces
        GridPane fg = grid();
        fg.addRow(0, Ui.label(t("model.face"), "text-muted"), Ui.label(t("model.texture_var"), "text-muted"),
                Ui.label(t("model.uv"), "text-muted"), Ui.label(t("model.face_rotation"), "text-muted"), Ui.label(t("model.cullface"), "text-muted"));
        int fr = 1;
        List<String> keys = new ArrayList<>(e.textures.keySet());
        for (String dir : Cube.FACES) {
            Face fc = cu.face(dir);
            CheckBox on = new CheckBox(t("face." + dir));
            on.setSelected(fc.enabled);
            ComboBox<String> tex = Ui.combo(keys, k -> "#" + k);
            tex.setValue(keys.contains(fc.texture) ? fc.texture : keys.isEmpty() ? null : keys.get(0));
            tex.valueProperty().addListener((o, a, b) -> {
                if (b == null) return;
                fc.texture = b;
                edited();
            });
            CheckBox auto = new CheckBox(t("model.uv_auto"));
            auto.setSelected(fc.uv == null);
            HBox uvFields = Ui.hbox(4);
            Runnable fillUv = () -> {
                uvFields.getChildren().clear();
                double[] uv = Models.uv(cu, dir);
                for (int i = 0; i < 4; i++) {
                    int k = i;
                    TextField tf = num(uv[i], x -> {
                        if (fc.uv == null) fc.uv = Models.uv(cu, dir).clone();
                        fc.uv[k] = Models.clamp(x, 0, 16);
                    });
                    tf.setPrefColumnCount(3);
                    tf.setDisable(fc.uv == null);
                    uvFields.getChildren().add(tf);
                }
            };
            fillUv.run();
            auto.selectedProperty().addListener((o, a, b) -> {
                fc.uv = b ? null : Models.autoUv(cu, dir).clone();
                fillUv.run();
                edited();
            });
            ComboBox<Integer> rot = new ComboBox<>();
            rot.getItems().setAll(0, 90, 180, 270);
            rot.setValue(fc.rotation);
            rot.valueProperty().addListener((o, a, b) -> {
                if (b == null) return;
                fc.rotation = b;
                edited();
            });
            List<String> culls = new ArrayList<>(List.of(""));
            culls.addAll(Cube.FACES);
            ComboBox<String> cull = Ui.combo(culls, x -> x.isEmpty() ? t("model.cull_none") : t("face." + x));
            cull.setValue(fc.cullface == null ? "" : fc.cullface);
            cull.valueProperty().addListener((o, a, b) -> {
                if (b == null) return;
                fc.cullface = b;
                edited();
            });
            on.selectedProperty().addListener((o, a, b) -> {
                fc.enabled = b;
                tex.setDisable(!b);
                edited();
            });
            tex.setDisable(!fc.enabled);
            fg.addRow(fr++, on, tex, Ui.hbox(8, auto, uvFields), rot, cull);
        }
        faceBox.getChildren().add(fg);
    }

    private void shift(Cube cu, int axis, double d) {
        cu.from[axis] = Models.clamp(Math.round((cu.from[axis] + d) * 1000.0) / 1000.0, -16, 32);
        cu.to[axis] = Models.clamp(Math.round((cu.to[axis] + d) * 1000.0) / 1000.0, -16, 32);
        cu.origin[axis] = Models.clamp(Math.round((cu.origin[axis] + d) * 1000.0) / 1000.0, -16, 32);
        edited();
        rebuildCube();
    }

    private static GridPane grid() {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(8);
        return g;
    }

    private static Label label(String s) {
        Label l = Ui.label(s, "form-label");
        l.setMinWidth(150);
        return l;
    }

    // ------------------------------------------------------------------ textures

    private void rebuildTextures() {
        textureBox.getChildren().clear();
        for (String key : new ArrayList<>(e.textures.keySet())) {
            String value = e.textures.get(key);
            boolean project = c.ws.project.texture(value) != null;
            TextureField tf = new TextureField(c.ws, c.window(), project ? value : "", () -> e.name + (key.equals("0") ? "" : "_" + key),
                    e.kind.equals("item") ? "gem" : "stone", v -> {
                e.textures.put(key, v);
                edited();
            });
            TextField vanilla = new TextField(project ? "" : value);
            vanilla.setPromptText("minecraft:block/stone");
            vanilla.setPrefColumnCount(16);
            vanilla.textProperty().addListener((o, a, b) -> {
                if (!b.isBlank()) e.textures.put(key, b.trim());
                edited();
            });
            Button remove = Ui.iconButton("mdi2c-close", t("action.delete"), () -> {
                if (e.textures.size() <= 1) return;
                e.textures.remove(key);
                rebuildTextures();
                rebuildCube();
                edited();
            });
            remove.setDisable(e.textures.size() <= 1);
            Label k = Ui.label("#" + key, "lang-code");
            k.setMinWidth(60);
            textureBox.getChildren().add(Ui.hbox(8, k, tf, Ui.label(t("model.or_resource"), "text-muted"), vanilla, remove));
        }
        Button add = Ui.button(t("model.add_texture"), "mdi2p-plus", () -> {
            int i = 0;
            while (e.textures.containsKey(String.valueOf(i))) i++;
            e.textures.put(String.valueOf(i), "");
            rebuildTextures();
            rebuildCube();
            edited();
        });
        textureBox.getChildren().add(add);
    }

    // ------------------------------------------------------------------ display

    private void rebuildDisplay() {
        displayBox.getChildren().clear();
        ComboBox<String> ctxBox = Ui.combo(Models.DISPLAY_CONTEXTS, x -> t("display." + x));
        ctxBox.setValue(displayContext);
        ctxBox.valueProperty().addListener((o, a, b) -> {
            if (b == null) return;
            displayContext = b;
            rebuildDisplay();
        });
        Display d = e.display.get(displayContext);
        CheckBox custom = new CheckBox(t("model.display_custom"));
        custom.setSelected(d != null);
        custom.selectedProperty().addListener((o, a, b) -> {
            if (b) e.display.putIfAbsent(displayContext, new Display());
            else e.display.remove(displayContext);
            rebuildDisplay();
            edited();
        });
        displayBox.getChildren().add(Ui.hbox(12, ctxBox, custom));
        if (d == null) return;
        GridPane g = grid();
        g.addRow(0, label(t("model.display_rotation")), xyzFree(d.rotation));
        g.addRow(1, label(t("model.display_translation")), xyzFree(d.translation));
        g.addRow(2, label(t("model.display_scale")), xyzFree(d.scale));
        displayBox.getChildren().add(g);
    }

    private Node xyzFree(double[] arr) {
        HBox box = Ui.hbox(6);
        String[] axes = {"X", "Y", "Z"};
        for (int i = 0; i < 3; i++) {
            int k = i;
            box.getChildren().addAll(Ui.label(axes[i], "text-muted"), num(arr[i], x -> arr[k] = x));
        }
        return box;
    }

    // ------------------------------------------------------------------ files

    private void importModel() {
        FileChooser fc = new FileChooser();
        fc.setTitle(t("model.import"));
        fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(t("model.filter"), "*.json", "*.bbmodel"));
        File file = fc.showOpenDialog(c.window());
        if (file == null) return;
        try {
            Models.Imported im = Models.read(Files.readString(file.toPath(), StandardCharsets.UTF_8), file.getName());
            Model m = im.model();
            // Images embedded in a .bbmodel become project textures.
            for (var en : im.images().entrySet()) {
                int[] wh = new int[2];
                int[] px = Images.read(en.getValue(), wh);
                if (px == null) continue;
                String n = c.ws.project.uniqueTextureName(Ids.toId(im.imageNames().getOrDefault(en.getKey(), e.name)));
                TextureAsset ta = TextureAsset.of(n, px, wh[0], wh[1]);
                c.ws.project.textures.add(ta);
                m.textures.put(en.getKey(), ta.id);
            }
            e.cubes = m.cubes;
            e.textures = m.textures;
            e.display = m.display;
            e.ambientOcclusion = m.ambientOcclusion;
            e.kind = m.kind;
            e.textureWidth = m.textureWidth;
            e.textureHeight = m.textureHeight;
            e.bones = m.bones;
            e.animations = m.animations;
            sel = e.cubes.isEmpty() ? -1 : 0;
            rig.rebuildAll();
            rebuildTextures();
            rebuildDisplay();
            c.ws.structureChanged();
            structureChanged();
        } catch (Exception ex) {
            Ui.error(c.window(), t("model.import_failed"), ex);
        }
    }

    private void exportModel() {
        FileChooser fc = new FileChooser();
        fc.setTitle(t("model.export"));
        fc.setInitialFileName(e.name + ".json");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = fc.showSaveDialog(c.window());
        if (file == null) return;
        try {
            String ns = c.ns();
            var json = Models.toJava(e, v -> {
                TextureAsset ta = c.ws.project.texture(v);
                if (ta != null) return ns + ":" + (e.kind.equals("item") ? "item/" : "block/") + ta.name;
                return v == null || v.isBlank() ? null : v;
            });
            Files.writeString(file.toPath(), J.pretty(json), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            Ui.error(c.window(), t("model.export_failed"), ex);
        }
    }
}
