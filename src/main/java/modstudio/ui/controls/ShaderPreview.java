package modstudio.ui.controls;

import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import modstudio.core.Project;
import modstudio.core.ShaderNodes;
import modstudio.core.Systems.ShaderNode;
import modstudio.core.TextureAsset;
import modstudio.core.TextureTemplates;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Live preview of a shader graph: the graph runs on the CPU for every pixel of a sample picture (a project texture or
 * a built-in one) and is drawn next to the unchanged picture, animated with time. It shows what the graph does to a
 * colour; the game also lights, fogs and blends what the shader draws.
 */
public final class ShaderPreview extends VBox {
    private static final int RES = 64, SCALE = 3;

    private final Project project;
    private final List<ShaderNode> nodes;
    private final Canvas before = new Canvas(RES * SCALE, RES * SCALE);
    private final Canvas after = new Canvas(RES * SCALE, RES * SCALE);
    private final WritableImage beforeImg = new WritableImage(RES, RES);
    private final WritableImage afterImg = new WritableImage(RES, RES);
    private final ComboBox<String> source = new ComboBox<>();
    private final Label timeLabel = Ui.label("", "text-muted", "text-small");
    private final ShaderNodes.Env env = new ShaderNodes.Env();
    private final double[] out = new double[4];
    private ShaderNodes.Program program;
    private int[] px = new int[16 * 16];
    private int pw = 16, ph = 16;
    private boolean playing = true;
    private double time;
    private double lightLevel = 1.0;
    private boolean fogEnabled;
    private Color fogColor = Color.web("#90B4FF");
    private long last;
    private final AnimationTimer timer;

    public ShaderPreview(Project project, List<ShaderNode> nodes) {
        super(8);
        this.project = project;
        this.nodes = nodes;
        getStyleClass().add("shader-preview");
        List<String> choices = new ArrayList<>(List.of("@block", "@gem", "@gradient", "@scene"));
        for (TextureAsset t : project.textures) choices.add(t.name);
        source.getItems().setAll(choices);
        source.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(String s) {
                return s == null ? "" : s.startsWith("@") ? I18n.t("shader.preview_" + s.substring(1)) : s;
            }

            @Override
            public String fromString(String s) {
                return s;
            }
        });
        source.setValue("@block");
        source.valueProperty().addListener((o, a, b) -> {
            loadSource();
            drawBefore();
            drawAfter();
        });
        Button play = Ui.iconButton("mdi2p-pause", I18n.t("shader.preview_pause"), null);
        play.setOnAction(e -> {
            playing = !playing;
            play.setGraphic(Ui.icon(playing ? "mdi2p-pause" : "mdi2p-play"));
        });
        Button restart = Ui.iconButton("mdi2r-replay", I18n.t("shader.preview_restart"), () -> {
            time = 0;
            drawAfter();
        });
        HBox bar = Ui.hbox(8, Ui.label(I18n.t("shader.preview_source"), "form-label"), source, play, restart, timeLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        Slider lightSlider = new Slider(0.1, 1.5, 1.0);
        lightSlider.setPrefWidth(100);
        lightSlider.valueProperty().addListener((o, a, b) -> {
            lightLevel = b.doubleValue();
            drawAfter();
        });
        CheckBox fogBox = new CheckBox(I18n.t("shader.preview_fog"));
        fogBox.selectedProperty().addListener((o, a, b) -> {
            fogEnabled = b;
            drawAfter();
        });
        ColorPicker fogPicker = new ColorPicker(fogColor);
        fogPicker.setOnAction(e -> {
            fogColor = fogPicker.getValue();
            drawAfter();
        });
        HBox envBar = Ui.hbox(10, Ui.label(I18n.t("shader.preview_light"), "text-muted", "text-small"), lightSlider, fogBox, fogPicker);
        envBar.setAlignment(Pos.CENTER_LEFT);
        VBox b1 = new VBox(4, Ui.label(I18n.t("shader.preview_before"), "text-muted", "text-small"), before);
        VBox b2 = new VBox(4, Ui.label(I18n.t("shader.preview_after"), "text-muted", "text-small"), after);
        HBox views = Ui.hbox(16, b1, b2);
        getChildren().addAll(bar, envBar, views);
        env.texture = this::sample;
        loadSource();
        recompile();
        drawBefore();
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (last == 0) last = now;
                double dt = (now - last) / 1e9;
                if (dt < 0.05) return;
                last = now;
                if (playing) time += dt;
                drawAfter();
            }
        };
        sceneProperty().addListener((o, a, b) -> {
            if (b == null) timer.stop();
            else {
                last = 0;
                timer.start();
            }
        });
    }

    /** Rebuilds the evaluator after the graph changed. */
    public void recompile() {
        program = new ShaderNodes.Program(nodes);
        drawAfter();
    }

    private void loadSource() {
        String s = source.getValue() == null ? "@block" : source.getValue();
        switch (s) {
            case "@block" -> {
                px = TextureTemplates.generate("block", 0xFF8A8F98);
                pw = ph = 16;
            }
            case "@gem" -> {
                px = TextureTemplates.generate("gem", 0xFFE0115F);
                pw = ph = 16;
            }
            case "@gradient" -> {
                pw = ph = 32;
                px = new int[pw * ph];
                for (int y = 0; y < ph; y++) {
                    for (int x = 0; x < pw; x++) {
                        java.awt.Color c = java.awt.Color.getHSBColor(x / (float) pw, 0.75f, 0.35f + 0.65f * (1 - y / (float) ph));
                        px[y * pw + x] = 0xFF000000 | (c.getRGB() & 0xFFFFFF);
                    }
                }
            }
            case "@scene" -> {
                pw = ph = 64;
                px = new int[pw * ph];
                for (int y = 0; y < ph; y++) {
                    for (int x = 0; x < pw; x++) {
                        int col;
                        if (y < 24) col = (x > 46 && x < 56 && y > 4 && y < 14) ? 0xFFFFEE88 : 0xFF7FA8FF;
                        else if (y < 28) col = 0xFF5D9B3C;
                        else col = ((x / 8 + y / 8) % 2 == 0) ? 0xFF7B573C : 0xFF6B4B34;
                        px[y * pw + x] = col;
                    }
                }
            }
            default -> {
                TextureAsset t = project.textureByName(s);
                if (t != null && t.firstFrame() != null) {
                    px = t.firstFrame();
                    pw = t.width;
                    ph = t.frameHeight();
                }
            }
        }
        if (px == null || px.length < pw * ph) {
            px = new int[16 * 16];
            pw = ph = 16;
        }
    }

    private void sample(double u, double v, double[] o) {
        int x = Math.floorMod((int) Math.floor(u * pw), pw), y = Math.floorMod((int) Math.floor(v * ph), ph);
        int c = px[y * pw + x];
        o[0] = (c >> 16 & 255) / 255.0;
        o[1] = (c >> 8 & 255) / 255.0;
        o[2] = (c & 255) / 255.0;
        o[3] = (c >>> 24) / 255.0;
    }

    private static int checker(int x, int y) {
        return ((x / 8 + y / 8) % 2 == 0) ? 0xFF2A2A2A : 0xFF3A3A3A;
    }

    private static int over(int bg, double r, double g, double b, double a) {
        a = Math.max(0, Math.min(1, a));
        int br = bg >> 16 & 255, bgg = bg >> 8 & 255, bb = bg & 255;
        int rr = (int) Math.round(Math.max(0, Math.min(1, r)) * 255 * a + br * (1 - a));
        int gg = (int) Math.round(Math.max(0, Math.min(1, g)) * 255 * a + bgg * (1 - a));
        int bl = (int) Math.round(Math.max(0, Math.min(1, b)) * 255 * a + bb * (1 - a));
        return 0xFF000000 | rr << 16 | gg << 8 | bl;
    }

    private void drawBefore() {
        PixelWriter w = beforeImg.getPixelWriter();
        double[] c = new double[4];
        for (int y = 0; y < RES; y++) {
            for (int x = 0; x < RES; x++) {
                sample((x + 0.5) / RES, (y + 0.5) / RES, c);
                w.setArgb(x, y, over(checker(x, y), c[0], c[1], c[2], c[3]));
            }
        }
        paint(before, beforeImg);
    }

    private void drawAfter() {
        if (program == null) return;
        PixelWriter w = afterImg.getPixelWriter();
        env.time = time;
        for (int y = 0; y < RES; y++) {
            for (int x = 0; x < RES; x++) {
                env.u = (x + 0.5) / RES;
                env.v = (y + 0.5) / RES;
                env.sx = x * 4 + 2;
                env.sy = y * 4 + 2;
                sample(env.u, env.v, env.color);
                try {
                    program.run(env, out);
                    double shade = Math.max(0.1, Math.min(1.5, lightLevel));
                    for (int i = 0; i < 3; i++) out[i] *= shade;
                    if (fogEnabled) {
                        double dx = env.u - 0.5, dy = env.v - 0.5;
                        double fogT = Math.max(0.0, Math.min(1.0, (Math.hypot(dx, dy) - 0.15) / 0.45));
                        out[0] = out[0] * (1 - fogT) + fogColor.getRed() * fogT;
                        out[1] = out[1] * (1 - fogT) + fogColor.getGreen() * fogT;
                        out[2] = out[2] * (1 - fogT) + fogColor.getBlue() * fogT;
                    }
                } catch (RuntimeException ex) {
                    out[0] = 1;
                    out[1] = 0;
                    out[2] = 1;
                    out[3] = 1;
                }
                w.setArgb(x, y, over(checker(x, y), out[0], out[1], out[2], out[3]));
            }
        }
        paint(after, afterImg);
        timeLabel.setText(I18n.t("shader.preview_time", String.format(java.util.Locale.ROOT, "%.1f", time)));
    }

    private static void paint(Canvas c, WritableImage img) {
        GraphicsContext g = c.getGraphicsContext2D();
        g.setImageSmoothing(false);
        g.drawImage(img, 0, 0, c.getWidth(), c.getHeight());
    }
}
