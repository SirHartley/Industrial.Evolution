package indevo.exploration.fog.sim;

import java.nio.ByteBuffer;
import java.util.Random;

import static indevo.exploration.fog.sim.FogNoise.fbm;
import static indevo.exploration.fog.sim.FogNoise.smooth;

public class FogGrid {

    public static final float REFILL = 0.3f;
    public static final float RELAX_TAU = 14f;
    public static final float DRAG = 0.45f;
    public static final float CONFINE = 0.55f;
    public static final float AMBIENT = 300f;
    public static final float AMBIENT_RATE = 0.5f;
    public static final int ITER = 10;
    public static final float EB_RADIUS = 420f;
    public static final float DISP_TAU = 6f;
    public static final float MAX_DT = 0.1f;
    // body wake: samples along the path, spacing and least width in wake radii and cells, cut per second
    public static final int WAKE_SAMPLES = 12;
    public static final float WAKE_STEP = 0.5f, WAKE_MIN_CELLS = 2.2f, WAKE_RATE = 3f;
    public static final int RECENTRE_CELLS = 8;

    public final int n;
    public final float cell;
    public final float size;
    public final Shape shape;

    public float ox;
    public float oy;
    private float strength;
    private float time;
    private int row = 0;

    public final float[] m, d, u, v, gx, gy;
    private final float[] t1, t2, t3, t4, p, div, src, w, psi;

    public static class Shape {

        // a bank's lobes reach about this many lobe scales from its centre, radius is that reach
        public static final float LOBE_REACH = 2.4f;
        // in lobe radii: solid inside EDGE_IN, gone at EDGE_OUT, arms end at ARM_REACH
        private static final double EDGE_IN = 0.62, EDGE_OUT = 1.3, ARM_REACH = 2.6;
        // nebula cover at which fog starts to thin and at which it is gone
        private static final double GATE_IN = 0.03, GATE_OUT = 0.4;

        public final int seed;
        public final float radius;
        public final boolean wide;
        // x, y, inner, outer, hazeIn, hazeOut per clear ring in bank local coordinates: no fog inside inner, full fog from outer
        // with hazeOut above 0 a thin haze of HAZE density starts at hazeIn, is full at hazeOut and fills the ring
        public static final int KEEP_STRIDE = 6;
        public static final double HAZE = 0.5;
        public float[] keep = new float[0];
        // tile fields no fog may lie on, in system coordinates; anchor is the fog centre there
        public FogCover[] covers = new FogCover[0];
        public float anchorX, anchorY;
        // x, y, radius, angle, stretch per lobe
        private final float[] lobes;

        public Shape(int seed, float radius, boolean wide) {
            this.seed = seed;
            this.radius = radius;
            this.wide = wide;
            this.lobes = wide ? new float[0] : buildLobes(seed, radius / LOBE_REACH);
        }

        private static float[] buildLobes(int seed, float scale) {
            Random random = new Random(seed);
            int count = 5 + random.nextInt(3);
            float[] out = new float[count * 5];
            for (int i = 0; i < count; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                float dist = i == 0 ? 0f : scale * (0.4f + 0.6f * random.nextFloat());
                out[i * 5] = (float) Math.cos(angle) * dist;
                out[i * 5 + 1] = (float) Math.sin(angle) * dist;
                out[i * 5 + 2] = scale * (0.24f + 0.2f * random.nextFloat());
                out[i * 5 + 3] = (float) (random.nextDouble() * Math.PI);
                out[i * 5 + 4] = 1.6f + 1.6f * random.nextFloat();
            }
            return out;
        }

        public float mask(double x, double y, double t) {
            double body = wide ? spellBody(x, y, t) : bankBody(x, y, t);
            if (body <= 0) return 0f;
            double inner = 0.5 + 0.7 * fbm(x / 1100 + 3 - t * 0.014, y / 1100 + 5 + t * 0.011, seed + 3, 4);
            double keepMult = 1;
            float[] k = keep;
            for (int i = 0; i + KEEP_STRIDE <= k.length; i += KEEP_STRIDE) {
                double dist = Math.hypot(x - k[i], y - k[i + 1]);
                double full = smooth(k[i + 2], k[i + 3], dist);
                keepMult *= k[i + 5] > 0 ? HAZE * smooth(k[i + 4], k[i + 5], dist) + (1 - HAZE) * full : full;
            }
            for (FogCover cover : covers) keepMult *= 1 - smooth(GATE_IN, GATE_OUT, cover.at((float) x + anchorX, (float) y + anchorY));
            return (float) Math.min(1, Math.max(0, body * inner * keepMult));
        }

        private double spellBody(double x, double y, double t) {
            double edge = radius * (0.85 + 0.3 * fbm(x / 2400 + 11 + t * 0.012, y / 2400 + 7 - t * 0.009, seed, 3));
            return 1 - smooth(edge * 0.85, edge, Math.hypot(x, y));
        }

        // stretched lobes in a slowly drifting warped space: solid middles, a noisy soft outline, ridged arms reaching out
        private double bankBody(double x, double y, double t) {
            double scale = radius / LOBE_REACH;
            double wx = x + (fbm(x / 3400 + 41 + t * 0.006, y / 3400 + 3, seed + 21, 3) - 0.5) * scale * 0.9;
            double wy = y + (fbm(x / 3400 + 9, y / 3400 + 57 - t * 0.005, seed + 22, 3) - 0.5) * scale * 0.9;
            double q = Double.MAX_VALUE;
            float[] l = lobes;
            for (int i = 0; i + 4 < l.length; i += 5) {
                double dx = wx - l[i], dy = wy - l[i + 1], c = Math.cos(l[i + 3]), s = Math.sin(l[i + 3]);
                q = Math.min(q, Math.hypot((dx * c + dy * s) / l[i + 4], -dx * s + dy * c) / l[i + 2]);
            }
            if (q >= ARM_REACH) return 0;
            double outline = fbm(wx / 2000 + 71 - t * 0.004, wy / 2000 + 13, seed + 41, 3);
            double body = 1 - smooth(EDGE_IN, EDGE_OUT, q * (0.45 + 1.3 * (1 - outline)));
            double rn = fbm(wx / 1100 + t * 0.012, wy / 1100 - t * 0.009, seed + 33, 4);
            double ridge = Math.pow(1 - Math.abs(2 * rn - 1), 4);
            return Math.max(body, ridge * (1 - smooth(0.7, ARM_REACH, q)) * 0.8);
        }

        public float curl(double x, double y, double t) {
            return (float) (fbm(x / 1300 + 31 + t * 0.02, y / 1300 + 17 - t * 0.017, seed + 9, 2) * 1300);
        }
    }

    public static class Mover {

        public static final float PAD = 60f;

        public float x, y, vx, vy, r;
        public float cut = 1f;
        public float pad = PAD;
        public float shed = 0f;
        public float side = 1f;
        // planets, moons and stations: full cut at any speed and a wake along the path behind
        public boolean body = false;
        private final float[] trail = new float[WAKE_SAMPLES * 2];
        private int trailCount = 0;

        public void clearTrail() {
            trailCount = 0;
        }
    }

    public FogGrid(int n, float cell, Shape shape, float cx, float cy, float strength, float time) {
        this.n = n;
        this.cell = cell;
        this.size = n * cell;
        this.shape = shape;
        this.strength = strength;
        this.time = time;
        this.ox = snap(cx - size / 2f);
        this.oy = snap(cy - size / 2f);

        int nn = n * n;
        m = new float[nn]; d = new float[nn]; u = new float[nn]; v = new float[nn];
        gx = new float[nn]; gy = new float[nn];
        t1 = new float[nn]; t2 = new float[nn]; t3 = new float[nn]; t4 = new float[nn];
        p = new float[nn]; div = new float[nn]; src = new float[nn]; w = new float[nn]; psi = new float[nn];

        refresh(n);
        fill();
    }

    public void setStrength(float strength) {
        this.strength = strength;
    }

    public void fill() {
        for (int k = 0; k < d.length; k++) d[k] = m[k] * strength;
    }

    public boolean inWindow(float x, float y) {
        float margin = cell * 2f;
        return x > ox + margin && y > oy + margin && x < ox + size - margin && y < oy + size - margin;
    }

    public void recentre(float cx, float cy) {
        float half = size / 2f, limit = RECENTRE_CELLS * cell;
        if (Math.abs(cx - (ox + half)) < limit && Math.abs(cy - (oy + half)) < limit) return;

        float nox = snap(cx - half), noy = snap(cy - half);
        int si = Math.round((nox - ox) / cell), sj = Math.round((noy - oy) / cell);
        ox = nox;
        oy = noy;

        if (Math.abs(si) >= n || Math.abs(sj) >= n) {
            for (float[] a : new float[][]{u, v, gx, gy, p, src}) java.util.Arrays.fill(a, 0f);
            refresh(n);
            fill();
            return;
        }

        for (float[] a : new float[][]{m, d, u, v, gx, gy, p, src, psi}) shift(a, si, sj);

        for (int j = 0; j < n; j++) {
            boolean newRow = j + sj < 0 || j + sj >= n;
            for (int i = 0; i < n; i++) {
                if (!newRow && i + si >= 0 && i + si < n) continue;
                int k = j * n + i;
                double x = ox + (i + 0.5) * cell, y = oy + (j + 0.5) * cell;
                m[k] = shape.mask(x, y, time);
                psi[k] = shape.curl(x, y, time);
                d[k] = m[k] * strength;
            }
        }
    }

    // a moving thing drags fog along, shoves it aside and sheds eddies off its tail
    public void mover(Mover b, float dt) {
        float sp = (float) Math.hypot(b.vx, b.vy);
        if (sp < 2f) return;
        float rad = b.r * 1.5f + b.pad, ux = b.vx / sp, uy = b.vy / sp, fill = 1f;
        // a hole narrower than a cell would miss the cell centres, so it cuts the nearest cells at a share of the strength
        if (rad < cell) {
            fill = rad / cell;
            rad = cell;
        }
        float speedCut = Math.min(1.5f, sp / 170f);
        float grab = Math.min(1f, dt * 9f) * fill, cut = (b.body ? Math.max(1f, speedCut) : speedCut) * b.cut * fill;
        float outer = rad * 1.5f;

        int i0 = Math.max(1, (int) Math.floor((b.x - outer - ox) / cell)), i1 = Math.min(n - 2, (int) Math.ceil((b.x + outer - ox) / cell));
        int j0 = Math.max(1, (int) Math.floor((b.y - outer - oy) / cell)), j1 = Math.min(n - 2, (int) Math.ceil((b.y + outer - oy) / cell));
        for (int j = j0; j <= j1; j++) for (int i = i0; i <= i1; i++) {
            float dx = ox + (i + 0.5f) * cell - b.x, dy = oy + (j + 0.5f) * cell - b.y;
            float dist = (float) Math.hypot(dx, dy);
            if (dist >= outer) continue;
            int k = j * n + i;
            float f = Math.max(0f, 1f - dist / outer), ff = f * f;
            u[k] += (b.vx * 0.85f - u[k]) * grab * ff;
            v[k] += (b.vy * 0.85f - v[k]) * grab * ff;
            if (dist < rad) {
                src[k] += sp / rad * 0.55f * (1f - dist / rad) * dt * 6f * fill;
                d[k] *= 1f - Math.min(1f, dt * 10f * cut * (1f - dist / rad));
            }
        }

        if (b.body) wake(b, rad, dt);

        b.shed += sp * dt / (rad * 2.6f);
        if (b.shed >= 1f) {
            b.shed -= 1f;
            b.side = -b.side;
            float px = -uy * b.side, py = ux * b.side;
            swirl(b.x - ux * rad * 0.8f + px * rad * 0.55f, b.y - uy * rad * 0.8f + py * rad * 0.55f, rad * 1.7f, -b.side * sp * 1.25f * fill);
        }
    }

    // an orbiting body is too slow to outrun the refill, so it holds a tapering wake open along the path it came
    private void wake(Mover b, float rad, float dt) {
        float base = Math.max(rad, cell * WAKE_MIN_CELLS), step = base * WAKE_STEP;
        float[] t = b.trail;
        if (b.trailCount == 0 || Math.hypot(b.x - t[0], b.y - t[1]) >= step) {
            b.trailCount = Math.min(WAKE_SAMPLES, b.trailCount + 1);
            System.arraycopy(t, 0, t, 2, (b.trailCount - 1) * 2);
            t[0] = b.x;
            t[1] = b.y;
        }
        float frac = (float) Math.hypot(b.x - t[0], b.y - t[1]) / step;
        for (int s = 0; s < b.trailCount; s++) {
            float f = 1f - (s + frac) / WAKE_SAMPLES;
            if (f <= 0f) continue;
            float x = t[s * 2], y = t[s * 2 + 1], r = base * (0.55f + 0.45f * f);
            int i0 = Math.max(1, (int) Math.floor((x - r - ox) / cell)), i1 = Math.min(n - 2, (int) Math.ceil((x + r - ox) / cell));
            int j0 = Math.max(1, (int) Math.floor((y - r - oy) / cell)), j1 = Math.min(n - 2, (int) Math.ceil((y + r - oy) / cell));
            for (int j = j0; j <= j1; j++) for (int i = i0; i <= i1; i++) {
                float dist = (float) Math.hypot(ox + (i + 0.5f) * cell - x, oy + (j + 0.5f) * cell - y);
                if (dist < r) d[j * n + i] *= 1f - Math.min(1f, dt * WAKE_RATE * f * (1f - dist / r));
            }
        }
    }

    public void blast(float x, float y, float rad, float spin) {
        float outer = rad * 1.3f;
        int i0 = Math.max(1, (int) Math.floor((x - outer - ox) / cell)), i1 = Math.min(n - 2, (int) Math.ceil((x + outer - ox) / cell));
        int j0 = Math.max(1, (int) Math.floor((y - outer - oy) / cell)), j1 = Math.min(n - 2, (int) Math.ceil((y + outer - oy) / cell));
        for (int j = j0; j <= j1; j++) for (int i = i0; i <= i1; i++) {
            float dist = (float) Math.hypot(ox + (i + 0.5f) * cell - x, oy + (j + 0.5f) * cell - y);
            if (dist >= outer) continue;
            int k = j * n + i;
            src[k] += 5.5f * Math.max(0f, 1f - dist / outer);
            d[k] *= (float) smooth(rad * 0.5, rad * 1.05, dist);
        }
        swirl(x, y, rad * 1.8f, spin);
        for (int a = 0; a < 6; a++) {
            double an = a / 6.0 * Math.PI * 2 + 0.4;
            swirl(x + (float) Math.cos(an) * rad * 1.05f, y + (float) Math.sin(an) * rad * 1.05f, rad * 0.75f, (a % 2 == 1 ? 1 : -1) * 330f);
        }
    }

    // instant cut along a segment, full inside inner and gone at outer, pushed out to both sides
    public void lance(float x0, float y0, float x1, float y1, float inner, float outer, float push) {
        float len = (float) Math.hypot(x1 - x0, y1 - y0);
        if (len < 1f) return;
        float ux = (x1 - x0) / len, uy = (y1 - y0) / len;
        for (int j = 1; j < n - 1; j++) for (int i = 1; i < n - 1; i++) {
            float dx = ox + (i + 0.5f) * cell - x0, dy = oy + (j + 0.5f) * cell - y0;
            float along = dx * ux + dy * uy;
            if (along < 0f || along > len) continue;
            float side = dx * uy - dy * ux, dist = Math.abs(side);
            if (dist >= outer) continue;
            int k = j * n + i;
            float f = 1f - dist / outer, s = Math.signum(side) * push * f;
            d[k] *= (float) smooth(inner, outer, dist);
            src[k] += 3f * f;
            u[k] += uy * s;
            v[k] -= ux * s;
        }
    }

    public void ring(float x, float y, float r0, float r1, float dt) {
        float outer = r1 + 160f;
        int i0 = Math.max(1, (int) Math.floor((x - outer - ox) / cell)), i1 = Math.min(n - 2, (int) Math.ceil((x + outer - ox) / cell));
        int j0 = Math.max(1, (int) Math.floor((y - outer - oy) / cell)), j1 = Math.min(n - 2, (int) Math.ceil((y + outer - oy) / cell));
        for (int j = j0; j <= j1; j++) for (int i = i0; i <= i1; i++) {
            float dx = ox + (i + 0.5f) * cell - x, dy = oy + (j + 0.5f) * cell - y;
            float dist = (float) Math.hypot(dx, dy);
            if (dist >= outer) continue;
            int k = j * n + i;
            if (dist < r1) d[k] *= (float) (0.75 * smooth(r0 * 0.8, r1, dist) + 0.25 * smooth(r1 * 0.5, r1 + 40, dist));
            float band = Math.max(0f, 1f - Math.abs(dist - r1) / 160f);
            src[k] += 22f * band * dt;
            if (dist > 1f) {
                float tw = 260f * band * dt * ((((int) (Math.atan2(dy, dx) * 5)) % 2 != 0) ? 1f : -1f);
                u[k] -= dy / dist * tw;
                v[k] += dx / dist * tw;
            }
        }
    }

    public void step(float dt, float maskTime) {
        dt = Math.min(dt, MAX_DT);
        if (dt <= 0f) return;
        time = maskTime;
        refresh(Math.max(1, Math.round(n * dt * 2)));

        float h = cell, st = strength;
        int nn = n * n;
        float i2h = 1f / (2f * h), amb = Math.min(1f, AMBIENT_RATE * dt);
        for (int j = 1; j < n - 1; j++) for (int i = 1; i < n - 1; i++) {
            int k = j * n + i;
            w[k] = (v[k + 1] - v[k - 1] - u[k + n] + u[k - n]) * i2h;
            float tu = (psi[k + n] - psi[k - n]) * i2h * AMBIENT, tv = -(psi[k + 1] - psi[k - 1]) * i2h * AMBIENT;
            float wt = amb * Math.min(1f, d[k] * 1.5f + 0.15f);
            u[k] += (tu - u[k]) * wt * 0.25f + tu * wt * 0.08f;
            v[k] += (tv - v[k]) * wt * 0.25f + tv * wt * 0.08f;
        }

        float ce = CONFINE * h * dt;
        for (int j = 2; j < n - 2; j++) for (int i = 2; i < n - 2; i++) {
            int k = j * n + i;
            float nx = Math.abs(w[k + 1]) - Math.abs(w[k - 1]), ny = Math.abs(w[k + n]) - Math.abs(w[k - n]);
            float l = (float) Math.hypot(nx, ny) + 1e-6f;
            nx /= l;
            ny /= l;
            u[k] += ny * w[k] * ce;
            v[k] -= nx * w[k] * ce;
        }

        advect(u, t1, dt);
        advect(v, t2, dt);
        float drag = (float) Math.exp(-DRAG * dt);
        for (int k = 0; k < nn; k++) {
            u[k] = t1[k] * drag;
            v[k] = t2[k] * drag;
        }

        // projection to a target divergence: holes pull fog in, blasts push it out
        float sd = (float) Math.exp(-dt * 7);
        for (int j = 1; j < n - 1; j++) for (int i = 1; i < n - 1; i++) {
            int k = j * n + i;
            float s = src[k] - REFILL * (m[k] * st - d[k]);
            if (s > 6f) s = 6f;
            else if (s < -1.5f) s = -1.5f;
            div[k] = ((u[k + 1] - u[k - 1] + v[k + n] - v[k - n]) * i2h - s) * h * h;
            src[k] *= sd;
        }
        for (int it = 0; it < ITER; it++) for (int c = 0; c < 2; c++) {
            for (int j = 1; j < n - 1; j++) for (int i = 1 + ((j + c) & 1); i < n - 1; i += 2) {
                int k = j * n + i;
                p[k] = (p[k - 1] + p[k + 1] + p[k - n] + p[k + n] - div[k]) * 0.25f;
            }
        }
        for (int j = 1; j < n - 1; j++) for (int i = 1; i < n - 1; i++) {
            int k = j * n + i;
            u[k] -= (p[k + 1] - p[k - 1]) * i2h;
            v[k] -= (p[k + n] - p[k - n]) * i2h;
        }

        advect(d, t1, dt);
        advect(gx, t3, dt);
        advect(gy, t4, dt);
        float rl = 1f - (float) Math.exp(-dt / RELAX_TAU), dd = (float) Math.exp(-dt / DISP_TAU);
        for (int k = 0; k < nn; k++) {
            float x = t1[k] + (m[k] * st - t1[k]) * rl;
            d[k] = x < 0f ? 0f : Math.min(x, 1.3f);
            gx[k] = (t3[k] + u[k] * dt) * dd;
            gy[k] = (t4[k] + v[k] * dt) * dd;
        }

        // border cells stand for the fog outside the window, which sits at the rest mask
        for (int i = 0; i < n; i++) {
            setBorder(i);
            setBorder((n - 1) * n + i);
            setBorder(i * n);
            setBorder(i * n + n - 1);
        }
    }

    private void setBorder(int k) {
        d[k] = m[k] * strength;
        u[k] = 0f;
        v[k] = 0f;
    }

    // rgba per cell: density / 1.3, detail offset x and y around 128, flow speed
    public void pack(ByteBuffer out) {
        out.clear();
        for (int k = 0; k < n * n; k++) {
            out.put(toByte(d[k] / 1.3f * 255f));
            out.put(toByte(128f + gx[k] / 700f * 127f));
            out.put(toByte(128f + gy[k] / 700f * 127f));
            out.put(toByte((float) Math.hypot(u[k], v[k]) / 400f * 255f));
        }
        out.flip();
    }

    private static byte toByte(float x) {
        return (byte) (x < 0f ? 0 : x > 255f ? 255 : (int) x);
    }

    private float snap(float x) {
        return (float) Math.floor(x / cell) * cell;
    }

    private void refresh(int rows) {
        for (int c = 0; c < rows; c++) {
            int j = row;
            row = (row + 1) % n;
            double y = oy + (j + 0.5) * cell;
            for (int i = 0; i < n; i++) {
                double x = ox + (i + 0.5) * cell;
                m[j * n + i] = shape.mask(x, y, time);
                psi[j * n + i] = shape.curl(x, y, time);
            }
        }
    }

    private void shift(float[] a, int si, int sj) {
        for (int j = 0; j < n; j++) {
            int sy = j + sj;
            for (int i = 0; i < n; i++) {
                int sx = i + si;
                div[j * n + i] = sx >= 0 && sx < n && sy >= 0 && sy < n ? a[sy * n + sx] : 0f;
            }
        }
        System.arraycopy(div, 0, a, 0, a.length);
    }

    private void swirl(float x, float y, float rad, float gamma) {
        int i0 = Math.max(1, (int) Math.floor((x - rad - ox) / cell)), i1 = Math.min(n - 2, (int) Math.ceil((x + rad - ox) / cell));
        int j0 = Math.max(1, (int) Math.floor((y - rad - oy) / cell)), j1 = Math.min(n - 2, (int) Math.ceil((y + rad - oy) / cell));
        for (int j = j0; j <= j1; j++) for (int i = i0; i <= i1; i++) {
            float dx = ox + (i + 0.5f) * cell - x, dy = oy + (j + 0.5f) * cell - y;
            float dist = (float) Math.hypot(dx, dy);
            if (dist >= rad) continue;
            int k = j * n + i;
            float f = 1f - dist / rad, g = gamma * f * f / rad;
            u[k] -= dy * g * 2f;
            v[k] += dx * g * 2f;
        }
    }

    private void advect(float[] from, float[] to, float dt) {
        float inv = dt / cell;
        for (int j = 1; j < n - 1; j++) for (int i = 1; i < n - 1; i++) {
            int k = j * n + i;
            float x = i - u[k] * inv, y = j - v[k] * inv;
            if (x < 0.5f) x = 0.5f;
            if (y < 0.5f) y = 0.5f;
            if (x > n - 1.5f) x = n - 1.5f;
            if (y > n - 1.5f) y = n - 1.5f;
            int a = (int) x, b = (int) y, q = b * n + a;
            float fx = x - a, fy = y - b;
            to[k] = (from[q] * (1 - fx) + from[q + 1] * fx) * (1 - fy) + (from[q + n] * (1 - fx) + from[q + n + 1] * fx) * fy;
        }
    }
}
