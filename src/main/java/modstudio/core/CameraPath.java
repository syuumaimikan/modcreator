package modstudio.core;

import modstudio.core.Systems.CameraKey;
import modstudio.core.Systems.Cutscene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Camera position and rotation of a cutscene at any time. The editor preview and every generator use this, so what
 * the editor shows is what the game plays. Yaw follows Minecraft: 0 looks south (+Z), 90 looks west (-X); positive
 * pitch looks down.
 */
public final class CameraPath {
    private CameraPath() {}

    public record Pose(double x, double y, double z, double yaw, double pitch) {}

    public static final List<String> EASINGS = List.of("smooth", "linear", "ease_in", "ease_out", "ease_in_out", "cut");

    public static List<CameraKey> sorted(Cutscene c) {
        List<CameraKey> r = new ArrayList<>(c.keys);
        r.sort(Comparator.comparingDouble(k -> k.time));
        return r;
    }

    /** Length in ticks (at least 1). */
    public static int ticks(Cutscene c) {
        return Math.max(1, (int) Math.ceil(c.length() * 20 - 1e-6));
    }

    public static double ease(String e, double u) {
        u = Math.max(0, Math.min(1, u));
        return switch (e == null ? "" : e) {
            case "linear", "smooth" -> u;
            case "ease_in" -> u * u * u;
            case "ease_out" -> 1 - Math.pow(1 - u, 3);
            case "ease_in_out" -> u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
            case "cut" -> u >= 1 ? 1 : 0;
            default -> u;
        };
    }

    /** Yaw and pitch that look from one point at another. */
    public static double[] look(double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        double h = Math.sqrt(dx * dx + dz * dz);
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = Math.toDegrees(Math.atan2(-dy, h));
        return new double[]{yaw, pitch};
    }

    private static double[] rotation(CameraKey k) {
        return k.lookAt ? look(k.x, k.y, k.z, k.tx, k.ty, k.tz) : new double[]{k.yaw, k.pitch};
    }

    private static Pose pose(CameraKey k) {
        double[] r = rotation(k);
        return new Pose(k.x, k.y, k.z, r[0], r[1]);
    }

    static double catmull(double p0, double p1, double p2, double p3, double u) {
        double u2 = u * u, u3 = u2 * u;
        return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
    }

    private static double lerpAngle(double a, double b, double u) {
        double d = ((b - a) % 360 + 540) % 360 - 180;
        return a + d * u;
    }

    public static Pose at(Cutscene c, double t) {
        List<CameraKey> k = sorted(c);
        if (k.isEmpty()) return new Pose(0, 0, 0, 0, 0);
        if (t <= k.get(0).time) return pose(k.get(0));
        CameraKey last = k.get(k.size() - 1);
        if (t >= last.time) return pose(last);
        int i = 0;
        while (i < k.size() - 2 && t >= k.get(i + 1).time) i++;
        CameraKey a = k.get(i), b = k.get(i + 1);
        double span = b.time - a.time;
        double u = span <= 1e-9 ? 1 : (t - a.time) / span;
        double e = ease(b.easing, u);
        double x, y, z;
        if ("smooth".equals(b.easing)) {
            CameraKey p0 = i > 0 ? k.get(i - 1) : a, p3 = i + 2 < k.size() ? k.get(i + 2) : b;
            x = catmull(p0.x, a.x, b.x, p3.x, e);
            y = catmull(p0.y, a.y, b.y, p3.y, e);
            z = catmull(p0.z, a.z, b.z, p3.z, e);
        } else {
            x = a.x + (b.x - a.x) * e;
            y = a.y + (b.y - a.y) * e;
            z = a.z + (b.z - a.z) * e;
        }
        double yaw, pitch;
        if (a.lookAt && b.lookAt) {
            double[] r = look(x, y, z, a.tx + (b.tx - a.tx) * e, a.ty + (b.ty - a.ty) * e, a.tz + (b.tz - a.tz) * e);
            yaw = r[0];
            pitch = r[1];
        } else {
            double[] ra = rotation(a), rb = rotation(b);
            yaw = lerpAngle(ra[0], rb[0], e);
            pitch = ra[1] + (rb[1] - ra[1]) * e;
        }
        return new Pose(x, y, z, yaw, Math.max(-90, Math.min(90, pitch)));
    }
}
