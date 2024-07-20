package indevo.other;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

import java.util.ArrayList;
import java.util.List;

@Deprecated
public class POC_SkillCleaner {
    public static void run(){
        for (OfficerDataAPI data : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()){
            List<String> toRemove = new ArrayList<>();
            for (MutableCharacterStatsAPI.SkillLevelAPI level : data.getPerson().getStats().getSkillsCopy()){
                if (level.getSkill().getId().startsWith("pc_")) toRemove.add(level.getSkill().getId());
            }

            for (String s : toRemove) data.getPerson().getStats().setSkillLevel(s, 0);
        }

        for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getMembersWithFightersCopy()) {
            m.getVariant().removePermaMod("ScarPersonalityChanger");
            m.getVariant().removeMod("ScarPersonalityChanger");
        }

        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) m.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).unmodify("pc_increase_officer_prob_mult");
    }

    //runcode indevo.other.POC_SkillCleaner.run()
}
