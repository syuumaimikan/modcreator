package modstudio.ui.controls;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import modstudio.core.Elements;
import modstudio.core.TextureAsset;
import modstudio.ui.Ui;
import modstudio.ui.Workspace;

/** Isometric preview of a block using its face textures. */
public final class BlockPreview extends StackPane {
    private final Canvas canvas = new Canvas(150, 150);
    private final Workspace ws;

    public BlockPreview(Workspace ws) {
        this.ws = ws;
        getChildren().add(canvas);
        getStyleClass().add("block-preview");
        setMinSize(170, 170);
        setMaxSize(170, 170);
    }

    public void draw(Elements.Block b) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        g.setImageSmoothing(false);
        Image top = img(b.face("top"));
        Image side = img(b.face("side"));
        if (b.shape.equals("cross")) {
            if (side != null) g.drawImage(side, 27, 27, 96, 96);
            return;
        }
        if (b.isCrop()) {
            Image ripe = b.stageTextures.isEmpty() ? null : img(b.stageTextures.get(b.stageTextures.size() - 1));
            if (ripe != null) g.drawImage(ripe, 27, 27, 96, 96);
            return;
        }
        double cx = 75, w = 56, y0 = 14, hgt = switch (b.shape) {
            case "slab", "carpet" -> b.shape.equals("carpet") ? 8 : 32;
            case "pressure_plate", "button" -> 12;
            default -> 64;
        };
        if (hgt < 64) y0 += (64 - hgt) * 0.7;
        // top: A(cx,y0) B(cx+w,y0+w/2) D(cx-w,y0+w/2)
        face(g, top, cx, y0, w / 16, w / 32, -w / 16, w / 32, 1.0);
        // left: from D(cx-w, y0+w/2) along +x to C(cx, y0+w), down by hgt
        face(g, side, cx - w, y0 + w / 2, w / 16, w / 32, 0, hgt / 16, 0.78);
        // right: from C(cx, y0+w) to B(cx+w, y0+w/2), down by hgt
        face(g, side, cx, y0 + w, w / 16, -w / 32, 0, hgt / 16, 0.62);
        g.setTransform(1, 0, 0, 1, 0, 0);
    }

    private void face(GraphicsContext g, Image img, double tx, double ty, double ux, double uy, double vx, double vy, double light) {
        g.setTransform(ux, uy, vx, vy, tx, ty);
        if (img != null) {
            g.drawImage(img, 0, 0, 16, 16);
        } else {
            g.setFill(Ui.dark() ? Color.web("#30363d") : Color.web("#d0d7de"));
            g.fillRect(0, 0, 16, 16);
        }
        if (light < 1) {
            g.setFill(Color.rgb(0, 0, 0, 1 - light));
            g.fillRect(0, 0, 16, 16);
        }
        g.setStroke(Color.rgb(0, 0, 0, 0.25));
        g.setLineWidth(0.08);
        g.strokeRect(0, 0, 16, 16);
    }

    private Image img(String textureId) {
        TextureAsset t = ws.project.texture(textureId);
        if (t == null) return null;
        return ws.texture(textureId, 16 * Math.max(1, 16 / Math.max(1, t.width)));
    }
}
