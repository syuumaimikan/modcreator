package modstudio.ui.editors;

import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import modstudio.core.ElementType;
import modstudio.core.Elements.ArmorMaterial;
import modstudio.core.Elements.Item;
import modstudio.core.Ids;
import modstudio.core.TextureAsset;
import modstudio.core.TextureTemplates;
import modstudio.ui.Ui;
import modstudio.ui.controls.IdField;
import modstudio.ui.controls.TextureField;

import java.util.List;

/** Armor material: stats shared by a set of pieces, the worn textures and a shortcut that creates the pieces. */
final class ArmorEditor extends EditorBase<ArmorMaterial> {
    static final List<String> SOUNDS = List.of("leather", "chain", "iron", "gold", "diamond", "netherite", "turtle", "generic");
    private final VBox pieces = new VBox(6);
    private final ColorPicker color = new ColorPicker(Color.web("#8C9BAB"));

    ArmorEditor(Ctx c, ArmorMaterial e) {
        super(c, e);
    }

    @Override
    protected void fill() {
        f.section(t("section.general"), t("help.armor_material_intro"));
        nameRow();
        f.row(t("field.armor_layer1"), new TextureField(c.ws, c.window(), e.layer1, () -> e.name + "_layer_1", "armor_layer_1", v -> {
            e.layer1 = v;
            f.changed();
        }), t("help.armor_layer1"));
        f.row(t("field.armor_layer2"), new TextureField(c.ws, c.window(), e.layer2, () -> e.name + "_layer_2", "armor_layer_2", v -> {
            e.layer2 = v;
            f.changed();
        }), t("help.armor_layer2"));

        f.section(t("section.armor_stats"));
        f.integer(t("field.armor_helmet"), 0, 30, () -> e.helmet, v -> e.helmet = v);
        f.integer(t("field.armor_chestplate"), 0, 30, () -> e.chestplate, v -> e.chestplate = v);
        f.integer(t("field.armor_leggings"), 0, 30, () -> e.leggings, v -> e.leggings = v);
        f.integer(t("field.armor_boots"), 0, 30, () -> e.boots, v -> e.boots = v);
        f.decimal(t("field.armor_toughness"), () -> e.toughness, v -> e.toughness = Math.max(0, v), t("help.armor_toughness"));
        f.decimal(t("field.knockback_resistance"), () -> e.knockbackResistance, v -> e.knockbackResistance = Math.max(0, Math.min(1, v)), t("help.armor_knockback"));
        f.integer(t("field.armor_durability"), 1, 500, () -> e.durability, v -> e.durability = v, t("help.armor_durability"));
        f.integer(t("field.enchantability"), 0, 100, () -> e.enchantability, v -> e.enchantability = v, t("help.enchantability"));
        f.choice(t("field.equip_sound"), SOUNDS, s -> t("armor_sound." + s), () -> e.equipSound, v -> e.equipSound = v);
        f.id(t("field.repair_item"), IdField.Kind.ITEM_OR_TAG, () -> e.repairItem, v -> e.repairItem = v, env(), t("help.repair_item"));

        f.section(t("section.armor_pieces"), t("help.armor_pieces"));
        f.add(pieces);
        color.setPrefWidth(160);
        HBox actions = new HBox(8, color, Ui.button(t("armor.create_pieces"), "mdi2p-plus-box-multiple-outline", this::createPieces));
        actions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        f.add(actions);
    }

    private List<Item> members() {
        return c.p.all(Item.class).stream().filter(i -> i.isArmor() && i.armorMaterial.equals(e.name)).toList();
    }

    @Override
    protected void refresh() {
        pieces.getChildren().clear();
        for (String kind : Item.ARMOR_KINDS) {
            Item it = members().stream().filter(i -> i.kind.equals(kind)).findFirst().orElse(null);
            ImageView icon = new ImageView(it == null ? null : c.ws.texture(it.texture, 32));
            icon.setFitWidth(32);
            icon.setFitHeight(32);
            Label name = new Label(t("kind." + kind));
            name.setMinWidth(110);
            Label value = it == null ? Ui.label(t("armor.piece_missing"), "text-muted")
                    : new Label((it.displayName.isBlank() ? Ids.titleCase(it.name) : it.displayName) + "  +" + e.defense(kind)
                    + "  " + t("armor.durability_value", e.durabilityOf(kind)));
            HBox row = new HBox(10, icon, name, value);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            pieces.getChildren().add(row);
        }
    }

    private static int argb(Color col) {
        return 0xFF000000 | ((int) Math.round(col.getRed() * 255) << 16) | ((int) Math.round(col.getGreen() * 255) << 8) | (int) Math.round(col.getBlue() * 255);
    }

    /** Creates the missing pieces with generated icons, plus generated worn textures when none are set. */
    private void createPieces() {
        int base = argb(color.getValue());
        var project = c.ws.project;
        if (project.texture(e.layer1) == null) e.layer1 = addTexture(e.name + "_layer_1", TextureTemplates.generate("armor_layer_1", base), 64, 32);
        if (project.texture(e.layer2) == null) e.layer2 = addTexture(e.name + "_layer_2", TextureTemplates.generate("armor_layer_2", base), 64, 32);
        String title = Ids.titleCase(e.name);
        for (String kind : Item.ARMOR_KINDS) {
            if (members().stream().anyMatch(i -> i.kind.equals(kind))) continue;
            Item it = new Item();
            it.name = project.uniqueName(ElementType.ITEM, e.name + "_" + kind);
            it.kind = kind;
            it.armorMaterial = e.name;
            it.maxStack = 1;
            it.creativeTab = "minecraft:combat";
            it.displayName = title + " " + Ids.titleCase(kind);
            it.texture = addTexture(it.name, TextureTemplates.generate(kind, base), 16, 16);
            project.elements.add(it);
        }
        c.ws.structureChanged();
        refresh();
    }

    private String addTexture(String name, int[] px, int w, int h) {
        TextureAsset t = TextureAsset.of(c.ws.project.uniqueTextureName(name), px, w, h);
        c.ws.project.textures.add(t);
        return t.id;
    }
}
