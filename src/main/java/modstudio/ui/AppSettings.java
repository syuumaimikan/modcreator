package modstudio.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** User preferences stored in ~/.modstudio/settings.json. */
public final class AppSettings {
    public String language = defaultLanguage();
    public String theme = "light";
    public List<String> recent = new ArrayList<>();
    public String jdkPath = "";
    public boolean autosave = true;
    public double width = 1320;
    public double height = 860;
    public boolean maximized;
    /** AI Assistant / MCP configuration: builtin, openai, gemini, anthropic. */
    public String aiProvider = "builtin";
    public String aiBaseUrl = "https://api.openai.com/v1";
    public String aiModel = "gpt-4o-mini";
    public String aiApiKey = "";
    /** When true (snapshot runs), nothing is written to disk. */
    public transient boolean transientMode;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static String defaultLanguage() {
        String l = Locale.getDefault().getLanguage();
        return switch (l) {
            case "ja", "zh", "ko", "en" -> l;
            default -> "en";
        };
    }

    public static Path dir() {
        return Path.of(System.getProperty("user.home"), ".modstudio");
    }

    public static Path projectsDir() {
        return Path.of(System.getProperty("user.home"), "ModStudio");
    }

    public static AppSettings load() {
        Path f = dir().resolve("settings.json");
        try {
            if (Files.exists(f)) {
                AppSettings s = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), AppSettings.class);
                if (s != null) {
                    if (s.recent == null) s.recent = new ArrayList<>();
                    return s;
                }
            }
        } catch (Exception ignored) {
        }
        return new AppSettings();
    }

    public void save() {
        if (transientMode) return;
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve("settings.json"), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public void addRecent(Path file) {
        String s = file.toAbsolutePath().normalize().toString();
        recent.remove(s);
        recent.add(0, s);
        while (recent.size() > 12) recent.remove(recent.size() - 1);
        save();
    }
}
