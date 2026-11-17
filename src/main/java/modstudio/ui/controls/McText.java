package modstudio.ui.controls;

import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/** Renders text with Minecraft &amp; color codes as a TextFlow (previews of HUDs, menus and tooltips). */
public final class McText {
    private McText() {}

    private static final String CODES = "0123456789abcdef";
    private static final String[] HEX = {"#000000", "#0000AA", "#00AA00", "#00AAAA", "#AA0000", "#AA00AA", "#FFAA00", "#AAAAAA",
            "#555555", "#5555FF", "#55FF55", "#55FFFF", "#FF5555", "#FF55FF", "#FFFF55", "#FFFFFF"};

    public static Color color(char code) {
        int i = CODES.indexOf(Character.toLowerCase(code));
        return i < 0 ? Color.WHITE : Color.web(HEX[i]);
    }

    /** Color for a named text color (red, dark_aqua, ...). */
    public static Color named(String name) {
        String[] names = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
                "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
        for (int i = 0; i < names.length; i++) if (names[i].equals(name)) return Color.web(HEX[i]);
        return Color.WHITE;
    }

    public static TextFlow flow(String text, Color base, double size) {
        TextFlow tf = new TextFlow();
        String s = text == null ? "" : text;
        Color color = base;
        boolean bold = false, italic = false, under = false, strike = false;
        StringBuilder buf = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < s.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(s.charAt(i + 1)) >= 0) {
                add(tf, buf, color, bold, italic, under, strike, size);
                char code = Character.toLowerCase(s.charAt(i + 1));
                if (CODES.indexOf(code) >= 0) {
                    color = color(code);
                    bold = italic = under = strike = false;
                } else switch (code) {
                    case 'l' -> bold = true;
                    case 'o' -> italic = true;
                    case 'n' -> under = true;
                    case 'm' -> strike = true;
                    case 'r' -> {
                        color = base;
                        bold = italic = under = strike = false;
                    }
                    default -> {
                    }
                }
                i += 2;
                continue;
            }
            buf.append(c);
            i++;
        }
        add(tf, buf, color, bold, italic, under, strike, size);
        return tf;
    }

    private static void add(TextFlow tf, StringBuilder buf, Color color, boolean bold, boolean italic, boolean under, boolean strike, double size) {
        if (buf.isEmpty()) return;
        Text t = new Text(buf.toString());
        t.setFill(color);
        t.setFont(Font.font("Monospaced", bold ? FontWeight.BOLD : FontWeight.NORMAL, italic ? FontPosture.ITALIC : FontPosture.REGULAR, size));
        t.setUnderline(under);
        t.setStrikethrough(strike);
        tf.getChildren().add(t);
        buf.setLength(0);
    }

    /** Removes color codes. */
    public static String plain(String s) {
        return s == null ? "" : s.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
    }
}
