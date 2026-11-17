package modstudio.gen;

import modstudio.core.*;
import modstudio.core.Elements.*;

import java.util.ArrayList;
import java.util.List;

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
                || (loader == Loader.FORGE && it.burnTime > 0);
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
        String r = m.repairItem.isBlank() ? "" : Ids.full(m.repairItem);
        if (r.isEmpty()) return "Ingredient.EMPTY";
        if (Ids.isTag(r)) return "Ingredient.of(ModProcedures.itemTag(" + Src.str(r.substring(1)) + "))";
        return "Ingredient.of(" + anyItemRef(r) + ")";
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
                s.imp(ctx.registryKeys() ? "net.minecraft.world.item.ToolMaterial" : "net.minecraft.world.item.Tiers");
                s.imp("net.minecraft.world.item." + superClass(it));
                if (ctx.components() && !ctx.registryKeys()) {
                    s.imp(it.kind.equals("sword") ? "net.minecraft.world.item.SwordItem" : "net.minecraft.world.item.DiggerItem");
                }
            }
            if (it.kind.equals("food") && it.fastFood && ctx.registryKeys()) s.imp("net.minecraft.world.item.component.Consumables");
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
        if (!it.damageable() && it.maxStack != 64) sb.append(".stacksTo(").append(Math.max(1, Math.min(64, it.maxStack))).append(')');
        if (!it.rarity.equals("common")) sb.append(".rarity(Rarity.").append(it.rarity.toUpperCase()).append(')');
        if (it.fireResistant) sb.append(".fireResistant()");
        if (it.customTooltip && ctx.v.atLeast(McVersion.V1_21_4)) {
            sb.append(".component(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE, ").append(ctx.mainClass).append(".id(").append(Src.str(it.name)).append("))");
        }
        if (it.kind.equals("food")) {
            sb.append(".food(").append(food(it));
            if (it.fastFood && ctx.registryKeys()) sb.append(", Consumables.defaultFood().consumeSeconds(0.8F).build()");
            sb.append(')');
        }
        if (!it.song.isBlank() && ctx.singular() && ctx.p.byName(ElementType.SONG, it.song) != null) {
            sb.append(".jukeboxPlayable(ResourceKey.create(Registries.JUKEBOX_SONG, ").append(ctx.mainClass).append(".id(")
                    .append(Src.str(it.song)).append("))).stacksTo(1)");
        }
        Elements.ArmorMaterial am = armorMat(it);
        if (am != null && armorProps()) sb.append(".humanoidArmor(").append(armorConst(it)).append(", ").append(armorType(it)).append(')');
        else if (am != null && registeredArmor()) sb.append(".durability(").append(am.durabilityOf(it.kind)).append(')');
        if (it.isTool() && !toolClass(it)) {
            sb.append('.').append(it.kind).append('(').append(tier(it.tier)).append(", ").append(Src.f(it.attackDamage))
                    .append(", ").append(Src.f(it.attackSpeed)).append(')');
        }
        return sb.toString();
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
        if (!procs.isEmpty()) {
            s.imp("net.minecraft.world.InteractionHand", "net.minecraft.world.entity.player.Player", "net.minecraft.world.level.Level",
                    ctx.pkg + ".ModProcedures");
            if (ctx.registryKeys()) {
                s.imp("net.minecraft.world.InteractionResult");
                s.blank().l("@Override").l("public InteractionResult use(Level level, Player player, InteractionHand hand) {");
            } else {
                s.imp("net.minecraft.world.InteractionResultHolder");
                s.blank().l("@Override").l("public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {");
            }
            s.l("if (!level.isClientSide()) {");
            for (Procedure p : procs) s.l("ModProcedures.run(player, " + Src.str("procedures/" + p.name) + ");");
            s.l("}");
            if (!it.kind.equals("basic")) s.l("return super.use(level, player, hand);");
            else if (ctx.registryKeys()) s.l("return InteractionResult.SUCCESS;");
            else s.l("return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());");
            s.l("}");
        }
        if (loader == Loader.FORGE && it.burnTime > 0) {
            s.imp("net.minecraft.world.item.crafting.RecipeType");
            s.blank().l("@Override").l("public int getBurnTime(ItemStack itemStack, RecipeType<?> recipeType) {")
                    .l("return " + it.burnTime + ";").l("}");
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "item/" + cls + ".java", s.toString());
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
            sb.append(".noCollission().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.DESTROY)");
            if (b.light > 0) sb.append(".lightLevel(state -> ").append(Math.min(15, b.light)).append(')');
            return sb.toString();
        }
        sb.append(".strength(").append(Src.f(b.hardness)).append(", ").append(Src.f(b.resistance)).append(')');
        sb.append(".sound(").append(soundType(b.sound)).append(')');
        if (b.light > 0) sb.append(".lightLevel(state -> ").append(Math.min(15, b.light)).append(')');
        if (b.requiresTool && !b.tool.equals("none")) sb.append(".requiresCorrectToolForDrops()");
        if (b.cutout()) sb.append(".noOcclusion()");
        if (b.shape.equals("cross")) sb.append(".noCollission()");
        if (Math.abs(b.friction - 0.6) > 1e-6) sb.append(".friction(").append(Src.f(b.friction)).append(')');
        if (Math.abs(b.speedFactor - 1) > 1e-6) sb.append(".speedFactor(").append(Src.f(b.speedFactor)).append(')');
        if (Math.abs(b.jumpFactor - 1) > 1e-6) sb.append(".jumpFactor(").append(Src.f(b.jumpFactor)).append(')');
        return sb.toString();
    }

    public String blockConstruct(Block b) {
        String cls = needsBlockClass(b) ? blockClassName(b) : b.shape.equals("pillar") ? "RotatedPillarBlock" : "Block";
        return "new " + cls + "(" + blockProps(b) + ")";
    }

    /** Model blocks with a custom outline or a facing direction get their own class. */
    public boolean needsBlockClass(Block b) {
        if (b.isCrop()) return true;
        if (!b.shape.equals("model")) return false;
        return b.rotatable || shaped(b);
    }

    private boolean shaped(Block b) {
        Model m = ctx.p.model(b.model);
        return b.modelCollision && m != null && !Models.isFullCube(m);
    }

    public String blockClassName(Block b) {
        return Ids.toPascal(b.name) + "Block";
    }

    private static final String[] FACINGS = {"NORTH", "EAST", "SOUTH", "WEST"};

    /** Generates a block class with the model's outline and, when rotatable, a horizontal facing property. */
    public void blockClass(Block b) {
        if (b.isCrop()) {
            cropClass(b);
            return;
        }
        String cls = blockClassName(b);
        Model m = ctx.p.model(b.model);
        boolean shaped = shaped(b), rot = b.rotatable;
        boolean codec = ctx.v.atLeast(McVersion.V1_20_4);
        Src s = new Src(ctx.pkg + ".block");
        s.imp("net.minecraft.world.level.block.Block", "net.minecraft.world.level.block.state.BlockBehaviour",
                "net.minecraft.world.level.block.state.BlockState");
        if (shaped) s.imp("net.minecraft.core.BlockPos", "net.minecraft.world.level.BlockGetter",
                "net.minecraft.world.phys.shapes.CollisionContext", "net.minecraft.world.phys.shapes.Shapes",
                "net.minecraft.world.phys.shapes.VoxelShape");
        if (rot) s.imp("net.minecraft.core.Direction", "net.minecraft.world.item.context.BlockPlaceContext",
                "net.minecraft.world.level.block.HorizontalDirectionalBlock", "net.minecraft.world.level.block.state.StateDefinition");
        if (rot && codec) s.imp("com.mojang.serialization.MapCodec");
        s.l("public class " + cls + " extends " + (rot ? "HorizontalDirectionalBlock" : "Block") + " {");
        if (rot && codec) s.l("public static final MapCodec<" + cls + "> CODEC = simpleCodec(" + cls + "::new);");
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
        s.l("public " + cls + "(BlockBehaviour.Properties properties) {").l("super(properties);");
        if (rot) s.l("registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));");
        s.l("}");
        if (rot) {
            if (codec) s.blank().l("@Override").l("protected MapCodec<? extends HorizontalDirectionalBlock> codec() {").l("return CODEC;").l("}");
            s.blank().l("@Override").l("protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {")
                    .l("builder.add(FACING);").l("}");
            s.blank().l("@Override").l("public BlockState getStateForPlacement(BlockPlaceContext context) {")
                    .l("return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());").l("}");
        }
        if (shaped) {
            s.blank().l("@Override").l("public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {");
            if (rot) {
                s.l("return switch (state.getValue(FACING)) {").l("case EAST -> SHAPE_EAST;").l("case SOUTH -> SHAPE_SOUTH;")
                        .l("case WEST -> SHAPE_WEST;").l("default -> SHAPE_NORTH;").l("};");
            } else s.l("return SHAPE;");
            s.l("}");
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "block/" + cls + ".java", s.toString());
    }

    /** A CropBlock whose seed is the block's own item. */
    private void cropClass(Block b) {
        String cls = blockClassName(b);
        boolean codec = ctx.v.atLeast(McVersion.V1_20_4);
        Src s = new Src(ctx.pkg + ".block");
        s.imp("net.minecraft.world.level.ItemLike", "net.minecraft.world.level.block.CropBlock",
                "net.minecraft.world.level.block.state.BlockBehaviour", ctx.pkg + ".ModItems");
        if (codec) s.imp("com.mojang.serialization.MapCodec");
        s.l("public class " + cls + " extends CropBlock {");
        if (codec) s.l("public static final MapCodec<" + cls + "> CODEC = simpleCodec(" + cls + "::new);").blank();
        s.l("public " + cls + "(BlockBehaviour.Properties properties) {").l("super(properties);").l("}");
        if (codec) s.blank().l("@Override").l("public MapCodec<? extends CropBlock> codec() {").l("return CODEC;").l("}");
        s.blank().l("@Override").l("protected ItemLike getBaseSeedId() {").l("return " + itemRef(b.name) + ";").l("}");
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
            if (b.isCrop()) s.imp("net.minecraft.world.level.material.PushReaction");
            else if (b.shape.equals("pillar")) s.imp("net.minecraft.world.level.block.RotatedPillarBlock");
        }
    }

    public void procedures() {
        Src s = new Src(ctx.pkg);
        s.imp("net.minecraft.commands.CommandSourceStack", "net.minecraft.core.registries.BuiltInRegistries",
                "net.minecraft.core.registries.Registries", "net.minecraft.resources.ResourceLocation",
                "net.minecraft.server.MinecraftServer", "net.minecraft.tags.TagKey",
                "net.minecraft.world.entity.Entity", "net.minecraft.world.item.Item");
        s.l("/** Runs procedures compiled to functions in data/" + ctx.ns + "/" + ctx.funcDir() + ". */");
        s.l("public final class ModProcedures {");
        s.l("private ModProcedures() {").l("}");
        s.blank();
        s.l("public static void run(Entity entity, String function) {");
        s.l("if (entity.level().isClientSide()) return;");
        s.l("MinecraftServer server = entity.getServer();");
        s.l("if (server == null) return;");
        if (ctx.registryKeys()) {
            s.imp("net.minecraft.server.level.ServerLevel");
            s.l("CommandSourceStack source = entity.createCommandSourceStackForNameResolution((ServerLevel) entity.level()).withPermission(4).withSuppressedOutput();");
        } else {
            s.l("CommandSourceStack source = entity.createCommandSourceStack().withPermission(4).withSuppressedOutput();");
        }
        s.l("server.getCommands().performPrefixedCommand(source, \"function \" + " + ctx.mainClass + ".MODID + \":\" + function);");
        s.l("}");
        s.blank();
        s.l("public static void run(CommandSourceStack source, String function) {");
        s.l("source.getServer().getCommands().performPrefixedCommand(source.withPermission(4).withSuppressedOutput(), \"function \" + " + ctx.mainClass + ".MODID + \":\" + function);");
        s.l("}");
        s.blank();
        s.l("public static Item item(String id) {");
        s.l("return BuiltInRegistries.ITEM." + (ctx.registryKeys() ? "getValue(" : "get(") + GenContext.rlParse(ctx.singular(), "id") + ");");
        s.l("}");
        s.blank();
        s.l("public static TagKey<Item> itemTag(String id) {");
        s.l("return TagKey.create(Registries.ITEM, " + GenContext.rlParse(ctx.singular(), "id") + ");");
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
        return !ctx.all(Elements.Command.class).isEmpty() || new BridgeGen(ctx).modNeeded();
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
                BridgeGen.treeImports(s);
                for (String line : bridge.brigadier(c)) s.l(line);
                continue;
            }
            s.l("dispatcher.register(Commands.literal(" + Src.str(c.name) + ").requires(source -> source.hasPermission("
                    + Math.max(0, Math.min(4, c.permission)) + ")).executes(context -> {");
            s.l("ModProcedures.run(context.getSource(), " + Src.str("commands/" + c.name) + ");");
            s.l("return 1;");
            s.l("}));");
        }
        if (modstudio.gen.InterfaceGen.needsBridge(ctx.p)) s.l("ModBridge.register(dispatcher);");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModCommands.java", s.toString());
        bridge.mod();
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

    /** Applies user code files for this target. */
    public static void codeFiles(GenContext ctx) {
        for (CodeFile cf : ctx.all(CodeFile.class)) {
            boolean match = cf.target.equals(ctx.target.id)
                    || (cf.target.equals("mods") && ctx.target.isMod());
            if (!match || cf.path.isBlank()) continue;
            String path = cf.path.replace("{package}", ctx.pkgPath).replace("{modid}", ctx.ns);
            String content = cf.content.replace("{package_name}", ctx.pkg).replace("{modid}", ctx.ns).replace("{main_class}", ctx.mainClass);
            ctx.files.text(path, content);
        }
    }
}
