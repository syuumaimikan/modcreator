package modstudio.gen;

import modstudio.core.*;
import modstudio.core.Elements.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Code shared by Forge, NeoForge and Fabric (all use official Mojang names). */
public final class JavaCommon {
    public enum Loader { FORGE, NEOFORGE, FABRIC }

    private final GenContext ctx;
    private final Loader loader;

    public JavaCommon(GenContext ctx, Loader loader) {
        this.ctx = ctx;
        this.loader = loader;
    }

    public String itemRef(String name) {
        return "ModItems." + Ids.toConst(name) + (loader == Loader.FABRIC ? "" : ".get()");
    }

    public String blockRef(String name) {
        return "ModBlocks." + Ids.toConst(name) + (loader == Loader.FABRIC ? "" : ".get()");
    }

    /** Reference to any item id: project items directly, others through the registry. */
    public String anyItemRef(String id) {
        String f = Ids.full(id);
        Element e = ctx.p.itemLike(f);
        if (e != null) return itemRef(e.name);
        return "ModProcedures.item(" + Src.str(f) + ")";
    }

    public List<Procedure> rightClickProcs(String itemId) {
        List<Procedure> r = new ArrayList<>();
        for (Procedure p : ctx.all(Procedure.class)) {
            if (p.trigger.equals("item_right_click") && Ids.full(p.triggerValue).equals(itemId)) r.add(p);
        }
        return r;
    }

    /** Right-click procedures whose item is not a project item with its own class. */
    public List<Procedure> eventRightClickProcs() {
        List<Procedure> r = new ArrayList<>();
        for (Procedure p : ctx.all(Procedure.class)) {
            if (!p.trigger.equals("item_right_click") || p.triggerValue.isBlank()) continue;
            if (ctx.p.itemLike(Ids.full(p.triggerValue)) instanceof Item) continue;
            r.add(p);
        }
        return r;
    }

    public boolean needsClass(Item it) {
        return it.glint || !it.tooltip.isBlank() || !it.shiftTooltip.isBlank() || !rightClickProcs(ctx.id(it.name)).isEmpty()
                || !it.onUse.isEmpty() || !it.onHitEntity.isEmpty() || !it.onTickInHand.isEmpty()
                || it.kind.equals("throwable")
                || (it.kind.equals("elytra") && !ctx.registryKeys())
                || (loader == Loader.FORGE && (it.burnTime > 0 || ModelGen.wornArmorModel(ctx.p, it) != null))
                || isPrimarySetBonusPiece(it)
                || (it.useCooldown > 0 && !ctx.registryKeys())
                || Math.abs(it.attackKnockback) > 0.01
                || (it.enchantability >= 0 && !ctx.registryKeys())
                || (!it.repairItem.isBlank() && !ctx.registryKeys())
                || (!it.useReturnItem.isBlank())
                || (it.kind.equals("food") && !ctx.registryKeys() && (!it.foodEffect.isBlank() || it.useDurationTicks > 0))
                || (it.attributes != null && !it.attributes.isEmpty() && !ctx.components())
                || (!it.craftRemainder.isBlank() && !Ids.full(it.craftRemainder).startsWith("minecraft:"));
    }

    /** 1.21.5+: swords and pickaxes are plain items configured through Item.Properties. */
    public boolean toolProps() {
        return ctx.v.atLeast(McVersion.V1_21_8);
    }

    // ------------------------------------------------------------------ armor

    public Elements.ArmorMaterial armorMat(Item it) {
        return it.isArmor() && ctx.p.byName(ElementType.ARMOR_MATERIAL, it.armorMaterial) instanceof Elements.ArmorMaterial m ? m : null;
    }

    /** Armor pieces with an existing material; other armor kinds stay plain items. */
    public boolean armor(Item it) {
        return armorMat(it) != null;
    }

    public boolean hasArmor() {
        return !ctx.all(Elements.ArmorMaterial.class).isEmpty();
    }

    /** 1.21.5+: armor is a plain item configured with Item.Properties.humanoidArmor. */
    private boolean armorProps() {
        return toolProps();
    }

    /** 1.21.2+: equipment ArmorMaterial records with an equipment asset instead of a registry. */
    public boolean equipmentArmor() {
        return ctx.registryKeys();
    }

    /** 1.20.5 to 1.21.1: armor materials live in a registry and items take a Holder. */
    public boolean registeredArmor() {
        return ctx.components() && !equipmentArmor();
    }

    private String armorType(Item it) {
        return (equipmentArmor() ? "ArmorType." : "ArmorItem.Type.") + it.kind.toUpperCase();
    }

    private String armorConst(Item it) {
        return "ModArmorMaterials." + Ids.toConst(it.armorMaterial);
    }

    public static String equipSound(String s) {
        return "ARMOR_EQUIP_" + switch (s) {
            case "leather", "chain", "gold", "diamond", "netherite", "turtle", "generic" -> s.toUpperCase();
            default -> "IRON";
        };
    }

    private String repairIngredient(Elements.ArmorMaterial m) {
        return repairIngredient(m.repairItem);
    }

    private String repairIngredient(String repairItem) {
        String r = repairItem == null || repairItem.isBlank() ? "" : Ids.full(repairItem);
        if (r.isEmpty()) return "Ingredient.EMPTY";
        if (Ids.isTag(r)) return "Ingredient.of(ModProcedures.itemTag(" + Src.str(r.substring(1)) + "))";
        return "Ingredient.of(" + anyItemRef(r) + ")";
    }

    public boolean hasToolMaterials() {
        return !ctx.all(Elements.ToolMaterial.class).isEmpty();
    }

    /** ModToolMaterials for this version and loader. */
    public void toolMaterials() {
        List<Elements.ToolMaterial> mats = ctx.all(Elements.ToolMaterial.class);
        if (mats.isEmpty()) return;
        String main = ctx.mainClass;
        Src s = new Src(ctx.pkg);
        if (ctx.registryKeys()) {
            s.imp("net.minecraft.core.registries.Registries", "net.minecraft.tags.TagKey",
                    "net.minecraft.world.item.ToolMaterial");
            s.l("/** Custom tool materials referenced by custom tools and weapons. */");
            s.l("public final class ModToolMaterials {");
            for (Elements.ToolMaterial tm : mats) {
                s.l("public static final ToolMaterial " + Ids.toConst(tm.name) + " = new ToolMaterial("
                        + "TagKey.create(Registries.BLOCK, " + main + ".id(" + Src.str("incorrect_for_" + tm.name + "_tool") + ")), "
                        + Math.max(1, tm.durability) + ", " + Src.f(Math.max(0.5, tm.speed)) + ", "
                        + Src.f(tm.attackDamageBonus) + ", " + Math.max(1, tm.enchantability) + ", "
                        + "TagKey.create(Registries.ITEM, " + main + ".id(" + Src.str("repairs_" + tm.name + "_tool") + ")));");
            }
            s.blank().l("private ModToolMaterials() {").l("}");
            s.l("}");
            ctx.files.text(ctx.javaDir() + "ModToolMaterials.java", s.toString());
            return;
        }
        s.imp("net.minecraft.world.item.Tier", "net.minecraft.world.item.crafting.Ingredient",
                "java.util.function.Supplier");
        if (ctx.components()) {
            s.imp("net.minecraft.core.registries.Registries", "net.minecraft.tags.TagKey",
                    "net.minecraft.world.level.block.Block");
        }
        s.l("public enum ModToolMaterials implements Tier {");
        for (int i = 0; i < mats.size(); i++) {
            Elements.ToolMaterial tm = mats.get(i);
            String c = Ids.toConst(tm.name);
            String arg = ctx.components()
                    ? "TagKey.create(Registries.BLOCK, " + main + ".id(" + Src.str("incorrect_for_" + tm.name + "_tool") + "))"
                    : String.valueOf(Math.max(0, Math.min(4, tm.miningLevel)));
            s.l(c + "(" + arg + ", " + Math.max(1, tm.durability) + ", " + Src.f(Math.max(0.5, tm.speed)) + ", "
                    + Src.f(tm.attackDamageBonus) + ", " + Math.max(1, tm.enchantability) + ", () -> "
                    + repairIngredient(tm.repairItem) + ")" + (i == mats.size() - 1 ? ";" : ","));
        }
        s.blank();
        if (ctx.components()) s.l("private final TagKey<Block> incorrectBlocks;");
        else s.l("private final int level;");
        s.l("private final int uses;");
        s.l("private final float speed;");
        s.l("private final float attackDamageBonus;");
        s.l("private final int enchantability;");
        s.l("private final Supplier<Ingredient> repair;");
        s.blank();
        s.l("ModToolMaterials(" + (ctx.components() ? "TagKey<Block> incorrectBlocks" : "int level")
                + ", int uses, float speed, float attackDamageBonus, int enchantability, Supplier<Ingredient> repair) {");
        if (ctx.components()) s.l("this.incorrectBlocks = incorrectBlocks;");
        else s.l("this.level = level;");
        s.l("this.uses = uses; this.speed = speed; this.attackDamageBonus = attackDamageBonus; this.enchantability = enchantability; this.repair = repair;");
        s.l("}");
        s.blank().l("@Override").l("public int getUses() { return uses; }");
        s.blank().l("@Override").l("public float getSpeed() { return speed; }");
        s.blank().l("@Override").l("public float getAttackDamageBonus() { return attackDamageBonus; }");
        if (ctx.components()) {
            s.blank().l("@Override").l("public TagKey<Block> getIncorrectBlocksForDrops() { return incorrectBlocks; }");
        } else {
            s.blank().l("@Override").l("public int getLevel() { return level; }");
        }
        s.blank().l("@Override").l("public int getEnchantmentValue() { return enchantability; }");
        s.blank().l("@Override").l("public Ingredient getRepairIngredient() { return repair.get(); }");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModToolMaterials.java", s.toString());
    }

    /** ModArmorMaterials for this version and loader. */
    public void armorMaterials() {
        List<Elements.ArmorMaterial> mats = ctx.all(Elements.ArmorMaterial.class);
        String main = ctx.mainClass;
        Src s = new Src(ctx.pkg);
        s.imp("net.minecraft.sounds.SoundEvents");
        if (!equipmentArmor() && !registeredArmor()) {
            // 1.20.1 - 1.20.4: an enum implementing the ArmorMaterial interface
            s.imp("net.minecraft.sounds.SoundEvent", "net.minecraft.world.item.ArmorItem", "net.minecraft.world.item.ArmorMaterial",
                    "net.minecraft.world.item.crafting.Ingredient", "java.util.function.Supplier");
            s.l("/** Armor materials. Worn textures: assets/minecraft/textures/models/armor/<name>_layer_1.png and _layer_2.png. */");
            s.l("public enum ModArmorMaterials implements ArmorMaterial {");
            for (int i = 0; i < mats.size(); i++) {
                Elements.ArmorMaterial m = mats.get(i);
                s.l(Ids.toConst(m.name) + "(" + Src.str(ctx.ns + "_" + m.name) + ", new int[]{" + m.durabilityOf("helmet") + ", " + m.durabilityOf("chestplate")
                        + ", " + m.durabilityOf("leggings") + ", " + m.durabilityOf("boots") + "}, new int[]{" + m.helmet + ", " + m.chestplate + ", "
                        + m.leggings + ", " + m.boots + "}, " + m.enchantability + ", SoundEvents." + equipSound(m.equipSound) + ", "
                        + Src.f(m.toughness) + ", " + Src.f(m.knockbackResistance) + ", () -> " + repairIngredient(m) + ")" + (i == mats.size() - 1 ? ";" : ","));
            }
            s.blank();
            s.l("private final String name;").l("private final int[] durability;").l("private final int[] defense;").l("private final int enchantability;")
                    .l("private final SoundEvent sound;").l("private final float toughness;").l("private final float knockbackResistance;")
                    .l("private final Supplier<Ingredient> repair;");
            s.blank();
            s.l("ModArmorMaterials(String name, int[] durability, int[] defense, int enchantability, SoundEvent sound, float toughness, float knockbackResistance, Supplier<Ingredient> repair) {");
            s.l("this.name = name;").l("this.durability = durability;").l("this.defense = defense;").l("this.enchantability = enchantability;")
                    .l("this.sound = sound;").l("this.toughness = toughness;").l("this.knockbackResistance = knockbackResistance;").l("this.repair = repair;");
            s.l("}");
            s.blank();
            s.l("private static int index(ArmorItem.Type type) {");
            s.l("return switch (type) {").l("case HELMET -> 0;").l("case CHESTPLATE -> 1;").l("case LEGGINGS -> 2;").l("default -> 3;").l("};");
            s.l("}");
            s.blank();
            s.l("@Override").l("public int getDurabilityForType(ArmorItem.Type type) {").l("return durability[index(type)];").l("}");
            s.blank();
            s.l("@Override").l("public int getDefenseForType(ArmorItem.Type type) {").l("return defense[index(type)];").l("}");
            s.blank();
            s.l("@Override").l("public int getEnchantmentValue() {").l("return enchantability;").l("}");
            s.blank();
            s.l("@Override").l("public SoundEvent getEquipSound() {").l("return sound;").l("}");
            s.blank();
            s.l("@Override").l("public Ingredient getRepairIngredient() {").l("return repair.get();").l("}");
            s.blank();
            s.l("@Override").l("public String getName() {").l("return name;").l("}");
            s.blank();
            s.l("@Override").l("public float getToughness() {").l("return toughness;").l("}");
            s.blank();
            s.l("@Override").l("public float getKnockbackResistance() {").l("return knockbackResistance;").l("}");
            s.l("}");
            ctx.files.text(ctx.javaDir() + "ModArmorMaterials.java", s.toString());
            return;
        }
        s.imp("java.util.EnumMap", "java.util.Map");
        String typeCls = equipmentArmor() ? "ArmorType" : "ArmorItem.Type";
        if (equipmentArmor()) {
            s.imp("net.minecraft.world.item.equipment.ArmorMaterial", "net.minecraft.world.item.equipment.ArmorType",
                    "net.minecraft.world.item.equipment.EquipmentAssets", "net.minecraft.core.registries.Registries",
                    "net.minecraft.resources.ResourceKey", "net.minecraft.tags.TagKey");
            s.l("/** Armor materials. Worn textures come from the equipment assets in assets/" + ctx.ns + "/equipment. */");
            s.l("public final class ModArmorMaterials {");
            for (Elements.ArmorMaterial m : mats) {
                s.l("public static final ArmorMaterial " + Ids.toConst(m.name) + " = new ArmorMaterial(" + Math.max(1, m.durability) + ", "
                        + defenseCall(m) + ", " + m.enchantability + ", SoundEvents." + equipSound(m.equipSound) + ", " + Src.f(m.toughness) + ", "
                        + Src.f(m.knockbackResistance) + ", TagKey.create(Registries.ITEM, " + main + ".id(" + Src.str("repairs_" + m.name) + ")), "
                        + "ResourceKey.create(EquipmentAssets.ROOT_ID, " + main + ".id(" + Src.str(m.name) + ")));");
            }
        } else {
            s.imp("net.minecraft.core.Holder", "net.minecraft.world.item.ArmorItem", "net.minecraft.world.item.ArmorMaterial",
                    "net.minecraft.world.item.crafting.Ingredient", "java.util.List");
            s.l("/** Armor materials. Worn textures: assets/" + ctx.ns + "/textures/models/armor/<name>_layer_1.png and _layer_2.png. */");
            s.l("public final class ModArmorMaterials {");
            if (loader != Loader.FABRIC) {
                s.imp("net.minecraft.core.registries.Registries",
                        loader == Loader.NEOFORGE ? "net.neoforged.neoforge.registries.DeferredRegister" : "net.minecraftforge.registries.DeferredRegister");
                s.l("public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, " + main + ".MODID);");
                s.blank();
            } else {
                s.imp("net.minecraft.core.Registry", "net.minecraft.core.registries.BuiltInRegistries");
            }
            for (Elements.ArmorMaterial m : mats) {
                String c = Ids.toConst(m.name);
                String expr = "new ArmorMaterial(" + defenseCall(m) + ", " + m.enchantability + ", SoundEvents." + equipSound(m.equipSound)
                        + ", () -> " + repairIngredient(m) + ", List.of(new ArmorMaterial.Layer(" + main + ".id(" + Src.str(m.name) + "))), "
                        + Src.f(m.toughness) + ", " + Src.f(m.knockbackResistance) + ")";
                switch (loader) {
                    case FABRIC -> s.l("public static final Holder<ArmorMaterial> " + c + " = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL, "
                            + main + ".id(" + Src.str(m.name) + "), " + expr + ");");
                    case NEOFORGE -> {
                        s.imp("net.neoforged.neoforge.registries.DeferredHolder");
                        s.l("public static final DeferredHolder<ArmorMaterial, ArmorMaterial> " + c + " = ARMOR_MATERIALS.register(" + Src.str(m.name) + ", () -> " + expr + ");");
                    }
                    case FORGE -> {
                        // the item constructors need a holder before the registry event runs
                        s.l("private static final ArmorMaterial " + c + "_MATERIAL = " + expr + ";");
                        s.l("public static final Holder<ArmorMaterial> " + c + " = Holder.direct(" + c + "_MATERIAL);");
                    }
                }
            }
            if (loader == Loader.FORGE) {
                s.blank().l("static {");
                for (Elements.ArmorMaterial m : mats) s.l("ARMOR_MATERIALS.register(" + Src.str(m.name) + ", () -> " + Ids.toConst(m.name) + "_MATERIAL);");
                s.l("}");
            }
        }
        s.blank().l("private ModArmorMaterials() {").l("}");
        s.blank();
        s.l("private static Map<" + typeCls + ", Integer> defense(int helmet, int chestplate, int leggings, int boots) {");
        s.l("Map<" + typeCls + ", Integer> map = new EnumMap<>(" + typeCls + ".class);");
        s.l("map.put(" + typeCls + ".HELMET, helmet);").l("map.put(" + typeCls + ".CHESTPLATE, chestplate);")
                .l("map.put(" + typeCls + ".LEGGINGS, leggings);").l("map.put(" + typeCls + ".BOOTS, boots);")
                .l("map.put(" + typeCls + ".BODY, chestplate);");
        s.l("return map;");
        s.l("}");
        s.blank().l("public static void init() {").l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModArmorMaterials.java", s.toString());
    }

    private static String defenseCall(Elements.ArmorMaterial m) {
        return "defense(" + m.helmet + ", " + m.chestplate + ", " + m.leggings + ", " + m.boots + ")";
    }

    // ------------------------------------------------------------------ status effects and potions

    public boolean hasEffects() {
        return !ctx.all(Elements.StatusEffect.class).isEmpty();
    }

    public boolean hasPotions() {
        return ctx.all(Elements.StatusEffect.class).stream().anyMatch(e -> e.potion) || !ctx.all(Elements.Potion.class).isEmpty();
    }

    /** MobEffects constant for a vanilla effect in a version, or null when the effect does not exist there. */
    public static String vanillaEffectConst(McVersion v, String path) {
        boolean newer = Vanilla.NEW_EFFECTS.contains("minecraft:" + path);
        if (!Vanilla.EFFECTS.contains("minecraft:" + path) && !newer) return null;
        if (newer && !v.components()) return null;
        if (v.atLeast(McVersion.V1_21_8)) return path.toUpperCase(java.util.Locale.ROOT);
        return switch (path) {
            case "speed" -> "MOVEMENT_SPEED";
            case "slowness" -> "MOVEMENT_SLOWDOWN";
            case "haste" -> "DIG_SPEED";
            case "mining_fatigue" -> "DIG_SLOWDOWN";
            case "strength" -> "DAMAGE_BOOST";
            case "instant_health" -> "HEAL";
            case "instant_damage" -> "HARM";
            case "jump_boost" -> "JUMP";
            case "nausea" -> "CONFUSION";
            case "resistance" -> "DAMAGE_RESISTANCE";
            default -> path.toUpperCase(java.util.Locale.ROOT);
        };
    }

    /** Effect value for a MobEffectInstance (vanilla or project), or null when it cannot be referenced. */
    private String mobEffect(Src s, String id) {
        String f = Ids.full(id.trim());
        if (Ids.ns(f).equals(ctx.ns)) return ctx.p.byName(ElementType.STATUS_EFFECT, Ids.path(f)) instanceof Elements.StatusEffect ? effectArg(Ids.path(f)) : null;
        if (!Ids.ns(f).equals("minecraft")) return null;
        String c = vanillaEffectConst(ctx.v, Ids.path(f));
        if (c == null) return null;
        s.imp("net.minecraft.world.effect.MobEffects");
        return "MobEffects." + c;
    }

    /** Attributes that exist as Attributes constants in this version. */
    public boolean attributeExists(String a) {
        if (ctx.components()) return true;
        return List.of("max_health", "movement_speed", "attack_damage", "attack_speed", "armor", "armor_toughness",
                "knockback_resistance", "luck", "follow_range", "attack_knockback", "flying_speed").contains(a);
    }

    private String operation(String op) {
        if (ctx.components()) {
            return "AttributeModifier.Operation." + switch (op) {
                case "add_value" -> "ADD_VALUE";
                case "add_multiplied_base" -> "ADD_MULTIPLIED_BASE";
                default -> "ADD_MULTIPLIED_TOTAL";
            };
        }
        return "AttributeModifier.Operation." + switch (op) {
            case "add_value" -> "ADDITION";
            case "add_multiplied_base" -> "MULTIPLY_BASE";
            default -> "MULTIPLY_TOTAL";
        };
    }

    private static String color(String hex) {
        try {
            return "0x" + String.format("%06X", Integer.parseInt(hex.trim().replace("#", ""), 16) & 0xFFFFFF);
        } catch (Exception e) {
            return "0xE0115F";
        }
    }

    /** Effect value for a MobEffectInstance: a Holder from 1.20.5. */
    private String effectArg(String name) {
        String c = "ModEffects." + Ids.toConst(name);
        if (!ctx.components()) return loader == Loader.FABRIC ? c : c + ".get()";
        return loader == Loader.FORGE ? c + ".getHolder().orElseThrow()" : c;
    }

    /** ModMobEffect (tick procedure), ModEffects and ModPotions for this version and loader. */
    public void effects() {
        String main = ctx.mainClass;
        // the effect class
        Src e = new Src(ctx.pkg);
        e.imp("net.minecraft.world.effect.MobEffect", "net.minecraft.world.effect.MobEffectCategory", "net.minecraft.world.entity.LivingEntity");
        e.l("/** Status effect that runs a compiled procedure every few ticks while active. */");
        e.l("public class ModMobEffect extends MobEffect {");
        e.l("private final String tickFunction;").l("private final int interval;");
        e.blank();
        e.l("public ModMobEffect(MobEffectCategory category, int color, String tickFunction, int interval) {");
        e.l("super(category, color);").l("this.tickFunction = tickFunction;").l("this.interval = Math.max(1, interval);").l("}");
        e.blank().l("@Override");
        e.l(ctx.v.atLeast(McVersion.V1_20_4) ? "public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {"
                : "public boolean isDurationEffectTick(int duration, int amplifier) {");
        e.l("return tickFunction != null && duration % interval == 0;").l("}");
        e.blank().l("@Override");
        if (ctx.registryKeys()) {
            e.imp("net.minecraft.server.level.ServerLevel");
            e.l("public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {");
        } else if (ctx.components()) {
            e.l("public boolean applyEffectTick(LivingEntity entity, int amplifier) {");
        } else {
            e.l("public void applyEffectTick(LivingEntity entity, int amplifier) {");
        }
        e.l("if (tickFunction != null) ModProcedures.run(entity, tickFunction);");
        if (ctx.components()) e.l("return true;");
        e.l("}");
        e.l("}");
        ctx.files.text(ctx.javaDir() + "ModMobEffect.java", e.toString());

        // registration
        Src s = new Src(ctx.pkg);
        s.imp("net.minecraft.world.effect.MobEffect", "net.minecraft.world.effect.MobEffectCategory",
                "net.minecraft.world.entity.ai.attributes.AttributeModifier", "net.minecraft.world.entity.ai.attributes.Attributes");
        s.l("public final class ModEffects {");
        if (loader != Loader.FABRIC) {
            s.imp("net.minecraft.core.registries.Registries",
                    loader == Loader.NEOFORGE ? "net.neoforged.neoforge.registries.DeferredRegister" : "net.minecraftforge.registries.DeferredRegister");
            s.l("public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, " + main + ".MODID);");
            s.blank();
        } else {
            s.imp("net.minecraft.core.Registry", "net.minecraft.core.registries.BuiltInRegistries");
        }
        for (Elements.StatusEffect se : ctx.all(Elements.StatusEffect.class)) {
            String tickFn = se.onTick.isEmpty() ? "null" : Src.str("status/" + se.name + "/tick");
            StringBuilder expr = new StringBuilder("new ModMobEffect(MobEffectCategory." + se.category.toUpperCase() + ", " + color(se.color)
                    + ", " + tickFn + ", " + Math.max(1, se.tickInterval) + ")");
            int i = 0;
            for (Elements.EffectModifier m : se.modifiers) {
                if (m.attribute.isBlank()) continue;
                String a = m.attribute.contains(":") ? Ids.path(m.attribute) : m.attribute;
                if (!attributeExists(a)) {
                    ctx.note("status effect " + se.name + ": attribute " + a + " does not exist in " + ctx.v.id);
                    continue;
                }
                String id;
                if (ctx.singular()) id = main + ".id(" + Src.str("effect." + se.name + "." + i) + ")";
                else id = Src.str(java.util.UUID.nameUUIDFromBytes((ctx.ns + ":" + se.name + ":" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
                expr.append(".addAttributeModifier(Attributes.").append(a.toUpperCase()).append(", ").append(id).append(", ")
                        .append(Src.d(m.amount)).append(", ").append(operation(m.operation)).append(')');
                i++;
            }
            String c = Ids.toConst(se.name);
            switch (loader) {
                case FABRIC -> {
                    if (ctx.components()) {
                        s.imp("net.minecraft.core.Holder");
                        s.l("public static final Holder<MobEffect> " + c + " = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, " + main + ".id("
                                + Src.str(se.name) + "), " + expr + ");");
                    } else {
                        s.l("public static final MobEffect " + c + " = Registry.register(BuiltInRegistries.MOB_EFFECT, " + main + ".id("
                                + Src.str(se.name) + "), " + expr + ");");
                    }
                }
                case NEOFORGE -> {
                    s.imp("net.neoforged.neoforge.registries.DeferredHolder");
                    s.l("public static final DeferredHolder<MobEffect, MobEffect> " + c + " = EFFECTS.register(" + Src.str(se.name) + ", () -> " + expr + ");");
                }
                case FORGE -> {
                    s.imp("net.minecraftforge.registries.RegistryObject");
                    s.l("public static final RegistryObject<MobEffect> " + c + " = EFFECTS.register(" + Src.str(se.name) + ", () -> " + expr + ");");
                }
            }
        }
        s.blank().l("private ModEffects() {").l("}");
        s.blank().l("public static void init() {").l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModEffects.java", s.toString());
    }

    /** ModPotions: potions of project effects and potion elements. */
    public void potions() {
        String main = ctx.mainClass;
        Src po = new Src(ctx.pkg);
        po.imp("net.minecraft.world.effect.MobEffectInstance", "net.minecraft.world.item.alchemy.Potion");
        po.l("public final class ModPotions {");
        if (loader != Loader.FABRIC) {
            po.imp("net.minecraft.core.registries.Registries",
                    loader == Loader.NEOFORGE ? "net.neoforged.neoforge.registries.DeferredRegister" : "net.minecraftforge.registries.DeferredRegister");
            po.l("public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, " + main + ".MODID);");
            po.blank();
        } else {
            po.imp("net.minecraft.core.Registry", "net.minecraft.core.registries.BuiltInRegistries");
        }
        for (Elements.StatusEffect se : ctx.all(Elements.StatusEffect.class)) {
            if (!se.potion) continue;
            String expr = "new Potion(" + Src.str(potionKey(ctx, se)) + ", new MobEffectInstance(" + effectArg(se.name) + ", "
                    + Math.max(1, se.potionSeconds) * 20 + ", " + Math.max(0, se.potionAmplifier) + "))";
            String c = Ids.toConst(se.name);
            switch (loader) {
                case FABRIC -> po.l("public static final Potion " + c + " = Registry.register(BuiltInRegistries.POTION, " + main + ".id("
                        + Src.str(se.name) + "), " + expr + ");");
                case NEOFORGE -> {
                    po.imp("net.neoforged.neoforge.registries.DeferredHolder");
                    po.l("public static final DeferredHolder<Potion, Potion> " + c + " = POTIONS.register(" + Src.str(se.name) + ", () -> " + expr + ");");
                }
                case FORGE -> {
                    po.imp("net.minecraftforge.registries.RegistryObject");
                    po.l("public static final RegistryObject<Potion> " + c + " = POTIONS.register(" + Src.str(se.name) + ", () -> " + expr + ");");
                }
            }
        }
        for (Elements.Potion p : ctx.all(Elements.Potion.class)) {
            StringBuilder expr = new StringBuilder("new Potion(" + Src.str(ctx.ns + "." + p.name));
            for (Elements.PotionEffect pe : p.effects) {
                String arg = mobEffect(po, pe.effect);
                if (arg == null) {
                    ctx.note("potion " + p.name + ": effect " + pe.effect + " does not exist in " + ctx.v.id);
                    continue;
                }
                expr.append(", new MobEffectInstance(").append(arg).append(", ").append(Math.max(1, pe.seconds) * 20).append(", ")
                        .append(Math.max(0, Math.min(255, pe.amplifier))).append(", false, ").append(pe.particles).append(", ").append(pe.icon).append(')');
            }
            expr.append(')');
            String c = Ids.toConst(p.name);
            switch (loader) {
                case FABRIC -> po.l("public static final Potion " + c + " = Registry.register(BuiltInRegistries.POTION, " + main + ".id("
                        + Src.str(p.name) + "), " + expr + ");");
                case NEOFORGE -> {
                    po.imp("net.neoforged.neoforge.registries.DeferredHolder");
                    po.l("public static final DeferredHolder<Potion, Potion> " + c + " = POTIONS.register(" + Src.str(p.name) + ", () -> " + expr + ");");
                }
                case FORGE -> {
                    po.imp("net.minecraftforge.registries.RegistryObject");
                    po.l("public static final RegistryObject<Potion> " + c + " = POTIONS.register(" + Src.str(p.name) + ", () -> " + expr + ");");
                }
            }
        }
        po.blank().l("private ModPotions() {").l("}");
        po.blank().l("public static void init() {").l("}");
        po.l("}");
        ctx.files.text(ctx.javaDir() + "ModPotions.java", po.toString());
    }

    /** Potion name used in the translation keys item.minecraft.potion.effect.<name>. */
    public static String potionKey(GenContext ctx, Elements.StatusEffect se) {
        return ctx.ns + "." + se.name;
    }

    // ------------------------------------------------------------------ paintings before 1.21 (registered in code)

    public boolean codePaintings() {
        return !ctx.singular() && !ctx.all(Elements.Painting.class).isEmpty();
    }

    public void paintings() {
        String main = ctx.mainClass;
        Src s = new Src(ctx.pkg);
        s.imp("net.minecraft.world.entity.decoration.PaintingVariant");
        s.l("public final class ModPaintings {");
        if (loader != Loader.FABRIC) {
            s.imp("net.minecraft.core.registries.Registries",
                    loader == Loader.NEOFORGE ? "net.neoforged.neoforge.registries.DeferredRegister" : "net.minecraftforge.registries.DeferredRegister");
            s.l("public static final DeferredRegister<PaintingVariant> PAINTINGS = DeferredRegister.create(Registries.PAINTING_VARIANT, " + main + ".MODID);");
            s.blank();
        } else {
            s.imp("net.minecraft.core.Registry", "net.minecraft.core.registries.BuiltInRegistries");
        }
        for (Elements.Painting pa : ctx.all(Elements.Painting.class)) {
            String expr = "new PaintingVariant(" + 16 * Math.max(1, Math.min(16, pa.width)) + ", " + 16 * Math.max(1, Math.min(16, pa.height)) + ")";
            String c = Ids.toConst(pa.name);
            switch (loader) {
                case FABRIC -> s.l("public static final PaintingVariant " + c + " = Registry.register(BuiltInRegistries.PAINTING_VARIANT, " + main + ".id("
                        + Src.str(pa.name) + "), " + expr + ");");
                case NEOFORGE -> {
                    s.imp("net.neoforged.neoforge.registries.DeferredHolder");
                    s.l("public static final DeferredHolder<PaintingVariant, PaintingVariant> " + c + " = PAINTINGS.register(" + Src.str(pa.name) + ", () -> " + expr + ");");
                }
                case FORGE -> {
                    s.imp("net.minecraftforge.registries.RegistryObject");
                    s.l("public static final RegistryObject<PaintingVariant> " + c + " = PAINTINGS.register(" + Src.str(pa.name) + ", () -> " + expr + ");");
                }
            }
        }
        s.blank().l("private ModPaintings() {").l("}");
        s.blank().l("public static void init() {").l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModPaintings.java", s.toString());
    }

    /** Whether the tool kind still has its own item class in this version. */
    private boolean toolClass(Item it) {
        if (!toolProps()) return true;
        return it.kind.equals("axe") || it.kind.equals("shovel") || it.kind.equals("hoe");
    }

    public String tier(String t) {
        Elements.ToolMaterial tm = ctx.p.toolMaterial(t);
        if (tm != null) return "ModToolMaterials." + Ids.toConst(tm.name);
        return (ctx.registryKeys() ? "ToolMaterial." : "Tiers.") + switch (t) {
            case "wood" -> "WOOD";
            case "stone" -> "STONE";
            case "gold" -> "GOLD";
            case "diamond" -> "DIAMOND";
            case "netherite" -> "NETHERITE";
            default -> "IRON";
        };
    }

    public String superClass(Item it) {
        if (it.isTool() && !toolClass(it)) return "Item";
        if (armor(it)) return armorProps() ? "Item" : "ArmorItem";
        return switch (it.kind) {
            case "sword" -> "SwordItem";
            case "pickaxe" -> "PickaxeItem";
            case "axe" -> "AxeItem";
            case "shovel" -> "ShovelItem";
            case "hoe" -> "HoeItem";
            case "bow" -> "BowItem";
            case "crossbow" -> "CrossbowItem";
            case "shield" -> "ShieldItem";
            case "trident" -> "TridentItem";
            case "elytra" -> ctx.registryKeys() ? "Item" : "ElytraItem";
            default -> "Item";
        };
    }

    public String className(Item it) {
        return Ids.toPascal(it.name) + "Item";
    }

    public void importsForItems(Src s) {
        s.imp("net.minecraft.world.item.Item", "net.minecraft.world.item.Rarity", "net.minecraft.world.item.BlockItem");
        for (Item it : ctx.all(Item.class)) {
            if (it.kind.equals("food")) s.imp("net.minecraft.world.food.FoodProperties");
            if (it.isTool()) {
                if (ctx.p.toolMaterial(it.tier) != null) s.imp(ctx.pkg + ".ModToolMaterials");
                else s.imp(ctx.registryKeys() ? "net.minecraft.world.item.ToolMaterial" : "net.minecraft.world.item.Tiers");
                s.imp("net.minecraft.world.item." + superClass(it));
                if (ctx.components() && !ctx.registryKeys()) {
                    s.imp(it.kind.equals("sword") ? "net.minecraft.world.item.SwordItem" : "net.minecraft.world.item.DiggerItem");
                }
            } else {
                String sup = superClass(it);
                if (!sup.equals("Item") && !sup.equals("ArmorItem")) s.imp("net.minecraft.world.item." + sup);
            }
            if (it.kind.equals("elytra") && ctx.registryKeys()) {
                s.imp("net.minecraft.core.component.DataComponents", "net.minecraft.util.Unit",
                        "net.minecraft.world.entity.EquipmentSlot", "net.minecraft.world.item.equipment.Equippable");
            }
            if (it.kind.equals("food") && ctx.registryKeys() && (it.fastFood || !it.foodEffect.isBlank() || it.useDurationTicks > 0 || !"eat".equals(it.consumeAnimation))) {
                s.imp("net.minecraft.world.item.component.Consumable", "net.minecraft.world.item.ItemUseAnimation");
                if (!it.foodEffect.isBlank() && mobEffect(s, it.foodEffect) != null) {
                    s.imp("net.minecraft.world.effect.MobEffectInstance", "net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect");
                }
            }
            if (!it.craftRemainder.isBlank() && Ids.full(it.craftRemainder).startsWith("minecraft:")) s.imp(ctx.pkg + ".ModProcedures");
            if (!it.repairItem.isBlank() && ctx.registryKeys()) s.imp(ctx.pkg + ".ModProcedures");
            if (it.attributes != null && !it.attributes.isEmpty() && ctx.components()) {
                s.imp("net.minecraft.world.entity.EquipmentSlotGroup",
                        "net.minecraft.world.entity.ai.attributes.AttributeModifier",
                        "net.minecraft.world.entity.ai.attributes.Attributes",
                        "net.minecraft.world.item.component.ItemAttributeModifiers");
            }
            if (!it.song.isBlank() && ctx.singular()) s.imp("net.minecraft.resources.ResourceKey", "net.minecraft.core.registries.Registries");
            if (armor(it)) {
                if (!armorProps()) s.imp("net.minecraft.world.item.ArmorItem");
                if (equipmentArmor()) s.imp("net.minecraft.world.item.equipment.ArmorType");
            }
            if (needsClass(it)) s.imp(ctx.pkg + ".item." + className(it));
        }
    }

    private String food(Item it) {
        StringBuilder sb = new StringBuilder("new FoodProperties.Builder().nutrition(").append(it.nutrition).append(')');
        sb.append(ctx.components() ? ".saturationModifier(" : ".saturationMod(").append(Src.f(it.saturation)).append(')');
        if (it.alwaysEdible) sb.append(ctx.components() ? ".alwaysEdible()" : ".alwaysEat()");
        if (it.fastFood && !ctx.registryKeys()) sb.append(".fast()");
        return sb.append(".build()").toString();
    }

    /** Main class expression for an item registry key (1.21.2+). */
    public String itemKey(String name) {
        return ctx.mainClass + ".itemKey(" + Src.str(name) + ")";
    }

    public String blockKey(String name) {
        return ctx.mainClass + ".blockKey(" + Src.str(name) + ")";
    }

    /** Properties for a block's item. */
    public String blockItemProps(String name) {
        if (!ctx.registryKeys()) return "new Item.Properties()";
        return "new Item.Properties().setId(" + itemKey(name) + ").useBlockDescriptionPrefix()";
    }

    public String itemProps(Item it) {
        StringBuilder sb = new StringBuilder("new Item.Properties()");
        if (ctx.registryKeys()) sb.append(".setId(").append(itemKey(it.name)).append(')');
        boolean hasDur = it.damageable() || it.maxDamage > 0;
        if (!hasDur && it.maxStack != 64) sb.append(".stacksTo(").append(Math.max(1, Math.min(64, it.maxStack))).append(')');
        if (!it.rarity.equals("common")) sb.append(".rarity(Rarity.").append(it.rarity.toUpperCase()).append(')');
        if (it.fireResistant) sb.append(".fireResistant()");
        if (!it.craftRemainder.isBlank() && Ids.full(it.craftRemainder).startsWith("minecraft:")) {
            sb.append(".craftRemainder(ModProcedures.item(").append(Src.str(Ids.full(it.craftRemainder))).append("))");
        }
        if (it.customTooltip && ctx.v.atLeast(McVersion.V1_21_4)) {
            sb.append(".component(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE, ").append(ctx.mainClass).append(".id(").append(Src.str(it.name)).append("))");
        }
        if (it.kind.equals("food")) {
            sb.append(".food(").append(food(it));
            if (ctx.registryKeys() && (it.fastFood || !it.foodEffect.isBlank() || it.useDurationTicks > 0 || !"eat".equals(it.consumeAnimation))) {
                float secs = it.useDurationTicks > 0 ? it.useDurationTicks / 20.0F : (it.fastFood ? 0.8F : 1.6F);
                String anim = switch (it.consumeAnimation == null ? "eat" : it.consumeAnimation) {
                    case "drink" -> "DRINK";
                    case "block" -> "BLOCK";
                    case "bow" -> "BOW";
                    case "spear" -> "SPEAR";
                    case "none" -> "NONE";
                    default -> "EAT";
                };
                sb.append(", Consumable.builder().consumeSeconds(").append(Src.f(secs))
                        .append(").animation(ItemUseAnimation.").append(anim).append(')');
                if (!it.foodEffect.isBlank()) {
                    Src dummy = new Src(ctx.pkg);
                    String eff = mobEffect(dummy, it.foodEffect);
                    if (eff != null) {
                        int ticks = Math.max(1, it.foodEffectSeconds) * 20;
                        int amp = Math.max(0, Math.min(255, it.foodEffectAmplifier));
                        float ch = (float) Math.max(0.0, Math.min(1.0, it.foodEffectChance));
                        sb.append(".onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(").append(eff)
                                .append(", ").append(ticks).append(", ").append(amp).append("), ").append(Src.f(ch)).append("))");
                    }
                }
                sb.append(".build()");
            }
            sb.append(')');
        }
        if (!it.song.isBlank() && ctx.singular() && ctx.p.byName(ElementType.SONG, it.song) != null) {
            sb.append(".jukeboxPlayable(ResourceKey.create(Registries.JUKEBOX_SONG, ").append(ctx.mainClass).append(".id(")
                    .append(Src.str(it.song)).append("))).stacksTo(1)");
        }
        Elements.ArmorMaterial am = armorMat(it);
        if (am != null && armorProps()) sb.append(".humanoidArmor(").append(armorConst(it)).append(", ").append(armorType(it)).append(')');
        else if (am != null && registeredArmor()) sb.append(".durability(").append(am.durabilityOf(it.kind)).append(')');
        if (List.of("bow", "crossbow", "shield", "trident", "elytra").contains(it.kind)) {
            sb.append(".durability(").append(Math.max(1, it.durability)).append(')');
        }
        if (it.kind.equals("elytra") && ctx.registryKeys()) {
            sb.append(".component(DataComponents.GLIDER, Unit.INSTANCE)")
                    .append(".component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).build())");
        }
        if (it.isTool() && !toolClass(it)) {
            sb.append('.').append(it.kind).append('(').append(tier(it.tier)).append(", ").append(Src.f(it.attackDamage))
                    .append(", ").append(Src.f(it.attackSpeed)).append(')');
        }
        if (it.maxDamage > 0) sb.append(".durability(").append(it.maxDamage).append(')');
        if (it.enchantability >= 0 && ctx.registryKeys()) sb.append(".enchantable(").append(it.enchantability).append(')');
        if (it.useCooldown > 0 && ctx.registryKeys()) sb.append(".useCooldown(").append(Src.f(it.useCooldown)).append(')');
        if (!it.repairItem.isBlank() && ctx.registryKeys()) {
            String rep = Ids.full(it.repairItem.trim());
            if (Ids.isTag(rep)) sb.append(".repairable(ModProcedures.itemTag(").append(Src.str(rep.substring(1))).append("))");
            else if (Ids.ns(rep).equals("minecraft")) sb.append(".repairable(ModProcedures.item(").append(Src.str(rep)).append("))");
            else sb.append(".repairable(ModProcedures.itemTag(").append(Src.str(ctx.ns + ":repair_" + it.name)).append("))");
        }
        if (it.attributes != null && !it.attributes.isEmpty() && ctx.components()) {
            sb.append(".attributes(").append(itemAttrModifiersExpr(it)).append(')');
        }
        return sb.toString();
    }

    private String itemAttrModifiersExpr(Item it) {
        StringBuilder sb = new StringBuilder("ItemAttributeModifiers.builder()");
        int idx = 0;
        for (Item.ItemAttribute a : it.attributes) {
            if (a.attribute == null || a.attribute.isBlank()) continue;
            String attr = a.attribute.contains(":") ? Ids.path(a.attribute) : a.attribute.trim();
            if (!attributeExists(attr)) continue;
            String uuidStr = java.util.UUID.nameUUIDFromBytes((ctx.ns + ":" + it.name + ":" + idx).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            String modArgs = ctx.singular()
                    ? ctx.mainClass + ".id(" + Src.str("item." + it.name + ".attr_" + idx) + "), " + Src.d(a.amount)
                    : "java.util.UUID.fromString(" + Src.str(uuidStr) + "), " + Src.str(it.name + "_" + idx) + ", " + Src.d(a.amount);
            String slot = switch (a.slot == null ? "mainhand" : a.slot) {
                case "any" -> "ANY";
                case "hand" -> "HAND";
                case "offhand" -> "OFFHAND";
                case "head" -> "HEAD";
                case "chest" -> "CHEST";
                case "legs" -> "LEGS";
                case "feet" -> "FEET";
                case "armor" -> "ARMOR";
                default -> "MAINHAND";
            };
            sb.append(".add(Attributes.").append(attr.toUpperCase(Locale.ROOT))
                    .append(", new AttributeModifier(").append(modArgs)
                    .append(", ").append(operation(a.operation)).append("), EquipmentSlotGroup.").append(slot).append(')');
            idx++;
        }
        return sb.append(".build()").toString();
    }

    public String itemConstruct(Item it) {
        String cls = needsClass(it) ? className(it) : superClass(it);
        String props = itemProps(it);
        if (armor(it) && !armorProps()) return "new " + cls + "(" + armorConst(it) + ", " + armorType(it) + ", " + props + ")";
        if (!it.isTool() || !toolClass(it)) return "new " + cls + "(" + props + ")";
        String tier = tier(it.tier);
        boolean intDamage = it.kind.equals("sword") || it.kind.equals("pickaxe") || it.kind.equals("hoe");
        if (ctx.registryKeys()) {
            return "new " + cls + "(" + tier + ", " + Src.f(it.attackDamage) + ", " + Src.f(it.attackSpeed) + ", " + props + ")";
        }
        if (ctx.components()) {
            String attrs = it.kind.equals("sword")
                    ? "SwordItem.createAttributes(" + tier + ", " + (int) Math.round(it.attackDamage) + ", " + Src.f(it.attackSpeed) + ")"
                    : "DiggerItem.createAttributes(" + tier + ", " + Src.f(it.attackDamage) + ", " + Src.f(it.attackSpeed) + ")";
            return "new " + cls + "(" + tier + ", " + props + ".attributes(" + attrs + "))";
        }
        String dmg = intDamage ? String.valueOf((int) Math.round(it.attackDamage)) : Src.f(it.attackDamage);
        return "new " + cls + "(" + tier + ", " + dmg + ", " + Src.f(it.attackSpeed) + ", " + props + ")";
    }

    /** Custom item subclass for glint, tooltips, right-click procedures and fuel. */
    public void itemClass(Item it) {
        String cls = className(it);
        String sup = superClass(it);
        Src s = new Src(ctx.pkg + ".item");
        s.imp("net.minecraft.world.item.Item", "net.minecraft.world.item.ItemStack");
        if (sup.equals("ArmorItem")) {
            s.imp("net.minecraft.world.item.ArmorItem");
        } else if (!sup.equals("Item")) {
            s.imp("net.minecraft.world.item." + sup, ctx.registryKeys() ? "net.minecraft.world.item.ToolMaterial" : "net.minecraft.world.item.Tier");
        }
        s.l("public class " + cls + " extends " + sup + " {");
        boolean intDamage = it.kind.equals("sword") || it.kind.equals("pickaxe") || it.kind.equals("hoe");
        if (armor(it) && !armorProps()) {
            String mat = "ArmorMaterial";
            if (equipmentArmor()) s.imp("net.minecraft.world.item.equipment.ArmorMaterial", "net.minecraft.world.item.equipment.ArmorType");
            else s.imp("net.minecraft.world.item.ArmorMaterial");
            if (registeredArmor()) {
                s.imp("net.minecraft.core.Holder");
                mat = "Holder<ArmorMaterial>";
            }
            s.l("public " + cls + "(" + mat + " material, " + (equipmentArmor() ? "ArmorType" : "ArmorItem.Type") + " type, Item.Properties properties) {")
                    .l("super(material, type, properties);").l("}");
        } else if (!it.isTool() || !toolClass(it)) {
            s.l("public " + cls + "(Item.Properties properties) {").l("super(properties);").l("}");
        } else if (ctx.registryKeys()) {
            s.l("public " + cls + "(ToolMaterial material, float attackDamage, float attackSpeed, Item.Properties properties) {")
                    .l("super(material, attackDamage, attackSpeed, properties);").l("}");
        } else if (ctx.components()) {
            s.l("public " + cls + "(Tier tier, Item.Properties properties) {").l("super(tier, properties);").l("}");
        } else {
            String dt = intDamage ? "int" : "float";
            s.l("public " + cls + "(Tier tier, " + dt + " attackDamage, float attackSpeed, Item.Properties properties) {")
                    .l("super(tier, attackDamage, attackSpeed, properties);").l("}");
        }
        if (it.glint) {
            s.blank().l("@Override").l("public boolean isFoil(ItemStack stack) {").l("return true;").l("}");
        }
        if (!it.tooltip.isBlank() || !it.shiftTooltip.isBlank()) {
            s.imp("java.util.List", "net.minecraft.network.chat.Component", "net.minecraft.world.item.TooltipFlag");
            s.blank().l("@Override");
            String add = "tooltip.add";
            if (toolProps()) {
                s.imp("java.util.function.Consumer", "net.minecraft.world.item.component.TooltipDisplay");
                s.l("public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {");
                add = "tooltip.accept";
            } else if (ctx.components()) {
                s.l("public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {");
            } else {
                s.imp("net.minecraft.world.level.Level");
                s.l("public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {");
            }
            if (!it.tooltip.isBlank()) for (String line : it.tooltip.split("\\R")) {
                String txt = line.contains("&") ? GenContext.legacy(line) : "§7" + line;
                s.l(add + "(Component.literal(" + Src.str(txt) + "));");
            }
            if (!it.shiftTooltip.isBlank()) {
                // client only: appendHoverText never runs on a dedicated server
                s.imp("net.minecraft.client.gui.screens.Screen", "net.minecraft.ChatFormatting");
                s.l("if (Screen.hasShiftDown()) {");
                for (String line : it.shiftTooltip.split("\\R")) {
                    String txt = line.contains("&") ? GenContext.legacy(line) : "§7" + line;
                    s.l(add + "(Component.literal(" + Src.str(txt) + "));");
                }
                s.l("} else {");
                s.l(add + "(Component.translatable(" + Src.str(ctx.ns + ".tooltip.hold_shift") + ").withStyle(ChatFormatting.DARK_GRAY));");
                s.l("}");
            }
            if (toolProps()) s.l("super.appendHoverText(stack, context, display, tooltip, flag);");
            else s.l(ctx.components() ? "super.appendHoverText(stack, context, tooltip, flag);" : "super.appendHoverText(stack, level, tooltip, flag);");
            s.l("}");
        }
        List<Procedure> procs = rightClickProcs(ctx.id(it.name));
        boolean cooldownOld = it.useCooldown > 0 && !ctx.registryKeys();
        boolean hasUse = !procs.isEmpty() || !it.onUse.isEmpty() || it.kind.equals("throwable") || cooldownOld;
        if (hasUse) {
            s.imp("net.minecraft.world.InteractionHand", "net.minecraft.world.entity.player.Player", "net.minecraft.world.level.Level",
                    ctx.pkg + ".ModProcedures");
            if (ctx.registryKeys()) {
                s.imp("net.minecraft.world.InteractionResult");
                s.blank().l("@Override").l("public InteractionResult use(Level level, Player player, InteractionHand hand) {");
            } else {
                s.imp("net.minecraft.world.InteractionResultHolder");
                s.blank().l("@Override").l("public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {");
            }
            s.l("ItemStack stack = player.getItemInHand(hand);");
            s.l("if (!level.isClientSide()) {");
            for (Procedure p : procs) s.l("ModProcedures.run(player, " + Src.str("procedures/" + p.name) + ");");
            if (!it.onUse.isEmpty()) s.l("ModProcedures.run(player, " + Src.str("items/" + it.name + "/on_use") + ");");
            if (cooldownOld) s.l("player.getCooldowns().addCooldown(this, " + Math.max(1, (int) Math.round(it.useCooldown * 20)) + ");");
            if (it.kind.equals("throwable")) {
                if (ctx.v.atLeast(McVersion.V1_21_11)) s.imp("net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball");
                else s.imp("net.minecraft.world.entity.projectile.Snowball");
                String speed = Src.f(Math.max(0.1, it.projectileSpeed));
                boolean dmg = it.projectileDamage > 0.01;
                if (dmg) s.imp("net.minecraft.world.phys.EntityHitResult");
                if (ctx.registryKeys()) {
                    s.imp("net.minecraft.server.level.ServerLevel", "net.minecraft.world.entity.projectile.Projectile");
                    if (dmg) {
                        s.l("Projectile.spawnProjectileFromRotation((lvl, owner, st) -> new Snowball(lvl, owner, st) {");
                        s.l("    @Override protected void onHitEntity(EntityHitResult res) { super.onHitEntity(res); if (level() instanceof ServerLevel sl) res.getEntity().hurtServer(sl, damageSources().thrown(this, getOwner()), " + Src.f(it.projectileDamage) + "); }");
                        s.l("}, (ServerLevel) level, stack, player, 0.0F, " + speed + ", 1.0F);");
                    } else {
                        s.l("Projectile.spawnProjectileFromRotation(Snowball::new, (ServerLevel) level, stack, player, 0.0F, " + speed + ", 1.0F);");
                    }
                } else {
                    if (dmg) {
                        s.l("Snowball proj = new Snowball(level, player) {");
                        s.l("    @Override protected void onHitEntity(EntityHitResult res) { super.onHitEntity(res); res.getEntity().hurt(damageSources().thrown(this, getOwner()), " + Src.f(it.projectileDamage) + "); }");
                        s.l("};");
                    } else {
                        s.l("Snowball proj = new Snowball(level, player);");
                    }
                    s.l("proj.setItem(stack);");
                    s.l("proj.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, " + speed + ", 1.0F);");
                    s.l("level.addFreshEntity(proj);");
                }
                s.l("if (!player.getAbilities().instabuild) stack.shrink(1);");
            }
            s.l("}");
            if (!it.kind.equals("basic") && !it.kind.equals("throwable")) s.l("return super.use(level, player, hand);");
            else if (ctx.registryKeys()) s.l("return InteractionResult.SUCCESS;");
            else s.l("return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());");
            s.l("}");
        }
        boolean hasKnock = Math.abs(it.attackKnockback) > 0.01;
        if (!it.onHitEntity.isEmpty() || hasKnock) {
            s.imp("net.minecraft.world.entity.LivingEntity");
            if (!it.onHitEntity.isEmpty()) s.imp(ctx.pkg + ".ModProcedures");
            if (hasKnock) s.imp("net.minecraft.util.Mth");
            s.blank().l("@Override");
            if (ctx.v.atLeast(McVersion.V1_21_8)) {
                s.l("public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {");
                if (!it.onHitEntity.isEmpty()) s.l("ModProcedures.run(attacker, " + Src.str("items/" + it.name + "/on_hit") + ");");
                if (hasKnock) s.l("target.knockback(" + Src.d(it.attackKnockback) + ", Mth.sin(attacker.getYRot() * 0.017453292F), -Mth.cos(attacker.getYRot() * 0.017453292F));");
                s.l("super.hurtEnemy(stack, target, attacker);");
            } else {
                s.l("public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {");
                if (!it.onHitEntity.isEmpty()) s.l("ModProcedures.run(attacker, " + Src.str("items/" + it.name + "/on_hit") + ");");
                if (hasKnock) s.l("target.knockback(" + Src.d(it.attackKnockback) + ", Mth.sin(attacker.getYRot() * 0.017453292F), -Mth.cos(attacker.getYRot() * 0.017453292F));");
                s.l("return super.hurtEnemy(stack, target, attacker);");
            }
            s.l("}");
        }
        Elements.ArmorMaterial setMat = isPrimarySetBonusPiece(it) ? armorMat(it) : null;
        String setEff = setMat == null ? null : mobEffect(s, setMat.setBonusEffect);
        boolean hasSetBonus = setEff != null;
        if (!it.onTickInHand.isEmpty() || hasSetBonus) {
            s.imp("net.minecraft.world.entity.Entity");
            if (!it.onTickInHand.isEmpty()) s.imp(ctx.pkg + ".ModProcedures");
            if (hasSetBonus) {
                s.imp("net.minecraft.world.entity.player.Player", "net.minecraft.world.effect.MobEffectInstance", "net.minecraft.world.entity.EquipmentSlot", ctx.pkg + ".ModItems");
                if (Ids.ns(Ids.full(setMat.setBonusEffect)).equals(ctx.ns)) s.imp(ctx.pkg + ".ModEffects");
            }
            s.blank().l("@Override");
            if (ctx.v.atLeast(McVersion.V1_21_8)) {
                s.imp("net.minecraft.server.level.ServerLevel", "net.minecraft.world.entity.EquipmentSlot");
                s.l("public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {");
                if (!it.onTickInHand.isEmpty()) s.l("if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) ModProcedures.run(entity, " + Src.str("items/" + it.name + "/on_tick") + ");");
                if (hasSetBonus) emitArmorSetBonusCheck(s, setMat, setEff);
                s.l("super.inventoryTick(stack, level, entity, slot);");
            } else {
                s.imp("net.minecraft.world.entity.LivingEntity", "net.minecraft.world.level.Level");
                s.l("public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {");
                if (!it.onTickInHand.isEmpty()) s.l("if (!level.isClientSide() && (selected || (entity instanceof LivingEntity le && le.getOffhandItem() == stack))) ModProcedures.run(entity, " + Src.str("items/" + it.name + "/on_tick") + ");");
                if (hasSetBonus) {
                    s.l("if (!level.isClientSide()) {");
                    emitArmorSetBonusCheck(s, setMat, setEff);
                    s.l("}");
                }
                s.l("super.inventoryTick(stack, level, entity, slot, selected);");
            }
            s.l("}");
        }
        emitItemExtraMethods(s, it);
        if (loader == Loader.FORGE && it.burnTime > 0) {
            s.imp("net.minecraft.world.item.crafting.RecipeType");
            s.blank().l("@Override").l("public int getBurnTime(ItemStack itemStack, RecipeType<?> recipeType) {")
                    .l("return " + it.burnTime + ";").l("}");
        }
        if (loader == Loader.FORGE && ModelGen.wornArmorModel(ctx.p, it) != null) {
            s.imp("java.util.function.Consumer", "net.minecraftforge.client.extensions.common.IClientItemExtensions", ctx.pkg + ".client.ModArmorModels");
            s.blank().l("@Override").l("public void initializeClient(Consumer<IClientItemExtensions> consumer) {")
                    .l("consumer.accept(ModArmorModels.ext_" + Ids.toConst(it.name) + "());").l("}");
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "item/" + cls + ".java", s.toString());
    }

    private void emitArmorSetBonusCheck(Src s, Elements.ArmorMaterial am, String eff) {
        s.l("if (entity instanceof Player pl) {");
        for (var e : List.of("helmet", "chestplate", "leggings", "boots")) {
            String slot = e.equals("helmet") ? "HEAD" : e.equals("chestplate") ? "CHEST" : e.equals("leggings") ? "LEGS" : "FEET";
            List<String> matches = new ArrayList<>();
            for (Item arm : ctx.all(Item.class)) {
                if (arm.kind.equals(e) && am.name.equals(arm.armorMaterial)) matches.add("st" + slot + ".is(" + itemRef(arm.name) + ")");
            }
            if (matches.isEmpty()) continue;
            s.l("ItemStack st" + slot + " = pl.getItemBySlot(EquipmentSlot." + slot + ");");
            s.l("if (!(" + String.join(" || ", matches) + ")) return;");
        }
        s.l("pl.addEffect(new MobEffectInstance(" + eff + ", 45, " + Math.max(0, Math.min(255, am.setBonusAmplifier)) + ", true, false));");
        s.l("}");
    }

    private void emitItemExtraMethods(Src s, Item it) {
        if (it.enchantability >= 0 && !ctx.registryKeys()) {
            s.blank().l("@Override public int getEnchantmentValue() { return " + it.enchantability + "; }");
        }
        if (!it.repairItem.isBlank() && !ctx.registryKeys()) {
            s.imp(ctx.pkg + ".ModProcedures");
            if (!Ids.isTag(Ids.full(it.repairItem)) && Ids.ns(Ids.full(it.repairItem)).equals(ctx.ns)) s.imp(ctx.pkg + ".ModItems");
            s.blank().l("@Override public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) { return " + stackMatchesFor("repair", it.repairItem) + "; }");
        }
        if (!it.craftRemainder.isBlank() && !Ids.full(it.craftRemainder).startsWith("minecraft:")) {
            s.imp(ctx.pkg + ".ModItems", ctx.pkg + ".ModProcedures");
            String ret = "new ItemStack(" + anyItemRef(it.craftRemainder) + ")";
            if (loader == Loader.FABRIC) {
                s.blank().l("@Override public ItemStack getRecipeRemainder(ItemStack stack) { return " + ret + "; }");
            } else {
                s.blank().l("@Override public boolean hasCraftingRemainingItem(ItemStack stack) { return true; }");
                s.blank().l("@Override public ItemStack getCraftingRemainingItem(ItemStack stack) { return " + ret + "; }");
            }
        }
        if (it.attributes != null && !it.attributes.isEmpty() && !ctx.components()) {
            s.imp("com.google.common.collect.ImmutableMultimap", "com.google.common.collect.Multimap",
                    "net.minecraft.world.entity.EquipmentSlot", "net.minecraft.world.entity.ai.attributes.Attribute",
                    "net.minecraft.world.entity.ai.attributes.AttributeModifier", "net.minecraft.world.entity.ai.attributes.Attributes");
            s.blank().l("@Override public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {");
            s.l("ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();");
            s.l("builder.putAll(super.getDefaultAttributeModifiers(slot));");
            int idx = 0;
            for (Item.ItemAttribute a : it.attributes) {
                if (a.attribute == null || a.attribute.isBlank()) continue;
                String attr = a.attribute.contains(":") ? Ids.path(a.attribute) : a.attribute.trim();
                if (!attributeExists(attr)) continue;
                String slotConst = switch (a.slot == null ? "mainhand" : a.slot) {
                    case "offhand" -> "OFFHAND";
                    case "head" -> "HEAD";
                    case "chest" -> "CHEST";
                    case "legs" -> "LEGS";
                    case "feet" -> "FEET";
                    default -> "MAINHAND";
                };
                String uuid = java.util.UUID.nameUUIDFromBytes((ctx.ns + ":" + it.name + ":" + idx).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                s.l("if (slot == EquipmentSlot." + slotConst + ") builder.put(Attributes." + attr.toUpperCase(Locale.ROOT)
                        + ", new AttributeModifier(java.util.UUID.fromString(" + Src.str(uuid) + "), " + Src.str(it.name + "_" + idx) + ", "
                        + Src.d(a.amount) + ", " + operation(a.operation) + "));");
                idx++;
            }
            s.l("return builder.build();");
            s.l("}");
        }
        boolean oldFood = it.kind.equals("food") && !ctx.registryKeys() && !it.foodEffect.isBlank();
        String effExpr = oldFood ? mobEffect(s, it.foodEffect) : null;
        boolean hasReturn = !it.useReturnItem.isBlank();
        if (effExpr != null || hasReturn) {
            s.imp("net.minecraft.world.entity.LivingEntity", "net.minecraft.world.level.Level");
            if (effExpr != null) {
                s.imp("net.minecraft.world.effect.MobEffectInstance");
                if (Ids.ns(Ids.full(it.foodEffect)).equals(ctx.ns)) s.imp(ctx.pkg + ".ModEffects");
            }
            if (hasReturn) s.imp("net.minecraft.world.entity.player.Player", ctx.pkg + ".ModProcedures", ctx.pkg + ".ModItems");
            s.blank().l("@Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {");
            s.l("ItemStack result = super.finishUsingItem(stack, level, entity);");
            if (effExpr != null) {
                int ticks = Math.max(1, it.foodEffectSeconds) * 20;
                int amp = Math.max(0, Math.min(255, it.foodEffectAmplifier));
                s.l("if (!level.isClientSide() && level.random.nextDouble() < " + Src.d(Math.max(0.0, Math.min(1.0, it.foodEffectChance))) + ") {");
                s.l("    entity.addEffect(new MobEffectInstance(" + effExpr + ", " + ticks + ", " + amp + "));");
                s.l("}");
            }
            if (hasReturn) {
                String retStack = "new ItemStack(" + anyItemRef(it.useReturnItem) + ")";
                s.l("if (!level.isClientSide()) {");
                s.l("    ItemStack given = " + retStack + ";");
                s.l("    if (result.isEmpty()) return given;");
                s.l("    if (entity instanceof Player pl && !pl.getAbilities().instabuild && !pl.getInventory().add(given)) pl.drop(given, false);");
                s.l("}");
            }
            s.l("return result;");
            s.l("}");
        }
        if (it.kind.equals("food") && !ctx.registryKeys()) {
            if (it.useDurationTicks > 0) {
                s.blank().l("@Override");
                if (ctx.components()) {
                    s.imp("net.minecraft.world.entity.LivingEntity");
                    s.l("public int getUseDuration(ItemStack stack, LivingEntity entity) { return " + it.useDurationTicks + "; }");
                } else {
                    s.l("public int getUseDuration(ItemStack stack) { return " + it.useDurationTicks + "; }");
                }
            }
            if (!"eat".equals(it.consumeAnimation)) {
                s.imp("net.minecraft.world.item.UseAnim");
                String anim = switch (it.consumeAnimation == null ? "eat" : it.consumeAnimation) {
                    case "drink" -> "DRINK";
                    case "block" -> "BLOCK";
                    case "bow" -> "BOW";
                    case "spear" -> "SPEAR";
                    case "none" -> "NONE";
                    default -> "EAT";
                };
                s.blank().l("@Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim." + anim + "; }");
            }
        }
    }

    private String stackMatchesFor(String var, String id) {
        String f = Ids.full(id);
        if (Ids.isTag(f)) return var + ".is(ModProcedures.itemTag(" + Src.str(f.substring(1)) + "))";
        return var + ".is(" + anyItemRef(f) + ")";
    }

    public static String soundType(String s) {
        return "SoundType." + switch (s) {
            case "netherite" -> "NETHERITE_BLOCK";
            case "bone" -> "BONE_BLOCK";
            case "slime" -> "SLIME_BLOCK";
            default -> s.toUpperCase();
        };
    }

    public String blockProps(Block b) {
        StringBuilder sb = new StringBuilder("BlockBehaviour.Properties.of()");
        if (ctx.registryKeys()) sb.append(".setId(").append(blockKey(b.name)).append(')');
        if (b.isCrop()) {
            sb.append(".").append(ctx.noCollision()).append(".randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.DESTROY)");
            if (b.light > 0) sb.append(".lightLevel(state -> ").append(Math.min(15, b.light)).append(')');
            return sb.toString();
        }
        sb.append(".strength(").append(Src.f(b.hardness)).append(", ").append(Src.f(b.resistance)).append(')');
        sb.append(".sound(").append(soundType(b.sound)).append(')');
        if (b.light > 0) sb.append(".lightLevel(state -> ").append(Math.min(15, b.light)).append(')');
        if (b.requiresTool && !b.tool.equals("none")) sb.append(".requiresCorrectToolForDrops()");
        boolean nonFull = !List.of("cube", "cube_bottom_top", "pillar").contains(b.shape);
        if (b.cutout() || nonFull) sb.append(".noOcclusion()");
        if (!b.hasCollision || b.shape.equals("cross") || b.shape.equals("button") || b.shape.equals("pressure_plate")) sb.append(".").append(ctx.noCollision());
        if (!b.onRandomTick.isEmpty()) sb.append(".randomTicks()");
        if (Math.abs(b.friction - 0.6) > 1e-6) sb.append(".friction(").append(Src.f(b.friction)).append(')');
        if (Math.abs(b.speedFactor - 1) > 1e-6) sb.append(".speedFactor(").append(Src.f(b.speedFactor)).append(')');
        if (Math.abs(b.jumpFactor - 1) > 1e-6) sb.append(".jumpFactor(").append(Src.f(b.jumpFactor)).append(')');
        if (b.flammability > 0 || b.fireSpread > 0) sb.append(".ignitedByLava()");
        if (b.instrument != null && !b.instrument.isBlank() && !"harp".equals(b.instrument)) {
            sb.append(".instrument(NoteBlockInstrument.").append(b.instrument.trim().toUpperCase(Locale.ROOT)).append(')');
        }
        if (b.pushReaction != null && !b.pushReaction.isBlank() && !"normal".equals(b.pushReaction)) {
            sb.append(".pushReaction(PushReaction.").append(b.pushReaction.trim().toUpperCase(Locale.ROOT)).append(')');
        }
        return sb.toString();
    }

    private String blockSuperClass(Block b) {
        if ("cube".equals(b.shape) && b.falling) return "FallingBlock";
        return switch (b.shape) {
            case "crop" -> "CropBlock";
            case "pillar" -> "RotatedPillarBlock";
            case "slab" -> "SlabBlock";
            case "stairs" -> "StairBlock";
            case "fence" -> "FenceBlock";
            case "wall" -> "WallBlock";
            case "door" -> "DoorBlock";
            case "trapdoor" -> "TrapDoorBlock";
            case "button" -> "ButtonBlock";
            case "pressure_plate" -> "PressurePlateBlock";
            case "carpet" -> "CarpetBlock";
            case "model" -> b.rotatable ? "HorizontalDirectionalBlock" : "Block";
            default -> "Block";
        };
    }

    private static boolean protectedShapeCtor(Block b) {
        return List.of("stairs", "door", "trapdoor", "button", "pressure_plate").contains(b.shape);
    }

    private static boolean nativelyWaterlogged(Block b) {
        return List.of("slab", "stairs", "fence", "wall", "trapdoor").contains(b.shape);
    }

    private boolean customWaterlog(Block b) {
        return b.waterloggable && !b.isCrop() && !nativelyWaterlogged(b);
    }

    private boolean hasBlockBehavior(Block b) {
        return b.redstoneSignal > 0 || b.canConnectRedstone || customWaterlog(b) || b.falling || b.xpMax > 0
                || (loader != Loader.FABRIC && (b.flammability > 0 || b.fireSpread > 0))
                || !b.onUse.isEmpty() || !b.onStep.isEmpty() || !b.onRandomTick.isEmpty() || !b.onNeighborChanged.isEmpty();
    }

    public String blockConstruct(Block b) {
        String cls = needsBlockClass(b) ? blockClassName(b) : blockSuperClass(b);
        return "new " + cls + "(" + blockProps(b) + ")";
    }

    /** Blocks needing subclasses: crops, rotatable/shaped models, protected vanilla shapes, container blocks, or blocks with behavior/redstone/waterlogging. */
    public boolean needsBlockClass(Block b) {
        if (b.isCrop() || protectedShapeCtor(b) || hasBlockBehavior(b) || blockContainer(b) != null) return true;
        return b.shape.equals("model") && (b.rotatable || shaped(b));
    }

    private ModElements.Container blockContainer(Block b) {
        for (ModElements.Container ct : ctx.all(ModElements.Container.class)) {
            String cid = ct.block.isBlank() ? ct.name : Ids.path(Ids.full(ct.block));
            if (cid.equals(b.name)) return ct;
        }
        return null;
    }

    private boolean shaped(Block b) {
        Model m = ctx.p.model(b.model);
        return b.modelCollision && m != null && !Models.isFullCube(m);
    }

    public String blockClassName(Block b) {
        return Ids.toPascal(b.name) + "Block";
    }

    private static final String[] FACINGS = {"NORTH", "EAST", "SOUTH", "WEST"};

    /** Generates a block subclass with model shape, rotation, waterlogging, redstone and event hooks. */
    public void blockClass(Block b) {
        if (b.isCrop()) {
            cropClass(b);
            return;
        }
        String cls = blockClassName(b);
        String sup = blockSuperClass(b);
        Model m = ctx.p.model(b.model);
        boolean shaped = b.shape.equals("model") && shaped(b), rot = b.shape.equals("model") && b.rotatable;
        boolean wl = customWaterlog(b);
        ModElements.Container ct = blockContainer(b);
        boolean hasContainer = ct != null;
        boolean codec = ctx.v.atLeast(McVersion.V1_20_4);
        boolean newer = ctx.v.atLeast(McVersion.V1_20_4);
        Src s = new Src(ctx.pkg + ".block");
        s.imp("net.minecraft.world.level.block.Block", "net.minecraft.world.level.block.state.BlockBehaviour",
                "net.minecraft.world.level.block.state.BlockState");
        if (!sup.equals("Block")) s.imp("net.minecraft.world.level.block." + sup);
        if (hasContainer) s.imp("net.minecraft.world.level.block.EntityBlock", "net.minecraft.world.level.block.entity.BlockEntity");
        if (b.shape.equals("stairs")) s.imp("net.minecraft.world.level.block.Blocks");
        if (List.of("door", "trapdoor", "button", "pressure_plate").contains(b.shape)) {
            s.imp("net.minecraft.world.level.block.state.properties.BlockSetType");
        }
        if (shaped) s.imp("net.minecraft.core.BlockPos", "net.minecraft.world.level.BlockGetter",
                "net.minecraft.world.phys.shapes.CollisionContext", "net.minecraft.world.phys.shapes.Shapes",
                "net.minecraft.world.phys.shapes.VoxelShape");
        if (rot || wl) {
            s.imp("net.minecraft.core.Direction", "net.minecraft.world.item.context.BlockPlaceContext",
                    "net.minecraft.world.level.block.state.StateDefinition");
        }
        if (wl) {
            s.imp("net.minecraft.core.BlockPos", "net.minecraft.world.level.block.SimpleWaterloggedBlock",
                    "net.minecraft.world.level.block.state.properties.BlockStateProperties",
                    "net.minecraft.world.level.block.state.properties.BooleanProperty",
                    "net.minecraft.world.level.material.FluidState", "net.minecraft.world.level.material.Fluids");
        }
        if (rot && codec) s.imp("com.mojang.serialization.MapCodec");
        if (sup.equals("FallingBlock") && codec) s.imp("com.mojang.serialization.MapCodec");
        List<String> ifaces = new ArrayList<>();
        if (wl) ifaces.add("SimpleWaterloggedBlock");
        if (hasContainer) ifaces.add("EntityBlock");
        s.l("public class " + cls + " extends " + sup + (ifaces.isEmpty() ? "" : " implements " + String.join(", ", ifaces)) + " {");
        if ((rot || sup.equals("FallingBlock")) && codec) s.l("public static final MapCodec<" + cls + "> CODEC = simpleCodec(" + cls + "::new);");
        if (wl) s.l("public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;");
        if (shaped) {
            List<double[]> boxes = new ArrayList<>();
            for (double[] bx : Models.boxes(m)) {
                double[] c = new double[6];
                for (int i = 0; i < 6; i++) c[i] = Models.clamp(bx[i], 0, 16);
                if (c[3] > c[0] && c[4] > c[1] && c[5] > c[2]) boxes.add(c);
            }
            if (rot) {
                for (int q = 0; q < 4; q++) s.l("private static final VoxelShape SHAPE_" + FACINGS[q] + " = " + shapeExpr(boxes, q) + ";");
            } else s.l("private static final VoxelShape SHAPE = " + shapeExpr(boxes, 0) + ";");
        }
        s.blank();
        String setType = b.requiresTool ? "BlockSetType.IRON" : b.sound.equals("wood") ? "BlockSetType.OAK" : "BlockSetType.STONE";
        s.l("public " + cls + "(BlockBehaviour.Properties properties) {");
        switch (b.shape) {
            case "stairs" -> s.l("super(Blocks.STONE.defaultBlockState(), properties);");
            case "door", "trapdoor" -> s.l(newer ? "super(" + setType + ", properties);" : "super(properties, " + setType + ");");
            case "button" -> {
                String ticks = b.sound.equals("wood") ? "30" : "20";
                s.l(newer ? "super(" + setType + ", " + ticks + ", properties);"
                        : "super(properties, " + setType + ", " + ticks + ", " + b.sound.equals("wood") + ");");
            }
            case "pressure_plate" -> s.l(newer ? "super(" + setType + ", properties);"
                    : "super(PressurePlateBlock.Sensitivity." + (b.sound.equals("wood") ? "EVERYTHING" : "MOBS") + ", properties, " + setType + ");");
            default -> s.l("super(properties);");
        }
        if (rot || wl) {
            StringBuilder def = new StringBuilder(sup.equals("Block") || sup.equals("HorizontalDirectionalBlock")
                    ? "stateDefinition.any()" : "defaultBlockState()");
            if (rot) def.append(".setValue(FACING, Direction.NORTH)");
            if (wl) def.append(".setValue(WATERLOGGED, false)");
            s.l("registerDefaultState(" + def + ");");
        }
        s.l("}");
        if ((rot || sup.equals("FallingBlock")) && codec) {
            String codecRet = sup.equals("FallingBlock") ? "FallingBlock" : "HorizontalDirectionalBlock";
            s.blank().l("@Override").l("protected MapCodec<? extends " + codecRet + "> codec() {").l("return CODEC;").l("}");
        }
        if (rot || wl) {
            List<String> added = new ArrayList<>();
            if (rot) added.add("FACING");
            if (wl) added.add("WATERLOGGED");
            s.blank().l("@Override").l("protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {");
            if (!sup.equals("Block") && !sup.equals("HorizontalDirectionalBlock")) s.l("super.createBlockStateDefinition(builder);");
            s.l("builder.add(" + String.join(", ", added) + ");").l("}");
            s.blank().l("@Override").l("public BlockState getStateForPlacement(BlockPlaceContext context) {");
            s.l("BlockState st = " + (sup.equals("Block") || sup.equals("HorizontalDirectionalBlock")
                    ? "defaultBlockState();" : "super.getStateForPlacement(context);"));
            s.l("if (st == null) return null;");
            if (rot) s.l("st = st.setValue(FACING, context.getHorizontalDirection().getOpposite());");
            if (wl) s.l("st = st.setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);");
            s.l("return st;").l("}");
        }
        if (wl) {
            s.blank().l("@Override").l("public FluidState getFluidState(BlockState state) {")
                    .l("return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);").l("}");
            s.blank().l("@Override");
            if (ctx.registryKeys()) {
                s.imp("net.minecraft.world.level.LevelReader", "net.minecraft.world.level.ScheduledTickAccess", "net.minecraft.util.RandomSource");
                s.l("public BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {");
                s.l("if (state.getValue(WATERLOGGED)) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(level));");
                s.l("return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);").l("}");
            } else {
                s.imp("net.minecraft.world.level.LevelAccessor");
                s.l("public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {");
                s.l("if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(level));");
                s.l("return super.updateShape(state, direction, neighborState, level, pos, neighborPos);").l("}");
            }
        }
        if (shaped) {
            s.blank().l("@Override").l("public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {");
            if (rot) {
                s.l("return switch (state.getValue(FACING)) {").l("case EAST -> SHAPE_EAST;").l("case SOUTH -> SHAPE_SOUTH;")
                        .l("case WEST -> SHAPE_WEST;").l("default -> SHAPE_NORTH;").l("};");
            } else s.l("return SHAPE;");
            s.l("}");
        }
        blockBehaviorMethods(s, b, ct);
        if (ct != null) {
            String pas = Ids.toPascal(ct.name);
            s.blank().l("@Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new "
                    + pas + "BlockEntity(pos, state); }");
            new AdvancedModGen(ctx, this, loader).containerBlockMethods(s, ct, pas + "BlockEntity", Ids.toConst(ct.name));
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "block/" + cls + ".java", s.toString());
    }

    private void blockBehaviorMethods(Src s, Block b, ModElements.Container ct) {
        if (b.redstoneSignal > 0 || b.canConnectRedstone) {
            s.blank().l("@Override").l("public boolean isSignalSource(BlockState state) {").l("return true;").l("}");
            if (b.redstoneSignal > 0) {
                s.imp("net.minecraft.core.BlockPos", "net.minecraft.core.Direction", "net.minecraft.world.level.BlockGetter");
                s.blank().l("@Override").l("public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {")
                        .l("return " + Math.max(0, Math.min(15, b.redstoneSignal)) + ";").l("}");
            }
            if (b.canConnectRedstone && loader != Loader.FABRIC) {
                s.imp("net.minecraft.core.BlockPos", "net.minecraft.core.Direction", "net.minecraft.world.level.BlockGetter");
                s.blank().l("@Override").l("public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {")
                        .l("return true;").l("}");
            } else if (b.canConnectRedstone && b.redstoneSignal <= 0) {
                s.blank().l("@Override").l("public boolean isSignalSource(BlockState state) {").l("return true;").l("}");
            }
        }
        if (!b.onUse.isEmpty() && (ct == null || !ct.openOnRightClick)) {
            s.imp("net.minecraft.core.BlockPos", "net.minecraft.world.InteractionResult", "net.minecraft.world.entity.player.Player",
                    "net.minecraft.world.level.Level", "net.minecraft.world.phys.BlockHitResult", ctx.pkg + ".ModProcedures");
            boolean interactive = List.of("door", "trapdoor", "button").contains(b.shape);
            s.blank().l("@Override");
            if (ctx.components()) {
                s.l("protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {");
                s.l("if (!level.isClientSide()) ModProcedures.run(player, " + Src.str("blocks/" + b.name + "/on_use") + ");");
                s.l(interactive ? "return super.useWithoutItem(state, level, pos, player, hit);" : "return InteractionResult.SUCCESS;");
            } else {
                s.imp("net.minecraft.world.InteractionHand");
                s.l("public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {");
                s.l("if (!level.isClientSide()) ModProcedures.run(player, " + Src.str("blocks/" + b.name + "/on_use") + ");");
                s.l(interactive ? "return super.use(state, level, pos, player, hand, hit);" : "return InteractionResult.SUCCESS;");
            }
            s.l("}");
        }
        if (!b.onStep.isEmpty()) {
            s.imp("net.minecraft.core.BlockPos", "net.minecraft.world.entity.Entity", "net.minecraft.world.level.Level",
                    ctx.pkg + ".ModProcedures");
            s.blank().l("@Override").l("public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {");
            s.l("if (!level.isClientSide()) ModProcedures.run(entity, " + Src.str("blocks/" + b.name + "/on_step") + ");");
            s.l("super.stepOn(level, pos, state, entity);").l("}");
        }
        if (!b.onRandomTick.isEmpty()) {
            s.imp("net.minecraft.core.BlockPos", "net.minecraft.server.level.ServerLevel", "net.minecraft.util.RandomSource",
                    ctx.pkg + ".ModProcedures");
            s.blank().l("@Override").l("public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {");
            s.l("ModProcedures.run(level, pos, " + Src.str("blocks/" + b.name + "/on_random_tick") + ");");
            s.l("super.randomTick(state, level, pos, random);").l("}");
        }
        if (!b.onNeighborChanged.isEmpty()) {
            s.imp("net.minecraft.core.BlockPos", "net.minecraft.server.level.ServerLevel", "net.minecraft.world.level.Level",
                    ctx.pkg + ".ModProcedures");
            s.blank().l("@Override");
            if (ctx.registryKeys()) {
                s.imp("net.minecraft.world.level.redstone.Orientation");
                s.l("protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, Orientation orientation, boolean movedByPiston) {");
                s.l("if (level instanceof ServerLevel sl) ModProcedures.run(sl, pos, " + Src.str("blocks/" + b.name + "/on_neighbor_changed") + ");");
                s.l("super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);");
            } else {
                s.l("public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {");
                s.l("if (level instanceof ServerLevel sl) ModProcedures.run(sl, pos, " + Src.str("blocks/" + b.name + "/on_neighbor_changed") + ");");
                s.l("super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);");
            }
            s.l("}");
        }
        if (b.xpMax > 0) {
            int xpMin = Math.max(0, b.xpMin), xpMax = Math.max(xpMin, b.xpMax);
            s.imp("net.minecraft.core.BlockPos", "net.minecraft.server.level.ServerLevel",
                    "net.minecraft.util.valueproviders.UniformInt", "net.minecraft.world.item.ItemStack");
            s.blank().l("@Override");
            if (ctx.v.atLeast(McVersion.V1_20_4)) {
                s.l("protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExperience) {");
                s.l("super.spawnAfterBreak(state, level, pos, tool, dropExperience);");
                s.l("if (dropExperience) this.tryDropExperience(level, pos, tool, UniformInt.of(" + xpMin + ", " + xpMax + "));");
            } else {
                s.imp("net.minecraft.world.item.enchantment.EnchantmentHelper", "net.minecraft.world.item.enchantment.Enchantments");
                s.l("public void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExperience) {");
                s.l("super.spawnAfterBreak(state, level, pos, tool, dropExperience);");
                s.l("if (dropExperience && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) == 0) {");
                s.l("    this.popExperience(level, pos, UniformInt.of(" + xpMin + ", " + xpMax + ").sample(level.random));");
                s.l("}");
            }
            s.l("}");
        }
        if (loader != Loader.FABRIC && (b.flammability > 0 || b.fireSpread > 0)) {
            s.imp("net.minecraft.core.BlockPos", "net.minecraft.core.Direction", "net.minecraft.world.level.BlockGetter");
            s.blank().l("@Override").l("public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return " + Math.max(0, b.flammability) + "; }");
            s.blank().l("@Override").l("public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return " + Math.max(0, b.fireSpread) + "; }");
        }
    }

    /** A CropBlock whose seed is the block's own item. */
    private void cropClass(Block b) {
        String cls = blockClassName(b);
        boolean codec = ctx.v.atLeast(McVersion.V1_20_4);
        Src s = new Src(ctx.pkg + ".block");
        s.imp("net.minecraft.world.level.ItemLike", "net.minecraft.world.level.block.Block", "net.minecraft.world.level.block.CropBlock",
                "net.minecraft.world.level.block.state.BlockBehaviour", "net.minecraft.world.level.block.state.BlockState", ctx.pkg + ".ModItems");
        if (codec) s.imp("com.mojang.serialization.MapCodec");
        s.l("public class " + cls + " extends CropBlock {");
        if (codec) s.l("public static final MapCodec<" + cls + "> CODEC = simpleCodec(" + cls + "::new);").blank();
        s.l("public " + cls + "(BlockBehaviour.Properties properties) {").l("super(properties);").l("}");
        if (codec) s.blank().l("@Override").l("public MapCodec<? extends CropBlock> codec() {").l("return CODEC;").l("}");
        s.blank().l("@Override").l("protected ItemLike getBaseSeedId() {").l("return " + itemRef(b.name) + ";").l("}");
        blockBehaviorMethods(s, b, null);
        s.l("}");
        ctx.files.text(ctx.javaDir() + "block/" + cls + ".java", s.toString());
    }

    /** Java expression combining the boxes (0..16 units) after turning them quarterTurns clockwise. */
    static String shapeExpr(List<double[]> boxes, int quarterTurns) {
        if (boxes.isEmpty()) return "Shapes.block()";
        List<String> parts = new ArrayList<>();
        for (double[] b : boxes) {
            double[] p1 = Models.rotateY(new double[]{b[0], b[1], b[2]}, quarterTurns);
            double[] p2 = Models.rotateY(new double[]{b[3], b[4], b[5]}, quarterTurns);
            parts.add("Block.box(" + d(Math.min(p1[0], p2[0])) + ", " + d(b[1]) + ", " + d(Math.min(p1[2], p2[2])) + ", "
                    + d(Math.max(p1[0], p2[0])) + ", " + d(b[4]) + ", " + d(Math.max(p1[2], p2[2])) + ")");
        }
        if (parts.size() == 1) return parts.get(0);
        return "Shapes.or(" + String.join(", ", parts) + ")";
    }

    private static String d(double v) {
        return v == Math.rint(v) ? (long) v + ".0" : String.valueOf(v);
    }

    public void importsForBlocks(Src s) {
        s.imp("net.minecraft.world.level.block.Block", "net.minecraft.world.level.block.SoundType",
                "net.minecraft.world.level.block.state.BlockBehaviour");
        for (Block b : ctx.all(Block.class)) {
            if (needsBlockClass(b)) s.imp(ctx.pkg + ".block." + blockClassName(b));
            else {
                String sup = blockSuperClass(b);
                if (!sup.equals("Block")) s.imp("net.minecraft.world.level.block." + sup);
            }
            if (b.isCrop() || (b.pushReaction != null && !b.pushReaction.isBlank() && !"normal".equals(b.pushReaction)))
                s.imp("net.minecraft.world.level.material.PushReaction");
            if (b.instrument != null && !b.instrument.isBlank() && !"harp".equals(b.instrument))
                s.imp("net.minecraft.world.level.block.state.properties.NoteBlockInstrument");
        }
        List<Elements.Fluid> fluids = ctx.all(Elements.Fluid.class);
        if (!fluids.isEmpty()) {
            s.imp("net.minecraft.world.level.material.PushReaction");
            for (Elements.Fluid fl : fluids) {
                if (hasFluidBlockClass(fl)) s.imp(ctx.pkg + ".block." + fluidBlockClassName(fl));
                else s.imp("net.minecraft.world.level.block.LiquidBlock");
            }
        }
    }

    public static boolean hasFluidBlockClass(Elements.Fluid fl) {
        return fl.damagePerTick > 0 || !fl.onEntityInside.isEmpty();
    }

    public static String fluidBlockClassName(Elements.Fluid fl) {
        return Ids.toPascal(fl.name) + "FluidBlock";
    }

    public String fluidBlockProps(Elements.Fluid fl) {
        StringBuilder sb = new StringBuilder("BlockBehaviour.Properties.of().replaceable()." + ctx.noCollision() + ".strength(100.0F).pushReaction(PushReaction.DESTROY)");
        if (ctx.registryKeys()) sb.append(".setId(").append(blockKey(fl.name)).append(')');
        if (fl.luminance > 0) sb.append(".lightLevel(state -> ").append(Math.min(15, fl.luminance)).append(')');
        return sb.toString();
    }

    public void fluidBlockClass(Elements.Fluid fl) {
        if (!hasFluidBlockClass(fl)) return;
        String cls = fluidBlockClassName(fl);
        Src s = new Src(ctx.pkg + ".block");
        s.imp("net.minecraft.core.BlockPos", "net.minecraft.world.entity.Entity", "net.minecraft.world.level.Level",
                "net.minecraft.world.level.block.LiquidBlock", "net.minecraft.world.level.block.state.BlockBehaviour",
                "net.minecraft.world.level.block.state.BlockState", "net.minecraft.world.level.material.FlowingFluid");
        if (loader == Loader.FORGE) s.imp("java.util.function.Supplier");
        if (fl.damagePerTick > 0 && ctx.registryKeys()) s.imp("net.minecraft.server.level.ServerLevel");
        if (!fl.onEntityInside.isEmpty()) s.imp(ctx.pkg + ".ModProcedures");
        if (ctx.v.atLeast(McVersion.V1_21_8)) s.imp("net.minecraft.world.entity.InsideBlockEffectApplier");
        s.l("public class " + cls + " extends LiquidBlock {");
        if (loader == Loader.FORGE) {
            s.l("public " + cls + "(Supplier<? extends FlowingFluid> fluid, BlockBehaviour.Properties properties) {");
        } else {
            s.l("public " + cls + "(FlowingFluid fluid, BlockBehaviour.Properties properties) {");
        }
        s.l("super(fluid, properties);").l("}");
        s.blank().l("@Override");
        if (ctx.v.atLeast(McVersion.V1_21_8)) {
            s.l("protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {");
            s.l("super.entityInside(state, level, pos, entity, effectApplier, isPrecise);");
        } else {
            s.l("public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {");
            s.l("super.entityInside(state, level, pos, entity);");
        }
        s.l("if (level.isClientSide()) return;");
        if (fl.damagePerTick > 0) {
            String ds = fl.isLavaLike ? "level.damageSources().lava()" : "level.damageSources().generic()";
            if (ctx.registryKeys()) {
                s.l("if (level instanceof ServerLevel sl) entity.hurtServer(sl, " + ds + ", " + Src.f(fl.damagePerTick) + ");");
            } else {
                s.l("entity.hurt(" + ds + ", " + Src.f(fl.damagePerTick) + ");");
            }
        }
        if (!fl.onEntityInside.isEmpty()) {
            s.l("ModProcedures.run(entity, " + Src.str("fluids/" + fl.name + "/inside") + ");");
        }
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "block/" + cls + ".java", s.toString());
    }

    public static int rgbInt(String hex, int fallback) {
        try {
            return Integer.parseInt(hex.trim().replace("#", ""), 16) & 0xFFFFFF;
        } catch (Exception e) {
            return fallback;
        }
    }

    public String fluidTextureId(Elements.Fluid fl, boolean flowing) {
        String tex = flowing && !fl.flowingTexture.isBlank() ? fl.flowingTexture : fl.stillTexture;
        if (tex != null && !tex.isBlank()) {
            TextureAsset asset = ctx.p.texture(tex);
            return ctx.id("block/" + (asset != null ? asset.name : tex));
        }
        return flowing ? "minecraft:block/water_flow" : "minecraft:block/water_still";
    }

    public String spawnEggExpr(Src i, ModElements.CustomEntity ce) {
        String c = Ids.toConst(ce.name);
        String name = ce.name + "_spawn_egg";
        int p = rgbInt(ce.eggPrimary, 0x3F76E4), sec = rgbInt(ce.eggSecondary, 0x1E3A8A);
        String idPart = ctx.registryKeys() ? ".setId(" + itemKey(name) + ")" : "";
        if (ctx.v.atLeast(McVersion.V1_21_9)) {
            i.imp("net.minecraft.world.item.SpawnEggItem");
            String ref = loader == Loader.FABRIC ? "ModEntities." + c : "ModEntities." + c + ".get()";
            return "new SpawnEggItem(new Item.Properties()" + idPart + ".spawnEgg(" + ref + "))";
        }
        String props = "new Item.Properties()" + idPart;
        if (ctx.v.itemDefinitions()) {
            if (loader == Loader.FORGE) {
                i.imp("net.minecraftforge.common.ForgeSpawnEggItem");
                return "new ForgeSpawnEggItem(ModEntities." + c + ", 0x" + String.format("%06X", p) + ", 0x" + String.format("%06X", sec) + ", " + props + ")";
            }
            i.imp("net.minecraft.world.item.SpawnEggItem");
            String ref = loader == Loader.FABRIC ? "ModEntities." + c : "ModEntities." + c + ".get()";
            return "new SpawnEggItem(" + ref + ", " + props + ")";
        }
        String eggCls = switch (loader) {
            case FABRIC -> "SpawnEggItem";
            case NEOFORGE -> "DeferredSpawnEggItem";
            case FORGE -> "ForgeSpawnEggItem";
        };
        if (loader == Loader.FABRIC) i.imp("net.minecraft.world.item.SpawnEggItem");
        else if (loader == Loader.NEOFORGE) i.imp("net.neoforged.neoforge.common.DeferredSpawnEggItem");
        else i.imp("net.minecraftforge.common.ForgeSpawnEggItem");
        return "new " + eggCls + "(ModEntities." + c + ", 0x" + String.format("%06X", p) + ", 0x" + String.format("%06X", sec) + ", " + props + ")";
    }

    public void procedures() {
        Src s = new Src(ctx.pkg);
        s.imp("net.minecraft.commands.CommandSourceStack", "net.minecraft.core.BlockPos",
                "net.minecraft.core.registries.BuiltInRegistries", "net.minecraft.core.registries.Registries",
                ctx.rlImport(), "net.minecraft.server.MinecraftServer",
                "net.minecraft.server.level.ServerLevel", "net.minecraft.tags.TagKey",
                "net.minecraft.world.entity.Entity", "net.minecraft.world.item.Item",
                "net.minecraft.world.level.block.Block", "net.minecraft.world.phys.Vec3");
        if (ctx.permissionSet()) s.imp("net.minecraft.server.permissions.PermissionSet");
        s.l("/** Runs procedures compiled to functions in data/" + ctx.ns + "/" + ctx.funcDir() + ". */");
        s.l("public final class ModProcedures {");
        s.l("private ModProcedures() {").l("}");
        s.blank();
        s.l("public static void run(Entity entity, String function) {");
        s.l("if (entity.level().isClientSide()) return;");
        s.l("MinecraftServer server = entity.level().getServer();");
        s.l("if (server == null) return;");
        if (ctx.registryKeys()) {
            s.l("CommandSourceStack source = entity.createCommandSourceStackForNameResolution((ServerLevel) entity.level())." + ctx.withAllPermissions() + ".withSuppressedOutput();");
        } else {
            s.l("CommandSourceStack source = entity.createCommandSourceStack()." + ctx.withAllPermissions() + ".withSuppressedOutput();");
        }
        s.l("server.getCommands().performPrefixedCommand(source, \"function \" + " + ctx.mainClass + ".MODID + \":\" + function);");
        s.l("}");
        s.blank();
        s.l("public static void run(ServerLevel level, BlockPos pos, String function) {");
        s.l("CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level).withPosition(Vec3.atCenterOf(pos))." + ctx.withAllPermissions() + ".withSuppressedOutput();");
        s.l("run(source, function);");
        s.l("}");
        s.blank();
        s.l("public static void run(CommandSourceStack source, String function) {");
        s.l("source.getServer().getCommands().performPrefixedCommand(source." + ctx.withAllPermissions() + ".withSuppressedOutput(), \"function \" + " + ctx.mainClass + ".MODID + \":\" + function);");
        s.l("}");
        s.blank();
        s.l("public static Item item(String id) {");
        s.l("return BuiltInRegistries.ITEM." + (ctx.registryKeys() ? "getValue(" : "get(") + ctx.rlParseExpr("id") + ");");
        s.l("}");
        s.blank();
        s.l("public static TagKey<Item> itemTag(String id) {");
        s.l("return TagKey.create(Registries.ITEM, " + ctx.rlParseExpr("id") + ");");
        s.l("}");
        s.blank();
        s.l("public static TagKey<Block> blockTag(String id) {");
        s.l("return TagKey.create(Registries.BLOCK, " + ctx.rlParseExpr("id") + ");");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModProcedures.java", s.toString());
    }

    /** Condition expression testing an ItemStack variable named "stack". */
    public String stackMatches(String id) {
        String f = Ids.full(id);
        if (Ids.isTag(f)) return "stack.is(ModProcedures.itemTag(" + Src.str(f.substring(1)) + "))";
        return "stack.is(" + anyItemRef(f) + ")";
    }

    public boolean hasCommands() {
        return !ctx.all(Elements.Command.class).isEmpty() || new BridgeGen(ctx).modNeeded()
                || ScreenGen.needsCommand(ctx) || !JavaSteps.hooks(ctx, "register_commands").isEmpty();
    }

    public void commands() {
        Src s = new Src(ctx.pkg);
        s.imp("com.mojang.brigadier.CommandDispatcher", "net.minecraft.commands.CommandSourceStack", "net.minecraft.commands.Commands");
        s.l("public final class ModCommands {");
        s.l("private ModCommands() {").l("}");
        s.blank();
        s.l("public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {");
        BridgeGen bridge = new BridgeGen(ctx);
        for (Elements.Command c : ctx.all(Elements.Command.class)) {
            if (!c.branches.isEmpty()) {
                BridgeGen.treeImports(s, ctx);
                if (ctx.permissionSet()) s.imp("net.minecraft.server.permissions.Permissions");
                for (String line : bridge.brigadier(c)) s.l(line);
                continue;
            }
            int perm = Math.max(0, Math.min(4, c.permission));
            if (ctx.permissionSet() && perm > 0) s.imp("net.minecraft.server.permissions.Permissions");
            s.l("dispatcher.register(Commands.literal(" + Src.str(c.name) + ").requires(source -> "
                    + ctx.hasPermissionExpr("source", perm, false) + ").executes(context -> {");
            s.l("ModProcedures.run(context.getSource(), " + Src.str("commands/" + c.name) + ");");
            s.l("return 1;");
            s.l("}));");
        }
        if (modstudio.gen.InterfaceGen.needsBridge(ctx.p)) s.l("ModBridge.register(dispatcher);");
        if (ScreenGen.needsCommand(ctx)) s.l("ModUiCommand.register(dispatcher);");
        for (var h : JavaSteps.hooks(ctx, "register_commands")) s.l(h.fqcn() + "." + h.method() + "(dispatcher);");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModCommands.java", s.toString());
        bridge.mod();
        if (ScreenGen.needsCommand(ctx)) new ScreenGen(ctx).serverClass();
    }

    /** itemKey/blockKey helpers on the main class for 1.21.2+ registry ids. */
    public static void registryKeyHelpers(GenContext ctx, Src s) {
        if (!ctx.registryKeys()) return;
        s.imp("net.minecraft.core.registries.Registries", "net.minecraft.resources.ResourceKey",
                "net.minecraft.world.item.Item", "net.minecraft.world.level.block.Block");
        s.l("public static ResourceKey<Item> itemKey(String path) {");
        s.l("return ResourceKey.create(Registries.ITEM, id(path));");
        s.l("}");
        s.blank();
        s.l("public static ResourceKey<Block> blockKey(String path) {");
        s.l("return ResourceKey.create(Registries.BLOCK, id(path));");
        s.l("}");
        s.blank();
    }

    public void writeIcon(String path) {
        TextureAsset t = ctx.p.texture(ctx.p.meta.icon);
        if (t != null) ctx.files.bytes(path, t.bytes());
    }

    private boolean isPrimarySetBonusPiece(Item it) {
        Elements.ArmorMaterial am = armorMat(it);
        if (am == null || am.setBonusEffect == null || am.setBonusEffect.isBlank()) return false;
        for (String slot : List.of("helmet", "chestplate", "leggings", "boots")) {
            for (Item arm : ctx.all(Item.class)) {
                if (arm.kind.equals(slot) && am.name.equals(arm.armorMaterial)) return arm == it;
            }
        }
        return false;
    }

    /** Names of project items/blocks assigned to a vanilla creative tab key across mod loaders. */
    static List<String> tabMembers(GenContext ctx, AdvancedModGen adv, String key) {
        List<String> r = new ArrayList<>();
        for (Element e : ctx.p.elements) {
            if (e instanceof Item it && it.creativeTab.equals(key)) r.add(it.name);
            if (e instanceof Block b && b.creativeTab.equals(key)) r.add(b.name);
            if (e instanceof Elements.Fluid fl && fl.generateBucket && fl.creativeTab.equals(key)) r.add(fl.name + "_bucket");
            if (e instanceof ModElements.CustomEntity ce && ce.spawnEgg && ce.isLivingMob() && ce.creativeTab.equals(key)) r.add(ce.name + "_spawn_egg");
            if (e instanceof ModElements.Portal pt && pt.generateIgniter && pt.creativeTab.equals(key)) r.add(pt.name + "_igniter");
            if (e instanceof ModElements.ArmorTrim at && at.generateTemplate && at.creativeTab.equals(key)) r.add(at.name + "_armor_trim_smithing_template");
        }
        if ("minecraft:functional_blocks".equals(key)) {
            for (ModElements.Container ct : ctx.all(ModElements.Container.class)) {
                if (adv.standaloneContainerBlock(ct)) r.add(ct.block.isBlank() ? ct.name : Ids.path(Ids.full(ct.block)));
            }
        }
        return r;
    }

    /** Applies user code files for this target, adjusting resource roots and pack folders when needed. */
    public static void codeFiles(GenContext ctx) {
        boolean forgeEb7 = ctx.target == Target.FORGE && ctx.v.atLeast(McVersion.V1_21_8);
        for (CodeFile cf : ctx.all(CodeFile.class)) {
            if (!Target.matchesSelector(cf.target, ctx.target) || cf.path.isBlank()) continue;
            String path = cf.path.replace("{package}", ctx.pkgPath).replace("{modid}", ctx.ns);
            if (!ctx.target.usesGradle()) {
                if (cf.isJvmLanguage()) continue;
                if (path.startsWith("src/main/resources/")) path = path.substring("src/main/resources/".length());
                if (ctx.target == Target.RESOURCEPACK && path.startsWith("data/")) continue;
                if (ctx.target == Target.DATAPACK && path.startsWith("assets/")) continue;
            }
            path = adaptResourceDataFolder(path, ctx);
            String content = cf.content.replace("{package_name}", ctx.pkg).replace("{modid}", ctx.ns).replace("{main_class}", ctx.mainClass);
            if (forgeEb7 && cf.isJvmLanguage()) {
                content = content.replace("net.minecraftforge.eventbus.api.SubscribeEvent",
                        "net.minecraftforge.eventbus.api.listener.SubscribeEvent");
            }
            ctx.files.text(path, content);
        }
    }

    private static String adaptResourceDataFolder(String path, GenContext ctx) {
        String prefix = path.startsWith("src/main/resources/data/") ? "src/main/resources/data/"
                : path.startsWith("data/") ? "data/" : null;
        if (prefix == null) return path;
        String tail = path.substring(prefix.length());
        int s1 = tail.indexOf('/');
        if (s1 <= 0) return path;
        String ns = tail.substring(0, s1);
        String afterNs = tail.substring(s1 + 1);
        boolean s = ctx.singular();
        String[][] swaps = {
                {"recipe/", "recipes/", ctx.recipeDir() + "/"},
                {"advancement/", "advancements/", ctx.advDir() + "/"},
                {"loot_table/", "loot_tables/", ctx.lootDir() + "/"},
                {"function/", "functions/", ctx.funcDir() + "/"},
                {"predicate/", "predicates/", ctx.predicateDir() + "/"},
                {"item_modifier/", "item_modifiers/", (s ? "item_modifier" : "item_modifiers") + "/"},
                {"tags/item/", "tags/items/", s ? "tags/item/" : "tags/items/"},
                {"tags/block/", "tags/blocks/", s ? "tags/block/" : "tags/blocks/"},
                {"tags/function/", "tags/functions/", s ? "tags/function/" : "tags/functions/"},
                {"tags/entity_type/", "tags/entity_types/", s ? "tags/entity_type/" : "tags/entity_types/"},
                {"tags/fluid/", "tags/fluids/", s ? "tags/fluid/" : "tags/fluids/"}
        };
        for (String[] pair : swaps) {
            if (afterNs.startsWith(pair[0])) return prefix + ns + "/" + pair[2] + afterNs.substring(pair[0].length());
            if (afterNs.startsWith(pair[1])) return prefix + ns + "/" + pair[2] + afterNs.substring(pair[1].length());
        }
        return path;
    }
}
