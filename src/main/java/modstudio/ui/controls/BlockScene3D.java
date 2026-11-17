package modstudio.ui.controls;

import javafx.geometry.Point3D;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import modstudio.core.BlockModels;
import modstudio.core.VanillaAssets;
import modstudio.ui.Ui;
import modstudio.ui.Workspace;

import java.io.ByteArrayInputStream;
import java.util.*;

/**
 * A rotatable 3D view of placed blocks drawn with their real models and textures. Faces against full opaque
 * neighbours are left out and faces sharing a texture go into one mesh, so large structures stay quick.
 */
public final class BlockScene3D extends Pane {
    /** A block at a cell. */
    public record Placed(int x, int y, int z, BlockModels.Shape shape) {}

    private final Workspace ws;
    private final Group content = new Group();
    private final SubScene sub;
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final Rotate yaw = new Rotate(-35, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(-30, Rotate.X_AXIS);
    private final Translate pan = new Translate(0, 0, 0);
    private final Translate dist = new Translate(0, 0, -80);
    private double lastX, lastY;
    private int fitW = -1, fitH = -1, fitD = -1;

    public BlockScene3D(Workspace ws, double w, double h) {
        this.ws = ws;
        setPrefSize(w, h);
        setMinSize(160, 140);
        getStyleClass().add("model-view");
        Group root = new Group(content, new AmbientLight(Color.WHITE));
        sub = new SubScene(root, w, h, true, SceneAntialiasing.BALANCED);
        sub.setFill(Ui.dark() ? Color.web("#161b22") : Color.web("#eef1f5"));
        camera.setNearClip(0.5);
        camera.setFarClip(20000);
        camera.setFieldOfView(35);
        camera.getTransforms().addAll(pan, yaw, pitch, dist);
        sub.setCamera(camera);
        sub.widthProperty().bind(widthProperty());
        sub.heightProperty().bind(heightProperty());
        getChildren().add(sub);
        installMouse();
    }

    /** Shows the blocks of a w x h x d volume; the camera is fitted again when the size changes. */
    public void show(List<Placed> blocks, int w, int h, int d) {
        Map<Long, BlockModels.Shape> at = new HashMap<>();
        for (Placed p : blocks) at.put(key(p.x(), p.y(), p.z()), p.shape());
        Map<String, TriangleMesh> meshes = new LinkedHashMap<>();
        Map<String, PhongMaterial> mats = new HashMap<>();
        for (Placed p : blocks) {
            for (BlockModels.Quad q : p.shape().quads()) {
                if (!q.cull().isEmpty()) {
                    int[] o = offset(q.cull());
                    BlockModels.Shape n = at.get(key(p.x() + o[0], p.y() + o[1], p.z() + o[2]));
                    if (n != null && n.full() && n.opaque()) continue;
                }
                String mk = q.texture() + "|" + q.shade() + "|" + q.tint();
                TriangleMesh m = meshes.computeIfAbsent(mk, k -> new TriangleMesh());
                mats.computeIfAbsent(mk, k -> material(q));
                int base = m.getPoints().size() / 3;
                for (int i = 0; i < 4; i++) {
                    double x = p.x() * 16 + q.pos()[i * 3], y = p.y() * 16 + q.pos()[i * 3 + 1], z = p.z() * 16 + q.pos()[i * 3 + 2];
                    // scene space: y down, z flipped so the handedness stays as in game
                    m.getPoints().addAll((float) x, (float) -y, (float) -z);
                    m.getTexCoords().addAll((float) q.uv()[i * 2], (float) q.uv()[i * 2 + 1]);
                }
                m.getFaces().addAll(base, base, base + 1, base + 1, base + 2, base + 2, base, base, base + 2, base + 2, base + 3, base + 3);
            }
        }
        content.getChildren().clear();
        for (var e : meshes.entrySet()) {
            MeshView mv = new MeshView(e.getValue());
            mv.setCullFace(CullFace.NONE);
            mv.setMaterial(mats.get(e.getKey()));
            content.getChildren().add(mv);
        }
        if (w != fitW || h != fitH || d != fitD) {
            fitW = w;
            fitH = h;
            fitD = d;
            home();
        }
    }

    /** Back to the starting view of the whole volume. */
    public void home() {
        pan.setX(fitW * 8.0);
        pan.setY(-fitH * 8.0);
        pan.setZ(-fitD * 8.0);
        yaw.setAngle(-35);
        pitch.setAngle(-30);
        double size = Math.max(fitW, Math.max(fitH, fitD)) * 16.0;
        dist.setZ(-Math.max(60, size * 2.6));
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 4096) << 40) | ((long) (y + 4096) << 20) | (z + 4096);
    }

    private static int[] offset(String dir) {
        return switch (dir) {
            case "north" -> new int[]{0, 0, -1};
            case "south" -> new int[]{0, 0, 1};
            case "west" -> new int[]{-1, 0, 0};
            case "east" -> new int[]{1, 0, 0};
            case "up" -> new int[]{0, 1, 0};
            default -> new int[]{0, -1, 0};
        };
    }

    private PhongMaterial material(BlockModels.Quad q) {
        PhongMaterial m = new PhongMaterial();
        m.setSpecularColor(Color.TRANSPARENT);
        Color tint = q.tint() < 0 ? Color.WHITE : Color.rgb((q.tint() >> 16) & 255, (q.tint() >> 8) & 255, q.tint() & 255);
        if (q.texture().startsWith("color:")) {
            Color c = VoxelEditor.color(q.texture().substring(6));
            m.setDiffuseColor(c.deriveColor(0, 1, q.shade(), 1));
            return m;
        }
        Image img = image(ws, q.texture(), 128);
        m.setDiffuseColor(Color.color(tint.getRed() * q.shade(), tint.getGreen() * q.shade(), tint.getBlue() * q.shade()));
        if (img != null) m.setDiffuseMap(img);
        else m.setDiffuseColor(VoxelEditor.color(q.texture()).deriveColor(0, 1, q.shade(), 1));
        return m;
    }

    private void installMouse() {
        sub.setOnMousePressed(e -> {
            lastX = e.getSceneX();
            lastY = e.getSceneY();
        });
        sub.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - lastX, dy = e.getSceneY() - lastY;
            lastX = e.getSceneX();
            lastY = e.getSceneY();
            if (e.getButton() == MouseButton.PRIMARY) {
                yaw.setAngle(yaw.getAngle() + dx * 0.5);
                pitch.setAngle(Math.max(-89.9, Math.min(89.9, pitch.getAngle() - dy * 0.5)));
            } else {
                double k = -dist.getZ() / 600.0;
                Point3D right = new Point3D(Math.cos(Math.toRadians(yaw.getAngle())), 0, -Math.sin(Math.toRadians(yaw.getAngle())));
                pan.setX(pan.getX() - right.getX() * dx * k);
                pan.setZ(pan.getZ() - right.getZ() * dx * k);
                pan.setY(pan.getY() - dy * k);
            }
        });
        sub.setOnScroll(e -> dist.setZ(Math.max(-6000, Math.min(-10, dist.getZ() * (e.getDeltaY() > 0 ? 0.9 : 1.1)))));
        sub.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) home();
        });
    }

    // ------------------------------------------------------------------ textures

    private static final Map<String, Optional<Image>> VANILLA = new HashMap<>();

    /**
     * Upscaled image of a project texture or a vanilla texture (minecraft:block/stone) read from a client jar on this
     * computer; animated textures show their first frame. Null when neither has it.
     */
    public static Image image(Workspace ws, String id, int size) {
        if (id == null || id.isBlank()) return null;
        Image own = ws.texture(id, size);
        if (own != null) return own;
        if (!id.contains(":")) return null;
        VanillaAssets va = VanillaAssets.get(ws.project.mc());
        if (va == null) return null;
        String key = va.version + "|" + id + "|" + size;
        return VANILLA.computeIfAbsent(key, k -> {
            byte[] png = va.texture(id);
            if (png == null) return Optional.empty();
            Image src = new Image(new ByteArrayInputStream(png));
            if (src.isError() || src.getWidth() < 1) return Optional.empty();
            int w = (int) src.getWidth(), h = Math.min((int) src.getHeight(), w);
            int[] px = new int[w * h];
            PixelReader r = src.getPixelReader();
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = r.getArgb(x, y);
            return Optional.of(Ui.pixelImage(px, w, h, size));
        }).orElse(null);
    }
}
