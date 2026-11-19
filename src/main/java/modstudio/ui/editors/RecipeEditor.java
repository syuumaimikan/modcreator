package modstudio.ui.editors;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import modstudio.core.Elements.Recipe;
import modstudio.ui.Ui;
import modstudio.ui.controls.Form;
import modstudio.ui.controls.IdField;
import modstudio.ui.controls.ItemSlot;

import java.util.List;

final class RecipeEditor extends EditorBase<Recipe> {
    private final VBox body = new VBox(16);

    RecipeEditor(Ctx c, Recipe e) {
        super(c, e);
    }

    @Override
    protected void fill() {
        f.section(t("section.general"));
        nameRow();
        f.choice(t("field.recipe_type"), List.of("shaped", "shapeless", "smelting", "blasting", "smoking", "campfire", "stonecutting", "smithing", "brewing"),
                x -> t("recipetype." + x), () -> e.recipeType, v -> {
                    e.recipeType = v;
                    javafx.application.Platform.runLater(this::buildBody);
                });
        f.text(t("field.group"), () -> e.group, v -> e.group = v, t("help.group"));
        f.addTop(body);
        buildBody();
    }

    private void buildBody() {
        while (e.grid.size() < 9) e.grid.add("");
        Form b = new Form(f::changed);
        b.setPadding(javafx.geometry.Insets.EMPTY);
        switch (e.recipeType) {
            case "shaped", "shapeless" -> {
                b.section(t("section.crafting"), e.recipeType.equals("shaped") ? t("help.shaped") : t("help.shapeless"));
                GridPane grid = new GridPane();
                grid.setHgap(4);
                grid.setVgap(4);
                for (int i = 0; i < 9; i++) {
                    final int idx = i;
                    grid.add(slot(IdField.Kind.ITEM_OR_TAG, e.grid.get(i), v -> e.grid.set(idx, v)), i % 3, i / 3);
                }
                b.add(machine(grid, resultBox()));
            }
            case "smelting", "blasting", "smoking", "campfire" -> {
                b.section(t("section.cooking"));
                VBox in = new VBox(6, Ui.label(t("field.input"), "text-muted", "text-small"), slot(IdField.Kind.ITEM_OR_TAG, e.input, v -> e.input = v));
                b.add(machine(in, resultBox()));
                b.decimal(t("field.experience"), () -> e.experience, v -> e.experience = v);
                b.integer(t("field.cook_time"), 1, 72000, () -> e.cookTime, v -> e.cookTime = v, t("help.cook_time"));
            }
            case "stonecutting" -> {
                b.section(t("section.stonecutting"));
                VBox in = new VBox(6, Ui.label(t("field.input"), "text-muted", "text-small"), slot(IdField.Kind.ITEM_OR_TAG, e.input, v -> e.input = v));
                b.add(machine(in, resultBox()));
            }
            case "smithing" -> {
                b.section(t("section.smithing"), t("help.smithing"));
                HBox in = new HBox(8,
                        new VBox(6, Ui.label(t("field.template"), "text-muted", "text-small"), slot(IdField.Kind.ITEM_OR_TAG, e.template, v -> e.template = v)),
                        new VBox(6, Ui.label(t("field.base"), "text-muted", "text-small"), slot(IdField.Kind.ITEM_OR_TAG, e.base, v -> e.base = v)),
                        new VBox(6, Ui.label(t("field.addition"), "text-muted", "text-small"), slot(IdField.Kind.ITEM_OR_TAG, e.addition, v -> e.addition = v)));
                b.add(machine(in, resultBox()));
            }
            case "brewing" -> {
                b.section(t("section.brewing"), t("help.brewing"));
                ItemSlot ingredient = slot(IdField.Kind.ITEM, e.addition, v -> e.addition = v);
                b.add(machine(new VBox(6, Ui.label(t("field.brew.ingredient"), "text-muted", "text-small"), ingredient),
                        new VBox(6, Ui.label(t("field.brew.flow"), "text-muted", "text-small"), Ui.icon("mdi2b-bottle-tonic-outline", 40))));
                b.id(t("field.brew.base"), IdField.Kind.POTION, () -> e.input, v -> e.input = v, env(), t("help.brew.base"));
                b.id(t("field.brew.result"), IdField.Kind.POTION, () -> e.result, v -> e.result = v, env(), t("help.brew.result"));
                body.getChildren().setAll(b);
                return;
            }
            default -> {
            }
        }
        b.text(t("field.recipe_components"), () -> e.resultComponents, v -> e.resultComponents = v.trim(), t("help.recipe_components"));
        b.check(t("field.recipe_remainder"), () -> e.keepRemainder, v -> e.keepRemainder = v);
        b.add(Ui.help(t("help.slot")));
        body.getChildren().setAll(b);
    }

    private ItemSlot slot(IdField.Kind kind, String value, java.util.function.Consumer<String> set) {
        return new ItemSlot(env(), kind, value, 52, v -> {
            set.accept(v);
            f.changed();
        });
    }

    private Node resultBox() {
        ItemSlot result = slot(IdField.Kind.ITEM, e.result, v -> e.result = v);
        result.getStyleClass().add("slot-large");
        Node count;
        if (e.isCooking() || e.recipeType.equals("smithing")) {
            count = new Label();
        } else {
            Spinner<Integer> sp = Form.intSpinner(1, 64, e.count, v -> {
                e.count = v;
                f.changed();
            });
            sp.setPrefWidth(90);
            count = new VBox(4, Ui.label(t("field.count"), "text-muted", "text-small"), sp);
        }
        return new VBox(6, Ui.label(t("field.result"), "text-muted", "text-small"), result, count);
    }

    private Node machine(Node input, Node output) {
        HBox box = new HBox(24, input, Ui.icon("mdi2a-arrow-right-bold", 28), output);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("machine");
        return box;
    }
}
