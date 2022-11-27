package indevo.exploration.minefields;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static indevo.exploration.minefields.conditions.IndEvo_MineFieldCondition.PLANET_KEY;

public class IndEvo_MineCustomEntityPlugin extends BaseCustomEntityPlugin {

    public static float GLOW_FREQUENCY = 0.3f; // on/off cycles per second
    public static float COLOUR_CHANGE_RANGE = 300f;

    transient protected SpriteAPI sprite;
    transient protected SpriteAPI glow;
    transient protected float facing = 0.0F;

    protected boolean pause = false;

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

    public float getRenderRange() {
        return entity.getRadius() + 100f;
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

        boolean colourChange = false;

        MarketAPI m = entity.getMemoryWithoutUpdate().contains(PLANET_KEY) ? entity.getMemoryWithoutUpdate().getEntity(PLANET_KEY).getMarket()
                : entity.getOrbitFocus() != null && entity.getOrbitFocus().getOrbitFocus() != null ? entity.getOrbitFocus().getOrbitFocus().getMarket()
                : null;

        if (m != null && !m.isPlanetConditionMarketOnly()) {
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            boolean friend = fleet.getFaction().getRelationshipLevel(m.getFactionId()).isAtWorst(RepLevel.INHOSPITABLE) && fleet.isTransponderOn();
            boolean inRange = Misc.getDistance(fleet, entity) < COLOUR_CHANGE_RANGE;

            colourChange = friend && inRange;
        }

        Color glowColor = colourChange ? new Color(20, 255, 20, 255) : new Color(255, 30, 0, 255);
        glow.setColor(glowColor);

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
        this.facing += this.rotation * amount;

        //---------

        phase += amount * GLOW_FREQUENCY * freqMult;
        while (phase > 1) {
            pause = !pause;
            phase--;
        }
    }
}
