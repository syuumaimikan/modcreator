package modstudio.ui.editors;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import modstudio.core.Elements.Item;
import modstudio.core.Ids;
import modstudio.core.PreviewModels;
import modstudio.core.Target;
import modstudio.core.Vanilla;
import modstudio.ui.Ui;
import modstudio.ui.controls.EntityPreview3D;
import modstudio.ui.controls.Form;
import modstudio.ui.controls.IdField;
import modstudio.ui.controls.TextureField;

import java.util.ArrayList;
import java.util.List;

final class ItemEditor extends EditorBase<Item> {
    private final ImageView previewImage = new ImageView();
    private final Label previewName = new Label();
    private final VBox previewTip = new VBox(2);
    private final VBox previewLines = new VBox(1);
    private final VBox attrBox = new VBox(6);
    private EntityPreview3D preview3d;
    private static final List<String> ATTRS = List.of(
            "attack_damage", "attack_speed", "attack_knockback", "max_health", "movement_speed",
            "armor", "armor_toughness", "knockback_resistance", "luck", "follow_range", "flying_speed",
            "entity_interaction_range", "block_interaction_range", "scale", "step_height", "safe_fall_distance", "jump_strength");
    private static final List<String> ATTR_OPS = List.of("add_value", "add_multiplied_base", "add_multiplied_total");
    private static final List<String> ATTR_SLOTS = List.of("mainhand", "offhand", "hand", "head", "chest", "legs", "feet", "armor", "any");

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
            case "helmet", "chestplate", "leggings", "boots" -> kind;
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
        f.choice(t("field.item_kind"), Item.ALL_KINDS,
                k -> t("kind." + k), () -> e.kind, v -> e.kind = v);
        f.choice(t("field.creative_tab"), creativeTabs(c), id -> tabName(c, id), () -> e.creativeTab, v -> e.creativeTab = v);
        f.integer(t("field.max_stack"), 1, 99, () -> e.maxStack, v -> e.maxStack = v);
        f.showIf(() -> !e.damageable());
        f.choice(t("field.rarity"), List.of("common", "uncommon", "rare", "epic"), r -> t("rarity." + r), () -> e.rarity, v -> e.rarity = v);
        f.check(t("field.glint"), () -> e.glint, v -> e.glint = v);
        f.check(t("field.fire_resistant"), () -> e.fireResistant, v -> e.fireResistant = v);
        f.decimal(t("field.use_cooldown"), () -> e.useCooldown, v -> e.useCooldown = Math.max(0, v), t("help.use_cooldown"));
        f.id(t("field.craft_remainder"), IdField.Kind.ITEM, () -> e.craftRemainder, v -> e.craftRemainder = v, env(), t("help.craft_remainder"));
        f.area(t("field.tooltip"), () -> e.tooltip, v -> e.tooltip = v, 3, t("help.tooltip"));
        localized("tooltip", t("field.tooltip"), () -> e.tooltip);
        f.area(t("field.shift_tooltip"), () -> e.shiftTooltip, v -> e.shiftTooltip = v, 3, t("help.shift_tooltip"));
        localized("shift_tooltip", t("field.shift_tooltip"), () -> e.shiftTooltip);

        f.section(t("section.tooltip_look"), t("help.tooltip_look"));
        f.check(t("field.custom_tooltip"), () -> e.customTooltip, v -> e.customTooltip = v);
        GameplayEditors.colorRow(f, t("field.tooltip_background"), () -> e.tooltipBackground, v -> e.tooltipBackground = v);
        f.showIf(() -> e.customTooltip);
        f.integer(t("field.tooltip_background_opacity"), 0, 100, () -> e.tooltipBackgroundOpacity, v -> e.tooltipBackgroundOpacity = v);
        f.showIf(() -> e.customTooltip);
        GameplayEditors.colorRow(f, t("field.tooltip_border_top"), () -> e.tooltipBorderTop, v -> e.tooltipBorderTop = v);
        f.showIf(() -> e.customTooltip);
        GameplayEditors.colorRow(f, t("field.tooltip_border_bottom"), () -> e.tooltipBorderBottom, v -> e.tooltipBorderBottom = v);
        f.showIf(() -> e.customTooltip);
        f.integer(t("field.tooltip_border_opacity"), 0, 100, () -> e.tooltipBorderOpacity, v -> e.tooltipBorderOpacity = v);
        f.showIf(() -> e.customTooltip);
        f.row(t("field.tooltip_background_texture"), new TextureField(c.ws, c.window(), e.tooltipBackgroundTexture, () -> e.name + "_tooltip_background", "block", v -> {
            e.tooltipBackgroundTexture = v;
            f.changed();
        }), t("help.tooltip_texture"));
        f.showIf(() -> e.customTooltip);
        f.row(t("field.tooltip_frame_texture"), new TextureField(c.ws, c.window(), e.tooltipFrameTexture, () -> e.name + "_tooltip_frame", "block", v -> {
            e.tooltipFrameTexture = v;
            f.changed();
        }), t("help.tooltip_texture"));
        f.showIf(() -> e.customTooltip);

        f.section(t("section.food"));
        f.sectionShowIf(() -> e.kind.equals("food"));
        f.integer(t("field.nutrition"), 0, 20, () -> e.nutrition, v -> e.nutrition = v, t("help.nutrition"));
        f.decimal(t("field.saturation"), () -> e.saturation, v -> e.saturation = v, t("help.saturation"));
        f.check(t("field.always_edible"), () -> e.alwaysEdible, v -> e.alwaysEdible = v);
        f.check(t("field.fast_food"), () -> e.fastFood, v -> e.fastFood = v);
        f.choice(t("field.consume_animation"), List.of("eat", "drink", "block", "bow", "spear", "none"), x -> t("consume_anim." + x), () -> e.consumeAnimation, v -> e.consumeAnimation = v);
        f.integer(t("field.use_duration_ticks"), 0, 72000, () -> e.useDurationTicks, v -> e.useDurationTicks = v, t("help.use_duration_ticks"));
        f.id(t("field.use_return_item"), IdField.Kind.ITEM, () -> e.useReturnItem, v -> e.useReturnItem = v, env(), t("help.use_return_item"));
        f.id(t("field.food_effect"), IdField.Kind.EFFECT, () -> e.foodEffect, v -> e.foodEffect = v, env(), t("help.food_effect"));
        f.integer(t("field.food_effect_seconds"), 1, 600, () -> e.foodEffectSeconds, v -> e.foodEffectSeconds = v);
        f.showIf(() -> !e.foodEffect.isBlank());
        f.integer(t("field.food_effect_amplifier"), 0, 10, () -> e.foodEffectAmplifier, v -> e.foodEffectAmplifier = v);
        f.showIf(() -> !e.foodEffect.isBlank());
        f.decimal(t("field.food_effect_chance"), () -> e.foodEffectChance, v -> e.foodEffectChance = Math.max(0, Math.min(1, v)));
        f.showIf(() -> !e.foodEffect.isBlank());

        f.section(t("section.tool"));
        f.sectionShowIf(e::isTool);
        List<String> tiers = new ArrayList<>(List.of("wood", "stone", "iron", "gold", "diamond", "netherite"));
        for (var tm : c.p.all(modstudio.core.Elements.ToolMaterial.class)) if (!tiers.contains(tm.name)) tiers.add(tm.name);
        if (!e.tier.isBlank() && !tiers.contains(e.tier)) tiers.add(e.tier);
        f.choice(t("field.tier"), tiers, x -> {
            var tm = c.p.toolMaterial(x);
            return tm != null ? (tm.displayName.isBlank() ? tm.name : tm.displayName) + " (" + x + ")" : t("tier." + x);
        }, () -> e.tier, v -> e.tier = v, t("help.tier"));
        f.decimal(t("field.attack_damage"), () -> e.attackDamage, v -> e.attackDamage = v, t("help.attack_damage"));
        f.decimal(t("field.attack_speed"), () -> e.attackSpeed, v -> e.attackSpeed = v, t("help.attack_speed"));
        f.decimal(t("field.attack_knockback"), () -> e.attackKnockback, v -> e.attackKnockback = v, t("help.attack_knockback"));

        f.section(t("section.armor"), t("help.armor_piece"));
        f.sectionShowIf(e::isArmor);
        List<String> mats = new ArrayList<>();
        for (var m : c.p.all(modstudio.core.Elements.ArmorMaterial.class)) mats.add(m.name);
        MachineEditors.ref(f, t("field.armor_material"), mats, false, () -> e.armorMaterial, v -> e.armorMaterial = v, t("help.armor_material"));
        f.choice(t("field.worn_model"), ModelEditor.modelChoices(c), x -> x.isEmpty() ? t("model.none") : x, () -> e.wornModel, v -> e.wornModel = v, t("help.worn_model"));

        f.section(t("section.durability_enchant"), t("help.durability_enchant"));
        f.sectionShowIf(e::damageable);
        f.integer(t("field.max_damage"), -1, 100000, () -> e.maxDamage, v -> e.maxDamage = v, t("help.max_damage"));
        f.integer(t("field.enchantability"), -1, 100, () -> e.enchantability, v -> e.enchantability = v, t("help.enchantability"));
        f.id(t("field.repair_item"), IdField.Kind.ITEM_OR_TAG, () -> e.repairItem, v -> e.repairItem = v, env(), t("help.repair_item"));

        f.section(t("section.attributes"), t("help.item_attributes"));
        f.add(attrBox);
        f.add(Ui.button(t("item.add_attribute"), "mdi2p-plus", () -> {
            e.attributes.add(new Item.ItemAttribute());
            rebuildAttrs();
            f.changed();
        }));
        rebuildAttrs();

        f.section(t("section.music_disc"), t("help.music_disc"));
        f.sectionShowIf(() -> e.kind.equals("basic"));
        List<String> songs = new ArrayList<>();
        for (var s : c.p.all(modstudio.core.Elements.Song.class)) songs.add(s.name);
        MachineEditors.ref(f, t("field.song"), songs, true, () -> e.song, v -> e.song = v, t("help.song_item"));

        f.section(t("section.equipment_ranged"), t("help.equipment_ranged"));
        f.sectionShowIf(() -> !e.isTool() && !e.isArmor() && e.damageable());
        f.integer(t("field.durability"), 1, 100000, () -> e.durability, v -> e.durability = v);
        f.decimal(t("field.projectile_damage"), () -> e.projectileDamage, v -> e.projectileDamage = v);
        f.showIf(e::isRanged);
        f.decimal(t("field.projectile_speed"), () -> e.projectileSpeed, v -> e.projectileSpeed = v);
        f.showIf(e::isRanged);

        f.section(t("section.fuel"));
        f.integer(t("field.burn_time"), 0, 100000, () -> e.burnTime, v -> e.burnTime = v, t("help.burn_time"));

        SystemEditors.logic(this, t("section.item_use"), t("help.item_use"), e.onUse, () -> t("start.item_use"));
        SystemEditors.logic(this, t("section.item_hit"), t("help.item_hit"), e.onHitEntity, () -> t("start.item_hit"));
        SystemEditors.logic(this, t("section.item_tick"), t("help.item_tick"), e.onTickInHand, () -> t("start.item_tick"));

        f.section(t("section.vanilla_base"), t("help.vanilla_base"));
        f.sectionShowIf(() -> c.p.targets().stream().anyMatch(Target::isPlugin) || c.p.has(Target.DATAPACK) || c.p.has(Target.RESOURCEPACK));
        f.choice(t("field.base_item"), new ArrayList<>(Vanilla.CMD_BASES.keySet()), id -> Ids.titleCase(Ids.path(id)) + "  (" + id + ")",
                () -> e.baseItem, v -> e.baseItem = v);
        f.integer(t("field.custom_model_data"), 0, 99_999_999, () -> e.customModelData, v -> e.customModelData = v, t("help.custom_model_data"));
    }

    private void rebuildAttrs() {
        attrBox.getChildren().clear();
        for (int i = 0; i < e.attributes.size(); i++) {
            int idx = i;
            Item.ItemAttribute a = e.attributes.get(i);
            var attr = Ui.combo(ATTRS, x -> t("attribute." + x));
            if (!ATTRS.contains(a.attribute)) attr.getItems().add(a.attribute);
            attr.setValue(a.attribute);
            attr.valueProperty().addListener((o, ov, nv) -> {
                if (nv != null) {
                    a.attribute = nv;
                    f.changed();
                }
            });
            var amount = Form.decimalField(a.amount, v -> {
                a.amount = v;
                f.changed();
            });
            amount.setPrefColumnCount(5);
            var op = Ui.combo(ATTR_OPS, x -> t("attr_op." + x));
            op.setValue(a.operation);
            op.valueProperty().addListener((o, ov, nv) -> {
                if (nv != null) {
                    a.operation = nv;
                    f.changed();
                }
            });
            var slot = Ui.combo(ATTR_SLOTS, x -> t("attr_slot." + x));
            slot.setValue(a.slot);
            slot.valueProperty().addListener((o, ov, nv) -> {
                if (nv != null) {
                    a.slot = nv;
                    f.changed();
                }
            });
            HBox row = Ui.hbox(6, attr, amount, op, slot, Ui.iconButton("mdi2c-close", t("action.remove"), () -> {
                e.attributes.remove(idx);
                rebuildAttrs();
                f.changed();
            }));
            attrBox.getChildren().add(row);
        }
        if (e.attributes.isEmpty()) attrBox.getChildren().add(Ui.muted(t("item.no_attributes")));
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
        VBox tip = previewTip;
        tip.getChildren().setAll(previewName, previewLines);
        tip.getStyleClass().add("mc-tooltip");
        tip.setPadding(new Insets(6, 10, 6, 10));
        tip.setMaxWidth(360);
        preview3d = new EntityPreview3D(c.ws, 165, 145, false);
        HBox box = new HBox(16, slot, tip, preview3d);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        box.getStyleClass().add("preview-strip");
        return box;
    }

    @Override
    protected void refresh() {
        previewImage.setImage(c.ws.texture(e.texture, 64));
        if (preview3d != null) {
            preview3d.setPreview(PreviewModels.forItem3d(c.p, e), e.texture, false, 1.0, 0, 0, null);
        }
        if (e.customTooltip) {
            int bg = modstudio.core.TooltipArt.argb(e.tooltipBackground, e.tooltipBackgroundOpacity);
            int top = modstudio.core.TooltipArt.argb(e.tooltipBorderTop, e.tooltipBorderOpacity);
            int bottom = modstudio.core.TooltipArt.argb(e.tooltipBorderBottom, e.tooltipBorderOpacity);
            previewTip.setStyle("-fx-background-color: " + modstudio.core.TooltipArt.css(bg) + "; -fx-border-color: linear-gradient(to bottom, "
                    + modstudio.core.TooltipArt.css(top) + ", " + modstudio.core.TooltipArt.css(bottom) + ");");
        } else {
            previewTip.setStyle("");
        }
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
        if (e.isArmor() && c.p.byName(modstudio.core.ElementType.ARMOR_MATERIAL, e.armorMaterial) instanceof modstudio.core.Elements.ArmorMaterial m) {
            Label l = new Label("+" + m.defense(e.kind) + " " + t("preview.armor"));
            l.getStyleClass().add("mc-tooltip-stat");
            previewLines.getChildren().add(l);
            if (m.toughness > 0) {
                Label tl = new Label("+" + fmt(m.toughness) + " " + t("preview.armor_toughness"));
                tl.getStyleClass().add("mc-tooltip-stat");
                previewLines.getChildren().add(tl);
            }
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
