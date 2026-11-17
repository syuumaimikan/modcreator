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

    /**
     * Cubic Hermite step between keys i and i + 1. The slope at a key is the central difference over time, so the
     * speed does not jump at keys even when they are unevenly spaced; the first and last keys start and stop gently.
     * Keys where ok is false do not bend the curve (their slope is zero).
     */
    static double hermite(double[] time, double[] v, boolean[] ok, int i, double u) {
        double span = time[i + 1] - time[i];
        double m0 = slope(time, v, ok, i) * span, m1 = slope(time, v, ok, i + 1) * span;
        double u2 = u * u, u3 = u2 * u;
        return (2 * u3 - 3 * u2 + 1) * v[i] + (u3 - 2 * u2 + u) * m0 + (-2 * u3 + 3 * u2) * v[i + 1] + (u3 - u2) * m1;
    }

    private static double slope(double[] time, double[] v, boolean[] ok, int i) {
        if (i <= 0 || i >= v.length - 1 || !ok[i - 1] || !ok[i] || !ok[i + 1]) return 0;
        double dt = time[i + 1] - time[i - 1];
        return dt <= 1e-9 ? 0 : (v[i + 1] - v[i - 1]) / dt;
    }

    private static double wrap(double d) {
        return ((d % 360) + 540) % 360 - 180;
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
        boolean smooth = "smooth".equals(b.easing) && span > 1e-9;
        double e = ease(b.easing, u);

        int n = k.size();
        double[] time = new double[n], yaw = new double[n], pitch = new double[n];
        boolean[] all = new boolean[n], look = new boolean[n];
        for (int j = 0; j < n; j++) {
            CameraKey kj = k.get(j);
            double[] r = rotation(kj);
            time[j] = kj.time;
            // unwrapped, so turning from 170 to -170 goes 20 degrees and not 340
            yaw[j] = j == 0 ? r[0] : yaw[j - 1] + wrap(r[0] - yaw[j - 1]);
            pitch[j] = r[1];
            all[j] = true;
            look[j] = kj.lookAt;
        }
        double x, y, z;
        if (smooth) {
            x = hermite(time, values(k, 0), all, i, u);
            y = hermite(time, values(k, 1), all, i, u);
            z = hermite(time, values(k, 2), all, i, u);
        } else {
            x = a.x + (b.x - a.x) * e;
            y = a.y + (b.y - a.y) * e;
            z = a.z + (b.z - a.z) * e;
        }
        double ry, rp;
        if (a.lookAt && b.lookAt) {
            double tx, ty, tz;
            if (smooth) {
                tx = hermite(time, values(k, 3), look, i, u);
                ty = hermite(time, values(k, 4), look, i, u);
                tz = hermite(time, values(k, 5), look, i, u);
            } else {
                tx = a.tx + (b.tx - a.tx) * e;
                ty = a.ty + (b.ty - a.ty) * e;
                tz = a.tz + (b.tz - a.tz) * e;
            }
            double[] r = look(x, y, z, tx, ty, tz);
            ry = r[0];
            rp = r[1];
        } else if (smooth) {
            ry = hermite(time, yaw, all, i, u);
            rp = hermite(time, pitch, all, i, u);
        } else {
            ry = yaw[i] + (yaw[i + 1] - yaw[i]) * e;
            rp = pitch[i] + (pitch[i + 1] - pitch[i]) * e;
        }
        return new Pose(x, y, z, wrap(ry), Math.max(-90, Math.min(90, rp)));
    }

    private static double[] values(List<CameraKey> k, int field) {
        double[] r = new double[k.size()];
        for (int j = 0; j < r.length; j++) {
            CameraKey kj = k.get(j);
            r[j] = switch (field) {
                case 0 -> kj.x;
                case 1 -> kj.y;
                case 2 -> kj.z;
                case 3 -> kj.tx;
                case 4 -> kj.ty;
                default -> kj.tz;
            };
        }
        return r;
    }
}
