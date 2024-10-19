package indevo.exploration.crucible.terrain;

import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin;

import java.awt.*;

public class CrucibleFieldTerrainPlugin extends MagneticFieldTerrainPlugin {

    protected float alphaMult = 0f;

    public void setAlphaMult(float alphaMult) {
        this.alphaMult = alphaMult;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        renderer.render(viewport.getAlphaMult() * alphaMult);
    }

    public static CampaignTerrainAPI generate(SectorEntityToken token, float flareProbability, float width) {
        StarSystemAPI system = token.getStarSystem();

        int baseIndex = (int) (CrucibleFieldTerrainPlugin.baseColors.length * StarSystemGenerator.random.nextFloat());
        int auroraIndex = (int) (CrucibleFieldTerrainPlugin.auroraColors.length * StarSystemGenerator.random.nextFloat());

        float bandWidth = token.getRadius() + width;
        float midRadius = 350f;
        float visStartRadius = token.getRadius();
        float visEndRadius = token.getRadius() + width + 50f;

        CampaignTerrainAPI magField = (CampaignTerrainAPI) system.addTerrain("crucible_field",
                new MagneticFieldTerrainPlugin.MagneticFieldParams(bandWidth, // terrain effect band width
                        midRadius, // terrain effect middle radius
                        token, // entity that it's around
                        visStartRadius, // visual band start
                        visEndRadius, // visual band end
                        CrucibleFieldTerrainPlugin.baseColors[baseIndex], // base color
                        flareProbability, // probability to spawn aurora sequence, checked once/day when no aurora in progress
                        CrucibleFieldTerrainPlugin.auroraColors[auroraIndex]
                ));

        magField.setCircularOrbit(token, 0, 0, 100);
        magField.setName("Radiation field");
        return magField;
    }

    public static Color[] baseColors = {
            new Color(255, 50, 50, 100),
            //new Color(50, 30, 100, 30),
            //new Color(75, 105, 165, 75)
    };

    public static Color[][] auroraColors = {
            {new Color(235, 100, 140),
                    new Color(210, 110, 180),
                    new Color(190, 140, 150),
                    new Color(210, 190, 140),
                    new Color(170, 200, 90),
                    new Color(160, 230, 65),
                    new Color(220, 70, 20)},
            {new Color(110, 20, 50, 130),
                    new Color(120, 30, 150, 150),
                    new Color(130, 50, 200, 190),
                    new Color(150, 70, 250, 240),
                    new Color(130, 80, 200, 255),
                    new Color(160, 0, 75),
                    new Color(255, 0, 127)},
            {new Color(140, 180, 90),
                    new Color(190, 145, 130),
                    new Color(225, 110, 165),
                    new Color(240, 55, 95),
                    new Color(250, 0, 45),
                    new Color(240, 0, 20),
                    new Color(150, 0, 10)},
            {new Color(40, 180, 90),
                    new Color(90, 145, 130),
                    new Color(145, 110, 165),
                    new Color(160, 55, 95),
                    new Color(130, 0, 45),
                    new Color(130, 0, 20),
                    new Color(150, 0, 10)},
            {new Color(110, 20, 50, 130),
                    new Color(120, 30, 150, 150),
                    new Color(130, 50, 200, 190),
                    new Color(150, 70, 250, 240),
                    new Color(130, 80, 200, 255),
                    new Color(160, 0, 75),
                    new Color(255, 0, 127)},
            {new Color(140, 60, 55),
                    new Color(155, 85, 65),
                    new Color(165, 105, 175),
                    new Color(180, 130, 90),
                    new Color(190, 150, 105),
                    new Color(205, 175, 120),
                    new Color(220, 200, 135)},
    };
}
