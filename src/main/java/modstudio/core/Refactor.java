package modstudio.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/** Updates references across the project when ids change. */
public final class Refactor {
    private Refactor() {}

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
        for (Element e : p.elements) walk(e, op, e.uid);
        return count[0];
    }

    /** Called when the mod id changes: rewrites "old:..." to "new:...". */
    public static void renameNamespace(Project p, String oldNs, String newNs) {
        if (oldNs.equals(newNs) || oldNs.isEmpty()) return;
        String a = oldNs + ":", b = newNs + ":";
        UnaryOperator<String> op = s -> {
            if (s == null) return null;
            if (s.startsWith(a)) return b + s.substring(a.length());
            if (s.startsWith("#" + a)) return "#" + b + s.substring(a.length() + 1);
            return s.replace("function " + a, "function " + b);
        };
        for (Element e : p.elements) walk(e, op, e.uid);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void walk(Object o, UnaryOperator<String> op, String skipUid) {
        if (o == null) return;
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                int m = f.getModifiers();
                if (Modifier.isStatic(m) || Modifier.isTransient(m)) continue;
                if (f.getName().equals("uid") || f.getName().equals("name") || f.getName().equals("loc")) continue;
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
                            } else walk(item, op, skipUid);
                        }
                    } else if (v instanceof Map map) {
                        for (Object k : map.keySet().toArray()) {
                            Object item = map.get(k);
                            if (item instanceof String s) {
                                String n = op.apply(s);
                                if (n != null && !n.equals(s)) map.put(k, n);
                            } else if (!(item instanceof Map)) walk(item, op, skipUid);
                        }
                    } else if (v != null && v.getClass().getName().startsWith("modstudio.core.Elements$")) {
                        walk(v, op, skipUid);
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        }
    }
}
