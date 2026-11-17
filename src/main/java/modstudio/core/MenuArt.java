package modstudio.core;

import modstudio.core.Systems.Menu;

/**
 * Background images of chest menus. The image covers the whole chest screen (menu and player inventory) and is
 * drawn by the client as a font glyph in the menu title, so it works without client code. The 16x16 inside of
 * every slot stays transparent, which keeps items visible however the client orders the title and the items.
 */
public final class MenuArt {
    private MenuArt() {}

    public static final int WIDTH = 176;

    public static int height(Menu m) {
        return 114 + 18 * rows(m);
    }

    public static int rows(Menu m) {
        return Math.max(1, Math.min(6, m.rows));
    }

    /** True when the menu shows a background image (chest menus only: other screens centre their titles). */
    public static boolean active(Project p, Menu m) {
        if (!m.layout.equals("chest")) return false;
        return switch (m.background) {
            case "panel" -> true;
            case "texture" -> p.texture(m.backgroundTexture) != null;
            default -> false;
        };
    }

    /** Pixels (ARGB, WIDTH x height) of the background, or null when the menu has none. */
    public static int[] image(Project p, Menu m) {
        if (!active(p, m)) return null;
        int w = WIDTH, h = height(m);
        int[] px;
        if (m.background.equals("texture")) {
            TextureAsset t = p.texture(m.backgroundTexture);
            int[] src = t.firstFrame();
            px = t.width == w && t.frameHeight() == h ? src.clone() : Images.resize(src, t.width, t.frameHeight(), w, h);
        } else {
            px = panel(rows(m), rgb(m.panelColor, 0xFFC6C6C6), rgb(m.frameColor, 0xFF555555));
        }
        for (int[] s : slots(m)) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) px[(s[1] + y) * w + s[0] + x] = 0;
        }
        return px;
    }

    /** Top-left corners of the 16x16 item areas: menu slots, then the player inventory and hotbar. */
    public static int[][] slots(Menu m) {
        int rows = rows(m);
        int[][] r = new int[rows * 9 + 36][];
        int k = 0;
        for (int row = 0; row < rows; row++) for (int c = 0; c < 9; c++) r[k++] = new int[]{8 + c * 18, 18 + row * 18};
        int inv = 18 * rows + 31;
        for (int row = 0; row < 3; row++) for (int c = 0; c < 9; c++) r[k++] = new int[]{8 + c * 18, inv + row * 18};
        for (int c = 0; c < 9; c++) r[k++] = new int[]{8 + c * 18, 18 * rows + 89};
        return r;
    }

    private static int rgb(String hex, int def) {
        try {
            return 0xFF000000 | Integer.parseInt(hex.trim().replace("#", ""), 16);
        } catch (Exception e) {
            return def;
        }
    }

    private static int shade(int c, double k) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        if (k >= 1) {
            r = (int) Math.min(255, r + (255 - r) * (k - 1));
            g = (int) Math.min(255, g + (255 - g) * (k - 1));
            b = (int) Math.min(255, b + (255 - b) * (k - 1));
        } else {
            r = (int) (r * k);
            g = (int) (g * k);
            b = (int) (b * k);
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Starting image for a background texture of a chest menu with the given rows (texture template menu_N). */
    public static int[] template(int rows, int baseArgb) {
        return panel(rows, 0xFF000000 | baseArgb, shade(0xFF000000 | baseArgb, 0.35));
    }

    /**
     * Step on which a slot appears in an opening animation (slots: one by one, rows, columns, center: rings from the
     * middle). The generated menus use the same order.
     */
    public static int order(String reveal, int i, int size, int cols) {
        int col = i % cols, row = i / cols, rows = (size + cols - 1) / cols;
        return switch (reveal) {
            case "rows" -> row;
            case "columns" -> col;
            case "center" -> Math.max(Math.abs(2 * col - (cols - 1)), Math.abs(2 * row - (rows - 1))) / 2;
            default -> i;
        };
    }

    public static final java.util.List<String> REVEALS = java.util.List.of("none", "slots", "rows", "columns", "center");
    public static final java.util.List<String> BACKGROUNDS = java.util.List.of("none", "panel", "texture");

    /** A bevelled panel in the menu colours with a frame, a title band and framed slots. */
    private static int[] panel(int rows, int base, int frame) {
        int w = WIDTH, h = 114 + 18 * rows;
        Menu m = new Menu();
        m.rows = rows;
        int[] px = new int[w * h];
        int light = shade(base, 1.45), dark = shade(base, 0.6), slotBg = shade(base, 0.7);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean corner = (x < 2 || x > w - 3) && (y < 2 || y > h - 3);
                if (corner && (x == 0 || x == w - 1) && (y == 0 || y == h - 1)) continue;
                int c;
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) c = frame;
                else if (x <= 2 || y <= 2) c = light;
                else if (x >= w - 3 || y >= h - 3) c = dark;
                else c = base;
                px[y * w + x] = c;
            }
        }
        // a thin accent line under the title and above the player inventory
        int split = 18 * rows(m) + 17;
        for (int x = 7; x < w - 7; x++) {
            px[15 * w + x] = frame;
            px[split * w + x] = shade(base, 0.8);
        }
        for (int[] s : slots(m)) {
            int sx = s[0] - 1, sy = s[1] - 1;
            for (int y = 0; y < 18; y++) {
                for (int x = 0; x < 18; x++) {
                    int c = x == 17 || y == 17 ? shade(base, 1.6) : x == 0 || y == 0 ? dark : slotBg;
                    px[(sy + y) * w + sx + x] = c;
                }
            }
        }
        return px;
    }

    /** Horizontal advance of the glyph: Minecraft measures bitmap glyphs to their last visible column, plus one. */
    public static int advance(int[] px, int w, int h) {
        int last = -1;
        for (int x = w - 1; x >= 0 && last < 0; x--) {
            for (int y = 0; y < h; y++) {
                if ((px[y * w + x] >>> 24) != 0) {
                    last = x;
                    break;
                }
            }
        }
        return last + 2;
    }

    /** Private use characters of the menu font: two spaces and the background glyph of the n-th menu. */
    public static char glyph(int n) {
        return (char) (0xE000 + n);
    }

    public static final char SHIFT_LEFT = '';

    public static char shiftBack(int n) {
        return (char) (0xF810 + n);
    }

    /** The title prefix that draws the background: move to the screen corner, draw, move back to the title. */
    public static String prefix(int n) {
        return "" + SHIFT_LEFT + glyph(n) + shiftBack(n);
    }
}
