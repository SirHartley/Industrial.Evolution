package livecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.combat.CombatEngine;

import java.util.List;

// Starts a real battle from the campaign: a real CombatState, the combat plugins from settings.json and campaign
// fleets. BattleDriver takes over inside combat. Add to Groups: case "battle": all.addAll(Battle.steps()); break;
public final class Battle {
    private Battle() {
    }

    // Faction fleets from the real fleet factory, so doctrine, weapons and carriers are what a player meets.
    static final String PLAYER_FACTION = Factions.HEGEMONY;
    static final String ENEMY_FACTION = Factions.TRITACHYON;
    static final int FLEETS_PER_SIDE = 2;
    static final float FLEET_POINTS = 250f;

    public static List<Step> steps() {
        return List.of(Battle::start);
    }

    static void fill(Ctx c, CampaignFleetAPI into, String faction) {
        for (int i = 0; i < FLEETS_PER_SIDE; i++) {
            FleetParamsV3 p = new FleetParamsV3(null, null, faction, 1f, FleetTypes.PATROL_LARGE, FLEET_POINTS, 0f, 0f, 0f, 0f, 0f, 0f);
            p.ignoreMarketFleetSizeMult = true;
            CampaignFleetAPI made = FleetFactoryV3.createFleet(p);
            for (FleetMemberAPI m : made.getFleetData().getMembersListCopy()) {
                made.getFleetData().removeFleetMember(m);
                into.getFleetData().addFleetMember(m);
            }
        }
    }

    static void start(Ctx c) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        player.getCargo().addCrew(20000);
        player.getCargo().addSupplies(5000);
        player.getCargo().addFuel(2000);
        CampaignFleetAPI enemy = Global.getFactory().createEmptyFleet(ENEMY_FACTION, "Live check enemy", true);
        fill(c, player, PLAYER_FACTION);
        fill(c, enemy, ENEMY_FACTION);
        for (CampaignFleetAPI f : new CampaignFleetAPI[]{player, enemy}) {
            f.getFleetData().setSyncNeeded();
            f.getFleetData().syncIfNeeded();
        }
        player.getContainingLocation().addEntity(enemy);
        enemy.setLocation(player.getLocation().x + 150f, player.getLocation().y);
        c.expect(player.getFleetData().getNumMembers() > 5 && enemy.getFleetData().getNumMembers() > 5,
                "both fleets have ships: " + player.getFleetData().getNumMembers() + " and " + enemy.getFleetData().getNumMembers());

        // A battle started from a script opens without the deployment dialog anyway. To test that dialog, call
        // CombatState.showDeployDialog() from BattleDriver (the G key calls the same method) and check
        // isShowingDeploymentDialog() before measuring.
        ((CombatEngine) CombatEngine.getInstance()).setShowDeploymentDialog(false);
        Global.getSector().getCampaignUI().startBattle(new BattleCreationContext(player, FleetGoal.ATTACK, enemy, FleetGoal.ATTACK));
        c.note("battle started");
        // the campaign Runner does not tick in combat; BattleDriver ends the run
        c.frames(1000000);
    }
}
