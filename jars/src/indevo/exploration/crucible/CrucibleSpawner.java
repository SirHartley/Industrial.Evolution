package indevo.exploration.crucible;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTiledTerrain;
import com.fs.starfarer.api.impl.campaign.terrain.NebulaTerrainPlugin;
import com.fs.starfarer.api.util.IntervalUtil;
import indevo.utils.helper.MiscIE;
import javafx.scene.shape.Circle;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

public class CrucibleSpawner {
    //crucible always spawns in nebula with the most planets

    public static final float MIN_DIST_AROUND_CRUCIBLE = 10000f;

    public static void devSpawn(){

        int planetAmt = 0;
        int oldPlanetAmt = 0;
        StarSystemAPI nebulaSystem = null;
        StarSystemAPI oldNebulaSystem = null;

        for (StarSystemAPI system : Global.getSector().getStarSystems()){
            if (!system.isNebula()) continue;

            int amt = system.getPlanets().size();

            //any age
            if (amt > planetAmt){
                planetAmt = amt;
                nebulaSystem = system;
            }

            //old
            if (system.getConstellation().getAge() == StarAge.OLD){
               if (amt > oldPlanetAmt){
                    oldPlanetAmt = amt;
                    oldNebulaSystem = system;
                }
            }
        }

        //we prefer old nebula
        StarSystemAPI targetSystem = oldNebulaSystem != null && oldPlanetAmt >= 2 ? oldNebulaSystem : nebulaSystem;
        if (targetSystem == null) return;

        //get loc away from all planets
        List<PlanetAPI> planets = targetSystem.getPlanets();
        Vector2f spawnLoc = null;
        float radius = 0f;

        //we just check alongside a circle that we increase by 500 units each time, checking every 500 px (scale with radius) times per increment to see if there's a fitting location
        while (spawnLoc == null){

        }


        SectorEntityToken y = Global.getSector().getPlayerFleet().getContainingLocation().addCustomEntity(null, null, "IndEvo_yeetopult", null, null);
        y.setCircularOrbit(t, 0, t.getRadius() + 80f, 30f);
    }



    public SectorEntityToken spawnCrucible(LocationAPI loc, Vector2f pos){
        SectorEntityToken t = loc.addCustomEntity(null, null, "IndEvo_crucible_bottom", null, null);
        t.setLocation(pos.x, pos.y);
        t = loc.addCustomEntity(null, null, "IndEvo_crucible_top", null, null);
        t.setLocation(pos.x, pos.y);
        CrucibleStationEntityPlugin.generateMagneticField(t, 1f, 300f);


        CampaignTerrainAPI nebula = null;
        for (CampaignTerrainAPI terrain : loc.getTerrainCopy()){
            if (terrain.getPlugin() instanceof NebulaTerrainPlugin) nebula = terrain;
            break;
        }

        if (nebula != null) {
            NebulaTerrainPlugin nebulaPlugin = (NebulaTerrainPlugin) nebula.getPlugin();
            NebulaEditor editor = new NebulaEditor(nebulaPlugin);
            editor.clearArc(pos.x, pos.y, 0, 600f, 0f, 360f);
        }

        return t;
    }
}
