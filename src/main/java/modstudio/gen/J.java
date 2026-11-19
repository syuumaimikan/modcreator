package modstudio.gen;

import com.google.gson.*;

import java.util.Collection;
import java.util.Map;

/** Tiny JSON builder. */
public final class J {
    private J() {}

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    public static JsonObject obj(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v == null) continue;
            o.add((String) kv[i], el(v));
        }
        return o;
    }

    public static JsonArray arr(Object... vs) {
        JsonArray a = new JsonArray();
        for (Object v : vs) a.add(el(v));
        return a;
    }

    public static JsonArray list(Collection<?> c) {
        JsonArray a = new JsonArray();
        for (Object v : c) a.add(el(v));
        return a;
    }

    @SuppressWarnings("unchecked")
    public static JsonElement el(Object v) {
        if (v == null) return JsonNull.INSTANCE;
        if (v instanceof JsonElement e) return e;
        if (v instanceof String s) return new JsonPrimitive(s);
        if (v instanceof Number n) return new JsonPrimitive(n);
        if (v instanceof Boolean b) return new JsonPrimitive(b);
        if (v instanceof Collection<?> c) return list(c);
        if (v instanceof Map<?, ?> m) {
            JsonObject o = new JsonObject();
            for (var e : ((Map<Object, Object>) m).entrySet()) o.add(String.valueOf(e.getKey()), el(e.getValue()));
            return o;
        }
        return new JsonPrimitive(String.valueOf(v));
    }

    public static String pretty(JsonElement e) {
        return PRETTY.toJson(e);
    }

    public static String compact(JsonElement e) {
        return COMPACT.toJson(e);
    }

    /** JSON string literal including quotes. */
    public static String quote(String s) {
        return COMPACT.toJson(s == null ? "" : s);
    }

    /** Number formatted without trailing ".0" when integral. */
    public static Number num(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e9) return (long) d;
        return d;
    }

    /** pack.mcmeta object handling both legacy integer pack_format and 1.21.9+ [major, minor] format arrays. */
    public static JsonObject packMeta(modstudio.core.McVersion minV, modstudio.core.McVersion maxV, boolean resource, String description) {
        JsonObject pack = obj("pack_format", minV.format(resource), "description", description == null ? "" : description);
        if (minV != maxV) {
            pack.add("supported_formats", arr(minV.format(resource), maxV.format(resource)));
        }
        if (maxV.minorPackFormat()) {
            pack.add("min_format", arr(minV.format(resource), minV.formatMinor(resource)));
            pack.add("max_format", arr(maxV.format(resource), maxV.formatMinor(resource)));
        }
        return obj("pack", pack);
    }

    public static JsonObject packMeta(modstudio.core.McVersion v, boolean resource, String description) {
        return packMeta(v, v, resource, description);
    }
}
