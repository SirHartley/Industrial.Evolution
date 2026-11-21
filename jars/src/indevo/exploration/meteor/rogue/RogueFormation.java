package indevo.exploration.meteor.rogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// generators of the approved mockup: x along the path (lead at the front), y across it, size in su
public enum RogueFormation {

    SCATTER(20f) {
        @Override
        public List<Rock> generate(Random r) {
            List<Rock> a = new ArrayList<>();
            int n = 10 + floor(next(r) * 5);
            for (int i = 0; i < n; i++) {
                float x = (next(r) - 0.5f) * 900f;
                float y = (next(r) - 0.5f) * 2600f;
                a.add(Rock.at(x, y, lerp(20f, 60f, pow(next(r), 1.6f))));
            }
            return a;
        }
    },

    CLUSTER(20f) {
        @Override
        public List<Rock> generate(Random r) {
            int n = 12 + floor(next(r) * 7);
            List<Rock> a = new ArrayList<>();
            a.add(Rock.at(0f, 0f, lerp(85f, 100f, next(r))));
            for (int i = 1; i < n; i++) {
                float ang = next(r) * TAU;
                float d = 110f + next(r) * 270f;
                a.add(Rock.at(cos(ang) * d, sin(ang) * d, lerp(20f, 50f, pow(next(r), 1.4f))));
            }
            return a;
        }
    },

    TEARDROP(20f) {
        @Override
        public List<Rock> generate(Random r) {
            List<Rock> a = new ArrayList<>();
            float rad = lerp(230f, 280f, next(r));
            float hx = 650f;
            float len = lerp(1500f, 1900f, next(r));
            boolean two = next(r) < 0.5f;

            a.add(Rock.at(hx + (two ? 70f : 0f), two ? -80f : 0f, lerp(85f, 100f, next(r))));
            if (two) a.add(Rock.at(hx - 60f, 90f, lerp(70f, 85f, next(r))));

            int hn = 4 + floor(next(r) * 3);
            float a0 = next(r) * TAU;
            for (int i = 0; i < hn; i++) {
                float ang = a0 + (float) i / hn * TAU + next(r) * 0.6f;
                float d = rad * lerp(0.6f, 1f, next(r));
                a.add(Rock.at(hx + cos(ang) * d, sin(ang) * d * 0.95f, lerp(35f, 60f, next(r))));
            }

            int tn = 12 + floor(next(r) * 5);
            for (int i = 0; i < tn; i++) {
                float f = pow((i + next(r)) / tn, 1.4f);
                float hw = rad * 0.95f * pow(1f - f, 1.2f) + 15f;
                float x = hx - rad * 0.5f - f * len;
                float y = (next(r) * 2f - 1f) * hw;
                a.add(Rock.at(x, y, lerp(50f, 20f, f) * lerp(0.8f, 1.1f, next(r))));
            }
            return a;
        }
    },

    WALL(10f) {
        @Override
        public List<Rock> generate(Random r) {
            int n = 9 + floor(next(r) * 5);
            List<Rock> a = new ArrayList<>();
            float tilt = (next(r) - 0.5f) * 0.5f;
            float p1 = next(r) * TAU;
            float p2 = next(r) * TAU;

            float[] ys = new float[n];
            float t = 0f;
            for (int i = 0; i < n; i++) {
                ys[i] = t;
                if (next(r) < 0.35f) t += lerp(70f, 130f, next(r));
                else t += lerp(220f, 430f, next(r));
            }

            float span = ys[n - 1];
            for (int i = 0; i < n; i++) {
                float u = ys[i] / span;
                float yy = (u - 0.5f) * 2400f;
                float x = sin(u * 4f + p1) * 170f + sin(u * 9f + p2) * 70f + gauss(r) * 110f;
                a.add(Rock.at(x + yy * sin(tilt), yy * cos(tilt), lerp(22f, 80f, pow(next(r), 1.3f))));
            }

            int m = 2 + floor(next(r) * 3);
            for (int j = 0; j < m; j++) {
                float x = -lerp(250f, 600f, next(r));
                float y = (next(r) - 0.5f) * 2000f;
                a.add(Rock.at(x, y, lerp(20f, 32f, next(r))));
            }
            return a;
        }
    },

    CHEVRON(10f) {
        @Override
        public List<Rock> generate(Random r) {
            List<Rock> a = new ArrayList<>();
            a.add(Rock.at(0f, 0f, lerp(60f, 80f, next(r))));

            if (next(r) < 0.7f) {
                float x = -lerp(60f, 130f, next(r));
                float side = next(r) < 0.5f ? -1f : 1f;
                float y = side * lerp(70f, 130f, next(r));
                a.add(Rock.at(x, y, lerp(35f, 50f, next(r))));
            }

            // both arms are rolled before either is placed
            float[][] arms = {
                    {-1f, lerp(900f, 1300f, next(r)), 4 + floor(next(r) * 3)},
                    {1f, lerp(700f, 1200f, next(r)), 3 + floor(next(r) * 3)}};

            for (float[] arm : arms) {
                float sd = arm[0];
                float len = arm[1];
                int n = (int) arm[2];
                float sweep = lerp(0.55f, 0.85f, next(r));
                float open = lerp(0.6f, 0.75f, next(r));
                float d = 0f;

                for (int k = 0; k < n; k++) {
                    d += len / n * lerp(0.6f, 1.4f, next(r));
                    float u = Math.min(1f, d / len);
                    float x = -d * sweep * (1f + 0.35f * u) + gauss(r) * 60f;
                    float y = sd * d * open * (1f - 0.15f * u) + gauss(r) * 60f;
                    a.add(Rock.at(x, y, lerp(55f, 22f, u) * lerp(0.75f, 1.15f, next(r))));
                }
            }

            int m = 1 + floor(next(r) * 3);
            for (int j = 0; j < m; j++) {
                float x = -lerp(300f, 800f, next(r));
                float y = gauss(r) * 250f;
                a.add(Rock.at(x, y, lerp(20f, 30f, next(r))));
            }
            return a;
        }
    },

    PAIR(10f) {
        @Override
        public List<Rock> generate(Random r) {
            List<Rock> a = new ArrayList<>();
            for (float cy : new float[]{-500f, 500f}) {
                int n = 6 + floor(next(r) * 4);
                float ox = (next(r) - 0.5f) * 500f;
                for (int i = 0; i < n; i++) {
                    float ang = next(r) * TAU;
                    float d = i > 0 ? 90f + next(r) * 170f : 0f;
                    float size = i > 0 ? lerp(20f, 45f, next(r)) : lerp(60f, 75f, next(r));
                    a.add(Rock.at(ox + cos(ang) * d, cy + sin(ang) * d, size));
                }
            }
            return a;
        }
    },

    ESCORT(5f) {
        @Override
        public List<Rock> generate(Random r) {
            List<Rock> a = new ArrayList<>();
            a.add(new Rock(0f, 0f, lerp(170f, 200f, next(r)), true, -1, 0f, 0f, 0f));
            int n = 5 + floor(next(r) * 4);
            for (int i = 0; i < n; i++) {
                float size = lerp(20f, 35f, next(r));
                float d = lerp(260f, 380f, next(r));
                float ang = (float) i / n * TAU + next(r) * 0.5f;
                float dir = next(r) < 0.5f ? -1f : 1f;
                float spin = dir * lerp(0.35f, 0.6f, next(r));
                a.add(new Rock(0f, 0f, size, false, 0, d, ang, spin));
            }
            return a;
        }
    };

    public static final float TAU = (float) (Math.PI * 2d);

    // orbitAngle in radians, orbitSpin in radians per second, focus is the index of the rock it circles or -1
    public record Rock(float x, float y, float size, boolean solo, int focus, float orbitRadius, float orbitAngle, float orbitSpin) {
        public static Rock at(float x, float y, float size) {
            return new Rock(x, y, size, false, -1, 0f, 0f, 0f);
        }

        public boolean isOrbiting() {
            return focus >= 0;
        }

        public Rock withSize(float newSize) {
            return new Rock(x, y, newSize, solo, focus, orbitRadius, orbitAngle, orbitSpin);
        }
    }

    public final float weight;

    RogueFormation(float weight) {
        this.weight = weight;
    }

    public abstract List<Rock> generate(Random random);

    public String getId() {
        return name().toLowerCase();
    }

    public static RogueFormation byId(String id) {
        for (RogueFormation formation : values()) if (formation.getId().equalsIgnoreCase(id)) return formation;
        return null;
    }

    public static int getTreasureIndex(List<Rock> rocks) {
        for (int i = 0; i < rocks.size(); i++) if (rocks.get(i).solo()) return i;
        for (int i = 0; i < rocks.size(); i++) if (!rocks.get(i).isOrbiting()) return i;
        return 0;
    }

    // largest distance of any rock edge from the formation origin, size is the entity radius in game
    public static float getFootprintRadius(List<Rock> rocks) {
        float ext = 0f;
        for (Rock rock : rocks) {
            Rock anchor = rock.isOrbiting() ? rocks.get(rock.focus()) : rock;
            float reach = (rock.isOrbiting() ? rock.orbitRadius() : 0f) + rock.size();
            ext = Math.max(ext, (float) Math.hypot(anchor.x(), anchor.y()) + reach);
        }
        return ext;
    }

    static float next(Random random) {
        return random.nextFloat();
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static float gauss(Random random) {
        return (next(random) + next(random) + next(random) - 1.5f) / 1.5f;
    }

    static int floor(float value) {
        return (int) Math.floor(value);
    }

    static float pow(float base, float exp) {
        return (float) Math.pow(base, exp);
    }

    static float sin(float value) {
        return (float) Math.sin(value);
    }

    static float cos(float value) {
        return (float) Math.cos(value);
    }
}
