package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import indevo.abilities.skills.scripts.AdminGovernTimeTracker;
import indevo.ids.Ids;
import indevo.industries.senate.conditions.Edict_FastBuild;
import indevo.utils.helper.ReflectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

// test only: builds the state, then waits while gamectl clicks through the real colony screens
public final class UiChecks {
    private UiChecks() {
    }

    public static List<Step> steps(int daysInOffice) {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            MarketAPI a = c.market("a");
            MarketAPI b = c.market("b");
            PersonAPI admin = OfficerManagerEvent.createAdmin(Global.getSector().getPlayerFaction(), 1, new Random());
            admin.getStats().setSkillLevel(Ids.MICROMANAGEMENT, 1);
            Global.getSector().getCharacterData().addAdmin(admin);
            a.setAdmin(admin);
            a.addIndustry(Industries.MINING);
            a.addIndustry(Industries.REFINING);
            b.addIndustry(Ids.SENATE);
            b.addIndustry(Industries.LIGHTINDUSTRY);
            b.addCondition(Edict_FastBuild.ID);
            b.reapplyConditions();
            b.getConstructionQueue().addToEnd(Industries.HEAVYINDUSTRY, 0);
            b.getIndustry(Industries.LIGHTINDUSTRY).startBuilding();
            b.reapplyIndustries();
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(5000000f);
            Colony.jump(Global.getSector().getHyperspace(), 0f, 0f);
            c.days(1.5f);
        });
        steps.add(c -> {
            AdminGovernTimeTracker tracker = AdminGovernTimeTracker.getInstanceOrRegister();
            Map<?, ?> list = (Map<?, ?>) ReflectionUtils.get("adminList", tracker);
            Object entry = list.get(c.market("a").getId());
            c.expect(entry != null, "admin tracker has colony A");
            ReflectionUtils.set("daysInOffice", entry, daysInOffice);
            c.note("A days in office " + tracker.getValueForMarket(c.market("a").getId()));
            Global.getSector().setPaused(true);
            c.note("READY");
            c.frames(100000000);
        });
        return steps;
    }
}
