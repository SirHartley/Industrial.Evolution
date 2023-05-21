package indevo.items.consumables.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomEntitySpecAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.loading.CampaignPingSpec;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class SpikeEntityPlugin extends BaseCustomEntityPlugin {

    public static float GLOW_FREQUENCY = 0.2f; // on/off cycles per second
    public static float TRIGGER_DETECTION_RANGE = 350f;
    public static float TIME_TO_ARM = 2f;

    private enum State {
        PASSIVE,
        ARMED,
        FIRING
    }

    private State state = State.PASSIVE;

    transient protected SpriteAPI sprite;
    transient protected SpriteAPI glow;
    transient protected float facing = 0.0F;

    protected float amt = 0f;
    protected float phase = 0f;
    protected float freqMult = 1f;

    protected float rotation;
    protected float radius;

    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        this.radius = entity.getRadius();
        this.rotation = (float) Math.random() * 10.0F - 5.0F;
        this.facing = (float) Math.random() * 360.0F;
        //this.entity = entity;
        readResolve();
    }

    public java.lang.Object readResolve() {
        glow = Global.getSettings().getSprite("IndEvo", "mineGlow");
        freqMult = (float) (2f * Math.random());

        this.sprite = Global.getSettings().getSprite("IndEvo", "mine4");
        return this;
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        float alphaMult = viewport.getAlphaMult();
        alphaMult *= entity.getSensorFaderBrightness();
        alphaMult *= entity.getSensorContactFaderBrightness();
        if (alphaMult <= 0f) return;

        CustomEntitySpecAPI spec = entity.getCustomEntitySpec();
        if (spec == null) return;

        Vector2f loc = entity.getLocation();

        this.sprite.setSize(radius * 2, radius * 2);
        sprite.setAngle(rotation);
        sprite.setAlphaMult(alphaMult);
        sprite.setNormalBlend();
        sprite.renderAtCenter(loc.x, loc.y);

        //if(!pause){
        float glowAlpha = 0f;
        if (phase < 0.5f) glowAlpha = phase * 2f;
        if (phase >= 0.5f) glowAlpha = (1f - (phase - 0.5f) * 2f);

        float glowAngle1 = (((phase * 1.3f) % 1) - 0.5f) * 12f;
        float glowAngle2 = (((phase * 1.9f) % 1) - 0.5f) * 12f;

        glow.setColor(getColour());

        glow.setSize(radius * 2, radius * 2);
        glow.setAlphaMult(alphaMult * glowAlpha);
        glow.setAdditiveBlend();

        glow.setAngle(entity.getFacing() - 90f + glowAngle1);
        glow.renderAtCenter(loc.x, loc.y);

        glow.setAngle(entity.getFacing() - 90f + glowAngle2);
        glow.setAlphaMult(alphaMult * glowAlpha * 0.5f);
        glow.renderAtCenter(loc.x, loc.y);
        // }
    }

    public void advance(float amount) {
        super.advance(amount);
        amt += amount;

        if (state == State.PASSIVE && amt > TIME_TO_ARM) {
            state = State.ARMED;
            entity.addFloatingText("Armed", getColour(), 0.5f);
        }

        if (state == State.ARMED) {
            for (CampaignFleetAPI fleet : Misc.getNearbyFleets(entity, TRIGGER_DETECTION_RANGE)) {
                if (fleet.isHostileTo(Global.getSector().getPlayerFleet())) {
                    state = State.FIRING;
                    break;
                }
            }
        }

        if (state == State.FIRING) {
            entity.addAbility(Ids.ABILITY_MINE_INTERDICT);
            entity.getAbility(Ids.ABILITY_MINE_INTERDICT).activate();
        }

        //blinky ---------

        this.facing += this.rotation * amount;
        phase += amount * GLOW_FREQUENCY * freqMult;
        if (phase >= 1) {
            if (state == State.ARMED) showRangePing();
            phase--;
        }
    }

    public Color getColour() {
        switch (state) {
            case ARMED:
                return new Color(200, 150, 0, 255);
            case FIRING:
                return new Color(255, 30, 0, 255);
            default:
                return new Color(20, 20, 255, 255);
        }
    }

    protected void showRangePing() {
        SectorEntityToken.VisibilityLevel vis = entity.getVisibilityLevelToPlayerFleet();
        if (vis == SectorEntityToken.VisibilityLevel.NONE || vis == SectorEntityToken.VisibilityLevel.SENSOR_CONTACT)
            return;

        float range = TRIGGER_DETECTION_RANGE;
        CampaignPingSpec custom = new CampaignPingSpec();
        //custom.setColor(getColour());
        custom.setUseFactionColor(true);
        custom.setWidth(7);
        custom.setMinRange(range - 100f);
        custom.setRange(200);
        custom.setDuration(2f);
        custom.setAlphaMult(0.25f);
        custom.setInFraction(0.2f);
        custom.setNum(1);

        Global.getSector().addPing(entity, custom);


    }

}
