package modstudio.core;

import java.util.function.Supplier;

public enum ElementType {
    ITEM("item", Elements.Item.class, Elements.Item::new),
    BLOCK("block", Elements.Block.class, Elements.Block::new),
    ARMOR_MATERIAL("armor_material", Elements.ArmorMaterial.class, Elements.ArmorMaterial::new),
    STATUS_EFFECT("status_effect", Elements.StatusEffect.class, Elements.StatusEffect::new),
    POTION("potion", Elements.Potion.class, Elements.Potion::new),
    ENCHANTMENT("enchantment", Elements.Enchantment.class, Elements.Enchantment::new),
    DAMAGE_TYPE("damage_type", Elements.DamageType.class, Elements.DamageType::new),
    PAINTING("painting", Elements.Painting.class, Elements.Painting::new),
    SONG("song", Elements.Song.class, Elements.Song::new),
    MODEL("model", Elements.Model.class, Elements.Model::new),
    SHADER("shader", Systems.Shader.class, Systems.Shader::new),
    CREATIVE_TAB("creative_tab", Elements.CreativeTab.class, Elements.CreativeTab::new),
    SOUND("sound", Elements.Sound.class, Elements.Sound::new),
    RECIPE("recipe", Elements.Recipe.class, Elements.Recipe::new),
    LOOT_TABLE("loot_table", Elements.LootTable.class, Elements.LootTable::new),
    ADVANCEMENT("advancement", Elements.Advancement.class, Elements.Advancement::new),
    TAG("tag", Elements.Tag.class, Elements.Tag::new),
    FUNCTION("function", Elements.Function.class, Elements.Function::new),
    PREDICATE("predicate", Elements.Predicate.class, Elements.Predicate::new),
    ITEM_MODIFIER("item_modifier", Elements.ItemModifier.class, Elements.ItemModifier::new),
    PROCEDURE("procedure", Elements.Procedure.class, Elements.Procedure::new),
    COMMAND("command", Elements.Command.class, Elements.Command::new),
    KEYBIND("keybind", Systems.KeyBind.class, Systems.KeyBind::new),
    CODE_FILE("code_file", Elements.CodeFile.class, Elements.CodeFile::new),
    MOB("mob", Systems.Mob.class, Systems.Mob::new),
    EFFECT("effect", Systems.Effect.class, Systems.Effect::new),
    STRUCTURE("structure", Systems.Structure.class, Systems.Structure::new),
    BIOME("biome", Systems.Biome.class, Systems.Biome::new),
    DIMENSION("dimension", Systems.Dimension.class, Systems.Dimension::new),
    MENU("menu", Systems.Menu.class, Systems.Menu::new),
    HUD("hud", Systems.Hud.class, Systems.Hud::new),
    API_REQUEST("api_request", Systems.ApiRequest.class, Systems.ApiRequest::new),
    RESOURCE("resource", Systems.Resource.class, Systems.Resource::new),
    MACHINE("machine", Systems.Machine.class, Systems.Machine::new),
    MULTIBLOCK("multiblock", Systems.Multiblock.class, Systems.Multiblock::new),
    CUTSCENE("cutscene", Systems.Cutscene.class, Systems.Cutscene::new),
    DIALOG("dialog", Systems.Dialog.class, Systems.Dialog::new),
    TRADER("trader", Systems.Trader.class, Systems.Trader::new),
    THEME("theme", Systems.Theme.class, Systems.Theme::new),
    ICON_FONT("icon_font", Systems.IconFont.class, Systems.IconFont::new),
    SCREEN("screen", Systems.CustomScreen.class, Systems.CustomScreen::new);

    public final String id;
    public final Class<? extends Element> cls;
    private final Supplier<? extends Element> factory;

    ElementType(String id, Class<? extends Element> cls, Supplier<? extends Element> factory) {
        this.id = id;
        this.cls = cls;
        this.factory = factory;
    }

    public Element create() {
        return factory.get();
    }

    /** Whether names may contain slashes (sub folders). */
    public boolean allowsPath() {
        return this == LOOT_TABLE || this == ADVANCEMENT || this == TAG || this == FUNCTION || this == PREDICATE || this == ITEM_MODIFIER;
    }

    /** Items and blocks share the item registry, so their names must not collide. */
    public String registryGroup() {
        return switch (this) {
            case ITEM, BLOCK -> "item";
            default -> id;
        };
    }

    public static ElementType of(String id) {
        for (ElementType t : values()) if (t.id.equals(id)) return t;
        return null;
    }
}
