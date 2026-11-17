package modstudio.ui.editors;

import modstudio.core.Elements.Advancement;
import modstudio.ui.controls.IdField;

import java.util.List;

final class AdvancementEditor extends EditorBase<Advancement> {
    private static final List<String> TRIGGERS = List.of("inventory_changed", "placed_block", "consume_item", "enchanted_item",
            "filled_bucket", "player_killed_entity", "player_hurt_entity", "changed_dimension", "enter_block",
            "item_used_on_block", "recipe_unlocked", "sleeping_in_bed", "tick", "impossible");
    private IdField value;

    AdvancementEditor(Ctx c, Advancement e) {
        super(c, e);
    }

    static IdField.Kind valueKind(String trigger) {
        return switch (trigger) {
            case "inventory_changed", "consume_item", "enchanted_item", "filled_bucket", "item_used_on_block" -> IdField.Kind.ITEM;
            case "placed_block", "enter_block" -> IdField.Kind.BLOCK;
            case "player_killed_entity", "player_hurt_entity" -> IdField.Kind.ENTITY;
            case "changed_dimension" -> IdField.Kind.DIMENSION;
            case "recipe_unlocked" -> IdField.Kind.RECIPE;
            default -> null;
        };
    }

    @Override
    protected void fill() {
        f.section(t("section.display"));
        nameRow();
        f.text(t("field.title"), () -> e.title, v -> e.title = v);
        localized("title", t("field.title"), () -> e.title);
        f.text(t("field.description"), () -> e.description, v -> e.description = v);
        localized("description", t("field.description"), () -> e.description);
        f.id(t("field.icon"), IdField.Kind.ITEM, () -> e.icon, v -> e.icon = v, env());
        f.choice(t("field.frame"), List.of("task", "goal", "challenge"), x -> t("frame." + x), () -> e.frame, v -> e.frame = v);
        f.id(t("field.parent"), IdField.Kind.ADVANCEMENT, () -> e.parent, v -> e.parent = v, env(), t("help.parent"));
        f.text(t("field.background"), () -> e.background, v -> e.background = v, t("help.background"));
        f.showIf(() -> e.parent.isBlank());
        f.check(t("field.show_toast"), () -> e.showToast, v -> e.showToast = v);
        f.check(t("field.announce"), () -> e.announce, v -> e.announce = v);
        f.check(t("field.hidden"), () -> e.hidden, v -> e.hidden = v);

        f.section(t("section.criteria"));
        f.choice(t("field.trigger"), TRIGGERS, x -> t("advtrigger." + x), () -> e.trigger, v -> {
            e.trigger = v;
            IdField.Kind k = valueKind(v);
            if (k != null && value != null) value.setKind(k);
        });
        value = f.id(t("field.trigger_value"), IdField.Kind.ITEM, () -> e.triggerValue, v -> e.triggerValue = v, env(), t("help.trigger_value"));
        IdField.Kind k = valueKind(e.trigger);
        if (k != null) value.setKind(k);
        f.showIf(() -> valueKind(e.trigger) != null);

        f.section(t("section.rewards"));
        f.integer(t("field.reward_xp"), 0, 100000, () -> e.rewardXp, v -> e.rewardXp = v);
        f.id(t("field.reward_function"), IdField.Kind.FUNCTION, () -> e.rewardFunction, v -> e.rewardFunction = v, env(), t("help.reward_function"));
        f.id(t("field.reward_loot"), IdField.Kind.LOOT_TABLE, () -> e.rewardLoot, v -> e.rewardLoot = v, env());
    }
}
