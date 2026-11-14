package indevo.industries.artillery.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import indevo.industries.artillery.utils.VisionBlockers;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LightConeRenderer {

    // brightness = peak * across(angle / half arc) * along(distance / drawn length), minus the shadows
    public static class Style {
        public float peak = 0.2f;
        public float edgeStart = 0.4f;
        public float edgeEnd = 1.25f;
        public float coreShare = 0f;
        public float coreWidth = 0.2f;
        public float farShare = 0.3f;
        public float falloff = 8f;
        public float endStart = 0.55f;
        public float nearSoftShare = 0.02f;
        public float textureShare = 0f;
        public float shadow = 0.9f;
        public float sourceRadius = 50f;
        public float dustGain = 0f;

        public float across(float t) {
            float halo = 1f - smooth((t - edgeStart) / (edgeEnd - edgeStart));
            if (coreShare <= 0f) return halo;

            float core = t / coreWidth;
            return (1f - coreShare) * halo + coreShare * (float) Math.exp(-core * core);
        }

        public float along(float r) {
            return (farShare + (1f - farShare) / (1f + falloff * r * r)) * (1f - smooth((r - endStart) / (1f - endStart))) * smooth(r / nearSoftShare);
        }
    }

    public static final float DRAWN_LENGTH_MULT = 1.1f;
    public static final float SMOKE_SOFT_DISTANCE = 60f;
    public static final float DUST_CELL = 50f;
    public static final float DUST_WANDER = 14f;
    public static final float DUST_BIG_SHARE = 0.15f;
    public static final float DUST_MAX_ALPHA = 0.9f;
    public static final float DUST_MIN_LIGHT = 0.004f;

    private static final float[] RINGS = {0f, 0.02f, 0.06f, 0.12f, 0.2f, 0.3f, 0.42f, 0.55f, 0.66f, 0.77f, 0.88f, 0.95f, 1f};
    private static final float[] EDGE_RAYS = {0.15f, 0.35f, 0.6f, 1f};
    private static final float[] INSIDE_RAYS = {0.03f, 0.1f, 0.25f, 0.5f, 0.75f};
    private static final int ARC_SEGMENTS = 40;
    private static final int MAX_RAYS = 160;
    private static final int MAX_SAMPLES = 64;
    private static final float MIN_SAMPLE_GAP = 0.25f;
    private static final float MIN_RAY_GAP = 0.02f;
    private static final float INSIDE_RAY_GAP = 0.05f;
    private static final float MIN_EDGE_ARC = 0.3f;
    private static final float MAX_EDGE_ARC = 8f;
    private static final int MAX_BIG_DUST = 2048;
    private static final float[] BIG_DUST = new float[MAX_BIG_DUST * 3];

    // scratch for one cone, drawing runs on one thread
    private static final float[] COS = new float[MAX_RAYS];
    private static final float[] SIN = new float[MAX_RAYS];
    private static final float[] ACROSS = new float[MAX_RAYS];
    private static final int[] SAMPLES = new int[MAX_RAYS];
    private static final float[][] DIST = new float[MAX_RAYS][MAX_SAMPLES];
    private static final float[][] LIGHT = new float[MAX_RAYS][MAX_SAMPLES];

    // additive light with no outline; arc and length are the checked ones, the light is drawn past both so the checked edge sits inside the fade
    // an arc of 360 with edgeStart 1 gives a round light with no edge
    // a blocker takes style.shadow of the light behind it, the rest is drawn unblocked as fill
    public static void draw(Vector2f loc, float angle, float arc, float length, List<VisionBlockers.Blocker> blockers,
                            Style style, Color color, float alpha, SpriteAPI texture, float scroll) {
        float peak = alpha * style.peak;
        if (peak <= 0f) return;

        float half = arc / 2f;
        float drawnHalf = Math.min(180f, half * style.edgeEnd);
        float drawnLength = length * DRAWN_LENGTH_MULT;
        List<VisionBlockers.Blocker> inCone = getBlockersInCone(blockers, loc, angle, drawnHalf, drawnLength);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        if (inCone.isEmpty()) {
            drawLight(loc, angle, half, drawnHalf, drawnLength, inCone, style, color, peak, texture, scroll);
        } else {
            drawLight(loc, angle, half, drawnHalf, drawnLength, inCone, style, color, peak * style.shadow, texture, scroll);
            drawLight(loc, angle, half, drawnHalf, drawnLength, null, style, color, peak * (1f - style.shadow), null, 0f);
        }

        GL11.glPopAttrib();
    }

    // light share at a point, 0 to 1 before peak and alpha
    public static float getLight(Vector2f loc, float angle, float arc, float length, List<VisionBlockers.Blocker> blockers,
                                 Style style, float x, float y) {
        float dx = x - loc.x;
        float dy = y - loc.y;
        float drawnLength = length * DRAWN_LENGTH_MULT;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist >= drawnLength || dist <= 0f) return 0f;

        float off = Math.abs(normalize((float) Math.toDegrees(Math.atan2(dy, dx)) - angle));
        float t = off / (arc / 2f);
        if (t >= style.edgeEnd) return 0f;

        float light = style.across(t) * style.along(dist / drawnLength);
        if (light <= 0f || blockers == null) return light;

        return light * (1f - style.shadow * (1f - getOpenShare(blockers, loc, dx / dist, dy / dist, dist, style.sourceRadius, true)));
    }

    // dust that stays where it is in space and only shows inside the light; points keep their pixel size at every zoom
    public static void drawDust(ViewportAPI viewport, Vector2f loc, float[] angles, float arc, float length, List<VisionBlockers.Blocker> blockers,
                                Style style, Color color, float alpha, float time) {
        if (alpha <= 0f || style.dustGain <= 0f) return;

        float reach = length * DRAWN_LENGTH_MULT;
        float minX = Math.max(viewport.getLLX(), loc.x - reach);
        float minY = Math.max(viewport.getLLY(), loc.y - reach);
        float maxX = Math.min(viewport.getLLX() + viewport.getVisibleWidth(), loc.x + reach);
        float maxY = Math.min(viewport.getLLY() + viewport.getVisibleHeight(), loc.y + reach);
        if (minX >= maxX || minY >= maxY) return;

        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float scale = Global.getSettings().getScreenScaleMult();
        float gain = alpha * style.peak * style.dustGain;
        int big = 0;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_POINT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_POINT_SMOOTH);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glPointSize(Math.max(1f, Math.round(scale)));
        GL11.glBegin(GL11.GL_POINTS);

        for (int cx = (int) Math.floor(minX / DUST_CELL); cx <= (int) Math.floor(maxX / DUST_CELL); cx++) {
            for (int cy = (int) Math.floor(minY / DUST_CELL); cy <= (int) Math.floor(maxY / DUST_CELL); cy++) {
                int hash = hash(cx, cy);
                float phase = unit(hash, 3) * 6.2832f;
                float x = (cx + unit(hash, 0)) * DUST_CELL + DUST_WANDER * (float) Math.sin(time * (0.05f + 0.1f * unit(hash, 4)) + phase);
                float y = (cy + unit(hash, 1)) * DUST_CELL + DUST_WANDER * (float) Math.cos(time * (0.05f + 0.1f * unit(hash, 5)) + phase);

                float light = 0f;
                for (float angle : angles) light = Math.max(light, getLight(loc, angle, arc, length, blockers, style, x, y));
                if (light < DUST_MIN_LIGHT) continue;

                float twinkle = 0.6f + 0.4f * (float) Math.sin(phase + time * (0.5f + 2f * unit(hash, 6)));
                float a = Math.min(DUST_MAX_ALPHA, light * gain * (0.4f + 0.8f * unit(hash, 2)) * twinkle);

                if (unit(hash, 7) < DUST_BIG_SHARE && big < MAX_BIG_DUST) {
                    BIG_DUST[big * 3] = x;
                    BIG_DUST[big * 3 + 1] = y;
                    BIG_DUST[big * 3 + 2] = a;
                    big++;
                    continue;
                }

                GL11.glColor4f(r, g, b, a);
                GL11.glVertex2f(x, y);
            }
        }

        GL11.glEnd();

        if (big > 0) {
            GL11.glPointSize(Math.max(2f, Math.round(2f * scale)));
            GL11.glBegin(GL11.GL_POINTS);
            for (int i = 0; i < big; i++) {
                GL11.glColor4f(r, g, b, BIG_DUST[i * 3 + 2]);
                GL11.glVertex2f(BIG_DUST[i * 3], BIG_DUST[i * 3 + 1]);
            }
            GL11.glEnd();
        }

        GL11.glPopAttrib();
    }

    private static void drawLight(Vector2f loc, float angle, float half, float drawnHalf, float drawnLength, List<VisionBlockers.Blocker> blockers,
                                  Style style, Color color, float alpha, SpriteAPI texture, float scroll) {
        float[] offsets = getRayOffsets(loc, angle, drawnHalf, drawnLength, blockers, style.sourceRadius);
        int rays = offsets.length;

        for (int i = 0; i < rays; i++) {
            double rad = Math.toRadians(angle + offsets[i]);
            COS[i] = (float) Math.cos(rad);
            SIN[i] = (float) Math.sin(rad);
            ACROSS[i] = (offsets[i] + drawnHalf) / (2f * drawnHalf);
            setSamples(i, loc, drawnLength, blockers, style, style.across(Math.abs(offsets[i]) / half));
        }

        boolean textured = texture != null && style.textureShare > 0f;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        drawMesh(loc, rays, color, alpha * (textured ? 1f - style.textureShare : 1f), 0f, 0f, 0f);

        if (textured) {
            float meanWidth = drawnLength * (float) Math.toRadians(drawnHalf);
            float texPerUnit = texture.getTextureWidth() * texture.getTextureHeight() * texture.getHeight() / (texture.getWidth() * meanWidth);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            texture.bindTexture();
            drawMesh(loc, rays, color, alpha * style.textureShare * 2f, texPerUnit, scroll * texture.getTextureWidth(), texture.getTextureHeight());
        }
    }

    // every ray has its own sample distances, so the strip between two rays is stitched by distance
    private static void drawMesh(Vector2f loc, int rays, Color color, float alpha, float texPerUnit, float texOffset, float texHeight) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int ray = 0; ray < rays - 1; ray++) {
            int near = 0;
            int far = 0;
            int nearLast = SAMPLES[ray] - 1;
            int farLast = SAMPLES[ray + 1] - 1;

            while (near < nearLast || far < farLast) {
                boolean stepNear = far >= farLast || (near < nearLast && DIST[ray][near + 1] <= DIST[ray + 1][far + 1]);

                for (int corner = 0; corner < 3; corner++) {
                    int at = corner == 0 || (corner == 2 && stepNear) ? ray : ray + 1;
                    int sample = corner == 0 ? near : corner == 1 ? far : stepNear ? near + 1 : far + 1;
                    float dist = DIST[at][sample];

                    GL11.glColor4f(r, g, b, Math.min(1f, alpha * LIGHT[at][sample]));
                    GL11.glTexCoord2f(dist * texPerUnit - texOffset, texHeight * ACROSS[at]);
                    GL11.glVertex2f(loc.x + COS[at] * dist, loc.y + SIN[at] * dist);
                }

                if (stepNear) near++;
                else far++;
            }
        }
        GL11.glEnd();
    }

    // samples sit on the rings, at the distance of each blocker, at the end of a blocked ray and where a shadow edge starts to fall on an open one
    // a planet is lit up to its middle with the light fading across the near side, smoke ends the ray just before it
    private static void setSamples(int ray, Vector2f loc, float drawnLength, List<VisionBlockers.Blocker> blockers, Style style, float across) {
        float dirX = COS[ray];
        float dirY = SIN[ray];
        float reach = drawnLength;
        float soft = 1f;

        if (blockers != null) for (VisionBlockers.Blocker blocker : blockers) {
            float toX = blocker.x - loc.x;
            float toY = blocker.y - loc.y;
            float along = toX * dirX + toY * dirY;
            float distSq = toX * toX + toY * toY;
            float radiusSq = blocker.radius * blocker.radius;

            if (distSq <= radiusSq) {
                if (blocker.smoke) reach = 0f;
                continue;
            }

            float offSq = distSq - along * along;
            if (along <= 0f || offSq >= radiusSq) continue;

            float halfChord = (float) Math.sqrt(radiusSq - offSq);
            float hit = blocker.smoke ? along - halfChord : along;
            if (hit >= reach) continue;

            reach = hit;
            soft = Math.max(1f, blocker.smoke ? SMOKE_SOFT_DISTANCE : halfChord);
        }

        float[] dist = DIST[ray];
        int count = 0;
        for (float ring : RINGS) if (ring * drawnLength < reach) dist[count++] = ring * drawnLength;
        dist[count++] = reach;

        if (reach < drawnLength) {
            dist[count++] = Math.max(0f, reach - soft);
            dist[count++] = Math.max(0f, reach - soft / 2f);
        }

        if (blockers != null) for (VisionBlockers.Blocker blocker : blockers) {
            float toX = blocker.x - loc.x;
            float toY = blocker.y - loc.y;
            float along = toX * dirX + toY * dirY;
            float distSq = toX * toX + toY * toY;
            float radiusSq = blocker.radius * blocker.radius;
            if (distSq <= radiusSq) continue;

            float nearest = (float) Math.sqrt(distSq) - blocker.radius;
            float touch = (float) Math.sqrt(distSq - radiusSq);
            if (nearest < reach && count < MAX_SAMPLES - 8) dist[count++] = nearest;
            if (touch < reach && count < MAX_SAMPLES - 8) dist[count++] = touch;

            float offSq = distSq - along * along;
            if (along <= 0f || along >= reach || offSq < radiusSq) continue;

            float step = Math.max(0.5f, ((float) Math.sqrt(offSq) - blocker.radius) * (float) Math.sqrt(distSq) / (2f * style.sourceRadius));
            for (float at = along; at < reach && count < MAX_SAMPLES - 1; at = at == along ? along + step : along + (at - along) * 2f) {
                dist[count++] = at;
                if (at - along > step * 8f) break;
            }
        }

        Arrays.sort(dist, 0, count);

        int kept = 1;
        for (int i = 1; i < count; i++) {
            if (dist[i] - dist[kept - 1] < MIN_SAMPLE_GAP) continue;
            dist[kept++] = dist[i];
        }

        for (int i = 0; i < kept; i++) {
            float light = across * style.along(dist[i] / drawnLength);
            if (reach < drawnLength) light *= smooth((reach - dist[i]) / soft);
            if (blockers != null && light > 0f) light *= getOpenShare(blockers, loc, dirX, dirY, dist[i], style.sourceRadius, false);
            LIGHT[ray][i] = light;
        }

        SAMPLES[ray] = kept;
    }

    // 1 in the open, 0 in full shadow; the soft edge starts at the blocker and widens with the distance behind it
    private static float getOpenShare(List<VisionBlockers.Blocker> blockers, Vector2f loc, float dirX, float dirY, float dist,
                                      float sourceRadius, boolean cutBlocked) {
        float open = 1f;

        for (VisionBlockers.Blocker blocker : blockers) {
            float toX = blocker.x - loc.x;
            float toY = blocker.y - loc.y;
            float along = toX * dirX + toY * dirY;
            if (along <= 0f) continue;

            float distSq = toX * toX + toY * toY;
            float radiusSq = blocker.radius * blocker.radius;
            if (distSq <= radiusSq) {
                if (blocker.smoke) return 0f;
                continue;
            }

            float offSq = distSq - along * along;
            if (offSq < radiusSq) {
                if (cutBlocked && dist > along - (float) Math.sqrt(radiusSq - offSq)) return 0f;
                continue;
            }

            if (dist <= along) continue;

            float edge = 2f * sourceRadius * (dist - along) / (float) Math.sqrt(distSq);
            if (edge <= 0f) continue;

            open *= smooth(((float) Math.sqrt(offSq) - blocker.radius) / edge);
            if (open <= 0f) return 0f;
        }

        return open;
    }

    private static List<VisionBlockers.Blocker> getBlockersInCone(List<VisionBlockers.Blocker> blockers, Vector2f loc, float angle,
                                                                  float drawnHalf, float drawnLength) {
        List<VisionBlockers.Blocker> result = new ArrayList<>();
        if (blockers == null) return result;

        for (VisionBlockers.Blocker blocker : blockers) {
            float toX = blocker.x - loc.x;
            float toY = blocker.y - loc.y;
            float dist = (float) Math.sqrt(toX * toX + toY * toY);
            if (dist - blocker.radius >= drawnLength) continue;

            if (dist > blocker.radius) {
                float wide = (float) Math.toDegrees(Math.asin(blocker.radius / dist)) + MAX_EDGE_ARC;
                float off = Math.abs(normalize((float) Math.toDegrees(Math.atan2(toY, toX)) - angle));
                if (off - wide >= drawnHalf) continue;
            }

            result.add(blocker);
        }

        return result;
    }

    // even rays plus a few on each side of every blocker edge and across its near side, so a shadow edge does not step from ray to ray while the cone turns
    private static float[] getRayOffsets(Vector2f loc, float angle, float drawnHalf, float drawnLength,
                                         List<VisionBlockers.Blocker> blockers, float sourceRadius) {
        int count = ARC_SEGMENTS + 1;
        int extra = blockers == null ? 0 : blockers.size() * (2 * (EDGE_RAYS.length + INSIDE_RAYS.length + 2) + 1);
        float[] offsets = new float[Math.min(MAX_RAYS, count + extra)];

        for (int i = 0; i <= ARC_SEGMENTS; i++) offsets[i] = -drawnHalf + 2f * drawnHalf * i / ARC_SEGMENTS;

        if (blockers != null) for (VisionBlockers.Blocker blocker : blockers) {
            float toX = blocker.x - loc.x;
            float toY = blocker.y - loc.y;
            float dist = (float) Math.sqrt(toX * toX + toY * toY);
            if (dist <= blocker.radius) continue;

            float middle = normalize((float) Math.toDegrees(Math.atan2(toY, toX)) - angle);
            float tangent = (float) Math.toDegrees(Math.asin(blocker.radius / dist));
            float behind = Math.max(0f, drawnLength - dist);
            float edgeArc = (float) Math.toDegrees(2f * sourceRadius * behind / (dist * drawnLength));
            edgeArc = Math.max(MIN_EDGE_ARC, Math.min(MAX_EDGE_ARC, edgeArc));

            count = addOffset(offsets, count, middle, drawnHalf);

            for (int side = -1; side <= 1; side += 2) {
                float edge = middle + side * tangent;
                count = addOffset(offsets, count, edge - side * INSIDE_RAY_GAP, drawnHalf);
                count = addOffset(offsets, count, edge, drawnHalf);
                for (float share : INSIDE_RAYS) count = addOffset(offsets, count, edge - side * tangent * share, drawnHalf);
                for (float share : EDGE_RAYS) count = addOffset(offsets, count, edge + side * edgeArc * share, drawnHalf);
            }
        }

        Arrays.sort(offsets, 0, count);

        int kept = 1;
        for (int i = 1; i < count; i++) {
            if (offsets[i] - offsets[kept - 1] < MIN_RAY_GAP) continue;
            offsets[kept++] = offsets[i];
        }

        return Arrays.copyOf(offsets, kept);
    }

    private static int addOffset(float[] offsets, int count, float offset, float drawnHalf) {
        if (drawnHalf >= 180f) offset = normalize(offset);
        if (offset <= -drawnHalf || offset >= drawnHalf || count >= offsets.length) return count;

        offsets[count] = offset;
        return count + 1;
    }

    private static int hash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h;
    }

    private static float unit(int hash, int salt) {
        int h = hash + salt * 0x9e3779b9;
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h & 0xffffff) / (float) 0x1000000;
    }

    private static float normalize(float angle) {
        angle %= 360f;
        if (angle > 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }

    private static float smooth(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return t * t * (3f - 2f * t);
    }
}
