package modstudio.ui.editors;

import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import modstudio.core.Systems;
import modstudio.core.TextureAsset;
import modstudio.core.ThemeArt;
import modstudio.core.VanillaAssets;
import modstudio.ui.Ui;
import modstudio.ui.controls.Picker;
import modstudio.ui.controls.TextureField;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Editor for UI themes: enchantment glint, replaced vanilla textures, splash texts, the tooltip box and screen buttons. */
final class ThemeEditor extends EditorBase<Systems.Theme> {
    private static final int VIEW = 64;
    private final VBox overrideRows = new VBox(6);
    private final VBox buttonRows = new VBox(6);
    private final Canvas glintView = new Canvas(VIEW * 2, VIEW * 2);
    private final WritableImage frame = new WritableImage(VIEW, VIEW);
    private int[] itemPx;
    private int[] glintPx;
    private int glintSize;
    private AnimationTimer timer;

    ThemeEditor(Ctx c, Systems.Theme e) {
        super(c, e);
    }

    @Override
    protected void fill() {
        f.section(t("section.general"), t("help.theme"));
        nameRow();

        f.section(t("section.glint"), t("help.glint"));
        f.choice(t("field.glint"), List.of("keep", "color", "texture"), x -> t("glint." + x), () -> e.glint, v -> e.glint = v);
        GameplayEditors.colorRow(f, t("field.glint_color"), () -> e.glintColor, v -> e.glintColor = v);
        f.showIf(() -> e.glint.equals("color"));
        f.choice(t("field.glint_pattern"), List.of("waves", "stripes", "sparkles", "aurora"), x -> t("glint_pattern." + x),
                () -> e.glintPattern, v -> e.glintPattern = v);
        f.showIf(() -> e.glint.equals("color"));
        f.row(t("field.glint_texture"), new TextureField(c.ws, c.window(), e.glintTexture, () -> e.name + "_glint", "block", v -> {
            e.glintTexture = v;
            f.changed();
        }), t("help.glint_texture"));
        f.showIf(() -> e.glint.equals("texture"));
        f.check(t("field.glint_armor"), () -> e.glintArmor, v -> e.glintArmor = v);
        f.showIf(() -> !e.glint.equals("keep"));
        glintView.getStyleClass().add("glint-view");
        f.row(t("field.glint_preview"), glintView);
        f.showIf(() -> !e.glint.equals("keep"));

        f.section(t("section.theme_textures"), t("help.theme_textures"));
        f.add(overrideRows);
        f.add(Ui.button(t("theme.add_texture"), "mdi2p-plus", this::addOverride));
        rebuildOverrides();

        f.section(t("section.theme_title"), t("help.theme_title"));
        f.area(t("field.splashes"), () -> e.splashes, v -> e.splashes = v, 5, t("help.splashes"));

        f.section(t("section.theme_tooltip"), t("help.theme_tooltip"));
        f.check(t("field.theme_tooltip"), () -> e.tooltip, v -> e.tooltip = v);
        GameplayEditors.colorRow(f, t("field.tooltip_background"), () -> e.tooltipBackground, v -> e.tooltipBackground = v);
        f.showIf(() -> e.tooltip);
        f.integer(t("field.tooltip_background_opacity"), 0, 100, () -> e.tooltipBackgroundOpacity, v -> e.tooltipBackgroundOpacity = v);
        f.showIf(() -> e.tooltip);
        GameplayEditors.colorRow(f, t("field.tooltip_border_top"), () -> e.tooltipBorderTop, v -> e.tooltipBorderTop = v);
        f.showIf(() -> e.tooltip);
        GameplayEditors.colorRow(f, t("field.tooltip_border_bottom"), () -> e.tooltipBorderBottom, v -> e.tooltipBorderBottom = v);
        f.showIf(() -> e.tooltip);
        f.integer(t("field.tooltip_border_opacity"), 0, 100, () -> e.tooltipBorderOpacity, v -> e.tooltipBorderOpacity = v);
        f.showIf(() -> e.tooltip);

        f.section(t("section.screen_buttons"), t("help.screen_buttons"));
        f.add(buttonRows);
        f.add(Ui.button(t("theme.add_button"), "mdi2p-plus", () -> {
            e.buttons.add(new Systems.ScreenButton());
            rebuildButtons();
            f.changed();
        }));
        rebuildButtons();

        long start = System.nanoTime();
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                drawGlint((now - start) / 1e9);
            }
        };
        glintView.sceneProperty().addListener((o, a, b) -> {
            if (b == null) timer.stop();
            else timer.start();
        });
    }

    @Override
    protected void refresh() {
        // the pattern is rebuilt from the current settings; the timer draws it moving over a sample item
        if (e.glint.equals("texture")) {
            TextureAsset t = c.p.texture(e.glintTexture);
            if (t != null) {
                glintPx = t.firstFrame();
                glintSize = t.width;
            } else {
                glintPx = null;
            }
        } else {
            glintPx = ThemeArt.glint(e.glintColor, e.glintPattern);
            glintSize = ThemeArt.GLINT;
        }
        if (itemPx == null) itemPx = sampleItem();
    }

    /** The diamond sword of the installed game, or a plain gem shape. */
    private int[] sampleItem() {
        Image img = c.ws.thumbnails().item("minecraft:diamond_sword", VIEW);
        int[] px = new int[VIEW * VIEW];
        if (img != null && img.getWidth() >= VIEW && img.getHeight() >= VIEW) {
            PixelReader r = img.getPixelReader();
            for (int y = 0; y < VIEW; y++) for (int x = 0; x < VIEW; x++) px[y * VIEW + x] = r.getArgb(x, y);
            return px;
        }
        for (int y = 0; y < VIEW; y++) {
            for (int x = 0; x < VIEW; x++) {
                if (Math.abs(x - VIEW / 2) + Math.abs(y - VIEW / 2) < VIEW / 2 - 4) px[y * VIEW + x] = 0xFF3FB8C8;
            }
        }
        return px;
    }

    private void drawGlint(double seconds) {
        if (itemPx == null || glintView.getScene() == null) return;
        var w = frame.getPixelWriter();
        int off = (int) (seconds * 10);
        for (int y = 0; y < VIEW; y++) {
            for (int x = 0; x < VIEW; x++) {
                int p = itemPx[y * VIEW + x];
                int a = p >>> 24;
                if (a == 0 || glintPx == null || e.glint.equals("keep")) {
                    w.setArgb(x, y, p);
                    continue;
                }
                int gx = Math.floorMod(x / 2 + off, glintSize), gy = Math.floorMod(y / 2 + off / 2, glintSize);
                int g = glintPx[gy * glintSize + gx];
                int r = Math.min(255, (p >> 16 & 0xFF) + (int) ((g >> 16 & 0xFF) * 0.8));
                int gg = Math.min(255, (p >> 8 & 0xFF) + (int) ((g >> 8 & 0xFF) * 0.8));
                int b = Math.min(255, (p & 0xFF) + (int) ((g & 0xFF) * 0.8));
                w.setArgb(x, y, a << 24 | r << 16 | gg << 8 | b);
            }
        }
        var gc = glintView.getGraphicsContext2D();
        gc.clearRect(0, 0, glintView.getWidth(), glintView.getHeight());
        gc.setImageSmoothing(false);
        gc.drawImage(frame, 0, 0, glintView.getWidth(), glintView.getHeight());
    }

    // ------------------------------------------------------------------ replaced vanilla textures

    private static final List<String> FOLDERS = List.of("gui/", "misc/", "environment/", "map/");

    private void addOverride() {
        VanillaAssets va = VanillaAssets.get(c.p.mc());
        if (va == null) {
            Ui.info(c.window(), t("theme.add_texture"), t("theme.no_game"));
            return;
        }
        List<Picker.Entry> entries = new ArrayList<>();
        for (String folder : FOLDERS) {
            for (String path : va.list("assets/minecraft/textures/" + folder)) {
                if (!path.endsWith(".png")) continue;
                String id = path.substring("assets/minecraft/textures/".length(), path.length() - 4);
                String group = id.contains("/") ? id.substring(0, id.lastIndexOf('/')) : id;
                entries.add(new Picker.Entry(id, id, image(va, path, 32), group));
            }
        }
        Picker.show(c.window(), t("theme.pick_texture"), entries, true, "").ifPresent(id -> {
            Systems.TextureOverride o = new Systems.TextureOverride();
            o.path = id.trim();
            byte[] meta = va.bytes("assets/minecraft/textures/" + o.path + ".png.mcmeta");
            o.meta = meta == null ? "" : new String(meta, StandardCharsets.UTF_8);
            e.textures.add(o);
            rebuildOverrides();
            f.changed();
        });
    }

    private static Image image(VanillaAssets va, String path, int size) {
        byte[] b = va.bytes(path);
        if (b == null) return null;
        try {
            return new Image(new ByteArrayInputStream(b), size, size, true, false);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void rebuildOverrides() {
        overrideRows.getChildren().clear();
        VanillaAssets va = VanillaAssets.get(c.p.mc());
        for (Systems.TextureOverride o : new ArrayList<>(e.textures)) {
            ImageView vanilla = new ImageView();
            vanilla.setFitWidth(32);
            vanilla.setFitHeight(32);
            vanilla.setPreserveRatio(true);
            vanilla.setSmooth(false);
            String size = "";
            if (va != null) {
                byte[] b = va.bytes("assets/minecraft/textures/" + o.path + ".png");
                if (b != null) {
                    try {
                        Image full = new Image(new ByteArrayInputStream(b));
                        vanilla.setImage(full);
                        size = (int) full.getWidth() + " x " + (int) full.getHeight();
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            Label path = new Label(o.path);
            path.getStyleClass().add("mono");
            Label dims = Ui.label(size.isEmpty() ? "" : t("theme.vanilla_size", size), "text-muted");
            VBox info = new VBox(2, path, dims);
            info.setMinWidth(220);
            String suggested = o.path.substring(o.path.lastIndexOf('/') + 1);
            TextureField tex = new TextureField(c.ws, c.window(), o.texture, () -> e.name + "_" + suggested, "block", v -> {
                o.texture = v;
                f.changed();
            });
            Button remove = Ui.iconButton("mdi2c-close", t("action.remove"), () -> {
                e.textures.remove(o);
                rebuildOverrides();
                f.changed();
            });
            HBox row = new HBox(10, vanilla, info, tex, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(tex, Priority.ALWAYS);
            row.getStyleClass().add("list-card");
            overrideRows.getChildren().add(row);
        }
        if (e.textures.isEmpty()) overrideRows.getChildren().add(Ui.label(t("theme.no_textures"), "text-muted"));
    }

    // ------------------------------------------------------------------ title and pause screen buttons

    private void rebuildButtons() {
        buttonRows.getChildren().clear();
        for (Systems.ScreenButton b : new ArrayList<>(e.buttons)) {
            ComboBox<String> screen = Ui.combo(List.of("title", "pause"), x -> t("screen_button.screen." + x));
            screen.setValue(b.screen);
            screen.valueProperty().addListener((o, x, y) -> {
                if (y != null) b.screen = y;
                f.changed();
            });
            ComboBox<String> corner = Ui.combo(List.of("top_left", "top_right", "bottom_left", "bottom_right"), x -> t("screen_button.corner." + x));
            corner.setValue(b.corner);
            corner.valueProperty().addListener((o, x, y) -> {
                if (y != null) b.corner = y;
                f.changed();
            });
            TextField label = new TextField(b.label);
            label.setPromptText(t("screen_button.label"));
            label.textProperty().addListener((o, x, y) -> {
                b.label = y;
                f.changed();
            });
            ComboBox<String> action = Ui.combo(List.of("url", "command"), x -> t("screen_button.action." + x));
            action.setValue(b.action);
            TextField value = new TextField(b.value);
            value.setPromptText(t("screen_button.value." + b.action));
            action.valueProperty().addListener((o, x, y) -> {
                if (y != null) b.action = y;
                value.setPromptText(t("screen_button.value." + b.action));
                f.changed();
            });
            value.textProperty().addListener((o, x, y) -> {
                b.value = y;
                f.changed();
            });
            Button remove = Ui.iconButton("mdi2c-close", t("action.remove"), () -> {
                e.buttons.remove(b);
                rebuildButtons();
                f.changed();
            });
            for (Region r : List.of(screen, corner, action)) r.setMinWidth(Region.USE_PREF_SIZE);
            HBox top = new HBox(8, screen, corner, label, remove);
            top.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(label, Priority.ALWAYS);
            HBox bottom = new HBox(8, action, value);
            bottom.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(value, Priority.ALWAYS);
            VBox card = new VBox(6, top, bottom);
            card.getStyleClass().add("list-card");
            buttonRows.getChildren().add(card);
        }
        if (e.buttons.isEmpty()) buttonRows.getChildren().add(Ui.label(t("theme.no_buttons"), "text-muted"));
    }
}
