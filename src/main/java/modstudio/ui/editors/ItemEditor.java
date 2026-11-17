package modstudio.ui.editors;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import modstudio.core.Elements.Item;
import modstudio.core.Ids;
import modstudio.core.Target;
import modstudio.core.Vanilla;
import modstudio.ui.Ui;
import modstudio.ui.controls.TextureField;

import java.util.ArrayList;
import java.util.List;

final class ItemEditor extends EditorBase<Item> {
    private final ImageView previewImage = new ImageView();
    private final Label previewName = new Label();
    private final VBox previewLines = new VBox(1);

    ItemEditor(Ctx c, Item e) {
        super(c, e);
    }

    static List<String> creativeTabs(Ctx c) {
        List<String> r = new ArrayList<>(Vanilla.CREATIVE_TABS.keySet());
        for (var t : c.p.all(modstudio.core.Elements.CreativeTab.class)) r.add("tab:" + t.name);
        return r;
    }

    static String tabName(Ctx c, String id) {
        if (id.startsWith("tab:")) {
            String n = id.substring(4);
            var tab = (modstudio.core.Elements.CreativeTab) c.p.byName(modstudio.core.ElementType.CREATIVE_TAB, n);
            String dn = tab == null || tab.displayName.isBlank() ? Ids.titleCase(n) : tab.displayName;
            return dn + "  (" + t("editor.project_tab") + ")";
        }
        return t("vtab." + Ids.path(id));
    }

    static String templateFor(String kind) {
        return switch (kind) {
            case "sword" -> "sword";
            case "pickaxe", "axe", "shovel", "hoe" -> "pickaxe";
            case "food" -> "apple";
            default -> "gem";
        };
    }

    @Override
    protected void fill() {
        f.addTop(preview());

        f.section(t("section.general"));
        nameRow();
        f.text(t("field.display_name"), () -> e.displayName, v -> e.displayName = v);
        localized("name", t("field.display_name"), () -> e.displayName);
        f.row(t("field.texture"), new TextureField(c.ws, c.window(), e.texture, () -> e.name, templateFor(e.kind), v -> {
            e.texture = v;
            f.changed();
        }), t("help.texture"));
        f.choice(t("field.model"), ModelEditor.modelChoices(c), x -> x.isEmpty() ? t("model.none") : x, () -> e.model, v -> e.model = v, t("help.item_model"));
        f.choice(t("field.item_kind"), List.of("basic", "food", "sword", "pickaxe", "axe", "shovel", "hoe"),
                k -> t("kind." + k), () -> e.kind, v -> e.kind = v);
        f.choice(t("field.creative_tab"), creativeTabs(c), id -> tabName(c, id), () -> e.creativeTab, v -> e.creativeTab = v);
        f.integer(t("field.max_stack"), 1, 99, () -> e.maxStack, v -> e.maxStack = v);
        f.showIf(() -> !e.isTool());
        f.choice(t("field.rarity"), List.of("common", "uncommon", "rare", "epic"), r -> t("rarity." + r), () -> e.rarity, v -> e.rarity = v);
        f.check(t("field.glint"), () -> e.glint, v -> e.glint = v);
        f.check(t("field.fire_resistant"), () -> e.fireResistant, v -> e.fireResistant = v);
        f.area(t("field.tooltip"), () -> e.tooltip, v -> e.tooltip = v, 3, t("help.tooltip"));
        f.area(t("field.shift_tooltip"), () -> e.shiftTooltip, v -> e.shiftTooltip = v, 3, t("help.shift_tooltip"));

        f.section(t("section.food"));
        f.sectionShowIf(() -> e.kind.equals("food"));
        f.integer(t("field.nutrition"), 0, 20, () -> e.nutrition, v -> e.nutrition = v, t("help.nutrition"));
        f.decimal(t("field.saturation"), () -> e.saturation, v -> e.saturation = v, t("help.saturation"));
        f.check(t("field.always_edible"), () -> e.alwaysEdible, v -> e.alwaysEdible = v);
        f.check(t("field.fast_food"), () -> e.fastFood, v -> e.fastFood = v);

        f.section(t("section.tool"));
        f.sectionShowIf(e::isTool);
        f.choice(t("field.tier"), List.of("wood", "stone", "iron", "gold", "diamond", "netherite"), x -> t("tier." + x), () -> e.tier, v -> e.tier = v, t("help.tier"));
        f.decimal(t("field.attack_damage"), () -> e.attackDamage, v -> e.attackDamage = v, t("help.attack_damage"));
        f.decimal(t("field.attack_speed"), () -> e.attackSpeed, v -> e.attackSpeed = v, t("help.attack_speed"));

        f.section(t("section.fuel"));
        f.integer(t("field.burn_time"), 0, 100000, () -> e.burnTime, v -> e.burnTime = v, t("help.burn_time"));

        f.section(t("section.vanilla_base"), t("help.vanilla_base"));
        f.sectionShowIf(() -> c.p.has(Target.PAPER) || c.p.has(Target.DATAPACK) || c.p.has(Target.RESOURCEPACK));
        f.choice(t("field.base_item"), new ArrayList<>(Vanilla.CMD_BASES.keySet()), id -> Ids.titleCase(Ids.path(id)) + "  (" + id + ")",
                () -> e.baseItem, v -> e.baseItem = v);
        f.integer(t("field.custom_model_data"), 0, 99_999_999, () -> e.customModelData, v -> e.customModelData = v, t("help.custom_model_data"));
    }

    private javafx.scene.Node preview() {
        previewImage.setFitWidth(64);
        previewImage.setFitHeight(64);
        previewImage.setSmooth(false);
        StackPane slot = new StackPane(previewImage);
        slot.getStyleClass().add("slot");
        slot.setMinSize(80, 80);
        slot.setMaxSize(80, 80);
        previewName.getStyleClass().add("mc-tooltip-name");
        VBox tip = new VBox(2, previewName, previewLines);
        tip.getStyleClass().add("mc-tooltip");
        tip.setPadding(new Insets(6, 10, 6, 10));
        tip.setMaxWidth(360);
        HBox box = new HBox(16, slot, tip);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        box.getStyleClass().add("preview-strip");
        return box;
    }

    @Override
    protected void refresh() {
        previewImage.setImage(c.ws.texture(e.texture, 64));
        previewName.setText(e.displayName.isBlank() ? Ids.titleCase(e.name) : e.displayName);
        previewName.setStyle("-fx-text-fill: " + switch (e.rarity) {
            case "uncommon" -> "#FFFF55";
            case "rare" -> "#55FFFF";
            case "epic" -> "#FF55FF";
            default -> "#FFFFFF";
        } + ";");
        previewLines.getChildren().clear();
        if (!e.tooltip.isBlank()) {
            for (String line : e.tooltip.split("\n")) {
                Label l = new Label(line.replaceAll("&[0-9a-fk-or]", ""));
                l.getStyleClass().add("mc-tooltip-line");
                previewLines.getChildren().add(l);
            }
        }
        if (e.isTool()) {
            Label l = new Label("+" + fmt(e.attackDamage + tierBonus()) + " " + t("preview.attack_damage"));
            l.getStyleClass().add("mc-tooltip-stat");
            previewLines.getChildren().add(l);
        }
        if (e.kind.equals("food")) {
            Label l = new Label(t("preview.food", e.nutrition, fmt(e.saturation)));
            l.getStyleClass().add("mc-tooltip-line");
            previewLines.getChildren().add(l);
        }
        Label id = new Label(fullId());
        id.getStyleClass().add("mc-tooltip-id");
        previewLines.getChildren().add(id);
    }

    private double tierBonus() {
        return switch (e.tier) {
            case "stone" -> 1;
            case "iron" -> 2;
            case "diamond" -> 3;
            case "netherite" -> 4;
            default -> 0;
        } + 1;
    }

    private static String fmt(double d) {
        return modstudio.ui.controls.Form.fmt(d);
    }
}
