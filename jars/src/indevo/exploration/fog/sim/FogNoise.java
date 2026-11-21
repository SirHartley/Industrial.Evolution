package indevo.exploration.fog.sim;

public final class FogNoise {

    private FogNoise() {
    }

    public static double hash(int ix, int iy, int seed) {
        int h = ix * 374761393 + iy * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xffffffffL) / 4294967296.0;
    }

    public static double value(double x, double y, int seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        double u = fx * fx * (3 - 2 * fx), v = fy * fy * (3 - 2 * fy);
        double a = hash(ix, iy, seed), b = hash(ix + 1, iy, seed), c = hash(ix, iy + 1, seed), d = hash(ix + 1, iy + 1, seed);
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }

    public static double fbm(double x, double y, int seed, int octaves) {
        double s = 0, a = 0.5, f = 1, n = 0;
        for (int i = 0; i < octaves; i++) {
            s += a * value(x * f, y * f, seed + i * 17);
            n += a;
            a *= 0.5;
            f *= 2;
        }
        return s / n;
    }

    public static double smooth(double e0, double e1, double x) {
        double t = Math.min(1, Math.max(0, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }
}
