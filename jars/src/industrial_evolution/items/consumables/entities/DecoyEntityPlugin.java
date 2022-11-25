package industrial_evolution.items.consumables.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CustomEntitySpecAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import industrial_evolution.campaign.ids.IndEvo_ids;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class DecoyEntityPlugin extends BaseCustomEntityPlugin {

    public static float GLOW_FREQUENCY = 5f; // on/off cycles per second
    public static float TIME_TO_ARM = 10f;

    private enum State {
        PASSIVE,
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
    }

    public void advance(float amount) {
        super.advance(amount);
        amt += amount;

        if (state == State.PASSIVE && amt > TIME_TO_ARM) state = State.FIRING;

        if (state == State.FIRING) {
            entity.addAbility(IndEvo_ids.ABILITY_MINE_DECOY);
            entity.getAbility(IndEvo_ids.ABILITY_MINE_DECOY).activate();
        }

        //blinky ---------

        this.facing += this.rotation * amount;
        phase += amount * GLOW_FREQUENCY * freqMult;
        if (phase >= 1) {
            phase--;
        }
    }

    public Color getColour() {
        if (state == State.FIRING) {
            return new Color(255, 30, 0, 255);
        }

        return new Color(20, 160, 200, 255);
    }
}