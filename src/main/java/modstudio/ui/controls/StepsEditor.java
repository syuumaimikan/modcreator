package modstudio.ui.controls;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import modstudio.core.Elements.Condition;
import modstudio.core.Elements.Step;
import modstudio.core.Logic;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;

import java.util.*;

/** Visual editor for a list of procedure steps, including nested if/else and repeat blocks. */
public final class StepsEditor extends VBox {
    private final IdField.Env env;
    private final List<Step> steps;
    private final Runnable onChange;
    private final int depth;

    private static final Map<String, String> CATEGORY_ICONS = new LinkedHashMap<>();
    static {
        CATEGORY_ICONS.put("chat", "mdi2m-message-text-outline");
        CATEGORY_ICONS.put("items", "mdi2d-diamond-stone");
        CATEGORY_ICONS.put("entity", "mdi2a-account-outline");
        CATEGORY_ICONS.put("world", "mdi2e-earth");
        CATEGORY_ICONS.put("data", "mdi2c-counter");
        CATEGORY_ICONS.put("logic", "mdi2s-source-branch");
        CATEGORY_ICONS.put("interface", "mdi2v-view-dashboard-outline");
        CATEGORY_ICONS.put("web", "mdi2w-web");
        CATEGORY_ICONS.put("machine", "mdi2c-cog-outline");
    }

    public StepsEditor(IdField.Env env, List<Step> steps, Runnable onChange, int depth) {
        super(8);
        this.env = env;
        this.steps = steps;
        this.onChange = onChange;
        this.depth = depth;
        getStyleClass().add("steps-editor");
        rebuild();
    }

    private void rebuild() {
        getChildren().clear();
        if (steps.isEmpty()) {
            Label l = Ui.help(I18n.t("steps.empty"));
            l.setPadding(new Insets(4, 0, 4, 2));
            getChildren().add(l);
        }
        for (int i = 0; i < steps.size(); i++) getChildren().add(card(steps.get(i), i));
        getChildren().add(addButton());
    }

    private void changed() {
        onChange.run();
    }

    private MenuButton addButton() {
        MenuButton mb = new MenuButton(I18n.t("steps.add"));
        mb.setGraphic(Ui.icon("mdi2p-plus"));
        mb.getStyleClass().add(depth == 0 ? "accent" : "flat");
        mb.getItems().addAll(stepMenus(kind -> {
            steps.add(Logic.newStep(kind));
            rebuild();
            changed();
        }));
        return mb;
    }

    /** Icon of a step category. */
    public static String categoryIcon(String category) {
        return CATEGORY_ICONS.getOrDefault(category, "mdi2c-circle-small");
    }

    /** Category menus listing every step; picking one calls the consumer with the step kind. */
    public static List<Menu> stepMenus(java.util.function.Consumer<String> pick) {
        Map<String, Menu> menus = new LinkedHashMap<>();
        for (Logic.Def d : Logic.STEPS.values()) {
            Menu m = menus.computeIfAbsent(d.category(), c -> {
                Menu menu = new Menu(I18n.t("stepcat." + c));
                menu.setGraphic(Ui.icon(categoryIcon(c)));
                return menu;
            });
            MenuItem mi = new MenuItem(I18n.t("step." + d.id()));
            mi.setOnAction(e -> pick.accept(d.id()));
            m.getItems().add(mi);
        }
        return new ArrayList<>(menus.values());
    }


    private Node card(Step s, int index) {
        Logic.Def def = Logic.STEPS.get(s.kind);
        String cat = def == null ? "logic" : def.category();
        Label title = Ui.label(I18n.tOr("step." + s.kind, s.kind), "step-title");
        title.setGraphic(Ui.icon(CATEGORY_ICONS.getOrDefault(cat, "mdi2c-circle-small")));
        Button up = Ui.iconButton("mdi2c-chevron-up", I18n.t("action.move_up"), () -> move(index, -1));
        Button down = Ui.iconButton("mdi2c-chevron-down", I18n.t("action.move_down"), () -> move(index, 1));
        Button dup = Ui.iconButton("mdi2c-content-copy", I18n.t("action.duplicate"), () -> {
            steps.add(index + 1, copy(s));
            rebuild();
            changed();
        });
        Button del = Ui.iconButton("mdi2c-close", I18n.t("action.delete"), () -> {
            steps.remove(index);
            rebuild();
            changed();
        });
        up.setDisable(index == 0);
        down.setDisable(index == steps.size() - 1);
        HBox header = Ui.hbox(4, title, Ui.spacer(), up, down, dup, del);

        VBox card = new VBox(8, header);
        card.getStyleClass().addAll("step-card", "step-" + cat);

        if (def != null && !def.params().isEmpty()) card.getChildren().add(params(def.params(), s.p));

        if (s.kind.equals("if")) {
            if (s.cond == null) s.cond = Logic.newCondition("chance");
            card.getChildren().add(conditionEditor(s));
            card.getChildren().add(branch(I18n.t("steps.then"), s.then));
            card.getChildren().add(branch(I18n.t("steps.else"), s.orElse));
        } else if (Logic.hasBody(s.kind)) {
            card.getChildren().add(branch(I18n.t("steps.body"), s.then));
        }
        return card;
    }

    private Node branch(String label, List<Step> list) {
        StepsEditor nested = new StepsEditor(env, list, onChange, depth + 1);
        VBox box = new VBox(6, Ui.label(label, "branch-label"), nested);
        box.getStyleClass().add("branch");
        box.setPadding(new Insets(4, 0, 0, 14));
        return box;
    }

    private Node conditionEditor(Step s) {
        return conditionFields(env, s, this::changed);
    }

    /** Condition kind, negate flag and parameters of an "if" step. */
    public static Node conditionFields(IdField.Env env, Step s, Runnable changed) {
        if (s.cond == null) s.cond = Logic.newCondition("chance");
        Condition c = s.cond;
        ComboBox<String> kind = Ui.combo(new ArrayList<>(Logic.CONDITIONS.keySet()), k -> I18n.tOr("cond." + k, k));
        kind.setValue(c.kind);
        CheckBox negate = new CheckBox(I18n.t("steps.negate"));
        negate.setSelected(c.negate);
        negate.selectedProperty().addListener((o, a, b) -> {
            c.negate = b;
            changed.run();
        });
        VBox paramsBox = new VBox();
        Runnable fill = () -> {
            Logic.Def d = Logic.CONDITIONS.get(c.kind);
            paramsBox.getChildren().clear();
            if (d != null && !d.params().isEmpty()) paramsBox.getChildren().add(paramFields(env, d.params(), c.p, changed));
        };
        kind.valueProperty().addListener((o, a, b) -> {
            if (b == null || b.equals(c.kind)) return;
            Condition n = Logic.newCondition(b);
            c.kind = n.kind;
            c.p = n.p;
            fill.run();
            changed.run();
        });
        fill.run();
        HBox row = Ui.hbox(10, Ui.label(I18n.t("steps.if"), "branch-label"), kind, negate);
        VBox box = new VBox(6, row, paramsBox);
        box.getStyleClass().add("condition-box");
        box.setPadding(new Insets(8));
        return box;
    }

    private Node params(List<Logic.Param> params, Map<String, String> values) {
        return paramFields(env, params, values, this::changed);
    }

    /** Parameter fields laid out in a wrapping row. */
    public static Node paramFields(IdField.Env env, List<Logic.Param> params, Map<String, String> values, Runnable changed) {
        FlowPane fp = new FlowPane(12, 8);
        for (Logic.Param p : params) {
            Node field = field(env, p, values, changed);
            Label l = Ui.label(I18n.tOr("param." + p.key(), p.key()), "param-label");
            VBox box = new VBox(3, l, field);
            fp.getChildren().add(box);
        }
        fp.setAlignment(Pos.TOP_LEFT);
        return fp;
    }

    private static Node field(IdField.Env env, Logic.Param p, Map<String, String> values, Runnable changed) {
        String v = values.getOrDefault(p.key(), p.def());
        switch (p.type()) {
            case BOOL -> {
                CheckBox cb = new CheckBox();
                cb.setSelected(Boolean.parseBoolean(v));
                cb.selectedProperty().addListener((o, a, b) -> {
                    values.put(p.key(), String.valueOf(b));
                    changed.run();
                });
                return cb;
            }
            case CHOICE -> {
                ComboBox<String> c = Ui.combo(p.choices(), x -> I18n.tOr("choice." + x, x));
                c.setValue(v);
                c.valueProperty().addListener((o, a, b) -> {
                    if (b == null) return;
                    values.put(p.key(), b);
                    changed.run();
                });
                return c;
            }
            case SELECTOR -> {
                ComboBox<String> c = new ComboBox<>();
                c.setEditable(true);
                c.getItems().addAll("@s", "@p", "@a", "@r", "@e", "@e[type=minecraft:zombie,distance=..10]");
                c.setValue(v);
                c.setPrefWidth(150);
                c.getEditor().textProperty().addListener((o, a, b) -> {
                    if (b == null || b.equals(values.get(p.key()))) return;
                    values.put(p.key(), b);
                    changed.run();
                });
                c.setTooltip(new Tooltip(I18n.t("steps.selector_help")));
                return c;
            }
            case REF -> {
                ComboBox<String> c = new ComboBox<>();
                c.setEditable(true);
                modstudio.core.ElementType et = p.choices().isEmpty() ? null : modstudio.core.ElementType.of(p.choices().get(0));
                if (et != null && env != null && env.ws() != null) {
                    for (var el : env.ws().project.elements) if (el.type() == et) c.getItems().add(el.name);
                }
                if ((v == null || v.isBlank()) && !c.getItems().isEmpty()) {
                    v = c.getItems().get(0);
                    values.put(p.key(), v);
                }
                c.setValue(v);
                c.setPrefWidth(200);
                c.getEditor().textProperty().addListener((o, a, b) -> {
                    if (b == null || b.equals(values.get(p.key()))) return;
                    values.put(p.key(), b);
                    changed.run();
                });
                c.valueProperty().addListener((o, a, b) -> {
                    if (b == null || b.equals(values.get(p.key()))) return;
                    values.put(p.key(), b);
                    changed.run();
                });
                return c;
            }
            case ITEM, BLOCK, ENTITY, EFFECT, SOUND, PARTICLE, FUNCTION, DIMENSION -> {
                IdField f = new IdField(env, Catalog.forParam(p.type()), v, s -> {
                    values.put(p.key(), s);
                    changed.run();
                });
                f.setPrefWidth(300);
                return f;
            }
            default -> {
                TextField tf = new TextField(v);
                tf.setPrefColumnCount(switch (p.type()) {
                    case INT, DECIMAL -> 5;
                    case COORD -> 5;
                    case LONGTEXT -> 34;
                    default -> 18;
                });
                tf.textProperty().addListener((o, a, b) -> {
                    values.put(p.key(), b);
                    if (p.type() == Logic.PType.INT) Ui.setDanger(tf, !b.trim().matches("-?\\d+"));
                    if (p.type() == Logic.PType.DECIMAL) Ui.setDanger(tf, !b.trim().matches("-?\\d+(\\.\\d+)?"));
                    changed.run();
                });
                return tf;
            }
        }
    }

    private void move(int i, int d) {
        int j = i + d;
        if (j < 0 || j >= steps.size()) return;
        Collections.swap(steps, i, j);
        rebuild();
        changed();
    }

    public static Step copy(Step s) {
        Step c = new Step();
        c.kind = s.kind;
        c.p = new LinkedHashMap<>(s.p);
        if (s.cond != null) {
            c.cond = new Condition();
            c.cond.kind = s.cond.kind;
            c.cond.negate = s.cond.negate;
            c.cond.p = new LinkedHashMap<>(s.cond.p);
        }
        for (Step t : s.then) c.then.add(copy(t));
        for (Step t : s.orElse) c.orElse.add(copy(t));
        return c;
    }
}
