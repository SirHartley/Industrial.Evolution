package indevo.ambience.shuttletraffic.movement;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import indevo.ambience.shuttletraffic.Shuttle;
import indevo.ambience.shuttletraffic.ShuttleTrail;
import org.lwjgl.util.vector.Vector2f;

public class FollowMover {

    private static final float BLEND_TIME = 1f;
    // closing speed into the slot at most, a long gap takes longer than BLEND_TIME
    private static final float BLEND_SPEED = 20f;

    private final Shuttle shuttle;
    private final Shuttle leader;
    private final float slotBack;
    private final float slotSide;
    private final float noise;
    private final boolean straggler;
    private float extraLag = 0f;
    // a craft that flies back into formation starts where it was and closes the gap over BLEND_TIME or longer
    private final Vector2f blendStart = new Vector2f();
    private boolean blendPending = false;
    private float blendX = 0f;
    private float blendY = 0f;
    private float blend = 0f;
    private float blendTime = BLEND_TIME;
    private SectorEntityToken frame;

    private final Vector2f base = new Vector2f();
    private final Vector2f tangent = new Vector2f();

    public FollowMover(Shuttle shuttle, Shuttle leader, float slotBack, float slotSide, float noise, boolean straggler) {
        this.shuttle = shuttle;
        this.leader = leader;
        this.slotBack = slotBack;
        this.slotSide = slotSide;
        this.noise = noise;
        this.straggler = straggler;
    }

    public void advance(float amount) {
        if (leader.isDone() || leader.isDown()) {
            shuttle.finish();
            return;
        }

        shuttle.copyLook(leader);

        if (straggler) {
            float target = Math.abs(leader.getTurnSpeed()) > 25f ? 30f : 0f;
            if (extraLag < target) extraLag = Math.min(target, extraLag + 15f * amount);
            else extraLag = Math.max(target, extraLag - 10f * amount);
        }

        float age = shuttle.getAge();
        float offset = shuttle.getPhase() * 6.28f;
        float side = slotSide + noise * (float) Math.sin(age * 0.7f + offset);
        float back = slotBack + extraLag + noise * 0.5f * (float) Math.sin(age * 0.5f + offset * 2f);
        leader.getTrail().getHistoryPoint(back, leader.pos, leader.getFacing(), base, tangent);

        float slotX = base.x - tangent.y * side;
        float slotY = base.y + tangent.x * side;
        if (blendPending) {
            blendPending = false;
            blendX = blendStart.x - slotX;
            blendY = blendStart.y - slotY;
            blendTime = Math.max(BLEND_TIME, (float) Math.sqrt(blendX * blendX + blendY * blendY) / BLEND_SPEED);
            blend = 1f;
        }

        blend = Math.max(0f, blend - amount / blendTime);
        shuttle.pos.set(slotX + blendX * blend, slotY + blendY * blend);
        shuttle.updateFacing(amount, Misc.getAngleInDegrees(tangent));
        frame = leader.getFrame();
        ShuttleTrail leadTrail = leader.getTrail();
        shuttle.getTrail().shift(leadTrail.getShiftX(), leadTrail.getShiftY());
        shuttle.pushTrailAtRear();
    }

    public void blendFrom(Vector2f pos) {
        blendStart.set(pos);
        blendPending = true;
    }

    public float getNoise() {
        return noise;
    }

    public boolean isStraggler() {
        return straggler;
    }

    public Shuttle getLeader() {
        return leader;
    }

    public SectorEntityToken getFrame() {
        return frame;
    }

    public float getSlotBack() {
        return slotBack;
    }

    public float getSlotSide() {
        return slotSide;
    }
}
