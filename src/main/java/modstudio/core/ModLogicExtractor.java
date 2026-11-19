package modstudio.core;

import modstudio.core.Elements.Step;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts real gameplay properties and behavioral procedures from both compiled JVM {@code .class}
 * files (constant pools + bytecode instruction streams across Mojmap, NeoForge, Forge, and Fabric Intermediary)
 * and Java/Kotlin source files when importing existing Minecraft mods.
 */
public final class ModLogicExtractor {
    private ModLogicExtractor() {}

    /** Extensible rule hook invoked per scanned class during {@link #analyze}. */
    private static final List<BiConsumer<ClassInfo, Result>> CUSTOM_RULES = new CopyOnWriteArrayList<>();

    public record ClassInfo(String internalName, String superName, int accessFlags, List<String> methodNames, List<String> stringConstants) {}

    /** Registers a custom bytecode extraction rule (used by ModStudioPlugin extensions). */
    public static void registerRule(BiConsumer<ClassInfo, Result> rule) {
        if (rule != null) CUSTOM_RULES.add(rule);
    }

    public static final class ItemProps {
        public String kind;
        public String tier;
        public Integer maxStack;
        public Integer maxDamage;
        public Integer enchantability;
        public Boolean fireResistant;
        public Boolean glint;
        public String rarity;
        public Double attackDamage;
        public Double attackSpeed;
        public Double attackKnockback;
        public Double useCooldown;
        public String craftRemainder;
        public Integer nutrition;
        public Double saturation;
        public Boolean alwaysEdible;
        public Boolean fastFood;
        public String foodEffect;
        public Integer foodEffectSeconds;
        public Integer foodEffectAmplifier;
        public Double foodEffectChance;
        public String armorMatRef;
        public String customClass;
        public final List<Step> onUse = new ArrayList<>();
        public final List<Step> onHitEntity = new ArrayList<>();
        public final List<Step> onTickInHand = new ArrayList<>();
    }

    public static final class BlockProps {
        public String shape;
        public Double hardness;
        public Double resistance;
        public Integer light;
        public Boolean requiresTool;
        public Boolean hasCollision;
        public Boolean falling;
        public Double friction;
        public Double speedFactor;
        public Double jumpFactor;
        public String sound;
        public String instrument;
        public String pushReaction;
        public Integer xpMin;
        public Integer xpMax;
        public String customClass;
        public final List<Step> onUse = new ArrayList<>();
        public final List<Step> onStep = new ArrayList<>();
        public final List<Step> onRandomTick = new ArrayList<>();
    }

    public static final class ArmorMatProps {
        public int durability = -1;
        public int helmet = -1, chestplate = -1, leggings = -1, boots = -1;
        public double toughness = -1;
        public double knockbackResistance = -1;
        public int enchantability = -1;
        public String equipSound;
        public String repairItem;
    }

    public static final class EntityProps {
        public String kind;
        public String category;
        public Double width, height, scale;
        public Double health, speed, attackDamage, followRange, armor, knockbackResistance;
        public Integer xpReward;
        public Boolean fireImmune;
        public Boolean boss;
        public String bossColor;
        public final List<String> aiGoals = new ArrayList<>();
        public Double projectileDamage;
        public String customClass;
        public final List<Step> onSpawn = new ArrayList<>();
        public final List<Step> onHurt = new ArrayList<>();
        public final List<Step> onDeath = new ArrayList<>();
        public final List<Step> onInteract = new ArrayList<>();
    }

    public static final class Result {
        public final Map<String, ItemProps> items = new LinkedHashMap<>();
        public final Map<String, BlockProps> blocks = new LinkedHashMap<>();
        public final Map<String, ArmorMatProps> armors = new LinkedHashMap<>();
        public final Map<String, EntityProps> entities = new LinkedHashMap<>();
        public final List<String> notes = new ArrayList<>();
        public int analyzedClasses;
        public int extractedHooks;
    }

    private static final Map<String, String> LEGACY_EFFECT_NAMES = Map.ofEntries(
            Map.entry("MOVEMENT_SPEED", "speed"),
            Map.entry("MOVEMENT_SLOWDOWN", "slowness"),
            Map.entry("DIG_SPEED", "haste"),
            Map.entry("DIG_SLOWDOWN", "mining_fatigue"),
            Map.entry("DAMAGE_BOOST", "strength"),
            Map.entry("HEAL", "instant_health"),
            Map.entry("HARM", "instant_damage"),
            Map.entry("JUMP", "jump_boost"),
            Map.entry("CONFUSION", "nausea"),
            Map.entry("DAMAGE_RESISTANCE", "resistance"),
            // Minecraft 1.12.2 Forge SRG MobEffects fields
            Map.entry("FIELD_76424_C", "speed"),
            Map.entry("FIELD_76421_D", "slowness"),
            Map.entry("FIELD_76422_E", "haste"),
            Map.entry("FIELD_76419_F", "mining_fatigue"),
            Map.entry("FIELD_76420_G", "strength"),
            Map.entry("FIELD_76432_H", "instant_health"),
            Map.entry("FIELD_76433_I", "instant_damage"),
            Map.entry("FIELD_76430_J", "jump_boost"),
            Map.entry("FIELD_76431_K", "nausea"),
            Map.entry("FIELD_76428_L", "regeneration"),
            Map.entry("FIELD_76429_M", "resistance"),
            Map.entry("FIELD_76426_N", "fire_resistance"),
            Map.entry("FIELD_76427_O", "water_breathing"),
            Map.entry("FIELD_76441_P", "invisibility"),
            Map.entry("FIELD_76440_Q", "blindness"),
            Map.entry("FIELD_76439_R", "night_vision"),
            Map.entry("FIELD_76438_S", "hunger"),
            Map.entry("FIELD_76437_T", "weakness"),
            Map.entry("FIELD_76436_U", "poison"),
            Map.entry("FIELD_82731_V", "wither"),
            Map.entry("FIELD_180152_W", "health_boost"),
            Map.entry("FIELD_76444_X", "absorption"),
            Map.entry("FIELD_76443_Y", "saturation"),
            Map.entry("FIELD_188423_X", "glowing"),
            Map.entry("FIELD_188424_Y", "levitation"),
            Map.entry("FIELD_188425_Z", "luck"),
            Map.entry("FIELD_189112_A", "unluck")
    );

    private static final Map<String, String> LEGACY_SOUND_TYPES = Map.ofEntries(
            Map.entry("FIELD_185848_A", "wood"),
            Map.entry("FIELD_185849_B", "gravel"),
            Map.entry("FIELD_185850_C", "grass"),
            Map.entry("FIELD_185851_D", "stone"),
            Map.entry("FIELD_185852_E", "metal"),
            Map.entry("FIELD_185853_F", "glass"),
            Map.entry("FIELD_185854_G", "wool"),
            Map.entry("FIELD_185855_H", "sand"),
            Map.entry("FIELD_185856_I", "snow"),
            Map.entry("FIELD_185857_J", "ladder"),
            Map.entry("FIELD_185858_K", "anvil"),
            Map.entry("FIELD_185859_L", "slime")
    );

    public static Result analyze(Collection<String> classPaths,
                                 Function<String, byte[]> classBytesLoader,
                                 Map<String, String> textSources,
                                 String primaryModId) {
        Result res = new Result();
        Map<String, ClassModel> classes = new LinkedHashMap<>();
        if (classPaths != null && classBytesLoader != null) {
            for (String path : classPaths) {
                if (path == null || !path.endsWith(".class") || path.contains("module-info")) continue;
                byte[] raw = classBytesLoader.apply(path);
                if (raw == null || raw.length < 24) continue;
                ClassModel cm = parseClass(raw);
                if (cm != null && !cm.internalName.isBlank()) {
                    classes.put(cm.internalName, cm);
                }
            }
        }
        res.analyzedClasses = classes.size();

        if (!classes.isEmpty()) {
            analyzeBytecodeClasses(classes, primaryModId, res);
            if (!CUSTOM_RULES.isEmpty()) {
                for (ClassModel cm : classes.values()) {
                    List<String> mnames = cm.methods.stream().map(m -> m.name).toList();
                    List<String> strs = new ArrayList<>();
                    for (MethodModel mm : cm.methods) {
                        for (Insn in : mm.insns) if (in.kind == InsnKind.PUSH_STR && in.strVal != null) strs.add(in.strVal);
                    }
                    ClassInfo ci = new ClassInfo(cm.internalName, cm.superName, cm.accessFlags, mnames, strs);
                    for (var rule : CUSTOM_RULES) rule.accept(ci, res);
                }
            }
        }
        if (textSources != null && !textSources.isEmpty()) {
            analyzeTextSources(textSources, primaryModId, res);
        }
        return res;
    }

    // ------------------------------------------------------------------ Bytecode inspection

    private static void analyzeBytecodeClasses(Map<String, ClassModel> classes, String primaryModId, Result res) {
        // First map per-class inheritance & method implementations
        Map<String, String> itemKindByClass = new LinkedHashMap<>();
        Map<String, String> blockShapeByClass = new LinkedHashMap<>();
        Map<String, Boolean> fallingByClass = new LinkedHashMap<>();
        Map<String, String> entityKindByClass = new LinkedHashMap<>();

        for (ClassModel cm : classes.values()) {
            String sup = resolveSuperKind(cm, classes, 0);
            if (isItemSuper(sup)) itemKindByClass.put(cm.internalName, mapItemKindFromSuper(sup));
            if (isBlockSuper(sup)) {
                blockShapeByClass.put(cm.internalName, mapBlockShapeFromSuper(sup));
                if (sup.endsWith("FallingBlock") || sup.endsWith("BlockFalling") || sup.endsWith("class_2346")) fallingByClass.put(cm.internalName, true);
            }
            if (isEntitySuper(sup)) entityKindByClass.put(cm.internalName, mapEntityKindFromSuper(sup));
        }

        // Scan constructors, clinit, and registration methods for string keys ("my_sword") near properties/news
        for (ClassModel cm : classes.values()) {
            for (MethodModel mm : cm.methods) {
                if (mm.insns.isEmpty()) continue;
                scanRegistrationWindow(cm, mm, classes, primaryModId, itemKindByClass, blockShapeByClass, fallingByClass, entityKindByClass, res);
            }
        }

        // Also match concrete classes directly named after items/blocks/entities (e.g. FireWandItem or 1.12 ItemElementalSword -> elemental_sword)
        for (ClassModel cm : classes.values()) {
            String simple = cm.simpleName();
            if (simple.contains("$") || cm.isAbstractOrInterface()) continue;
            if (itemKindByClass.containsKey(cm.internalName) || simple.endsWith("Item") || (simple.startsWith("Item") && simple.length() > 4 && Character.isUpperCase(simple.charAt(4)))) {
                String explicit = extractExplicitRegistryName(cm);
                String guess = explicit != null ? explicit : guessRegistryName(simple, "Item");
                if (!guess.isEmpty() && !isGenericHelperClassName(guess)) {
                    ItemProps ip = res.items.computeIfAbsent(guess, k -> new ItemProps());
                    if (ip.customClass == null) ip.customClass = cm.internalName;
                    if (ip.kind == null && itemKindByClass.containsKey(cm.internalName)) {
                        ip.kind = itemKindByClass.get(cm.internalName);
                    }
                    extractClassItemProps(cm, classes, ip);
                    extractItemLogicMethods(cm, classes, ip, res);
                }
            } else if (blockShapeByClass.containsKey(cm.internalName) || simple.endsWith("Block") || (simple.startsWith("Block") && simple.length() > 5 && Character.isUpperCase(simple.charAt(5)))) {
                String explicit = extractExplicitRegistryName(cm);
                String guess = explicit != null ? explicit : guessRegistryName(simple, "Block");
                if (!guess.isEmpty() && !isGenericHelperClassName(guess)) {
                    BlockProps bp = res.blocks.computeIfAbsent(guess, k -> new BlockProps());
                    if (bp.customClass == null) bp.customClass = cm.internalName;
                    if (bp.shape == null && blockShapeByClass.containsKey(cm.internalName)) {
                        bp.shape = blockShapeByClass.get(cm.internalName);
                    }
                    if (fallingByClass.getOrDefault(cm.internalName, false)) bp.falling = true;
                    extractClassBlockProps(cm, classes, bp);
                    extractBlockLogicMethods(cm, classes, bp, res);
                }
            } else if (entityKindByClass.containsKey(cm.internalName) || simple.endsWith("Entity") || (simple.startsWith("Entity") && simple.length() > 6 && Character.isUpperCase(simple.charAt(6)))) {
                String guess = guessRegistryName(simple, "Entity");
                if (!guess.isEmpty() && !isGenericHelperClassName(guess) && !simple.startsWith("EntityAI")) {
                    EntityProps ep = res.entities.computeIfAbsent(guess, k -> new EntityProps());
                    if (ep.customClass == null) ep.customClass = cm.internalName;
                    if (ep.kind == null && entityKindByClass.containsKey(cm.internalName)) {
                        ep.kind = entityKindByClass.get(cm.internalName);
                    }
                    extractEntityClassDetails(cm, classes, ep, res);
                }
            }
        }

        // Second pass for explicitly linked customClass records found during registration scan
        for (ItemProps ip : res.items.values()) {
            if (ip.customClass != null) {
                ClassModel cm = classes.get(ip.customClass);
                if (cm != null) {
                    extractClassItemProps(cm, classes, ip);
                    extractItemLogicMethods(cm, classes, ip, res);
                }
            }
        }
        for (BlockProps bp : res.blocks.values()) {
            if (bp.customClass != null) {
                ClassModel cm = classes.get(bp.customClass);
                if (cm != null) {
                    extractClassBlockProps(cm, classes, bp);
                    extractBlockLogicMethods(cm, classes, bp, res);
                }
            }
        }
        for (EntityProps ep : res.entities.values()) {
            if (ep.customClass != null) {
                ClassModel cm = classes.get(ep.customClass);
                if (cm != null) extractEntityClassDetails(cm, classes, ep, res);
            }
        }
    }

    private static void scanRegistrationWindow(ClassModel cm, MethodModel mm, Map<String, ClassModel> classes,
                                               String primaryModId,
                                               Map<String, String> itemKindByClass, Map<String, String> blockShapeByClass,
                                               Map<String, Boolean> fallingByClass, Map<String, String> entityKindByClass,
                                               Result res) {
        List<Insn> insns = mm.insns;
        // Identify nearby lambdas linked to string constants in register(name, () -> ...)
        for (int i = 0; i < insns.size(); i++) {
            Insn in = insns.get(i);
            if (in.kind != InsnKind.PUSH_STR || !looksLikeRegistryId(in.strVal)) continue;
            // Skip vanilla or external namespace references ("minecraft:stone", "c:ingots")
            if (in.strVal.contains(":")) {
                String ns = Ids.ns(in.strVal);
                if (!ns.equals(primaryModId) && ("minecraft".equals(ns) || "c".equals(ns) || "forge".equals(ns) || "fabric".equals(ns) || "neoforge".equals(ns))) {
                    continue;
                }
            }
            // Skip namespace arg in (namespace, path) pairs like ResourceLocation.fromNamespaceAndPath("mod", "id")
            if (i + 1 < insns.size() && insns.get(i + 1).kind == InsnKind.PUSH_STR && looksLikeRegistryId(insns.get(i + 1).strVal)) {
                continue;
            }
            String id = Ids.toId(in.strVal.contains(":") ? Ids.path(in.strVal) : in.strVal);
            if (id.isEmpty()) continue;

            // Isolate the statement preceding `i` without crossing into an earlier call/string statement
            int preStart = i;
            for (int b = i - 1; b >= Math.max(0, i - 4); b--) {
                Insn prev = insns.get(b);
                if (prev.kind == InsnKind.PUSH_STR || prev.kind == InsnKind.CALL || prev.kind == InsnKind.INVOKEDYNAMIC) break;
                preStart = b;
            }
            // Inspect window [i, nextRegistrationOrEnd) up to 90 instructions ahead
            int limit = Math.min(insns.size(), i + 90);
            boolean sawAction = false;
            for (int k = i + 1; k < limit; k++) {
                Insn nk = insns.get(k);
                if (nk.kind == InsnKind.NEW || nk.kind == InsnKind.INVOKEDYNAMIC
                        || (nk.kind == InsnKind.CALL && !isResourceLocationHelper(nk.owner, nk.name))) {
                    sawAction = true;
                }
                if (nk.kind == InsnKind.PUSH_STR && looksLikeRegistryId(nk.strVal) && sawAction) {
                    limit = k;
                    break;
                }
            }
            List<Insn> win = new ArrayList<>(insns.subList(preStart, i));
            expandSliceInPlace(cm, insns.subList(i, limit), classes, win, 0);

            String detectedType = detectRegisteredCategory(win, mm.name, classes, itemKindByClass, blockShapeByClass, entityKindByClass);
            if ("item".equals(detectedType)) {
                ItemProps ip = res.items.computeIfAbsent(id, k -> new ItemProps());
                String newCls = findConstructedModClass(win, classes, itemKindByClass.keySet());
                if (newCls != null && ip.customClass == null) ip.customClass = newCls;
                String supKind = detectItemKindInWindow(win, id, classes, itemKindByClass);
                if (supKind != null && (ip.kind == null || "basic".equals(ip.kind))) ip.kind = supKind;
                applyItemPropertyCalls(win, ip);
            } else if ("block".equals(detectedType)) {
                BlockProps bp = res.blocks.computeIfAbsent(id, k -> new BlockProps());
                String newCls = findConstructedModClass(win, classes, blockShapeByClass.keySet());
                if (newCls != null && bp.customClass == null) bp.customClass = newCls;
                String supShape = detectBlockShapeInWindow(win, id, classes, blockShapeByClass);
                if (supShape != null && bp.shape == null) bp.shape = supShape;
                if (newCls != null && fallingByClass.getOrDefault(newCls, false)) bp.falling = true;
                applyBlockPropertyCalls(win, bp);
            } else if ("entity".equals(detectedType)) {
                EntityProps ep = res.entities.computeIfAbsent(id, k -> new EntityProps());
                String newCls = findConstructedModClass(win, classes, entityKindByClass.keySet());
                if (newCls != null && ep.customClass == null) ep.customClass = newCls;
                applyEntityBuilderCalls(win, ep);
            } else if ("armor".equals(detectedType)) {
                ArmorMatProps ap = res.armors.computeIfAbsent(id, k -> new ArmorMatProps());
                applyArmorMaterialCalls(win, ap);
            }
        }
    }

    private static void expandSliceInPlace(ClassModel cm, List<Insn> slice, Map<String, ClassModel> classes,
                                           List<Insn> out, int depth) {
        for (Insn in : slice) {
            if (in.kind == InsnKind.INVOKEDYNAMIC && depth < 3 && in.intVal >= 0 && in.intVal < cm.indyTargets.size()) {
                MethodRef mr = cm.indyTargets.get(in.intVal);
                if (mr != null && classes.containsKey(mr.owner)) {
                    ClassModel targetCm = classes.get(mr.owner);
                    for (MethodModel lm : targetCm.methods) {
                        if (lm.name.equals(mr.name) && lm.desc.equals(mr.desc)) {
                            expandSliceInPlace(targetCm, lm.insns, classes, out, depth + 1);
                            break;
                        }
                    }
                }
            }
            out.add(in);
        }
    }

    private static String detectRegisteredCategory(List<Insn> win, String enclosingMethod,
                                                   Map<String, ClassModel> classes,
                                                   Map<String, String> itemClasses,
                                                   Map<String, String> blockClasses,
                                                   Map<String, String> entityClasses) {
        for (Insn in : win) {
            if (in.kind == InsnKind.NEW || in.kind == InsnKind.CALL) {
                String owner = in.owner != null ? in.owner : "";
                if (owner.contains("BlockBehaviour$Properties") || owner.contains("FabricBlockSettings")
                        || blockClasses.containsKey(owner) || owner.endsWith("Block") || owner.contains("class_2248")) {
                    if (!owner.endsWith("BlockItem") && !owner.contains("class_1747")) return "block";
                }
                if (owner.contains("ArmorMaterial") || owner.contains("ArmorItem") || owner.contains("class_1738")
                        || (in.name != null && (in.name.contains("ARMOR_MATERIAL") || in.name.equals("armorMaterial")))) {
                    if (owner.contains("ArmorMaterial") && !owner.contains("Item")) return "armor";
                }
                if (owner.contains("EntityType$Builder") || entityClasses.containsKey(owner) || owner.contains("class_1299")) {
                    return "entity";
                }
                if (owner.contains("Item$Properties") || owner.contains("FabricItemSettings")
                        || itemClasses.containsKey(owner) || owner.endsWith("Item") || owner.contains("class_1792")) {
                    return "item";
                }
            }
        }
        String ml = enclosingMethod.toLowerCase(Locale.ROOT);
        if (ml.contains("block")) return "block";
        if (ml.contains("entity")) return "entity";
        if (ml.contains("armor")) return "armor";
        if (ml.contains("item")) return "item";
        return "";
    }

    private static String findConstructedModClass(List<Insn> win, Map<String, ClassModel> classes, Set<String> known) {
        for (Insn in : win) {
            if (in.kind == InsnKind.NEW && in.owner != null && classes.containsKey(in.owner)) {
                if (known.contains(in.owner) || !in.owner.contains("Properties")) return in.owner;
            }
        }
        return null;
    }

    private static String detectItemKindInWindow(List<Insn> win, String id, Map<String, ClassModel> classes, Map<String, String> itemClasses) {
        for (Insn in : win) {
            if (in.kind == InsnKind.NEW && in.owner != null) {
                if (itemClasses.containsKey(in.owner)) return itemClasses.get(in.owner);
                String k = mapItemKindFromSuper(in.owner);
                if (k != null && !"basic".equals(k)) return k;
            }
            if (in.kind == InsnKind.CALL && in.name != null && (in.name.equals("food") || in.name.equals("nutrition") || in.name.equals("method_19265"))) {
                return "food";
            }
            if (in.kind == InsnKind.GETSTATIC && in.name != null) {
                if (in.name.equals("HELMET")) return "helmet";
                if (in.name.equals("CHESTPLATE")) return "chestplate";
                if (in.name.equals("LEGGINGS")) return "leggings";
                if (in.name.equals("BOOTS")) return "boots";
            }
        }
        return null;
    }

    private static String detectBlockShapeInWindow(List<Insn> win, String id, Map<String, ClassModel> classes, Map<String, String> blockClasses) {
        for (Insn in : win) {
            if (in.kind == InsnKind.NEW && in.owner != null) {
                if (blockClasses.containsKey(in.owner)) return blockClasses.get(in.owner);
                String sh = mapBlockShapeFromSuper(in.owner);
                if (sh != null && !"cube".equals(sh)) return sh;
            }
        }
        return null;
    }

    private static void applyItemPropertyCalls(List<Insn> insns, ItemProps ip) {
        for (int i = 0; i < insns.size(); i++) {
            Insn in = insns.get(i);
            if (in.kind != InsnKind.CALL || in.name == null) continue;
            String mn = in.name;
            String ow = in.owner == null ? "" : in.owner;
            // SwordItem / PickaxeItem / etc constructor (Tier, float dmg, float spd, Props) or (Tier, int dmg, float spd, Props)
            if (mn.equals("<init>") && (isToolClass(ow) || isToolClass(shortName(ow)))) {
                String k = mapItemKindFromSuper(ow);
                if (k != null && (ip.kind == null || "basic".equals(ip.kind))) ip.kind = k;
                List<Number> nums = recentNumbers(insns, i, 8);
                if (nums.size() >= 2) {
                    ip.attackDamage = nums.get(nums.size() - 2).doubleValue();
                    ip.attackSpeed = nums.get(nums.size() - 1).doubleValue();
                }
                String tier = findRecentStatic(insns, i, 12, "Tiers", "ToolMaterial", "class_1834");
                if (tier != null) ip.tier = normalizeTier(tier);
            }
            if (mn.equals("<init>") && (ow.endsWith("ItemFood") || ow.endsWith("net/minecraft/item/ItemFood"))) {
                ip.kind = "food";
                List<Number> nums = recentNumbers(insns, i, 6);
                for (Number n : nums) {
                    if (n instanceof Integer iv && iv >= 1 && iv <= 20 && ip.nutrition == null) ip.nutrition = iv;
                    else if (n instanceof Float fv && fv > 0.0f && fv <= 4.0f && ip.saturation == null) ip.saturation = (double) fv;
                }
            }
            if (mn.equals("stacksTo") || mn.equals("maxCount") || mn.equals("m_41487_") || mn.equals("method_7889")
                    || mn.equals("setMaxStackSize") || mn.equals("func_77625_d")) {
                Integer n = lastInt(insns, i, 4);
                if (n != null && n >= 1 && n <= 99) ip.maxStack = n;
            } else if (mn.equals("durability") || mn.equals("maxDamage") || mn.equals("m_41503_") || mn.equals("method_7895")
                    || mn.equals("setMaxDamage") || mn.equals("func_77656_e")) {
                Integer n = lastInt(insns, i, 4);
                if (n != null && n > 0 && n <= 100000) ip.maxDamage = n;
            } else if (mn.equals("fireResistant") || mn.equals("fireproof") || mn.equals("m_41486_") || mn.equals("method_24359")) {
                ip.fireResistant = true;
            } else if (mn.equals("rarity") || mn.equals("m_41497_") || mn.equals("method_7894")) {
                String r = findRecentStatic(insns, i, 4, "Rarity", "EnumRarity", "class_1814");
                if (r != null) ip.rarity = r.toLowerCase(Locale.ROOT);
            } else if (mn.equals("craftRemainder") || mn.equals("recipeRemainder") || mn.equals("m_41495_") || mn.equals("method_7896")
                    || mn.equals("setContainerItem") || mn.equals("func_77642_a")) {
                String it = findRecentStatic(insns, i, 4, "Items", "class_1802");
                if (it != null) ip.craftRemainder = "minecraft:" + it.toLowerCase(Locale.ROOT);
            } else if (mn.equals("useCooldown")) {
                Float f = lastFloat(insns, i, 4);
                if (f != null && f > 0) ip.useCooldown = (double) f;
            } else if (mn.equals("enchantable")) {
                Integer n = lastInt(insns, i, 4);
                if (n != null && n >= 0) ip.enchantability = n;
            } else if (mn.equals("nutrition") || mn.equals("hunger") || mn.equals("m_38760_") || mn.equals("method_19238")) {
                Integer n = lastInt(insns, i, 4);
                if (n != null && n >= 0 && n <= 20) {
                    ip.kind = "food";
                    ip.nutrition = n;
                }
            } else if (mn.equals("saturationMod") || mn.equals("saturationModifier") || mn.equals("m_38758_") || mn.equals("method_19237")) {
                Float f = lastFloat(insns, i, 4);
                if (f != null && f >= 0) {
                    ip.kind = "food";
                    ip.saturation = (double) f;
                }
            } else if (mn.equals("alwaysEat") || mn.equals("alwaysEdible") || mn.equals("m_38765_") || mn.equals("method_19240")
                    || mn.equals("setAlwaysEdible") || mn.equals("func_77848_i")) {
                ip.kind = "food";
                ip.alwaysEdible = true;
            } else if (mn.equals("fast") || mn.equals("snack") || mn.equals("m_38766_") || mn.equals("method_19241")) {
                ip.kind = "food";
                ip.fastFood = true;
            } else if (((mn.equals("effect") || mn.equals("statusEffect")) && (ow.contains("Food") || ow.contains("class_4174")))
                    || (mn.equals("<init>") && ow.contains("ApplyStatusEffectsConsumeEffect"))
                    || (mn.equals("<init>") && ow.contains("MobEffectInstance") && "food".equals(ip.kind))
                    || mn.equals("setPotionEffect") || mn.equals("func_185070_a")) {
                String eff = findRecentStatic(insns, i, 16, "MobEffects", "StatusEffects", "class_1294");
                if (eff != null) {
                    if (ip.kind == null || "basic".equals(ip.kind)) ip.kind = "food";
                    ip.foodEffect = normalizeEffectId(eff);
                    List<Number> nums = recentNumbers(insns, i, 12);
                    for (Number n : nums) {
                        if (n instanceof Integer iv && iv >= 20) ip.foodEffectSeconds = Math.max(1, iv / 20);
                        else if (n instanceof Integer iv && iv >= 0 && iv <= 5 && ip.foodEffectAmplifier == null) ip.foodEffectAmplifier = iv;
                        else if (n instanceof Float fv && fv > 0.0f && fv <= 1.0f) ip.foodEffectChance = (double) fv;
                    }
                }
            } else if (mn.equals("addCooldown") || mn.equals("setCooldown") || mn.equals("func_185145_a")) {
                Integer ticks = lastInt(insns, i, 6);
                if (ticks != null && ticks > 0 && ticks <= 24000) ip.useCooldown = ticks / 20.0;
            }
        }
    }

    private static void applyBlockPropertyCalls(List<Insn> insns, BlockProps bp) {
        for (int i = 0; i < insns.size(); i++) {
            Insn in = insns.get(i);
            if (in.kind != InsnKind.CALL || in.name == null) continue;
            String mn = in.name;
            if (mn.equals("strength") || mn.equals("m_60913_") || mn.equals("m_60978_") || mn.equals("method_9629")) {
                List<Number> nums = recentNumbers(insns, i, 4);
                if (nums.size() >= 2 && in.desc != null && in.desc.startsWith("(FF")) {
                    bp.hardness = Math.max(-1, nums.get(nums.size() - 2).doubleValue());
                    bp.resistance = Math.max(0, nums.get(nums.size() - 1).doubleValue());
                } else if (!nums.isEmpty()) {
                    double v = Math.max(-1, nums.get(nums.size() - 1).doubleValue());
                    bp.hardness = v;
                    if (bp.resistance == null) bp.resistance = v;
                }
            } else if (mn.equals("setHardness") || mn.equals("func_149711_c")) {
                Float f = lastFloat(insns, i, 4);
                if (f != null) bp.hardness = Math.max(-1, (double) f);
            } else if (mn.equals("setResistance") || mn.equals("func_149752_b")) {
                Float f = lastFloat(insns, i, 4);
                if (f != null && f >= 0) bp.resistance = (double) f;
            } else if (mn.equals("setBlockUnbreakable") || mn.equals("func_149722_s")) {
                bp.hardness = -1.0;
            } else if (mn.equals("setLightLevel") || mn.equals("func_149715_a")) {
                Float f = lastFloat(insns, i, 4);
                if (f != null && f >= 0 && f <= 1.0f) bp.light = Math.min(15, Math.round(f * 15.0f));
            } else if (mn.equals("setHarvestLevel")) {
                bp.requiresTool = true;
            } else if (mn.equals("instabreak") || mn.equals("breakInstantly")) {
                bp.hardness = 0.0;
                bp.resistance = 0.0;
            } else if (mn.equals("requiresCorrectToolForDrops") || mn.equals("requiresTool") || mn.equals("m_60999_") || mn.equals("method_29292")) {
                bp.requiresTool = true;
            } else if (mn.equals("noCollission") || mn.equals("noCollision") || mn.equals("m_60910_") || mn.equals("method_9634")) {
                bp.hasCollision = false;
            } else if (mn.equals("friction") || mn.equals("slipperiness") || mn.equals("m_60915_")) {
                Float f = lastFloat(insns, i, 4);
                if (f != null && f > 0) bp.friction = (double) f;
            } else if (mn.equals("speedFactor") || mn.equals("velocityMultiplier") || mn.equals("m_60956_")) {
                Float f = lastFloat(insns, i, 4);
                if (f != null && f > 0) bp.speedFactor = (double) f;
            } else if (mn.equals("jumpFactor") || mn.equals("jumpVelocityMultiplier") || mn.equals("m_60967_")) {
                Float f = lastFloat(insns, i, 4);
                if (f != null && f > 0) bp.jumpFactor = (double) f;
            } else if (mn.equals("sound") || mn.equals("sounds") || mn.equals("m_60918_") || mn.equals("method_9626")
                    || mn.equals("setSoundType") || mn.equals("func_149672_a")) {
                String snd = findRecentStatic(insns, i, 4, "SoundType", "BlockSoundGroup", "class_2498");
                if (snd != null) bp.sound = normalizeSoundType(snd);
            } else if (mn.equals("instrument")) {
                String inst = findRecentStatic(insns, i, 4, "NoteBlockInstrument");
                if (inst != null) bp.instrument = inst.toLowerCase(Locale.ROOT);
            } else if (mn.equals("pushReaction")) {
                String pr = findRecentStatic(insns, i, 4, "PushReaction");
                if (pr != null) bp.pushReaction = pr.toLowerCase(Locale.ROOT);
            } else if (mn.equals("of") && in.owner != null && in.owner.contains("UniformInt")) {
                List<Number> nums = recentNumbers(insns, i, 4);
                if (nums.size() >= 2) {
                    bp.xpMin = Math.max(0, nums.get(nums.size() - 2).intValue());
                    bp.xpMax = Math.max(bp.xpMin, nums.get(nums.size() - 1).intValue());
                }
            } else if (mn.equals("lightLevel") || mn.equals("luminance") || mn.equals("m_60953_")) {
                Integer lv = lastInt(insns, i, 6);
                if (lv != null && lv >= 0 && lv <= 15) bp.light = lv;
            }
        }
    }

    private static void applyArmorMaterialCalls(List<Insn> insns, ArmorMatProps ap) {
        for (int i = 0; i < insns.size(); i++) {
            Insn in = insns.get(i);
            if (in.kind == InsnKind.CALL && in.name != null) {
                if (in.name.equals("put") || in.name.contains("newarray")) {
                    // Track helmet/chest/legs/boots ints in order if adjacent
                    String slot = findRecentStatic(insns, i, 5, "ArmorItem$Type", "ArmorType", "EquipmentSlot");
                    Integer def = lastInt(insns, i, 3);
                    if (slot != null && def != null && def >= 0 && def <= 30) {
                        switch (slot) {
                            case "HELMET", "HEAD" -> ap.helmet = def;
                            case "CHESTPLATE", "CHEST" -> ap.chestplate = def;
                            case "LEGGINGS", "LEGS" -> ap.leggings = def;
                            case "BOOTS", "FEET" -> ap.boots = def;
                        }
                    }
                }
                if (in.name.equals("create") || in.name.equals("<init>") || in.name.equals("register")) {
                    List<Number> nums = recentNumbers(insns, i, 12);
                    for (Number n : nums) {
                        if (n instanceof Integer iv && iv >= 5 && iv <= 200 && ap.durability < 0) {
                            ap.durability = iv;
                        } else if (n instanceof Float fv && fv > 0 && fv <= 1.0f && ap.knockbackResistance < 0) {
                            ap.knockbackResistance = (double) fv;
                        } else if (n instanceof Float fv && fv > 1.0f && fv <= 10.0f && ap.toughness < 0) {
                            ap.toughness = (double) fv;
                        }
                    }
                }
            }
            if (in.kind == InsnKind.GETSTATIC && in.name != null && in.owner != null && in.owner.contains("SoundEvents")) {
                String n = in.name.toLowerCase(Locale.ROOT);
                if (n.contains("armor_equip_")) {
                    ap.equipSound = n.substring(n.indexOf("armor_equip_") + 12);
                }
            }
        }
    }

    private static void applyEntityBuilderCalls(List<Insn> insns, EntityProps ep) {
        for (int i = 0; i < insns.size(); i++) {
            Insn in = insns.get(i);
            if (in.kind == InsnKind.GETSTATIC && in.owner != null && in.owner.contains("MobCategory") && in.name != null) {
                ep.category = in.name.toLowerCase(Locale.ROOT);
            }
            if (in.kind == InsnKind.CALL && in.name != null) {
                if (in.name.equals("sized") || in.name.equals("dimensions") || in.name.equals("m_20699_")) {
                    List<Number> nums = recentNumbers(insns, i, 4);
                    if (nums.size() >= 2) {
                        ep.width = Math.max(0.2, nums.get(nums.size() - 2).doubleValue());
                        ep.height = Math.max(0.2, nums.get(nums.size() - 1).doubleValue());
                    }
                } else if (in.name.equals("fireImmune")) {
                    ep.fireImmune = true;
                }
            }
        }
    }

    private static void extractClassItemProps(ClassModel cm, Map<String, ClassModel> classes, ItemProps ip) {
        for (MethodModel mm : cm.methods) {
            if ("<init>".equals(mm.name) || "<clinit>".equals(mm.name)) {
                List<Insn> expanded = new ArrayList<>();
                expandSliceInPlace(cm, mm.insns, classes, expanded, 0);
                applyItemPropertyCalls(expanded, ip);
            } else if ("isFoil".equals(mm.name) || "hasGlint".equals(mm.name) || "m_5812_".equals(mm.name)
                    || "hasEffect".equals(mm.name) || "func_77636_d".equals(mm.name)) {
                Integer rv = lastInt(mm.insns, mm.insns.size(), 3);
                if (rv != null && rv == 1) ip.glint = true;
            } else if ("getRarity".equals(mm.name) || "func_77613_e".equals(mm.name)) {
                String r = findRecentStatic(mm.insns, mm.insns.size(), 8, "Rarity", "EnumRarity", "class_1814");
                if (r != null) ip.rarity = r.toLowerCase(Locale.ROOT);
            }
        }
    }

    private static void extractClassBlockProps(ClassModel cm, Map<String, ClassModel> classes, BlockProps bp) {
        for (MethodModel mm : cm.methods) {
            if ("<init>".equals(mm.name) || "<clinit>".equals(mm.name)) {
                List<Insn> expanded = new ArrayList<>();
                expandSliceInPlace(cm, mm.insns, classes, expanded, 0);
                applyBlockPropertyCalls(expanded, bp);
            } else if (mm.name.contains("lightLevel")) {
                Integer lv = lastInt(mm.insns, mm.insns.size(), 6);
                if (lv != null && lv >= 0 && lv <= 15) bp.light = lv;
            }
        }
    }

    private static void extractItemLogicMethods(ClassModel cm, Map<String, ClassModel> classes, ItemProps ip, Result res) {
        for (MethodModel mm : cm.methods) {
            String n = mm.name;
            String d = mm.desc == null ? "" : mm.desc;
            boolean isUse = n.equals("use") || n.equals("m_7203_") || n.equals("method_7836")
                    || n.equals("useOn") || n.equals("m_6225_") || n.equals("method_7884")
                    || n.equals("onItemRightClick") || n.equals("func_77659_a")
                    || n.equals("onItemUse") || n.equals("func_180614_a")
                    || n.equals("onUsingTick")
                    || d.contains(";Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)")
                    || d.contains(";Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/EnumHand;)");
            boolean isFinish = n.equals("finishUsingItem") || n.equals("m_5922_") || n.equals("method_7861")
                    || n.equals("onItemUseFinish") || n.equals("func_77654_b") || n.equals("onPlayerStoppedUsing") || n.equals("func_77615_a");
            boolean isHit = n.equals("hurtEnemy") || n.equals("m_7579_") || n.equals("method_7873")
                    || n.equals("hitEntity") || n.equals("func_77644_a")
                    || d.contains("ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z")
                    || d.contains("ItemStack;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/entity/EntityLivingBase;)Z");
            boolean isTick = n.equals("inventoryTick") || n.equals("m_6883_") || n.equals("method_7888")
                    || n.equals("onUpdate") || n.equals("func_77663_a");

            if (isUse || isFinish) {
                applyItemPropertyCalls(mm.insns, ip);
                List<Step> steps = extractSteps(mm, classes, false, 0);
                if (!steps.isEmpty()) {
                    ip.onUse.addAll(steps);
                    res.extractedHooks += steps.size();
                }
            } else if (isHit) {
                List<Step> steps = extractSteps(mm, classes, true, 0);
                if (!steps.isEmpty()) {
                    ip.onHitEntity.addAll(steps);
                    res.extractedHooks += steps.size();
                }
            } else if (isTick) {
                List<Step> steps = extractSteps(mm, classes, false, 0);
                if (!steps.isEmpty()) {
                    ip.onTickInHand.addAll(steps);
                    res.extractedHooks += steps.size();
                }
            }
        }
    }

    private static void extractBlockLogicMethods(ClassModel cm, Map<String, ClassModel> classes, BlockProps bp, Result res) {
        for (MethodModel mm : cm.methods) {
            String n = mm.name;
            boolean isUse = n.equals("use") || n.equals("useWithoutItem") || n.equals("useItemOn")
                    || n.equals("m_6227_") || n.equals("m_51273_") || n.equals("method_9534")
                    || n.equals("onBlockActivated") || n.equals("func_180639_a");
            boolean isStep = n.equals("stepOn") || n.equals("entityInside") || n.equals("m_141947_") || n.equals("m_7892_") || n.equals("method_9591")
                    || n.equals("onEntityWalk") || n.equals("func_176199_a") || n.equals("onEntityCollidedWithBlock") || n.equals("func_180634_a");
            boolean isTick = n.equals("randomTick") || n.equals("tick") || n.equals("m_213898_") || n.equals("method_9514")
                    || n.equals("updateTick") || n.equals("func_180650_b") || n.equals("randomTick") || n.equals("func_180645_a");
            if (isUse) {
                List<Step> steps = extractSteps(mm, classes, false, 0);
                if (!steps.isEmpty()) { bp.onUse.addAll(steps); res.extractedHooks += steps.size(); }
            } else if (isStep) {
                List<Step> steps = extractSteps(mm, classes, false, 0);
                if (!steps.isEmpty()) { bp.onStep.addAll(steps); res.extractedHooks += steps.size(); }
            } else if (isTick) {
                List<Step> steps = extractSteps(mm, classes, false, 0);
                if (!steps.isEmpty()) { bp.onRandomTick.addAll(steps); res.extractedHooks += steps.size(); }
            }
        }
    }

    private static void extractEntityClassDetails(ClassModel cm, Map<String, ClassModel> classes, EntityProps ep, Result res) {
        for (MethodModel mm : cm.methods) {
            String n = mm.name;
            if ("<init>".equals(n)) {
                for (int idx = 0; idx < mm.insns.size(); idx++) {
                    Insn in = mm.insns.get(idx);
                    if (in.kind == InsnKind.NEW && in.owner != null && (in.owner.contains("BossEvent") || in.owner.contains("BossInfo"))) ep.boss = true;
                    if (in.kind == InsnKind.GETSTATIC && in.owner != null && (in.owner.contains("BossBarColor") || in.owner.contains("BossInfo$Color")) && in.name != null) {
                        ep.bossColor = in.name.toLowerCase(Locale.ROOT);
                    }
                    if (in.kind == InsnKind.PUTFIELD && in.name != null && (in.name.equals("xpReward") || in.name.equals("field_70728_aV"))) {
                        Integer xp = lastInt(mm.insns, idx, 3);
                        if (xp != null && xp >= 0) ep.xpReward = xp;
                    }
                    if (in.kind == InsnKind.PUTFIELD && in.name != null && in.name.equals("field_70178_ae")) {
                        Integer imm = lastInt(mm.insns, idx, 3);
                        if (imm != null && imm == 1) ep.fireImmune = true;
                    }
                    if (in.kind == InsnKind.CALL && in.name != null && (in.name.equals("setSize") || in.name.equals("func_70105_a"))) {
                        List<Number> nums = recentNumbers(mm.insns, idx, 4);
                        if (nums.size() >= 2) {
                            ep.width = Math.max(0.2, Math.min(8.0, nums.get(nums.size() - 2).doubleValue()));
                            ep.height = Math.max(0.2, Math.min(8.0, nums.get(nums.size() - 1).doubleValue()));
                        }
                    }
                }
            }
            if (n.equals("createAttributes") || n.equals("registerAttributes") || n.equals("createMobAttributes") || n.contains("Attributes")
                    || n.equals("applyEntityAttributes") || n.equals("func_110147_ax")) {
                List<Insn> insns = mm.insns;
                for (int i = 0; i < insns.size(); i++) {
                    Insn in = insns.get(i);
                    if (in.kind == InsnKind.GETSTATIC && in.owner != null && (in.owner.contains("Attributes") || in.owner.contains("class_5134")) && in.name != null) {
                        Double val = firstDoubleAfter(insns, i, 6);
                        if (val == null || val <= 0) continue;
                        switch (in.name.toUpperCase(Locale.ROOT)) {
                            case "MAX_HEALTH", "GENERIC_MAX_HEALTH", "FIELD_111267_A" -> ep.health = Math.min(2048.0, val);
                            case "MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED", "FIELD_111263_D" -> ep.speed = Math.min(2.0, val);
                            case "ATTACK_DAMAGE", "GENERIC_ATTACK_DAMAGE", "FIELD_111264_E" -> ep.attackDamage = Math.min(500.0, val);
                            case "FOLLOW_RANGE", "GENERIC_FOLLOW_RANGE", "FIELD_111265_B" -> ep.followRange = Math.min(128.0, val);
                            case "ARMOR", "GENERIC_ARMOR", "FIELD_188791_G" -> ep.armor = Math.min(30.0, val);
                            case "KNOCKBACK_RESISTANCE", "GENERIC_KNOCKBACK_RESISTANCE", "FIELD_111266_C" -> ep.knockbackResistance = Math.min(1.0, val);
                        }
                    }
                }
            } else if (n.equals("registerGoals") || n.equals("initGoals") || n.equals("m_8099_") || n.equals("method_5959")
                    || n.equals("initEntityAI") || n.equals("func_184651_r")) {
                for (Insn in : mm.insns) {
                    if (in.kind == InsnKind.NEW && in.owner != null) {
                        String g = shortName(in.owner);
                        if (g.contains("FloatGoal") || g.contains("SwimGoal") || g.contains("EntityAISwimming")) addGoal(ep.aiGoals, "float");
                        else if (g.contains("MeleeAttack") || g.contains("EntityAIAttackMelee")) addGoal(ep.aiGoals, "melee");
                        else if (g.contains("PanicGoal") || g.contains("EscapeDangerGoal") || g.contains("EntityAIPanic")) addGoal(ep.aiGoals, "panic");
                        else if (g.contains("StrollGoal") || g.contains("WanderAround") || g.contains("EntityAIWander")) addGoal(ep.aiGoals, "stroll");
                        else if (g.contains("LookAtPlayer") || g.contains("LookAtEntityGoal") || g.contains("EntityAIWatchClosest")) addGoal(ep.aiGoals, "look_player");
                        else if (g.contains("RandomLookAround") || g.contains("LookAroundGoal") || g.contains("EntityAILookIdle")) addGoal(ep.aiGoals, "random_look");
                        else if (g.contains("NearestAttackableTarget") || g.contains("ActiveTargetGoal")) addGoal(ep.aiGoals, "attack_nearest_player");
                        else if (g.contains("RandomSwimmingGoal")) addGoal(ep.aiGoals, "swim_random");
                    }
                }
            } else if (n.equals("hurt") || n.equals("m_6469_") || n.equals("method_5643") || n.equals("attackEntityFrom") || n.equals("func_70097_a")) {
                List<Step> st = extractSteps(mm, classes, false, 0);
                if (!st.isEmpty()) { ep.onHurt.addAll(st); res.extractedHooks += st.size(); }
            } else if (n.equals("die") || n.equals("m_6667_") || n.equals("method_6078") || n.equals("onDeath") || n.equals("func_70645_a")) {
                List<Step> st = extractSteps(mm, classes, false, 0);
                if (!st.isEmpty()) { ep.onDeath.addAll(st); res.extractedHooks += st.size(); }
            } else if (n.equals("mobInteract") || n.equals("interactMob") || n.equals("m_6071_") || n.equals("processInteract") || n.equals("func_184645_a")) {
                List<Step> st = extractSteps(mm, classes, false, 0);
                if (!st.isEmpty()) { ep.onInteract.addAll(st); res.extractedHooks += st.size(); }
            }
        }
    }

    /** Extracts executable ModStudio {@link Step} actions from bytecode instruction stream (following up to 2 helper hops). */
    private static List<Step> extractSteps(MethodModel mm, Map<String, ClassModel> classes, boolean targetIsOpponent, int depth) {
        List<Step> steps = new ArrayList<>();
        List<Insn> insns = new ArrayList<>(mm.insns);
        if (depth < 2) {
            for (Insn in : mm.insns) {
                if (in.kind == InsnKind.CALL && in.owner != null && classes.containsKey(in.owner)) {
                    ClassModel helper = classes.get(in.owner);
                    for (MethodModel hm : helper.methods) {
                        if (hm.name.equals(in.name) && (in.desc == null || in.desc.equals(hm.desc))) {
                            steps.addAll(extractSteps(hm, classes, targetIsOpponent, depth + 1));
                        }
                    }
                }
            }
        }
        String targetSel = targetIsOpponent ? "@e[type=!minecraft:player,limit=1,sort=nearest,distance=..5]" : "@s";
        Set<String> dedup = new LinkedHashSet<>();

        for (int i = 0; i < insns.size(); i++) {
            Insn in = insns.get(i);
            if (in.kind != InsnKind.CALL || in.name == null) continue;
            String mn = in.name;
            String ow = in.owner == null ? "" : in.owner;

            if (mn.equals("addEffect") || mn.equals("addStatusEffect") || mn.equals("m_7292_") || mn.equals("method_6092")
                    || mn.equals("addPotionEffect") || mn.equals("func_70690_d")) {
                String eff = findRecentStatic(insns, i, 14, "MobEffects", "StatusEffects", "class_1294");
                String effId = eff != null ? normalizeEffectId(eff) : "minecraft:speed";
                List<Number> nums = recentNumbers(insns, i, 10);
                int ticks = 200;
                int amp = 0;
                for (Number n : nums) {
                    if (n instanceof Integer iv) {
                        if (iv >= 20) ticks = iv;
                        else if (iv >= 0 && iv <= 10) amp = iv;
                    }
                }
                int secs = Math.max(1, ticks / 20);
                String sig = "effect:" + effId + ":" + secs + ":" + amp;
                if (dedup.add(sig)) {
                    Step s = Logic.newStep("effect");
                    s.p.put("target", targetSel);
                    s.p.put("effect", effId);
                    s.p.put("seconds", String.valueOf(secs));
                    s.p.put("amplifier", String.valueOf(amp));
                    steps.add(s);
                }
            } else if (mn.equals("heal") || mn.equals("m_5634_") || mn.equals("method_6025") || mn.equals("func_70691_i")) {
                Float amt = lastFloat(insns, i, 5);
                int val = amt == null ? 4 : Math.max(1, Math.round(amt));
                if (dedup.add("heal:" + val)) {
                    Step s = Logic.newStep("heal");
                    s.p.put("target", "@s");
                    s.p.put("amount", String.valueOf(val));
                    steps.add(s);
                }
            } else if (mn.equals("hurt") || mn.equals("damage") || mn.equals("m_6469_") || mn.equals("method_5643")
                    || mn.equals("attackEntityFrom") || mn.equals("func_70097_a")) {
                Float amt = lastFloat(insns, i, 5);
                double val = amt == null ? 4.0 : Math.max(0.5, Math.round(amt * 10.0) / 10.0);
                if (dedup.add("damage:" + val)) {
                    Step s = Logic.newStep("damage");
                    s.p.put("target", targetSel);
                    s.p.put("amount", String.valueOf(val));
                    steps.add(s);
                }
            } else if (mn.equals("explode") || mn.equals("createExplosion") || mn.equals("newExplosion")
                    || mn.equals("m_46511_") || mn.equals("m_254849_") || mn.equals("method_8437")
                    || mn.equals("func_72876_a") || mn.equals("func_72885_a")) {
                Float pow = lastFloat(insns, i, 6);
                int pVal = pow == null ? 3 : Math.max(1, Math.min(12, Math.round(pow)));
                if (dedup.add("explosion:" + pVal)) {
                    Step s = Logic.newStep("explosion");
                    s.p.put("power", String.valueOf(pVal));
                    steps.add(s);
                }
            } else if (mn.equals("igniteForSeconds") || mn.equals("setSecondsOnFire") || mn.equals("setRemainingFireTicks")
                    || mn.equals("m_20254_") || mn.equals("setFire") || mn.equals("func_70015_d")) {
                Integer sec = lastInt(insns, i, 5);
                if (sec == null || sec > 0) {
                    int secs = (sec != null && sec > 0) ? (mn.contains("Ticks") ? Math.max(1, sec / 20) : sec) : 5;
                    if (dedup.add("ignite:" + secs)) {
                        Step s = Logic.newStep("command");
                        s.p.put("command", "execute as " + targetSel + " run data modify entity @s Fire set value " + (secs * 20) + "s");
                        steps.add(s);
                    }
                }
            } else if (mn.equals("playSound") || mn.equals("m_6263_") || mn.equals("m_5594_") || mn.equals("method_8396")
                    || mn.equals("func_184148_a") || mn.equals("func_184133_a") || mn.equals("func_184185_a")) {
                String snd = findRecentStatic(insns, i, 12, "SoundEvents", "class_3417");
                String sndId = snd != null && !snd.startsWith("field_") ? normalizeSoundEventId(snd) : "minecraft:entity.experience_orb.pickup";
                if (dedup.add("sound:" + sndId)) {
                    Step s = Logic.newStep("sound");
                    s.p.put("sound", sndId);
                    s.p.put("target", "@s");
                    steps.add(s);
                }
            } else if (mn.equals("sendParticles") || mn.equals("addParticle") || mn.equals("spawnParticles")
                    || mn.equals("spawnParticle") || mn.equals("func_175688_a")) {
                String pt = findRecentStatic(insns, i, 14, "ParticleTypes", "EnumParticleTypes", "class_2398");
                String ptId = pt != null && !pt.startsWith("field_") ? "minecraft:" + pt.toLowerCase(Locale.ROOT) : "minecraft:enchant";
                if (dedup.add("particle:" + ptId)) {
                    Step s = Logic.newStep("particle");
                    s.p.put("particle", ptId);
                    s.p.put("count", "12");
                    steps.add(s);
                }
            } else if (mn.equals("displayClientMessage") || mn.equals("sendSystemMessage") || mn.equals("m_5661_")
                    || mn.equals("sendMessage") || mn.equals("sendStatusMessage") || mn.equals("func_145747_a") || mn.equals("func_146105_b")) {
                String msg = findRecentString(insns, i, 10);
                if (msg != null && !msg.isBlank() && !msg.contains(".") && dedup.add("message:" + msg)) {
                    Step s = Logic.newStep("message");
                    s.p.put("target", "@s");
                    s.p.put("text", msg);
                    steps.add(s);
                }
            } else if (mn.equals("teleportTo") || mn.equals("randomTeleport") || mn.equals("m_6021_")
                    || mn.equals("setPositionAndUpdate") || mn.equals("func_70634_a") || mn.equals("attemptTeleport") || mn.equals("func_184595_k")) {
                if (dedup.add("tp")) {
                    Step s = Logic.newStep(mn.equals("randomTeleport") || mn.equals("attemptTeleport") || mn.equals("func_184595_k") ? "teleport_random" : "teleport_forward");
                    s.p.put("target", "@s");
                    steps.add(s);
                }
            } else if (mn.equals("push") || mn.equals("setDeltaMovement") || mn.equals("knockback")
                    || mn.equals("addVelocity") || mn.equals("func_70024_g")) {
                if (dedup.add("push")) {
                    Step s = Logic.newStep(targetIsOpponent ? "push_away" : "push");
                    if (!targetIsOpponent) {
                        s.p.put("target", "@s");
                        s.p.put("dy", "0.8");
                    }
                    steps.add(s);
                }
            } else if (mn.equals("giveExperienceLevels") || mn.equals("giveExperiencePoints")
                    || mn.equals("addExperience") || mn.equals("addExperienceLevel") || mn.equals("func_71023_q") || mn.equals("func_82242_a")) {
                Integer xp = lastInt(insns, i, 4);
                int val = xp == null ? 5 : xp;
                if (dedup.add("xp:" + val)) {
                    Step s = Logic.newStep("xp");
                    s.p.put("target", "@s");
                    s.p.put("amount", String.valueOf(val));
                    s.p.put("unit", (mn.contains("Points") || mn.equals("addExperience") || mn.equals("func_71023_q")) ? "points" : "levels");
                    steps.add(s);
                }
            } else if (mn.equals("addFreshEntity") || mn.equals("spawnEntity") || mn.equals("func_72838_d")) {
                String ent = findRecentNewOrStatic(insns, i, 14, List.of("EntityType", "class_1299", "LightningBolt", "EntityLightningBolt",
                        "Arrow", "EntityArrow", "EntityTippedArrow", "SmallFireball", "LargeFireball", "EntitySmallFireball", "EntityLargeFireball",
                        "Snowball", "EntitySnowball", "ThrownEnderpearl", "EntityEnderPearl", "WitherSkull", "EntityWitherSkull"));
                if (ent != null) {
                    if (ent.contains("Lightning")) {
                        if (dedup.add("lightning")) steps.add(Logic.newStep("lightning"));
                    } else if (ent.contains("Arrow") || ent.contains("Fireball") || ent.contains("Snowball") || ent.contains("Enderpearl") || ent.contains("EnderPearl") || ent.contains("Skull")) {
                        String projId = ent.contains("LargeFireball") ? "minecraft:fireball"
                                : ent.contains("Fireball") ? "minecraft:small_fireball"
                                : ent.contains("Snowball") ? "minecraft:snowball"
                                : (ent.contains("Enderpearl") || ent.contains("EnderPearl")) ? "minecraft:ender_pearl"
                                : ent.contains("Skull") ? "minecraft:wither_skull"
                                : "minecraft:arrow";
                        if (dedup.add("shoot:" + projId)) {
                            Step s = Logic.newStep("shoot");
                            s.p.put("entity", projId);
                            s.p.put("speed", "1.6");
                            steps.add(s);
                        }
                    } else if (ent.equals(ent.toUpperCase(Locale.ROOT))) {
                        String mobId = "minecraft:" + ent.toLowerCase(Locale.ROOT);
                        if (dedup.add("summon:" + mobId)) {
                            Step s = Logic.newStep("summon");
                            s.p.put("entity", mobId);
                            steps.add(s);
                        }
                    }
                }
            } else if (mn.equals("setBlockAndUpdate") || mn.equals("setBlock") || mn.equals("setBlockState") || mn.equals("func_180501_a")) {
                String blk = findRecentStatic(insns, i, 8, "Blocks", "class_2246");
                if (blk != null && !blk.startsWith("field_")) {
                    String bId = "minecraft:" + blk.toLowerCase(Locale.ROOT);
                    if (dedup.add("setblock:" + bId)) {
                        Step s = Logic.newStep("setblock");
                        s.p.put("block", bId);
                        steps.add(s);
                    }
                }
            }
        }
        return steps;
    }

    // ------------------------------------------------------------------ Java/Kotlin Source Analysis

    private static final Pattern REG_CALL = Pattern.compile(
            "\"([a-z0-9_]{2,64})\"\\s*,\\s*\\(\\)\\s*->\\s*new\\s+([A-Za-z0-9_$.]+)");
    private static final Pattern METHOD_DEF = Pattern.compile(
            "(?:public|protected)\\s+(?:InteractionResult|InteractionResultHolder<[^>]+>|ItemStack|boolean|void)\\s+(use|useOn|hurtEnemy|finishUsingItem|inventoryTick|stepOn|useWithoutItem|randomTick|die|hurt|mobInteract)\\s*\\(");

    private static void analyzeTextSources(Map<String, String> textSources, String modId, Result res) {
        for (var en : textSources.entrySet()) {
            String path = en.getKey();
            String src = en.getValue();
            if (src == null || src.isBlank()) continue;
            String low = path.toLowerCase(Locale.ROOT);
            if (!low.endsWith(".java") && !low.endsWith(".kt")) continue;

            // Match inline registrations like register("fire_staff", () -> new FireStaffItem(...))
            Matcher m = REG_CALL.matcher(src);
            while (m.find()) {
                String regId = Ids.toId(m.group(1));
                String clsSimple = shortName(m.group(2).replace('.', '/'));
                int end = Math.min(src.length(), m.end() + 450);
                String snip = src.substring(m.start(), end);
                if (clsSimple.contains("Block") && !clsSimple.contains("BlockItem")) {
                    BlockProps bp = res.blocks.computeIfAbsent(regId, k -> new BlockProps());
                    extractBlockPropsFromSource(snip, bp);
                    String sh = mapBlockShapeFromSuper(clsSimple);
                    if (sh != null && bp.shape == null) bp.shape = sh;
                } else if (clsSimple.contains("Item") || snip.contains("Item.Properties") || snip.contains("FoodProperties")) {
                    ItemProps ip = res.items.computeIfAbsent(regId, k -> new ItemProps());
                    extractItemPropsFromSource(snip, ip);
                    String k = mapItemKindFromSuper(clsSimple);
                    if (k != null && (ip.kind == null || "basic".equals(ip.kind))) ip.kind = k;
                }
            }

            // Detect per-class item/block/entity files (handle multiple top-level or nested classes)
            Matcher clsMat = Pattern.compile("(?:class|object)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(?:extends|:)\\s*([A-Za-z0-9_$.<>]+)").matcher(src);
            while (clsMat.find()) {
                String clsName = clsMat.group(1);
                String supName = clsMat.group(2);
                if (supName.contains("Item") || clsName.endsWith("Item")) {
                    String id = guessRegistryName(clsName, "Item");
                    if (!id.isEmpty()) {
                        ItemProps ip = res.items.computeIfAbsent(id, k -> new ItemProps());
                        extractItemPropsFromSource(src, ip);
                        String k = mapItemKindFromSuper(supName);
                        if (k != null && (ip.kind == null || "basic".equals(ip.kind))) ip.kind = k;
                        extractMethodStepsFromSource(src, true, ip, null, null, res);
                    }
                } else if (supName.contains("Block") || clsName.endsWith("Block")) {
                    String id = guessRegistryName(clsName, "Block");
                    if (!id.isEmpty()) {
                        BlockProps bp = res.blocks.computeIfAbsent(id, k -> new BlockProps());
                        extractBlockPropsFromSource(src, bp);
                        if (supName.contains("FallingBlock")) bp.falling = true;
                        extractMethodStepsFromSource(src, false, null, bp, null, res);
                    }
                } else if (supName.contains("Monster") || supName.contains("Animal") || supName.contains("Mob") || clsName.endsWith("Entity")) {
                    String id = guessRegistryName(clsName, "Entity");
                    if (!id.isEmpty()) {
                        EntityProps ep = res.entities.computeIfAbsent(id, k -> new EntityProps());
                        extractEntityFromSource(src, ep);
                        extractMethodStepsFromSource(src, false, null, null, ep, res);
                    }
                }
            }
        }
    }

    private static void extractItemPropsFromSource(String s, ItemProps ip) {
        if (s.contains("fireResistant()") || s.contains("fireproof()")) ip.fireResistant = true;
        Matcher st = Pattern.compile("stacksTo\\(\\s*(\\d+)\\s*\\)").matcher(s);
        if (st.find()) ip.maxStack = Integer.parseInt(st.group(1));
        Matcher dur = Pattern.compile("durability\\(\\s*(\\d+)\\s*\\)").matcher(s);
        if (dur.find()) ip.maxDamage = Integer.parseInt(dur.group(1));
        Matcher nut = Pattern.compile("nutrition\\(\\s*(\\d+)\\s*\\)").matcher(s);
        if (nut.find()) { ip.kind = "food"; ip.nutrition = Integer.parseInt(nut.group(1)); }
        Matcher sat = Pattern.compile("saturationMod(?:ifier)?\\(\\s*([0-9]+(?:\\.[0-9]+)?)[fFdD]?\\s*\\)").matcher(s);
        if (sat.find()) { ip.kind = "food"; ip.saturation = parseDoubleSafe(sat.group(1), 0.6); }
        if (s.contains("alwaysEdible()") || s.contains("alwaysEat()")) { ip.kind = "food"; ip.alwaysEdible = true; }
        Matcher cd = Pattern.compile("addCooldown\\([^,]+,\\s*(\\d+)\\s*\\)").matcher(s);
        if (cd.find()) ip.useCooldown = parseIntSafe(cd.group(1), 20) / 20.0;
    }

    private static void extractBlockPropsFromSource(String s, BlockProps bp) {
        Matcher str2 = Pattern.compile("strength\\(\\s*([0-9]+(?:\\.[0-9]+)?)[fFdD]?\\s*,\\s*([0-9]+(?:\\.[0-9]+)?)[fFdD]?\\s*\\)").matcher(s);
        if (str2.find()) {
            bp.hardness = parseDoubleSafe(str2.group(1), 1.5);
            bp.resistance = parseDoubleSafe(str2.group(2), 6.0);
        } else {
            Matcher str1 = Pattern.compile("strength\\(\\s*([0-9]+(?:\\.[0-9]+)?)[fFdD]?\\s*\\)").matcher(s);
            if (str1.find()) bp.hardness = bp.resistance = parseDoubleSafe(str1.group(1), 1.5);
        }
        if (s.contains("requiresCorrectToolForDrops()")) bp.requiresTool = true;
        if (s.contains("noCollission()") || s.contains("noCollision()")) bp.hasCollision = false;
        Matcher ll = Pattern.compile("lightLevel\\([^->]*->\\s*(\\d+)\\s*\\)").matcher(s);
        if (ll.find()) bp.light = Math.min(15, parseIntSafe(ll.group(1), 0));
        Matcher xp = Pattern.compile("UniformInt\\.of\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)").matcher(s);
        if (xp.find()) {
            bp.xpMin = parseIntSafe(xp.group(1), 0);
            bp.xpMax = parseIntSafe(xp.group(2), 0);
        }
    }

    private static void extractEntityFromSource(String s, EntityProps ep) {
        Matcher hp = Pattern.compile("MAX_HEALTH\\s*,\\s*([0-9]+(?:\\.[0-9]+)?)[fFdD]?").matcher(s);
        if (hp.find()) ep.health = parseDoubleSafe(hp.group(1), 20.0);
        Matcher sp = Pattern.compile("MOVEMENT_SPEED\\s*,\\s*([0-9]+(?:\\.[0-9]+)?)[fFdD]?").matcher(s);
        if (sp.find()) ep.speed = parseDoubleSafe(sp.group(1), 0.25);
        Matcher ad = Pattern.compile("ATTACK_DAMAGE\\s*,\\s*([0-9]+(?:\\.[0-9]+)?)[fFdD]?").matcher(s);
        if (ad.find()) ep.attackDamage = parseDoubleSafe(ad.group(1), 4.0);
        if (s.contains("FloatGoal")) addGoal(ep.aiGoals, "float");
        if (s.contains("MeleeAttackGoal")) addGoal(ep.aiGoals, "melee");
        if (s.contains("PanicGoal")) addGoal(ep.aiGoals, "panic");
        if (s.contains("StrollGoal")) addGoal(ep.aiGoals, "stroll");
        if (s.contains("LookAtPlayerGoal")) addGoal(ep.aiGoals, "look_player");
        if (s.contains("NearestAttackableTargetGoal")) addGoal(ep.aiGoals, "attack_nearest_player");
    }

    private static void extractMethodStepsFromSource(String src, boolean isItem, ItemProps ip, BlockProps bp, EntityProps ep, Result res) {
        Matcher mm = METHOD_DEF.matcher(src);
        while (mm.find()) {
            String method = mm.group(1);
            int brace = src.indexOf('{', mm.end());
            if (brace < 0) continue;
            int close = findMatchingBrace(src, brace);
            String body = src.substring(brace, Math.min(src.length(), close));
            boolean opponent = method.equals("hurtEnemy");
            String tgt = opponent ? "@e[type=!minecraft:player,limit=1,sort=nearest,distance=..5]" : "@s";
            List<Step> out = new ArrayList<>();

            Matcher ef = Pattern.compile("new\\s+MobEffectInstance\\(\\s*(?:MobEffects\\.)?([A-Za-z0-9_]+)\\s*,\\s*(\\d+)(?:\\s*,\\s*(\\d+))?").matcher(body);
            while (ef.find()) {
                Step s = Logic.newStep("effect");
                s.p.put("target", tgt);
                s.p.put("effect", normalizeEffectId(ef.group(1)));
                s.p.put("seconds", String.valueOf(Math.max(1, Integer.parseInt(ef.group(2)) / 20)));
                if (ef.group(3) != null) s.p.put("amplifier", ef.group(3));
                out.add(s);
            }
            Matcher hl = Pattern.compile("\\.heal\\(\\s*([0-9.]+)[fFdD]?\\s*\\)").matcher(body);
            if (hl.find()) {
                Step s = Logic.newStep("heal");
                s.p.put("target", "@s");
                s.p.put("amount", String.valueOf(Math.max(1, (int) Math.round(Double.parseDouble(hl.group(1))))));
                out.add(s);
            }
            Matcher ex = Pattern.compile("\\.explode\\([^;]*?,\\s*([0-9.]+)[fFdD]?").matcher(body);
            if (ex.find()) {
                Step s = Logic.newStep("explosion");
                s.p.put("power", String.valueOf(Math.max(1, (int) Math.round(Double.parseDouble(ex.group(1))))));
                out.add(s);
            }
            if (body.contains("LightningBolt") || body.contains("LIGHTNING_BOLT")) out.add(Logic.newStep("lightning"));
            if (!out.isEmpty()) {
                res.extractedHooks += out.size();
                if (ip != null) {
                    if (method.equals("hurtEnemy")) ip.onHitEntity.addAll(out);
                    else if (method.equals("inventoryTick")) ip.onTickInHand.addAll(out);
                    else ip.onUse.addAll(out);
                } else if (bp != null) {
                    if (method.equals("stepOn")) bp.onStep.addAll(out);
                    else if (method.equals("randomTick")) bp.onRandomTick.addAll(out);
                    else bp.onUse.addAll(out);
                } else if (ep != null) {
                    if (method.equals("hurt")) ep.onHurt.addAll(out);
                    else if (method.equals("die")) ep.onDeath.addAll(out);
                    else ep.onInteract.addAll(out);
                }
            }
        }
    }

    private static int findMatchingBrace(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i + 1;
        }
        return s.length();
    }

    // ------------------------------------------------------------------ Helper lookups

    private static String normalizeEffectId(String raw) {
        String up = raw.toUpperCase(Locale.ROOT);
        String mapped = LEGACY_EFFECT_NAMES.getOrDefault(up, up.toLowerCase(Locale.ROOT));
        return "minecraft:" + mapped;
    }

    private static String normalizeTier(String raw) {
        String low = raw.toLowerCase(Locale.ROOT);
        if (low.contains("netherite")) return "netherite";
        if (low.contains("diamond")) return "diamond";
        if (low.contains("gold")) return "gold";
        if (low.contains("stone")) return "stone";
        if (low.contains("wood")) return "wood";
        return "iron";
    }

    private static String normalizeSoundType(String raw) {
        String up = raw.toUpperCase(Locale.ROOT);
        if (LEGACY_SOUND_TYPES.containsKey(up)) return LEGACY_SOUND_TYPES.get(up);
        String low = raw.toLowerCase(Locale.ROOT);
        if (Vanilla.SOUND_TYPES.contains(low)) return low;
        if (low.contains("wood") || low.contains("plank")) return "wood";
        if (low.contains("metal") || low.contains("iron")) return "metal";
        if (low.contains("glass")) return "glass";
        if (low.contains("sand")) return "sand";
        if (low.contains("gravel")) return "gravel";
        return "stone";
    }

    private static void addGoal(List<String> list, String goal) {
        if (!list.contains(goal)) list.add(goal);
    }

    private static String resolveSuperKind(ClassModel cm, Map<String, ClassModel> classes, int depth) {
        if (cm == null || depth > 8) return "";
        String sup = cm.superName == null ? "" : cm.superName;
        if (isItemSuper(sup) || isBlockSuper(sup) || isEntitySuper(sup)) return sup;
        ClassModel parent = classes.get(sup);
        return parent != null ? resolveSuperKind(parent, classes, depth + 1) : sup;
    }

    private static boolean isItemSuper(String s) {
        return s.endsWith("Item") || s.endsWith("ArmorItem") || s.endsWith("BowItem") || s.endsWith("CrossbowItem")
                || s.endsWith("ShieldItem") || s.endsWith("TridentItem") || s.endsWith("ElytraItem")
                || s.endsWith("SnowballItem") || s.endsWith("EnderpearlItem") || s.endsWith("DiggerItem") || s.endsWith("TieredItem")
                || s.startsWith("net/minecraft/item/Item")
                || List.of("net/minecraft/class_1792", "net/minecraft/class_1829", "net/minecraft/class_1810",
                "net/minecraft/class_1743", "net/minecraft/class_1821", "net/minecraft/class_1794", "net/minecraft/class_1738",
                "net/minecraft/class_1753", "net/minecraft/class_1764", "net/minecraft/class_1819", "net/minecraft/class_1835",
                "net/minecraft/class_1770", "net/minecraft/class_1823", "net/minecraft/class_1776").contains(s);
    }

    private static boolean isToolClass(String s) {
        return s.endsWith("SwordItem") || s.endsWith("PickaxeItem") || s.endsWith("AxeItem")
                || s.endsWith("ShovelItem") || s.endsWith("HoeItem")
                || s.endsWith("ItemSword") || s.endsWith("ItemPickaxe") || s.endsWith("ItemAxe")
                || s.endsWith("ItemSpade") || s.endsWith("ItemHoe") || s.endsWith("ItemTool")
                || List.of("net/minecraft/class_1829", "net/minecraft/class_1810", "net/minecraft/class_1743",
                "net/minecraft/class_1821", "net/minecraft/class_1794").contains(s);
    }

    private static boolean isBlockSuper(String s) {
        return (s.endsWith("Block") && !s.contains("BlockEntity"))
                || (s.startsWith("net/minecraft/block/Block") && !s.contains("TileEntity") && !s.contains("BlockEntity"))
                || List.of("net/minecraft/class_2248", "net/minecraft/class_2346", "net/minecraft/class_2302",
                "net/minecraft/class_2410", "net/minecraft/class_2482", "net/minecraft/class_2510",
                "net/minecraft/class_2323", "net/minecraft/class_2533").contains(s);
    }

    private static boolean isEntitySuper(String s) {
        return s.endsWith("Monster") || s.endsWith("Animal") || s.endsWith("PathfinderMob") || s.endsWith("Mob")
                || s.endsWith("Projectile") || s.endsWith("ThrowableItemProjectile") || s.endsWith("AbstractArrow")
                || (s.startsWith("net/minecraft/entity/") && (s.endsWith("EntityMob") || s.endsWith("EntityCreature")
                || s.endsWith("EntityAnimal") || s.endsWith("EntityLiving") || s.endsWith("EntityAmbientCreature")
                || s.endsWith("EntityWaterMob") || s.endsWith("EntityFlying") || s.endsWith("EntityGolem")
                || s.endsWith("EntityTameable") || s.endsWith("EntityThrowable") || s.endsWith("EntityArrow")
                || s.endsWith("EntityFireball") || s.endsWith("EntityTippedArrow")))
                || List.of("net/minecraft/class_1588", "net/minecraft/class_1429", "net/minecraft/class_1314",
                "net/minecraft/class_1308", "net/minecraft/class_1676").contains(s);
    }

    private static String mapItemKindFromSuper(String sup) {
        if (sup.endsWith("SwordItem") || sup.endsWith("ItemSword") || sup.endsWith("class_1829")) return "sword";
        if (sup.endsWith("PickaxeItem") || sup.endsWith("ItemPickaxe") || sup.endsWith("class_1810")) return "pickaxe";
        if (sup.endsWith("AxeItem") || sup.endsWith("ItemAxe") || sup.endsWith("class_1743")) return "axe";
        if (sup.endsWith("ShovelItem") || sup.endsWith("ItemSpade") || sup.endsWith("class_1821")) return "shovel";
        if (sup.endsWith("HoeItem") || sup.endsWith("ItemHoe") || sup.endsWith("class_1794")) return "hoe";
        if (sup.endsWith("BowItem") || sup.endsWith("ItemBow") || sup.endsWith("class_1753")) return "bow";
        if (sup.endsWith("CrossbowItem") || sup.endsWith("class_1764")) return "crossbow";
        if (sup.endsWith("ShieldItem") || sup.endsWith("ItemShield") || sup.endsWith("class_1819")) return "shield";
        if (sup.endsWith("TridentItem") || sup.endsWith("class_1835")) return "trident";
        if (sup.endsWith("ElytraItem") || sup.endsWith("ItemElytra") || sup.endsWith("class_1770")) return "elytra";
        if (sup.endsWith("ItemFood")) return "food";
        if (sup.endsWith("SnowballItem") || sup.endsWith("EnderpearlItem") || sup.endsWith("ProjectileItem")
                || sup.endsWith("ItemSnowball") || sup.endsWith("ItemEnderPearl")
                || sup.endsWith("class_1823") || sup.endsWith("class_1776")) return "throwable";
        return "basic";
    }

    private static String mapBlockShapeFromSuper(String sup) {
        if (sup.endsWith("SlabBlock") || sup.endsWith("BlockSlab") || sup.endsWith("class_2482")) return "slab";
        if (sup.endsWith("StairBlock") || sup.endsWith("BlockStairs") || sup.endsWith("class_2510")) return "stairs";
        if (sup.endsWith("DoorBlock") || sup.endsWith("BlockDoor") || sup.endsWith("class_2323")) return "door";
        if (sup.endsWith("TrapDoorBlock") || sup.endsWith("BlockTrapDoor") || sup.endsWith("class_2533")) return "trapdoor";
        if (sup.endsWith("CropBlock") || sup.endsWith("BlockCrops") || sup.endsWith("class_2302")) return "crop";
        if (sup.endsWith("FenceBlock") || sup.endsWith("BlockFence")) return "fence";
        if (sup.endsWith("WallBlock") || sup.endsWith("BlockWall")) return "wall";
        if (sup.endsWith("ButtonBlock") || sup.endsWith("BlockButton")) return "button";
        if (sup.endsWith("PressurePlateBlock") || sup.endsWith("BlockPressurePlate")) return "pressure_plate";
        if (sup.endsWith("RotatedPillarBlock") || sup.endsWith("BlockRotatedPillar") || sup.endsWith("BlockLog")) return "pillar";
        if (sup.endsWith("BlockBush") || sup.endsWith("BlockFlower")) return "cross";
        return "cube";
    }

    private static String mapEntityKindFromSuper(String sup) {
        if (sup.endsWith("Projectile") || sup.endsWith("AbstractArrow") || sup.endsWith("EntityThrowable")
                || sup.endsWith("EntityArrow") || sup.endsWith("EntityTippedArrow") || sup.endsWith("EntityFireball")
                || sup.endsWith("class_1676")) return "projectile";
        if (sup.endsWith("Animal") || sup.endsWith("EntityAnimal") || sup.endsWith("EntityTameable") || sup.endsWith("class_1429")) return "passive";
        if (sup.endsWith("Monster") || sup.endsWith("EntityMob") || sup.endsWith("class_1588")) return "hostile";
        return "mob";
    }

    private static boolean looksLikeRegistryId(String s) {
        if (s == null || s.length() < 2 || s.length() > 80 || s.contains(" ") || s.contains("/")) return false;
        String clean = s.contains(":") ? Ids.path(s) : s;
        return clean.matches("[a-z][a-z0-9_]{1,63}");
    }

    private static boolean isResourceLocationHelper(String owner, String name) {
        if (owner == null) return false;
        return owner.contains("ResourceLocation") || owner.contains("Identifier") || owner.contains("class_2960")
                || owner.contains("ResourceKey") || owner.contains("class_5321");
    }

    private static String extractExplicitRegistryName(ClassModel cm) {
        for (MethodModel mm : cm.methods) {
            if (!"<init>".equals(mm.name)) continue;
            for (int i = 0; i < mm.insns.size(); i++) {
                Insn in = mm.insns.get(i);
                if (in.kind == InsnKind.CALL && in.name != null
                        && (in.name.equals("setRegistryName") || in.name.equals("setUnlocalizedName")
                        || in.name.equals("func_77655_b") || in.name.equals("func_149663_c"))) {
                    String s = findRecentString(mm.insns, i, 4);
                    if (s != null && looksLikeRegistryId(s)) {
                        return Ids.toId(s.contains(":") ? Ids.path(s) : s);
                    }
                }
            }
        }
        return null;
    }

    private static boolean isGenericHelperClassName(String id) {
        return id.isEmpty() || id.equals("base") || id.equals("tc") || id.equals("tc_base") || id.equals("tc_device")
                || id.equals("tc_item") || id.equals("tc_block") || id.equals("item") || id.equals("block") || id.equals("entity")
                || id.startsWith("abstract_") || id.startsWith("base_") || id.endsWith("_base") || id.startsWith("ai_");
    }

    private static String guessRegistryName(String simpleClassName, String suffix) {
        String s = simpleClassName;
        if (s.endsWith(suffix) && s.length() > suffix.length()) {
            s = s.substring(0, s.length() - suffix.length());
        } else if (s.startsWith(suffix) && s.length() > suffix.length() && Character.isUpperCase(s.charAt(suffix.length()))) {
            s = s.substring(suffix.length());
        }
        StringBuilder snake = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && Character.isLowerCase(s.charAt(i - 1))) snake.append('_');
            snake.append(Character.toLowerCase(c));
        }
        return Ids.toId(snake.toString());
    }

    private static String shortName(String internal) {
        int sl = internal.lastIndexOf('/');
        return sl >= 0 ? internal.substring(sl + 1) : internal;
    }

    private static void pushOperand(Deque<Object> stack, Insn in) {
        if (in.kind == InsnKind.PUSH_INT) stack.push(in.intVal);
        else if (in.kind == InsnKind.PUSH_FLOAT) stack.push(in.floatVal);
        else if (in.kind == InsnKind.PUSH_DOUBLE) stack.push(in.doubleVal);
        else if (in.kind == InsnKind.PUSH_STR) stack.push(in.strVal);
    }

    private static Integer lastInt(List<Insn> insns, int endIdx, int lookback) {
        for (int j = Math.min(endIdx - 1, insns.size() - 1); j >= Math.max(0, endIdx - lookback); j--) {
            if (insns.get(j).kind == InsnKind.PUSH_INT) return insns.get(j).intVal;
        }
        return null;
    }

    private static Float lastFloat(List<Insn> insns, int endIdx, int lookback) {
        int start = Math.max(0, endIdx - lookback);
        int end = Math.min(endIdx - 1, insns.size() - 1);
        for (int j = end; j >= start; j--) {
            if (insns.get(j).kind == InsnKind.PUSH_FLOAT) return insns.get(j).floatVal;
        }
        for (int j = end; j >= start; j--) {
            if (insns.get(j).kind == InsnKind.PUSH_INT) return (float) insns.get(j).intVal;
        }
        return null;
    }

    private static Double firstDoubleAfter(List<Insn> insns, int startIdx, int lookahead) {
        int end = Math.min(insns.size(), startIdx + 1 + lookahead);
        for (int j = startIdx + 1; j < end; j++) {
            Insn in = insns.get(j);
            if (j > startIdx + 1 && in.kind == InsnKind.GETSTATIC) break;
            if (in.kind == InsnKind.PUSH_DOUBLE) return in.doubleVal;
            if (in.kind == InsnKind.PUSH_FLOAT) return (double) in.floatVal;
            if (in.kind == InsnKind.PUSH_INT) return (double) in.intVal;
        }
        return null;
    }

    private static Double lastDouble(List<Insn> insns, int endIdx, int lookback) {
        for (int j = Math.min(endIdx - 1, insns.size() - 1); j >= Math.max(0, endIdx - lookback); j--) {
            Insn in = insns.get(j);
            if (in.kind == InsnKind.PUSH_DOUBLE) return in.doubleVal;
            if (in.kind == InsnKind.PUSH_FLOAT) return (double) in.floatVal;
            if (in.kind == InsnKind.PUSH_INT) return (double) in.intVal;
        }
        return null;
    }

    private static List<Number> recentNumbers(List<Insn> insns, int endIdx, int lookback) {
        List<Number> r = new ArrayList<>();
        for (int j = Math.max(0, endIdx - lookback); j < Math.min(endIdx, insns.size()); j++) {
            Insn in = insns.get(j);
            if (in.kind == InsnKind.PUSH_INT) r.add(in.intVal);
            else if (in.kind == InsnKind.PUSH_FLOAT) r.add(in.floatVal);
            else if (in.kind == InsnKind.PUSH_DOUBLE) r.add(in.doubleVal);
        }
        return r;
    }

    private static String findRecentStatic(List<Insn> insns, int endIdx, int lookback, String... ownerContains) {
        for (int j = Math.min(endIdx - 1, insns.size() - 1); j >= Math.max(0, endIdx - lookback); j--) {
            Insn in = insns.get(j);
            if (in.kind == InsnKind.GETSTATIC && in.owner != null && in.name != null) {
                for (String key : ownerContains) {
                    if (in.owner.contains(key)) return in.name;
                }
            }
        }
        return null;
    }

    private static String findRecentNewOrStatic(List<Insn> insns, int endIdx, int lookback, List<String> hints) {
        for (int j = Math.min(endIdx - 1, insns.size() - 1); j >= Math.max(0, endIdx - lookback); j--) {
            Insn in = insns.get(j);
            if (in.kind == InsnKind.GETSTATIC && in.owner != null && in.name != null
                    && (in.owner.contains("EntityType") || in.owner.contains("class_1299"))) {
                return in.name;
            }
            if (in.kind == InsnKind.NEW && in.owner != null) {
                for (String h : hints) {
                    if (in.owner.contains(h)) return shortName(in.owner);
                }
            }
        }
        return null;
    }

    private static String findRecentString(List<Insn> insns, int endIdx, int lookback) {
        for (int j = Math.min(endIdx - 1, insns.size() - 1); j >= Math.max(0, endIdx - lookback); j--) {
            if (insns.get(j).kind == InsnKind.PUSH_STR && insns.get(j).strVal != null) return insns.get(j).strVal;
        }
        return null;
    }

    // ------------------------------------------------------------------ Lightweight JVM Class & Bytecode Parser

    private enum InsnKind { NOP, PUSH_INT, PUSH_FLOAT, PUSH_DOUBLE, PUSH_STR, GETSTATIC, PUTSTATIC, PUTFIELD, CALL, NEW, INVOKEDYNAMIC }

    private record MethodRef(String owner, String name, String desc) {}

    private static final class Insn {
        final InsnKind kind;
        int intVal;
        float floatVal;
        double doubleVal;
        String strVal;
        String owner, name, desc;

        Insn(InsnKind kind) { this.kind = kind; }
    }

    private static final class MethodModel {
        final String name, desc;
        final List<Insn> insns;

        MethodModel(String name, String desc, List<Insn> insns) {
            this.name = name;
            this.desc = desc;
            this.insns = insns;
        }
    }

    private static final class ClassModel {
        final String internalName, superName;
        final int accessFlags;
        final List<MethodModel> methods = new ArrayList<>();
        final List<MethodRef> indyTargets = new ArrayList<>();

        ClassModel(String internalName, String superName, int accessFlags) {
            this.internalName = internalName;
            this.superName = superName;
            this.accessFlags = accessFlags;
        }

        boolean isAbstractOrInterface() { return (accessFlags & 0x0600) != 0; }

        String simpleName() { return shortName(internalName); }
    }

    private static ClassModel parseClass(byte[] raw) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
            if (in.readInt() != 0xCAFEBABE) return null;
            in.readUnsignedShort(); // minor
            in.readUnsignedShort(); // major
            int cpCount = in.readUnsignedShort();
            byte[] tags = new byte[cpCount];
            String[] utf8 = new String[cpCount];
            int[] a = new int[cpCount], b = new int[cpCount];
            int[] ints = new int[cpCount];
            float[] floats = new float[cpCount];
            double[] doubles = new double[cpCount];

            for (int i = 1; i < cpCount; i++) {
                byte tag = in.readByte();
                tags[i] = tag;
                switch (tag) {
                    case 1 -> utf8[i] = in.readUTF();
                    case 3 -> ints[i] = in.readInt();
                    case 4 -> floats[i] = in.readFloat();
                    case 5 -> { in.readLong(); i++; }
                    case 6 -> { doubles[i] = in.readDouble(); i++; }
                    case 7, 8, 16, 19, 20 -> a[i] = in.readUnsignedShort();
                    case 9, 10, 11, 12, 17, 18 -> {
                        a[i] = in.readUnsignedShort();
                        b[i] = in.readUnsignedShort();
                    }
                    case 15 -> {
                        a[i] = in.readUnsignedByte();
                        b[i] = in.readUnsignedShort();
                    }
                    default -> {
                        return null;
                    }
                }
            }
            int accessFlags = in.readUnsignedShort(); // access
            int thisIdx = in.readUnsignedShort();
            int superIdx = in.readUnsignedShort();
            String thisName = classStr(tags, utf8, a, thisIdx);
            String superName = classStr(tags, utf8, a, superIdx);
            ClassModel cm = new ClassModel(thisName, superName, accessFlags);

            int ifaces = in.readUnsignedShort();
            in.skipBytes(ifaces * 2);

            int fields = in.readUnsignedShort();
            for (int i = 0; i < fields; i++) {
                in.skipBytes(6);
                skipAttrs(in);
            }

            List<RawMethod> rawMethods = new ArrayList<>();
            int methods = in.readUnsignedShort();
            for (int i = 0; i < methods; i++) {
                in.readUnsignedShort();
                String mn = cp(utf8, in.readUnsignedShort());
                String md = cp(utf8, in.readUnsignedShort());
                byte[] codeBytes = null;
                int attrs = in.readUnsignedShort();
                for (int k = 0; k < attrs; k++) {
                    String an = cp(utf8, in.readUnsignedShort());
                    int alen = in.readInt();
                    if ("Code".equals(an) && alen >= 12) {
                        in.readUnsignedShort(); // maxStack
                        in.readUnsignedShort(); // maxLocals
                        int codeLen = in.readInt();
                        codeBytes = in.readNBytes(codeLen);
                        int exLen = in.readUnsignedShort();
                        in.skipBytes(exLen * 8);
                        skipAttrs(in);
                    } else {
                        in.skipBytes(alen);
                    }
                }
                rawMethods.add(new RawMethod(mn, md, codeBytes));
            }

            // Class attributes: BootstrapMethods links invokedynamic indices -> lambda implementation MethodRefs
            int cAttrs = in.readUnsignedShort();
            List<MethodRef> bsmImpls = new ArrayList<>();
            for (int i = 0; i < cAttrs; i++) {
                String an = cp(utf8, in.readUnsignedShort());
                int alen = in.readInt();
                if ("BootstrapMethods".equals(an) && alen >= 2) {
                    int n = in.readUnsignedShort();
                    for (int bi = 0; bi < n; bi++) {
                        in.readUnsignedShort(); // bootstrap_method_ref
                        int argc = in.readUnsignedShort();
                        MethodRef found = null;
                        for (int ai = 0; ai < argc; ai++) {
                            int argIdx = in.readUnsignedShort();
                            if (argIdx > 0 && argIdx < cpCount && tags[argIdx] == 15) {
                                int refIdx = b[argIdx];
                                if (refIdx > 0 && refIdx < cpCount && (tags[refIdx] == 10 || tags[refIdx] == 11)) {
                                    String own = classStr(tags, utf8, a, a[refIdx]);
                                    int nt = b[refIdx];
                                    found = new MethodRef(own, cp(utf8, a[nt]), cp(utf8, b[nt]));
                                }
                            }
                        }
                        bsmImpls.add(found);
                    }
                } else {
                    in.skipBytes(alen);
                }
            }
            for (int cpIdx = 1; cpIdx < cpCount; cpIdx++) {
                if (tags[cpIdx] == 18) { // InvokeDynamic
                    int bsmIdx = a[cpIdx];
                    MethodRef tgt = (bsmIdx >= 0 && bsmIdx < bsmImpls.size()) ? bsmImpls.get(bsmIdx) : null;
                    while (cm.indyTargets.size() <= bsmIdx) cm.indyTargets.add(null);
                    if (tgt != null) cm.indyTargets.set(bsmIdx, tgt);
                }
            }

            for (RawMethod rm : rawMethods) {
                List<Insn> decoded = rm.code == null ? List.of() : decodeCode(rm.code, tags, utf8, a, b, ints, floats, doubles);
                cm.methods.add(new MethodModel(rm.name, rm.desc, decoded));
            }
            return cm;
        } catch (Exception ignored) {
            return null;
        }
    }

    private record RawMethod(String name, String desc, byte[] code) {}

    private static List<Insn> decodeCode(byte[] code, byte[] tags, String[] utf8, int[] a, int[] b,
                                           int[] ints, float[] floats, double[] doubles) {
        List<Insn> out = new ArrayList<>();
        int pc = 0;
        int len = code.length;
        while (pc < len) {
            int op = code[pc] & 0xFF;
            if (op >= 0x02 && op <= 0x08) { // iconst_m1..iconst_5
                Insn in = new Insn(InsnKind.PUSH_INT);
                in.intVal = op - 0x03;
                out.add(in);
                pc++;
            } else if (op >= 0x0B && op <= 0x0D) { // fconst_0..2
                Insn in = new Insn(InsnKind.PUSH_FLOAT);
                in.floatVal = op - 0x0B;
                out.add(in);
                pc++;
            } else if (op == 0x0E || op == 0x0F) { // dconst_0..1
                Insn in = new Insn(InsnKind.PUSH_DOUBLE);
                in.doubleVal = op - 0x0E;
                out.add(in);
                pc++;
            } else if (op == 0x10 && pc + 1 < len) { // bipush
                Insn in = new Insn(InsnKind.PUSH_INT);
                in.intVal = code[pc + 1];
                out.add(in);
                pc += 2;
            } else if (op == 0x11 && pc + 2 < len) { // sipush
                Insn in = new Insn(InsnKind.PUSH_INT);
                in.intVal = (short) (((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF));
                out.add(in);
                pc += 3;
            } else if ((op == 0x12 && pc + 1 < len) || ((op == 0x13 || op == 0x14) && pc + 2 < len)) { // ldc / ldc_w / ldc2_w
                int idx = op == 0x12 ? (code[pc + 1] & 0xFF) : (((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF));
                pc += (op == 0x12 ? 2 : 3);
                if (idx > 0 && idx < tags.length) {
                    switch (tags[idx]) {
                        case 3 -> { Insn in = new Insn(InsnKind.PUSH_INT); in.intVal = ints[idx]; out.add(in); }
                        case 4 -> { Insn in = new Insn(InsnKind.PUSH_FLOAT); in.floatVal = floats[idx]; out.add(in); }
                        case 6 -> { Insn in = new Insn(InsnKind.PUSH_DOUBLE); in.doubleVal = doubles[idx]; out.add(in); }
                        case 8 -> { Insn in = new Insn(InsnKind.PUSH_STR); in.strVal = cp(utf8, a[idx]); out.add(in); }
                    }
                }
            } else if ((op == 0xB2 || op == 0xB3 || op == 0xB5 || op == 0xB6 || op == 0xB7 || op == 0xB8 || op == 0xB9) && pc + 2 < len) {
                int idx = ((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF);
                if (idx > 0 && idx < tags.length && (tags[idx] == 9 || tags[idx] == 10 || tags[idx] == 11)) {
                    InsnKind kind = op == 0xB2 ? InsnKind.GETSTATIC : op == 0xB3 ? InsnKind.PUTSTATIC : op == 0xB5 ? InsnKind.PUTFIELD : InsnKind.CALL;
                    Insn in = new Insn(kind);
                    in.owner = classStr(tags, utf8, a, a[idx]);
                    int nt = b[idx];
                    if (nt > 0 && nt < tags.length) {
                        in.name = cp(utf8, a[nt]);
                        in.desc = cp(utf8, b[nt]);
                    }
                    out.add(in);
                }
                pc += (op == 0xB9 ? 5 : 3);
            } else if (op == 0xBB && pc + 2 < len) { // new
                int idx = ((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF);
                Insn in = new Insn(InsnKind.NEW);
                in.owner = classStr(tags, utf8, a, idx);
                out.add(in);
                pc += 3;
            } else if (op == 0xBA) { // invokedynamic
                if (pc + 4 >= len) break;
                int idx = ((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF);
                Insn in = new Insn(InsnKind.INVOKEDYNAMIC);
                if (idx > 0 && idx < tags.length && tags[idx] == 18) in.intVal = a[idx];
                else in.intVal = -1;
                out.add(in);
                pc += 5;
            } else if (op == 0xAA) { // tableswitch
                int pad = (4 - ((pc + 1) & 3)) & 3;
                int base = pc + 1 + pad;
                if (base + 12 <= len) {
                    int low = readI4(code, base + 4), high = readI4(code, base + 8);
                    int cnt = Math.max(0, high - low + 1);
                    pc = base + 12 + cnt * 4;
                } else break;
            } else if (op == 0xAB) { // lookupswitch
                int pad = (4 - ((pc + 1) & 3)) & 3;
                int base = pc + 1 + pad;
                if (base + 8 <= len) {
                    int npairs = Math.max(0, readI4(code, base + 4));
                    pc = base + 8 + npairs * 8;
                } else break;
            } else if (op == 0xC4 && pc + 1 < len) { // wide
                int sub = code[pc + 1] & 0xFF;
                pc += (sub == 0x84 ? 6 : 4);
            } else {
                int step = opcodeLength(op);
                if (step <= 0) break;
                pc += step;
            }
        }
        return out;
    }

    private static int readI4(byte[] c, int i) {
        return ((c[i] & 0xFF) << 24) | ((c[i + 1] & 0xFF) << 16) | ((c[i + 2] & 0xFF) << 8) | (c[i + 3] & 0xFF);
    }

    private static double parseDoubleSafe(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static String normalizeSoundEventId(String enumConst) {
        String low = enumConst.toLowerCase(Locale.ROOT);
        if (low.startsWith("entity_") || low.startsWith("block_") || low.startsWith("item_")
                || low.startsWith("ambient_") || low.startsWith("music_") || low.startsWith("ui_")
                || low.startsWith("weather_")) {
            return "minecraft:" + low.replaceFirst("^([a-z]+)_", "$1.");
        }
        return "minecraft:entity." + low.replace('_', '.');
    }

    private static int opcodeLength(int op) {
        if (op == 0x15 || op == 0x16 || op == 0x17 || op == 0x18 || op == 0x19
                || op == 0x36 || op == 0x37 || op == 0x38 || op == 0x39 || op == 0x3A
                || op == 0xA9 || op == 0xBC) return 2;
        if (op == 0x84 || op == 0xB4 || op == 0xBD || op == 0xC0 || op == 0xC1
                || (op >= 0x99 && op <= 0xA8) || op == 0xC6 || op == 0xC7) return 3;
        if (op == 0xC5) return 4;
        if (op == 0xC8 || op == 0xC9) return 5;
        return 1;
    }

    private static String classStr(byte[] tags, String[] utf8, int[] a, int idx) {
        if (idx <= 0 || idx >= tags.length) return "";
        return tags[idx] == 7 ? cp(utf8, a[idx]) : cp(utf8, idx);
    }

    private static String cp(String[] utf8, int idx) {
        return idx > 0 && idx < utf8.length && utf8[idx] != null ? utf8[idx] : "";
    }

    private static void skipAttrs(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            in.readUnsignedShort();
            in.skipBytes(in.readInt());
        }
    }
}
