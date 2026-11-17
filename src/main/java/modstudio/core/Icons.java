package modstudio.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Icon fonts: every icon of the project gets a private use character in the default font, written as :name: in
 * texts. {space:N} moves the following text N pixels (negative moves left), for laying out HUD images in titles and
 * the action bar.
 */
public final class Icons {
    private Icons() {}

    /** First character used for icons. */
    public static final char FIRST = '';
    /** Space characters: index i moves by 2^i pixels, index 8 + i by -2^i. */
    public static final char SPACE = '';
    public static final int SPACE_BITS = 8;
    private static final Pattern ICON = Pattern.compile(":([a-z0-9_]+):");
    private static final Pattern SPACE_TOKEN = Pattern.compile("\\{space:(-?\\d+)}");

    /** Icon name to its character, in project order; the first icon of a name wins. */
    public static Map<String, String> glyphs(Project p) {
        Map<String, String> r = new LinkedHashMap<>();
        int i = 0;
        for (Systems.IconFont f : p.all(Systems.IconFont.class)) {
            for (Systems.Icon ic : f.icons) {
                if (ic.name == null || ic.name.isBlank()) continue;
                char ch = (char) (FIRST + i++);
                r.putIfAbsent(ic.name.trim(), String.valueOf(ch));
            }
        }
        return r;
    }

    /** Character of each icon in order (same numbering as glyphs, duplicates included). */
    public static char charOf(int index) {
        return (char) (FIRST + index);
    }

    /** Replaces :name: with the icon characters and {space:N} with space characters. */
    public static String apply(String s, Map<String, String> glyphs) {
        if (s == null || s.isEmpty()) return s;
        if (s.indexOf('{') >= 0) {
            Matcher m = SPACE_TOKEN.matcher(s);
            StringBuilder b = new StringBuilder();
            while (m.find()) m.appendReplacement(b, Matcher.quoteReplacement(spaces(parse(m.group(1)))));
            m.appendTail(b);
            s = b.toString();
        }
        if (glyphs == null || glyphs.isEmpty() || s.indexOf(':') < 0) return s;
        Matcher m = ICON.matcher(s);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            String g = glyphs.get(m.group(1));
            m.appendReplacement(b, Matcher.quoteReplacement(g != null ? g : m.group()));
        }
        m.appendTail(b);
        return b.toString();
    }

    private static int parse(String s) {
        try {
            return Math.max(-4096, Math.min(4096, Integer.parseInt(s)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Space characters that move by exactly n pixels. */
    public static String spaces(int n) {
        StringBuilder b = new StringBuilder();
        int a = Math.abs(n);
        for (int bit = SPACE_BITS - 1; bit >= 0; bit--) {
            int v = 1 << bit;
            while (a >= v) {
                b.append((char) (SPACE + (n < 0 ? SPACE_BITS : 0) + bit));
                a -= v;
            }
        }
        return b.toString();
    }

    /** Whether the text uses an icon or a space token. */
    public static boolean uses(String s) {
        return s != null && (ICON.matcher(s).find() || SPACE_TOKEN.matcher(s).find());
    }
}
