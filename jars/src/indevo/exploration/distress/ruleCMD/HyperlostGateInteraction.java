package indevo.exploration.distress.ruleCMD;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;
import java.util.Map;

import static indevo.exploration.distress.hullmods.GhostShip.HYPERLOST_GATE_LOSS_CHANCE;
import static indevo.ids.Ids.*;

public class HyperlostGateInteraction extends BaseCommandPlugin {

    public static Logger log = Global.getLogger(HyperlostGateInteraction.class);

    @Override
    @SuppressWarnings("unchecked")
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, final Map<String, MemoryAPI> memoryMap) {

        if (dialog == null) {
            return false;
        }

        TextPanelAPI text = dialog.getTextPanel();
        Color t = Misc.getTextColor();
        Color h = Misc.getHighlightColor();
        Color b = Misc.getNegativeHighlightColor();

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

        WeightedRandomPicker<FleetMemberAPI> members = new WeightedRandomPicker<>();
        try {
            FleetDataAPI fleetData = fleet.getFleetData();
            for (FleetMemberAPI mem : fleetData.getMembersInPriorityOrder()) {
            if (mem.getVariant().getHullMods().contains(HYPERLOST_HULLMOD) 
                    || mem.getVariant().getHullMods().contains(MYSTERY_HYPERLOST_HULLMOD)) {
                    members.add(mem);
                    log.info("found hyperlost ghost ship " + mem.getHullId() + ", adding to ghost ship for gate to (maybe) eat picker");
                }
            }
        } catch (NullPointerException nllptrxc) {
            log.error("something to do with player's fleet was null");
        }

        FleetMemberAPI member = members.pick();
        String shipName;

        float chance = HYPERLOST_GATE_LOSS_CHANCE;
        float roll = (float) Math.random();
        boolean badThingHappened = roll < chance;
        log.info("rolled " + roll + " against " + chance);

        if (member == null) {
            log.error("fleet member was null");
            text.addPara("You order your fleet to traverse the dead gateway. Your bridge crew is especially quiet during the passage.");
            text.addPara("Nothing happens.");
        } else {
            boolean known = member.getVariant().hasHullMod(HYPERLOST_HULLMOD);

            shipName = member.getShipName();
            log.info("picked hyperlost ghost ship " + shipName + " for gate to (maybe) eat");

            if (!badThingHappened) {
                log.info("bad thing didn't happened");

                text.addPara("You order your fleet through the dead gateway. The atmosphere is tense and quiet for a moment; then you're safely through, with nothing having happened. One of your officers cracks a lame joke, and you all chuckle for a moment. Your levity is cut short when your tactical officer abruptly stands up from his console. \"Captain, you'd better take a look at this,\" he says, \"The " + shipName + " is out of formation - and they're not responding to hails.\"");

                text.addPara("Unable to reach the rogue ship on comms, you watch as it glides silently through the enormous gate. As it reaches the middle, the ship accelerates suddenly, plunging through the ring at emergency burn and rejoining your formation. Your comms panel lights up with hails, the " + shipName + "'s captain connecting to your bridge on your private command frequency to report: Strange happenings began aboard ship as they neared the gate, with crews calling in anomalous sightings and sounds across all decks, and their comms were completely offline. However, once they burned through the gate, all the anomalies seemed to dissipate - and as far as your lieutenant in command of the ship can tell, the " + shipName + " is no longer anything but a perfectly ordinary spacecraft.");
                
                log.info("clearing ghost ship hullmods from " + shipName);
                member.getVariant().removePermaMod(MYSTERY_HYPERLOST_HULLMOD);
                member.getVariant().removePermaMod(HYPERLOST_HULLMOD);
                
            } else {
                log.info("bad thing happened");

                text.addPara("You order your fleet through the dead gateway. The atmosphere is tense and quiet for a moment; then you're safely through, with nothing having happened. One of your officers cracks a lame joke, and you all chuckle for a moment. Your levity is cut short when your tactical officer abruptly stands up from his console. \"Captain, you'd better take a look at this,\" he says, \"The " + shipName + " is out of formation - and they're not responding to hails.\"");

                text.addPara("Unable to reach the rogue ship on comms, you watch as it glides silently through the enormous ring of the gate. As it reaches the middle, the ship kills its engines and comes to a complete halt. You try one more time to hail the crew, but get nothing but static - static that sounds like screaming, if you listen closely.");

                text.addPara("The gate begins to spin, end on end, accelerating with every revolution. The " + shipName + " is suddenly illuminated by a stark light as if caught in the glare of an unseen star - but the shadows are all wrong, seeping across the hull like blood from a grisly wound. Every time you see the ship through the blur, its silhouette seems to be changing, twisting and buckling, pieces of plating and internal structure bulging outward grotesquely like muscle. And then, with a brilliant flash of eye-searing white, the ring stops spinning and slams back into its previous position. When your vision finally clears, the " + shipName + " is gone - gone, save for a cloud of mutilated corpses; all that remained of its crew, floating in the center of the dead, silent gate.");
                
                int crew = (int) (member.getMinCrew() * member.getCrewFraction());
                
                text.addPara("Lost " + shipName + ".", b, h, "" + shipName);
                text.addPara("Lost " + crew + " crew.", b, h, "" + crew);

                log.info("byebye " + shipName);
                fleet.getCargo().removeCrew(crew);
                fleet.removeFleetMemberWithDestructionFlash(member);
                GhostShipIntel intel = new GhostShipIntel(GATE_LOST, member, known, null);
                Global.getSector().getIntelManager().addIntel(intel);
                Global.getSector().getMemoryWithoutUpdate().unset(HAS_HYPERLOST_KEY);
            }
        }

        return true;
    }
}
