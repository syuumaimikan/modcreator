package modstudio.ui.editors;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import modstudio.core.Elements.Step;
import modstudio.core.Ids;
import modstudio.core.Logic;
import modstudio.core.PreviewModels;
import modstudio.core.Systems.*;
import modstudio.gen.SystemsGen;
import modstudio.ui.Ui;
import modstudio.ui.controls.*;

import java.util.List;
import java.util.function.Supplier;

/** Editors for the game system elements (mobs, effects, worldgen, interface, web requests, machines). */
final class SystemEditors {
    private SystemEditors() {}

    static final List<String> BOSS_COLORS = List.of("pink", "blue", "red", "green", "yellow", "purple", "white");
    static final List<String> BOSS_STYLES = List.of("progress", "notched_6", "notched_10", "notched_12", "notched_20");

    /** Adds a section holding a block / graph / script editor for one list of steps. */
    static void logic(EditorBase<?> ed, String title, String help, List<Step> steps, Supplier<String> start) {
        ed.f.section(title, help);
        ed.f.add(new LogicEditor(ed.env(), steps, ed.f::changed, start));
    }

    // ------------------------------------------------------------------ mob

    static final class MobEditor extends EditorBase<Mob> {
        private EntityPreview3D preview3d;
        private final Label previewName = Ui.label("", "title-4");
        private final Label previewInfo = Ui.muted("");

        MobEditor(Ctx c, Mob e) {
            super(c, e);
        }

        /** Picker for an animation of the chosen model (the list follows the model). */
        private void animation(String label, java.util.function.Supplier<String> get, java.util.function.Consumer<String> set, String help) {
            javafx.scene.control.ComboBox<String> cb = new javafx.scene.control.ComboBox<>();
            cb.setEditable(true);
            cb.setPrefWidth(220);
            cb.setValue(get.get());
            cb.setOnShowing(ev -> {
                java.util.List<String> items = new java.util.ArrayList<>();
                items.add("");
                modstudio.core.Elements.Model md = c.ws.project.model(e.rig);
                if (md != null) for (var a : md.animations) items.add(a.name);
                cb.getItems().setAll(items);
            });
            cb.valueProperty().addListener((o, a, b) -> {
                String v = b == null ? "" : b.trim();
                if (v.equals(get.get())) return;
                set.accept(v);
                f.changed();
            });
            f.row(label, cb, help);
            f.showIf(() -> !e.rig.isBlank());
        }

        @Override
        protected void fill() {
            preview3d = new EntityPreview3D(c.ws, 230, 210, true);
            VBox info = new VBox(4, previewName, previewInfo);
            HBox top = new HBox(16, preview3d, info);
            top.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            top.getStyleClass().add("preview-strip");
            f.addTop(top);

            f.section(t("section.general"), t("help.mob"));
            nameRow();
            f.id(t("field.mob.base"), IdField.Kind.ENTITY, () -> e.base, v -> e.base = v, env(), t("help.mob.base"));
            f.text(t("field.display_name"), () -> e.displayName, v -> e.displayName = v);
            localized("name", t("field.display_name"), () -> e.displayName.isBlank() ? Ids.titleCase(e.name) : e.displayName);
            f.check(t("field.mob.show_name"), () -> e.showName, v -> e.showName = v);

            f.section(t("section.mob.stats"));
            f.decimal(t("field.mob.health"), () -> e.health, v -> e.health = v);
            f.decimal(t("field.mob.damage"), () -> e.damage, v -> e.damage = v);
            f.decimal(t("field.mob.speed"), () -> e.speed, v -> e.speed = v, t("help.mob.speed"));
            f.decimal(t("field.mob.armor"), () -> e.armor, v -> e.armor = v);
            f.decimal(t("field.mob.knockback"), () -> e.knockbackResistance, v -> e.knockbackResistance = v, t("help.mob.knockback"));
            f.decimal(t("field.mob.follow"), () -> e.followRange, v -> e.followRange = v);
            f.decimal(t("field.mob.scale"), () -> e.scale, v -> e.scale = v, t("help.mob.scale"));
            f.check(t("field.mob.glowing"), () -> e.glowing, v -> e.glowing = v);
            f.check(t("field.mob.silent"), () -> e.silent, v -> e.silent = v);
            f.check(t("field.mob.no_ai"), () -> e.noAi, v -> e.noAi = v);
            f.check(t("field.mob.invulnerable"), () -> e.invulnerable, v -> e.invulnerable = v);
            f.check(t("field.mob.fire_immune"), () -> e.fireImmune, v -> e.fireImmune = v);
            f.check(t("field.mob.baby"), () -> e.baby, v -> e.baby = v);
            f.check(t("field.mob.persistent"), () -> e.persistent, v -> e.persistent = v, t("help.mob.persistent"));

            f.section(t("section.mob.look"), t("help.mob.look"));
            f.id(t("field.mob.appearance"), IdField.Kind.ITEM, () -> e.appearance, v -> e.appearance = v, env(), t("help.mob.appearance"));
            f.decimal(t("field.mob.appearance_scale"), () -> e.appearanceScale, v -> e.appearanceScale = v);
            f.id(t("field.mob.mainhand"), IdField.Kind.ITEM, () -> e.mainhand, v -> e.mainhand = v, env());
            f.id(t("field.mob.offhand"), IdField.Kind.ITEM, () -> e.offhand, v -> e.offhand = v, env());
            f.id(t("field.mob.head"), IdField.Kind.ITEM, () -> e.head, v -> e.head = v, env());
            f.id(t("field.mob.chest"), IdField.Kind.ITEM, () -> e.chest, v -> e.chest = v, env());
            f.id(t("field.mob.legs"), IdField.Kind.ITEM, () -> e.legs, v -> e.legs = v, env());
            f.id(t("field.mob.feet"), IdField.Kind.ITEM, () -> e.feet, v -> e.feet = v, env());
            f.check(t("field.mob.drop_equipment"), () -> e.dropEquipment, v -> e.dropEquipment = v);

            f.section(t("section.mob.rig"), t("help.mob.rig"));
            f.choice(t("field.mob.rig"), ModelEditor.modelChoices(c), x -> x.isEmpty() ? t("rig.no_model") : x, () -> e.rig, v -> e.rig = v,
                    t("help.mob.rig_model"));
            f.decimal(t("field.mob.rig_scale"), () -> e.rigScale, v -> e.rigScale = v, t("help.mob.rig_scale"));
            f.showIf(() -> !e.rig.isBlank());
            animation(t("field.mob.anim_idle"), () -> e.idleAnim, v -> e.idleAnim = v, t("help.mob.anim_idle"));
            animation(t("field.mob.anim_walk"), () -> e.walkAnim, v -> e.walkAnim = v, t("help.mob.anim_walk"));
            animation(t("field.mob.anim_hurt"), () -> e.hurtAnim, v -> e.hurtAnim = v, t("help.mob.anim_hurt"));
            animation(t("field.mob.anim_attack"), () -> e.attackAnim, v -> e.attackAnim = v, t("help.mob.anim_attack"));
            animation(t("field.mob.anim_death"), () -> e.deathAnim, v -> e.deathAnim = v, t("help.mob.anim_death"));
            f.id(t("field.mob.loot"), IdField.Kind.LOOT_TABLE, () -> e.lootTable, v -> e.lootTable = v, env(), t("help.mob.loot"));

            f.section(t("section.mob.spawn"), t("help.mob.spawn"));
            f.check(t("field.mob.natural"), () -> e.naturalSpawn, v -> e.naturalSpawn = v);
            f.text(t("field.mob.spawn_biome"), () -> e.spawnBiome, v -> e.spawnBiome = v, t("help.mob.spawn_biome"));
            f.showIf(() -> e.naturalSpawn);
            f.decimal(t("field.mob.spawn_chance"), () -> e.spawnChance, v -> e.spawnChance = v);
            f.showIf(() -> e.naturalSpawn);
            f.integer(t("field.mob.spawn_interval"), 1, 3600, () -> e.spawnInterval, v -> e.spawnInterval = v);
            f.showIf(() -> e.naturalSpawn);
            f.integer(t("field.mob.spawn_max"), 1, 100, () -> e.spawnMax, v -> e.spawnMax = v);
            f.showIf(() -> e.naturalSpawn);
            f.check(t("field.mob.spawn_night"), () -> e.spawnAtNight, v -> e.spawnAtNight = v);
            f.showIf(() -> e.naturalSpawn);
            f.add(Ui.help(t("help.mob.spawn_step", c.ns(), e.name)));

            f.section(t("section.mob.boss"), t("help.mob.boss"));
            f.check(t("field.mob.boss"), () -> e.boss, v -> e.boss = v);
            f.choice(t("field.boss.color"), BOSS_COLORS, x -> t("choice." + x), () -> e.bossColor, v -> e.bossColor = v);
            f.showIf(() -> e.boss);
            f.choice(t("field.boss.style"), BOSS_STYLES, x -> t("choice." + x), () -> e.bossStyle, v -> e.bossStyle = v);
            f.showIf(() -> e.boss);
            f.integer(t("field.boss.radius"), 8, 256, () -> e.bossRadius, v -> e.bossRadius = v);
            f.showIf(() -> e.boss);
            ListEditor<BossPhase> phases = new ListEditor<>(e.phases, BossPhase::new,
                    (p, i) -> t("boss.phase_title", i + 1, p.health), (p, pf) -> {
                pf.integer(t("field.boss.phase_health"), 0, 100, () -> p.health, v -> p.health = v, t("help.boss.phase_health"));
                pf.text(t("field.boss.phase_message"), () -> p.message, v -> p.message = v);
                pf.add(new LogicEditor(env(), p.steps, pf::changed, () -> t("boss.phase_start", p.health)));
            }, f::changed, t("boss.add_phase"));
            f.add(phases);
            f.showIf(() -> e.boss);

            logic(this, t("section.mob.on_spawn"), t("help.mob.on_spawn"), e.onSpawn, () -> t("mob.start.spawn"));
            logic(this, t("section.mob.on_tick"), t("help.mob.on_tick"), e.onTick, () -> t("mob.start.tick", e.tickInterval));
            f.integer(t("field.mob.tick_interval"), 1, 1200, () -> e.tickInterval, v -> e.tickInterval = v);
            logic(this, t("section.mob.on_hurt"), t("help.mob.on_hurt"), e.onHurt, () -> t("mob.start.hurt"));
            logic(this, t("section.mob.on_death"), t("help.mob.on_death"), e.onDeath, () -> t("mob.start.death"));
            logic(this, t("section.mob.on_interact"), t("help.mob.on_interact"), e.onInteract, () -> t("mob.start.interact"));
        }

        @Override
        protected void refresh() {
            if (preview3d == null) return;
            var mdl = PreviewModels.forMob(c.p, e);
            double sc = e.rig.isBlank() ? Math.max(0.1, e.scale) : Math.max(0.1, e.rigScale * e.scale);
            preview3d.setPreview(mdl, "", false, sc, 0.6 * sc, 1.95 * sc, e.idleAnim);
            previewName.setText(e.displayName.isBlank() ? Ids.titleCase(e.name) : e.displayName);
            StringBuilder sb = new StringBuilder();
            sb.append(e.base).append('\n');
            sb.append("HP: ").append(Form.fmt(e.health)).append("  |  DMG: ").append(Form.fmt(e.damage))
                    .append("  |  Scale: ").append(Form.fmt(sc));
            if (e.boss) sb.append('\n').append(t("field.boss"));
            previewInfo.setText(sb.toString());
        }

        @Override
        protected String idText() {
            return c.ns() + ":mobs/" + e.name + "/spawn";
        }
    }

    // ------------------------------------------------------------------ structure

    static final class StructureEditor extends EditorBase<Structure> {
        StructureEditor(Ctx c, Structure e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.structure"));
            nameRow();
            f.section(t("section.structure.blocks"), t("help.structure.blocks"));
            VoxelEditor vox = new VoxelEditor(env(), e, f::changed, t("voxel.keep"));
            f.add(vox);
            javafx.scene.control.Button imp = Ui.button(t("structure.import"), "mdi2f-file-import-outline", () -> {
                javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
                fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(t("structure.nbt_files"), "*.nbt"));
                java.io.File file = fc.showOpenDialog(c.window());
                if (file == null) return;
                try {
                    if (!modstudio.gen.Nbt.importStructure(java.nio.file.Files.readAllBytes(file.toPath()), e)) {
                        Ui.info(c.window(), t("structure.import"), t("structure.import_invalid"));
                        return;
                    }
                    vox.reload();
                    f.changed();
                } catch (Exception ex) {
                    Ui.error(c.window(), t("structure.import"), ex);
                }
            });
            f.add(Ui.hbox(8, imp, Ui.help(t("help.structure.import"))));
            f.text(t("field.structure.chest_loot"), () -> e.chestLoot, v -> e.chestLoot = v, t("help.structure.chest_loot"));

            f.section(t("section.structure.worldgen"), t("help.structure.worldgen"));
            f.check(t("field.structure.generate"), () -> e.generate, v -> e.generate = v);
            f.text(t("field.structure.biomes"), () -> e.biomes, v -> e.biomes = v, t("help.structure.biomes"));
            f.showIf(() -> e.generate);
            f.integer(t("field.structure.spacing"), 2, 4096, () -> e.spacing, v -> e.spacing = v, t("help.structure.spacing"));
            f.showIf(() -> e.generate);
            f.integer(t("field.structure.separation"), 0, 4095, () -> e.separation, v -> e.separation = v);
            f.showIf(() -> e.generate);
            f.choice(t("field.structure.terrain"), List.of("none", "beard_thin", "beard_box", "bury", "encapsulate"),
                    x -> t("terrain." + x), () -> e.terrain, v -> e.terrain = v, t("help.structure.terrain"));
            f.showIf(() -> e.generate);
            f.choice(t("field.structure.step"), List.of("surface_structures", "underground_structures", "underground_decoration",
                    "local_modifications", "strongholds"), x -> x, () -> e.step, v -> e.step = v);
            f.showIf(() -> e.generate);
            f.integer(t("field.structure.y_offset"), -64, 64, () -> e.yOffset, v -> e.yOffset = v, t("help.structure.y_offset"));
            f.showIf(() -> e.generate);
            f.add(Ui.help(t("help.structure.place", c.ns(), e.name)));
        }
    }

    // ------------------------------------------------------------------ biome

    static final class BiomeEditor extends EditorBase<Biome> {
        BiomeEditor(Ctx c, Biome e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.biome"));
            nameRow();
            f.choice(t("field.biome.features"), SystemsGen.featurePresetNames(), x -> t("preset." + x), () -> e.features, v -> e.features = v,
                    t("help.biome.features"));
            f.decimal(t("field.biome.temperature"), () -> e.temperature, v -> e.temperature = v, t("help.biome.temperature"));
            f.decimal(t("field.biome.downfall"), () -> e.downfall, v -> e.downfall = v);
            f.check(t("field.biome.precipitation"), () -> e.precipitation, v -> e.precipitation = v);

            f.section(t("section.biome.colors"), t("help.biome.colors"));
            colorRow(t("field.biome.sky"), () -> e.sky, v -> e.sky = v);
            colorRow(t("field.biome.fog"), () -> e.fog, v -> e.fog = v);
            colorRow(t("field.biome.water"), () -> e.water, v -> e.water = v);
            colorRow(t("field.biome.water_fog"), () -> e.waterFog, v -> e.waterFog = v);
            colorRow(t("field.biome.grass"), () -> e.grass, v -> e.grass = v);
            colorRow(t("field.biome.foliage"), () -> e.foliage, v -> e.foliage = v);

            f.section(t("section.biome.atmosphere"));
            f.id(t("field.biome.particle"), IdField.Kind.PARTICLE, () -> e.ambientParticle, v -> e.ambientParticle = v, env(), t("help.biome.particle"));
            f.decimal(t("field.biome.particle_chance"), () -> e.particleChance, v -> e.particleChance = v);
            f.id(t("field.biome.music"), IdField.Kind.SOUND, () -> e.music, v -> e.music = v, env());
            f.id(t("field.biome.ambient_sound"), IdField.Kind.SOUND, () -> e.ambientSound, v -> e.ambientSound = v, env());

            f.section(t("section.biome.spawns"), t("help.biome.spawns"));
            f.add(new ListEditor<>(e.spawns, MobSpawn::new, (s, i) -> s.entity + "  " + t("spawn.weight", s.weight), (s, sf) -> {
                sf.id(t("field.spawn.entity"), IdField.Kind.ENTITY, () -> s.entity, v -> s.entity = v, env());
                sf.choice(t("field.spawn.category"), List.of("monster", "creature", "ambient", "water_creature", "water_ambient",
                        "underground_water_creature", "axolotls", "misc"), x -> t("category." + x), () -> s.category, v -> s.category = v);
                sf.integer(t("field.spawn.weight"), 1, 1000, () -> s.weight, v -> s.weight = v);
                sf.integer(t("field.spawn.min"), 1, 64, () -> s.min, v -> s.min = v);
                sf.integer(t("field.spawn.max"), 1, 64, () -> s.max, v -> s.max = v);
            }, f::changed, t("spawn.add")));
            f.add(Ui.help(t("help.biome.use")));
        }

        private void colorRow(String label, Supplier<String> get, java.util.function.Consumer<String> set) {
            javafx.scene.control.ColorPicker cp = new javafx.scene.control.ColorPicker();
            try {
                if (!get.get().isBlank()) cp.setValue(Color.web(get.get()));
            } catch (Exception ignored) {
            }
            javafx.scene.control.TextField tf = new javafx.scene.control.TextField(get.get());
            tf.setPrefColumnCount(8);
            tf.setPromptText(t("biome.default_color"));
            cp.setOnAction(x -> {
                Color col = cp.getValue();
                String hex = String.format("#%02X%02X%02X", (int) Math.round(col.getRed() * 255), (int) Math.round(col.getGreen() * 255),
                        (int) Math.round(col.getBlue() * 255));
                tf.setText(hex);
            });
            tf.textProperty().addListener((o, a, b) -> {
                set.accept(b.trim());
                f.changed();
            });
            f.row(label, Ui.hbox(8, cp, tf));
        }
    }

    // ------------------------------------------------------------------ dimension

    static final class DimensionEditor extends EditorBase<Dimension> {
        DimensionEditor(Ctx c, Dimension e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.dimension"));
            nameRow();
            f.choice(t("field.dim.generator"), List.of("noise", "flat", "void"), x -> t("generator." + x), () -> e.generator, v -> e.generator = v);
            f.choice(t("field.dim.noise"), List.of("minecraft:overworld", "minecraft:amplified", "minecraft:large_biomes", "minecraft:caves",
                    "minecraft:floating_islands", "minecraft:nether", "minecraft:end"), x -> t("noise." + x.substring(10)), () -> e.noise, v -> e.noise = v,
                    t("help.dim.noise"));
            f.showIf(() -> e.generator.equals("noise"));
            f.choice(t("field.dim.biome_source"), List.of("single", "checkerboard", "multi_noise", "vanilla_overworld", "vanilla_nether"),
                    x -> t("source." + x), () -> e.biomeSource, v -> e.biomeSource = v);
            f.showIf(() -> e.generator.equals("noise"));
            f.area(t("field.dim.biomes"), () -> String.join("\n", e.biomes), v -> {
                e.biomes.clear();
                for (String l : v.split("\\R")) if (!l.isBlank()) e.biomes.add(l.trim());
            }, 4, t("help.dim.biomes"));
            f.area(t("field.dim.layers"), () -> e.flatLayers, v -> e.flatLayers = v, 5, t("help.dim.layers"));
            f.showIf(() -> e.generator.equals("flat"));

            f.section(t("section.dim.rules"), t("help.dim.rules"));
            f.choice(t("field.dim.effects"), List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                    x -> t("sky." + x.substring(10)), () -> e.effects, v -> e.effects = v);
            f.decimal(t("field.dim.ambient_light"), () -> e.ambientLight, v -> e.ambientLight = v, t("help.dim.ambient_light"));
            f.integer(t("field.dim.fixed_time"), -1, 24000, () -> e.fixedTime, v -> e.fixedTime = v, t("help.dim.fixed_time"));
            f.decimal(t("field.dim.scale"), () -> e.coordinateScale, v -> e.coordinateScale = v, t("help.dim.scale"));
            f.check(t("field.dim.skylight"), () -> e.hasSkylight, v -> e.hasSkylight = v);
            f.check(t("field.dim.ceiling"), () -> e.hasCeiling, v -> e.hasCeiling = v);
            f.check(t("field.dim.ultrawarm"), () -> e.ultrawarm, v -> e.ultrawarm = v, t("help.dim.ultrawarm"));
            f.check(t("field.dim.natural"), () -> e.natural, v -> e.natural = v, t("help.dim.natural"));
            f.check(t("field.dim.bed"), () -> e.bedWorks, v -> e.bedWorks = v);
            f.check(t("field.dim.anchor"), () -> e.respawnAnchorWorks, v -> e.respawnAnchorWorks = v);
            f.check(t("field.dim.piglin"), () -> e.piglinSafe, v -> e.piglinSafe = v);
            f.check(t("field.dim.raids"), () -> e.hasRaids, v -> e.hasRaids = v);
            f.integer(t("field.dim.min_y"), -2032, 2016, () -> e.minY, v -> e.minY = v);
            f.showIf(() -> !e.generator.equals("noise"));
            f.integer(t("field.dim.height"), 16, 4064, () -> e.height, v -> e.height = v, t("help.dim.height"));
            f.showIf(() -> !e.generator.equals("noise"));
            f.add(Ui.help(t("help.dim.travel", c.ns(), e.name)));
        }
    }

    // ------------------------------------------------------------------ particle effect

    static final List<String> SHAPES = List.of("point", "circle", "sphere", "helix", "cone", "spiral", "line", "wave", "square", "star");

    static final class EffectEditor extends EditorBase<Effect> {
        private final Canvas top = new Canvas(220, 220);
        private final Canvas side = new Canvas(220, 220);

        EffectEditor(Ctx c, Effect e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.effect"));
            nameRow();
            f.choice(t("field.effect.shape"), SHAPES, x -> t("shape." + x), () -> e.shape, v -> e.shape = v);
            f.id(t("field.effect.particle"), IdField.Kind.PARTICLE, () -> e.particle, v -> e.particle = v, env(), t("help.effect.particle"));
            f.text(t("field.effect.color"), () -> e.color, v -> e.color = v, t("help.effect.color"));
            f.decimal(t("field.effect.size"), () -> e.size, v -> e.size = v);

            f.section(t("section.effect.shape"));
            f.integer(t("field.effect.points"), 1, 2000, () -> e.points, v -> e.points = v);
            f.decimal(t("field.effect.radius"), () -> e.radius, v -> e.radius = v);
            f.decimal(t("field.effect.height"), () -> e.height, v -> e.height = v);
            f.showIf(() -> e.shape.equals("helix") || e.shape.equals("cone"));
            f.decimal(t("field.effect.length"), () -> e.length, v -> e.length = v);
            f.showIf(() -> e.shape.equals("line") || e.shape.equals("wave"));
            f.decimal(t("field.effect.turns"), () -> e.turns, v -> e.turns = v);
            f.showIf(() -> e.shape.equals("helix") || e.shape.equals("cone") || e.shape.equals("spiral") || e.shape.equals("wave"));
            f.choice(t("field.effect.plane"), List.of("horizontal", "vertical", "facing"), x -> t("plane." + x), () -> e.plane, v -> e.plane = v,
                    t("help.effect.plane"));
            f.decimal(t("field.effect.y_offset"), () -> e.yOffset, v -> e.yOffset = v);
            f.integer(t("field.effect.count"), 1, 100, () -> e.count, v -> e.count = v);
            f.decimal(t("field.effect.spread"), () -> e.spread, v -> e.spread = v);
            f.decimal(t("field.effect.speed"), () -> e.speed, v -> e.speed = v);
            f.check(t("field.effect.force"), () -> e.force, v -> e.force = v, t("help.effect.force"));

            f.section(t("section.preview"));
            VBox a = new VBox(4, Ui.muted(t("effect.view_top")), top);
            VBox b = new VBox(4, Ui.muted(t("effect.view_side")), side);
            Label count = Ui.muted("");
            f.add(new HBox(16, a, b));
            f.add(count);
            f.add(Ui.help(t("help.effect.use", c.ns(), e.name)));
            preview = () -> count.setText(t("effect.commands", SystemsGen.shapePoints(e).size()));
        }

        private Runnable preview = () -> {
        };

        @Override
        protected void refresh() {
            draw(top, true);
            draw(side, false);
            preview.run();
        }

        private void draw(Canvas cv, boolean fromTop) {
            GraphicsContext g = cv.getGraphicsContext2D();
            double w = cv.getWidth(), h = cv.getHeight();
            boolean dark = Ui.dark();
            g.setFill(dark ? Color.web("#161b22") : Color.web("#f6f8fa"));
            g.fillRect(0, 0, w, h);
            g.setStroke(dark ? Color.web("#30363d") : Color.web("#d0d7de"));
            g.strokeLine(w / 2, 0, w / 2, h);
            g.strokeLine(0, h / 2, w, h / 2);
            List<double[]> pts = SystemsGen.shapePoints(e);
            double ext = 0.5;
            for (double[] p : pts) ext = Math.max(ext, Math.max(Math.abs(p[0]), Math.max(Math.abs(p[1]), Math.abs(p[2]))));
            double sc = (Math.min(w, h) / 2 - 12) / ext;
            Color col;
            try {
                col = e.particle.endsWith("dust") ? Color.web(e.color) : Color.web(dark ? "#f0883e" : "#d4761b");
            } catch (Exception ex) {
                col = Color.RED;
            }
            g.setFill(col);
            boolean vertical = e.plane.equals("vertical");
            for (double[] p : pts) {
                double x = p[0], yUp = vertical ? p[2] : p[1], depth = vertical ? p[1] : p[2];
                double px = w / 2 + x * sc;
                double py = fromTop ? h / 2 + depth * sc : h / 2 - yUp * sc;
                g.fillOval(px - 2.5, py - 2.5, 5, 5);
            }
        }

        @Override
        protected String idText() {
            return c.ns() + ":effects/" + e.name;
        }
    }
}
