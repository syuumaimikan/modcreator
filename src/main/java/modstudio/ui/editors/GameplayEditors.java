package modstudio.ui.editors;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import modstudio.core.Elements.*;
import modstudio.core.Logic;
import modstudio.ui.Ui;
import modstudio.ui.controls.Form;
import modstudio.ui.controls.IdField;
import modstudio.ui.controls.TextureField;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Editors for status effects, enchantments, damage types, paintings and jukebox songs. */
final class GameplayEditors {
    private GameplayEditors() {}

    static final List<String> OPERATIONS = List.of("add_value", "add_multiplied_base", "add_multiplied_total");
    static final List<String> SLOTS = List.of("any", "mainhand", "offhand", "hand", "head", "chest", "legs", "feet", "armor", "body");
    static final List<String> ITEM_SETS = List.of("#minecraft:enchantable/weapon", "#minecraft:enchantable/sword", "#minecraft:enchantable/sharp_weapon",
            "#minecraft:enchantable/mining", "#minecraft:enchantable/mining_loot", "#minecraft:enchantable/durability", "#minecraft:enchantable/armor",
            "#minecraft:enchantable/head_armor", "#minecraft:enchantable/chest_armor", "#minecraft:enchantable/leg_armor",
            "#minecraft:enchantable/foot_armor", "#minecraft:enchantable/bow", "#minecraft:enchantable/crossbow", "#minecraft:enchantable/trident",
            "#minecraft:enchantable/fishing", "#minecraft:enchantable/equippable", "#minecraft:enchantable/vanishing");

    /** Hex colour row with a picker and a text field. */
    static void colorRow(Form f, String label, Supplier<String> get, Consumer<String> set) {
        ColorPicker cp = new ColorPicker(MachineEditors.color(get.get()));
        TextField tf = new TextField(get.get());
        tf.setPrefColumnCount(8);
        cp.setOnAction(x -> tf.setText(MachineEditors.css(cp.getValue())));
        tf.textProperty().addListener((o, a, b) -> {
            set.accept(b.trim());
            try {
                cp.setValue(Color.web(b.trim()));
            } catch (Exception ignored) {
            }
            f.changed();
        });
        HBox row = new HBox(8, cp, tf);
        row.setAlignment(Pos.CENTER_LEFT);
        f.row(label, row);
    }

    static ComboBox<String> editableCombo(List<String> values, String value, Consumer<String> set, Form f) {
        ComboBox<String> box = new ComboBox<>();
        box.getItems().setAll(values);
        box.setEditable(true);
        box.setValue(value);
        box.setPrefWidth(300);
        box.valueProperty().addListener((o, a, b) -> {
            set.accept(b == null ? "" : b.trim());
            f.changed();
        });
        return box;
    }

    // ------------------------------------------------------------------ status effect

    static final class StatusEffectEditor extends EditorBase<StatusEffect> {
        private final VBox modifierRows = new VBox(6);

        StatusEffectEditor(Ctx c, StatusEffect e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.status_effect"));
            nameRow();
            f.text(t("field.display_name"), () -> e.displayName, v -> e.displayName = v);
            localized("name", t("field.display_name"), () -> e.displayName);
            colorRow(f, t("field.effect_color"), () -> e.color, v -> e.color = v);
            f.choice(t("field.effect_category"), List.of("beneficial", "harmful", "neutral"), x -> t("effect_category." + x),
                    () -> e.category, v -> e.category = v);
            f.row(t("field.effect_icon"), new TextureField(c.ws, c.window(), e.icon, () -> e.name + "_icon", "gem", v -> {
                e.icon = v;
                f.changed();
            }), t("help.effect_icon"));

            f.section(t("section.effect_modifiers"), t("help.effect_modifiers"));
            f.add(modifierRows);
            f.add(Ui.button(t("effect.add_modifier"), "mdi2p-plus", () -> {
                e.modifiers.add(new EffectModifier());
                rebuild();
                f.changed();
            }));
            rebuild();

            SystemEditors.logic(this, t("section.effect_tick"), t("help.effect_tick"), e.onTick, () -> t("effect.start.tick", e.tickInterval));
            f.integer(t("field.tick_interval"), 1, 1200, () -> e.tickInterval, v -> e.tickInterval = v, t("help.tick_interval"));

            f.section(t("section.potion"), t("help.potion"));
            f.check(t("field.make_potion"), () -> e.potion, v -> e.potion = v);
            f.text(t("field.potion_name"), () -> e.potionName, v -> e.potionName = v, t("help.potion_name"));
            localized("potion", t("field.potion_name"), () -> e.potionName.isBlank() ? "Potion of " + e.displayName : e.potionName);
            f.integer(t("field.potion_seconds"), 1, 100000, () -> e.potionSeconds, v -> e.potionSeconds = v);
            f.integer(t("field.potion_amplifier"), 0, 255, () -> e.potionAmplifier, v -> e.potionAmplifier = v, t("help.potion_amplifier"));
        }

        private void rebuild() {
            modifierRows.getChildren().clear();
            for (EffectModifier m : new ArrayList<>(e.modifiers)) {
                ComboBox<String> attr = Ui.combo(List.of(Logic.ATTRIBUTES), a -> t("choice." + a));
                attr.setValue(m.attribute);
                attr.valueProperty().addListener((o, a, b) -> {
                    if (b != null) m.attribute = b;
                    f.changed();
                });
                TextField amount = Form.decimalField(m.amount, v -> {
                    m.amount = v;
                    f.changed();
                });
                amount.setPrefColumnCount(6);
                ComboBox<String> op = Ui.combo(OPERATIONS, x -> t("operation." + x));
                op.setValue(m.operation);
                op.valueProperty().addListener((o, a, b) -> {
                    if (b != null) m.operation = b;
                    f.changed();
                });
                Button remove = Ui.iconButton("mdi2c-close", t("action.remove"), () -> {
                    e.modifiers.remove(m);
                    rebuild();
                    f.changed();
                });
                HBox row = new HBox(8, attr, amount, op, remove);
                row.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(attr, Priority.ALWAYS);
                modifierRows.getChildren().add(row);
            }
            if (e.modifiers.isEmpty()) modifierRows.getChildren().add(Ui.label(t("effect.no_modifiers"), "text-muted"));
        }
    }

    // ------------------------------------------------------------------ potion

    static final class PotionEditor extends EditorBase<Potion> {
        private final VBox effectRows = new VBox(6);

        PotionEditor(Ctx c, Potion e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.potion_element"));
            nameRow();
            f.text(t("field.display_name"), () -> e.displayName, v -> e.displayName = v, t("help.potion_display_name"));
            localized("name", t("field.display_name"), () -> e.displayName);
            f.text(t("field.splash_name"), () -> e.splashName, v -> e.splashName = v);
            localized("splash", t("field.splash_name"), () -> e.splashName.isBlank() ? "Splash " + e.displayName : e.splashName);
            f.text(t("field.lingering_name"), () -> e.lingeringName, v -> e.lingeringName = v);
            localized("lingering", t("field.lingering_name"), () -> e.lingeringName.isBlank() ? "Lingering " + e.displayName : e.lingeringName);
            f.text(t("field.arrow_name"), () -> e.arrowName, v -> e.arrowName = v);
            localized("arrow", t("field.arrow_name"), () -> e.arrowName.isBlank() ? "Tipped Arrow (" + e.displayName + ")" : e.arrowName);
            colorRow(f, t("field.potion_color"), () -> e.color, v -> e.color = v);

            f.section(t("section.potion_effects"), t("help.potion_effects"));
            f.add(effectRows);
            f.add(Ui.button(t("potion.add_effect"), "mdi2p-plus", () -> {
                e.effects.add(new PotionEffect());
                rebuild();
                f.changed();
            }));
            rebuild();
        }

        private void rebuild() {
            effectRows.getChildren().clear();
            for (PotionEffect pe : new ArrayList<>(e.effects)) {
                IdField effect = new IdField(env(), IdField.Kind.EFFECT, pe.effect, v -> {
                    pe.effect = v;
                    f.changed();
                });
                var seconds = Form.intSpinner(1, 1000000, pe.seconds, v -> {
                    pe.seconds = v;
                    f.changed();
                });
                seconds.setPrefWidth(110);
                seconds.setTooltip(new javafx.scene.control.Tooltip(t("field.potion_seconds")));
                var amp = Form.intSpinner(0, 255, pe.amplifier, v -> {
                    pe.amplifier = v;
                    f.changed();
                });
                amp.setPrefWidth(80);
                amp.setTooltip(new javafx.scene.control.Tooltip(t("help.potion_amplifier")));
                javafx.scene.control.CheckBox particles = new javafx.scene.control.CheckBox(t("potion.particles"));
                particles.setSelected(pe.particles);
                particles.selectedProperty().addListener((o, a, b) -> {
                    pe.particles = b;
                    f.changed();
                });
                particles.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                javafx.scene.control.CheckBox icon = new javafx.scene.control.CheckBox(t("potion.icon"));
                icon.setSelected(pe.icon);
                icon.selectedProperty().addListener((o, a, b) -> {
                    pe.icon = b;
                    f.changed();
                });
                icon.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                Button remove = Ui.iconButton("mdi2c-close", t("action.remove"), () -> {
                    e.effects.remove(pe);
                    rebuild();
                    f.changed();
                });
                javafx.scene.control.Label secLabel = Ui.label(t("potion.seconds_short"), "text-muted");
                secLabel.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                javafx.scene.control.Label ampLabel = Ui.label(t("potion.level_short"), "text-muted");
                ampLabel.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                HBox top = new HBox(8, effect, remove);
                top.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(effect, Priority.ALWAYS);
                HBox bottom = new HBox(8, secLabel, seconds, ampLabel, amp, particles, icon);
                bottom.setAlignment(Pos.CENTER_LEFT);
                VBox card = new VBox(6, top, bottom);
                card.getStyleClass().add("list-card");
                effectRows.getChildren().add(card);
            }
            if (e.effects.isEmpty()) effectRows.getChildren().add(Ui.label(t("potion.no_effects"), "text-muted"));
        }
    }

    // ------------------------------------------------------------------ enchantment

    static final class EnchantmentEditor extends EditorBase<Enchantment> {
        EnchantmentEditor(Ctx c, Enchantment e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.enchantment"));
            nameRow();
            f.text(t("field.display_name"), () -> e.displayName, v -> e.displayName = v);
            localized("name", t("field.display_name"), () -> e.displayName);
            f.row(t("field.supported_items"), editableCombo(ITEM_SETS, e.supportedItems, v -> e.supportedItems = v, f), t("help.supported_items"));
            List<String> primary = new ArrayList<>(List.of(""));
            primary.addAll(ITEM_SETS);
            f.row(t("field.primary_items"), editableCombo(primary, e.primaryItems, v -> e.primaryItems = v, f), t("help.primary_items"));
            f.choice(t("field.enchant_slot"), SLOTS, x -> t("slot." + x), () -> e.slot, v -> e.slot = v);
            f.integer(t("field.max_level"), 1, 255, () -> e.maxLevel, v -> e.maxLevel = v);
            f.integer(t("field.enchant_weight"), 1, 1024, () -> e.weight, v -> e.weight = v, t("help.enchant_weight"));
            f.integer(t("field.anvil_cost"), 0, 100, () -> e.anvilCost, v -> e.anvilCost = v);
            f.check(t("field.treasure"), () -> e.treasure, v -> e.treasure = v, t("help.treasure"));
            f.check(t("field.curse"), () -> e.curse, v -> e.curse = v);
            f.id(t("field.exclusive_with"), IdField.Kind.ENCHANTMENT, () -> e.exclusiveWith, v -> e.exclusiveWith = v, env(), t("help.exclusive_with"));

            f.section(t("section.enchant_cost"), t("help.enchant_cost"));
            f.integer(t("field.min_cost_base"), 0, 200, () -> e.minCostBase, v -> e.minCostBase = v);
            f.integer(t("field.min_cost_per_level"), 0, 200, () -> e.minCostPerLevel, v -> e.minCostPerLevel = v);
            f.integer(t("field.max_cost_base"), 0, 200, () -> e.maxCostBase, v -> e.maxCostBase = v);
            f.integer(t("field.max_cost_per_level"), 0, 200, () -> e.maxCostPerLevel, v -> e.maxCostPerLevel = v);

            f.section(t("section.enchant_effects"), t("help.enchant_effects"));
            f.decimal(t("field.damage_per_level"), () -> e.damagePerLevel, v -> e.damagePerLevel = v, t("help.damage_per_level"));
            f.decimal(t("field.protection_per_level"), () -> e.protectionPerLevel, v -> e.protectionPerLevel = v, t("help.protection_per_level"));
            List<String> attrs = new ArrayList<>(List.of(""));
            attrs.addAll(List.of(Logic.ATTRIBUTES));
            f.choice(t("field.enchant_attribute"), attrs, a -> a.isEmpty() ? t("choice.none") : t("choice." + a), () -> e.attribute, v -> e.attribute = v);
            f.decimal(t("field.attribute_per_level"), () -> e.attributePerLevel, v -> e.attributePerLevel = v);
            f.showIf(() -> !e.attribute.isBlank());
            f.choice(t("field.attribute_operation"), OPERATIONS, x -> t("operation." + x), () -> e.attributeOperation, v -> e.attributeOperation = v);
            f.showIf(() -> !e.attribute.isBlank());

            SystemEditors.logic(this, t("section.enchant_on_hit"), t("help.enchant_on_hit"), e.onHit, () -> t("enchant.start.hit"));
            SystemEditors.logic(this, t("section.enchant_tick"), t("help.enchant_tick"), e.onTick, () -> t("enchant.start.tick"));
        }
    }

    // ------------------------------------------------------------------ damage type

    static final class DamageTypeEditor extends EditorBase<DamageType> {
        DamageTypeEditor(Ctx c, DamageType e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.damage_type"));
            nameRow();
            f.text(t("field.death_message"), () -> e.deathMessage, v -> e.deathMessage = v, t("help.death_message"));
            localized("death", t("field.death_message"), () -> e.deathMessage);
            f.text(t("field.death_message_attacker"), () -> e.deathMessageAttacker, v -> e.deathMessageAttacker = v, t("help.death_message_attacker"));
            localized("death_attacker", t("field.death_message_attacker"), () -> e.deathMessageAttacker);
            f.choice(t("field.damage_scaling"), List.of("never", "when_caused_by_living_non_player", "always"), x -> t("scaling." + x),
                    () -> e.scaling, v -> e.scaling = v, t("help.damage_scaling"));
            f.choice(t("field.damage_effects"), List.of("hurt", "thorns", "drowning", "burning", "poking", "freezing"), x -> t("damage_effect." + x),
                    () -> e.effects, v -> e.effects = v);
            f.decimal(t("field.exhaustion"), () -> e.exhaustion, v -> e.exhaustion = Math.max(0, v), t("help.exhaustion"));

            f.section(t("section.damage_tags"), t("help.damage_tags"));
            f.check(t("damage_tag.bypasses_armor"), () -> e.bypassesArmor, v -> e.bypassesArmor = v);
            f.check(t("damage_tag.bypasses_shield"), () -> e.bypassesShield, v -> e.bypassesShield = v);
            f.check(t("damage_tag.is_fire"), () -> e.fire, v -> e.fire = v);
            f.check(t("damage_tag.is_projectile"), () -> e.projectile, v -> e.projectile = v);
            f.check(t("damage_tag.is_explosion"), () -> e.explosion, v -> e.explosion = v);
            f.check(t("damage_tag.no_knockback"), () -> e.noKnockback, v -> e.noKnockback = v);
            f.check(t("damage_tag.witch_resistant_to"), () -> e.magic, v -> e.magic = v);
            f.add(Ui.help(t("help.damage_type_use", c.p.meta.modId + ":" + e.name)));
        }
    }

    // ------------------------------------------------------------------ painting

    static final class PaintingEditor extends EditorBase<Painting> {
        private final ImageView preview = new ImageView();

        PaintingEditor(Ctx c, Painting e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            preview.setPreserveRatio(true);
            preview.setSmooth(false);
            HBox strip = new HBox(preview);
            strip.getStyleClass().add("preview-strip");
            strip.setMinHeight(140);
            strip.setAlignment(Pos.CENTER_LEFT);
            f.addTop(strip);
            f.section(t("section.general"), t("help.painting"));
            nameRow();
            f.text(t("field.painting_title"), () -> e.title, v -> e.title = v);
            localized("title", t("field.painting_title"), () -> e.title);
            f.text(t("field.painting_author"), () -> e.author, v -> e.author = v);
            localized("author", t("field.painting_author"), () -> e.author);
            f.row(t("field.texture"), new TextureField(c.ws, c.window(), e.texture, () -> e.name, "block", v -> {
                e.texture = v;
                f.changed();
            }), t("help.painting_texture"));
            f.integer(t("field.painting_width"), 1, 16, () -> e.width, v -> e.width = v);
            f.integer(t("field.painting_height"), 1, 16, () -> e.height, v -> e.height = v);
            f.check(t("field.placeable"), () -> e.placeable, v -> e.placeable = v, t("help.placeable"));
        }

        @Override
        protected void refresh() {
            preview.setImage(c.ws.texture(e.texture, 256));
            double unit = 120.0 / Math.max(e.width, e.height);
            preview.setFitWidth(unit * e.width);
            preview.setFitHeight(unit * e.height);
            preview.setPreserveRatio(false);
        }
    }

    // ------------------------------------------------------------------ jukebox song

    static final class SongEditor extends EditorBase<Song> {
        SongEditor(Ctx c, Song e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.song"));
            nameRow();
            f.text(t("field.song_description"), () -> e.description, v -> e.description = v, t("help.song_description"));
            localized("description", t("field.song_description"), () -> e.description);
            f.id(t("field.song_sound"), IdField.Kind.SOUND, () -> e.sound, v -> e.sound = v, env(), t("help.song_sound"));
            f.decimal(t("field.song_length"), () -> e.lengthSeconds, v -> e.lengthSeconds = Math.max(1, v), t("help.song_length"));
            f.integer(t("field.comparator_output"), 0, 15, () -> e.comparatorOutput, v -> e.comparatorOutput = v, t("help.comparator_output"));
            long discs = c.p.all(Item.class).stream().filter(i -> i.song.equals(e.name)).count();
            f.add(Ui.help(t("song.discs", discs)));
        }
    }
}
