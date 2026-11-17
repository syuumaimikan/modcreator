package modstudio.core;

/**
 * Tooltip box pictures drawn from colours in the vanilla layout: 100 x 100 nine-slice sprites (Minecraft 1.21.2+
 * tooltip_style) with a 9 pixel margin around the box, a filled background with cut corners and a one pixel frame
 * whose colour fades from the top to the bottom.
 */
public final class TooltipArt {
    public static final int SIZE = 100;

    private TooltipArt() {}

    /** ARGB from #RRGGBB and an opacity in percent. */
    public static int argb(String hex, int opacityPercent) {
        int rgb;
        try {
            rgb = Integer.parseInt(hex.trim().replace("#", ""), 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            rgb = 0;
        }
        int a = (int) Math.round(Math.max(0, Math.min(100, opacityPercent)) * 2.55);
        return a << 24 | rgb;
    }

    public static int[] background(int argb) {
        int[] px = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean main = x >= 9 && x < 91 && y >= 9 && y < 91;
                boolean edges = (y == 8 || y == 91) && x >= 9 && x < 91 || (x == 8 || x == 91) && y >= 9 && y < 91;
                if (main || edges) px[y * SIZE + x] = argb;
            }
        }
        return px;
    }

    public static int[] frame(int top, int bottom) {
        int[] px = new int[SIZE * SIZE];
        for (int x = 9; x < 91; x++) {
            px[9 * SIZE + x] = top;
            px[90 * SIZE + x] = bottom;
        }
        for (int y = 10; y < 90; y++) {
            int c = lerp(top, bottom, (y - 10) / 79.0);
            px[y * SIZE + 9] = c;
            px[y * SIZE + 90] = c;
        }
        return px;
    }

    public static int lerp(int a, int b, double t) {
        int r = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = a >>> shift & 0xFF, cb = b >>> shift & 0xFF;
            r |= (int) Math.round(ca + (cb - ca) * t) << shift;
        }
        return r;
    }

    /** CSS rgba() for previews. */
    public static String css(int argb) {
        return "rgba(" + (argb >> 16 & 0xFF) + "," + (argb >> 8 & 0xFF) + "," + (argb & 0xFF) + "," + String.format(java.util.Locale.ROOT, "%.3f", (argb >>> 24) / 255.0) + ")";
    }
}
