package modstudio.ui.controls;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import modstudio.core.Ids;
import modstudio.core.Systems.Volume;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;

import java.util.Map;

/**
 * Layer-by-layer block editor for structures and multiblock patterns. The left grid edits one Y layer
 * (left click paints the selected palette block, right click clears), the right view shows the whole volume.
 */
public final class VoxelEditor extends VBox {
    private static final double CELL = 22;

    private final Volume v;
    private final Runnable onChange;
    private final IdField.Env env;
    private final Canvas grid = new Canvas();
    private final Canvas iso = new Canvas(360, 320);
    private final ListView<Integer> palette = new ListView<>();
    private final Slider layer = new Slider();
    private final Label layerLabel = new Label();
    private final Label info = Ui.muted("");
    private int brush = 0;
    private final String keepLabel;

    public VoxelEditor(IdField.Env env, Volume v, Runnable onChange, String keepLabel) {
        super(10);
        this.env = env;
        this.v = v;
        this.onChange = onChange;
        this.keepLabel = keepLabel;
        v.ensure();
        getStyleClass().add("voxel-editor");

        Spinner<Integer> sx = Form.intSpinner(1, 48, v.sx, n -> resize(n, v.sy, v.sz));
        Spinner<Integer> sy = Form.intSpinner(1, 48, v.sy, n -> resize(v.sx, n, v.sz));
        Spinner<Integer> sz = Form.intSpinner(1, 48, v.sz, n -> resize(v.sx, v.sy, n));
        for (Spinner<Integer> s : java.util.List.of(sx, sy, sz)) s.setPrefWidth(80);
        HBox size = Ui.hbox(8, Ui.muted(I18n.t("voxel.size")), Ui.muted("X"), sx, Ui.muted("Y"), sy, Ui.muted("Z"), sz);
        size.setAlignment(Pos.CENTER_LEFT);

        layer.setMin(0);
        layer.setMajorTickUnit(1);
        layer.setMinorTickCount(0);
        layer.setSnapToTicks(true);
        layer.setBlockIncrement(1);
        layer.valueProperty().addListener((o, a, b) -> redraw());
        Button fill = Ui.button(I18n.t("voxel.fill_layer"), "mdi2f-format-color-fill", () -> fillLayer(brush));
        Button clear = Ui.button(I18n.t("voxel.clear_layer"), "mdi2e-eraser", () -> fillLayer(-1));
        Button copy = Ui.button(I18n.t("voxel.copy_below"), "mdi2c-content-copy", this::copyBelow);
        HBox layerRow = Ui.hbox(8, layerLabel, layer, fill, clear, copy);
        layerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(layer, Priority.ALWAYS);

        palette.setPrefHeight(230);
        palette.setPrefWidth(250);
        palette.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                javafx.scene.shape.Rectangle sw = new javafx.scene.shape.Rectangle(14, 14, item < 0 ? Color.TRANSPARENT : color(v.palette.get(item)));
                sw.setStroke(Color.GRAY);
                setGraphic(sw);
                setText(item < 0 ? keepLabel : v.palette.get(item));
            }
        });
        palette.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null) brush = b;
        });
        IdField newBlock = new IdField(env, IdField.Kind.BLOCK, "minecraft:stone", s -> {
        });
        Button add = Ui.button(I18n.t("voxel.add_block"), "mdi2p-plus", () -> {
            String id = newBlock.getValue().trim();
            if (id.isEmpty()) return;
            if (!v.palette.contains(id)) v.palette.add(id);
            refreshPalette();
            palette.getSelectionModel().select(Integer.valueOf(v.palette.indexOf(id)));
            onChange.run();
        });
        Button remove = Ui.iconButton("mdi2d-delete-outline", I18n.t("voxel.remove_block"), this::removePaletteEntry);
        VBox paletteBox = new VBox(6, Ui.label(I18n.t("voxel.palette"), "param-label"), palette, newBlock, Ui.hbox(6, add, remove));
        paletteBox.setPrefWidth(260);

        grid.setOnMousePressed(this::paint);
        grid.setOnMouseDragged(this::paint);
        VBox gridBox = new VBox(6, Ui.label(I18n.t("voxel.layer_view"), "param-label"), new ScrollPane(grid));
        ((ScrollPane) gridBox.getChildren().get(1)).setPrefViewportHeight(300);
        ((ScrollPane) gridBox.getChildren().get(1)).setPrefViewportWidth(320);
        VBox isoBox = new VBox(6, Ui.label(I18n.t("voxel.view_3d"), "param-label"), iso);
        HBox main = new HBox(14, paletteBox, gridBox, isoBox);
        getChildren().addAll(size, layerRow, main, info, Ui.help(I18n.t("voxel.help")));
        setPadding(new Insets(4, 0, 0, 0));
        refreshPalette();
        palette.getSelectionModel().select(Integer.valueOf(Math.min(0, v.palette.size() - 1)));
        updateLayerRange();
        redraw();
    }

    /** Re-reads the volume after it was replaced (for example by an import). */
    public void reload() {
        v.ensure();
        refreshPalette();
        updateLayerRange();
        redraw();
    }

    private void refreshPalette() {
        palette.getItems().clear();
        palette.getItems().add(-1);
        for (int i = 0; i < v.palette.size(); i++) palette.getItems().add(i);
    }

    private void removePaletteEntry() {
        Integer sel = palette.getSelectionModel().getSelectedItem();
        if (sel == null || sel < 0) return;
        v.palette.remove((int) sel);
        for (int i = 0; i < v.cells.length; i++) {
            if (v.cells[i] == sel) v.cells[i] = -1;
            else if (v.cells[i] > sel) v.cells[i]--;
        }
        brush = -1;
        refreshPalette();
        changed();
    }

    private void resize(int x, int y, int z) {
        v.resize(x, y, z);
        updateLayerRange();
        changed();
    }

    private void updateLayerRange() {
        layer.setMax(Math.max(0, v.sy - 1));
        if (layer.getValue() > v.sy - 1) layer.setValue(v.sy - 1);
    }

    private int y() {
        return (int) Math.round(layer.getValue());
    }

    private void fillLayer(int value) {
        int y = y();
        for (int z = 0; z < v.sz; z++) for (int x = 0; x < v.sx; x++) v.set(x, y, z, value);
        changed();
    }

    private void copyBelow() {
        int y = y();
        if (y == 0) return;
        for (int z = 0; z < v.sz; z++) for (int x = 0; x < v.sx; x++) v.set(x, y, z, v.get(x, y - 1, z));
        changed();
    }

    private void paint(MouseEvent e) {
        int x = (int) (e.getX() / CELL), z = (int) (e.getY() / CELL);
        if (x < 0 || z < 0 || x >= v.sx || z >= v.sz) return;
        int value = e.getButton() == MouseButton.SECONDARY ? -1 : brush;
        if (v.get(x, y(), z) == value) return;
        v.set(x, y(), z, value);
        changed();
    }

    private void changed() {
        onChange.run();
        redraw();
    }

    public void redraw() {
        layerLabel.setText(I18n.t("voxel.layer", y(), v.sy - 1));
        info.setText(I18n.t("voxel.info", v.sx, v.sy, v.sz, v.count()));
        drawGrid();
        drawIso();
    }

    private void drawGrid() {
        grid.setWidth(v.sx * CELL + 1);
        grid.setHeight(v.sz * CELL + 1);
        GraphicsContext g = grid.getGraphicsContext2D();
        boolean dark = Ui.dark();
        g.setFill(dark ? Color.web("#0d1117") : Color.web("#ffffff"));
        g.fillRect(0, 0, grid.getWidth(), grid.getHeight());
        int y = y();
        for (int z = 0; z < v.sz; z++)
            for (int x = 0; x < v.sx; x++) {
                int idx = v.get(x, y, z);
                if (idx >= 0 && idx < v.palette.size()) {
                    g.setFill(color(v.palette.get(idx)));
                    g.fillRect(x * CELL + 1, z * CELL + 1, CELL - 1, CELL - 1);
                } else if (y > 0 && v.get(x, y - 1, z) >= 0) {
                    g.setFill(dark ? Color.web("#21262d") : Color.web("#eaeef2"));
                    g.fillRect(x * CELL + 1, z * CELL + 1, CELL - 1, CELL - 1);
                }
            }
        g.setStroke(dark ? Color.web("#30363d") : Color.web("#d0d7de"));
        for (int x = 0; x <= v.sx; x++) g.strokeLine(x * CELL + 0.5, 0, x * CELL + 0.5, v.sz * CELL);
        for (int z = 0; z <= v.sz; z++) g.strokeLine(0, z * CELL + 0.5, v.sx * CELL, z * CELL + 0.5);
    }

    private void drawIso() {
        GraphicsContext g = iso.getGraphicsContext2D();
        double w = iso.getWidth(), h = iso.getHeight();
        boolean dark = Ui.dark();
        g.setFill(dark ? Color.web("#161b22") : Color.web("#f6f8fa"));
        g.fillRect(0, 0, w, h);
        double span = v.sx + v.sz;
        double s = Math.min((w - 20) / (span * 0.866), (h - 20) / (span * 0.5 + v.sy));
        s = Math.max(2, Math.min(24, s));
        double ox = w / 2 + (v.sx - v.sz) * 0.866 * s / 2 - (v.sx - v.sz) * 0.866 * s / 2;
        double oy = h - 10 - (v.sx + v.sz) * 0.5 * s;
        int cur = y();
        for (int y = 0; y < v.sy; y++)
            for (int z = v.sz - 1; z >= 0; z--)
                for (int x = v.sx - 1; x >= 0; x--) {
                    int idx = v.get(x, y, z);
                    if (idx < 0 || idx >= v.palette.size()) continue;
                    String id = v.palette.get(idx);
                    if (Ids.full(id).equals("minecraft:air")) continue;
                    Color c = color(id);
                    if (y > cur) c = c.deriveColor(0, 0.4, 1, 0.35);
                    double px = ox + (x - z) * 0.866 * s;
                    double py = oy + (x + z) * 0.5 * s - y * s;
                    cube(g, px, py, s, c);
                }
    }

    private static void cube(GraphicsContext g, double x, double y, double s, Color c) {
        double dx = 0.866 * s, dy = 0.5 * s;
        g.setFill(c.brighter());
        g.fillPolygon(new double[]{x, x + dx, x, x - dx}, new double[]{y - dy, y, y + dy, y}, 4);
        g.setFill(c);
        g.fillPolygon(new double[]{x - dx, x, x, x - dx}, new double[]{y, y + dy, y + dy + s, y + s}, 4);
        g.setFill(c.darker());
        g.fillPolygon(new double[]{x, x + dx, x + dx, x}, new double[]{y + dy, y, y + s, y + dy + s}, 4);
    }

    private static final Map<String, String> COLORS = Map.ofEntries(
            Map.entry("stone", "#7d7d7d"), Map.entry("cobblestone", "#6f6f6f"), Map.entry("stone_bricks", "#7a7a7a"),
            Map.entry("oak_planks", "#a2834f"), Map.entry("spruce_planks", "#735531"), Map.entry("glass", "#c6e2ee"),
            Map.entry("dirt", "#866043"), Map.entry("grass_block", "#5f9f3b"), Map.entry("sand", "#dbd3a0"),
            Map.entry("gold_block", "#f5d33a"), Map.entry("iron_block", "#d8d8d8"), Map.entry("diamond_block", "#62ded6"),
            Map.entry("amethyst_block", "#8561c2"), Map.entry("lodestone", "#93959a"), Map.entry("water", "#3f76e4"),
            Map.entry("lava", "#e46b19"), Map.entry("obsidian", "#1b1428"), Map.entry("bricks", "#966253"),
            Map.entry("oak_log", "#6b5430"), Map.entry("deepslate_bricks", "#4a4a4f"), Map.entry("chest", "#a9772f"),
            Map.entry("torch", "#ffd25c"), Map.entry("lantern", "#e2a74a"), Map.entry("air", "#ffffff"),
            Map.entry("quartz_block", "#ebe5de"), Map.entry("chiseled_quartz_block", "#e7e2da"), Map.entry("quartz_pillar", "#ece6df"),
            Map.entry("smooth_quartz", "#ece6df"), Map.entry("sea_lantern", "#acc8be"), Map.entry("enchanting_table", "#5c2a34"),
            Map.entry("smooth_stone", "#9e9e9e"), Map.entry("blast_furnace", "#5f5f60"), Map.entry("furnace", "#6e6e6e"),
            Map.entry("grindstone", "#8c8c8c"), Map.entry("daylight_detector", "#bfa77a"), Map.entry("light_blue_glazed_terracotta", "#5fa5d1"),
            Map.entry("copper_block", "#c06c50"), Map.entry("netherite_block", "#42393b"), Map.entry("emerald_block", "#2ad15f"),
            Map.entry("redstone_block", "#af1906"), Map.entry("lapis_block", "#1f43a0"), Map.entry("coal_block", "#101010"),
            Map.entry("bookshelf", "#75603c"), Map.entry("crafting_table", "#77583a"), Map.entry("beacon", "#75dcd7"),
            Map.entry("end_stone", "#dbdea0"), Map.entry("purpur_block", "#a97ea9"), Map.entry("prismarine", "#63a497"),
            Map.entry("netherrack", "#6e3535"), Map.entry("white_wool", "#e9ecec"), Map.entry("terracotta", "#985e43"),
            Map.entry("polished_andesite", "#84878a"), Map.entry("andesite", "#888889"), Map.entry("diorite", "#bdbcbd"),
            Map.entry("granite", "#95674f"), Map.entry("glowstone", "#ab8654"), Map.entry("packed_ice", "#8db4fa"),
            Map.entry("snow_block", "#f9fefe"), Map.entry("hay_block", "#a68b0c"), Map.entry("iron_bars", "#8a8b88"),
            Map.entry("cauldron", "#4a4a4a"), Map.entry("anvil", "#454545"), Map.entry("piston", "#9b8a6b"));

    static Color color(String id) {
        String path = Ids.path(Ids.full(id));
        int b = path.indexOf('[');
        if (b >= 0) path = path.substring(0, b);
        String hex = COLORS.get(path);
        if (hex != null) return Color.web(hex);
        int h = path.hashCode();
        return Color.hsb(Math.floorMod(h, 360), 0.35 + Math.floorMod(h >> 8, 30) / 100.0, 0.55 + Math.floorMod(h >> 16, 35) / 100.0);
    }
}
