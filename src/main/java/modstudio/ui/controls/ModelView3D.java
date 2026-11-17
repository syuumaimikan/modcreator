package modstudio.ui.controls;

import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import modstudio.core.Elements.Cube;
import modstudio.core.Elements.Face;
import modstudio.core.Elements.Model;
import modstudio.core.Models;
import modstudio.core.Rig;
import modstudio.core.TextureAsset;
import modstudio.ui.Ui;
import modstudio.ui.Workspace;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Interactive 3D preview of a cuboid model. Left drag orbits, right or middle drag pans, the wheel zooms and a
 * click selects a cube. Coordinates follow Minecraft model space (0..16, y up) centred on the block.
 */
public final class ModelView3D extends Pane {
    private final Workspace ws;
    private final Group world = new Group();
    private final Group content = new Group();
    private final Group overlay = new Group();
    private final SubScene sub;
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final Rotate yaw = new Rotate(-35, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(-28, Rotate.X_AXIS);
    private final Translate pan = new Translate(0, 0, 0);
    private final Translate dist = new Translate(0, 0, -58);
    private final Map<String, PhongMaterial> materials = new HashMap<>();
    private Model model;
    private int selected = -1;
    private boolean showGrid = true;
    private IntConsumer onSelect = i -> {};
    /** Bone matrices in model space (animation preview); null shows the model at rest. */
    private Map<String, double[]> pose;
    private final Map<String, Affine> boneTransforms = new HashMap<>();
    private String selectedBone = "";
    private double lastX, lastY, pressX, pressY;

    public ModelView3D(Workspace ws, double w, double h, boolean interactive) {
        this.ws = ws;
        setPrefSize(w, h);
        setMinSize(120, 100);
        getStyleClass().add("model-view");
        world.getChildren().addAll(content, overlay);
        AmbientLight light = new AmbientLight(Color.WHITE);
        Group root = new Group(world, light);
        sub = new SubScene(root, w, h, true, SceneAntialiasing.BALANCED);
        sub.setFill(Ui.dark() ? Color.web("#161b22") : Color.web("#eef1f5"));
        camera.setNearClip(0.1);
        camera.setFarClip(5000);
        camera.setFieldOfView(35);
        camera.getTransforms().addAll(pan, yaw, pitch, dist);
        sub.setCamera(camera);
        sub.widthProperty().bind(widthProperty());
        sub.heightProperty().bind(heightProperty());
        getChildren().add(sub);
        if (interactive) installMouse();
    }

    public void setOnSelect(IntConsumer c) {
        onSelect = c == null ? i -> {} : c;
    }

    public void setShowGrid(boolean b) {
        showGrid = b;
        rebuild();
    }

    public void setModel(Model m) {
        boolean fresh = m != model;
        this.model = m;
        if (fresh) fit();
        rebuild();
    }

    /** Scene point the camera orbits and its distance, chosen so the whole model is in view. */
    private double homeX, homeY, homeZ, homeDist = -58;

    private void fit() {
        homeX = homeY = homeZ = 0;
        homeDist = -58;
        if (model == null || model.cubes.isEmpty()) return;
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Cube c : model.cubes) {
            for (int i = 0; i < 3; i++) {
                lo[i] = Math.min(lo[i], Math.min(c.from[i], c.to[i]));
                hi[i] = Math.max(hi[i], Math.max(c.from[i], c.to[i]));
            }
        }
        double size = Math.max(hi[0] - lo[0], Math.max(hi[1] - lo[1], hi[2] - lo[2]));
        if (size <= 16 && lo[0] >= 0 && lo[1] >= 0 && lo[2] >= 0 && hi[0] <= 16 && hi[1] <= 16 && hi[2] <= 16) return;
        double[] c = fx((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2);
        homeX = c[0];
        homeY = c[1];
        homeZ = c[2];
        homeDist = -Math.max(58, size * 3.4);
        pan.setX(homeX);
        pan.setY(homeY);
        pan.setZ(homeZ);
        dist.setZ(homeDist);
    }

    public void setSelected(int i) {
        selected = i;
        rebuildOverlay();
    }

    /** Bone highlighted with its pivot marker. */
    public void setSelectedBone(String bone) {
        selectedBone = bone == null ? "" : bone;
        rebuildOverlay();
    }

    /** Moves the bones without rebuilding the meshes. */
    public void setPose(Map<String, double[]> p) {
        pose = p;
        for (var e : boneTransforms.entrySet()) e.getValue().setToTransform(sceneAffine(bonePose(e.getKey())));
        rebuildOverlay();
    }

    private double[] bonePose(String bone) {
        double[] m = pose == null ? null : pose.get(bone);
        return m == null ? Rig.identity() : m;
    }

    /** A model space matrix expressed in scene space: F * m * F^-1 with F = diag(1,-1,-1) * T(-8,-8,-8). */
    private static Affine sceneAffine(double[] m) {
        double[] f = Rig.mul(Rig.scale(1, -1, -1), Rig.translate(-8, -8, -8));
        double[] fi = Rig.mul(Rig.translate(8, 8, 8), Rig.scale(1, -1, -1));
        double[] s = Rig.mul(f, m, fi);
        return new Affine(s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7], s[8], s[9], s[10], s[11]);
    }

    /** Camera presets: front, side, top, iso. */
    public void view(String preset) {
        pan.setX(homeX);
        pan.setY(homeY);
        pan.setZ(homeZ);
        switch (preset) {
            case "front" -> {
                yaw.setAngle(0);
                pitch.setAngle(0);
            }
            case "side" -> {
                yaw.setAngle(-90);
                pitch.setAngle(0);
            }
            case "top" -> {
                yaw.setAngle(0);
                pitch.setAngle(-89.9);
            }
            default -> {
                yaw.setAngle(-35);
                pitch.setAngle(-28);
            }
        }
        dist.setZ(homeDist);
    }

    private void installMouse() {
        sub.setOnMousePressed(e -> {
            lastX = pressX = e.getSceneX();
            lastY = pressY = e.getSceneY();
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
        sub.setOnScroll(e -> dist.setZ(Math.max(-400, Math.min(-8, dist.getZ() + e.getDeltaY() * 0.08))));
        sub.setOnMouseClicked(e -> {
            if (Math.abs(e.getSceneX() - pressX) > 3 || Math.abs(e.getSceneY() - pressY) > 3) return;
            PickResult pr = e.getPickResult();
            Node n = pr == null ? null : pr.getIntersectedNode();
            while (n != null && !(n.getUserData() instanceof Integer)) n = n.getParent();
            int idx = n == null ? -1 : (Integer) n.getUserData();
            onSelect.accept(idx);
        });
    }

    // ------------------------------------------------------------------ building

    /** Minecraft model space to scene space: centred, y up becomes y down, z flipped to keep handedness. */
    private static double[] fx(double x, double y, double z) {
        return new double[]{x - 8, 8 - y, 8 - z};
    }

    public void rebuild() {
        materials.clear();
        content.getChildren().clear();
        boneTransforms.clear();
        if (model != null) {
            boolean rig = Rig.animated(model);
            Map<String, Group> groups = new HashMap<>();
            if (rig) {
                for (var b : Rig.ordered(model)) {
                    Group bg = new Group();
                    Affine a = sceneAffine(bonePose(b.name));
                    bg.getTransforms().add(a);
                    boneTransforms.put(b.name, a);
                    groups.put(b.name, bg);
                    content.getChildren().add(bg);
                }
            }
            for (int i = 0; i < model.cubes.size(); i++) {
                Cube c = model.cubes.get(i);
                Group g = cubeNode(c);
                g.setUserData(i);
                Group parent = rig ? groups.get(Rig.boneOf(model, c)) : null;
                (parent == null ? content : parent).getChildren().add(g);
            }
        }
        rebuildOverlay();
    }

    private Group cubeNode(Cube c) {
        Group g = new Group();
        double x1 = Math.min(c.from[0], c.to[0]), y1 = Math.min(c.from[1], c.to[1]), z1 = Math.min(c.from[2], c.to[2]);
        double x2 = Math.max(c.from[0], c.to[0]), y2 = Math.max(c.from[1], c.to[1]), z2 = Math.max(c.from[2], c.to[2]);
        // A degenerate cube (one axis of size 0) is a flat plane; give it a hair of thickness so both sides render.
        for (String dir : Cube.FACES) {
            Face f = c.face(dir);
            if (!f.enabled) continue;
            double[][] q = switch (dir) {
                case "north" -> new double[][]{{x2, y2, z1}, {x1, y2, z1}, {x1, y1, z1}, {x2, y1, z1}};
                case "south" -> new double[][]{{x1, y2, z2}, {x2, y2, z2}, {x2, y1, z2}, {x1, y1, z2}};
                case "west" -> new double[][]{{x1, y2, z1}, {x1, y2, z2}, {x1, y1, z2}, {x1, y1, z1}};
                case "east" -> new double[][]{{x2, y2, z2}, {x2, y2, z1}, {x2, y1, z1}, {x2, y1, z2}};
                case "up" -> new double[][]{{x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}};
                default -> new double[][]{{x1, y1, z2}, {x2, y1, z2}, {x2, y1, z1}, {x1, y1, z1}};
            };
            double[] uv = Models.uv(c, dir);
            float[][] uvc = {
                    {(float) (uv[0] / 16), (float) (uv[1] / 16)}, {(float) (uv[2] / 16), (float) (uv[1] / 16)},
                    {(float) (uv[2] / 16), (float) (uv[3] / 16)}, {(float) (uv[0] / 16), (float) (uv[3] / 16)}};
            int rot = ((f.rotation / 90) % 4 + 4) % 4;
            TriangleMesh mesh = new TriangleMesh();
            for (double[] p : q) {
                double[] s = fx(p[0], p[1], p[2]);
                mesh.getPoints().addAll((float) s[0], (float) s[1], (float) s[2]);
            }
            for (int i = 0; i < 4; i++) {
                float[] t = uvc[(i - rot + 4) % 4];
                mesh.getTexCoords().addAll(t[0], t[1]);
            }
            mesh.getFaces().addAll(0, 0, 1, 1, 2, 2, 0, 0, 2, 2, 3, 3);
            MeshView mv = new MeshView(mesh);
            mv.setCullFace(CullFace.NONE);
            double shade = !c.shade ? 1.0 : switch (dir) {
                case "up" -> 1.0;
                case "down" -> 0.55;
                case "north", "south" -> 0.82;
                default -> 0.66;
            };
            mv.setMaterial(material(f.texture, shade));
            g.getChildren().add(mv);
        }
        if (c.rotation != 0) {
            double[] o = fx(c.origin[0], c.origin[1], c.origin[2]);
            // The scene is the model space turned 180 degrees around x, which reverses rotations around y and z.
            Point3D axis = switch (c.axis) {
                case "x" -> Rotate.X_AXIS;
                case "z" -> Rotate.Z_AXIS;
                default -> Rotate.Y_AXIS;
            };
            double angle = c.axis.equals("x") ? c.rotation : -c.rotation;
            g.getTransforms().add(new Rotate(angle, o[0], o[1], o[2], axis));
        }
        return g;
    }

    private PhongMaterial material(String textureKey, double shade) {
        String key = textureKey + "@" + shade;
        PhongMaterial cached = materials.get(key);
        if (cached != null) return cached;
        PhongMaterial m = new PhongMaterial();
        m.setDiffuseColor(Color.gray(shade));
        m.setSpecularColor(Color.TRANSPARENT);
        Image img = image(textureKey);
        if (img != null) m.setDiffuseMap(img);
        else m.setDiffuseColor(Color.hsb(210, 0.08, 0.62 * shade + 0.2));
        materials.put(key, m);
        return m;
    }

    private final Map<String, Image> images = new HashMap<>();

    /** Upscaled (nearest neighbour) image of the texture bound to a model texture variable. */
    private Image image(String textureKey) {
        if (model == null) return null;
        String value = model.textures.get(textureKey == null || textureKey.isBlank() ? "0" : textureKey);
        if (value == null || value.isBlank()) value = model.textures.values().stream().filter(v -> v != null && !v.isBlank()).findFirst().orElse(null);
        if (value == null) return null;
        TextureAsset t = ws.project.texture(value);
        if (t == null) return BlockScene3D.image(ws, value, 256);
        String cacheKey = t.id + ":" + t.png.hashCode() + ":" + t.frameTime;
        Image img = images.get(cacheKey);
        if (img == null) {
            int[] px = t.firstFrame();
            int fh = t.frameHeight();
            int scale = Math.max(1, Math.min(16, 256 / Math.max(t.width, fh)));
            img = Ui.pixelImage(px, t.width, fh, Math.max(t.width, fh) * scale);
            images.put(cacheKey, img);
        }
        return img;
    }

    private void rebuildOverlay() {
        overlay.getChildren().clear();
        if (showGrid) {
            PhongMaterial line = new PhongMaterial(Ui.dark() ? Color.web("#3d4450") : Color.web("#c4cad3"));
            PhongMaterial edge = new PhongMaterial(Ui.dark() ? Color.web("#6e7681") : Color.web("#8c959f"));
            for (int i = 0; i <= 16; i++) {
                boolean border = i == 0 || i == 16;
                double t = border ? 0.08 : 0.03;
                overlay.getChildren().add(line(fx(i, 0, 0), fx(i, 0, 16), t, border ? edge : line));
                overlay.getChildren().add(line(fx(0, 0, i), fx(16, 0, i), t, border ? edge : line));
            }
            // North marker: a short bar outside the grid on the north side.
            PhongMaterial north = new PhongMaterial(Color.web("#cf222e"));
            overlay.getChildren().add(line(fx(7, 0, -1.5), fx(9, 0, -1.5), 0.25, north));
        }
        if (model != null && selected >= 0 && selected < model.cubes.size()) {
            Cube c = model.cubes.get(selected);
            PhongMaterial sel = new PhongMaterial(Color.web("#2f81f7"));
            double x1 = Math.min(c.from[0], c.to[0]) - 0.05, y1 = Math.min(c.from[1], c.to[1]) - 0.05, z1 = Math.min(c.from[2], c.to[2]) - 0.05;
            double x2 = Math.max(c.from[0], c.to[0]) + 0.05, y2 = Math.max(c.from[1], c.to[1]) + 0.05, z2 = Math.max(c.from[2], c.to[2]) + 0.05;
            double[][] p = {{x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2}, {x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}};
            int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
            Group box = new Group();
            for (int[] e : edges) box.getChildren().add(line(fx(p[e[0]][0], p[e[0]][1], p[e[0]][2]), fx(p[e[1]][0], p[e[1]][1], p[e[1]][2]), 0.1, sel));
            if (c.rotation != 0) {
                double[] o = fx(c.origin[0], c.origin[1], c.origin[2]);
                Point3D axis = switch (c.axis) {
                    case "x" -> Rotate.X_AXIS;
                    case "z" -> Rotate.Z_AXIS;
                    default -> Rotate.Y_AXIS;
                };
                box.getTransforms().add(new Rotate(c.axis.equals("x") ? c.rotation : -c.rotation, o[0], o[1], o[2], axis));
            }
            box.setMouseTransparent(true);
            if (Rig.animated(model)) {
                Group moved = new Group(box);
                moved.getTransforms().add(sceneAffine(bonePose(Rig.boneOf(model, c))));
                overlay.getChildren().add(moved);
            } else {
                overlay.getChildren().add(box);
            }
        }
        if (Rig.animated(model)) {
            // Pivot markers: a small cube at each bone pivot, the selected bone in accent colour.
            PhongMaterial pin = new PhongMaterial(Color.web("#d29922"));
            PhongMaterial pinSel = new PhongMaterial(Color.web("#2f81f7"));
            Map<String, double[]> world = pose != null ? pose : Rig.pose(model, null, 0);
            for (var b : model.bones) {
                double[] m = world.getOrDefault(b.name, Rig.identity());
                double[] at = Rig.apply(m, b.pivot[0], b.pivot[1], b.pivot[2]);
                double[] s = fx(at[0], at[1], at[2]);
                boolean on = b.name.equals(selectedBone);
                Box mark = new Box(on ? 0.9 : 0.6, on ? 0.9 : 0.6, on ? 0.9 : 0.6);
                mark.setMaterial(on ? pinSel : pin);
                mark.setTranslateX(s[0]);
                mark.setTranslateY(s[1]);
                mark.setTranslateZ(s[2]);
                mark.setMouseTransparent(true);
                overlay.getChildren().add(mark);
            }
        }
        overlay.setMouseTransparent(true);
    }

    /** A thin box between two points along one axis. */
    private static Node line(double[] a, double[] b, double t, PhongMaterial m) {
        double dx = Math.abs(b[0] - a[0]), dy = Math.abs(b[1] - a[1]), dz = Math.abs(b[2] - a[2]);
        Box box = new Box(Math.max(dx, t), Math.max(dy, t), Math.max(dz, t));
        box.setTranslateX((a[0] + b[0]) / 2);
        box.setTranslateY((a[1] + b[1]) / 2);
        box.setTranslateZ((a[2] + b[2]) / 2);
        box.setMaterial(m);
        return box;
    }
}
