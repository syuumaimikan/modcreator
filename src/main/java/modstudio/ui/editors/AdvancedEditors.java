package modstudio.ui.editors;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import modstudio.core.CodeTemplates;
import modstudio.core.Ids;
import modstudio.core.ModElements.*;
import modstudio.core.PreviewModels;
import modstudio.ui.Ui;
import modstudio.ui.controls.CodeEditor;
import modstudio.ui.controls.EntityPreview3D;
import modstudio.ui.controls.Form;
import modstudio.ui.controls.IdField;
import modstudio.ui.controls.TextureField;

import java.util.List;

/** Editors for mod containers, native entities, configs, mixins, portals, world features, professions, armor trims, banners and custom recipe types. */
final class AdvancedEditors {
    private AdvancedEditors() {}

    // ------------------------------------------------------------------ container

    static final class ContainerEditor extends EditorBase<Container> {
        private final VBox recipesBox = new VBox(8);
        private final VBox slotsBox = new VBox(8);

        ContainerEditor(Ctx c, Container e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.container"));
            nameRow();
            f.text(t("field.display_name"), () -> e.displayName, v -> e.displayName = v);
            localized("name", t("field.display_name"), () -> e.displayName);
            f.id(t("field.container_block"), IdField.Kind.BLOCK, () -> e.block, v -> e.block = v, env());
            f.integer(t("field.container_rows"), 1, 6, () -> e.rows, v -> e.rows = v);
            f.check(t("field.container_custom_slots"), () -> e.useCustomSlots, v -> {
                e.useCustomSlots = v;
                f.changed();
            });
            f.check(t("field.container_right_click"), () -> e.openOnRightClick, v -> e.openOnRightClick = v);
            f.choice(t("field.container_comparator"), List.of("0", "1"),
                    s -> t("comparator." + s), () -> String.valueOf(e.comparatorMode), v -> e.comparatorMode = "1".equals(v) ? 1 : 0);

            f.section(t("section.container_slots"));
            f.add(slotsBox);
            f.add(Ui.button(t("container.add_slot"), "mdi2p-plus", () -> {
                SlotDef sd = new SlotDef();
                sd.index = e.slots.size();
                sd.x = 8 + (sd.index % 9) * 18;
                sd.y = 18 + (sd.index / 9) * 18;
                e.slots.add(sd);
                rebuildSlots();
                f.changed();
            }));
            f.sectionShowIf(() -> e.useCustomSlots);
            rebuildSlots();

            f.section(t("section.container_recipes"), t("help.container_recipes"));
            f.add(recipesBox);
            f.add(Ui.button(t("container.add_recipe"), "mdi2p-plus", () -> {
                e.recipes.add(new ContainerRecipe());
                rebuildRecipes();
                f.changed();
            }));
            rebuildRecipes();

            SystemEditors.logic(this, t("section.container_open"), t("help.container_open"), e.onOpen, () -> t("start.container_open"));
            SystemEditors.logic(this, t("section.container_close"), t("help.container_close"), e.onClose, () -> t("start.container_close"));
            SystemEditors.logic(this, t("section.container_craft"), t("help.container_craft"), e.onCraft, () -> t("start.container_craft"));
        }

        private void rebuildSlots() {
            slotsBox.getChildren().clear();
            for (int i = 0; i < e.slots.size(); i++) {
                SlotDef sd = e.slots.get(i);
                Spinner<Integer> sx = Form.intSpinner(0, 256, sd.x, v -> { sd.x = v; f.changed(); });
                Spinner<Integer> sy = Form.intSpinner(0, 256, sd.y, v -> { sd.y = v; f.changed(); });
                ComboBox<String> kind = Ui.combo(List.of("generic", "input", "fuel", "output", "readonly"), x -> t("slotkind." + x));
                kind.setValue(sd.kind);
                kind.valueProperty().addListener((o, a, b) -> { sd.kind = b; f.changed(); });
                Button del = Ui.iconButton("mdi2c-close", t("action.delete"), () -> {
                    e.slots.remove(sd);
                    rebuildSlots();
                    f.changed();
                });
                slotsBox.getChildren().add(Ui.hbox(8, Ui.label("#" + i), kind, Ui.label("X"), sx, Ui.label("Y"), sy, del));
            }
        }

        private void rebuildRecipes() {
            recipesBox.getChildren().clear();
            for (ContainerRecipe cr : e.recipes) {
                IdField in1 = new IdField(env(), IdField.Kind.ITEM, cr.input, v -> { cr.input = v; f.changed(); });
                IdField in2 = new IdField(env(), IdField.Kind.ITEM, cr.secondInput, v -> { cr.secondInput = v; f.changed(); });
                IdField out = new IdField(env(), IdField.Kind.ITEM, cr.output, v -> { cr.output = v; f.changed(); });
                in1.setPrefWidth(150);
                in2.setPrefWidth(150);
                out.setPrefWidth(150);
                Spinner<Integer> cnt = Form.intSpinner(1, 64, cr.outputCount, v -> { cr.outputCount = v; f.changed(); });
                Spinner<Integer> ticks = Form.intSpinner(1, 72000, cr.processTicks, v -> { cr.processTicks = v; f.changed(); });
                CheckBox fuel = new CheckBox(t("field.requires_fuel"));
                fuel.setSelected(cr.requiresFuel);
                fuel.selectedProperty().addListener((o, a, b) -> { cr.requiresFuel = b; f.changed(); });
                Button del = Ui.iconButton("mdi2c-close", t("action.delete"), () -> {
                    e.recipes.remove(cr);
                    rebuildRecipes();
                    f.changed();
                });
                HBox row = Ui.hbox(8, in1, Ui.label("+"), in2, Ui.label("->"), out, cnt, Ui.label("t:"), ticks, fuel, del);
                row.setAlignment(Pos.CENTER_LEFT);
                recipesBox.getChildren().add(row);
            }
        }
    }

    // ------------------------------------------------------------------ entity

    static final class CustomEntityEditor extends EditorBase<CustomEntity> {
        private EntityPreview3D preview3d;
        private final Label previewName = Ui.label("", "title-4");
        private final Label previewInfo = Ui.muted("");

        CustomEntityEditor(Ctx c, CustomEntity e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            preview3d = new EntityPreview3D(c.ws, 240, 220, true);
            VBox info = new VBox(4, previewName, previewInfo);
            HBox top = new HBox(16, preview3d, info);
            top.setAlignment(Pos.CENTER_LEFT);
            top.getStyleClass().add("preview-strip");
            f.addTop(top);

            f.section(t("section.general"), t("help.entity"));
            nameRow();
            f.text(t("field.display_name"), () -> e.displayName, v -> e.displayName = v);
            localized("name", t("field.display_name"), () -> e.displayName);
            f.choice(t("field.entity_kind"), CustomEntity.KINDS, k -> t("entitykind." + k), () -> e.kind, v -> {
                e.kind = v;
                f.changed();
            });
            f.choice(t("field.entity_category"), CustomEntity.CATEGORIES, k -> t("entitycat." + k), () -> e.category, v -> e.category = v);
            f.row(t("field.entity_texture"), new TextureField(c.ws, c.window(), e.texture, () -> e.name + "_skin", "mob", v -> {
                e.texture = v;
                f.changed();
            }));
            f.choice(t("field.model"), ModelEditor.modelChoices(c), x -> x.isEmpty() ? t("model.none") : x, () -> e.model, v -> e.model = v);
            f.choice(t("field.base_appearance"), List.of("biped", "quadruped", "slime"), k -> t("entity_app." + k), () -> e.baseAppearance, v -> e.baseAppearance = v);
            f.showIf(() -> e.model.isBlank() && e.isLivingMob());
            f.text(t("field.idle_anim"), () -> e.idleAnim, v -> e.idleAnim = v.trim(), t("help.entity_anims"));
            f.showIf(() -> !e.model.isBlank());
            f.text(t("field.walk_anim"), () -> e.walkAnim, v -> e.walkAnim = v.trim());
            f.showIf(() -> !e.model.isBlank());
            f.text(t("field.attack_anim"), () -> e.attackAnim, v -> e.attackAnim = v.trim());
            f.showIf(() -> !e.model.isBlank());

            f.section(t("section.entity_attributes"));
            f.decimal(t("field.entity_width"), () -> e.width, v -> e.width = Math.max(0.2, v));
            f.decimal(t("field.entity_height"), () -> e.height, v -> e.height = Math.max(0.2, v));
            f.decimal(t("field.health"), () -> e.health, v -> e.health = Math.max(1, v));
            f.decimal(t("field.speed"), () -> e.speed, v -> e.speed = Math.max(0.05, v));
            f.decimal(t("field.damage"), () -> e.attackDamage, v -> e.attackDamage = Math.max(0, v));
            f.decimal(t("field.follow_range"), () -> e.followRange, v -> e.followRange = Math.max(1, v));
            f.decimal(t("field.knockback_resistance"), () -> e.knockbackResistance, v -> e.knockbackResistance = Math.max(0, Math.min(1, v)));
            f.decimal(t("field.entity_armor"), () -> e.armor, v -> e.armor = Math.max(0, Math.min(30, v)));
            f.decimal(t("field.entity_scale"), () -> e.scale, v -> e.scale = Math.max(0.2, Math.min(4.0, v)), t("help.entity_scale"));
            f.decimal(t("field.step_height"), () -> e.stepHeight, v -> e.stepHeight = Math.max(0, Math.min(4.0, v)));
            f.integer(t("field.xp_reward"), -1, 10000, () -> e.xpReward, v -> e.xpReward = v, t("help.xp_reward"));
            f.check(t("field.fire_immune"), () -> e.fireImmune, v -> e.fireImmune = v);
            f.check(t("field.glowing"), () -> e.glowing, v -> e.glowing = v);
            f.check(t("field.silent"), () -> e.silent, v -> e.silent = v);
            f.check(t("field.baby"), () -> e.baby, v -> e.baby = v);
            f.check(t("field.invulnerable"), () -> e.invulnerable, v -> e.invulnerable = v);
            f.check(t("field.pushable"), () -> e.pushable, v -> e.pushable = v);
            f.check(t("field.can_swim"), () -> e.canSwim, v -> e.canSwim = v);
            f.check(t("field.can_fly"), () -> e.canFly, v -> e.canFly = v);
            f.check(t("field.burns_in_daylight"), () -> e.burnsInDaylight, v -> e.burnsInDaylight = v);
            f.check(t("field.rideable"), () -> e.rideable, v -> e.rideable = v);
            f.check(t("field.tameable"), () -> e.tameable, v -> e.tameable = v);
            f.id(t("field.tame_item"), IdField.Kind.ITEM_OR_TAG, () -> e.tameItem, v -> e.tameItem = v, env());
            f.showIf(() -> e.tameable);
            f.check(t("field.boss"), () -> e.boss, v -> e.boss = v);
            f.choice(t("field.boss_color"), List.of("pink", "blue", "red", "green", "yellow", "purple", "white"),
                    k -> t("bosscolor." + k), () -> e.bossColor, v -> e.bossColor = v);
            f.showIf(() -> e.boss);
            f.choice(t("field.boss_style"), List.of("progress", "notched_6", "notched_10", "notched_12", "notched_20"),
                    k -> t("bossstyle." + k), () -> e.bossStyle, v -> e.bossStyle = v);
            f.showIf(() -> e.boss);
            f.id(t("field.mainhand_item"), IdField.Kind.ITEM, () -> e.mainhand, v -> e.mainhand = v, env(), t("help.entity_equipment"));
            f.id(t("field.head_item"), IdField.Kind.ITEM, () -> e.head, v -> e.head = v, env());
            f.id(t("field.hurt_sound"), IdField.Kind.SOUND, () -> e.hurtSound, v -> e.hurtSound = v, env());
            f.id(t("field.death_sound"), IdField.Kind.SOUND, () -> e.deathSound, v -> e.deathSound = v, env());
            f.id(t("field.ambient_sound"), IdField.Kind.SOUND, () -> e.ambientSound, v -> e.ambientSound = v, env());
            f.id(t("field.loot_table"), IdField.Kind.LOOT_TABLE, () -> e.lootTable, v -> e.lootTable = v, env());
            f.sectionShowIf(e::isLivingMob);

            f.section(t("section.entity_projectile"));
            f.decimal(t("field.projectile_damage"), () -> e.projectileDamage, v -> e.projectileDamage = Math.max(0, v));
            f.sectionShowIf(e::isProjectile);

            f.section(t("section.entity_ai"));
            FlowPane goals = new FlowPane(12, 8);
            for (String g : CustomEntity.AI_GOALS) {
                CheckBox cb = new CheckBox(t("aigoal." + g));
                cb.setSelected(e.aiGoals.contains(g));
                cb.selectedProperty().addListener((o, a, b) -> {
                    if (b && !e.aiGoals.contains(g)) e.aiGoals.add(g);
                    else if (!b) e.aiGoals.remove(g);
                    f.changed();
                });
                goals.getChildren().add(cb);
            }
            f.add(goals);
            f.sectionShowIf(e::isLivingMob);

            f.section(t("section.entity_spawn"));
            f.check(t("field.spawn_egg"), () -> e.spawnEgg, v -> e.spawnEgg = v);
            GameplayEditors.colorRow(f, t("field.egg_primary"), () -> e.eggPrimary, v -> e.eggPrimary = v);
            GameplayEditors.colorRow(f, t("field.egg_secondary"), () -> e.eggSecondary, v -> e.eggSecondary = v);
            f.choice(t("field.creative_tab"), ItemEditor.creativeTabs(c), id -> ItemEditor.tabName(c, id), () -> e.creativeTab, v -> e.creativeTab = v);
            f.check(t("field.natural_spawn"), () -> e.naturalSpawn, v -> e.naturalSpawn = v);
            f.text(t("field.spawn_biomes"), () -> e.spawnBiomes, v -> e.spawnBiomes = v);
            f.showIf(() -> e.naturalSpawn);
            f.integer(t("field.spawn_weight"), 1, 200, () -> e.spawnWeight, v -> e.spawnWeight = v);
            f.showIf(() -> e.naturalSpawn);
            f.integer(t("field.spawn_min"), 1, 20, () -> e.spawnMin, v -> e.spawnMin = v);
            f.showIf(() -> e.naturalSpawn);
            f.integer(t("field.spawn_max"), 1, 20, () -> e.spawnMax, v -> e.spawnMax = v);
            f.showIf(() -> e.naturalSpawn);
            f.sectionShowIf(e::isLivingMob);

            SystemEditors.logic(this, t("section.mob_spawn"), t("help.mob_spawn"), e.onSpawn, () -> t("start.mob_spawn"));
            SystemEditors.logic(this, t("section.mob_hurt"), t("help.mob_hurt"), e.onHurt, () -> t("start.mob_hurt"));
            SystemEditors.logic(this, t("section.mob_death"), t("help.mob_death"), e.onDeath, () -> t("start.mob_death"));
            SystemEditors.logic(this, t("section.mob_interact"), t("help.mob_interact"), e.onInteract, () -> t("start.mob_interact"));
        }

        @Override
        protected void refresh() {
            if (preview3d == null) return;
            var mdl = PreviewModels.forCustomEntity(c.p, e);
            double sc = e.scale <= 0 ? 1.0 : e.scale;
            preview3d.setPreview(mdl, e.texture, false, sc, e.width * sc, e.height * sc, e.idleAnim);
            previewName.setText(e.displayName.isBlank() ? Ids.titleCase(e.name) : e.displayName);
            StringBuilder sb = new StringBuilder();
            sb.append(t("entitykind." + e.kind)).append("  |  ").append(t("entitycat." + e.category));
            if (e.isLivingMob()) {
                sb.append('\n').append("HP: ").append(Form.fmt(e.health))
                        .append("  |  DMG: ").append(Form.fmt(e.attackDamage))
                        .append("  |  Size: ").append(Form.fmt(e.width * sc)).append(" x ").append(Form.fmt(e.height * sc));
                if (e.boss) sb.append('\n').append(t("field.boss")).append(" (").append(t("bosscolor." + e.bossColor)).append(')');
            } else {
                sb.append('\n').append(t("field.projectile_damage")).append(": ").append(Form.fmt(e.projectileDamage));
            }
            previewInfo.setText(sb.toString());
        }
    }

    // ------------------------------------------------------------------ mod config

    static final class ModConfigEditor extends EditorBase<ModConfig> {
        private final VBox entries = new VBox(8);

        ModConfigEditor(Ctx c, ModConfig e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.mod_config"));
            nameRow();
            f.choice(t("field.config_scope"), List.of("common", "client", "server"), s -> t("cfgscope." + s), () -> e.scope, v -> e.scope = v);
            f.text(t("field.config_file"), () -> e.fileName, v -> e.fileName = v);
            f.section(t("section.config_entries"));
            f.add(entries);
            f.add(Ui.button(t("config.add_entry"), "mdi2p-plus", () -> {
                e.entries.add(new ConfigEntry());
                rebuild();
                f.changed();
            }));
            rebuild();
        }

        private void rebuild() {
            entries.getChildren().clear();
            for (ConfigEntry en : e.entries) {
                TextField key = new TextField(en.key);
                key.setPrefWidth(190);
                HBox.setHgrow(key, Priority.ALWAYS);
                key.textProperty().addListener((o, a, b) -> { en.key = b.trim(); f.changed(); });
                ComboBox<String> kind = Ui.combo(List.of("bool", "int", "double", "string"), k -> t("cfgkind." + k));
                kind.setValue(en.kind);
                kind.valueProperty().addListener((o, a, b) -> { en.kind = b; f.changed(); });
                TextField val = new TextField(en.value);
                val.setPrefWidth(120);
                val.textProperty().addListener((o, a, b) -> { en.value = b.trim(); f.changed(); });
                TextField min = new TextField(en.min);
                min.setPrefWidth(75);
                min.setPromptText(t("loot.col_min"));
                min.textProperty().addListener((o, a, b) -> { en.min = b.trim(); f.changed(); });
                TextField max = new TextField(en.max);
                max.setPrefWidth(75);
                max.setPromptText(t("loot.col_max"));
                max.textProperty().addListener((o, a, b) -> { en.max = b.trim(); f.changed(); });
                TextField comment = new TextField(en.comment);
                comment.setPromptText(t("field.comment"));
                comment.setPrefWidth(220);
                HBox.setHgrow(comment, Priority.ALWAYS);
                comment.textProperty().addListener((o, a, b) -> { en.comment = b; f.changed(); });
                Button del = Ui.iconButton("mdi2c-close", t("action.delete"), () -> {
                    e.entries.remove(en);
                    rebuild();
                    f.changed();
                });
                HBox r1 = Ui.hbox(8, key, kind, val, del);
                r1.setAlignment(Pos.CENTER_LEFT);
                HBox r2 = Ui.hbox(8, min, max, comment);
                r2.setAlignment(Pos.CENTER_LEFT);
                VBox card = new VBox(6, r1, r2);
                card.getStyleClass().add("list-card");
                entries.getChildren().add(card);
            }
        }
    }

    // ------------------------------------------------------------------ mixin

    static final class MixinEditor extends EditorBase<MixinSpec> {
        MixinEditor(Ctx c, MixinSpec e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.mixin"));
            nameRow();
            f.check(t("field.mixin_enabled"), () -> e.enabled, v -> e.enabled = v);
            f.choice(t("field.mixin_side"), List.of("common", "client", "server"), s -> t("cfgscope." + s), () -> e.side, v -> e.side = v);
            CodeEditor rawEd = new CodeEditor("java");
            f.check(t("field.mixin_raw"), () -> e.rawMode, v -> {
                e.rawMode = v;
                if (v && (e.rawCode == null || e.rawCode.isBlank())) {
                    e.rawCode = CodeTemplates.mixinPreset("inject_head", e);
                    rawEd.setText(e.rawCode);
                }
                f.changed();
            });

            f.text(t("field.mixin_target"), () -> e.targetClass, v -> e.targetClass = v.trim());
            f.showIf(() -> !e.rawMode);
            f.check(t("field.mixin_targets_str"), () -> e.useTargetsString, v -> e.useTargetsString = v);
            f.showIf(() -> !e.rawMode);
            f.text(t("field.mixin_method"), () -> e.methodTarget, v -> e.methodTarget = v.trim());
            f.showIf(() -> !e.rawMode);
            f.choice(t("field.mixin_at"), List.of("HEAD", "TAIL", "RETURN"), s -> s, () -> e.injectAt, v -> e.injectAt = v);
            f.showIf(() -> !e.rawMode);
            f.check(t("field.mixin_cancellable"), () -> e.cancellable, v -> e.cancellable = v);
            f.showIf(() -> !e.rawMode);

            f.section(t("section.mixin_code"));
            f.sectionShowIf(() -> !e.rawMode);
            CodeEditor code = new CodeEditor("java");
            code.setText(e.code);
            code.setOnTextChanged(v -> {
                e.code = v;
                f.changed();
            });
            code.setPrefHeight(220);
            f.add(code);

            f.section(t("section.mixin_raw_code"), t("help.mixin_raw"));
            f.sectionShowIf(() -> e.rawMode);
            ComboBox<String> mxPreset = Ui.combo(CodeTemplates.MIXIN_PRESETS, k -> t("mixinpreset." + k));
            Button insertMx = Ui.button(t("code.apply_template"), "mdi2f-file-code-outline", () -> {
                String k = mxPreset.getValue();
                if (k == null) return;
                e.rawCode = CodeTemplates.mixinPreset(k, e);
                rawEd.setText(e.rawCode);
                f.changed();
            });
            f.add(Ui.hbox(8, mxPreset, insertMx));
            rawEd.setText(e.rawCode == null ? "" : e.rawCode);
            rawEd.setOnTextChanged(v -> {
                e.rawCode = v;
                f.changed();
            });
            rawEd.setPrefHeight(360);
            f.add(rawEd);
        }
    }

    // ------------------------------------------------------------------ portal

    static final class PortalEditor extends EditorBase<Portal> {
        PortalEditor(Ctx c, Portal e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.portal"));
            nameRow();
            f.text(t("field.display_name"), () -> e.displayName, v -> e.displayName = v);
            localized("name", t("field.display_name"), () -> e.displayName);
            f.text(t("field.portal_dimension"), () -> e.targetDimension, v -> e.targetDimension = v.trim());
            f.id(t("field.portal_frame"), IdField.Kind.BLOCK, () -> e.frameBlock, v -> e.frameBlock = v, env());
            f.check(t("field.portal_generate_igniter"), () -> e.generateIgniter, v -> e.generateIgniter = v);
            f.id(t("field.portal_igniter_item"), IdField.Kind.ITEM, () -> e.igniterItem, v -> e.igniterItem = v, env());
            f.showIf(() -> !e.generateIgniter);
            f.choice(t("field.creative_tab"), ItemEditor.creativeTabs(c), id -> ItemEditor.tabName(c, id), () -> e.creativeTab, v -> e.creativeTab = v);
            f.showIf(() -> e.generateIgniter);
            f.row(t("field.portal_texture"), new TextureField(c.ws, c.window(), e.texture, () -> e.name + "_portal", "block", v -> {
                e.texture = v;
                f.changed();
            }));
            f.row(t("field.portal_igniter_texture"), new TextureField(c.ws, c.window(), e.igniterTexture, () -> e.name + "_igniter", "gem", v -> {
                e.igniterTexture = v;
                f.changed();
            }));
            f.showIf(() -> e.generateIgniter);
            GameplayEditors.colorRow(f, t("field.tint"), () -> e.tintColor, v -> e.tintColor = v);
        }
    }

    // ------------------------------------------------------------------ worldgen feature

    static final class WorldFeatureEditor extends EditorBase<WorldFeature> {
        WorldFeatureEditor(Ctx c, WorldFeature e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.world_feature"));
            nameRow();
            f.choice(t("field.feature_type"), WorldFeature.TYPES, s -> t("featuretype." + s), () -> e.featureType, v -> {
                e.featureType = v;
                f.changed();
            });
            f.choice(t("field.feature_step"), WorldFeature.STEPS, s -> t("featurestep." + s), () -> e.step, v -> e.step = v);
            f.text(t("field.spawn_biomes"), () -> e.biomes, v -> e.biomes = v.trim());
            f.id(t("field.feature_block"), IdField.Kind.BLOCK, () -> e.block, v -> e.block = v, env());
            f.text(t("field.feature_replace"), () -> e.replaceTarget, v -> e.replaceTarget = v.trim());
            f.showIf(() -> "ore".equals(e.featureType));
            f.id(t("field.feature_trunk"), IdField.Kind.BLOCK, () -> e.trunkBlock, v -> e.trunkBlock = v, env());
            f.showIf(() -> "tree".equals(e.featureType));
            f.id(t("field.feature_leaves"), IdField.Kind.BLOCK, () -> e.leavesBlock, v -> e.leavesBlock = v, env());
            f.showIf(() -> "tree".equals(e.featureType));

            f.section(t("section.feature_placement"));
            f.integer(t("field.feature_count"), 1, 128, () -> e.count, v -> e.count = v);
            f.integer(t("field.feature_size"), 1, 64, () -> e.size, v -> e.size = v);
            f.choice(t("field.ore_distribution"), List.of("trapezoid", "uniform"), x -> t("ore_dist." + x), () -> e.distribution, v -> e.distribution = v);
            f.showIf(() -> "ore".equals(e.featureType));
            f.integer(t("field.ore_min_y"), -64, 320, () -> e.minY, v -> e.minY = v);
            f.showIf(() -> "ore".equals(e.featureType));
            f.integer(t("field.ore_max_y"), -64, 320, () -> e.maxY, v -> e.maxY = v);
            f.showIf(() -> "ore".equals(e.featureType));
            f.check(t("field.ore_prevent_surface"), () -> e.preventSurface, v -> e.preventSurface = v, t("help.ore_prevent_surface"));
            f.showIf(() -> "ore".equals(e.featureType));
            f.integer(t("field.ore_surface_depth"), 0, 64, () -> e.surfaceDepth, v -> e.surfaceDepth = v, t("help.ore_surface_depth"));
            f.showIf(() -> "ore".equals(e.featureType) && e.preventSurface);
            f.decimal(t("field.ore_discard_air"), () -> e.discardAir, v -> e.discardAir = Math.max(0, Math.min(1, v)), t("help.ore_discard_air"));
            f.showIf(() -> "ore".equals(e.featureType));
            f.integer(t("field.xz_spread"), 1, 16, () -> e.xzSpread, v -> e.xzSpread = v);
            f.showIf(() -> "patch".equals(e.featureType));
            f.integer(t("field.y_spread"), 0, 16, () -> e.ySpread, v -> e.ySpread = v);
            f.showIf(() -> "patch".equals(e.featureType));
            f.integer(t("field.tree_height"), 2, 24, () -> e.trunkHeight, v -> e.trunkHeight = v);
            f.showIf(() -> "tree".equals(e.featureType));
            f.integer(t("field.tree_foliage"), 1, 8, () -> e.foliageRadius, v -> e.foliageRadius = v);
            f.showIf(() -> "tree".equals(e.featureType));
            f.check(t("field.feature_rare"), () -> e.rareFilter, v -> e.rareFilter = v);
        }
    }

    // ------------------------------------------------------------------ villager profession

    static final class ProfessionEditor extends EditorBase<Profession> {
        private final VBox trades = new VBox(8);

        ProfessionEditor(Ctx c, Profession e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.profession"));
            nameRow();
            f.text(t("field.display_name"), () -> e.displayName, v -> e.displayName = v);
            localized("name", t("field.display_name"), () -> e.displayName);
            f.id(t("field.profession_workstation"), IdField.Kind.BLOCK, () -> e.workstationBlock, v -> e.workstationBlock = v, env());
            f.id(t("field.work_sound"), IdField.Kind.SOUND, () -> e.workSound, v -> e.workSound = v, env());

            f.section(t("section.profession_trades"));
            f.add(trades);
            f.add(Ui.button(t("trader.add_trade"), "mdi2p-plus", () -> {
                e.trades.add(new ProfessionTrade());
                rebuild();
                f.changed();
            }));
            rebuild();
        }

        private void rebuild() {
            trades.getChildren().clear();
            for (ProfessionTrade tr : e.trades) {
                Spinner<Integer> lvl = Form.intSpinner(1, 5, tr.level, v -> { tr.level = v; f.changed(); });
                lvl.setPrefWidth(60);
                IdField cost = new IdField(env(), IdField.Kind.ITEM, tr.costA, v -> { tr.costA = v; f.changed(); });
                cost.setPrefWidth(210);
                HBox.setHgrow(cost, Priority.ALWAYS);
                Spinner<Integer> ca = Form.intSpinner(1, 64, tr.countA, v -> { tr.countA = v; f.changed(); });
                ca.setPrefWidth(64);
                IdField costB = new IdField(env(), IdField.Kind.ITEM, tr.costB, v -> { tr.costB = v; f.changed(); });
                costB.setPrefWidth(190);
                HBox.setHgrow(costB, Priority.ALWAYS);
                Spinner<Integer> cb = Form.intSpinner(0, 64, tr.countB, v -> { tr.countB = v; f.changed(); });
                cb.setPrefWidth(64);
                IdField res = new IdField(env(), IdField.Kind.ITEM, tr.result, v -> { tr.result = v; f.changed(); });
                res.setPrefWidth(220);
                HBox.setHgrow(res, Priority.ALWAYS);
                Spinner<Integer> rc = Form.intSpinner(1, 64, tr.resultCount, v -> { tr.resultCount = v; f.changed(); });
                rc.setPrefWidth(64);
                Spinner<Integer> uses = Form.intSpinner(1, 999, tr.maxUses, v -> { tr.maxUses = v; f.changed(); });
                uses.setPrefWidth(72);
                Spinner<Integer> xp = Form.intSpinner(0, 1000, tr.xp, v -> { tr.xp = v; f.changed(); });
                xp.setPrefWidth(72);
                Button del = Ui.iconButton("mdi2c-close", t("action.delete"), () -> {
                    e.trades.remove(tr);
                    rebuild();
                    f.changed();
                });
                HBox r1 = Ui.hbox(6, Ui.label("Lv"), lvl, cost, ca, Ui.label("+"), costB, cb, del);
                r1.setAlignment(Pos.CENTER_LEFT);
                HBox r2 = Ui.hbox(8, Ui.label("->"), res, rc, Ui.label(t("field.max_uses")), uses, Ui.label(t("field.xp")), xp);
                r2.setAlignment(Pos.CENTER_LEFT);
                VBox card = new VBox(6, r1, r2);
                card.getStyleClass().add("list-card");
                trades.getChildren().add(card);
            }
        }
    }

    // ------------------------------------------------------------------ armor trim / banner / recipe type

    static final class ArmorTrimEditor extends EditorBase<ArmorTrim> {
        ArmorTrimEditor(Ctx c, ArmorTrim e) { super(c, e); }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.armor_trim"));
            nameRow();
            f.text(t("field.display_name"), () -> e.displayName, v -> e.displayName = v);
            localized("name", t("field.display_name"), () -> e.displayName);
            f.check(t("field.trim_generate_template"), () -> e.generateTemplate, v -> e.generateTemplate = v);
            f.id(t("field.trim_item"), IdField.Kind.ITEM, () -> e.item, v -> e.item = v, env());
            f.showIf(() -> !e.generateTemplate);
            f.choice(t("field.creative_tab"), ItemEditor.creativeTabs(c), id -> ItemEditor.tabName(c, id), () -> e.creativeTab, v -> e.creativeTab = v);
            f.showIf(() -> e.generateTemplate);
            f.row(t("field.texture"), new TextureField(c.ws, c.window(), e.texture, () -> e.name + "_trim", "gem", v -> {
                e.texture = v;
                f.changed();
            }));
            f.check(t("field.trim_decal"), () -> e.decal, v -> e.decal = v);
        }
    }

    static final class BannerPatternEditor extends EditorBase<BannerPattern> {
        BannerPatternEditor(Ctx c, BannerPattern e) { super(c, e); }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.banner_pattern"));
            nameRow();
            f.text(t("field.display_name"), () -> e.displayName, v -> e.displayName = v);
            localized("name", t("field.display_name"), () -> e.displayName);
            f.row(t("field.texture"), new TextureField(c.ws, c.window(), e.texture, () -> e.name + "_banner", "block", v -> {
                e.texture = v;
                f.changed();
            }));
        }
    }

    static final class RecipeTypeEditor extends EditorBase<CustomRecipeType> {
        RecipeTypeEditor(Ctx c, CustomRecipeType e) { super(c, e); }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.recipe_type"));
            nameRow();
            f.text(t("field.display_name"), () -> e.displayName, v -> e.displayName = v);
            f.integer(t("field.recipe_type_inputs"), 1, 4, () -> e.maxInputs, v -> e.maxInputs = v);
            f.check(t("field.recipe_type_timed"), () -> e.timed, v -> e.timed = v);
        }
    }
}
