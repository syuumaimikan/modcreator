package modstudio.gen;

import com.google.gson.JsonElement;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory set of generated files keyed by relative path (forward slashes). */
public final class FileMap {
    private final Map<String, byte[]> files = new LinkedHashMap<>();

    public void text(String path, String content) {
        files.put(norm(path), content.getBytes(StandardCharsets.UTF_8));
    }

    public void json(String path, JsonElement json) {
        text(path, J.pretty(json) + "\n");
    }

    public void bytes(String path, byte[] data) {
        files.put(norm(path), data);
    }

    public boolean has(String path) {
        return files.containsKey(norm(path));
    }

    public byte[] get(String path) {
        return files.get(norm(path));
    }

    public void remove(String path) {
        files.remove(norm(path));
    }

    public Map<String, byte[]> all() {
        return files;
    }

    public List<String> paths() {
        List<String> r = new ArrayList<>(files.keySet());
        r.sort(String::compareTo);
        return r;
    }

    public int size() {
        return files.size();
    }

    public void putAll(String prefix, FileMap other) {
        for (var e : other.files.entrySet()) files.put(norm(prefix + e.getKey()), e.getValue());
    }

    private static String norm(String p) {
        String s = p.replace('\\', '/');
        while (s.startsWith("/")) s = s.substring(1);
        return s;
    }
}
