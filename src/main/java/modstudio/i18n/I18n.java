package modstudio.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** UI translations. Bundles live in /modstudio/i18n/messages_<lang>.properties (UTF-8). */
public final class I18n {
    private I18n() {}

    public static final Map<String, String> LANGUAGES = new LinkedHashMap<>();
    static {
        LANGUAGES.put("ja", "日本語");
        LANGUAGES.put("en", "English");
        LANGUAGES.put("zh", "简体中文");
        LANGUAGES.put("ko", "한국어");
    }

    private static String lang = "ja";
    private static Properties current = load("ja");
    private static final Properties FALLBACK = load("en");

    private static Properties load(String code) {
        Properties p = new Properties();
        try (InputStream in = I18n.class.getResourceAsStream("/modstudio/i18n/messages_" + code + ".properties")) {
            if (in != null) p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
        return p;
    }

    public static void setLanguage(String code) {
        if (!LANGUAGES.containsKey(code)) code = "en";
        lang = code;
        current = load(code);
        Locale.setDefault(switch (code) {
            case "ja" -> Locale.JAPANESE;
            case "zh" -> Locale.SIMPLIFIED_CHINESE;
            case "ko" -> Locale.KOREAN;
            default -> Locale.ENGLISH;
        });
    }

    public static String language() {
        return lang;
    }

    public static boolean has(String key) {
        return current.containsKey(key) || FALLBACK.containsKey(key);
    }

    public static String t(String key, Object... args) {
        String s = current.getProperty(key);
        if (s == null) s = FALLBACK.getProperty(key);
        if (s == null) return key;
        if (args.length == 0) return s;
        try {
            return MessageFormat.format(s.replace("'", "''"), args);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    /** Looks up key, falling back to a default text when absent. */
    public static String tOr(String key, String def) {
        return has(key) ? t(key) : def;
    }

    /** Looks up key with MessageFormat arguments, falling back to default text when absent. */
    public static String tOr(String key, String def, Object... args) {
        return has(key) ? t(key, args) : def;
    }
}
