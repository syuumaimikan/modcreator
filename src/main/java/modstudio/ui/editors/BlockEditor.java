package modstudio.ui.editors;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import modstudio.core.Elements.Block;
import modstudio.core.Ids;
import modstudio.core.Vanilla;
import modstudio.ui.Ui;
import modstudio.ui.controls.BlockPreview;
import modstudio.ui.controls.IdField;
import modstudio.ui.controls.ModelView3D;
import modstudio.ui.controls.TextureField;

import java.util.List;

final class BlockEditor extends EditorBase<Block> {
    private BlockPreview preview;
    private ModelView3D modelPreview;
    private final Label previewName = Ui.label("", "title-4");
    private final Label previewInfo = Ui.muted("");

    BlockEditor(Ctx c, Block e) {
        super(c, e);
    }

    @Override
    protected void fill() {
        preview = new BlockPreview(c.ws);
        modelPreview = new ModelView3D(c.ws, 170, 170, true);
        modelPreview.setShowGrid(false);
        modelPreview.setMinSize(170, 170);
        modelPreview.setMaxSize(170, 170);
        StackPane previewBox = new StackPane(preview, modelPreview);
        VBox info = new VBox(4, previewName, previewInfo);
        HBox top = new HBox(16, previewBox, info);
        top.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        top.getStyleClass().add("preview-strip");
        f.addTop(top);

        f.section(t("section.general"));
        nameRow();
        f.text(t("field.display_name"), () -> e.displayName, v -> e.displayName = v);
        localized("name", t("field.display_name"), () -> e.displayName);
        f.choice(t("field.shape"), List.of("cube", "cube_bottom_top", "pillar", "cross", "model"), s -> t("shape." + s), () -> e.shape, v -> e.shape = v, t("help.shape"));
        f.choice(t("field.model"), ModelEditor.modelChoices(c), x -> x.isEmpty() ? t("model.none") : x, () -> e.model, v -> e.model = v, t("help.block_model"));
        f.showIf(() -> e.shape.equals("model"));
        f.check(t("field.model_collision"), () -> e.modelCollision, v -> e.modelCollision = v);
        f.showIf(() -> e.shape.equals("model"));
        f.check(t("field.rotatable"), () -> e.rotatable, v -> e.rotatable = v);
        f.showIf(() -> e.shape.equals("model"));
        texture(t("field.texture_all"), e.texture, v -> e.texture = v, "block");
        f.showIf(() -> e.shape.equals("cube") || e.shape.equals("cross"));
        texture(t("field.texture_top"), e.textureTop, v -> e.textureTop = v, "block");
        f.showIf(() -> e.shape.equals("cube_bottom_top") || e.shape.equals("pillar"));
        texture(t("field.texture_bottom"), e.textureBottom, v -> e.textureBottom = v, "block");
        f.showIf(() -> e.shape.equals("cube_bottom_top"));
        texture(t("field.texture_side"), e.textureSide, v -> e.textureSide = v, "block");
        f.showIf(() -> e.shape.equals("cube_bottom_top") || e.shape.equals("pillar"));
        f.choice(t("field.creative_tab"), ItemEditor.creativeTabs(c), id -> ItemEditor.tabName(c, id), () -> e.creativeTab, v -> e.creativeTab = v);

        f.section(t("section.properties"));
        f.choice(t("field.sound_type"), Vanilla.SOUND_TYPES, s -> Ids.titleCase(s), () -> e.sound, v -> e.sound = v);
        f.decimal(t("field.hardness"), () -> e.hardness, v -> e.hardness = v, t("help.hardness"));
        f.decimal(t("field.resistance"), () -> e.resistance, v -> e.resistance = v, t("help.resistance"));
        f.integer(t("field.light"), 0, 15, () -> e.light, v -> e.light = v, t("help.light"));
        f.check(t("field.transparent"), () -> e.transparent, v -> e.transparent = v, t("help.transparent"));
        f.decimal(t("field.friction"), () -> e.friction, v -> e.friction = v, t("help.friction"));
        f.decimal(t("field.speed_factor"), () -> e.speedFactor, v -> e.speedFactor = v);
        f.decimal(t("field.jump_factor"), () -> e.jumpFactor, v -> e.jumpFactor = v);

        f.section(t("section.mining"));
        f.choice(t("field.tool"), List.of("none", "pickaxe", "axe", "shovel", "hoe"), x -> x.equals("none") ? t("tool.none") : t("kind." + x), () -> e.tool, v -> e.tool = v);
        f.choice(t("field.tool_level"), List.of("wood", "stone", "iron", "diamond"), x -> t("tier." + x), () -> e.toolLevel, v -> e.toolLevel = v, t("help.tool_level"));
        f.showIf(() -> !e.tool.equals("none"));
        f.check(t("field.requires_tool"), () -> e.requiresTool, v -> e.requiresTool = v, t("help.requires_tool"));

        f.section(t("section.drops"));
        f.choice(t("field.drop"), List.of("self", "item", "none"), x -> t("drop." + x), () -> e.drop, v -> e.drop = v);
        f.id(t("field.drop_item"), IdField.Kind.ITEM, () -> e.dropItem, v -> e.dropItem = v, env());
        f.showIf(() -> e.drop.equals("item"));
        f.integer(t("field.drop_min"), 0, 64, () -> e.dropMin, v -> e.dropMin = v);
        f.showIf(() -> e.drop.equals("item"));
        f.integer(t("field.drop_max"), 0, 64, () -> e.dropMax, v -> e.dropMax = v);
        f.showIf(() -> e.drop.equals("item"));
        f.check(t("field.silk_touch"), () -> e.silkTouch, v -> e.silkTouch = v, t("help.silk_touch"));
        f.showIf(() -> e.drop.equals("item"));
        f.check(t("field.fortune"), () -> e.fortune, v -> e.fortune = v);
        f.showIf(() -> e.drop.equals("item"));

        f.section(t("section.ore"), t("help.ore"));
        f.check(t("field.ore_gen"), () -> e.oreGen, v -> e.oreGen = v);
        f.choice(t("field.ore_dimension"), List.of("overworld", "nether", "end"), x -> t("dim." + x), () -> e.oreDimension, v -> e.oreDimension = v);
        f.showIf(() -> e.oreGen);
        f.integer(t("field.ore_size"), 1, 64, () -> e.oreSize, v -> e.oreSize = v, t("help.ore_size"));
        f.showIf(() -> e.oreGen);
        f.integer(t("field.ore_count"), 1, 128, () -> e.oreCount, v -> e.oreCount = v, t("help.ore_count"));
        f.showIf(() -> e.oreGen);
        f.integer(t("field.ore_min_y"), -64, 320, () -> e.oreMinY, v -> e.oreMinY = v);
        f.showIf(() -> e.oreGen);
        f.integer(t("field.ore_max_y"), -64, 320, () -> e.oreMaxY, v -> e.oreMaxY = v);
        f.showIf(() -> e.oreGen);
    }

    private void texture(String label, String value, java.util.function.Consumer<String> set, String template) {
        f.row(label, new TextureField(c.ws, c.window(), value, () -> e.name, template.equals("block") && e.oreGen ? "ore" : template, v -> {
            set.accept(v);
            f.changed();
        }));
    }

    @Override
    protected void refresh() {
        if (preview == null) return;
        boolean useModel = e.shape.equals("model");
        preview.setVisible(!useModel);
        modelPreview.setVisible(useModel);
        if (useModel) modelPreview.setModel(c.ws.project.model(e.model));
        else preview.draw(e);
        previewName.setText(e.displayName.isBlank() ? Ids.titleCase(e.name) : e.displayName);
        StringBuilder sb = new StringBuilder();
        sb.append(t("preview.hardness", modstudio.ui.controls.Form.fmt(e.hardness)));
        if (e.light > 0) sb.append("  |  ").append(t("preview.light", e.light));
        if (!e.tool.equals("none")) sb.append("  |  ").append(t("kind." + e.tool)).append(" (").append(t("tier." + e.toolLevel)).append(")");
        if (e.oreGen) sb.append("\n").append(t("preview.ore", e.oreMinY, e.oreMaxY, e.oreCount));
        previewInfo.setText(sb.toString());
    }
}
