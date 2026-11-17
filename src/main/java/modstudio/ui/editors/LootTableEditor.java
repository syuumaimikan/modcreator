package modstudio.ui.editors;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.layout.*;
import modstudio.core.Elements.LootEntry;
import modstudio.core.Elements.LootPool;
import modstudio.core.Elements.LootTable;
import modstudio.ui.Ui;
import modstudio.ui.controls.Form;
import modstudio.ui.controls.IdField;

import java.util.List;

final class LootTableEditor extends EditorBase<LootTable> {
    private final VBox pools = new VBox(16);

    LootTableEditor(Ctx c, LootTable e) {
        super(c, e);
    }

    @Override
    protected void fill() {
        f.section(t("section.general"));
        nameRow();
        f.choice(t("field.loot_type"), List.of("chest", "block", "entity", "fishing", "gift", "generic"), x -> t("loottype." + x),
                () -> e.lootType, v -> e.lootType = v);
        f.id(t("field.replaces"), IdField.Kind.LOOT_TABLE, () -> e.replaces, v -> e.replaces = v, env(), t("help.replaces"));
        f.addTop(pools);
        Button addPool = Ui.button(t("loot.add_pool"), "mdi2p-plus", () -> {
            LootPool p = new LootPool();
            p.entries.add(new LootEntry());
            e.pools.add(p);
            rebuild();
            f.changed();
        });
        f.addTop(addPool);
        rebuild();
    }

    private void rebuild() {
        pools.getChildren().clear();
        for (int i = 0; i < e.pools.size(); i++) pools.getChildren().add(pool(e.pools.get(i), i));
    }

    private Node pool(LootPool p, int index) {
        Button del = Ui.iconButton("mdi2d-delete-outline", t("loot.remove_pool"), () -> {
            e.pools.remove(p);
            rebuild();
            f.changed();
        });
        HBox head = Ui.hbox(8, Ui.label(t("loot.pool", index + 1), "card-title"), Ui.spacer(), del);

        Spinner<Integer> rmin = Form.intSpinner(0, 64, p.rollsMin, v -> {
            p.rollsMin = v;
            f.changed();
        });
        Spinner<Integer> rmax = Form.intSpinner(0, 64, p.rollsMax, v -> {
            p.rollsMax = v;
            f.changed();
        });
        rmin.setPrefWidth(90);
        rmax.setPrefWidth(90);
        var bonus = Form.decimalField(p.bonusRolls, v -> {
            p.bonusRolls = v;
            f.changed();
        });
        var chance = Form.decimalField(p.chance, v -> {
            p.chance = v;
            f.changed();
        });
        CheckBox killed = new CheckBox(t("field.killed_by_player"));
        killed.setSelected(p.killedByPlayer);
        killed.selectedProperty().addListener((o, a, b) -> {
            p.killedByPlayer = b;
            f.changed();
        });
        ComboBox<String> pred = new ComboBox<>();
        pred.setEditable(true);
        pred.getItems().add("");
        for (var pr : c.p.all(modstudio.core.Elements.Predicate.class)) pred.getItems().add(pr.name);
        pred.setValue(p.predicate);
        pred.setPrefWidth(180);
        pred.valueProperty().addListener((o, a, b) -> {
            p.predicate = b == null ? "" : b.trim();
            f.changed();
        });
        FlowPane settings = new FlowPane(16, 8,
                labelled(t("field.rolls_min"), rmin), labelled(t("field.rolls_max"), rmax),
                labelled(t("field.bonus_rolls"), bonus), labelled(t("field.chance"), chance), labelled("", killed),
                labelled(t("field.loot.predicate"), pred));

        GridPane table = new GridPane();
        table.setHgap(8);
        table.setVgap(6);
        String[] heads = {t("loot.col_kind"), t("loot.col_name"), t("loot.col_weight"), t("loot.col_min"), t("loot.col_max"), t("loot.col_fortune"), t("loot.col_enchant"), ""};
        for (int i = 0; i < heads.length; i++) table.add(Ui.label(heads[i], "text-muted", "text-small"), i, 0);
        int row = 1;
        for (LootEntry en : p.entries) {
            ComboBox<String> kind = Ui.combo(List.of("item", "tag", "loot_table", "empty"), x -> t("lootkind." + x));
            kind.setValue(en.kind);
            kind.setPrefWidth(112);
            IdField name = new IdField(env(), kindFor(en.kind), en.name, v -> {
                en.name = v;
                f.changed();
            });
            name.setPrefWidth(230);
            name.setDisable(en.kind.equals("empty"));
            kind.valueProperty().addListener((o, a, b) -> {
                en.kind = b;
                name.setKind(kindFor(b));
                name.setDisable(b.equals("empty"));
                f.changed();
            });
            Spinner<Integer> w = Form.intSpinner(1, 1000, en.weight, v -> {
                en.weight = v;
                f.changed();
            });
            Spinner<Integer> mn = Form.intSpinner(0, 64, en.countMin, v -> {
                en.countMin = v;
                f.changed();
            });
            Spinner<Integer> mx = Form.intSpinner(0, 64, en.countMax, v -> {
                en.countMax = v;
                f.changed();
            });
            for (Spinner<Integer> s : List.of(w, mn, mx)) s.setPrefWidth(74);
            CheckBox fortune = new CheckBox();
            fortune.setSelected(en.fortune);
            fortune.selectedProperty().addListener((o, a, b) -> {
                en.fortune = b;
                f.changed();
            });
            CheckBox enchant = new CheckBox();
            enchant.setSelected(en.enchant);
            enchant.selectedProperty().addListener((o, a, b) -> {
                en.enchant = b;
                f.changed();
            });
            Button remove = Ui.iconButton("mdi2c-close", t("action.delete"), () -> {
                p.entries.remove(en);
                rebuild();
                f.changed();
            });
            table.addRow(row++, kind, name, w, mn, mx, fortune, enchant, remove);
        }
        Button add = Ui.button(t("loot.add_entry"), "mdi2p-plus", () -> {
            p.entries.add(new LootEntry());
            rebuild();
            f.changed();
        });
        add.getStyleClass().add("flat");

        ScrollPane wrap = new ScrollPane(table);
        table.setPadding(new javafx.geometry.Insets(0, 0, 14, 0));
        wrap.getStyleClass().add("edge-to-edge");
        wrap.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox card = new VBox(12, head, settings, wrap, add, Ui.help(t("help.loot_weight")));
        card.getStyleClass().add("card");
        return card;
    }

    private static IdField.Kind kindFor(String kind) {
        return switch (kind) {
            case "tag" -> IdField.Kind.ITEM_OR_TAG;
            case "loot_table" -> IdField.Kind.LOOT_TABLE;
            default -> IdField.Kind.ITEM;
        };
    }

    private static Node labelled(String label, Node n) {
        VBox b = new VBox(4, Ui.label(label, "text-muted", "text-small"), n);
        b.setAlignment(Pos.BOTTOM_LEFT);
        return b;
    }
}
