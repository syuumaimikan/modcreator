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
        PATTERNS.put("seeds", new String[]{
                "................",
                "................",
                "................",
                "................",
                "......1.........",
                ".....141...1....",
                ".....131..141...",
                "......1...131...",
                "...1.......1....",
                "..141...1.......",
                "..131..141..1...",
                "...1...131.141..",
                "........1..131..",
                "............1...",
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
        PATTERNS.put("hammer", new String[]{
                "................",
                "................",
                "..1111111111....",
                ".145555444331...",
                ".144554433221...",
                ".144444333221...",
                "..1111111111....",
                "......1hh1......",
                "......1hh1......",
                "......1hh1......",
                "......1hh1......",
                "......1hh1......",
                "......1hh1......",
                "......1hh1......",
                "......1111......",
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

        PATTERNS.put("helmet", new String[]{
                "................",
                "................",
                "................",
                "....11111111....",
                "...1455555531...",
                "..145333333321..",
                "..143333333321..",
                "..143322223321..",
                "..1431....1321..",
                "..1431....1321..",
                "..1321....1221..",
                "..111......111..",
                "................",
                "................",
                "................",
                "................"});
        PATTERNS.put("chestplate", new String[]{
                "................",
                "..111......111..",
                ".14531....13541.",
                ".14533111133541.",
                ".14533333333341.",
                "..114333333311..",
                "....14333331....",
                "....14333321....",
                "....14333321....",
                "....14322221....",
                "....14333321....",
                "....13333321....",
                "....11111111....",
                "................",
                "................",
                "................"});
        PATTERNS.put("leggings", new String[]{
                "................",
                "................",
                "....11111111....",
                "....14555541....",
                "....14333321....",
                "....14311321....",
                "....1431.1321...",
                "....1431.1321...",
                "....1431.1321...",
                "....1431.1321...",
                "....1431.1321...",
                "....1321.1221...",
                "....1111.1111...",
                "................",
                "................",
                "................"});
        PATTERNS.put("boots", new String[]{
                "................",
                "................",
                "................",
                "................",
                "................",
                "................",
                "..1111....1111..",
                "..1451....1451..",
                "..1431....1431..",
                "..1431....1431..",
                ".14331...14331..",
                "1443321.1443321.",
                "1333221.1333221.",
                "1111111.1111111.",
                "................",
                "................"});    }

    public static String[] names() {
        java.util.List<String> r = new java.util.ArrayList<>(PATTERNS.keySet());
        r.addAll(java.util.List.of("block", "ore", "crop", "armor_layer_1", "armor_layer_2"));
        for (int rows = 1; rows <= 6; rows++) r.add("menu_" + rows);
        return r.toArray(String[]::new);
    }

    /**
     * One growth stage of a crop: green stalks that grow taller with each stage, with fruit in the base colour on the
     * last stage. Stage runs from 0 to stages - 1.
     */
    public static int[] cropStage(int stage, int stages, int baseArgb) {
        int[] shades = shades(baseArgb);
        int[] px = new int[256];
        stages = Math.max(1, stages);
        stage = Math.max(0, Math.min(stages - 1, stage));
        int dark = 0xFF2E5A1C, mid = 0xFF4C8A2A, light = 0xFF6DB33F;
        int height = Math.min(14, 3 + (stage + 1) * 11 / stages);
        int[] xs = {2, 6, 9, 13};
        for (int s = 0; s < xs.length; s++) {
            int x = xs[s];
            int h = Math.max(2, height - (s % 2 == 0 ? 0 : 1));
            int top = 16 - h;
            for (int y = 15; y >= top; y--) {
                px[y * 16 + x] = y > 12 ? dark : mid;
                if ((y + s) % 3 == 0 && y < 15 && y > top) {
                    int lx = x + ((y / 3 + s) % 2 == 0 ? 1 : -1);
                    if (lx >= 0 && lx < 16) px[y * 16 + lx] = light;
                }
            }
            if (stage == stages - 1 && stages > 1) {
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int fx = x + dx, fy = top + dy;
                        if (fx < 0 || fx > 15 || fy > 15) continue;
                        px[fy * 16 + fx] = dx == 0 && dy == 0 ? shades[4] : dy == 1 ? shades[1] : shades[3];
                    }
                }
            }
        }
        return px;
    }

    /** Width and height of a template's image: 64 x 32 for worn armor layers, 16 x 16 otherwise. */
    public static int[] size(String template) {
        if (menuRows(template) > 0) return new int[]{MenuArt.WIDTH, 114 + 18 * menuRows(template)};
        return template != null && template.startsWith("armor_layer") ? new int[]{64, 32} : new int[]{16, 16};
    }

    /** Rows of a chest menu background template (menu_1 to menu_6), or 0. */
    private static int menuRows(String template) {
        if (template == null || !template.matches("menu_[1-6]")) return 0;
        return template.charAt(5) - '0';
    }

    public static int[] generate(String template, int baseArgb) {
        if (menuRows(template) > 0) return MenuArt.template(menuRows(template), baseArgb);
        int[] shades = shades(baseArgb);
        if (template.startsWith("armor_layer")) return armorLayer(template.endsWith("2") ? 2 : 1, shades);
        int[] px = new int[256];
        switch (template) {
            case "block" -> block(px, shades);
            case "ore" -> ore(px, shades);
            case "crop" -> {
                return cropStage(3, 4, baseArgb);
            }
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

    // ------------------------------------------------------------------ worn armor (64 x 32, vanilla humanoid layout)

    /** Layer 1 covers the head, body, arms and the boot part of the legs; layer 2 the waist and the legs. */
    public static int[] armorLayer(int layer, int[] s) {
        int[] px = new int[64 * 32];
        if (layer == 1) {
            box(px, 0, 0, 8, 8, 8, s, 0, 8, true);
            box(px, 16, 16, 8, 12, 4, s, 0, 12, false);
            box(px, 40, 16, 4, 12, 4, s, 0, 12, false);
            box(px, 0, 16, 4, 12, 4, s, 8, 12, false);
        } else {
            box(px, 16, 16, 8, 12, 4, s, 7, 12, false);
            box(px, 0, 16, 4, 12, 4, s, 0, 10, false);
        }
        return px;
    }

    /**
     * Paints the six faces of a model box whose texture starts at (u, v) and measures dx, dy, dz.
     * Only rows y0 to y1 of the side faces are painted; a visor leaves the middle of the front face open.
     */
    private static void box(int[] px, int u, int v, int dx, int dy, int dz, int[] s, int y0, int y1, boolean visor) {
        if (y0 == 0) face(px, u + dz, v, dx, dz, s, 0, dz, false);
        if (y1 == dy) face(px, u + dz + dx, v, dx, dz, s, 0, dz, false);
        face(px, u, v + dz, dz, dy, s, y0, y1, false);
        face(px, u + dz, v + dz, dx, dy, s, y0, y1, visor);
        face(px, u + dz + dx, v + dz, dz, dy, s, y0, y1, false);
        face(px, u + dz + dx + dz, v + dz, dx, dy, s, y0, y1, false);
        if (y0 > 0) {
            // a bright rim where a partial piece (boots) starts
            for (int x = u; x < u + 2 * (dx + dz); x++) px[(v + dz + y0) * 64 + x] = s[4];
        }
    }

    private static void face(int[] px, int x0, int y0, int w, int h, int[] s, int from, int to, boolean visor) {
        for (int y = from; y < to; y++) {
            for (int x = 0; x < w; x++) {
                if (visor && y >= 3 && x > 0 && x < w - 1 && y < h - 1) continue;
                int c;
                if (y == from || x == 0) c = s[3];
                else if (y == to - 1 || x == w - 1) c = s[1];
                else c = hash(x0 + x, y0 + y) % 5 == 0 ? s[3] : s[2];
                px[(y0 + y) * 64 + x0 + x] = c;
            }
        }
    }
}
