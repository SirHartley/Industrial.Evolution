package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class ShuttlePath {

    public interface Waypoint {
        void getPosition(Vector2f out);

        void getDirection(Vector2f out);
    }

    public static class PadWaypoint implements Waypoint {
        public final ShuttleAnchor anchor;
        private final float dirX;
        private final float dirY;
        private final boolean inbound;

        public PadWaypoint(ShuttleAnchor anchor, float angle, boolean inbound) {
            this.anchor = anchor;
            this.dirX = (float) Math.cos(Math.toRadians(angle));
            this.dirY = (float) Math.sin(Math.toRadians(angle));
            this.inbound = inbound;
        }

        @Override
        public void getPosition(Vector2f out) {
            Vector2f loc = anchor.getLocation();
            float depth = anchor.getRadius() * anchor.getPadDepth();
            out.set(loc.x + dirX * depth, loc.y + dirY * depth);
        }

        @Override
        public void getDirection(Vector2f out) {
            if (inbound) out.set(-dirX, -dirY);
            else out.set(dirX, dirY);
        }
    }

    public static class FixedWaypoint implements Waypoint {
        private final float x;
        private final float y;
        private final float dirX;
        private final float dirY;

        public FixedWaypoint(Vector2f position, Vector2f direction) {
            this.x = position.x;
            this.y = position.y;
            float len = direction.length();
            this.dirX = len > 0.001f ? direction.x / len : 1f;
            this.dirY = len > 0.001f ? direction.y / len : 0f;
        }

        @Override
        public void getPosition(Vector2f out) {
            out.set(x, y);
        }

        @Override
        public void getDirection(Vector2f out) {
            out.set(dirX, dirY);
        }
    }

    public static class RelativeWaypoint implements Waypoint {
        private final SectorEntityToken entity;
        private final float offsetX;
        private final float offsetY;
        private final float dirX;
        private final float dirY;

        public RelativeWaypoint(SectorEntityToken entity, float offsetX, float offsetY, float angle) {
            this.entity = entity;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.dirX = (float) Math.cos(Math.toRadians(angle));
            this.dirY = (float) Math.sin(Math.toRadians(angle));
        }

        @Override
        public void getPosition(Vector2f out) {
            Vector2f loc = entity.getLocation();
            out.set(loc.x + offsetX, loc.y + offsetY);
        }

        @Override
        public void getDirection(Vector2f out) {
            out.set(dirX, dirY);
        }
    }

    public static class SegmentWaypoint implements Waypoint {
        private final Segment segment;
        private final float t;
        private final Vector2f a = new Vector2f();
        private final Vector2f b = new Vector2f();

        public SegmentWaypoint(Segment segment, float t) {
            this.segment = segment;
            this.t = t;
        }

        @Override
        public void getPosition(Vector2f out) {
            segment.evaluate(t, out);
        }

        @Override
        public void getDirection(Vector2f out) {
            segment.evaluate(Math.max(0f, t - 0.01f), a);
            segment.evaluate(Math.min(1f, t + 0.01f), b);
            Vector2f.sub(b, a, out);
            float len = out.length();
            if (len > 0.0001f) out.scale(1f / len);
            else out.set(1f, 0f);
        }
    }

    public abstract static class Segment {
        private final float[] cumulative;
        private float length = 0f;

        protected Segment(int samples) {
            this.cumulative = new float[Math.max(4, samples) + 1];
        }

        public abstract void evaluate(float t, Vector2f out);

        public float getScaleMult(float t) {
            return 1f;
        }

        public float getLength() {
            return length;
        }

        void rebuild(Vector2f prev, Vector2f next) {
            int samples = cumulative.length - 1;
            evaluate(0f, prev);
            cumulative[0] = 0f;

            for (int i = 1; i <= samples; i++) {
                evaluate(i / (float) samples, next);
                cumulative[i] = cumulative[i - 1] + Vector2f.sub(next, prev, null).length();
                prev.set(next);
            }

            length = cumulative[samples];
        }

        float getT(float distance) {
            int samples = cumulative.length - 1;
            if (length <= 0f || distance <= 0f) return 0f;
            if (distance >= length) return 1f;

            int lo = 0;
            int hi = samples;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (cumulative[mid] <= distance) lo = mid;
                else hi = mid;
            }

            float span = cumulative[hi] - cumulative[lo];
            float frac = span > 0f ? (distance - cumulative[lo]) / span : 0f;
            return (lo + frac) / samples;
        }
    }

    public static class BezierSegment extends Segment {
        private final Waypoint start;
        private final Waypoint end;
        private final float startHandle;
        private final float endHandle;
        private final float bend;
        private final Vector2f p0 = new Vector2f();
        private final Vector2f p3 = new Vector2f();
        private final Vector2f d0 = new Vector2f();
        private final Vector2f d3 = new Vector2f();

        public BezierSegment(Waypoint start, Waypoint end, float startHandle, float endHandle, float bend) {
            super(16);
            this.start = start;
            this.end = end;
            this.startHandle = startHandle;
            this.endHandle = endHandle;
            this.bend = bend;
        }

        @Override
        public void evaluate(float t, Vector2f out) {
            start.getPosition(p0);
            end.getPosition(p3);
            start.getDirection(d0);
            end.getDirection(d3);

            float cx = p3.x - p0.x;
            float cy = p3.y - p0.y;
            float chord = (float) Math.sqrt(cx * cx + cy * cy);
            float nx = chord > 0.001f ? -cy / chord : 0f;
            float ny = chord > 0.001f ? cx / chord : 0f;

            float p1x = p0.x + d0.x * startHandle + nx * bend;
            float p1y = p0.y + d0.y * startHandle + ny * bend;
            float p2x = p3.x - d3.x * endHandle + nx * bend;
            float p2y = p3.y - d3.y * endHandle + ny * bend;

            float u = 1f - t;
            float a = u * u * u;
            float b = 3f * u * u * t;
            float c = 3f * u * t * t;
            float d = t * t * t;

            out.set(a * p0.x + b * p1x + c * p2x + d * p3.x, a * p0.y + b * p1y + c * p2y + d * p3.y);
        }
    }

    public static class CircleSegment extends Segment {
        private final SectorEntityToken centerEntity;
        private final float offsetX;
        private final float offsetY;
        private final float radiusX;
        private final float radiusY;
        private final float cos;
        private final float sin;
        private final float startAngle;
        private final float sweep;
        private final float scaleBump;

        public CircleSegment(SectorEntityToken centerEntity, float offsetX, float offsetY, float radius, float startAngle, float sweep, float scaleBump) {
            this(centerEntity, offsetX, offsetY, radius, radius, 0f, startAngle, sweep, scaleBump);
        }

        // center is centerEntity + offset, or the offset alone when centerEntity is null; radiusY and rotation make an ellipse
        public CircleSegment(SectorEntityToken centerEntity, float offsetX, float offsetY, float radiusX, float radiusY, float rotation,
                             float startAngle, float sweep, float scaleBump) {
            super(Math.max(16, (int) (Math.abs(sweep) / 22.5f)));
            this.centerEntity = centerEntity;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.radiusX = radiusX;
            this.radiusY = radiusY;
            this.cos = (float) Math.cos(Math.toRadians(rotation));
            this.sin = (float) Math.sin(Math.toRadians(rotation));
            this.startAngle = startAngle;
            this.sweep = sweep;
            this.scaleBump = scaleBump;
        }

        @Override
        public void evaluate(float t, Vector2f out) {
            float cx = offsetX;
            float cy = offsetY;
            if (centerEntity != null) {
                cx += centerEntity.getLocation().x;
                cy += centerEntity.getLocation().y;
            }

            double angle = Math.toRadians(startAngle + sweep * t);
            float ex = (float) Math.cos(angle) * radiusX;
            float ey = (float) Math.sin(angle) * radiusY;
            out.set(cx + ex * cos - ey * sin, cy + ex * sin + ey * cos);
        }

        @Override
        public float getScaleMult(float t) {
            return 1f + scaleBump * (float) Math.sin(Math.PI * t);
        }
    }

    private final List<Segment> segments = new ArrayList<>();
    private final Vector2f tmpA = new Vector2f();
    private final Vector2f tmpB = new Vector2f();
    private float length = 0f;

    public ShuttlePath add(Segment segment) {
        segments.add(segment);
        return this;
    }

    public void rebuild() {
        length = 0f;
        for (Segment segment : segments) {
            segment.rebuild(tmpA, tmpB);
            length += segment.getLength();
        }
    }

    public float getLength() {
        return length;
    }

    public void getPoint(float distance, Vector2f out) {
        float remaining = Math.max(0f, distance);
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (remaining <= segment.getLength() || i == segments.size() - 1) {
                segment.evaluate(segment.getT(remaining), out);
                return;
            }

            remaining -= segment.getLength();
        }

        out.set(0f, 0f);
    }

    public float getScaleMult(float distance) {
        float remaining = Math.max(0f, distance);
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (remaining <= segment.getLength() || i == segments.size() - 1) return segment.getScaleMult(segment.getT(remaining));
            remaining -= segment.getLength();
        }

        return 1f;
    }

    public void getTangent(float distance, Vector2f out) {
        getPoint(Math.max(0f, distance - 1.5f), tmpA);
        getPoint(Math.min(length, distance + 1.5f), tmpB);
        Vector2f.sub(tmpB, tmpA, out);
        float len = out.length();
        if (len > 0.0001f) out.scale(1f / len);
        else out.set(1f, 0f);
    }
}
