package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class ShuttlePath {

    public interface Waypoint {
        void getPosition(Vector2f out);

        void getDirection(Vector2f out);

        // the entity this point moves with, null for a fixed point
        default SectorEntityToken getFrame() {
            return null;
        }
    }

    public static class PadWaypoint implements Waypoint {
        public final ShuttleAnchor anchor;
        private final Waypoint reference;
        private final float angleCos;
        private final float angleSin;
        private final float tiltCos;
        private final float tiltSin;
        private final boolean inbound;
        private final Vector2f base = new Vector2f();

        // the pad sits angle degrees off the live direction toward reference, so it turns with orbiting anchors
        public PadWaypoint(ShuttleAnchor anchor, Waypoint reference, float angle, float tilt, boolean inbound) {
            this.anchor = anchor;
            this.reference = reference;
            this.angleCos = (float) Math.cos(Math.toRadians(angle));
            this.angleSin = (float) Math.sin(Math.toRadians(angle));
            this.tiltCos = (float) Math.cos(Math.toRadians(tilt));
            this.tiltSin = (float) Math.sin(Math.toRadians(tilt));
            this.inbound = inbound;
        }

        @Override
        public SectorEntityToken getFrame() {
            return anchor.entity;
        }

        @Override
        public void getPosition(Vector2f out) {
            getBase(base);
            Vector2f loc = anchor.getLocation();
            float depth = anchor.getRadius() * anchor.getPadDepth();
            out.set(loc.x + base.x * depth, loc.y + base.y * depth);
        }

        @Override
        public void getDirection(Vector2f out) {
            getBase(base);
            float x = base.x * tiltCos - base.y * tiltSin;
            float y = base.x * tiltSin + base.y * tiltCos;
            if (inbound) out.set(-x, -y);
            else out.set(x, y);
        }

        private void getBase(Vector2f out) {
            float x = 1f;
            float y = 0f;
            Vector2f loc = anchor.getLocation();
            reference.getPosition(out);
            float dx = out.x - loc.x;
            float dy = out.y - loc.y;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > 0.001f) {
                x = dx / len;
                y = dy / len;
            }

            out.set(x * angleCos - y * angleSin, x * angleSin + y * angleCos);
        }
    }

    public static class EntityWaypoint implements Waypoint {
        private final SectorEntityToken entity;

        public EntityWaypoint(SectorEntityToken entity) {
            this.entity = entity;
        }

        @Override
        public SectorEntityToken getFrame() {
            return entity;
        }

        @Override
        public void getPosition(Vector2f out) {
            out.set(entity.getLocation());
        }

        @Override
        public void getDirection(Vector2f out) {
            out.set(1f, 0f);
        }
    }

    // beside a star on a lane between two orbiting anchors, pushed out to the clearance on one fixed side, so the live lane never sweeps through the star
    public static class StarPassWaypoint implements Waypoint {
        private final SectorEntityToken a;
        private final SectorEntityToken b;
        private final SectorEntityToken star;
        private final float clearance;
        private final float bend;
        private final float offset;
        private final boolean reverse;
        private final float side;

        // bend and offset are along the left normal of a to b
        public StarPassWaypoint(SectorEntityToken a, SectorEntityToken b, SectorEntityToken star, float clearance, float bend, float offset, boolean reverse) {
            this.a = a;
            this.b = b;
            this.star = star;
            this.clearance = clearance;
            this.bend = bend;
            this.offset = offset;
            this.reverse = reverse;

            Vector2f base = new Vector2f();
            float across = getBase(base, 0f);
            this.side = across != 0f ? Math.signum(across) : (bend < 0f ? -1f : 1f);
        }

        @Override
        public void getPosition(Vector2f out) {
            float across = getBase(out, offset) * side;
            if (across >= clearance) return;

            Vector2f la = a.getLocation();
            Vector2f lb = b.getLocation();
            float len = Math.max(1f, Misc.getDistance(la, lb));
            float push = (clearance - across) * getFade(len) * side;
            out.x -= (lb.y - la.y) / len * push;
            out.y += (lb.x - la.x) / len * push;
        }

        @Override
        public void getDirection(Vector2f out) {
            Vector2f.sub(b.getLocation(), a.getLocation(), out);
            float len = out.length();
            if (len > 0.0001f) out.scale((reverse ? -1f : 1f) / len);
            else out.set(1f, 0f);
        }

        // point on the bent lane abreast of the star, returns its signed distance from the star along the left normal
        private float getBase(Vector2f out, float extra) {
            Vector2f la = a.getLocation();
            Vector2f lb = b.getLocation();
            Vector2f ls = star.getLocation();
            float cx = lb.x - la.x;
            float cy = lb.y - la.y;
            float len = Math.max(1f, (float) Math.sqrt(cx * cx + cy * cy));
            float nx = -cy / len;
            float ny = cx / len;
            float u = Math.max(0.2f, Math.min(0.8f, ((ls.x - la.x) * cx + (ls.y - la.y) * cy) / (len * len)));
            float shift = bend * 3f * u * (1f - u) + extra;

            out.set(la.x + cx * u + nx * shift, la.y + cy * u + ny * shift);
            return (out.x - ls.x) * nx + (out.y - ls.y) * ny;
        }

        // no push once the star is no longer between the anchors
        private float getFade(float len) {
            Vector2f la = a.getLocation();
            Vector2f lb = b.getLocation();
            Vector2f ls = star.getLocation();
            float u = ((ls.x - la.x) * (lb.x - la.x) + (ls.y - la.y) * (lb.y - la.y)) / (len * len);
            return Math.max(0f, Math.min(1f, Math.min(u, 1f - u) / 0.1f));
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
        public SectorEntityToken getFrame() {
            return entity;
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
        public SectorEntityToken getFrame() {
            return segment.getFrame(t);
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

        public abstract SectorEntityToken getStartFrame();

        public abstract SectorEntityToken getEndFrame();

        // share of the end frame in how a point at t moves when the frames move
        public float getEndWeight(float t) {
            return 0f;
        }

        public SectorEntityToken getFrame(float t) {
            return getEndWeight(t) < 0.5f ? getStartFrame() : getEndFrame();
        }

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

        @Override
        public SectorEntityToken getStartFrame() {
            return start.getFrame();
        }

        @Override
        public SectorEntityToken getEndFrame() {
            return end.getFrame();
        }

        // p0 and p1 move with the start, p2 and p3 with the end
        @Override
        public float getEndWeight(float t) {
            return t * t * (3f - 2f * t);
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

        @Override
        public SectorEntityToken getStartFrame() {
            return centerEntity;
        }

        @Override
        public SectorEntityToken getEndFrame() {
            return centerEntity;
        }
    }

    public static class Cursor {
        int segment = 0;
        // arc length fraction inside the segment, so rebuilding a table never moves the craft
        float fraction = 0f;

        public void reset() {
            segment = 0;
            fraction = 0f;
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

    public void refresh(Cursor cursor) {
        if (segments.isEmpty()) return;

        Segment segment = segments.get(cursor.segment);
        length -= segment.getLength();
        segment.rebuild(tmpA, tmpB);
        length += segment.getLength();
    }

    public float getLength() {
        return length;
    }

    public float getTraveled(Cursor cursor) {
        float traveled = 0f;
        for (int i = 0; i < cursor.segment; i++) traveled += segments.get(i).getLength();
        return traveled + segments.get(cursor.segment).getLength() * cursor.fraction;
    }

    public void place(Cursor cursor, float distance) {
        float remaining = Math.max(0f, distance);
        for (int i = 0; i < segments.size(); i++) {
            float segmentLength = segments.get(i).getLength();
            if (remaining <= segmentLength || i == segments.size() - 1) {
                cursor.segment = i;
                cursor.fraction = segmentLength > 0f ? Math.min(1f, remaining / segmentLength) : 1f;
                return;
            }

            remaining -= segmentLength;
        }
    }

    // returns true once the cursor passes the end of the last segment
    public boolean move(Cursor cursor, float distance) {
        float remaining = distance;
        while (true) {
            Segment segment = segments.get(cursor.segment);
            float segmentLength = segment.getLength();
            float left = segmentLength * (1f - cursor.fraction);
            if (remaining < left && segmentLength > 0f) {
                cursor.fraction += remaining / segmentLength;
                return false;
            }

            if (cursor.segment == segments.size() - 1) {
                cursor.fraction = 1f;
                return true;
            }

            remaining -= Math.max(0f, left);
            cursor.segment++;
            cursor.fraction = 0f;
            refresh(cursor);
        }
    }

    public void getPoint(Cursor cursor, Vector2f out) {
        Segment segment = segments.get(cursor.segment);
        segment.evaluate(segment.getT(cursor.fraction * segment.getLength()), out);
    }

    public void getTangent(Cursor cursor, Vector2f out) {
        Segment segment = segments.get(cursor.segment);
        float t = segment.getT(cursor.fraction * segment.getLength());
        float a = Math.max(0f, t - 0.005f);
        float b = Math.min(1f, t + 0.005f);
        segment.evaluate(a, tmpA);
        segment.evaluate(b, tmpB);
        Vector2f.sub(tmpB, tmpA, out);
        float len = out.length();
        if (len > 0.0001f) out.scale(1f / len);
        else out.set(1f, 0f);
    }

    public Segment getSegment(Cursor cursor) {
        return segments.get(cursor.segment);
    }

    public float getEndWeight(Cursor cursor) {
        Segment segment = segments.get(cursor.segment);
        return segment.getEndWeight(segment.getT(cursor.fraction * segment.getLength()));
    }

    public float getScaleMult(Cursor cursor) {
        Segment segment = segments.get(cursor.segment);
        return segment.getScaleMult(segment.getT(cursor.fraction * segment.getLength()));
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

    public void getTangent(float distance, Vector2f out) {
        getPoint(Math.max(0f, distance - 1.5f), tmpA);
        getPoint(Math.min(length, distance + 1.5f), tmpB);
        Vector2f.sub(tmpB, tmpA, out);
        float len = out.length();
        if (len > 0.0001f) out.scale(1f / len);
        else out.set(1f, 0f);
    }
}
