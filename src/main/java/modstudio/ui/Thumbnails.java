package modstudio.ui;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Affine;
import modstudio.core.*;
import modstudio.ui.controls.BlockScene3D;
import modstudio.ui.controls.VoxelEditor;

import java.util.*;

/**
 * Small pictures of elements for the lists: textures for items and paintings, isometric renders of blocks, models,
 * mobs and structures (with vanilla models from the installed game), and the item a recipe, tab or advancement shows.
 */
public final class Thumbnails {
    private final Workspace ws;
    private final Map<String, Optional<Image>> cache = new HashMap<>();

    public Thumbnails(Workspace ws) {
        this.ws = ws;
    }

    /** A picture of the element, or null when its type icon fits better. */
    public Image of(Element e, int size) {
        String key = e.uid + "|" + size + "|" + ProjectIO.GSON.toJson(e).hashCode();
        Optional<Image> hit = cache.get(key);
        if (hit != null) return hit.orElse(null);
        if (cache.size() > 400) cache.clear();
        Image img;
        try {
            img = make(e, size);
        } catch (RuntimeException ex) {
            img = null;
        }
        cache.put(key, Optional.ofNullable(img));
        return img;
    }

    private BlockModels models() {
        return new BlockModels(ws.project, VanillaAssets.get(ws.project.mc()));
    }

    private Image make(Element e, int size) {
        return switch (e) {
            case Elements.Item it -> {
                Elements.Model m = it.model.isBlank() ? null : model(it.model);
                yield m != null && !m.cubes.isEmpty() ? iso(m, size) : ws.texture(it.texture, size);
            }
            case Elements.Block b -> block(ws.ns() + ":" + b.name, size);
            case Elements.Model m -> m.cubes.isEmpty() ? null : iso(m, size);
            case Systems.Mob m -> {
                Elements.Model rig = m.rig.isBlank() ? null : model(m.rig);
                if (rig == null && !m.appearance.isBlank()) rig = model(m.appearance);
                yield rig != null && !rig.cubes.isEmpty() ? iso(rig, size) : item(m.base + "_spawn_egg", size);
            }
            case Systems.Volume v -> volume(v, size);
            case Elements.Painting p -> ws.texture(p.texture, size);
            case Elements.StatusEffect s -> s.icon.isBlank() ? null : ws.texture(s.icon, size);
            case Elements.Potion po -> potion(po.color, size);
            case Elements.CreativeTab t -> item(t.icon, size);
            case Elements.Advancement a -> item(a.icon, size);
            case Elements.Recipe r -> item(r.result, size);
            case Elements.Enchantment en -> item("minecraft:enchanted_book", size);
            case Elements.Song so -> item("minecraft:music_disc_cat", size);
            case Elements.ArmorMaterial am -> item("minecraft:iron_chestplate", size);
            case Elements.LootTable lt -> {
                for (Elements.LootPool pool : lt.pools) for (Elements.LootEntry en : pool.entries) {
                    if (!en.name.isBlank() && !en.name.startsWith("#")) yield item(en.name, size);
                }
                yield null;
            }
            case Systems.Trader tr -> item("minecraft:emerald", size);
            case Systems.Machine mc -> mc.block.isBlank() ? null : item(mc.block, size);
            case Systems.KeyBind k -> key(Systems.KeyBind.keyLabel(k.key), size);
            default -> null;
        };
    }

    /** A potion bottle with its liquid in the potion's colour, from the installed game's textures. */
    private Image potion(String hex, int size) {
        Image bottle = BlockScene3D.image(ws, "minecraft:item/potion", size);
        Image liquid = BlockScene3D.image(ws, "minecraft:item/potion_overlay", size);
        if (bottle == null || liquid == null) return null;
        Color tint;
        try {
            tint = Color.web(hex.trim());
        } catch (RuntimeException ex) {
            tint = Color.web("#3F76E4");
        }
        int w = (int) Math.min(bottle.getWidth(), liquid.getWidth()), h = (int) Math.min(bottle.getHeight(), liquid.getHeight());
        WritableImage out = new WritableImage(w, h);
        var lr = liquid.getPixelReader();
        var br = bottle.getPixelReader();
        var pw = out.getPixelWriter();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color l = lr.getColor(x, y), b = br.getColor(x, y);
                double la = l.getOpacity(), ba = b.getOpacity();
                double a = ba + la * (1 - ba);
                if (a <= 0) {
                    pw.setColor(x, y, Color.TRANSPARENT);
                    continue;
                }
                double r = (b.getRed() * ba + l.getRed() * tint.getRed() * la * (1 - ba)) / a;
                double g = (b.getGreen() * ba + l.getGreen() * tint.getGreen() * la * (1 - ba)) / a;
                double bl = (b.getBlue() * ba + l.getBlue() * tint.getBlue() * la * (1 - ba)) / a;
                pw.setColor(x, y, new Color(clamp(r), clamp(g), clamp(bl), clamp(a)));
            }
        }
        return out;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private Elements.Model model(String name) {
        String n = Ids.path(Ids.full(name));
        for (Element x : ws.project.elements) if (x instanceof Elements.Model m && m.name.equals(n)) return m;
        return null;
    }

    /**
     * Icon for an item id: the project's own picture, else the vanilla item texture, else the block model drawn
     * isometrically.
     */
    public Image item(String id, int size) {
        if (id == null || id.isBlank() || id.startsWith("#")) return null;
        String full = Ids.full(id.trim());
        int b = full.indexOf('[');
        if (b >= 0) full = full.substring(0, b);
        Image own = ws.itemImage(full, size);
        Element el = ws.project.itemLike(full);
        if (el instanceof Elements.Block) return block(full, size);
        if (own != null) return own;
        Image tex = BlockScene3D.image(ws, Ids.ns(full) + ":item/" + Ids.path(full), size);
        if (tex != null) return tex;
        return block(full, size);
    }

    private Image block(String id, int size) {
        BlockModels.Shape s = models().shape(id);
        if (s.quads().isEmpty()) return null;
        if (s.quads().stream().allMatch(q -> q.texture().startsWith("color:"))) {
            if (VanillaAssets.get(ws.project.mc()) == null || !Ids.full(id).startsWith("minecraft:")) return paint(List.of(new Placed(0, 0, 0, s)), size);
            return null;
        }
        return paint(List.of(new Placed(0, 0, 0, s)), size);
    }

    private Image iso(Elements.Model m, int size) {
        Map<String, String> tex = m.textures;
        String first = tex.values().stream().filter(v -> v != null && !v.isBlank()).findFirst().orElse(null);
        BlockModels.Shape shape = BlockModels.of(m.cubes, k -> {
            String v = tex.get(k == null || k.isBlank() ? "0" : k);
            return v == null || v.isBlank() ? first : v;
        });
        return paint(List.of(new Placed(0, 0, 0, shape)), size);
    }

    private Image volume(Systems.Volume v, int size) {
        v.ensure();
        BlockModels bm = models();
        List<Placed> blocks = new ArrayList<>();
        int max = 0;
        for (int y = 0; y < v.sy; y++)
            for (int z = 0; z < v.sz; z++)
                for (int x = 0; x < v.sx; x++) {
                    int idx = v.get(x, y, z);
                    if (idx < 0 || idx >= v.palette.size()) continue;
                    BlockModels.Shape s = bm.shape(v.palette.get(idx));
                    if (s.quads().isEmpty()) continue;
                    blocks.add(new Placed(x, y, z, s));
                    if (++max > 3000) break;
                }
        return blocks.isEmpty() ? null : paint(blocks, size);
    }

    private Image key(String label, int size) {
        Canvas c = new Canvas(size, size);
        GraphicsContext g = c.getGraphicsContext2D();
        double pad = size * 0.08;
        g.setFill(Ui.dark() ? Color.web("#30363d") : Color.web("#d0d7de"));
        g.fillRoundRect(pad, pad + size * 0.06, size - pad * 2, size - pad * 2, size * 0.25, size * 0.25);
        g.setFill(Ui.dark() ? Color.web("#484f58") : Color.web("#f6f8fa"));
        g.fillRoundRect(pad, pad, size - pad * 2, size - pad * 2 - size * 0.06, size * 0.25, size * 0.25);
        g.setFill(Ui.dark() ? Color.web("#e6edf3") : Color.web("#1f2328"));
        String text = label.length() > 3 ? label.substring(0, 3) : label;
        g.setFont(Font.font(Ui.uiFont() == null ? "System" : Ui.uiFont(), FontWeight.BOLD, size * (text.length() > 1 ? 0.3 : 0.45)));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(javafx.geometry.VPos.CENTER);
        g.fillText(text, size / 2.0, size / 2.0 - size * 0.03);
        return snapshot(c);
    }

    // ------------------------------------------------------------------ isometric painter

    private record Placed(int x, int y, int z, BlockModels.Shape shape) {}

    private record Face(double[] sx, double[] sy, double[] uv, String texture, double light, int tint, double depth) {}

    private static final double C = 0.8660254, S = 0.5;

    /** Draws the faces that look towards the viewer (up, east, south), far ones first, with textures mapped on. */
    private Image paint(List<Placed> blocks, int size) {
        Map<Long, BlockModels.Shape> at = new HashMap<>();
        for (Placed p : blocks) at.put(((long) p.x() << 40) ^ ((long) p.y() << 20) ^ p.z(), p.shape());
        List<Face> faces = new ArrayList<>();
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Placed p : blocks) {
            for (BlockModels.Quad q : p.shape().quads()) {
                double[] pos = q.pos();
                // face normal from the corners; only faces towards (+x, +y, +z) are seen
                double ax = pos[3] - pos[0], ay = pos[4] - pos[1], az = pos[5] - pos[2];
                double bx = pos[9] - pos[0], by = pos[10] - pos[1], bz = pos[11] - pos[2];
                double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                // the corner order gives an inward normal, so the outward one is its negative
                double facing = -(nx + ny + nz);
                if (facing <= 1e-6) continue;
                if (!q.cull().isEmpty()) {
                    int[] o = switch (q.cull()) {
                        case "east" -> new int[]{1, 0, 0};
                        case "south" -> new int[]{0, 0, 1};
                        case "up" -> new int[]{0, 1, 0};
                        case "west" -> new int[]{-1, 0, 0};
                        case "north" -> new int[]{0, 0, -1};
                        default -> new int[]{0, -1, 0};
                    };
                    BlockModels.Shape n = at.get(((long) (p.x() + o[0]) << 40) ^ ((long) (p.y() + o[1]) << 20) ^ (p.z() + o[2]));
                    if (n != null && n.full() && n.opaque()) continue;
                }
                double[] sx = new double[4], sy = new double[4];
                double depth = 0;
                for (int i = 0; i < 4; i++) {
                    double x = p.x() * 16 + pos[i * 3], y = p.y() * 16 + pos[i * 3 + 1], z = p.z() * 16 + pos[i * 3 + 2];
                    sx[i] = (x - z) * C;
                    sy[i] = (x + z) * S - y;
                    depth += x + y + z;
                    minX = Math.min(minX, sx[i]);
                    maxX = Math.max(maxX, sx[i]);
                    minY = Math.min(minY, sy[i]);
                    maxY = Math.max(maxY, sy[i]);
                }
                faces.add(new Face(sx, sy, q.uv(), q.texture(), q.shade(), q.tint(), depth / 4));
            }
        }
        if (faces.isEmpty()) return null;
        faces.sort(Comparator.comparingDouble(Face::depth));
        double span = Math.max(maxX - minX, maxY - minY);
        double scale = (size - 2) / Math.max(1e-6, span);
        double ox = (size - (maxX - minX) * scale) / 2 - minX * scale, oy = (size - (maxY - minY) * scale) / 2 - minY * scale;
        Canvas c = new Canvas(size, size);
        GraphicsContext g = c.getGraphicsContext2D();
        g.setImageSmoothing(false);
        for (Face f : faces) {
            double[] x = new double[4], y = new double[4];
            for (int i = 0; i < 4; i++) {
                x[i] = ox + f.sx()[i] * scale;
                y[i] = oy + f.sy()[i] * scale;
            }
            drawFace(g, f, x, y);
        }
        return snapshot(c);
    }

    private void drawFace(GraphicsContext g, Face f, double[] x, double[] y) {
        Image img = f.texture().startsWith("color:") ? null : BlockScene3D.image(ws, f.texture(), 16);
        g.save();
        g.beginPath();
        g.moveTo(x[0], y[0]);
        for (int i = 1; i < 4; i++) g.lineTo(x[i], y[i]);
        g.closePath();
        g.clip();
        if (img == null) {
            g.setFill(VoxelEditor.color(f.texture().startsWith("color:") ? f.texture().substring(6) : f.texture()));
            g.fillPolygon(x, y, 4);
        } else {
            // map texture pixels (u, v) to the screen with the affine through corners 0, 1 and 3
            double w = img.getWidth(), h = img.getHeight();
            double u0 = f.uv()[0] * w, v0 = f.uv()[1] * h, u1 = f.uv()[2] * w, v1 = f.uv()[3] * h, u3 = f.uv()[6] * w, v3 = f.uv()[7] * h;
            double det = (u1 - u0) * (v3 - v0) - (u3 - u0) * (v1 - v0);
            if (Math.abs(det) > 1e-9) {
                double a = ((x[1] - x[0]) * (v3 - v0) - (x[3] - x[0]) * (v1 - v0)) / det;
                double bb = ((x[3] - x[0]) * (u1 - u0) - (x[1] - x[0]) * (u3 - u0)) / det;
                double cc = ((y[1] - y[0]) * (v3 - v0) - (y[3] - y[0]) * (v1 - v0)) / det;
                double d = ((y[3] - y[0]) * (u1 - u0) - (y[1] - y[0]) * (u3 - u0)) / det;
                double tx = x[0] - a * u0 - bb * v0, ty = y[0] - cc * u0 - d * v0;
                g.setTransform(new Affine(a, bb, tx, cc, d, ty));
                double ul = w, vl = h, uh = 0, vh = 0;
                for (int i = 0; i < 4; i++) {
                    ul = Math.min(ul, f.uv()[i * 2] * w);
                    uh = Math.max(uh, f.uv()[i * 2] * w);
                    vl = Math.min(vl, f.uv()[i * 2 + 1] * h);
                    vh = Math.max(vh, f.uv()[i * 2 + 1] * h);
                }
                ul = Math.max(0, ul);
                vl = Math.max(0, vl);
                uh = Math.min(w, Math.max(uh, ul + 1));
                vh = Math.min(h, Math.max(vh, vl + 1));
                // a little overdraw hides seams between neighbouring faces
                g.drawImage(img, ul, vl, uh - ul, vh - vl, ul - 0.03, vl - 0.03, uh - ul + 0.06, vh - vl + 0.06);
                g.setTransform(new Affine());
            }
            if (f.tint() >= 0) {
                g.setGlobalBlendMode(javafx.scene.effect.BlendMode.MULTIPLY);
                g.setFill(Color.rgb((f.tint() >> 16) & 255, (f.tint() >> 8) & 255, f.tint() & 255));
                g.fillPolygon(x, y, 4);
                g.setGlobalBlendMode(javafx.scene.effect.BlendMode.SRC_OVER);
            }
        }
        if (f.light() < 1) {
            g.setFill(Color.rgb(0, 0, 0, (1 - f.light()) * 0.9));
            g.fillPolygon(x, y, 4);
        }
        g.restore();
    }

    private static Image snapshot(Canvas c) {
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        WritableImage out = new WritableImage((int) c.getWidth(), (int) c.getHeight());
        return c.snapshot(sp, out);
    }
}
