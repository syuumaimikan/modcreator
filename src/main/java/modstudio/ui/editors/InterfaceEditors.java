package modstudio.ui.editors;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextFlow;
import javafx.animation.AnimationTimer;
import modstudio.core.Ids;
import modstudio.core.MenuArt;
import modstudio.core.Secrets;
import modstudio.core.Systems.*;
import modstudio.core.Systems.Menu;
import modstudio.core.WebTest;
import modstudio.ui.Ui;
import modstudio.ui.controls.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Editors for inventory menus, HUD elements and web requests. */
final class InterfaceEditors {
    private InterfaceEditors() {}

    // ------------------------------------------------------------------ menu

    static final class MenuEditor extends EditorBase<Menu> {
        private final GridPane grid = new GridPane();
        private final VBox detail = new VBox(8);
        private final Label gridTitle = Ui.label("", "mc-menu-title");
        private final ImageView art = new ImageView();
        private final Label artNote = Ui.label("", "text-muted");
        private int selected = -1;
        /** Game tick of the opening animation preview, or -1 when it is not playing. */
        private int previewTick = -1;
        private AnimationTimer player;

        MenuEditor(Ctx c, Menu e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.menu"));
            nameRow();
            f.text(t("field.menu.title"), () -> e.title, v -> e.title = v);
            f.choice(t("field.menu.layout"), List.of("chest", "hopper", "dispenser"), x -> t("menu.layout." + x), () -> e.layout, v -> {
                e.layout = v;
                select(-1);
            });
            f.integer(t("field.menu.rows"), 1, 6, () -> e.rows, v -> {
                e.rows = v;
                select(-1);
            });
            f.showIf(() -> e.layout.equals("chest"));
            f.id(t("field.menu.filler"), IdField.Kind.ITEM, () -> e.filler, v -> e.filler = v, env(), t("help.menu.filler"));
            f.text(t("field.menu.deny"), () -> e.denyMessage, v -> e.denyMessage = v, t("help.menu.deny"));

            f.section(t("section.menu.slots"), t("help.menu.slots"));
            grid.setHgap(2);
            grid.setVgap(2);
            VBox frame = new VBox(6, gridTitle, grid);
            frame.getStyleClass().add("mc-menu");
            frame.setPadding(new Insets(8, 10, 10, 10));
            frame.setMaxWidth(Region.USE_PREF_SIZE);
            Button play = Ui.button(t("menu.play_animation"), "mdi2p-play", this::playPreview);
            f.add(Ui.hbox(8, frame, new VBox(6, play)));
            f.add(detail);

            f.section(t("section.menu.look"), t("help.menu.look"));
            f.choice(t("field.menu.background"), MenuArt.BACKGROUNDS, x -> t("menu.background." + x), () -> e.background, v -> {
                e.background = v;
                f.updateVisibility();
            });
            f.row(t("field.menu.background_texture"), new TextureField(c.ws, c.window(), e.backgroundTexture, () -> e.name + "_menu",
                    "menu_" + MenuArt.rows(e), v -> {
                e.backgroundTexture = v;
                f.changed();
            }), t("help.menu.background_texture"));
            f.showIf(() -> e.background.equals("texture"));
            GameplayEditors.colorRow(f, t("field.menu.panel_color"), () -> e.panelColor, v -> e.panelColor = v);
            f.showIf(() -> e.background.equals("panel"));
            GameplayEditors.colorRow(f, t("field.menu.frame_color"), () -> e.frameColor, v -> e.frameColor = v);
            f.showIf(() -> e.background.equals("panel"));
            GameplayEditors.colorRow(f, t("field.menu.title_color"), () -> e.titleColor, v -> e.titleColor = v);
            art.setSmooth(false);
            art.setPreserveRatio(true);
            art.setFitWidth(MenuArt.WIDTH * 2);
            f.add(new VBox(6, art, artNote));
            f.choice(t("field.menu.open_animation"), MenuArt.REVEALS, x -> t("menu.reveal." + x), () -> e.openAnimation, v -> {
                e.openAnimation = v;
                f.updateVisibility();
            });
            f.integer(t("field.menu.animation_speed"), 1, 20, () -> e.animationSpeed, v -> e.animationSpeed = v, t("help.menu.animation_speed"));
            f.showIf(() -> !e.openAnimation.equals("none"));
            f.id(t("field.menu.open_sound"), IdField.Kind.SOUND, () -> e.openSound, v -> e.openSound = v, env());
            f.id(t("field.menu.click_sound"), IdField.Kind.SOUND, () -> e.clickSound, v -> e.clickSound = v, env());
            f.section(t("section.menu.platforms"));
            f.add(Ui.help(t("help.menu.platforms", c.ns(), e.name.isBlank() ? "name" : e.name)));
        }

        private MenuSlot slotAt(int pos) {
            for (MenuSlot s : e.slots) if (s.slot == pos) return s;
            return null;
        }

        private int columns() {
            return e.layout.equals("hopper") ? 5 : e.layout.equals("dispenser") ? 3 : 9;
        }

        /** Plays the opening animation and the animated slots in the slot grid for a few seconds. */
        private void playPreview() {
            if (player != null) player.stop();
            int end = revealEnd();
            int stopAt = end + (e.slots.stream().anyMatch(s -> !s.frames.isEmpty()) ? 100 : 10);
            long[] startNs = {-1};
            player = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (startNs[0] < 0) startNs[0] = now;
                    int tick = (int) ((now - startNs[0]) / 50_000_000L);
                    if (grid.getScene() == null || tick > stopAt) {
                        stop();
                        previewTick = -1;
                        refresh();
                        return;
                    }
                    if (tick != previewTick) {
                        previewTick = tick;
                        refresh();
                    }
                }
            };
            player.start();
        }

        private int revealEnd() {
            if (e.openAnimation.equals("none")) return 0;
            int end = 0;
            for (int i = 0; i < e.size(); i++) end = Math.max(end, (MenuArt.order(e.openAnimation, i, e.size(), columns()) + 1) * Math.max(1, e.animationSpeed));
            return end;
        }

        /** Item shown in a slot at the current preview tick, or null while it is still hidden. */
        private String shownItem(int pos, MenuSlot s, String item) {
            if (previewTick < 0) return item;
            if (!e.openAnimation.equals("none")
                    && MenuArt.order(e.openAnimation, pos, e.size(), columns()) * Math.max(1, e.animationSpeed) > previewTick) return null;
            if (s == null || s.frames.isEmpty() || previewTick < revealEnd()) return item;
            List<String> all = new ArrayList<>();
            all.add(item);
            for (String fr : s.frames) if (!fr.isBlank()) all.add(fr);
            return all.get((previewTick / Math.max(1, s.frameTicks)) % all.size());
        }

        @Override
        protected void refresh() {
            int[] px = MenuArt.image(c.p, e);
            art.setImage(px == null ? null : Ui.pixelImage(px, MenuArt.WIDTH, MenuArt.height(e), MenuArt.WIDTH * 2));
            art.setVisible(px != null);
            art.setManaged(px != null);
            artNote.setText(e.background.equals("none") ? "" : !e.layout.equals("chest") ? t("menu.background_chest_only")
                    : px == null ? t("menu.background_missing") : t("menu.background_note"));
            gridTitle.setStyle(e.titleColor.isBlank() ? "" : "-fx-text-fill: " + (e.titleColor.startsWith("#") ? e.titleColor : "#" + e.titleColor) + ";");
            gridTitle.setText(MenuText.plain(e.title.isBlank() ? Ids.titleCase(e.name) : e.title));
            grid.getChildren().clear();
            int cols = columns(), size = e.size();
            for (int i = 0; i < size; i++) grid.add(cell(i), i % cols, i / cols);
            if (selected >= size) select(-1);
        }

        private Node cell(int pos) {
            StackPane cell = new StackPane();
            cell.getStyleClass().add("slot");
            cell.setMinSize(44, 44);
            cell.setPrefSize(44, 44);
            MenuSlot s = slotAt(pos);
            String item = shownItem(pos, s, s != null ? s.item : e.filler);
            if (item != null && !item.isBlank()) {
                Image img = c.ws.itemImage(Ids.full(item), 32);
                if (img != null) {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(32);
                    iv.setFitHeight(32);
                    iv.setSmooth(false);
                    if (s == null) iv.setOpacity(0.35);
                    cell.getChildren().add(iv);
                } else if (s != null) {
                    Label l = new Label(Ids.path(Ids.full(item)).replace('_', ' '));
                    l.getStyleClass().add("slot-text");
                    l.setWrapText(true);
                    l.setMaxWidth(40);
                    cell.getChildren().add(l);
                }
            }
            if (s != null && s.count > 1 && item != null) {
                Label n = new Label(Integer.toString(s.count));
                n.getStyleClass().add("slot-count");
                StackPane.setAlignment(n, Pos.BOTTOM_RIGHT);
                cell.getChildren().add(n);
            }
            if (pos == selected) cell.getStyleClass().add("slot-selected");
            if (s != null) {
                String tip = (s.name.isBlank() ? s.item : MenuText.plain(s.name)) + "\n" + t("menu.action." + s.action);
                Tooltip.install(cell, new Tooltip(tip));
            }
            cell.setOnMouseClicked(ev -> select(pos));
            return cell;
        }

        private void select(int pos) {
            selected = pos;
            detail.getChildren().clear();
            if (pos >= 0) {
                MenuSlot s = slotAt(pos);
                if (s == null) {
                    detail.getChildren().add(Ui.hbox(8, Ui.label(t("menu.slot_empty", pos), "text-muted"),
                            Ui.button(t("menu.add_button"), "mdi2p-plus", () -> {
                                MenuSlot n = new MenuSlot();
                                n.slot = pos;
                                n.name = "&a" + t("menu.new_button");
                                e.slots.add(n);
                                f.changed();
                                select(pos);
                            })));
                } else {
                    detail.getChildren().add(slotForm(s));
                }
            }
            refresh();
        }

        private Node slotForm(MenuSlot s) {
            Form sf = Form.inline(f::changed);
            sf.section(t("menu.slot_title", s.slot));
            sf.id(t("field.item"), IdField.Kind.ITEM, () -> s.item, v -> s.item = v, env());
            sf.integer(t("field.count"), 1, 99, () -> s.count, v -> s.count = v);
            sf.text(t("field.menu.slot_name"), () -> s.name, v -> s.name = v, t("help.color_codes"));
            sf.area(t("field.menu.lore"), () -> s.lore, v -> s.lore = v, 2, null);
            sf.check(t("field.glint"), () -> s.glint, v -> s.glint = v);
            VBox frameList = new VBox(4);
            Runnable[] rebuild = new Runnable[1];
            rebuild[0] = () -> {
                frameList.getChildren().clear();
                for (int i = 0; i < s.frames.size(); i++) {
                    int index = i;
                    IdField id = new IdField(env(), IdField.Kind.ITEM, s.frames.get(i), v -> {
                        s.frames.set(index, v);
                        sf.changed();
                    });
                    HBox.setHgrow(id, Priority.ALWAYS);
                    frameList.getChildren().add(Ui.hbox(6, Ui.label(Integer.toString(i + 1), "text-muted"), id,
                            Ui.iconButton("mdi2c-close", t("action.delete"), () -> {
                                s.frames.remove(index);
                                sf.changed();
                                rebuild[0].run();
                            })));
                }
                frameList.getChildren().add(Ui.button(t("menu.add_frame"), "mdi2p-plus", () -> {
                    s.frames.add(s.frames.isEmpty() ? s.item : s.frames.get(s.frames.size() - 1));
                    sf.changed();
                    rebuild[0].run();
                }));
            };
            rebuild[0].run();
            sf.row(t("field.menu.frames"), frameList, t("help.menu.frames"));
            sf.integer(t("field.menu.frame_ticks"), 1, 200, () -> s.frameTicks, v -> s.frameTicks = v);
            sf.section(t("section.menu.action"));
            List<String> menus = c.p.all(Menu.class).stream().map(m -> m.name).filter(n -> !n.equals(e.name)).collect(Collectors.toList());
            sf.choice(t("field.menu.action"), List.of("none", "run", "command", "menu", "close"), x -> t("menu.action." + x), () -> s.action, v -> s.action = v);
            ComboBox<String> target = new ComboBox<>();
            target.getItems().setAll(menus);
            target.setEditable(true);
            target.setValue(s.target);
            target.valueProperty().addListener((o, a, b) -> {
                s.target = b == null ? "" : b;
                sf.changed();
            });
            sf.row(t("field.menu.target_menu"), target);
            sf.showIf(() -> s.action.equals("menu"));
            sf.text(t("field.menu.command"), () -> s.target, v -> s.target = v, t("help.menu.command"));
            sf.showIf(() -> s.action.equals("command"));
            sf.check(t("field.menu.close_after"), () -> s.closeAfter, v -> s.closeAfter = v);
            sf.showIf(() -> s.action.equals("run") || s.action.equals("command"));
            LogicEditor le = new LogicEditor(env(), s.steps, sf::changed, () -> t("menu.click_start", s.slot));
            sf.add(le);
            sf.showIf(le, () -> s.action.equals("run"));
            sf.section(t("section.menu.rules"));
            sf.text(t("field.menu.permission"), () -> s.permission, v -> s.permission = v, t("help.menu.permission"));
            sf.text(t("field.menu.cost_objective"), () -> s.costObjective, v -> s.costObjective = v, t("help.menu.cost"));
            sf.integer(t("field.menu.cost"), 0, 1_000_000, () -> s.cost, v -> s.cost = v);
            sf.add(Ui.hbox(8, Ui.spacer(), Ui.button(t("action.delete"), "mdi2d-delete-outline", () -> {
                e.slots.remove(s);
                f.changed();
                select(s.slot);
            })));
            sf.updateVisibility();
            sf.getStyleClass().add("card");
            return sf;
        }
    }

    /** Plain text helper shared by previews. */
    static final class MenuText {
        static String plain(String s) {
            return McText.plain(s);
        }
    }

    // ------------------------------------------------------------------ HUD

    static final class HudEditor extends EditorBase<Hud> {
        private final StackPane preview = new StackPane();

        HudEditor(Ctx c, Hud e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            preview.getStyleClass().add("hud-preview");
            preview.setMinHeight(220);
            preview.setPrefHeight(220);
            preview.setMaxWidth(460);
            f.addTop(preview);
            f.section(t("section.general"), t("help.hud"));
            nameRow();
            f.choice(t("field.hud.kind"), List.of("sidebar", "bossbar", "actionbar"), x -> t("hud.kind." + x), () -> e.kind, v -> e.kind = v);
            f.text(t("field.hud.title"), () -> e.title, v -> e.title = v, t("help.color_codes"));
            f.area(t("field.hud.lines"), () -> String.join("\n", e.lines), v -> {
                e.lines.clear();
                if (!v.isEmpty()) e.lines.addAll(List.of(v.split("\n", -1)));
            }, 6, t("help.hud.lines"));
            f.choice(t("field.hud.color"), SystemEditors.BOSS_COLORS, x -> t("choice." + x), () -> e.color, v -> e.color = v);
            f.showIf(() -> e.kind.equals("bossbar"));
            f.choice(t("field.hud.style"), SystemEditors.BOSS_STYLES, x -> t("choice." + x), () -> e.style, v -> e.style = v);
            f.showIf(() -> e.kind.equals("bossbar"));
            f.integer(t("field.hud.refresh"), 1, 200, () -> e.refresh, v -> e.refresh = v, t("help.hud.refresh"));
            f.check(t("field.hud.auto_start"), () -> e.autoStart, v -> e.autoStart = v, t("help.hud.auto_start"));
            f.text(t("field.hud.players"), () -> e.players, v -> e.players = v);
            f.showIf(() -> e.autoStart && !e.kind.equals("sidebar"));

            f.section(t("section.hud.timer"), t("help.hud.timer"));
            f.check(t("field.hud.timer"), () -> e.timer, v -> e.timer = v);
            f.integer(t("field.hud.seconds"), 1, 86_400, () -> e.seconds, v -> e.seconds = v);
            f.showIf(() -> e.timer);
            SystemEditors.logic(this, t("section.hud.on_finish"), t("help.hud.on_finish"), e.onFinish, () -> t("hud.finish_start"));
            f.section(t("section.hud.use"));
            f.add(Ui.help(t("help.hud.use", c.ns(), e.name.isBlank() ? "name" : e.name)));
        }

        private String sample(String text) {
            int secs = Math.max(0, (int) Math.round(e.seconds * 0.6));
            return text.replace("{timer}", Integer.toString(secs))
                    .replace("{min}", Integer.toString(secs / 60))
                    .replace("{sec}", String.format("%02d", secs % 60))
                    .replace("{player}", "Steve")
                    .replaceAll("\\{score:[^}]*\\}", "12");
        }

        @Override
        protected void refresh() {
            preview.getChildren().clear();
            String title = e.title.isBlank() ? Ids.titleCase(e.name) : e.title;
            List<String> lines = e.lines.stream().filter(l -> !l.isBlank()).collect(Collectors.toList());
            String bar = lines.isEmpty() ? title : String.join("  ", lines);
            switch (e.kind) {
                case "sidebar" -> {
                    VBox box = new VBox(1);
                    box.getStyleClass().add("hud-sidebar");
                    TextFlow head = McText.flow(sample(title), Color.WHITE, 12);
                    head.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
                    box.getChildren().add(head);
                    for (String l : e.lines.subList(0, Math.min(15, e.lines.size()))) box.getChildren().add(McText.flow(sample(l.isEmpty() ? " " : l), Color.WHITE, 12));
                    box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                    StackPane.setAlignment(box, Pos.CENTER_RIGHT);
                    StackPane.setMargin(box, new Insets(0, 8, 0, 0));
                    preview.getChildren().add(box);
                }
                case "bossbar" -> {
                    TextFlow name = McText.flow(sample(bar), Color.WHITE, 12);
                    name.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
                    double w = 240;
                    Rectangle back = new Rectangle(w, 6, Color.web("#2a2a2a"));
                    Rectangle front = new Rectangle(w * (e.timer ? 0.6 : 1.0), 6, McText.named(e.color.equals("pink") ? "light_purple" : e.color.equals("purple") ? "dark_purple" : e.color));
                    StackPane barPane = new StackPane(back, front);
                    StackPane.setAlignment(front, Pos.CENTER_LEFT);
                    barPane.setMaxWidth(w);
                    VBox box = new VBox(3, name, barPane);
                    box.setAlignment(Pos.TOP_CENTER);
                    box.setMaxWidth(w);
                    StackPane.setAlignment(box, Pos.TOP_CENTER);
                    StackPane.setMargin(box, new Insets(8, 0, 0, 0));
                    preview.getChildren().add(box);
                }
                default -> {
                    TextFlow text = McText.flow(sample(bar), Color.WHITE, 12);
                    text.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
                    HBox hotbar = new HBox(2);
                    for (int i = 0; i < 9; i++) {
                        Region r = new Region();
                        r.getStyleClass().add("hud-hotbar-slot");
                        r.setPrefSize(20, 20);
                        hotbar.getChildren().add(r);
                    }
                    hotbar.setAlignment(Pos.CENTER);
                    VBox box = new VBox(10, text, hotbar);
                    box.setAlignment(Pos.BOTTOM_CENTER);
                    StackPane.setAlignment(box, Pos.BOTTOM_CENTER);
                    StackPane.setMargin(box, new Insets(0, 0, 8, 0));
                    box.setMaxHeight(Region.USE_PREF_SIZE);
                    preview.getChildren().add(box);
                }
            }
        }
    }

    // ------------------------------------------------------------------ web request

    static final class ApiRequestEditor extends EditorBase<ApiRequest> {
        private final Label cronInfo = Ui.help("");
        private final Label secretInfo = Ui.help("");
        private final Label status = Ui.label("", "web-status");
        private final VBox extracted = new VBox(4);
        private final TextArea body = new TextArea();
        private final TextField testPlayer = new TextField("Notch");
        private final TextField testUuid = new TextField("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        private final Label urlPreview = Ui.label("", "mono-small", "text-muted");

        ApiRequestEditor(Ctx c, ApiRequest e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.api"));
            nameRow();
            f.choice(t("field.api.method"), List.of("GET", "POST", "PUT", "PATCH", "DELETE"), x -> x, () -> e.method, v -> e.method = v);
            f.text(t("field.api.url"), () -> e.url, v -> e.url = v, t("help.api.url"));
            f.add(urlPreview);
            f.area(t("field.api.headers"), () -> e.headers, v -> e.headers = v, 3, t("help.api.headers"));
            f.area(t("field.api.body"), () -> e.body, v -> e.body = v, 5, t("help.api.body"));
            f.showIf(() -> !e.method.equals("GET") && !e.method.equals("DELETE"));
            f.integer(t("field.api.timeout"), 1, 120, () -> e.timeout, v -> e.timeout = v);
            f.integer(t("field.api.retries"), 0, 5, () -> e.retries, v -> e.retries = v, t("help.api.retries"));
            f.add(secretInfo);
            f.add(Ui.hbox(8, Ui.button(t("api.open_secrets"), "mdi2k-key-outline", () -> c.view.show("secrets"))));

            f.section(t("section.api.extract"), t("help.api.extract"));
            f.add(new ListEditor<>(e.extract, Extract::new, (x, i) -> x.key.isBlank() ? t("api.extract_n", i + 1) : x.key + "  <-  " + x.path, (x, xf) -> {
                xf.text(t("field.api.path"), () -> x.path, v -> x.path = v, t("help.api.path"));
                xf.text(t("field.api.key"), () -> x.key, v -> x.key = v, t("help.api.key"));
                xf.check(t("field.api.to_score"), () -> x.toScore, v -> x.toScore = v, t("help.api.to_score"));
            }, f::changed, t("api.add_extract")));

            f.section(t("section.api.schedule"), t("help.api.schedule"));
            f.choice(t("field.api.schedule"), List.of("manual", "interval", "cron"), x -> t("api.schedule." + x), () -> e.schedule, v -> e.schedule = v);
            f.integer(t("field.api.interval"), 5, 86_400, () -> e.intervalSeconds, v -> e.intervalSeconds = v);
            f.showIf(() -> e.schedule.equals("interval"));
            f.text(t("field.api.cron"), () -> e.cron, v -> e.cron = v, t("help.api.cron"));
            f.showIf(() -> e.schedule.equals("cron"));
            f.add(cronInfo);
            f.showIf(cronInfo, () -> e.schedule.equals("cron"));

            f.section(t("section.api.test"), t("help.api.test"));
            testPlayer.setPrefColumnCount(10);
            testUuid.setPrefColumnCount(24);
            f.add(Ui.hbox(8, Ui.label(t("api.test_player")), testPlayer, Ui.label(t("api.test_uuid")), testUuid));
            Button send = Ui.button(t("api.send_test"), "mdi2s-send-outline", null);
            send.getStyleClass().add("accent");
            send.setOnAction(ev -> sendTest(send));
            f.add(Ui.hbox(8, send, status));
            f.add(extracted);
            body.setEditable(false);
            body.setPrefRowCount(10);
            body.getStyleClass().add("mono");
            body.setVisible(false);
            body.setManaged(false);
            f.add(body);

            SystemEditors.logic(this, t("section.api.on_success"), t("help.api.on_success", c.ns(), e.name.isBlank() ? "name" : e.name), e.onSuccess, () -> t("api.start.success"));
            SystemEditors.logic(this, t("section.api.on_client_error"), t("help.api.on_client_error"), e.onClientError, () -> t("api.start.client_error"));
            SystemEditors.logic(this, t("section.api.on_server_error"), t("help.api.on_server_error"), e.onServerError, () -> t("api.start.server_error"));
            SystemEditors.logic(this, t("section.api.on_failure"), t("help.api.on_failure"), e.onFailure, () -> t("api.start.failure"));
        }

        @Override
        protected void refresh() {
            Map<String, String> vars = WebTest.sampleVars(testPlayer.getText(), testUuid.getText());
            urlPreview.setText(WebTest.fill(e.url, vars, null, true, 'u'));
            if (e.schedule.equals("cron")) {
                if (!WebTest.cronValid(e.cron)) cronInfo.setText(t("api.cron_invalid"));
                else {
                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    String next = WebTest.nextRuns(e.cron, LocalDateTime.now(), 3).stream().map(fmt::format).collect(Collectors.joining(",  "));
                    cronInfo.setText(t("api.cron_next", next.isEmpty() ? "-" : next));
                }
            }
            List<String> names = new ArrayList<>();
            java.util.regex.Matcher m = Secrets.REF.matcher(e.url + "\n" + e.headers + "\n" + e.body);
            while (m.find()) if (!names.contains(m.group(1))) names.add(m.group(1));
            if (names.isEmpty()) secretInfo.setText(t("api.no_secrets"));
            else {
                Secrets s = new Secrets(c.ws.dir());
                String list = names.stream().map(n -> n + (s.get(n).isEmpty() ? " (" + t("secrets.missing") + ")" : " (" + t("secrets.set") + ")"))
                        .collect(Collectors.joining(", "));
                secretInfo.setText(t("api.secrets_used", list));
            }
        }

        private void sendTest(Button send) {
            send.setDisable(true);
            status.setText(t("api.sending"));
            status.setStyle("");
            extracted.getChildren().clear();
            Secrets secrets = new Secrets(c.ws.dir());
            Map<String, String> vars = WebTest.sampleVars(testPlayer.getText().trim(), testUuid.getText().trim());
            Thread th = new Thread(() -> {
                WebTest.Result r = WebTest.send(e, secrets, vars);
                Platform.runLater(() -> show(r, send));
            }, "web-test");
            th.setDaemon(true);
            th.start();
        }

        private void show(WebTest.Result r, Button send) {
            send.setDisable(false);
            if (r.status() < 0) {
                status.setText(t("api.test_failed", r.error()));
                status.setStyle("-fx-text-fill: #e5534b;");
            } else {
                String outcome = r.status() >= 500 ? "server_error" : r.status() >= 400 ? "client_error" : "success";
                status.setText(t("api.test_result", r.status(), r.millis(), t("api.outcome." + outcome)));
                status.setStyle(outcome.equals("success") ? "-fx-text-fill: #57ab5a;" : "-fx-text-fill: #d29922;");
            }
            for (var en : r.extracted().entrySet()) {
                Label k = Ui.label(en.getKey(), "mono-small");
                k.setMinWidth(140);
                Label v = Ui.label(en.getValue().isEmpty() ? t("api.not_found") : en.getValue(), "mono-small");
                extracted.getChildren().add(Ui.hbox(8, k, v));
            }
            String text = r.body();
            try {
                if (!text.isBlank()) text = new GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(text));
            } catch (RuntimeException ignored) {
            }
            body.setText(text.length() > 200_000 ? text.substring(0, 200_000) : text);
            body.setVisible(!text.isEmpty());
            body.setManaged(!text.isEmpty());
        }
    }
}
