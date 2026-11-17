package modstudio.ui.editors;

import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import modstudio.core.Elements.ItemModifier;
import modstudio.core.Elements.ModifierFunction;
import modstudio.core.Elements.Predicate;
import modstudio.core.Elements.PredicateTerm;
import modstudio.core.Ids;
import modstudio.core.Logic;
import modstudio.core.Systems.Dialog;
import modstudio.core.Systems.DialogButton;
import modstudio.core.Systems.Trade;
import modstudio.core.Systems;
import modstudio.core.Systems.Trader;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;
import modstudio.ui.controls.Form;
import modstudio.ui.controls.IdField;
import modstudio.ui.controls.ListEditor;
import modstudio.ui.controls.LogicEditor;
import modstudio.ui.controls.StepsEditor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Editors for predicates, item modifiers, dialogs and traders. */
final class DataEditors {
    private DataEditors() {}

    /**
     * A kind picker followed by the value fields of the chosen kind; switching the kind resets the values to the
     * defaults of the new kind.
     */
    static void kindParams(EditorBase<?> ed, Form xf, Map<String, List<Logic.Param>> defs, String prefix,
                           Supplier<String> get, Consumer<String> set, Map<String, String> values) {
        VBox holder = new VBox();
        Runnable rebuild = () -> holder.getChildren().setAll(
                StepsEditor.paramFields(ed.env(), defs.getOrDefault(get.get(), List.of()), values, xf::changed));
        Logic.defaults(defs.get(get.get()), values);
        xf.choice(EditorBase.t("field.kind"), new ArrayList<>(defs.keySet()), k -> EditorBase.t(prefix + k), get, v -> {
            if (v.equals(get.get())) return;
            set.accept(v);
            values.clear();
            Logic.defaults(defs.get(v), values);
            rebuild.run();
        });
        xf.add(holder);
        rebuild.run();
    }

    /** Text without &amp; colour codes, for previews. */
    static String plain(String s) {
        return s == null ? "" : s.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
    }

    // ------------------------------------------------------------------ predicate

    static final class PredicateEditor extends EditorBase<Predicate> {
        PredicateEditor(Ctx c, Predicate e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.predicate"));
            nameRow();
            f.choice(t("field.predicate.combine"), List.of("all", "any"), x -> t("predicate.combine." + x), () -> e.combine, v -> e.combine = v);
            f.section(t("section.predicate.terms"), t("help.predicate.terms"));
            f.add(new ListEditor<>(e.terms, () -> {
                PredicateTerm x = new PredicateTerm();
                Logic.defaults(Logic.PREDICATE_TERMS.get(x.kind), x.p);
                return x;
            }, (x, i) -> (i + 1) + ". " + (x.invert ? t("predicate.not") + " " : "") + t("pterm." + x.kind), (x, xf) -> {
                kindParams(this, xf, Logic.PREDICATE_TERMS, "pterm.", () -> x.kind, v -> x.kind = v, x.p);
                xf.check(t("field.predicate.invert"), () -> x.invert, v -> x.invert = v);
            }, f::changed, t("predicate.add_term")));
            f.section(t("section.usage"));
            f.add(Ui.help(t("help.predicate.use", fullId())));
        }
    }

    // ------------------------------------------------------------------ item modifier

    static final class ItemModifierEditor extends EditorBase<ItemModifier> {
        ItemModifierEditor(Ctx c, ItemModifier e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.item_modifier"));
            nameRow();
            f.section(t("section.item_modifier.functions"), t("help.item_modifier.functions"));
            f.add(new ListEditor<>(e.functions, () -> {
                ModifierFunction x = new ModifierFunction();
                Logic.defaults(Logic.MODIFIER_FUNCTIONS.get(x.kind), x.p);
                return x;
            }, (x, i) -> (i + 1) + ". " + t("mfn." + x.kind), (x, xf) ->
                    kindParams(this, xf, Logic.MODIFIER_FUNCTIONS, "mfn.", () -> x.kind, v -> x.kind = v, x.p),
                    f::changed, t("item_modifier.add_function")));
            f.section(t("section.usage"));
            f.add(Ui.help(t("help.item_modifier.use", fullId())));
        }
    }

    // ------------------------------------------------------------------ dialog

    static final class DialogEditor extends EditorBase<Dialog> {
        private final VBox preview = new VBox(10);

        DialogEditor(Ctx c, Dialog e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            preview.getStyleClass().add("mc-dialog");
            preview.setAlignment(Pos.TOP_CENTER);
            preview.setMaxWidth(460);
            HBox strip = new HBox(preview);
            strip.getStyleClass().add("preview-strip");
            f.addTop(strip);

            f.section(t("section.general"), t("help.dialog"));
            nameRow();
            f.choice(t("field.dialog.kind"), List.of("notice", "confirmation", "multi_action"), x -> t("dialog.kind." + x), () -> e.kind, v -> e.kind = v);
            f.text(t("field.dialog.title"), () -> e.title, v -> e.title = v, t("help.dialog.text"));
            f.area(t("field.dialog.body"), () -> e.body, v -> e.body = v, 5, t("help.dialog.body"));
            f.integer(t("field.dialog.columns"), 1, 6, () -> e.columns, v -> e.columns = v);
            f.showIf(() -> e.kind.equals("multi_action"));
            f.check(t("field.dialog.can_close"), () -> e.canClose, v -> e.canClose = v);
            f.check(t("field.dialog.pause"), () -> e.pause, v -> e.pause = v, t("help.dialog.pause"));
            f.id(t("field.dialog.open_sound"), IdField.Kind.SOUND, () -> e.openSound, v -> e.openSound = v, env());

            f.section(t("section.dialog.buttons"), t("help.dialog.buttons"));
            f.add(new ListEditor<>(e.buttons, DialogButton::new, (x, i) -> buttonTitle(x, i), (x, xf) -> {
                xf.text(t("field.dialog.label"), () -> x.label, v -> x.label = v);
                xf.text(t("field.dialog.tooltip"), () -> x.tooltip, v -> x.tooltip = v);
                xf.choice(t("field.dialog.action"), List.of("close", "run", "dialog", "url", "suggest", "copy"), a -> t("dialog.action." + a),
                        () -> x.action, v -> x.action = v);
                ComboBox<String> other = new ComboBox<>();
                other.setEditable(true);
                for (var d : c.p.all(Dialog.class)) if (!d.name.equals(e.name)) other.getItems().add(d.name);
                other.setValue(x.target);
                other.valueProperty().addListener((o, a, b) -> {
                    x.target = b == null ? "" : b;
                    xf.changed();
                });
                xf.row(t("field.dialog.target_dialog"), other);
                xf.showIf(() -> x.action.equals("dialog"));
                xf.text(t("field.dialog.target"), () -> x.target, v -> x.target = v, t("help.dialog.target"));
                xf.showIf(() -> List.of("url", "suggest", "copy").contains(x.action));
                xf.integer(t("field.dialog.width"), 20, 1024, () -> x.width, v -> x.width = v);
                LogicEditor le = new LogicEditor(env(), x.steps, xf::changed, () -> t("dialog.click_start", plain(x.label)));
                xf.add(le);
                xf.showIf(le, () -> x.action.equals("run"));
            }, f::changed, t("dialog.add_button")));
            f.section(t("section.usage"));
            f.add(Ui.help(t("help.dialog.use", c.ns(), e.name)));
        }

        private String buttonTitle(DialogButton x, int i) {
            String role = switch (e.kind) {
                case "notice" -> i == 0 ? t("dialog.role.button") : t("dialog.role.unused");
                case "confirmation" -> i == 0 ? t("dialog.role.yes") : i == 1 ? t("dialog.role.no") : t("dialog.role.unused");
                default -> String.valueOf(i + 1);
            };
            return role + "  " + plain(x.label) + "  (" + t("dialog.action." + x.action) + ")";
        }

        @Override
        protected void refresh() {
            preview.getChildren().clear();
            Label title = Ui.label(plain(e.title.isBlank() ? Ids.titleCase(e.name) : e.title), "mc-dialog-title");
            preview.getChildren().add(title);
            if (!e.body.isBlank()) {
                Label body = Ui.label(plain(e.body), "mc-dialog-body");
                body.setWrapText(true);
                body.setMaxWidth(420);
                preview.getChildren().add(body);
            }
            GridPane grid = new GridPane();
            grid.setHgap(6);
            grid.setVgap(6);
            grid.setAlignment(Pos.CENTER);
            List<DialogButton> buttons = e.effectiveButtons();
            int cols = e.kind.equals("multi_action") ? Math.max(1, e.columns) : 2;
            for (int i = 0; i < buttons.size(); i++) {
                DialogButton b = buttons.get(i);
                Label l = Ui.label(plain(b.label.isBlank() ? "OK" : b.label), "mc-button");
                l.setPrefWidth(Math.max(40, Math.min(300, b.width * 0.9)));
                l.setMaxWidth(Double.MAX_VALUE);
                if (b.action.equals("close")) l.getStyleClass().add("mc-button-off");
                if (!b.tooltip.isBlank()) l.setTooltip(new javafx.scene.control.Tooltip(plain(b.tooltip)));
                grid.add(l, i % cols, i / cols);
            }
            preview.getChildren().add(grid);
        }
    }

    // ------------------------------------------------------------------ trader

    static final class TraderEditor extends EditorBase<Trader> {
        TraderEditor(Ctx c, Trader e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.trader"));
            nameRow();
            f.choice(t("field.trader.entity"), List.of("villager", "wandering_trader"), x -> t("trader.entity." + x), () -> e.entity, v -> e.entity = v);
            f.choice(t("field.trader.profession"), Trader.PROFESSIONS, x -> t("profession." + x), () -> e.profession, v -> e.profession = v);
            f.showIf(() -> e.entity.equals("villager"));
            f.choice(t("field.trader.villager_type"), Trader.VILLAGER_TYPES, x -> t("villager_type." + x), () -> e.villagerType, v -> e.villagerType = v);
            f.showIf(() -> e.entity.equals("villager"));
            f.integer(t("field.trader.level"), 1, 5, () -> e.level, v -> e.level = v, t("help.trader.level"));
            f.showIf(() -> e.entity.equals("villager"));
            f.text(t("field.trader.display_name"), () -> e.displayName, v -> e.displayName = v, t("help.dialog.text"));
            f.choice(t("field.trader.name_color"), List.of(Logic.COLORS), x -> t("choice." + x), () -> e.nameColor, v -> e.nameColor = v);
            f.check(t("field.trader.no_ai"), () -> e.noAi, v -> e.noAi = v, t("help.trader.no_ai"));
            f.check(t("field.trader.invulnerable"), () -> e.invulnerable, v -> e.invulnerable = v);
            f.check(t("field.trader.silent"), () -> e.silent, v -> e.silent = v);

            f.section(t("section.trader.trades"), t("help.trader.trades"));
            f.add(new ListEditor<>(e.trades, Trade::new, (x, i) -> tradeTitle(x), (x, xf) -> {
                xf.id(t("field.trader.buy"), IdField.Kind.ITEM, () -> x.buy, v -> x.buy = v, env());
                xf.integer(t("field.count"), 1, 99, () -> x.buyCount, v -> x.buyCount = v);
                xf.id(t("field.trader.buy_b"), IdField.Kind.ITEM, () -> x.buyB, v -> x.buyB = v, env(), t("help.trader.buy_b"));
                xf.integer(t("field.count"), 1, 99, () -> x.buyBCount, v -> x.buyBCount = v);
                xf.id(t("field.trader.sell"), IdField.Kind.ITEM, () -> x.sell, v -> x.sell = v, env());
                xf.integer(t("field.count"), 1, 99, () -> x.sellCount, v -> x.sellCount = v);
                xf.integer(t("field.trader.max_uses"), 1, 100_000, () -> x.maxUses, v -> x.maxUses = v, t("help.trader.max_uses"));
                xf.integer(t("field.trader.xp"), 0, 1000, () -> x.xp, v -> x.xp = v, t("help.trader.xp"));
                xf.decimal(t("field.trader.price_multiplier"), () -> x.priceMultiplier, v -> x.priceMultiplier = Math.max(0, v), t("help.trader.price_multiplier"));
                xf.check(t("field.trader.reward_exp"), () -> x.rewardExp, v -> x.rewardExp = v);
            }, f::changed, t("trader.add_trade")));
            f.section(t("section.usage"));
            f.add(Ui.help(t("help.trader.use", c.ns(), e.name)));
        }

        private static String tradeTitle(Trade x) {
            StringBuilder sb = new StringBuilder();
            sb.append(x.buyCount).append(' ').append(Ids.path(Ids.full(x.buy)));
            if (!x.buyB.isBlank()) sb.append(" + ").append(x.buyBCount).append(' ').append(Ids.path(Ids.full(x.buyB)));
            sb.append("  ->  ").append(x.sellCount).append(' ').append(Ids.path(Ids.full(x.sell)));
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------ key binding

    static final List<String> KEYS = keys();

    private static List<String> keys() {
        List<String> r = new java.util.ArrayList<>();
        for (char ch = 'a'; ch <= 'z'; ch++) r.add("key.keyboard." + ch);
        for (char ch = '0'; ch <= '9'; ch++) r.add("key.keyboard." + ch);
        for (int i = 1; i <= 12; i++) r.add("key.keyboard.f" + i);
        for (int i = 0; i <= 9; i++) r.add("key.keyboard.keypad." + i);
        r.addAll(List.of("key.keyboard.left.alt", "key.keyboard.right.alt", "key.keyboard.left.control", "key.keyboard.right.control",
                "key.keyboard.tab", "key.keyboard.caps.lock", "key.keyboard.grave.accent", "key.keyboard.minus", "key.keyboard.equal",
                "key.keyboard.left.bracket", "key.keyboard.right.bracket", "key.keyboard.semicolon", "key.keyboard.apostrophe",
                "key.keyboard.comma", "key.keyboard.period", "key.keyboard.slash", "key.keyboard.backslash", "key.keyboard.insert",
                "key.keyboard.delete", "key.keyboard.home", "key.keyboard.end", "key.keyboard.page.up", "key.keyboard.page.down",
                "key.keyboard.up", "key.keyboard.down", "key.keyboard.left", "key.keyboard.right",
                "key.mouse.middle", "key.mouse.4", "key.mouse.5", "key.keyboard.unknown"));
        return r;
    }

    static final class KeyBindEditor extends EditorBase<Systems.KeyBind> {
        KeyBindEditor(Ctx c, Systems.KeyBind e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.keybind"));
            nameRow();
            f.text(t("field.display_name"), () -> e.displayName, v -> e.displayName = v);
            f.choice(t("field.keybind.key"), KEYS, KeyBindEditor::label, () -> e.key, v -> e.key = v, t("help.keybind.key"));
            f.choice(t("field.keybind.category"), Systems.KeyBind.CATEGORIES, x -> t("keycat." + x), () -> e.category, v -> e.category = v);
            f.choice(t("field.keybind.fallback"), Systems.KeyBind.FALLBACKS, x -> t("fallback." + x), () -> e.fallback, v -> e.fallback = v,
                    t("help.keybind.fallback"));
            SystemEditors.logic(this, t("section.steps"), t("help.keybind.steps"), e.steps, () -> t("keybind.start", label(e.key)));
            f.section(t("section.usage"));
            int n = c.p.all(Systems.KeyBind.class).indexOf(e) + 1;
            f.add(Ui.help(t("help.keybind.use", c.ns() + ".key", n)));
        }

        private static String label(String key) {
            if (key.equals("key.keyboard.unknown")) return I18n.t("key.unbound");
            if (key.startsWith("key.mouse.")) {
                String b = key.substring(10);
                return b.equals("middle") ? I18n.t("key.mouse_middle") : I18n.t("key.mouse_button", b);
            }
            return Systems.KeyBind.keyLabel(key);
        }
    }
}
