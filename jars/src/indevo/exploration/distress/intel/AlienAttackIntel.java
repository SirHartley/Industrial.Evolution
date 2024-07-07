package indevo.exploration.distress.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class AlienAttackIntel extends GhostShipIntelBase {

    public static final float MARINE_LOSS_ON_COMBAT_SUCCESS = 0.2f;

    private int crewLosses;
    private int marineLosses;
    private boolean won = true;

    public AlienAttackIntel(FleetMemberAPI member, boolean known) {
        super(member, known);
        rollBattleResults();
        applyLosses();
    }

    @Override
    public String getName() {
        return "Alien Attack";
    }

    @Override
    public String getIcon() {
        return "graphics/icons/markets/alien_life.png";
    }

    @Override
    protected String getDescriptionText() {
        String shipName = member.getShipName();
        if (known && won) {
            return "Despite every possible preparation being made, the crew of the " + shipName + " were unprepared for the sheer speed and violence when the hostile xenolife known to inhabit the ship finally made its attack. " +
                    "Boiling out of disused holds and airlocks, seemingly everywhere at once, the onboard defenses placed in anticipation of such an assault were quickly overwhelmed. Your marine forces were alert and ready for action, " +
                    "quickly linking up to form kill-teams and deploying heavy weapons with extreme prejudice to clear the halls, but nonetheless some losses were suffered - hapless crew torn apart by needle-teeth, marines dragged " +
                    "screaming to their deaths in darkened ventilation shafts and corridors. Finally the comms feed quiets, punctuated by CP-fire as your marines clear the final few holds and bilges of the vessel... Although the xenoforms " +
                    "have been beaten back for now, you're not certain the ship will ever be truly safe.";
        } else if (known) {
            return "Despite extensive security protocols and back-up plans, the crew of the " + shipName + " were woefully underprepared for the looming threat aboard. All it took was one mistake on a routine security " +
                    "lockup and the ship's bestial inhabitants took their opportunity; swarming out of disused holds and feasting on your hapless crew as they struggled to reach their equipment only to be picked off one by one," +
                    " dragged screaming into ventilation shafts by sleek, blue-black predator xenoforms. You watch helplessly from your flagship's bridge as the vessel twists out of formation with your fleet, shaking violently " +
                    "with internal stresses as builtin failsafes and automated defenses are overwhelmed. Finally, it flares into a ball of eye-searing white - one of the crew aboard must have triggered the self-destruct rather than " +
                    "risk a potential rescue or salvage party being overwhelmed by the creatures, or worse, carrying them back to the rest of your fleet.";
        } else if (won) {
            return "For some time now, the " + shipName + "'s crew had been spreading rumors about movements in empty halls and access tunnels. While initially dismissed, all skepticism ceases when your marine " +
                    "lieutenant in command of the onboard contingent makes a sudden connection to the bridge: \"LUDD PRESERVE US, CAPTAIN - I DON'T KNOW WHAT IT IS, BUT WE'RE UNDER ATTACK!\" As your tactical officer " +
                    "scrambles to pull up a force disposition, the comms channel dissolves into a staticy mess of screamed epithets and weapons fire. By the time a secure connection is established, your squads are " +
                    "engaged across the entire crew section of the vessel - under attack by ghostly swarms of vaguely humanoid blue-black creatures brandishing meter-long bone spurs and needle-sharp teeth more than " +
                    "capable of punching through CP-hardened deck suits designed for small arms combat. While your forces are able to rally and drive off the foe, it's clear that something else has made this ship its home.";
        } else {
            return "For some time now, the " + shipName + "'s crew had been spreading rumors about movements in empty halls and access tunnels. While initially dismissed, all skepticism ceases when your lieutenant " +
                    "in command of the onboard security makes a sudden connection to the bridge: \"LUDD PRESERVE US, CAPTAIN - I DON'T KNOW WHAT IT IS, BUT WE'RE UNDER ATTACK!\" As your tactical officer scrambles " +
                    "to pull up a force disposition, the comms channel dissolves into a staticy mess of screamed epithets and CP-fire. While most of the cameras show the unidentified threat as nothing more than a " +
                    "grey-black blur, a particularly unlucky crewman is pushed to the ground and captures a single frame of a hideous blue-black skinned creature, large fangs extending to reveal a separate mouth. " +
                    "The sickening wet crunch of his skull being punctured is cut off by the termination of the feed, leaving all on the bridge stunned.";
        }
    }

    @Override
    protected String getDescriptionImage() {
        if (won) return  "graphics/illustrations/vayra_ghost_ship_combat.jpg";
        else return  "graphics/illustrations/vayra_ghost_ship_death.jpg";
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        float pad = 3f;
        float opad = 10f;
        float initPad = pad;

        if (mode == ListInfoMode.IN_DESC) {
            initPad = opad;
        }

        Color tc = getBulletColorForMode(mode);

        bullet(info);

        String shipName = member.getShipName() + ", " + member.getHullSpec().getNameWithDesignationWithDashClass();

        info.addPara(shipName + " lost", initPad, tc, h, shipName);
        info.addPara(crewLosses + " crew lost", initPad, tc, h, "" + crewLosses);

        unindent(info);
    }

    private void rollBattleResults() {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        float marines = playerFleet.getCargo().getMarines();
        float crew = playerFleet.getCargo().getCrew();
        float requiredMarines = Math.max(1, (int) member.getMinCrew());
        float missingMarines = Math.max(0, requiredMarines - marines);
        float marineRatio = Math.min(1f, marines / requiredMarines);

        if (marineRatio < Math.random()) {
            won = false;
            crewLosses = (int) Math.min(crew, member.getMinCrew() * member.getCrewFraction());
            marineLosses = (int) Math.min(marines, requiredMarines);
        } else {
            float lossMult = missingMarines / requiredMarines;
            double baseLoss = Math.random() * requiredMarines * MARINE_LOSS_ON_COMBAT_SUCCESS * lossMult;
            crewLosses = (int) Math.min(baseLoss, member.getMinCrew() * member.getCrewFraction());
            marineLosses = (int) Math.min(baseLoss, requiredMarines);
        }
    }

    private void applyLosses() {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        cargo.removeCrew(crewLosses);
        cargo.removeMarines(marineLosses);
    }
}
