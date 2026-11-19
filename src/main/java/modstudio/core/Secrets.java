package modstudio.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API keys and webhook URLs kept outside the project file. They are stored in secrets.local.properties next to the
 * project, never exported, and copied into the test run folder so the mod or plugin finds them at runtime.
 */
public final class Secrets {
    public static final String FILE = "secrets.local.properties";
    public static final Pattern REF = Pattern.compile("\\{secret:([A-Za-z0-9_.-]+)\\}");

    private final Path file;
    private final LinkedHashMap<String, String> values = new LinkedHashMap<>();

    public Secrets(Path projectDir) {
        this.file = projectDir.resolve(FILE);
        load();
    }

    public Path file() {
        return file;
    }

    private void load() {
        values.clear();
        if (!Files.exists(file)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException ignored) {
            return;
        }
        List<String> names = new ArrayList<>(p.stringPropertyNames());
        Collections.sort(names);
        for (String n : names) values.put(n, p.getProperty(n));
    }

    public Map<String, String> all() {
        return Collections.unmodifiableMap(values);
    }

    public String get(String name) {
        return values.getOrDefault(name, "");
    }

    public void put(String name, String value) {
        values.put(name, value == null ? "" : value);
    }

    public void remove(String name) {
        values.remove(name);
    }

    public void save() throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("# Local secrets for this project. Never commit or share this file.\n");
            Properties p = new Properties() {
                @Override
                public synchronized Set<Map.Entry<Object, Object>> entrySet() {
                    TreeMap<Object, Object> sorted = new TreeMap<>();
                    for (Map.Entry<Object, Object> en : super.entrySet()) {
                        sorted.put(en.getKey(), en.getValue());
                    }
                    return Collections.synchronizedSet(sorted.entrySet());
                }
            };
            p.putAll(values);
            p.store(w, null);
        }
        if (file.getParent() != null) ignoreInGit(file.getParent());
    }

    /** Adds the secrets file to the project's .gitignore so it is not committed by accident. */
    private static void ignoreInGit(Path dir) {
        Path gi = dir.resolve(".gitignore");
        try {
            String s = Files.exists(gi) ? Files.readString(gi, StandardCharsets.UTF_8) : "";
            if (s.lines().anyMatch(l -> l.trim().equals(FILE))) return;
            if (!s.isEmpty() && !s.endsWith("\n")) s += "\n";
            Files.writeString(gi, s + FILE + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /** Secret names referenced by the project's web requests. */
    public static Set<String> referenced(Project p) {
        Set<String> r = new TreeSet<>();
        for (Systems.ApiRequest a : p.all(Systems.ApiRequest.class)) {
            for (String part : List.of(a.url, a.headers, a.body)) {
                Matcher m = REF.matcher(part == null ? "" : part);
                while (m.find()) r.add(m.group(1));
            }
        }
        return r;
    }

    /** Writes the values the project uses to a runtime secrets file (test runs only). */
    public void writeRuntime(Project p, Path target) throws IOException {
        Set<String> names = referenced(p);
        if (names.isEmpty()) return;
        Files.createDirectories(target.getParent());
        StringBuilder sb = new StringBuilder("# Copied from " + FILE + " for this test run.\n");
        for (String n : names) sb.append(n).append('=').append(escape(get(n))).append('\n');
        Files.writeString(target, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String escape(String v) {
        StringBuilder sb = new StringBuilder();
        for (char c : v.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
