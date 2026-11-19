package modstudio.ui.editors;

import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import modstudio.core.TextureAsset;
import modstudio.core.TextureTemplates;
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
    private static final List<String> INSTRUMENTS = List.of(
            "harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar",
            "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling");
    private static final List<String> PUSH_REACTIONS = List.of("normal", "destroy", "block", "ignore");
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
        f.choice(t("field.shape"), Block.SHAPES, s -> t("shape." + s), () -> e.shape, v -> {
            e.shape = v;
            if (v.equals("crop")) {
                e.tool = "none";
                e.requiresTool = false;
                e.hardness = 0;
                e.resistance = 0;
                e.sound = "crop";
                if (e.creativeTab.equals("minecraft:building_blocks")) e.creativeTab = "minecraft:natural_blocks";
            }
        }, t("help.shape"));
        f.choice(t("field.model"), ModelEditor.modelChoices(c), x -> x.isEmpty() ? t("model.none") : x, () -> e.model, v -> e.model = v, t("help.block_model"));
        f.showIf(() -> e.shape.equals("model"));
        f.check(t("field.model_collision"), () -> e.modelCollision, v -> e.modelCollision = v);
        f.showIf(() -> e.shape.equals("model"));
        f.check(t("field.rotatable"), () -> e.rotatable, v -> e.rotatable = v);
        f.showIf(() -> e.shape.equals("model"));
        texture(t("field.texture_all"), e.texture, v -> e.texture = v, "block");
        f.showIf(() -> !e.shape.equals("cube_bottom_top") && !e.shape.equals("pillar") && !e.shape.equals("model") && !e.isCrop());
        f.row(t("field.seed_texture"), new TextureField(c.ws, c.window(), e.texture, () -> e.name + "_seeds", "seeds", v -> {
            e.texture = v;
            f.changed();
        }), t("help.seed_texture"));
        f.showIf(() -> e.isCrop());
        texture(t("field.texture_top"), e.textureTop, v -> e.textureTop = v, "block");
        f.showIf(() -> e.shape.equals("cube_bottom_top") || e.shape.equals("pillar") || e.shape.equals("door"));
        texture(t("field.texture_bottom"), e.textureBottom, v -> e.textureBottom = v, "block");
        f.showIf(() -> e.shape.equals("cube_bottom_top"));
        texture(t("field.texture_side"), e.textureSide, v -> e.textureSide = v, "block");
        f.showIf(() -> e.shape.equals("cube_bottom_top") || e.shape.equals("pillar"));
        f.choice(t("field.creative_tab"), ItemEditor.creativeTabs(c), id -> ItemEditor.tabName(c, id), () -> e.creativeTab, v -> e.creativeTab = v);

        f.section(t("section.crop"), t("help.crop"));
        f.add(stages);
        cropColor.setPrefWidth(160);
        HBox cropActions = new HBox(8, cropColor,
                Ui.button(t("crop.add_stage"), "mdi2p-plus", this::addStage),
                Ui.button(t("crop.generate_stages"), "mdi2s-sprout-outline", this::generateStages));
        cropActions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        f.add(cropActions);
        f.id(t("field.crop_produce"), IdField.Kind.ITEM, () -> e.cropProduce, v -> e.cropProduce = v, env(), t("help.crop_produce"));
        f.integer(t("field.crop_produce_min"), 0, 64, () -> e.cropProduceMin, v -> e.cropProduceMin = v);
        f.integer(t("field.crop_produce_max"), 0, 64, () -> e.cropProduceMax, v -> e.cropProduceMax = v);
        f.check(t("field.crop_seed_bonus"), () -> e.cropSeedBonus, v -> e.cropSeedBonus = v, t("help.crop_seed_bonus"));
        f.sectionShowIf(() -> e.isCrop());
        rebuildStages();

        f.section(t("section.properties"));
        f.choice(t("field.sound_type"), Vanilla.SOUND_TYPES, s -> Ids.titleCase(s), () -> e.sound, v -> e.sound = v);
        f.decimal(t("field.hardness"), () -> e.hardness, v -> e.hardness = v, t("help.hardness"));
        f.decimal(t("field.resistance"), () -> e.resistance, v -> e.resistance = v, t("help.resistance"));
        f.integer(t("field.light"), 0, 15, () -> e.light, v -> e.light = v, t("help.light"));
        f.check(t("field.transparent"), () -> e.transparent, v -> e.transparent = v, t("help.transparent"));
        f.decimal(t("field.friction"), () -> e.friction, v -> e.friction = v, t("help.friction"));
        f.decimal(t("field.speed_factor"), () -> e.speedFactor, v -> e.speedFactor = v);
        f.decimal(t("field.jump_factor"), () -> e.jumpFactor, v -> e.jumpFactor = v);
        f.integer(t("field.redstone_signal"), 0, 15, () -> e.redstoneSignal, v -> e.redstoneSignal = v);
        f.check(t("field.can_connect_redstone"), () -> e.canConnectRedstone, v -> e.canConnectRedstone = v);
        f.check(t("field.waterloggable"), () -> e.waterloggable, v -> e.waterloggable = v);
        f.check(t("field.has_collision"), () -> e.hasCollision, v -> e.hasCollision = v, t("help.has_collision"));
        f.check(t("field.falling_block"), () -> e.falling, v -> e.falling = v, t("help.falling_block"));
        f.integer(t("field.flammability"), 0, 300, () -> e.flammability, v -> e.flammability = v, t("help.flammability"));
        f.integer(t("field.fire_spread"), 0, 300, () -> e.fireSpread, v -> e.fireSpread = v, t("help.fire_spread"));
        f.choice(t("field.instrument"), INSTRUMENTS, x -> t("instrument." + x), () -> e.instrument, v -> e.instrument = v, t("help.instrument"));
        f.choice(t("field.push_reaction"), PUSH_REACTIONS, x -> t("push_reaction." + x), () -> e.pushReaction, v -> e.pushReaction = v, t("help.push_reaction"));

        f.section(t("section.mining"));
        f.sectionShowIf(() -> !e.isCrop());
        f.choice(t("field.tool"), List.of("none", "pickaxe", "axe", "shovel", "hoe"), x -> x.equals("none") ? t("tool.none") : t("kind." + x), () -> e.tool, v -> e.tool = v);
        f.choice(t("field.tool_level"), List.of("wood", "stone", "iron", "diamond"), x -> t("tier." + x), () -> e.toolLevel, v -> e.toolLevel = v, t("help.tool_level"));
        f.showIf(() -> !e.tool.equals("none"));
        f.check(t("field.requires_tool"), () -> e.requiresTool, v -> e.requiresTool = v, t("help.requires_tool"));

        f.section(t("section.drops"));
        f.sectionShowIf(() -> !e.isCrop());
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
        f.integer(t("field.xp_min"), 0, 100, () -> e.xpMin, v -> e.xpMin = v, t("help.xp_drop"));
        f.integer(t("field.xp_max"), 0, 100, () -> e.xpMax, v -> e.xpMax = v);

        f.section(t("section.ore"), t("help.ore"));
        f.sectionShowIf(() -> !e.isCrop());
        f.check(t("field.ore_gen"), () -> e.oreGen, v -> e.oreGen = v);
        f.choice(t("field.ore_dimension"), List.of("overworld", "nether", "end"), x -> t("dim." + x), () -> e.oreDimension, v -> e.oreDimension = v);
        f.showIf(() -> e.oreGen);
        f.choice(t("field.ore_distribution"), List.of("trapezoid", "uniform"), x -> t("ore_dist." + x), () -> e.oreDistribution, v -> e.oreDistribution = v);
        f.showIf(() -> e.oreGen);
        f.integer(t("field.ore_size"), 1, 64, () -> e.oreSize, v -> e.oreSize = v, t("help.ore_size"));
        f.showIf(() -> e.oreGen);
        f.integer(t("field.ore_count"), 1, 128, () -> e.oreCount, v -> e.oreCount = v, t("help.ore_count"));
        f.showIf(() -> e.oreGen);
        f.integer(t("field.ore_min_y"), -64, 320, () -> e.oreMinY, v -> e.oreMinY = v);
        f.showIf(() -> e.oreGen);
        f.integer(t("field.ore_max_y"), -64, 320, () -> e.oreMaxY, v -> e.oreMaxY = v);
        f.showIf(() -> e.oreGen);
        f.check(t("field.ore_prevent_surface"), () -> e.orePreventSurface, v -> e.orePreventSurface = v, t("help.ore_prevent_surface"));
        f.showIf(() -> e.oreGen);
        f.integer(t("field.ore_surface_depth"), 0, 64, () -> e.oreSurfaceDepth, v -> e.oreSurfaceDepth = v, t("help.ore_surface_depth"));
        f.showIf(() -> e.oreGen && e.orePreventSurface);
        f.decimal(t("field.ore_discard_air"), () -> e.oreDiscardAir, v -> e.oreDiscardAir = Math.max(0, Math.min(1, v)), t("help.ore_discard_air"));
        f.showIf(() -> e.oreGen);
        f.text(t("field.ore_replace_target"), () -> e.oreReplaceTarget, v -> e.oreReplaceTarget = v, t("help.ore_replace_target"));
        f.showIf(() -> e.oreGen);
        f.text(t("field.ore_biomes"), () -> e.oreBiomes, v -> e.oreBiomes = v, t("help.ore_biomes"));
        f.showIf(() -> e.oreGen);

        SystemEditors.logic(this, t("section.block_use"), t("help.block_use"), e.onUse, () -> t("start.block_use"));
        SystemEditors.logic(this, t("section.block_step"), t("help.block_step"), e.onStep, () -> t("start.block_step"));
        SystemEditors.logic(this, t("section.block_random_tick"), t("help.block_random_tick"), e.onRandomTick, () -> t("start.block_random_tick"));
        SystemEditors.logic(this, t("section.block_neighbor"), t("help.block_neighbor"), e.onNeighborChanged, () -> t("start.block_neighbor"));
    }

    private final VBox stages = new VBox(6);
    private final ColorPicker cropColor = new ColorPicker(Color.web("#E0115F"));

    /** One row per growth stage: the ages it covers, its texture and a remove button. */
    private void rebuildStages() {
        stages.getChildren().clear();
        int n = e.stageTextures.size();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            StringBuilder ages = new StringBuilder();
            for (int age = 0; age <= 7; age++) {
                if (Block.stageOf(age, n) == i) ages.append(ages.length() == 0 ? "" : ", ").append(age);
            }
            Label label = new Label(t("crop.stage", i + 1, ages.toString()));
            label.setMinWidth(150);
            TextureField tf = new TextureField(c.ws, c.window(), e.stageTextures.get(i), () -> e.name + "_stage" + idx, "crop", v -> {
                e.stageTextures.set(idx, v);
                f.changed();
            });
            HBox.setHgrow(tf, javafx.scene.layout.Priority.ALWAYS);
            HBox row = new HBox(8, label, tf, Ui.iconButton("mdi2c-close", t("action.remove"), () -> {
                e.stageTextures.remove(idx);
                f.changed();
                rebuildStages();
            }));
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            stages.getChildren().add(row);
        }
        if (n == 0) stages.getChildren().add(Ui.muted(t("crop.no_stages")));
    }

    private void addStage() {
        if (e.stageTextures.size() >= 8) return;
        e.stageTextures.add("");
        f.changed();
        rebuildStages();
    }

    /** Replaces the stage list with four generated stages and adds a seed icon when none is set. */
    private void generateStages() {
        Color col = cropColor.getValue();
        int base = 0xFF000000 | ((int) Math.round(col.getRed() * 255) << 16) | ((int) Math.round(col.getGreen() * 255) << 8) | (int) Math.round(col.getBlue() * 255);
        var project = c.ws.project;
        e.stageTextures.clear();
        for (int i = 0; i < 4; i++) {
            TextureAsset tex = TextureAsset.of(project.uniqueTextureName(e.name + "_stage" + i), TextureTemplates.cropStage(i, 4, base), 16, 16);
            project.textures.add(tex);
            e.stageTextures.add(tex.id);
        }
        if (project.texture(e.texture) == null) {
            TextureAsset seeds = TextureAsset.of(project.uniqueTextureName(e.name + "_seeds"), TextureTemplates.generate("seeds", 0xFFB08850), 16, 16);
            project.textures.add(seeds);
            e.texture = seeds.id;
        }
        c.ws.structureChanged();
        f.changed();
        rebuildStages();
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
        if (stages.getChildren().isEmpty() || stages.getChildren().size() != Math.max(1, e.stageTextures.size())) rebuildStages();
        boolean useModel = e.shape.equals("model");
        preview.setVisible(!useModel);
        modelPreview.setVisible(useModel);
        if (useModel) modelPreview.setModel(c.ws.project.model(e.model));
        else preview.draw(e);
        previewName.setText(e.displayName.isBlank() ? Ids.titleCase(e.name) : e.displayName);
        StringBuilder sb = new StringBuilder();
        if (e.isCrop()) {
            sb.append(t("preview.crop", Math.max(1, Math.min(8, e.stageTextures.size()))));
            if (!e.cropProduce.isBlank()) sb.append('\n').append(t("preview.crop_produce", e.cropProduce, e.cropProduceMin, Math.max(e.cropProduceMin, e.cropProduceMax)));
            previewInfo.setText(sb.toString());
            return;
        }
        sb.append(t("preview.hardness", modstudio.ui.controls.Form.fmt(e.hardness)));
        if (e.light > 0) sb.append("  |  ").append(t("preview.light", e.light));
        if (!e.tool.equals("none")) sb.append("  |  ").append(t("kind." + e.tool)).append(" (").append(t("tier." + e.toolLevel)).append(")");
        if (e.oreGen) sb.append("\n").append(t("preview.ore", e.oreMinY, e.oreMaxY, e.oreCount));
        previewInfo.setText(sb.toString());
    }
}
