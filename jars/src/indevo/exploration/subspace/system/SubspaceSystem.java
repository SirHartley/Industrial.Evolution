package indevo.exploration.subspace.system;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.CategoryGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class SubspaceSystem {
    public static final String name = "Subspace";

    //subspace is static and

    public static void gen() {
        StarSystemAPI subspace = Global.getSector().createStarSystem(name);
        SectorEntityToken center = subspace.initNonStarCenter();

        //tech
        subspace.setName(name);
        subspace.setBaseName(name);
        subspace.setProcgen(false);
        subspace.setType(StarSystemGenerator.StarSystemType.NEBULA);
        subspace.getLocation().set(new Vector2f(0f, 0f));
        subspace.setDoNotShowIntelFromThisLocationOnMap(true);

        //tags
        subspace.addTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER);
        subspace.addTag(Tags.DO_NOT_RESPAWN_PLAYER_IN);
        subspace.addTag(Tags.NOT_RANDOM_MISSION_TARGET);
        subspace.addTag(Tags.THEME_HIDDEN);
        subspace.addTag(Tags.THEME_UNSAFE);

        //visuals
        //public static SectorEntityToken addNebulaFromPNG(String image, float centerX, float centerY, LocationAPI location,
        //			String category, String key, int tilesWide, int tilesHigh, StarAge age) {

        //Misc.addNebulaFromPNG("", center.getLocation().x, center.getLocation().y, subspace, "nebula_blue", "test", 26000,26000, StarAge.YOUNG);

        spawnOmegaBlackholes(subspace);

        subspace.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
        subspace.setLightColor(new Color(127, 0, 255, 255));

       /* SectorEntityToken field = subspace.addTerrain(Terrain.MAGNETIC_FIELD,
                new MagneticFieldTerrainPlugin.MagneticFieldParams(10000f, // terrain effect band width
                        200f, // terrain effect middle radius
                        center, // entity that it's around
                        350f, // visual band start
                        650f, // visual band end
                        new Color(60, 60, 150, 90), // base color
                        1f, // probability to spawn aurora sequence, checked once/day when no aurora in progress
                        new Color(130, 60, 150, 130),
                        new Color(150, 30, 120, 150),
                        new Color(200, 50, 130, 190),
                        new Color(250, 70, 150, 240),
                        new Color(200, 80, 130, 255),
                        new Color(75, 0, 160, 255),
                        new Color(127, 0, 255, 255)
                ));
        field.setCircularOrbit(center, 0, 0, 75);*/
    }

    private static void spawnOmegaBlackholes(StarSystemAPI system){
        for (LocationAPI loc : Global.getSector().getAllLocations()){
            if (loc.hasTag(Tags.HAS_CORONAL_TAP)){

            }
        }
    }

    public StarSystemAPI get(){
        return Global.getSector().getStarSystem(name);
    }
}
