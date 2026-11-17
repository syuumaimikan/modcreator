package modstudio.ui.editors;

import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import modstudio.core.Element;
import modstudio.core.ElementType;
import modstudio.core.Ids;
import modstudio.core.Systems;
import modstudio.core.Systems.CustomScreen;
import modstudio.core.Systems.ScreenWidget;
import modstudio.core.TextureAsset;
import modstudio.ui.Ui;
import modstudio.ui.controls.Form;
import modstudio.ui.controls.IdField;
import modstudio.ui.controls.McText;
import modstudio.ui.controls.TextureField;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Editors for custom screens and HUD overlays (with a live, draggable layout preview) and for icon fonts. */
final class ScreenEditors {
    private ScreenEditors() {}

    static final List<String> KINDS = List.of("panel", "image", "text", "button", "bar", "item", "entity");
    static final List<String> ANCHORS = List.of("top_left", "top", "top_right", "left", "center", "right", "bottom_left", "bottom", "bottom_right");
    static final List<String> ANIMATIONS = List.of("none", "fade_in", "slide_up", "slide_down", "slide_left", "slide_right", "pop", "pulse", "bob", "blink",
            "look_around");
    static final List<String> ACTIONS = List.of("close", "screen", "procedure", "url", "command");
    static final List<String> VALUES = List.of("health", "food", "xp", "level", "air", "armor", "score");

    static String kindIcon(String kind) {
        return switch (kind) {
            case "panel" -> "mdi2r-rectangle-outline";
            case "image" -> "mdi2i-image-outline";
            case "button" -> "mdi2g-gesture-tap-button";
            case "bar" -> "mdi2p-progress-check";
            case "item" -> "mdi2d-diamond-stone";
            case "entity" -> "mdi2a-account-outline";
            default -> "mdi2f-format-text";
        };
    }

    /** Project icons by name (the first icon of a name wins, as in the game). */
    static Map<String, Systems.Icon> icons(modstudio.core.Project p) {
        Map<String, Systems.Icon> r = new LinkedHashMap<>();
        for (Systems.IconFont f : p.all(Systems.IconFont.class)) {
            for (Systems.Icon ic : f.icons) if (ic.name != null && !ic.name.isBlank()) r.putIfAbsent(ic.name.trim(), ic);
        }
        return r;
    }

    // ------------------------------------------------------------------ text drawing shared by both previews

    /** Draws Minecraft-like text with colour codes, :icon: glyphs and {space:N} on a canvas, in GUI pixels. */
    static final class TextPainter {
        private static final Font FONT = Font.font("Monospaced", 9);
        private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)(?::([A-Za-z0-9_.+-]+))?}");
        private final modstudio.core.Project p;
        private final Map<String, Image> frames = new HashMap<>();
        private Map<String, Systems.Icon> icons = Map.of();

        TextPainter(modstudio.core.Project p) {
            this.p = p;
        }

        void refresh() {
            icons = icons(p);
        }

        private record Run(String text, Color color, Systems.Icon icon, int space) {}

        /** Sample values for the placeholders the game fills in. */
        static String sample(String s) {
            if (s == null || s.indexOf('{') < 0) return s == null ? "" : s;
            Matcher m = PLACEHOLDER.matcher(s);
            StringBuilder b = new StringBuilder();
            while (m.find()) {
                String v = switch (m.group(1)) {
                    case "score" -> m.group(2) == null ? null : "12";
                    case "player" -> "Steve";
                    case "health" -> "14";
                    case "max_health" -> "20";
                    case "food" -> "18";
                    case "level" -> "7";
                    case "armor" -> "10";
                    case "x" -> "128";
                    case "y" -> "64";
                    case "z" -> "-30";
                    case "dimension" -> "overworld";
                    case "fps" -> "60";
                    case "time" -> String.format(Locale.ROOT, "%02d:%02d", LocalTime.now().getHour(), LocalTime.now().getMinute());
                    case "date" -> LocalDate.now().toString();
                    case "game_time" -> "08:00";
                    case "day" -> "3";
                    case "media_title" -> "Song Title";
                    case "media_artist" -> "Artist";
                    case "media" -> "Artist - Song Title";
                    case "os" -> System.getProperty("os.name", "");
                    case "java" -> System.getProperty("java.version", "");
                    case "memory" -> "1024 / 4096 MB";
                    default -> null;
                };
                m.appendReplacement(b, Matcher.quoteReplacement(v != null ? v : m.group()));
            }
            m.appendTail(b);
            return b.toString();
        }

        private List<Run> runs(String line, Color base) {
            List<Run> r = new ArrayList<>();
            StringBuilder buf = new StringBuilder();
            Color col = base;
            int i = 0;
            while (i < line.length()) {
                char ch = line.charAt(i);
                if ((ch == '&' || ch == '§') && i + 1 < line.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(line.charAt(i + 1)) >= 0) {
                    if (!buf.isEmpty()) r.add(new Run(buf.toString(), col, null, 0));
                    buf.setLength(0);
                    char code = Character.toLowerCase(line.charAt(i + 1));
                    if ("0123456789abcdef".indexOf(code) >= 0) col = McText.color(code);
                    else if (code == 'r') col = base;
                    i += 2;
                    continue;
                }
                if (ch == ':') {
                    int end = line.indexOf(':', i + 1);
                    Systems.Icon ic = end > i + 1 ? icons.get(line.substring(i + 1, end)) : null;
                    if (ic != null) {
                        if (!buf.isEmpty()) r.add(new Run(buf.toString(), col, null, 0));
                        buf.setLength(0);
                        r.add(new Run("", col, ic, 0));
                        i = end + 1;
                        continue;
                    }
                }
                if (ch == '{' && line.startsWith("{space:", i)) {
                    int end = line.indexOf('}', i);
                    if (end > 0) {
                        int n;
                        try {
                            n = Integer.parseInt(line.substring(i + 7, end).trim());
                        } catch (NumberFormatException ex) {
                            n = 0;
                        }
                        if (!buf.isEmpty()) r.add(new Run(buf.toString(), col, null, 0));
                        buf.setLength(0);
                        r.add(new Run("", col, null, n));
                        i = end + 1;
                        continue;
                    }
                }
                buf.append(ch);
                i++;
            }
            if (!buf.isEmpty()) r.add(new Run(buf.toString(), col, null, 0));
            return r;
        }

        static double measure(String s) {
            Text t = new Text(s);
            t.setFont(FONT);
            return t.getLayoutBounds().getWidth();
        }

        private double width(Run run) {
            if (run.icon() != null) {
                TextureAsset t = p.texture(run.icon().texture);
                int h = Math.max(1, Math.min(256, run.icon().height));
                return t == null ? h + 1 : Math.round(t.width * (double) h / Math.max(1, t.frameHeight())) + 1;
            }
            if (run.space() != 0) return run.space();
            return measure(run.text());
        }

        double width(String line) {
            double w = 0;
            for (Run run : runs(line, Color.WHITE)) w += width(run);
            return w;
        }

        /** Draws one line with its top at y. */
        void line(GraphicsContext g, String line, double x, double y, Color base, boolean shadow) {
            g.setFont(FONT);
            g.setTextBaseline(VPos.TOP);
            double cx = x;
            for (Run run : runs(line, base)) {
                if (run.icon() != null) {
                    Systems.Icon ic = run.icon();
                    TextureAsset t = p.texture(ic.texture);
                    int h = Math.max(1, Math.min(256, ic.height));
                    double w = width(run) - 1;
                    double top = y + 7 - Math.min(h, ic.ascent);
                    Image img = t == null ? null : frame(t, 0);
                    if (img != null) g.drawImage(img, cx, top, w, h);
                    else {
                        g.setFill(Color.web("#ff00ff"));
                        g.fillRect(cx, top, w, h);
                    }
                } else if (run.space() == 0) {
                    if (shadow) {
                        g.setFill(run.color().deriveColor(0, 1, 0.25, 1));
                        g.fillText(run.text(), cx + 1, y + 1 - 1);
                    }
                    g.setFill(run.color());
                    g.fillText(run.text(), cx, y - 1);
                }
                cx += width(run);
            }
        }

        /** One frame of a project texture at its own size (drawn without smoothing). */
        Image frame(TextureAsset t, int index) {
            int frames = Math.max(1, t.frames());
            int f = Math.floorMod(index, frames);
            String key = t.id + "|" + t.png.hashCode() + "|" + f;
            Image img = this.frames.get(key);
            if (img == null) {
                int[] px = t.pixels();
                int fh = t.frameHeight();
                if (px == null || px.length < t.width * fh * (f + 1)) return null;
                WritableImage w = new WritableImage(Math.max(1, t.width), Math.max(1, fh));
                PixelWriter pw = w.getPixelWriter();
                for (int y = 0; y < fh; y++) for (int x = 0; x < t.width; x++) pw.setArgb(x, y, px[(f * fh + y) * t.width + x]);
                img = w;
                if (this.frames.size() > 400) this.frames.clear();
                this.frames.put(key, img);
            }
            return img;
        }
    }

    // ------------------------------------------------------------------ custom screen

    static final class ScreenEditor extends EditorBase<CustomScreen> {
        /** GUI pixels of a 1920 x 1080 window at GUI scale 4. */
        private static final int W = 480, H = 270;
        private static final double K = 1.5;
        private final Canvas canvas = new Canvas(W * K, H * K);
        private final VBox widgetRows = new VBox(6);
        private final VBox props = new VBox();
        private final Map<ScreenWidget, Label> rowLabels = new HashMap<>();
        private final TextPainter text;
        private ScreenWidget selected;
        private AnimationTimer timer;
        private long start = System.nanoTime();
        private boolean play = true;
        private double dragX, dragY;
        private int dragWx, dragWy;
        private boolean dragging;

        ScreenEditor(Ctx c, CustomScreen e) {
            super(c, e);
            text = new TextPainter(c.p);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.screen"));
            nameRow();
            f.choice(t("field.screen_mode"), List.of("screen", "overlay"), x -> t("screen_mode." + x), () -> e.mode, v -> e.mode = v);
            f.text(t("field.screen_title"), () -> e.title, v -> e.title = v, t("help.screen_title"));
            f.showIf(() -> !e.overlay());
            f.check(t("field.screen_dim"), () -> e.dim, v -> e.dim = v);
            f.showIf(() -> !e.overlay());
            f.check(t("field.screen_pause"), () -> e.pause, v -> e.pause = v, t("help.screen_pause"));
            f.showIf(() -> !e.overlay());
            f.integer(t("field.screen_refresh"), 5, 1200, () -> e.refresh, v -> e.refresh = v, t("help.screen_refresh"));
            f.showIf(e::overlay);

            f.section(t("section.screen_layout"), t("help.screen_layout"));
            StackPane holder = new StackPane(canvas);
            holder.getStyleClass().add("screen-canvas");
            holder.setMaxWidth(W * K + 2);
            holder.setMinWidth(W * K + 2);
            holder.setAlignment(Pos.TOP_LEFT);
            f.add(holder);
            MenuButton add = new MenuButton(t("screen.add_widget"), Ui.icon("mdi2p-plus"));
            for (String k : KINDS) {
                MenuItem mi = new MenuItem(t("widget." + k), Ui.icon(kindIcon(k)));
                mi.setOnAction(ev -> addWidget(k));
                add.getItems().add(mi);
            }
            CheckBox playBox = new CheckBox(t("screen.play_animations"));
            playBox.setSelected(true);
            playBox.selectedProperty().addListener((o, a, b) -> {
                play = b;
                draw();
            });
            Button replay = Ui.button(t("screen.replay"), "mdi2r-replay", () -> start = System.nanoTime());
            HBox tools = Ui.hbox(10, add, playBox, replay);
            tools.setAlignment(Pos.CENTER_LEFT);
            f.add(tools);
            f.add(Ui.help(t("help.screen_canvas")));
            setupCanvas();

            f.section(t("section.screen_widgets"), t("help.screen_widgets"));
            f.add(widgetRows);
            rebuildRows();

            f.section(t("section.screen_widget"));
            f.add(props);
            f.sectionShowIf(() -> selected != null);
            rebuildProps();

            f.section(t("section.screen_use"));
            f.add(Ui.help(t("help.screen_use", c.ns())));
            f.add(Ui.help(t("help.screen_values")));

            timer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (play) draw();
                }
            };
            canvas.sceneProperty().addListener((o, a, b) -> {
                if (b == null) timer.stop();
                else timer.start();
            });
        }

        @Override
        protected void refresh() {
            text.refresh();
            for (var en : rowLabels.entrySet()) en.getValue().setText(rowText(en.getKey()));
            draw();
        }

        // -------------------------------------------------------------- widgets list

        private void addWidget(String kind) {
            ScreenWidget w = new ScreenWidget();
            w.kind = kind;
            switch (kind) {
                case "panel" -> {
                    w.width = 176;
                    w.height = 120;
                    w.color = "#C6C6C6";
                    w.color2 = "#373737";
                }
                case "image" -> {
                    w.width = 32;
                    w.height = 32;
                }
                case "text" -> {
                    w.text = "Hello, {player}";
                    w.width = 100;
                    w.height = 10;
                }
                case "button" -> {
                    w.text = t("screen.sample_button");
                    w.width = 80;
                    w.height = 20;
                    w.color2 = "#6B6B6B";
                }
                case "bar" -> {
                    w.width = 80;
                    w.height = 8;
                    w.color = "#E04040";
                    w.color2 = "#301010";
                    w.text = "{value}/{max}";
                }
                case "item" -> {
                    w.width = 16;
                    w.height = 16;
                }
                case "entity" -> {
                    w.width = 40;
                    w.height = 60;
                }
                default -> {
                }
            }
            if (e.overlay()) {
                w.anchor = "top_left";
                w.x = 6;
                w.y = 6 + 12 * e.widgets.size();
            } else {
                w.x = -w.width / 2;
                w.y = -w.height / 2;
            }
            e.widgets.add(w);
            selected = w;
            rebuildRows();
            rebuildProps();
            f.changed();
        }

        private String rowText(ScreenWidget w) {
            String detail = switch (w.kind) {
                case "text", "button" -> w.text;
                case "bar" -> w.value + (w.text.isBlank() ? "" : "  " + w.text);
                case "item" -> w.item;
                case "entity" -> w.entity;
                default -> {
                    TextureAsset tex = c.p.texture(w.texture);
                    yield tex == null ? "" : tex.name;
                }
            };
            String where = t("anchor." + w.anchor) + "  " + w.x + ", " + w.y + "  " + w.width + " x " + w.height;
            return (detail == null || detail.isBlank() ? "" : detail.replace('\n', ' ') + "   ") + where;
        }

        private void rebuildRows() {
            widgetRows.getChildren().clear();
            rowLabels.clear();
            List<ScreenWidget> list = e.widgets;
            for (int i = 0; i < list.size(); i++) {
                ScreenWidget w = list.get(i);
                int index = i;
                Label kind = Ui.label(t("widget." + w.kind), "text-bold");
                Label detail = Ui.label(rowText(w), "text-muted", "text-small");
                rowLabels.put(w, detail);
                VBox info = new VBox(2, kind, detail);
                info.setMinWidth(0);
                HBox.setHgrow(info, Priority.ALWAYS);
                Button up = Ui.iconButton("mdi2a-arrow-up", t("screen.move_back"), () -> move(index, -1));
                Button down = Ui.iconButton("mdi2a-arrow-down", t("screen.move_front"), () -> move(index, 1));
                Button copy = Ui.iconButton("mdi2c-content-duplicate", t("action.duplicate"), () -> duplicate(w));
                Button remove = Ui.iconButton("mdi2c-close", t("action.remove"), () -> remove(w));
                up.setDisable(i == 0);
                down.setDisable(i == list.size() - 1);
                HBox row = new HBox(10, Ui.icon(kindIcon(w.kind), 18), info, up, down, copy, remove);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("list-card");
                if (w == selected) row.getStyleClass().add("selected-card");
                row.setOnMouseClicked(ev -> select(w));
                widgetRows.getChildren().add(row);
            }
            if (list.isEmpty()) widgetRows.getChildren().add(Ui.label(t("screen.no_widgets"), "text-muted"));
        }

        private void move(int index, int d) {
            int j = index + d;
            if (j < 0 || j >= e.widgets.size()) return;
            ScreenWidget w = e.widgets.remove(index);
            e.widgets.add(j, w);
            rebuildRows();
            f.changed();
        }

        private void duplicate(ScreenWidget w) {
            ScreenWidget copy = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(w), ScreenWidget.class);
            copy.x += 8;
            copy.y += 8;
            e.widgets.add(e.widgets.indexOf(w) + 1, copy);
            selected = copy;
            rebuildRows();
            rebuildProps();
            f.changed();
        }

        private void remove(ScreenWidget w) {
            e.widgets.remove(w);
            if (selected == w) selected = null;
            rebuildRows();
            rebuildProps();
            f.changed();
        }

        private void select(ScreenWidget w) {
            if (selected == w) return;
            selected = w;
            rebuildRows();
            rebuildProps();
            f.updateVisibility();
            draw();
        }

        // -------------------------------------------------------------- properties of the selected widget

        private void rebuildProps() {
            props.getChildren().clear();
            ScreenWidget w = selected;
            if (w == null || !e.widgets.contains(w)) return;
            Form p = Form.inline(f::changed);
            p.choice(t("field.widget_kind"), KINDS, x -> t("widget." + x), () -> w.kind, v -> {
                w.kind = v;
                rebuildRows();
            });
            p.choice(t("field.widget_anchor"), ANCHORS, x -> t("anchor." + x), () -> w.anchor, v -> w.anchor = v, t("help.widget_anchor"));
            p.row(t("field.widget_position"), Ui.hbox(8, spinner(-2000, 2000, w.x, v -> w.x = v), spinner(-2000, 2000, w.y, v -> w.y = v)));
            p.row(t("field.widget_size"), Ui.hbox(8, spinner(1, 2000, w.width, v -> w.width = v), spinner(1, 2000, w.height, v -> w.height = v)));
            p.area(t("field.widget_text"), () -> w.text, v -> w.text = v, 2, t("help.widget_text"));
            p.showIf(() -> List.of("text", "button", "bar").contains(w.kind));
            p.choice(t("field.widget_align"), List.of("left", "center", "right"), x -> t("align." + x), () -> w.align, v -> w.align = v);
            p.showIf(() -> w.kind.equals("text"));
            p.check(t("field.widget_shadow"), () -> w.shadow, v -> w.shadow = v);
            p.showIf(() -> w.kind.equals("text") || w.kind.equals("button"));
            GameplayEditors.colorRow(p, t("field.widget_color"), () -> w.color, v -> w.color = v);
            p.showIf(() -> List.of("panel", "text", "button", "bar").contains(w.kind));
            GameplayEditors.colorRow(p, t("field.widget_color2"), () -> w.color2, v -> w.color2 = v);
            p.showIf(() -> List.of("panel", "button", "bar").contains(w.kind));
            p.row(t("field.widget_texture"), new TextureField(c.ws, c.window(), w.texture, () -> e.name + "_" + w.kind, "block", v -> {
                w.texture = v;
                p.changed();
            }), t("help.widget_texture"));
            p.showIf(() -> List.of("panel", "image", "button", "bar").contains(w.kind));
            p.id(t("field.widget_item"), IdField.Kind.ITEM, () -> w.item, v -> w.item = v, env());
            p.showIf(() -> w.kind.equals("item"));
            p.text(t("field.widget_entity"), () -> w.entity, v -> w.entity = v, t("help.widget_entity"));
            p.showIf(() -> w.kind.equals("entity"));
            String[] objective = {w.value.startsWith("score:") ? w.value.substring(6) : "points"};
            p.choice(t("field.widget_value"), VALUES, x -> t("widget_value." + x), () -> w.value.startsWith("score:") ? "score" : w.value,
                    v -> w.value = v.equals("score") ? "score:" + objective[0] : v);
            p.showIf(() -> w.kind.equals("bar"));
            p.text(t("field.widget_objective"), () -> objective[0], v -> {
                objective[0] = v.trim();
                if (w.value.startsWith("score:")) w.value = "score:" + objective[0];
            });
            p.showIf(() -> w.kind.equals("bar") && w.value.startsWith("score:"));
            p.text(t("field.widget_max"), () -> w.max, v -> w.max = v, t("help.widget_max"));
            p.showIf(() -> w.kind.equals("bar"));
            p.choice(t("field.widget_action"), ACTIONS, x -> t("widget_action." + x), () -> w.action, v -> w.action = v);
            p.showIf(() -> w.kind.equals("button"));
            ComboBox<String> target = new ComboBox<>();
            target.setEditable(true);
            target.setPrefWidth(260);
            target.getEditor().setText(w.target);
            target.setOnShowing(ev -> target.getItems().setAll(targets(w.action)));
            target.getEditor().textProperty().addListener((o, a, b) -> {
                w.target = b;
                p.changed();
            });
            target.valueProperty().addListener((o, a, b) -> {
                if (b != null && !b.equals(target.getEditor().getText())) target.getEditor().setText(b);
            });
            p.row(t("field.widget_target"), target, t("help.widget_target"));
            p.showIf(() -> w.kind.equals("button") && !w.action.equals("close"));
            p.text(t("field.widget_tooltip"), () -> w.tooltip, v -> w.tooltip = v);
            p.showIf(() -> !e.overlay());
            p.integer(t("field.widget_opacity"), 0, 100, () -> w.opacity, v -> w.opacity = v);
            p.decimal(t("field.widget_scale"), () -> w.scale, v -> w.scale = v);
            p.choice(t("field.widget_animation"), ANIMATIONS, x -> t("animation." + x), () -> w.animation, v -> {
                w.animation = v;
                start = System.nanoTime();
            });
            p.decimal(t("field.widget_speed"), () -> w.speed, v -> w.speed = v);
            p.showIf(() -> !w.animation.equals("none"));
            p.updateVisibility();
            props.getChildren().add(p);
        }

        private List<String> targets(String action) {
            List<String> r = new ArrayList<>();
            switch (action) {
                case "screen" -> {
                    for (CustomScreen s : c.p.all(CustomScreen.class)) if (!s.overlay() && s != e) r.add(s.name);
                }
                case "procedure" -> {
                    for (Element el : c.p.elements) if (el.type() == ElementType.PROCEDURE) r.add(el.name);
                }
                case "url" -> r.add("https://");
                default -> {
                }
            }
            return r;
        }

        private Spinner<Integer> spinner(int min, int max, int value, Consumer<Integer> set) {
            Spinner<Integer> sp = new Spinner<>(min, max, value);
            sp.setEditable(true);
            sp.setPrefWidth(110);
            sp.valueProperty().addListener((o, a, b) -> {
                if (b == null) return;
                set.accept(b);
                f.changed();
            });
            return sp;
        }

        // -------------------------------------------------------------- canvas

        private int left(ScreenWidget w) {
            return (anchorIndex(w) % 3) * W / 2 + w.x;
        }

        private int top(ScreenWidget w) {
            return (anchorIndex(w) / 3) * H / 2 + w.y;
        }

        private static int anchorIndex(ScreenWidget w) {
            int i = ANCHORS.indexOf(w.anchor);
            return i < 0 ? 4 : i;
        }

        private ScreenWidget hit(double x, double y) {
            for (int i = e.widgets.size() - 1; i >= 0; i--) {
                ScreenWidget w = e.widgets.get(i);
                int l = left(w), tp = top(w);
                if (x >= l && y >= tp && x < l + Math.max(4, w.width) && y < tp + Math.max(4, w.height)) return w;
            }
            return null;
        }

        private void setupCanvas() {
            canvas.setFocusTraversable(true);
            canvas.setOnMousePressed(ev -> {
                canvas.requestFocus();
                ScreenWidget w = hit(ev.getX() / K, ev.getY() / K);
                select(w);
                if (w != null) {
                    dragX = ev.getX();
                    dragY = ev.getY();
                    dragWx = w.x;
                    dragWy = w.y;
                    dragging = false;
                }
            });
            canvas.setOnMouseDragged(ev -> {
                if (selected == null) return;
                int nx = dragWx + (int) Math.round((ev.getX() - dragX) / K);
                int ny = dragWy + (int) Math.round((ev.getY() - dragY) / K);
                if (ev.isShiftDown()) {
                    nx = Math.round(nx / 4.0f) * 4;
                    ny = Math.round(ny / 4.0f) * 4;
                }
                if (nx != selected.x || ny != selected.y) {
                    selected.x = nx;
                    selected.y = ny;
                    dragging = true;
                    draw();
                }
            });
            canvas.setOnMouseReleased(ev -> {
                if (!dragging) return;
                dragging = false;
                rebuildProps();
                f.changed();
            });
            canvas.setOnKeyPressed(ev -> {
                if (selected == null) return;
                int step = ev.isShiftDown() ? 10 : 1;
                KeyCode k = ev.getCode();
                if (k == KeyCode.LEFT) selected.x -= step;
                else if (k == KeyCode.RIGHT) selected.x += step;
                else if (k == KeyCode.UP) selected.y -= step;
                else if (k == KeyCode.DOWN) selected.y += step;
                else if (k == KeyCode.DELETE) {
                    remove(selected);
                    ev.consume();
                    return;
                } else return;
                ev.consume();
                rebuildProps();
                f.changed();
            });
        }

        private double age() {
            return (System.nanoTime() - start) / 1e9;
        }

        private double progress(ScreenWidget w, double t) {
            return Math.min(1, Math.max(0, t * w.speed * 2.5));
        }

        private static double ease(double p) {
            double q = 1 - p;
            return 1 - q * q * q;
        }

        private double wave(ScreenWidget w, double t) {
            return Math.sin(t * w.speed * Math.PI * 2);
        }

        private double fade(ScreenWidget w, double t) {
            if (!play) return 1;
            return switch (w.animation) {
                case "fade_in" -> progress(w, t);
                case "blink" -> 0.35 + 0.65 * (0.5 + 0.5 * wave(w, t));
                case "slide_up", "slide_down", "slide_left", "slide_right", "pop" -> Math.min(1, progress(w, t) * 2);
                default -> 1;
            };
        }

        private double offX(ScreenWidget w, double t) {
            if (!play) return 0;
            double d = (1 - ease(progress(w, t))) * 30;
            return switch (w.animation) {
                case "slide_left" -> Math.round(d);
                case "slide_right" -> -Math.round(d);
                default -> 0;
            };
        }

        private double offY(ScreenWidget w, double t) {
            if (!play) return 0;
            double d = (1 - ease(progress(w, t))) * 20;
            return switch (w.animation) {
                case "slide_up" -> Math.round(d);
                case "slide_down" -> -Math.round(d);
                case "bob" -> Math.round(wave(w, t) * 3);
                default -> 0;
            };
        }

        private double grow(ScreenWidget w, double t) {
            if (!play) return 1;
            return switch (w.animation) {
                case "pop" -> {
                    double q = progress(w, t) - 1;
                    yield 1 + 2.70158 * q * q * q + 1.70158 * q * q;
                }
                case "pulse" -> 1 + 0.06 * wave(w, t);
                default -> 1;
            };
        }

        private static Color color(String hex, Color def) {
            try {
                return Color.web(hex.trim().startsWith("#") ? hex.trim() : "#" + hex.trim());
            } catch (RuntimeException ex) {
                return def;
            }
        }

        private void draw() {
            GraphicsContext g = canvas.getGraphicsContext2D();
            g.setImageSmoothing(false);
            g.save();
            g.scale(K, K);
            g.setFill(Color.web("#86b0ff"));
            g.fillRect(0, 0, W, H * 0.58);
            g.setFill(Color.web("#5d8f3a"));
            g.fillRect(0, H * 0.58, W, H * 0.42);
            g.setFill(Color.web("#4a7330"));
            for (int x = 0; x < W; x += 16) g.fillRect(x, H * 0.58, 8, 3);
            if (e.overlay()) {
                g.setFill(Color.rgb(0, 0, 0, 0.45));
                g.fillRect(W / 2.0 - 91, H - 22, 182, 22);
                g.setStroke(Color.rgb(255, 255, 255, 0.5));
                g.setLineWidth(1);
                for (int i = 0; i < 9; i++) g.strokeRect(W / 2.0 - 91 + 1 + i * 20, H - 21, 20, 20);
                g.setFill(Color.WHITE);
                g.fillRect(W / 2.0 - 0.5, H / 2.0 - 4, 1, 9);
                g.fillRect(W / 2.0 - 4, H / 2.0 - 0.5, 9, 1);
            } else if (e.dim) {
                g.setFill(Color.rgb(16, 16, 16, 0.72));
                g.fillRect(0, 0, W, H);
            }
            g.setStroke(Color.rgb(255, 255, 255, 0.10));
            g.setLineWidth(1 / K);
            g.strokeLine(W / 2.0, 0, W / 2.0, H);
            g.strokeLine(0, H / 2.0, W, H / 2.0);
            double t = age();
            for (ScreenWidget w : e.widgets) {
                try {
                    widget(g, w, t);
                } catch (RuntimeException ex) {
                    // a half-typed value; draw the rest
                }
                g.setGlobalAlpha(1);
            }
            if (selected != null && e.widgets.contains(selected)) {
                ScreenWidget w = selected;
                g.setStroke(Color.web("#4da3ff"));
                g.setLineWidth(1.5 / K);
                g.setLineDashes(3 / K, 2 / K);
                g.strokeRect(left(w) - 0.5, top(w) - 0.5, w.width + 1, w.height + 1);
                g.setLineDashes();
                double ax = (anchorIndex(w) % 3) * W / 2.0, ay = (anchorIndex(w) / 3) * H / 2.0;
                g.setFill(Color.web("#4da3ff"));
                g.fillOval(ax - 2.5, ay - 2.5, 5, 5);
                g.strokeLine(ax, ay, left(w), top(w));
            }
            g.restore();
        }

        private void widget(GraphicsContext g, ScreenWidget w, double t) {
            double a = Math.max(0, Math.min(100, w.opacity)) / 100.0 * fade(w, t);
            if (a < 0.03) return;
            double x = left(w) + offX(w, t), y = top(w) + offY(w, t);
            double k = Math.max(0.1, Math.min(10, w.scale)) * grow(w, t);
            boolean scaled = Math.abs(k - 1) > 0.001 && !w.kind.equals("entity");
            g.save();
            g.setGlobalAlpha(a);
            if (scaled) {
                double cx = x + w.width / 2.0, cy = y + w.height / 2.0;
                g.translate(cx, cy);
                g.scale(k, k);
                g.translate(-cx, -cy);
            }
            Color c1 = color(w.color, Color.WHITE), c2 = color(w.color2, Color.web("#202020"));
            TextureAsset tex = c.p.texture(w.texture);
            switch (w.kind) {
                case "panel" -> {
                    if (tex != null) image(g, tex, x, y, w.width, w.height, t);
                    else {
                        g.setFill(c2);
                        g.fillRect(x, y, w.width, w.height);
                        g.setFill(c1);
                        g.fillRect(x + 1, y + 1, w.width - 2, w.height - 2);
                    }
                }
                case "image" -> {
                    if (tex != null) image(g, tex, x, y, w.width, w.height, t);
                    else missing(g, x, y, w.width, w.height);
                }
                case "text" -> {
                    double ly = y;
                    for (String line : TextPainter.sample(w.text).split("\n", -1)) {
                        double tw = text.width(line);
                        double tx = w.align.equals("center") ? x + (w.width - tw) / 2 : w.align.equals("right") ? x + w.width - tw : x;
                        text.line(g, line, tx, ly, c1, w.shadow);
                        ly += 10;
                    }
                }
                case "button" -> {
                    if (tex != null) image(g, tex, x, y, w.width, w.height, t);
                    else {
                        g.setFill(Color.BLACK);
                        g.fillRect(x, y, w.width, w.height);
                        g.setFill(c2);
                        g.fillRect(x + 1, y + 1, w.width - 2, w.height - 2);
                        g.setFill(Color.rgb(255, 255, 255, 0.18));
                        g.fillRect(x + 1, y + 1, w.width - 2, 1);
                    }
                    String label = TextPainter.sample(w.text);
                    text.line(g, label, x + (w.width - text.width(label)) / 2, y + (w.height - 8) / 2.0, c1, w.shadow);
                }
                case "bar" -> {
                    double[] vm = sampleValue(w);
                    double ratio = vm[1] <= 0 ? 0 : Math.max(0, Math.min(1, vm[0] / vm[1]));
                    g.setFill(c2);
                    g.fillRect(x, y, w.width, w.height);
                    double fw = Math.round((w.width - 2) * ratio);
                    if (fw > 0) {
                        if (tex != null) {
                            Image img = text.frame(tex, frameIndex(tex, t));
                            if (img != null)
                                g.drawImage(img, 0, 0, Math.max(1, img.getWidth() * ratio), img.getHeight(), x + 1, y + 1, fw, w.height - 2);
                        } else {
                            g.setFill(c1);
                            g.fillRect(x + 1, y + 1, fw, w.height - 2);
                        }
                    }
                    if (!w.text.isBlank()) {
                        String label = TextPainter.sample(w.text).replace("{value}", number(vm[0])).replace("{max}", number(vm[1]));
                        text.line(g, label, x + (w.width - text.width(label)) / 2, y + (w.height - 8) / 2.0, Color.WHITE, true);
                    }
                }
                case "item" -> {
                    String id = w.item == null || w.item.isBlank() ? "minecraft:stone" : w.item.trim();
                    Image img = c.ws.itemImage(Ids.full(id), 32);
                    if (img == null) img = c.ws.thumbnails().item(Ids.full(id), 32);
                    double ix = x + (w.width - 16) / 2.0, iy = y + (w.height - 16) / 2.0;
                    if (img != null) g.drawImage(img, ix, iy, 16, 16);
                    else missing(g, ix, iy, 16, 16);
                }
                case "entity" -> figure(g, w, x, y, k);
                default -> {
                }
            }
            g.restore();
        }

        private int frameIndex(TextureAsset tex, double t) {
            int frames = Math.max(1, tex.frames());
            if (frames <= 1 || !play) return 0;
            return (int) (t * 20 / Math.max(1, tex.frameTime)) % frames;
        }

        private void image(GraphicsContext g, TextureAsset tex, double x, double y, double w, double h, double t) {
            Image img = text.frame(tex, frameIndex(tex, t));
            if (img != null) g.drawImage(img, x, y, w, h);
            else missing(g, x, y, w, h);
        }

        private static void missing(GraphicsContext g, double x, double y, double w, double h) {
            g.setFill(Color.web("#000000"));
            g.fillRect(x, y, w, h);
            g.setFill(Color.web("#f800f8"));
            double s = Math.max(2, Math.min(w, h) / 2);
            for (double yy = 0; yy < h; yy += s) {
                for (double xx = 0; xx < w; xx += s) {
                    if (((int) (xx / s) + (int) (yy / s)) % 2 == 0) g.fillRect(x + xx, y + yy, Math.min(s, w - xx), Math.min(s, h - yy));
                }
            }
        }

        /** A blocky figure standing in for the entity (the game draws the real model, turned toward the mouse). */
        private void figure(GraphicsContext g, ScreenWidget w, double x, double y, double k) {
            boolean player = w.entity == null || w.entity.isBlank() || w.entity.trim().equals("player");
            double h = Math.min(w.height, w.width * 2) * Math.min(1.5, k) * 0.9;
            double u = h / 32;
            double cx = x + w.width / 2.0, base = y + w.height - 2;
            Color skin = player ? Color.web("#c69c7b") : Color.web("#6aa84f");
            Color shirt = player ? Color.web("#3fa7b5") : Color.web("#4f7a3a");
            Color pants = player ? Color.web("#3b3f8f") : Color.web("#2f4f2f");
            g.setFill(pants);
            g.fillRect(cx - 4 * u, base - 12 * u, 4 * u, 12 * u);
            g.fillRect(cx, base - 12 * u, 4 * u, 12 * u);
            g.setFill(shirt);
            g.fillRect(cx - 4 * u, base - 24 * u, 8 * u, 12 * u);
            g.setFill(skin);
            g.fillRect(cx - 8 * u, base - 24 * u, 4 * u, 12 * u);
            g.fillRect(cx + 4 * u, base - 24 * u, 4 * u, 12 * u);
            g.fillRect(cx - 4 * u, base - 32 * u, 8 * u, 8 * u);
            g.setFill(Color.web("#2b1d0e"));
            g.fillRect(cx - 4 * u, base - 32 * u, 8 * u, 2 * u);
            g.setFill(Color.WHITE);
            g.fillRect(cx - 3 * u, base - 28 * u, 2 * u, u);
            g.fillRect(cx + u, base - 28 * u, 2 * u, u);
            if (!player) {
                String id = Ids.path(Ids.full(w.entity.trim()));
                text.line(g, id, cx - text.width(id) / 2, base + 1, Color.WHITE, true);
            }
        }

        private static double[] sampleValue(ScreenWidget w) {
            double v, m;
            switch (w.value) {
                case "health" -> {
                    v = 14;
                    m = 20;
                }
                case "food" -> {
                    v = 18;
                    m = 20;
                }
                case "xp" -> {
                    v = 0.45;
                    m = 1;
                }
                case "level" -> {
                    v = 7;
                    m = 100;
                }
                case "air" -> {
                    v = 300;
                    m = 300;
                }
                case "armor" -> {
                    v = 10;
                    m = 20;
                }
                default -> {
                    v = 12;
                    m = 100;
                }
            }
            String max = w.max == null ? "" : w.max.trim();
            if (max.startsWith("score:")) m = 20;
            else if (!max.isEmpty()) {
                try {
                    m = Double.parseDouble(max);
                } catch (NumberFormatException ignored) {
                    // keep the value's own maximum
                }
            }
            return new double[] {v, m};
        }

        private static String number(double d) {
            return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d);
        }
    }

    // ------------------------------------------------------------------ icon font

    static final class IconFontEditor extends EditorBase<Systems.IconFont> {
        private final VBox rows = new VBox(6);
        private final Canvas sample = new Canvas(460, 54);
        private final TextPainter text;
        private String sampleText = "";

        IconFontEditor(Ctx c, Systems.IconFont e) {
            super(c, e);
            text = new TextPainter(c.p);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.icon_font"));
            nameRow();
            f.section(t("section.icons"), t("help.icons"));
            f.add(rows);
            f.add(Ui.button(t("icon.add"), "mdi2p-plus", () -> {
                Systems.Icon ic = new Systems.Icon();
                ic.name = uniqueName();
                e.icons.add(ic);
                rebuild();
                f.changed();
            }));
            rebuild();
            f.section(t("section.icon_preview"), t("help.icon_preview"));
            TextField input = new TextField();
            input.setPromptText(t("icon.sample_prompt"));
            input.textProperty().addListener((o, a, b) -> {
                sampleText = b;
                drawSample();
            });
            f.add(input);
            StackPane holder = new StackPane(sample);
            holder.getStyleClass().add("screen-canvas");
            holder.setMaxWidth(462);
            holder.setAlignment(Pos.TOP_LEFT);
            f.add(holder);
            f.section(t("section.icon_use"));
            f.add(Ui.help(t("help.icon_use")));
        }

        private String uniqueName() {
            var used = icons(c.p).keySet();
            for (int i = 1; ; i++) {
                String n = "icon" + i;
                if (!used.contains(n)) return n;
            }
        }

        @Override
        protected void refresh() {
            text.refresh();
            drawSample();
        }

        private String defaultSample() {
            StringBuilder b = new StringBuilder("&e");
            for (Systems.Icon ic : e.icons) if (ic.name != null && !ic.name.isBlank()) b.append(':').append(ic.name.trim()).append(": ");
            return b.append("&f").append(t("icon.sample_text")).toString();
        }

        private void drawSample() {
            GraphicsContext g = sample.getGraphicsContext2D();
            g.setImageSmoothing(false);
            g.save();
            g.setFill(Color.web("#1e1e1e"));
            g.fillRect(0, 0, sample.getWidth(), sample.getHeight());
            g.scale(3, 3);
            g.setFill(Color.rgb(0, 0, 0, 0.5));
            String s = TextPainter.sample(sampleText.isBlank() ? defaultSample() : sampleText);
            double w = text.width(s);
            g.fillRect(2, 3, Math.min(sample.getWidth() / 3 - 4, w + 4), 12);
            text.line(g, s, 4, 5, Color.WHITE, true);
            g.restore();
        }

        private void rebuild() {
            rows.getChildren().clear();
            for (Systems.Icon ic : new ArrayList<>(e.icons)) {
                TextField name = new TextField(ic.name);
                name.setPrefColumnCount(10);
                name.setPromptText(t("icon.name"));
                Label code = Ui.label(":" + ic.name + ":", "mono", "text-muted");
                name.textProperty().addListener((o, a, b) -> {
                    ic.name = b.trim().toLowerCase(Locale.ROOT);
                    code.setText(":" + ic.name + ":");
                    f.changed();
                });
                TextureField tex = new TextureField(c.ws, c.window(), ic.texture, () -> "icon_" + ic.name, "item", v -> {
                    ic.texture = v;
                    f.changed();
                });
                Spinner<Integer> height = new Spinner<>(1, 256, ic.height);
                height.setEditable(true);
                height.setPrefWidth(84);
                height.valueProperty().addListener((o, a, b) -> {
                    if (b == null) return;
                    ic.height = b;
                    f.changed();
                });
                Spinner<Integer> ascent = new Spinner<>(-256, 256, ic.ascent);
                ascent.setEditable(true);
                ascent.setPrefWidth(84);
                ascent.valueProperty().addListener((o, a, b) -> {
                    if (b == null) return;
                    ic.ascent = b;
                    f.changed();
                });
                Button remove = Ui.iconButton("mdi2c-close", t("action.remove"), () -> {
                    e.icons.remove(ic);
                    rebuild();
                    f.changed();
                });
                VBox nameBox = new VBox(2, name, code);
                HBox sizes = Ui.hbox(6, Ui.label(t("icon.height"), "text-muted"), height, Ui.label(t("icon.ascent"), "text-muted"), ascent);
                sizes.setAlignment(Pos.CENTER_LEFT);
                VBox right = new VBox(6, tex, sizes);
                HBox.setHgrow(right, Priority.ALWAYS);
                HBox row = new HBox(10, nameBox, right, remove);
                row.setAlignment(Pos.CENTER_LEFT);
                for (Region r : List.of(height, ascent)) r.setMinWidth(Region.USE_PREF_SIZE);
                row.getStyleClass().add("list-card");
                rows.getChildren().add(row);
            }
            if (e.icons.isEmpty()) rows.getChildren().add(Ui.label(t("icon.none"), "text-muted"));
        }
    }
}
