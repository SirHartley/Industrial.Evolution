package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.combat.CombatEngine;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// registered in data/config/settings.json, only active in the petsfight group: deploys everything, then wins the fight
public class PetsBattlePlugin extends BaseEveryFrameCombatPlugin {
    private static final Logger log = Global.getLogger(PetsBattlePlugin.class);
    private CombatEngineAPI engine;
    private boolean active;
    private int frame = 0;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        String groups = System.getProperty("ie.check", "");
        active = Arrays.asList(groups.split(",")).contains("petsfight") && !engine.isSimulation() && engine.isInCampaign();
        if (!active) return;
        ((CombatEngine) engine).setShowDeploymentDialog(false);
        log.info("LIVE_CHECK note: pets battle plugin active");
    }

    private int deploy(int owner, float y, float facing) {
        CombatFleetManagerAPI fm = engine.getFleetManager(owner);
        int i = 0;
        for (FleetMemberAPI m : fm.getReservesCopy()) {
            fm.spawnFleetMember(m, new Vector2f(((i % 20) - 10f) * 450f, y), facing, 0f);
            i++;
        }
        return i;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (!active) return;
        frame++;
        if (engine.isPaused()) engine.setPaused(false);
        if (frame == 2) {
            int a = deploy(0, -4000f, 90f), b = deploy(1, 4000f, 270f);
            log.info("LIVE_CHECK note: pets battle deployed " + a + " and " + b + " ships");
        }
        ShipAPI player = engine.getPlayerShip();
        if (player != null && player.getShipAI() == null) player.resetDefaultAI();
        if (frame == 180) {
            PetsFightChecks.fought = true;
            for (ShipAPI ship : new ArrayList<>(engine.getShips())) {
                if (ship.getOwner() != 1 || ship.isHulk()) continue;
                engine.applyDamage(ship, ship.getLocation(), 1000000f, DamageType.HIGH_EXPLOSIVE, 0f, true, false, null);
            }
            log.info("LIVE_CHECK note: pets battle enemy destroyed");
        }
        if (frame == 900) {
            log.info("LIVE_CHECK note: pets battle ended by the plugin");
            engine.endCombat(1f, FleetSide.PLAYER);
        }
    }
}
