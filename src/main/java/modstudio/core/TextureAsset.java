package modstudio.core;

/** A PNG texture stored inside the project (base64 encoded). */
public final class TextureAsset {
    public String id = Ids.uid();
    public String name = "texture";
    public int width = 16;
    public int height = 16;
    public String png = "";
    /** Ticks per animation frame; 0 means a still texture. Frames are square and stacked from top to bottom. */
    public int frameTime = 0;
    /** Blend smoothly between animation frames. */
    public boolean interpolate = false;

    public static TextureAsset of(String name, int[] argb, int w, int h) {
        TextureAsset t = new TextureAsset();
        t.name = name;
        t.width = w;
        t.height = h;
        t.png = Images.encode(argb, w, h);
        return t;
    }

    public int[] pixels() {
        if (png == null || png.isEmpty()) return new int[width * height];
        int[] wh = new int[2];
        int[] px = Images.decode(png, wh);
        if (px == null) return new int[width * height];
        width = wh[0];
        height = wh[1];
        return px;
    }

    /** Number of animation frames (1 for a still texture). */
    public int frames() {
        if (frameTime <= 0 || width <= 0 || height <= width || height % width != 0) return 1;
        return height / width;
    }

    public boolean animated() {
        return frames() > 1;
    }

    /** The texture as the game shows it in a still preview: the first frame of an animation. */
    public int[] firstFrame() {
        int[] px = pixels();
        if (!animated()) return px;
        return java.util.Arrays.copyOf(px, width * width);
    }

    /** Height of one frame. */
    public int frameHeight() {
        return animated() ? width : height;
    }

    public byte[] bytes() {
        if (png == null || png.isEmpty()) {
            return Images.decodeBase64(Images.encode(new int[width * height], width, height));
        }
        return Images.decodeBase64(png);
    }
}
