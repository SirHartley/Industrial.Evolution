package indevo.dialogue.research.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.EnumSet;

import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * @Author: RuddyGreat
 * Thank you for supporting my incredibly stupid ideas!
 */

public class ShipSpriteGlitcherOverlay extends BaseCombatLayeredRenderingPlugin implements DamageTakenModifier {

    public final ShipAPI attachedTo;
    public final SpriteAPI sprite;
    public final IntervalUtil glitchInterval = new IntervalUtil(0.2f, 0.6f);
    public IntervalUtil currentRuntimeInterval = new IntervalUtil(1f, 2f);

    private final float spriteRadius;
    public int maxGlitches = 20;

    public static class SpriteGlitchData {

        final float height;
        final float maxXJitter;
        final float maxLifetime;
        final float creationTimestamp;

        final IntervalUtil xJitterInterval = new IntervalUtil(0.1f, 0.3f);
        final Color colour;

        float yPos;
        float currXOffest;

        public SpriteGlitchData(float height, float maxXJitter, float creationTimestamp, float maxLifetime, float startY, Color colour) {
            this.height = height;
            this.maxXJitter = maxXJitter;
            this.creationTimestamp = creationTimestamp;
            this.maxLifetime = maxLifetime;
            this.yPos = startY;
            this.colour = colour;
            currXOffest = MathUtils.getRandomNumberInRange(-maxXJitter, maxXJitter);
        }

        public void advance(float amount) {
            xJitterInterval.advance(amount);
            if (xJitterInterval.intervalElapsed()) {
                currXOffest = MathUtils.clamp(currXOffest += MathUtils.getRandomNumberInRange(-maxXJitter, maxXJitter), -maxXJitter, maxXJitter);
            }
        }

        public boolean isExpired() {
            return (Global.getCombatEngine().getTotalElapsedTime(false) - creationTimestamp) > maxLifetime;
        }
    }

    private final ArrayList<SpriteGlitchData> glitchData = new ArrayList<>();

    public ShipSpriteGlitcherOverlay(ShipAPI attachedTo) {
        this.attachedTo = attachedTo;
        this.sprite = attachedTo.getSpriteAPI();

        Vector2f spriteCenterCoords = new Vector2f(sprite.getCenterX(), sprite.getCenterY());
        //compare distance to corners from pivot point
        spriteRadius = Math.max(
                //bl vs br
                Math.max(new Vector2f(spriteCenterCoords.x - 0, spriteCenterCoords.y - 0).length(),
                        new Vector2f(spriteCenterCoords.x - sprite.getWidth(), spriteCenterCoords.y - 0).length()
                ),
                //tr vs tl
                Math.max(new Vector2f(spriteCenterCoords.x - sprite.getWidth(), spriteCenterCoords.y - sprite.getHeight()).length(),
                        new Vector2f(spriteCenterCoords.x - 0, spriteCenterCoords.y - sprite.getHeight()).length()
                )
        );

        for (int i = 0; i <= maxGlitches; i++) {
            glitchData.add(
                    new SpriteGlitchData(
                            MathUtils.getRandomNumberInRange(spriteRadius / 5, spriteRadius / 3),
                            20,
                            Global.getCombatEngine().getTotalElapsedTime(false),
                            MathUtils.getRandomNumberInRange(2, 12),
                            MathUtils.getRandomNumberInRange(-spriteRadius, spriteRadius), //todo will need to somehow weight this so that the glitches are spread out a lttle more
                            new Color(MathUtils.getRandomNumberInRange(0, 255), MathUtils.getRandomNumberInRange(0, 255), MathUtils.getRandomNumberInRange(0, 255), MathUtils.getRandomNumberInRange(0, 255))
                    )
            );
        }
    }

    //todo need to do a shit ton of number tuning
    //probably some manual controls as well?
    //intensity to up the speed (as a mult that gets passed into amount?)
    //max num. of copies
    //max x jitter of new copies

    //todo need to mess with the colours a little, I don't really like how they come out
    //particularly when they end up in yellow

    public static final float RUNTIME_UNIT = 0.75f;
    public static final float MAX_RUNTIME = 3.5f;
    public static final float DAMAGE_PER_RUNTIME_UNIT = 1000f;
    public static final float SHIELD_REDUCTION_MULT = 0.33f;

    @Override
    public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
        if (damage.getDamage() < DAMAGE_PER_RUNTIME_UNIT) return null;
        float amt = damage.getDamage() / DAMAGE_PER_RUNTIME_UNIT;
        float seconds = Math.min(amt * RUNTIME_UNIT, MAX_RUNTIME);
        if (shieldHit) seconds *= SHIELD_REDUCTION_MULT;
        currentRuntimeInterval = new IntervalUtil(seconds, seconds);

        return null;
    }

    @Override
    public void advance(float amount) {
        //this is dirty af
        if (attachedTo.getTravelDrive().isActive()) currentRuntimeInterval = new IntervalUtil(1, 2); //I dont care that this is trash code
        if (currentRuntimeInterval == null) return;

        currentRuntimeInterval.advance(amount);
        if (currentRuntimeInterval.intervalElapsed()) currentRuntimeInterval = null;

        //add new glitches
        if (glitchData.size() <= 20) {
            glitchData.add(
                    new SpriteGlitchData(
                            MathUtils.getRandomNumberInRange(spriteRadius / 5, spriteRadius / 3),
                            MathUtils.getRandomNumberInRange(10, 40),
                            Global.getCombatEngine().getTotalElapsedTime(false),
                            MathUtils.getRandomNumberInRange(2, 12),
                            MathUtils.getRandomNumberInRange(-spriteRadius, spriteRadius), //todo will need to somehow weight this so that the glitches are spread out a lttle more
                            new Color(MathUtils.getRandomNumberInRange(0, 255), MathUtils.getRandomNumberInRange(0, 255), MathUtils.getRandomNumberInRange(0, 255), MathUtils.getRandomNumberInRange(0, 255))
                    )
            );
        }

        //advance and expire old glitch data
        for (SpriteGlitchData data : new ArrayList<>(glitchData)) {
            if (data.isExpired()) {
                glitchData.remove(data);
            }
            data.advance(amount);
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (currentRuntimeInterval == null) return;

        final float halfWidth = sprite.getWidth() / 2f;
        final float halfHeight = sprite.getHeight() / 2f;
        final float angle = sprite.getAngle();
        float alphaMult = 1 - (currentRuntimeInterval.getElapsed() / currentRuntimeInterval.getIntervalDuration());

        //debug flags, fuck with them with your debugger
        boolean debug = false; //master control, disable this to disable all flags
        boolean debugDrawVertices = true; //draws the vertices at the edge of each glitch segment, unaffected by xJitter
        boolean debugDrawBoundingBoxes = true; //draws the bounding boxes of both the main sprite & each glitch segment
        boolean noRender = false; //skips rendering the segment sprites if true

        //calculate the difference between the sprite's pivot center & it's actual center
        Vector2f diffBetweenShipCenterAndSpriteCenter = VectorUtils.rotate(new Vector2f(halfWidth - sprite.getCenterX(), halfHeight - sprite.getCenterY()), angle).negate(null);

        //this should probably actually be a list of data holder classes that store the coords at each end + tex coords + side length?
        //solves the need to check the specific intersections if I just pack the texture coords in there with them
        //but it currently works fine so eh
        //sprite bounding box coords in world space
        Vector2f BBbl = VectorUtils.rotate(new Vector2f(-halfWidth, -halfHeight), angle).translate(attachedTo.getLocation().x, attachedTo.getLocation().y).translate(-diffBetweenShipCenterAndSpriteCenter.x, -diffBetweenShipCenterAndSpriteCenter.y);
        Vector2f BBbr = VectorUtils.rotate(new Vector2f(halfWidth, -halfHeight), angle).translate(attachedTo.getLocation().x, attachedTo.getLocation().y).translate(-diffBetweenShipCenterAndSpriteCenter.x, -diffBetweenShipCenterAndSpriteCenter.y);
        Vector2f BBtr = VectorUtils.rotate(new Vector2f(halfWidth, halfHeight), angle).translate(attachedTo.getLocation().x, attachedTo.getLocation().y).translate(-diffBetweenShipCenterAndSpriteCenter.x, -diffBetweenShipCenterAndSpriteCenter.y);
        Vector2f BBtl = VectorUtils.rotate(new Vector2f(-halfWidth, halfHeight), angle).translate(attachedTo.getLocation().x, attachedTo.getLocation().y).translate(-diffBetweenShipCenterAndSpriteCenter.x, -diffBetweenShipCenterAndSpriteCenter.y);

        for (SpriteGlitchData data : glitchData) {

            //how this works
            //each glitchData object has a y position (relative to the ship's center) & a height
            //the bounding box of the sprite is checked against the upper & lower bounds of the glitch & intersection points + tex coords are calculated
            //then they get rendered

            ArrayList<Vector2f> points = new ArrayList<>();
            ArrayList<Vector2f> texCoords = new ArrayList<>();

            float upperBound = attachedTo.getLocation().y + data.yPos + (data.height / 2f);
            float lowerBound = attachedTo.getLocation().y + data.yPos - (data.height / 2f);

            //check for intersections along each edge of the bounding box
            boolean LBintesectsTop = BBtl.y >= lowerBound && lowerBound >= BBtr.y || BBtr.y >= lowerBound && lowerBound >= BBtl.y;
            boolean LBintesectsLeft = BBtl.y >= lowerBound && lowerBound >= BBbl.y || BBbl.y >= lowerBound && lowerBound >= BBtl.y;
            boolean LBintesectsRight = BBtr.y >= lowerBound && lowerBound >= BBbr.y || BBbr.y >= lowerBound && lowerBound >= BBtr.y;
            boolean LBintesectsBottom = BBbl.y >= lowerBound && lowerBound >= BBbr.y || BBbr.y >= lowerBound && lowerBound >= BBbl.y;

            boolean UBintesectsTop = BBtl.y >= upperBound && upperBound >= BBtr.y || BBtr.y > upperBound && upperBound >= BBtl.y;
            boolean UBintesectsLeft = BBtl.y >= upperBound && upperBound >= BBbl.y || BBbl.y > upperBound && upperBound >= BBtl.y;
            boolean UBintesectsRight = BBtr.y >= upperBound && upperBound >= BBbr.y || BBbr.y > upperBound && upperBound >= BBtr.y;
            boolean UBintesectsBottom = BBbl.y >= upperBound && upperBound >= BBbr.y || BBbr.y > upperBound && upperBound >= BBbl.y;

            boolean noLBIntersection = !LBintesectsTop && !LBintesectsLeft && !LBintesectsRight && !LBintesectsBottom;
            boolean noUBIntersection = !UBintesectsTop && !UBintesectsLeft && !UBintesectsRight && !UBintesectsBottom;

            if (noLBIntersection && noUBIntersection) continue;

            //bound intersection checks
            //LB checks
            if (LBintesectsTop) {
                Vector2f intersectionPoint = calculateIntersectionPoint(lowerBound, BBtl, BBtr);
                float texX = (MathUtils.getDistance(BBtl, intersectionPoint) / sprite.getWidth()) * sprite.getTextureWidth();

                points.add(intersectionPoint);
                texCoords.add(new Vector2f(texX, sprite.getTextureHeight()));
            }

            if (LBintesectsLeft) {
                Vector2f intersectionPoint = calculateIntersectionPoint(lowerBound, BBtl, BBbl);
                float texY = (MathUtils.getDistance(BBbl, intersectionPoint) / sprite.getHeight()) * sprite.getTextureHeight();

                points.add(intersectionPoint);
                texCoords.add(new Vector2f(0, texY));
            }

            if (LBintesectsRight) {
                Vector2f intersectionPoint = calculateIntersectionPoint(lowerBound, BBtr, BBbr);
                float texY = (MathUtils.getDistance(BBbr, intersectionPoint) / sprite.getHeight()) * sprite.getTextureHeight();

                points.add(intersectionPoint);
                texCoords.add(new Vector2f(sprite.getTextureWidth(), texY));
            }

            if (LBintesectsBottom) {
                Vector2f intersectionPoint = calculateIntersectionPoint(lowerBound, BBbl, BBbr);
                float texX = (MathUtils.getDistance(BBbl, intersectionPoint) / sprite.getWidth()) * sprite.getTextureWidth();

                points.add(intersectionPoint);
                texCoords.add(new Vector2f(texX, 0));
            }

            //UB checks
            if (UBintesectsTop) {
                Vector2f intersectionPoint = calculateIntersectionPoint(upperBound, BBtl, BBtr);
                float texX = (MathUtils.getDistance(BBtl, intersectionPoint) / sprite.getWidth()) * sprite.getTextureWidth();

                points.add(intersectionPoint);
                texCoords.add(new Vector2f(texX, sprite.getTextureHeight()));
            }

            if (UBintesectsLeft) {
                Vector2f intersectionPoint = calculateIntersectionPoint(upperBound, BBtl, BBbl);
                float texY = (MathUtils.getDistance(BBbl, intersectionPoint) / sprite.getHeight()) * sprite.getTextureHeight();

                points.add(intersectionPoint);
                texCoords.add(new Vector2f(0, texY));
            }

            if (UBintesectsRight) {
                Vector2f intersectionPoint = calculateIntersectionPoint(upperBound, BBtr, BBbr);
                float texY = (MathUtils.getDistance(BBbr, intersectionPoint) / sprite.getHeight()) * sprite.getTextureHeight();

                points.add(intersectionPoint);
                texCoords.add(new Vector2f(sprite.getTextureWidth(), texY));
            }

            if (UBintesectsBottom) {
                Vector2f intersectionPoint = calculateIntersectionPoint(upperBound, BBbl, BBbr);
                float texX = (MathUtils.getDistance(BBbl, intersectionPoint) / sprite.getWidth()) * sprite.getTextureWidth();

                points.add(intersectionPoint);
                texCoords.add(new Vector2f(texX, 0));
            }

            //sort the lists of points
            ArrayList<Vector2f> sortedPoints = new ArrayList<>();
            ArrayList<Vector2f> sortedTexCoords = new ArrayList<>();

            //add point 0 to the already sorted list, remove it from the unsorted list
            sortedPoints.add(points.get(0));
            sortedTexCoords.add(texCoords.get(0));

            //loop through every point
            for (Vector2f point : points) {

                //get the point with the lowest angle
                float lastAngle = Float.MAX_VALUE;
                Vector2f nextPoint = null;
                for (Vector2f otherPoint : points) {
                    //ignore the point we're on
                    if (point.equals(otherPoint)) continue;
                    //ignore already sorted points
                    if (sortedPoints.contains(otherPoint)) continue;
                    float nextAngle = VectorUtils.getAngle(point, otherPoint);
                    if (nextAngle < lastAngle) {
                        //set the next point
                        lastAngle = nextAngle;
                        nextPoint = otherPoint;
                    }
                }

                if (nextPoint == null) continue;

                //add them to the sorted points list
                sortedPoints.add(nextPoint);
                sortedTexCoords.add(texCoords.get(points.indexOf(nextPoint)));
            }

            //this renders stuff
            if (!(noRender && debug)) {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                Misc.setColor(data.colour, alphaMult);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                sprite.bindTexture();
                GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                for (int i = 0; i < sortedPoints.size(); i++) {
                    vector2fTogl(sortedPoints.get(i).translate(data.currXOffest, 0), sortedTexCoords.get(i));
                }
                GL11.glEnd();
            }

            //debug visualisation - glitch segment vertices + bounds
            if (debug) {
                if (debugDrawVertices) {
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                    Misc.setColor(data.colour, 255);
                    GL11.glPointSize(10);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GL11.glBegin(GL11.GL_POINTS);
                    for (Vector2f sortedPoint : sortedPoints) {
                        vector2fTogl(sortedPoint);
                    }
                    GL11.glEnd();
                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                }

                if (debugDrawBoundingBoxes) {
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                    Misc.setColor(data.colour, 255);
                    GL11.glLineWidth(2);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GL11.glBegin(GL11.GL_LINE_LOOP);
                    for (Vector2f sortedPoint : sortedPoints) {
                        vector2fTogl(sortedPoint);
                    }
                    GL11.glEnd();
                }
            }
        }

        //debug visualisation - sprite bounding box
        if (debug) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            Misc.setColor(Color.WHITE);
            GL11.glLineWidth(2);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            vector2fTogl(BBbl);
            vector2fTogl(BBbr);
            vector2fTogl(BBtr);
            vector2fTogl(BBtl);
            GL11.glEnd();
        }
    }

    //copied from https://stackoverflow.com/questions/563198/how-do-you-detect-where-two-line-segments-intersect
    //and turned into java
    public Vector2f calculateIntersectionPoint(float y, Vector2f p2, Vector2f p3) {

        Vector2f toReturn = new Vector2f();

        Vector2f p0 = new Vector2f(attachedTo.getLocation().x - spriteRadius, y);
        Vector2f p1 = new Vector2f(attachedTo.getLocation().x + spriteRadius, y);

        float s1_x = p1.x - p0.x;
        float s1_y = p1.y - p0.y;
        float s2_x = p3.x - p2.x;
        float s2_y = p3.y - p2.y;

        float s = (-s1_y * (p0.x - p2.x) + s1_x * (p0.y - p2.y)) / (-s2_x * s1_y + s1_x * s2_y);
        float t = (s2_x * (p0.y - p2.y) - s2_y * (p0.x - p2.x)) / (-s2_x * s1_y + s1_x * s2_y);

        //this isn't acutally needed because the intersections are checked earlier on
        if (s >= 0 && s <= 1 && t >= 0 && t <= 1) {
            //intersection detected
            toReturn.x = p0.x + (t * s1_x);
            toReturn.y = p0.y + (t * s1_y);
            return toReturn;
        }
        //no intersection
        return null;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_LAYER);
    }

    @Override
    public float getRenderRadius() {
        return Float.MAX_VALUE;
    }

    public static void vector2fTogl(Vector2f vector) {
        glVertex2f(vector.x, vector.y);
    }

    public static void vector2fTogl(Vector2f vector, float texX, float texY) {
        glTexCoord2f(texX, texY);
        glVertex2f(vector.x, vector.y);
    }

    public static void vector2fTogl(Vector2f vector, Vector2f texCoords) {
        glTexCoord2f(texCoords.x, texCoords.y);
        glVertex2f(vector.x, vector.y);
    }

}