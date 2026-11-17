package modstudio.core;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/** PNG encoding helpers that work without a running UI toolkit. */
public final class Images {
    private Images() {}

    public static String encode(int[] argb, int w, int h) {
        return Base64.getEncoder().encodeToString(png(argb, w, h));
    }

    public static byte[] png(int[] argb, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, argb, 0, w);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    /** Decodes base64 PNG into ARGB pixels; wh receives width and height. */
    public static int[] decode(String b64, int[] wh) {
        try {
            return read(decodeBase64(b64), wh);
        } catch (Exception e) {
            return null;
        }
    }

    public static int[] read(byte[] bytes, int[] wh) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) throw new IOException("Unsupported image");
        int w = img.getWidth(), h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        wh[0] = w;
        wh[1] = h;
        return px;
    }

    public static byte[] decodeBase64(String b64) {
        return Base64.getDecoder().decode(b64);
    }

    /** Nearest-neighbour resize. */
    public static int[] resize(int[] src, int sw, int sh, int dw, int dh) {
        int[] r = new int[dw * dh];
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                r[y * dw + x] = src[(y * sh / dh) * sw + (x * sw / dw)];
            }
        }
        return r;
    }
}
