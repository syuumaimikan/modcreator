package modstudio.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Generates simple 16x16 starter textures from a base colour. */
public final class TextureTemplates {
    private TextureTemplates() {}

    public static final Map<String, String[]> PATTERNS = new LinkedHashMap<>();

    static {
        PATTERNS.put("gem", new String[]{
                "................",
                "................",
                "................",
                "....11111111....",
                "...1455554431...",
                "..145555444331..",
                ".14555444433331.",
                ".13444443333221.",
                "..133333332221..",
                "...1333332221...",
                "....13333221....",
                ".....133221.....",
                "......1321......",
                ".......11.......",
                "................",
                "................"});
        PATTERNS.put("ingot", new String[]{
                "................",
                "................",
                "................",
                "................",
                "................",
                "................",
                ".......1111111..",
                ".....1155555541.",
                "...114555555431.",
                ".11444444443321.",
                ".13333333332221.",
                ".1222222222111..",
                "..11111111111...",
                "................",
                "................",
                "................"});
        PATTERNS.put("dust", new String[]{
                "................",
                "................",
                "................",
                "................",
                "...4............",
                "...........4....",
                "................",
                "........1.......",
                ".......141......",
                ".....1145311....",
                "....145543331...",
                "...14554433321..",
                "..1333333322221.",
                "...11111111111..",
                "................",
                "................"});
        PATTERNS.put("nugget", new String[]{
                "................",
                "................",
                "................",
                "................",
                "................",
                "................",
                "......111.......",
                ".....14551......",
                "....1455431.....",
                "....1343321.....",
                ".....12221......",
                "......111.......",
                "................",
                "................",
                "................",
                "................"});
        PATTERNS.put("sword", new String[]{
                "................",
                "............111.",
                "...........1551.",
                "..........15541.",
                ".........15431..",
                "........15431...",
                ".......15431....",
                "......15431.....",
                "..11.15431......",
                "..1215431.......",
                "...12431........",
                "...hh21.........",
                "..hhh.11........",
                ".hHh............",
                ".hh.............",
                "................"});
        PATTERNS.put("pickaxe", new String[]{
                "................",
                "....11111.......",
                "...1455541......",
                "....11133411....",
                "........1h341...",
                ".......hh..131..",
                "......hHh...131.",
                ".....hHh.....11.",
                "....hHh.........",
                "...hHh..........",
                "..hHh...........",
                ".hHh............",
                ".hh.............",
                "................",
                "................",
                "................"});
        PATTERNS.put("apple", new String[]{
                "................",
                "................",
                ".......h........",
                "........h.......",
                "....111h111.....",
                "...14551433 1...".replace(" ", ""),
                "..1455533333 1..".replace(" ", ""),
                "..14553333332 1.".replace(" ", ""),
                "..1453333333221.",
                "..1433333332221.",
                "..1333333322221.",
                "...13333322221..",
                "....132222221...",
                ".....111111.....",
                "................",
                "................"});
    }

    public static String[] names() {
        String[] r = new String[PATTERNS.size() + 2];
        int i = 0;
        for (String k : PATTERNS.keySet()) r[i++] = k;
        r[i++] = "block";
        r[i] = "ore";
        return r;
    }

    public static int[] generate(String template, int baseArgb) {
        int[] shades = shades(baseArgb);
        int[] px = new int[256];
        switch (template) {
            case "block" -> block(px, shades);
            case "ore" -> ore(px, shades);
            default -> {
                String[] rows = PATTERNS.get(template);
                if (rows == null) return px;
                for (int y = 0; y < 16; y++) {
                    String row = pad(rows[y]);
                    for (int x = 0; x < 16; x++) px[y * 16 + x] = color(row.charAt(x), shades);
                }
            }
        }
        return px;
    }

    private static String pad(String s) {
        if (s.length() >= 16) return s.substring(0, 16);
        return s + ".".repeat(16 - s.length());
    }

    private static int color(char c, int[] shades) {
        return switch (c) {
            case '1' -> shades[0];
            case '2' -> shades[1];
            case '3' -> shades[2];
            case '4' -> shades[3];
            case '5' -> shades[4];
            case 'h' -> 0xFF8A6239;
            case 'H' -> 0xFF5E3F22;
            default -> 0;
        };
    }

    /** Five shades from dark to light. */
    public static int[] shades(int argb) {
        float[] hsb = java.awt.Color.RGBtoHSB((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, null);
        return new int[]{
                hsbToArgb(hsb[0], Math.min(1f, hsb[1] * 1.1f), hsb[2] * 0.38f),
                hsbToArgb(hsb[0], Math.min(1f, hsb[1] * 1.05f), hsb[2] * 0.66f),
                hsbToArgb(hsb[0], hsb[1], hsb[2]),
                hsbToArgb(hsb[0], hsb[1] * 0.75f, Math.min(1f, hsb[2] * 1.18f + 0.06f)),
                hsbToArgb(hsb[0], hsb[1] * 0.35f, Math.min(1f, hsb[2] * 1.3f + 0.25f))};
    }

    private static int hsbToArgb(float h, float s, float b) {
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(h, Math.max(0, Math.min(1, s)), Math.max(0, Math.min(1, b))) & 0xFFFFFF);
    }

    private static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7fffffff;
    }

    private static void block(int[] px, int[] s) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c;
                if (x == 0 || y == 0) c = s[3];
                else if (x == 15 || y == 15) c = s[1];
                else if (x == 1 || y == 1) c = s[4];
                else if (x == 14 || y == 14) c = s[1];
                else c = (hash(x, y) % 7 == 0) ? s[3] : s[2];
                px[y * 16 + x] = c;
            }
        }
        for (int i = 3; i < 7; i++) px[i * 16 + (10 - i)] = s[4];
    }

    private static final String[] ORE = {
            "................",
            "..4.............",
            ".453.......44...",
            ".332......4553..",
            "..2.......3532..",
            "..........232...",
            ".....45.........",
            "....4553........",
            "....3532........",
            ".....32.....45..",
            "...........4532.",
            "..45........32..",
            ".4553...........",
            ".3332.....453...",
            "..22......332...",
            "................"};

    private static void ore(int[] px, int[] s) {
        int[] stone = {0xFF6F6F6F, 0xFF7D7D7D, 0xFF8A8A8A, 0xFF959595};
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = stone[hash(x, y) % 4];
                char o = ORE[y].charAt(x);
                if (o != '.') c = color(o, s);
                px[y * 16 + x] = c;
            }
        }
    }
}
