package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.util.Misc;
import indevo.economy.plugins.RelicSpeedUpDialog;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.academy.industry.Academy;
import indevo.industries.academy.plugins.AcademyDialogPlugin;
import indevo.industries.academy.plugins.AcademyView;
import indevo.utils.helper.MiscIE;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// test only: every Academy panel view from code, audited and captured
public final class AcademyUiChecks {
    private static final String MOD = "iecheck_ui";
    private static final String[] VARIANTS = {"eagle_Assault", "dominator_Assault", "hammerhead_Balanced", "falcon_Attack", "sunder_CS", "enforcer_Assault",
            "medusa_Attack", "aurora_Balanced", "heron_Attack", "apogee_Balanced", "champion_Assault", "fury_Attack"};
    private static final String[] FIRST = {"Reka", "Ilse", "Mara", "Joss", "Dagny", "Pell", "Sun", "Vera", "Bram", "Ottilie", "Kasimir", "Wen", "Ada", "Ruth", "Tomas", "Lio"};
    private static final String[] LAST = {"Yamada", "Varga", "Linde", "Arden", "Holm", "Orsini", "Achterberg", "Kask", "Tolle", "Brand", "Novak", "Ito", "Quell", "Marr", "Sauer", "Pike"};
    private static final String[] LADDER = {Personalities.TIMID, Personalities.CAUTIOUS, Personalities.STEADY, Personalities.AGGRESSIVE, Personalities.RECKLESS};

    private AcademyUiChecks() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(AcademyUiChecks::setup);

        UiViews.view(steps, "academy-officers", c -> open(c, new AcademyView(false, id(c, "selected"), Personalities.STEADY)));
        UiViews.view(steps, "academy-officers-scrolled", c -> {
            AcademyView view = new AcademyView(false, id(c, "laststored"), null);
            view.scroll.set(AcademyView.LIST, 100000f);
            open(c, view);
        });
        UiViews.view(steps, "academy-officer-stored-holobench", c -> {
            academy(c).setSpecialItem(new SpecialItemData(ItemIds.HOLOBENCH, null));
            academy(c).setAICoreId(Commodities.ALPHA_CORE);
            c.market("a").reapplyIndustries();
            open(c, new AcademyView(false, id(c, "stored"), null));
        });
        UiViews.view(steps, "academy-stored-holobench-list", c -> {
            AcademyView view = new AcademyView(false, id(c, "laststored"), null);
            view.scroll.set(AcademyView.LIST, 100000f);
            open(c, view);
        });
        UiViews.view(steps, "academy-officer-skills", c -> open(c, new AcademyView(false, id(c, "skilled"), null)));
        UiViews.view(steps, "academy-trainee", c -> {
            Academy academy = academy(c);
            academy.setSpecialItem(null);
            academy.setAICoreId(null);
            c.market("a").reapplyIndustries();
            PersonAPI trainee = person(c, "trainee");
            academy.setOfficerForTraining(trainee, academy.getReachablePersonalities(trainee).get(0));
            c.expect(academy.getOfficerInTraining() == trainee, "trainee in training");
            MiscIE.getStorageCargo(c.market("a")).addCommodity(ItemIds.RARE_PARTS, 400f);
            open(c, new AcademyView(false, null, null));
        });
        UiViews.view(steps, "academy-officer-blocked", c -> open(c, new AcademyView(false, id(c, "selected"), null)));
        UiViews.view(steps, "academy-accelerate-short", c -> {
            relics(c, 0f);
            open(c, new AcademyView(false, null, null));
        });
        UiViews.view(steps, "academy-accelerate-used", c -> {
            relics(c, 400f);
            c.expect(RelicSpeedUpDialog.apply(academy(c).getTrainingSpeedUp(false)), "accelerate applied");
            open(c, new AcademyView(false, null, null));
        });
        UiViews.view(steps, "academy-trainee-paused", c -> {
            academy(c).setDisrupted(30f);
            open(c, new AcademyView(false, null, null));
        });
        UiViews.view(steps, "academy-retrieve-blocked", c -> {
            academy(c).setDisrupted(0f);
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            fleet.getCommander().getStats().getOfficerNumber().unmodify(MOD);
            int free = Misc.getMaxOfficers(fleet) - Misc.getNumNonMercOfficers(fleet);
            fleet.getCommander().getStats().getOfficerNumber().modifyFlat(MOD, -free);
            open(c, new AcademyView(false, id(c, "stored"), null));
        });
        UiViews.view(steps, "academy-officer-nocredits", c -> {
            Global.getSector().getPlayerFleet().getCommander().getStats().getOfficerNumber().unmodify(MOD);
            Global.getSector().getPlayerFleet().getCommander().getStats().getOfficerNumber().modifyFlat(MOD, 12f);
            MarketAPI galatia = Global.getSector().getEconomy().getMarket(Ids.ACADEMY_MARKET_ID);
            c.expect(galatia != null && Academy.get(galatia) != null, "galatia academy exists");
            credits(c, 0f);
            c.expect(AcademyDialogPlugin.show(galatia, new AcademyView(false, id(c, "selected"), Personalities.STEADY)), "galatia panel opens");
        });
        UiViews.view(steps, "academy-admins", c -> {
            credits(c, 5000000f);
            open(c, new AcademyView(true, id(c, "admin1"), null));
        });
        UiViews.view(steps, "academy-admin-governing", c -> open(c, new AcademyView(true, id(c, "governor"), null)));
        UiViews.view(steps, "academy-admin-trainee", c -> {
            PersonAPI admin = person(c, "stored1");
            academy(c).setAdminInTraining(admin);
            c.expect(academy(c).getAdminInTraining() == admin, "admin in training (" + academy(c).getAdminTrainBlocker(admin) + ", skills " + Academy.getAdminSkillCount(admin) + ")");
            open(c, new AcademyView(true, null, null));
        });
        UiViews.view(steps, "academy-worst", c -> {
            worst(c);
            open(c, new AcademyView(false, id(c, "worst"), Personalities.RECKLESS));
        });
        UiViews.view(steps, "academy-empty", c -> {
            empty(c);
            open(c, new AcademyView(false, null, null));
        });
        UiViews.view(steps, "academy-admins-empty", c -> open(c, new AcademyView(true, null, null)));
        steps.add(c -> c.keepDialog(false));
        return steps;
    }

    static void setup(Ctx c) {
        Colony.pair(c, 6);
        MarketAPI a = c.market("a");
        a.addIndustry(Ids.ACADEMY);
        a.reapplyIndustries();
        Colony.jump(a.getContainingLocation(), a.getPrimaryEntity().getLocation().x + 300f, a.getPrimaryEntity().getLocation().y);

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        credits(c, 5000000f);
        for (Object data : fleet.getFleetData().getOfficersCopy().toArray()) {
            PersonAPI p = ((com.fs.starfarer.api.characters.OfficerDataAPI) data).getPerson();
            if (!Misc.isUnremovable(p)) {
                for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) if (m.getCaptain() == p) m.setCaptain(null);
                fleet.getFleetData().removeOfficer(p);
            }
        }
        fleet.getCommander().getStats().getOfficerNumber().modifyFlat(MOD, 12f);

        Random random = new Random(7);
        List<String> combat = skills(false);
        Academy academy = academy(c);
        for (int i = 0; i < 16; i++) {
            int level = 1 + random.nextInt(7);
            PersonAPI p = OfficerManagerEvent.createOfficer(Global.getSector().getPlayerFaction(), level);
            p.getName().setFirst(FIRST[i]);
            p.getName().setLast(LAST[i]);
            p.setPersonality(LADDER[i % LADDER.length]);
            for (String id : combat) p.getStats().setSkillLevel(id, 0f);
            int count = i == 2 ? 14 : i == 3 ? 6 : 1 + random.nextInt(5);
            for (int s = 0; s < count && s < combat.size(); s++) p.getStats().setSkillLevel(combat.get((s * 5 + i) % combat.size()), s % 3 == 0 ? 2f : 1f);
            p.getStats().setLevel(Math.max(level, count));

            fleet.getFleetData().addOfficer(p);
            if (i < 10) {
                FleetMemberAPI m = Global.getFactory().createFleetMember(FleetMemberType.SHIP, VARIANTS[i % VARIANTS.length]);
                fleet.getFleetData().addFleetMember(m);
                if (i != 4) m.setCaptain(p);
                if (i == 0) c.put("ship0", m.getId());
            } else academy.storeOfficer(p);

            if (i == 1) c.put("selected", p.getId());
            if (i == 2) c.put("skilled", p.getId());
            if (i == 5) c.put("trainee", p.getId());
            if (i == 11) c.put("stored", p.getId());
        }
        lastStored(c, academy);

        List<String> admin = skills(true);
        PersonAPI governor = admin(admin, 2, "Halvard", "Okonkwo-Reyes");
        a.setAdmin(governor);
        c.put("governor", governor.getId());
        PersonAPI admin1 = admin(admin, 1, "Ines", "Moreau");
        c.put("admin1", admin1.getId());
        PersonAPI stored1 = admin(admin, 0, "Tobiah", "Lund");
        c.put("stored1", stored1.getId());
        PersonAPI stored2 = admin(admin, 2, "Greta", "Vasquez");
        academy.storeAdmin(stored1);
        academy.storeAdmin(stored2);
        Global.getSector().getPlayerFleet().getCommander().getStats().getAdminNumber().modifyFlat(MOD, 4f);
        c.note("academy at " + a.getName() + ", officers " + academy.getFleetOfficers().size() + ", stored " + academy.getOfficerStorage().size());
    }

    private static void lastStored(Ctx c, Academy academy) {
        List<PersonAPI> stored = new ArrayList<>(academy.getOfficerStorage());
        stored.sort((x, y) -> Integer.compare(y.getStats().getLevel(), x.getStats().getLevel()));
        c.put("laststored", stored.get(stored.size() - 1).getId());
    }

    private static PersonAPI admin(List<String> skills, int count, String first, String last) {
        PersonAPI p = OfficerManagerEvent.createAdmin(Global.getSector().getPlayerFaction(), 0, new Random(count));
        for (String id : Global.getSettings().getSortedSkillIds()) p.getStats().setSkillLevel(id, 0f);
        for (int i = 0; i < count; i++) p.getStats().setSkillLevel(skills.get(i), 1f);
        p.getName().setFirst(first);
        p.getName().setLast(last);
        Global.getSector().getCharacterData().addAdmin(p);
        return p;
    }

    private static List<String> skills(boolean admin) {
        List<String> out = new ArrayList<>();
        for (String id : Global.getSettings().getSortedSkillIds()) {
            SkillSpecAPI spec = Global.getSettings().getSkillSpec(id);
            if (spec.hasTag("deprecated") || !admin && (spec.hasTag("npc_only") || spec.hasTag("ai_core_only"))) continue;
            if (admin ? spec.isAdminSkill() : spec.isCombatOfficerSkill()) out.add(id);
        }
        return out;
    }

    static void worst(Ctx c) {
        MarketAPI a = c.market("a");
        a.setName("New Maxios Prime Station");
        credits(c, 987654321f);
        PersonAPI p = person(c, "selected");
        Academy academy = academy(c);
        if (academy.getOfficerInTraining() != null) academy.abortOfficerTraining(true);
        if (academy.getAdminInTraining() != null) academy.abortAdminTraining(true);
        p.getName().setFirst("Maximilian");
        p.getName().setLast("Wolkensteiner");
        PersonAPI stored = person(c, "stored");
        stored.getName().setFirst("Bartholomew Theodoric Alexandros");
        stored.getName().setLast("Vanderbijl-Oppenheimer");
        for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if (m.getCaptain() == p) m.setShipName("ISS One Foot In The Event Horizon");
        }
        PersonAPI governor = person(c, "governor");
        governor.getName().setFirst("Konstantin Leopold Maximilian");
        governor.getName().setLast("von Hohenberg-Lichtenstein");
        UiAudit.acceptLong("Bartholomew Theodoric Alexandros", "Konstantin Leopold Maximilian");
        c.put("worst", p.getId());
    }

    private static void empty(Ctx c) {
        Academy academy = academy(c);
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        academy.getOfficerStorage().clear();
        academy.getAdminStorage().clear();
        List<PersonAPI> officers = academy.getFleetOfficers();
        for (int i = 1; i < officers.size(); i++) academy.removeOfficerFromPlayerFleet(officers.get(i));
        for (PersonAPI admin : academy.getStaffAdmins()) {
            MarketAPI governed = Academy.getGovernedMarket(admin);
            if (governed != null) governed.setAdmin(Global.getSector().getPlayerPerson());
            Global.getSector().getCharacterData().removeAdmin(admin);
        }
        c.market("a").setName("Ward");
    }

    static void open(Ctx c, AcademyView view) {
        c.expect(AcademyDialogPlugin.show(c.market("a"), view), "academy panel opens");
    }

    static void relics(Ctx c, float amount) {
        MarketAPI a = c.market("a");
        MiscIE.getStorageCargo(a).removeCommodity(ItemIds.RARE_PARTS, MiscIE.getStorageCargo(a).getCommodityQuantity(ItemIds.RARE_PARTS));
        MiscIE.getPlayerCargo().removeCommodity(ItemIds.RARE_PARTS, MiscIE.getPlayerCargo().getCommodityQuantity(ItemIds.RARE_PARTS));
        if (amount > 0f) MiscIE.getStorageCargo(a).addCommodity(ItemIds.RARE_PARTS, amount);
    }

    private static void credits(Ctx c, float amount) {
        var credits = Global.getSector().getPlayerFleet().getCargo().getCredits();
        credits.set(amount);
    }

    static Academy academy(Ctx c) {
        return Academy.get(c.market("a"));
    }

    static String id(Ctx c, String key) {
        return (String) c.get(key);
    }

    static PersonAPI person(Ctx c, String key) {
        String id = id(c, key);
        Academy academy = academy(c);
        List<PersonAPI> all = new ArrayList<>(academy.getFleetOfficers());
        all.addAll(academy.getOfficerStorage());
        all.addAll(academy.getStaffAdmins());
        all.addAll(academy.getAdminStorage());
        if (academy.getOfficerInTraining() != null) all.add(academy.getOfficerInTraining());
        if (academy.getAdminInTraining() != null) all.add(academy.getAdminInTraining());
        for (PersonAPI p : all) if (p.getId().equals(id)) return p;
        throw new IllegalStateException("no person " + key);
    }
}
