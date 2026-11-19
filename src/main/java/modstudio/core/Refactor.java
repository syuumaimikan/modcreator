package modstudio.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

/** Updates references across the project when ids change. */
public final class Refactor {
    private Refactor() {}

    @FunctionalInterface
    public interface RefactorHook {
        void onRename(Project p, Element element, String oldName, String newName);
    }

    private static final List<RefactorHook> HOOKS = new CopyOnWriteArrayList<>();

    /** Registers a custom rename hook for plugin-defined element or reference models. */
    public static void registerHook(RefactorHook hook) {
        if (hook != null) HOOKS.add(hook);
    }

    /** Updates all references when an element is renamed from oldName to newName (after e.name has been updated). */
    public static void renameElement(Project p, Element e, String oldName, String newName) {
        if (p == null || e == null || oldName == null || newName == null || oldName.equals(newName)) return;
        String ns = p.meta.modId;
        switch (e.type()) {
            case ITEM, BLOCK, FLUID, FUNCTION, LOOT_TABLE, ADVANCEMENT, SOUND, STATUS_EFFECT, POTION, ENCHANTMENT,
                 DAMAGE_TYPE, PREDICATE, ITEM_MODIFIER, CONTAINER, ENTITY, PORTAL, WORLD_FEATURE, PROFESSION,
                 ARMOR_TRIM, BANNER_PATTERN, RECIPE_TYPE, BIOME, DIMENSION, COMMAND, STRUCTURE, MOB, EFFECT,
                 KEYBIND, SHADER, CUTSCENE, TRADER, MACHINE, MULTIBLOCK, API_REQUEST, RESOURCE, ICON_FONT, PAINTING -> {
                renameId(p, ns + ":" + oldName, ns + ":" + newName);
                if (e.type() == ElementType.FLUID) renameId(p, ns + ":" + oldName + "_bucket", ns + ":" + newName + "_bucket");
                if (e.type() == ElementType.ENTITY) renameId(p, ns + ":" + oldName + "_spawn_egg", ns + ":" + newName + "_spawn_egg");
                if (e.type() == ElementType.PORTAL) renameId(p, ns + ":" + oldName + "_igniter", ns + ":" + newName + "_igniter");
                if (e.type() == ElementType.ARMOR_TRIM) renameId(p, ns + ":" + oldName + "_armor_trim_smithing_template", ns + ":" + newName + "_armor_trim_smithing_template");
                if (e.type() == ElementType.BIOME) updateCommaLists(p, oldName, newName, ns);
            }
            case TAG -> {
                String tns = ((Elements.Tag) e).namespace;
                String tagNs = (tns == null || tns.isBlank()) ? ns : tns.trim();
                renameId(p, tagNs + ":" + oldName, tagNs + ":" + newName);
            }
            case PROCEDURE -> renameId(p, ns + ":procedures/" + oldName, ns + ":procedures/" + newName);
            case CREATIVE_TAB -> renameId(p, "tab:" + oldName, "tab:" + newName);
            case MODEL -> {
                for (Elements.Block b : p.all(Elements.Block.class)) if (oldName.equals(b.model)) b.model = newName;
                for (Elements.Item it : p.all(Elements.Item.class)) {
                    if (oldName.equals(it.model)) it.model = newName;
                    if (oldName.equals(it.wornModel)) it.wornModel = newName;
                }
                for (Elements.ArmorMaterial am : p.all(Elements.ArmorMaterial.class)) {
                    if (oldName.equals(am.helmetModel)) am.helmetModel = newName;
                    if (oldName.equals(am.chestplateModel)) am.chestplateModel = newName;
                    if (oldName.equals(am.leggingsModel)) am.leggingsModel = newName;
                    if (oldName.equals(am.bootsModel)) am.bootsModel = newName;
                }
                for (Systems.Mob m : p.all(Systems.Mob.class)) if (oldName.equals(m.rig)) m.rig = newName;
                for (Systems.Multiblock mb : p.all(Systems.Multiblock.class)) if (oldName.equals(mb.model)) mb.model = newName;
                for (ModElements.CustomEntity ce : p.all(ModElements.CustomEntity.class)) if (oldName.equals(ce.model)) ce.model = newName;
            }
            case ARMOR_MATERIAL -> {
                for (Elements.Item it : p.all(Elements.Item.class)) if (oldName.equals(it.armorMaterial)) it.armorMaterial = newName;
            }
            case TOOL_MATERIAL -> {
                for (Elements.Item it : p.all(Elements.Item.class)) if (oldName.equals(it.tier)) it.tier = newName;
            }
            case SONG -> {
                for (Elements.Item it : p.all(Elements.Item.class)) if (oldName.equals(it.song)) it.song = newName;
            }
            case CODE_FILE -> {
                if (oldName.equals(e.name)) break;
                renameId(p, ns + ":" + oldName, ns + ":" + newName);
            }
            default -> {
            }
        }
        renameTypedRefs(p, e.type(), oldName, newName);
        for (RefactorHook hook : HOOKS) hook.onRename(p, e, oldName, newName);
    }

    private static void updateCommaLists(Project p, String oldName, String newName, String ns) {
        String oldFull = ns + ":" + oldName;
        String newFull = ns + ":" + newName;
        for (Elements.Block b : p.all(Elements.Block.class)) {
            if (!b.oreBiomes.isBlank()) b.oreBiomes = replaceCsvToken(b.oreBiomes, oldName, newName, oldFull, newFull);
        }
        for (ModElements.WorldFeature wf : p.all(ModElements.WorldFeature.class)) {
            if (!wf.biomes.isBlank()) wf.biomes = replaceCsvToken(wf.biomes, oldName, newName, oldFull, newFull);
        }
        for (ModElements.CustomEntity ce : p.all(ModElements.CustomEntity.class)) {
            if (!ce.spawnBiomes.isBlank()) ce.spawnBiomes = replaceCsvToken(ce.spawnBiomes, oldName, newName, oldFull, newFull);
        }
    }

    private static String replaceCsvToken(String csv, String a, String b, String aFull, String bFull) {
        String[] parts = csv.split(",");
        for (int i = 0; i < parts.length; i++) {
            String t = parts[i].trim();
            if (t.equals(a)) parts[i] = b;
            else if (t.equals(aFull)) parts[i] = bFull;
            else parts[i] = t;
        }
        return String.join(",", parts);
    }

    private static void renameTypedRefs(Project p, ElementType type, String oldName, String newName) {
        String typeId = type.id;
        for (Element el : p.elements) {
            walkObjects(el, obj -> {
                if (obj instanceof Elements.Step st) {
                    Logic.Def def = Logic.STEPS.get(st.kind);
                    if (def != null) updateParams(def.params(), st.p, typeId, oldName, newName);
                    if (type == ElementType.COMMAND && "command".equals(st.kind)) {
                        String cmd = st.p.get("command");
                        if (cmd != null) st.p.put("command", rewriteCommand(cmd, oldName, newName));
                    }
                    if (st.cond != null) {
                        Logic.Def cdef = Logic.CONDITIONS.get(st.cond.kind);
                        if (cdef != null) updateParams(cdef.params(), st.cond.p, typeId, oldName, newName);
                    }
                } else if (obj instanceof Elements.Condition cond) {
                    Logic.Def def = Logic.CONDITIONS.get(cond.kind);
                    if (def != null) updateParams(def.params(), cond.p, typeId, oldName, newName);
                } else if (obj instanceof Elements.Function fn && type == ElementType.COMMAND && fn.code != null) {
                    fn.code = rewriteCommand(fn.code, oldName, newName);
                } else if (obj instanceof Systems.MenuSlot sl) {
                    if (type == ElementType.MENU && "menu".equals(sl.action) && oldName.equals(sl.target)) sl.target = newName;
                    else if (type == ElementType.COMMAND && "command".equals(sl.action)) sl.target = rewriteCommand(sl.target, oldName, newName);
                } else if (obj instanceof Systems.DialogButton b) {
                    if (type == ElementType.DIALOG && "dialog".equals(b.action) && oldName.equals(b.target)) b.target = newName;
                    else if (type == ElementType.COMMAND && ("suggest".equals(b.action) || "command".equals(b.action))) {
                        b.target = rewriteCommand(b.target, oldName, newName);
                    }
                } else if (obj instanceof Systems.ScreenWidget w) {
                    if (type == ElementType.SCREEN && "screen".equals(w.action) && oldName.equals(w.target)) w.target = newName;
                    else if (type == ElementType.PROCEDURE && "procedure".equals(w.action) && oldName.equals(w.target)) w.target = newName;
                    else if (type == ElementType.COMMAND && "command".equals(w.action)) w.target = rewriteCommand(w.target, oldName, newName);
                } else if (type == ElementType.RESOURCE && obj instanceof Systems.MachineIO io && oldName.equals(io.resource)) {
                    io.resource = newName;
                } else if (type == ElementType.RESOURCE && obj instanceof Systems.Multiblock mb && oldName.equals(mb.resource)) {
                    mb.resource = newName;
                } else if (type == ElementType.MULTIBLOCK && obj instanceof Systems.Machine m && oldName.equals(m.multiblock)) {
                    m.multiblock = newName;
                } else if (type == ElementType.PREDICATE && obj instanceof Elements.LootPool pool && oldName.equals(pool.predicate)) {
                    pool.predicate = newName;
                }
            });
        }
    }

    private static String rewriteCommand(String raw, String oldCmd, String newCmd) {
        if (raw == null || raw.isBlank() || oldCmd.isBlank()) return raw;
        return raw.replaceAll("(?m)(^|\\s)/?" + java.util.regex.Pattern.quote(oldCmd) + "(?=\\s|$)", "$1" + (raw.trim().startsWith("/") ? "/" : "") + newCmd);
    }

    private static void updateParams(List<Logic.Param> params, Map<String, String> values, String typeId, String oldName, String newName) {
        for (Logic.Param prm : params) {
            if (prm.type() == Logic.PType.REF && !prm.choices().isEmpty() && typeId.equals(prm.choices().get(0))) {
                if (oldName.equals(values.get(prm.key()))) values.put(prm.key(), newName);
            }
        }
    }

    /** Replaces every reference to oldId (also as tag "#oldId" or function prefix) with newId. */
    public static int renameId(Project p, String oldId, String newId) {
        if (oldId.equals(newId)) return 0;
        int[] count = {0};
        UnaryOperator<String> op = s -> {
            if (s == null) return null;
            if (s.equals(oldId)) { count[0]++; return newId; }
            if (s.equals("#" + oldId)) { count[0]++; return "#" + newId; }
            return s;
        };
        for (Element e : p.elements) walk(e, op, e.uid, false);
        if (p.meta != null && oldId.equals(p.meta.icon)) { p.meta.icon = newId; count[0]++; }
        return count[0];
    }

    /** Called when the mod id changes: rewrites "old:..." to "new:...". */
    public static void renameNamespace(Project p, String oldNs, String newNs) {
        if (oldNs.equals(newNs) || oldNs.isEmpty()) return;
        String a = oldNs + ":", b = newNs + ":";
        String tokenRegex = "(?<![A-Za-z0-9_.-])" + java.util.regex.Pattern.quote(oldNs) + ":";
        UnaryOperator<String> op = s -> {
            if (s == null) return null;
            if (s.startsWith(a)) return b + s.substring(a.length());
            if (s.startsWith("#" + a)) return "#" + b + s.substring(a.length() + 1);
            return s.replaceAll(tokenRegex, java.util.regex.Matcher.quoteReplacement(b));
        };
        for (Element e : p.elements) walk(e, op, e.uid, false);
        if (p.translations != null) {
            for (var langMap : p.translations.values()) {
                Map<String, String> next = new LinkedHashMap<>();
                for (var en : langMap.entrySet()) {
                    String k = en.getKey()
                            .replace("." + oldNs + ".", "." + newNs + ".")
                            .replaceFirst("^" + java.util.regex.Pattern.quote(oldNs) + "\\.", newNs + ".");
                    next.put(k, op.apply(en.getValue()));
                }
                langMap.clear();
                langMap.putAll(next);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void walk(Object o, UnaryOperator<String> op, String skipUid, boolean nestedNameAllowed) {
        if (o == null) return;
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                int m = f.getModifiers();
                if (Modifier.isStatic(m) || Modifier.isTransient(m)) continue;
                String fn = f.getName();
                if (fn.equals("uid") || fn.equals("loc")) continue;
                if (fn.equals("name") && !nestedNameAllowed) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(o);
                    if (v instanceof String s) {
                        String n = op.apply(s);
                        if (n != null && !n.equals(s)) f.set(o, n);
                    } else if (v instanceof List list) {
                        for (int i = 0; i < list.size(); i++) {
                            Object item = list.get(i);
                            if (item instanceof String s) {
                                String n = op.apply(s);
                                if (n != null && !n.equals(s)) list.set(i, n);
                            } else walk(item, op, skipUid, true);
                        }
                    } else if (v instanceof Map map) {
                        for (Object k : new ArrayList<>(map.keySet())) {
                            Object item = map.get(k);
                            if (item instanceof String s) {
                                String n = op.apply(s);
                                if (n != null && !n.equals(s)) map.put(k, n);
                            } else if (item instanceof Map<?, ?>) {
                                walk(item, op, skipUid, true);
                            } else {
                                walk(item, op, skipUid, true);
                            }
                        }
                    } else if (v != null && isNestedModel(v.getClass())) {
                        walk(v, op, skipUid, true);
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        }
    }

    private static boolean isNestedModel(Class<?> c) {
        String name = c.getName();
        return name.startsWith("modstudio.core.Elements$") || name.startsWith("modstudio.core.Systems$")
                || name.startsWith("modstudio.core.ModElements$");
    }

    @SuppressWarnings({"rawtypes"})
    private static void walkObjects(Object o, java.util.function.Consumer<Object> visitor) {
        if (o == null) return;
        visitor.accept(o);
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                int m = f.getModifiers();
                if (Modifier.isStatic(m) || Modifier.isTransient(m)) continue;
                if (f.getName().equals("loc")) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(o);
                    if (v instanceof List list) {
                        for (Object item : list) if (item != null && !(item instanceof String)) walkObjects(item, visitor);
                    } else if (v instanceof Map map) {
                        for (Object item : map.values()) {
                            if (item != null && !(item instanceof String)) walkObjects(item, visitor);
                        }
                    } else if (v != null && isNestedModel(v.getClass())) {
                        walkObjects(v, visitor);
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        }
    }
}
