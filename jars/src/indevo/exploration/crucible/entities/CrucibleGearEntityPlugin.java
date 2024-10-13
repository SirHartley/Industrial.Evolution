package indevo.exploration.crucible.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;

import java.awt.*;

public class CrucibleGearEntityPlugin extends BaseCustomEntityPlugin {

    public Color color;
    public float rotationSpeed;
    public float size;
    public transient SpriteAPI sprite;
    public boolean orbitSet = false;

    public static class CrucibleGearParams{
        public Color color;
        public float rotationSpeed;
        public float size;

        public CrucibleGearParams(Color color, float rotationSpeed, float size) {
            this.color = color;
            this.rotationSpeed = rotationSpeed;
            this.size = size;
        }
    }

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        this.color = ((CrucibleGearParams) pluginParams).color;
        this.rotationSpeed = ((CrucibleGearParams) pluginParams).rotationSpeed;
        this.size = ((CrucibleGearParams) pluginParams).size;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (entity.hasTag(BaseCrucibleEntityPlugin.TAG_ENABLED)){

            entity.setFacing(entity.getFacing() + rotationSpeed);

            if (!orbitSet){
                //set new orbit with a tenth of the orbit time
                entity.setCircularOrbit(entity.getOrbitFocus(), entity.getCircularOrbitAngle(), entity.getCircularOrbitRadius(), entity.getCircularOrbitPeriod() / 20f);
                orbitSet = true;
            }
        }
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);
        if (sprite == null) sprite = Global.getSettings().getSprite("IndEvo", "gearwheel");

        sprite.setColor(color);
        float locY = entity.getLocation().y;

        if (layer != CampaignEngineLayers.TERRAIN_6) {
            sprite.setColor(color.darker().darker());
            locY -= (size * 0.020f);
        }

        sprite.setAngle(entity.getFacing());
        sprite.setSize(size, size);
        sprite.renderAtCenter(entity.getLocation().x, locY);
    }
}
