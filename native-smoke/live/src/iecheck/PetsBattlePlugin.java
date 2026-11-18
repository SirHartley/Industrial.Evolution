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
    private boolean pods;
    private boolean sig;
    private int frame = 0;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        String groups = System.getProperty("ie.check", "");
        sig = Arrays.asList(groups.split(",")).contains("petssig");
        pods = Arrays.asList(groups.split(",")).contains("petspods") || sig;
        active = (Arrays.asList(groups.split(",")).contains("petsfight") || pods) && !engine.isSimulation() && engine.isInCampaign();
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
        if (frame == 2 || (pods && frame % 10 == 2 && frame < 100)) {
            int a = deploy(0, -4000f, 90f), b = deploy(1, 4000f, 270f);
            log.info("LIVE_CHECK note: pets battle deployed " + a + " and " + b + " ships");
        }
        // petssig: what the signature talents do in combat, Mend and Ctrl both take half their hull at frame 40
        if (sig && (frame == 30 || frame == 40 || frame == 110)) {
            for (ShipAPI ship : new ArrayList<>(engine.getShips())) {
                if (ship.getOwner() != 0 || ship.isFighter() || ship.isHulk() || ship.getFleetMember() == null) continue;
                String n = ship.getFleetMember().getShipName();
                if (frame == 40 && ("ISS Mend".equals(n) || "ISS Ctrl".equals(n))) ship.setHitpoints(ship.getMaxHitpoints() * 0.5f);
                if (frame == 30) {
                    log.info("LIVE_CHECK note: sigcombat " + n + " time mult " + ship.getMutableStats().getTimeMult().getModifiedValue()
                            + ", system " + (ship.getSystem() == null ? "none" : ship.getSystem().getId() + " max ammo " + ship.getSystem().getMaxAmmo())
                            + ", explosion radius x" + ship.getMutableStats().getDynamic().getValue(com.fs.starfarer.api.impl.campaign.ids.Stats.EXPLOSION_RADIUS_MULT)
                            + " damage x" + ship.getMutableStats().getDynamic().getValue(com.fs.starfarer.api.impl.campaign.ids.Stats.EXPLOSION_DAMAGE_MULT)
                            + ", captain " + (ship.getCaptain() == null ? "none" : ship.getCaptain().getNameString() + " " + ship.getCaptain().getPersonalityAPI().getId() + " level " + ship.getCaptain().getStats().getLevel() + " default " + ship.getCaptain().isDefault()));
                }
                if (frame == 110 && ("ISS Mend".equals(n) || "ISS Ctrl".equals(n))) {
                    log.info("LIVE_CHECK note: sigcombat " + n + " hull " + (ship.getHitpoints() / ship.getMaxHitpoints()) + " of max, 70 frames after half");
                }
            }
        }
        ShipAPI player = engine.getPlayerShip();
        if (player != null && player.getShipAI() == null) player.resetDefaultAI();
        // petspods: the player loses every ship whose name starts with ISS Pod
        if (pods && (frame == 120 || frame == 150)) {
            int lost = 0;
            for (ShipAPI ship : new ArrayList<>(engine.getShips())) {
                if (ship.getOwner() != 0 || ship.isHulk() || ship.getFleetMember() == null || ship.getFleetMember().getShipName() == null || !ship.getFleetMember().getShipName().startsWith("ISS Pod")) continue;
                engine.applyDamage(ship, ship.getLocation(), 1000000f, DamageType.HIGH_EXPLOSIVE, 0f, true, false, null);
                lost++;
            }
            log.info("LIVE_CHECK note: pets battle destroyed " + lost + " pod ships");
        }
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
