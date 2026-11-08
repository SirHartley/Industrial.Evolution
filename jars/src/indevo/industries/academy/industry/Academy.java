package indevo.industries.academy.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.plugins.OfficerLevelupPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.DynamicStatsAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.config.Setting;
import indevo.economy.RelicSpeedUpTarget;
import indevo.ids.Ids;
import indevo.utils.helper.BillingHelper;
import indevo.utils.helper.MiscIE;
import indevo.utils.timers.NewDayListener;

import java.awt.*;
import java.util.List;
import java.util.*;

public class Academy extends BaseIndustry implements NewDayListener, EconomyTickListener {

    public final String OFFICER_IS_TRAINING_KEY = "$IndEvo_officerIsTraining";
    public final String ADMIN_IS_TRAINING_KEY = "$IndEvo_adminIsTraining";

    //officer handling vars
    protected PersonAPI officerInTraining = null;
    protected trainingDirection currentDirection = trainingDirection.UNSET;
    protected String officerTrainingTarget = null;
    protected PersonAPI adminInTraining = null;
    private final String trainLevelKey = "$IndEvo_wasTrainedLevel";
    private final ArrayList<PersonAPI> officerStorage = new ArrayList<>();
    private int officerDaysPassed = 0;

    //admin handling vars
    private final ArrayList<PersonAPI> adminStorage = new ArrayList<>();
    private int adminDaysPassed = 0;

    //general
    private Random random = new Random();

    //AI cores
    private int currentAICoreDayReduction = 0;
    private final int betacoreReduction = 31;
    private final float gammaCoreUpkeepRed = 0.90f;

    public static Map<String, Color> COLOURS_BY_PERSONALITY = new LinkedHashMap<String, Color>() {{
        put(Personalities.TIMID, Color.BLUE);
        put(Personalities.CAUTIOUS, Color.CYAN);
        put(Personalities.STEADY, Color.GREEN);
        put(Personalities.AGGRESSIVE, Color.ORANGE);
        put(Personalities.RECKLESS, Color.RED);
    }};

    public enum trainingDirection {
        UNSET,
        BETTER,
        WEAKER
    }

    public static class TrainingSpeedUp implements RelicSpeedUpTarget {

        private final Academy academy;
        private final boolean admin;

        public TrainingSpeedUp(Academy academy, boolean admin) {
            this.academy = academy;
            this.admin = admin;
        }

        @Override
        public MarketAPI getMarket() {
            return academy.getMarket();
        }

        @Override
        public String getSpeedUpId() {
            return academy.getId() + (admin ? "_admin" : "_officer");
        }

        @Override
        public String getSpeedUpLabel() {
            return "Accelerate";
        }

        @Override
        public String getSpeedUpLore() {
            return "The Academy's neural trainers have spent centuries running on substitute parts. Relic hardware restores the more demanding simulation routines.";
        }

        @Override
        public int getSpeedUpDaysLeft() {
            if (admin) return academy.adminInTraining != null ? Math.max(0, academy.getRemainingAdminTrainingDays()) : 0;
            return academy.officerInTraining != null ? Math.max(0, academy.getRemainingOfficerTrainingDays()) : 0;
        }

        @Override
        public int getSpeedUpDaysTotal() {
            return admin ? academy.getAdminTrainingDays() : academy.getCurrentOfficerTrainingDays();
        }

        @Override
        public void speedUp(int days) {
            if (admin) academy.adminDaysPassed += days;
            else academy.officerDaysPassed += days;
        }
    }

    //debug-Logger
    private static void debugMessage(String Text) {
        boolean DEBUG = false; //set to false once done
        if (DEBUG) {
            Global.getLogger(Academy.class).info(Text);
        }
    }

    @Override
    public void apply() {
        super.apply(true);
        Global.getSector().getListenerManager().addListener(this, true);
        if (!market.getId().equals(Ids.ACADEMY_MARKET_ID)) increaseOfficerQuality();
    }

    @Override
    public void unapply() {
        super.unapply();
        Global.getSector().getListenerManager().removeListener(this);
        unmodifyOfficerQualityIncrease();
    }

    public static Academy get(MarketAPI market) {
        return market != null && market.hasIndustry(Ids.ACADEMY) ? (Academy) market.getIndustry(Ids.ACADEMY) : null;
    }

    public boolean isTrainingOfficer() {
        return officerInTraining != null;
    }

    public boolean isTrainingAdmin() {
        return adminInTraining != null;
    }

    @Override
    public void onNewDay() {

        if (isFunctional()) {
            if (!market.getId().equals(Ids.ACADEMY_MARKET_ID)) {
                //spawn officers/admins if the market has none:
                if (!marketHasHireableOfficer(false) && getRandomBoolean(Setting.DAILY_OFFICER_SPAWN_CHANCE.value())) {
                    createHireablePerson(false);
                }

                if (!marketHasHireableOfficer(true) && getRandomBoolean(Setting.DAILY_ADMIN_SPAWN_CHANCE.value())) {
                    createHireablePerson(true);
                }
            }

            if (!market.getFaction().isHostileTo(Global.getSector().getPlayerFaction())) {
                trainOfficerInTraining();
                trainAdminInTraining();
            }
        }
    }

    public void reportEconomyTick(int iterIndex) {
        int lastIterInMonth = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1;

        if (iterIndex != lastIterInMonth) return;

        BillingHelper.charge(this, "storage", "Personnel storage", calculateFeeStorageCost() + calculateSalaryStorageCost());
    }

    @Override
    public void reportEconomyMonthEnd() {

    }

    @Override
    public boolean isAvailableToBuild() {
        return Setting.ACADEMY.enabled();
    }

    @Override
    public boolean showWhenUnavailable() {
        return Setting.ACADEMY.enabled();
    }

    //officers in training tooltip
    protected boolean addNonAICoreInstalledItems(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        if (mode != IndustryTooltipMode.NORMAL) {
            return false;
        }
        float opad = 10f;
        float spad = 2f;

        FactionAPI faction = market.getFaction();
        Color color = faction.getBaseUIColor();
        Color hl = Misc.getHighlightColor();

        tooltip.addSectionHeading("Storage Data", color, faction.getDarkUIColor(), Alignment.MID, opad);

        tooltip.addPara("Officers in storage: %s", opad, hl, new String[]{getOfficerStorage().size() + ""});
        tooltip.addPara("Administrators in storage: %s", spad, hl, new String[]{getAdminStorage().size() + ""});

        if (!market.isPlayerOwned()) {
            tooltip.addPara("Monthly salary cost: %s, storage fees: %s", opad, hl, new String[]{Misc.getDGSCredits(calculateSalaryStorageCost()), Misc.getDGSCredits(calculateFeeStorageCost())});
        } else {
            tooltip.addPara("Monthly salary cost: %s, no storage fee.", opad, hl, new String[]{Misc.getDGSCredits(calculateSalaryStorageCost())});
        }

        if (officerInTraining != null) {
            PersonAPI officer = officerInTraining;
            tooltip.addSectionHeading("Officer in training", color, faction.getDarkUIColor(), Alignment.MID, opad);

            String oldPersonality = officer.getPersonalityAPI().getDisplayName();
            String newPersonality = Global.getSettings().getPersonaltySpec(getOfficerTrainingTarget()).getDisplayName();
            String desc = officer.getNameString() + ", training to change personality from %s to %s";
            String dayCount = getRemainingOfficerTrainingDays() + " days.";
            TooltipMakerAPI text = tooltip.beginImageWithText(officer.getPortraitSprite(), 48);
            text.addPara(desc, spad, Misc.getTextColor(), hl, new String[]{oldPersonality, newPersonality});
            text.addPara("Training will finish in %s", spad, hl, dayCount);
            tooltip.addImageWithText(opad);
        }

        if (adminInTraining != null) {
            PersonAPI admin = adminInTraining;
            tooltip.addSectionHeading("Administrator in training", color, faction.getDarkUIColor(), Alignment.MID, opad);

            String desc = admin.getNameString() + ", training to %s.";
            String dayCount = getRemainingAdminTrainingDays() + " days.";

            TooltipMakerAPI text = tooltip.beginImageWithText(admin.getPortraitSprite(), 48);
            text.addPara(desc, spad, Misc.getTextColor(), hl, new String[]{"increase skill level"});
            text.addPara("Training will finish in %s", spad, hl, dayCount);
            tooltip.addImageWithText(opad);
        }
        return true;
    }

    //payment calc

    private float calculateSalaryStorageCost() {
        float totalCost = 0;

        for (PersonAPI person : getOfficerStorage()) {
            totalCost += Misc.getOfficerSalary(person);
        }

        for (PersonAPI person : getAdminStorage()) {
            totalCost += Misc.getAdminSalary(person) * 0.1f;
        }

        return totalCost;
    }

    private float calculateFeeStorageCost() {
        if (!market.isPlayerOwned()) {
            int personAmount = +getAdminStorage().size() + getOfficerStorage().size();
            return Setting.MONTHLY_AI_STORAGE_COST.value() * personAmount;
        }

        return 0;
    }

    public float getOfficerTrainingFee(int steps) {
        return market.isPlayerOwned() ? 0f : steps * Setting.PERSONALITY_TRAINING_COST.intValue();
    }

    public float getAdminTrainingFee() {
        return market.isPlayerOwned() ? 0f : Setting.ADMIN_TRAINING_COST.intValue();
    }

    private void chargeTraining(float amount) {
        if (amount > 0) BillingHelper.chargeNow(amount);
    }

    //officer quality increases

    public static final float OFFICER_PROBABILITY_MULT = 1.5f;
    public static final float OFFICER_MAX_LEVEL_MULT = 1.5f;
    public static final float OFFICER_MAX_LEVEL_FOREIGN_MULT = 1.3f;

    private void increaseOfficerQuality() {
        for (MarketAPI market : MiscIE.getMarketsInLocation(this.market.getContainingLocation(), this.market.getFactionId())) {
            DynamicStatsAPI stats = market.getStats().getDynamic();
            OfficerLevelupPlugin plugin = (OfficerLevelupPlugin) Global.getSettings().getPlugin("officerLevelUp");

            if (market.getId() != this.market.getId()) {
                //don't apply if it already has a mult
                if (stats.getMod(Stats.OFFICER_MAX_LEVEL_MOD).getMultBonus(getModId()) != null) {
                    return;
                }

                stats.getMod(Stats.OFFICER_MAX_LEVEL_MOD).modifyMult(getModId(), OFFICER_MAX_LEVEL_FOREIGN_MULT, getNameForModifier());
            } else {
                stats.getMod(Stats.OFFICER_MAX_LEVEL_MOD).modifyMult(getModId(), OFFICER_MAX_LEVEL_MULT, getNameForModifier());
                stats.getMod(Stats.OFFICER_PROB_MOD).modifyFlat(getModId(), OFFICER_PROBABILITY_MULT);
            }
        }
    }

    private void unmodifyOfficerQualityIncrease() {
        for (MarketAPI market : MiscIE.getMarketsInLocation(this.market.getContainingLocation(), this.market.getFactionId())) {
            market.getStats().getDynamic().getMod(Stats.OFFICER_MAX_LEVEL_MOD).unmodify(getModId());
            market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).unmodify(getModId());
        }
    }


    //Admin Training

    private void trainAdminInTraining() {
        if (adminInTraining != null) {
            if (adminDaysPassed >= getAdminTrainingDays() || Global.getSettings().isDevMode()) {
                addRandomAdminSkill(adminInTraining);
                moveAdminInTrainingToStorage();
                adminDaysPassed = 0;
                market.getMemory().set(ADMIN_IS_TRAINING_KEY, false);

                MessageIntel intel = new MessageIntel("An administrator has finished training at %s.",
                        Misc.getTextColor(), new String[]{(market.getName())}, market.getFaction().getBrightUIColor());
                intel.addLine("They aquired a %s.", Misc.getTextColor(), new String[]{"new skill"}, Misc.getHighlightColor());
                intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
                intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "trainingdone"));
                Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, market);

            } else {
                adminDaysPassed++;
            }
        }
    }

    public RelicSpeedUpTarget getTrainingSpeedUp(boolean admin) {
        return new TrainingSpeedUp(this, admin);
    }

    public int getRemainingAdminTrainingDays() {
        return getAdminTrainingDays() - adminDaysPassed;
    }

    public int getAdminTrainingDays() {
        return Setting.ADMIN_TRAINING_DAY_COUNT.intValue() + 1 - currentAICoreDayReduction;
    }

    public PersonAPI getAdminInTraining() {
        return adminInTraining;
    }

    public void setAdminInTraining(PersonAPI person) {
        if (adminInTraining != null || !BillingHelper.canAfford(getAdminTrainingFee())) return;

        if (canTrainAdminPerson(person)
                && isAdminInPlayerFleet(person)) {

            Global.getSector().getCharacterData().removeAdmin(person);
            adminInTraining = person;
            adminDaysPassed = 0;
            market.getMemory().set(ADMIN_IS_TRAINING_KEY, true);
        } else if (canTrainAdminPerson(person) && adminStorage.contains(person)) {
            adminStorage.remove(person);
            adminInTraining = person;
            adminDaysPassed = 0;
            market.getMemory().set(ADMIN_IS_TRAINING_KEY, true);
        } else return;

        chargeTraining(getAdminTrainingFee());
    }

    public void abortAdminTraining(boolean moveToStorage_InsteadOfFleet) {
        if (adminInTraining != null) {
            if (moveToStorage_InsteadOfFleet || !playerHasAdminCapacity()) {
                adminStorage.add(adminInTraining);
            } else {
                Global.getSector().getCharacterData().addAdmin(adminInTraining);
            }

            adminInTraining = null;
            adminDaysPassed = 0;
            market.getMemory().set(ADMIN_IS_TRAINING_KEY, false);
        }
    }

    private boolean isAdminInPlayerFleet(PersonAPI person) {
        //player has admin
        for (AdminData admin : Global.getSector().getCharacterData().getAdmins()) {
            if (admin.getPerson() == person) {
                return true;
            }
        }
        return false;
    }

    public boolean canTrainAdminPerson(PersonAPI person) {
        return getAdminSkillCount(person) < 2 && getGovernedMarket(person) == null;
    }

    public static int getAdminSkillCount(PersonAPI person) {
        int skillCount = 0;

        for (String skillId : Global.getSettings().getSortedSkillIds()) {
            SkillSpecAPI skill = Global.getSettings().getSkillSpec(skillId);
            if (skill.isAdminSkill() && personHasSkill(person, skill)) skillCount++;
        }

        return skillCount;
    }

    public static MarketAPI getGovernedMarket(PersonAPI person) {
        for (MarketAPI market : Misc.getFactionMarkets(Global.getSector().getPlayerFaction())) {
            if (market.getAdmin() != null && market.getAdmin().getId().equals(person.getId())) return market;
        }

        return null;
    }

    public static FleetMemberAPI getCommandedShip(PersonAPI person) {
        for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if (member.getCaptain() == person) return member;
        }

        return null;
    }

    public List<PersonAPI> getFleetOfficers() {
        List<PersonAPI> officers = new ArrayList<>();
        for (OfficerDataAPI data : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
            if (!Misc.isUnremovable(data.getPerson())) officers.add(data.getPerson());
        }

        return officers;
    }

    public List<PersonAPI> getStaffAdmins() {
        List<PersonAPI> admins = new ArrayList<>();
        for (AdminData data : Global.getSector().getCharacterData().getAdmins()) admins.add(data.getPerson());
        return admins;
    }

    public String getOfficerTrainBlocker(PersonAPI person) {
        if (officerInTraining != null) return "Another officer is in training.";
        if (getOfficerStepsLeft(person) == 0) {
            return !hasAlphaCore() && getTrainedSteps(person) < 2 ? "No training steps left. An alpha core allows a second step." : "No training steps left.";
        }
        if (!BillingHelper.canAfford(getOfficerTrainingFee(1))) return "Not enough credits.";
        return null;
    }

    public String getAdminTrainBlocker(PersonAPI person) {
        if (adminInTraining != null) return "Another administrator is in training.";
        if (getAdminSkillCount(person) >= 2) return "Already has two administrator skills.";

        MarketAPI governed = getGovernedMarket(person);
        if (governed != null) return "Governs " + governed.getName() + ". Store or replace them first.";
        if (!BillingHelper.canAfford(getAdminTrainingFee())) return "Not enough credits.";
        return null;
    }

    public static String getOfficerSlotBlocker() {
        return playerHasOfficerCapacity() ? null : "No free officer slots.";
    }

    public static String getAdminSlotBlocker() {
        return playerHasAdminCapacity() || Global.getSettings().isDevMode() ? null : "No free administrator slots.";
    }

    public void addRandomAdminSkill(PersonAPI person) {
        if (random == null) random = new Random();

        person.getStats().setSkipRefresh(true);
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);

        Set<String> whitelist = MiscIE.getCSVSetFromMemory(Ids.ACADEMY_SKILL_LIST);
        for (String skillId : Global.getSettings().getSortedSkillIds()) {
            if (!whitelist.contains(skillId)) continue;

            SkillSpecAPI skill = Global.getSettings().getSkillSpec(skillId);
            if (skill.isAdminSkill()
                    && !personHasSkill(person, skill)
                    && !skill.hasTag(Skills.TAG_DEPRECATED)
                    && !skill.hasTag(Skills.TAG_PLAYER_ONLY)
                    && !skill.hasTag(Skills.TAG_AI_CORE_ONLY)) {
                picker.add(skillId);
            }
        }

        if (picker.isEmpty()) {
            person.getStats().setSkipRefresh(false);
            return;
        }

        String pick = picker.pickAndRemove();
        person.getStats().setSkillLevel(pick, 1);

        float tier = person.getMemoryWithoutUpdate().getFloat("$ome_adminTier");
        person.getMemory().set("$ome_adminTier", tier + 1);

        person.getStats().setSkipRefresh(false);
        person.getStats().refreshCharacterStatsEffects();
    }

    private void moveAdminInTrainingToStorage() {
        adminStorage.add(adminInTraining);
        adminInTraining = null;
    }

    //admin Storage

    public void storeAdmin(PersonAPI person) {
        MarketAPI governed = getGovernedMarket(person);
        if (governed != null) governed.setAdmin(Global.getSector().getPlayerPerson());

        Global.getSector().getCharacterData().removeAdmin(person);
        adminStorage.add(person);
    }

    public ArrayList<PersonAPI> getAdminStorage() {
        return adminStorage;
    }

    public static boolean playerHasAdminCapacity() {
        int max = Global.getSector().getPlayerFleet().getCommander().getStats().getAdminNumber().getModifiedInt();
        return Global.getSector().getCharacterData().getAdmins().size() < max;
    }

    public void returnAdmin(PersonAPI person) {
        if (playerHasAdminCapacity() || Global.getSettings().isDevMode()) {
            Global.getSector().getCharacterData().addAdmin(person);
            adminStorage.remove(person);
        }
    }

    //Officer training

    public static boolean playerHasOfficerCapacity() {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        return Misc.getNumNonMercOfficers(playerFleet) < Misc.getMaxOfficers(playerFleet);
    }

    public static boolean personHasSkill(PersonAPI person, SkillSpecAPI skill) {
        for (MutableCharacterStatsAPI.SkillLevelAPI skillLevelAPI : person.getStats().getSkillsCopy()) {
            if (skillLevelAPI.getSkill() == skill) {
                return true;
            }
        }
        return false;
    }

    public static int getPersonalitySteps(String fromId, String toId) {
        List<String> ladder = new ArrayList<>(COLOURS_BY_PERSONALITY.keySet());
        int from = ladder.indexOf(fromId);
        int to = ladder.indexOf(toId);
        return from < 0 || to < 0 ? -1 : Math.abs(to - from);
    }

    public boolean hasAlphaCore() {
        return Commodities.ALPHA_CORE.equals(getAICoreId());
    }

    public int getMaxOfficerSteps() {
        return hasAlphaCore() ? 2 : 1;
    }

    public int getTrainedSteps(PersonAPI person) {
        MemoryAPI mem = person.getMemoryWithoutUpdate();
        return mem.contains(trainLevelKey) ? (int) mem.getFloat(trainLevelKey) : 0;
    }

    public int getOfficerStepsLeft(PersonAPI person) {
        return Math.max(0, getMaxOfficerSteps() - getTrainedSteps(person));
    }

    public List<String> getReachablePersonalities(PersonAPI person) {
        List<String> reachable = new ArrayList<>();
        String current = person.getPersonalityAPI().getId();
        int stepsLeft = getOfficerStepsLeft(person);

        for (String id : COLOURS_BY_PERSONALITY.keySet()) {
            int steps = getPersonalitySteps(current, id);
            if (steps > 0 && steps <= stepsLeft) reachable.add(id);
        }

        return reachable;
    }

    public String getOfficerTrainingTarget() {
        if (officerInTraining == null) return null;
        if (officerTrainingTarget != null) return officerTrainingTarget;

        // saves from before officerTrainingTarget: one step in currentDirection
        List<String> ladder = new ArrayList<>(COLOURS_BY_PERSONALITY.keySet());
        String current = officerInTraining.getPersonalityAPI().getId();
        int index = ladder.indexOf(current);
        if (index < 0 || currentDirection == null) return current;

        switch (currentDirection) {
            case BETTER:
                return ladder.get(Math.min(index + 1, ladder.size() - 1));
            case WEAKER:
                return ladder.get(Math.max(index - 1, 0));
            default:
                return current;
        }
    }

    public int getCurrentOfficerTrainingSteps() {
        if (officerInTraining == null) return 0;
        return Math.max(0, getPersonalitySteps(officerInTraining.getPersonalityAPI().getId(), getOfficerTrainingTarget()));
    }

    public int getRemainingOfficerTrainingDays() {
        return getCurrentOfficerTrainingDays() - officerDaysPassed;
    }

    public int getCurrentOfficerTrainingDays() {
        return getOfficerTrainingDays(Math.max(1, getCurrentOfficerTrainingSteps()));
    }

    public int getOfficerTrainingDays(int steps) {
        return steps * getOfficerTrainingDays();
    }

    public int getOfficerTrainingDays() {
        return Setting.PERSONALITY_TRAINING_DAY_COUNT.intValue() + 1 - currentAICoreDayReduction;
    }

    private void trainOfficerInTraining() {
        if (officerInTraining == null) return;

        if (officerDaysPassed < getCurrentOfficerTrainingDays() && !Global.getSettings().isDevMode()) {
            officerDaysPassed++;
            return;
        }

        PersonalityAPI oldPersonality = officerInTraining.getPersonalityAPI();
        int steps = getCurrentOfficerTrainingSteps();

        if (steps > 0) {
            officerInTraining.setPersonality(getOfficerTrainingTarget());
            addTrainedSteps(officerInTraining, steps);
        }

        PersonalityAPI newPersonality = officerInTraining.getPersonalityAPI();

        moveOfficerInTrainingToStorage();
        clearOfficerTraining();

        MessageIntel intel = new MessageIntel("An officer has finished training at %s.",
                Misc.getTextColor(), new String[]{(market.getName())}, market.getFaction().getBrightUIColor());
        intel.addLine("Personality changed from %s to %s.", Misc.getTextColor(), new String[]{oldPersonality.getDisplayName(), newPersonality.getDisplayName()}, COLOURS_BY_PERSONALITY.get(oldPersonality.getId()), COLOURS_BY_PERSONALITY.get(newPersonality.getId()));
        intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
        intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "trainingdone"));
        Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, market);
    }

    private void clearOfficerTraining() {
        officerTrainingTarget = null;
        currentDirection = trainingDirection.UNSET;
        officerDaysPassed = 0;
        market.getMemory().set(OFFICER_IS_TRAINING_KEY, false);
    }

    private boolean isOfficerInPlayerFleet(PersonAPI person) {
        return Global.getSector().getPlayerFleet().getFleetData().getOfficerData(person) != null;
    }

    public void moveOfficerInTrainingToStorage() {
        officerStorage.add(officerInTraining);
        officerInTraining = null;
    }

    public PersonAPI getOfficerInTraining() {
        return officerInTraining;
    }

    public void abortOfficerTraining(boolean moveToStorage_InsteadOfFleet) {
        if (officerInTraining != null) {
            if (!moveToStorage_InsteadOfFleet && playerHasOfficerCapacity()) {
                Global.getSector().getPlayerFleet().getFleetData().addOfficer(officerInTraining);
            } else {
                officerStorage.add(officerInTraining);
            }

            officerInTraining = null;
            clearOfficerTraining();
        }
    }

    private void addTrainedSteps(PersonAPI person, int steps) {
        person.getMemory().set(trainLevelKey, getTrainedSteps(person) + (float) steps);
    }

    public boolean isOfficerTrainingAllowed(PersonAPI person) {
        return !Misc.isUnremovable(person) && getOfficerStepsLeft(person) > 0;
    }

    public void setOfficerForTraining(PersonAPI person, String targetId) {
        int steps = getPersonalitySteps(person.getPersonalityAPI().getId(), targetId);
        if (officerInTraining != null || !isOfficerTrainingAllowed(person) || steps < 1 || steps > getOfficerStepsLeft(person)) return;

        float fee = getOfficerTrainingFee(steps);
        if (!BillingHelper.canAfford(fee)) return;

        if (isOfficerInPlayerFleet(person)) removeOfficerFromPlayerFleet(person);
        else if (!officerStorage.remove(person)) return;

        List<String> ladder = new ArrayList<>(COLOURS_BY_PERSONALITY.keySet());
        officerInTraining = person;
        officerTrainingTarget = targetId;
        currentDirection = ladder.indexOf(targetId) > ladder.indexOf(person.getPersonalityAPI().getId()) ? trainingDirection.BETTER : trainingDirection.WEAKER;
        officerDaysPassed = 0;
        market.getMemory().set(OFFICER_IS_TRAINING_KEY, true);

        chargeTraining(fee);
    }

    //officer storage

    public void storeOfficer(PersonAPI person) {
        if (removeOfficerFromPlayerFleet(person)) {
            officerStorage.add(person);
        }
    }

    public boolean removeOfficerFromPlayerFleet(PersonAPI person) {
        FleetDataAPI playerFleetData = Global.getSector().getPlayerFleet().getFleetData();

        for (OfficerDataAPI officer : playerFleetData.getOfficersCopy()) {
            if (officer.getPerson() == person) {
                removeOfficerFromShipCommand(person);
                playerFleetData.removeOfficer(person);
                return true;
            }
        }

        return false;
    }

    public void removeOfficerFromShipCommand(PersonAPI person) {
        FleetDataAPI playerFleetData = Global.getSector().getPlayerFleet().getFleetData();

        for (FleetMemberAPI ship : playerFleetData.getMembersListCopy()) {
            if (ship.getCaptain() == person) {
                ship.setCaptain(null);
            }
        }
    }

    public void returnOfficer(PersonAPI person) {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        if (playerHasOfficerCapacity()
                && person != null) {

            playerFleet.getFleetData().addOfficer(person);
            officerStorage.remove(person);
        }
    }

    public ArrayList<PersonAPI> getOfficerStorage() {
        return officerStorage;
    }

    //Hireable person spawning

    private boolean marketHasHireableOfficer(boolean checkOnlyAdmins) {
        boolean hireable = false;

        for (PersonAPI person : market.getPeopleCopy()) {
            Collection<String> keys = person.getMemoryWithoutUpdate().getKeys();

            if (keys.contains("$ome_hireable")) {
                if (checkOnlyAdmins && keys.contains("$ome_isAdmin")) {
                    hireable = person.getMemoryWithoutUpdate().getBoolean("$ome_hireable");
                } else if (!checkOnlyAdmins && !keys.contains("$ome_isAdmin")) {
                    hireable = person.getMemoryWithoutUpdate().getBoolean("$ome_hireable");
                }
            }
        }
        return hireable;
    }

    private void createHireablePerson(boolean createAdministrator) {
        OfficerManagerEvent officerManager = getCurrentOfficerManagerEvent();

        if (officerManager != null) {
            if (createAdministrator) {
                officerManager.addAvailableAdmin(createAdmin());
            } else {
                officerManager.addAvailable(createOfficer());
            }

        } else {
            debugMessage("Unable to create a person due to OfficerManager not found");
        }
    }

    private OfficerManagerEvent getCurrentOfficerManagerEvent() {
        for (CampaignEventPlugin b : Global.getSector().getEventManager().getOngoingEvents()) {
            if (b.getEventType().equals(Events.OFFICER_MANAGER) && b instanceof OfficerManagerEvent) {
                return (OfficerManagerEvent) b;
            }
        }

        return null;
    }

    private OfficerManagerEvent.AvailableOfficer createAdmin() {
        WeightedRandomPicker<Integer> tierPicker = new WeightedRandomPicker<Integer>();
        tierPicker.add(0, 90);
        tierPicker.add(1, 10);

        int tier = tierPicker.pick();

        PersonAPI person = OfficerManagerEvent.createAdmin(market.getFaction(), tier, random);
        person.setFaction(Factions.INDEPENDENT);

        String hireKey = "adminHireTier" + tier;
        int hiringBonus = Global.getSettings().getInt(hireKey);

        int salary = (int) Misc.getAdminSalary(person);

        return new OfficerManagerEvent.AvailableOfficer(person, market.getId(), hiringBonus, salary);
    }

    protected OfficerManagerEvent.AvailableOfficer createOfficer() {
        float rand = random.nextFloat();
        int level = 1;
        if (rand > 0.65f) level = 2;
        if (rand > 0.90f) level = 3;

        PersonAPI person = OfficerManagerEvent.createOfficer(market.getFaction(),
                level, OfficerManagerEvent.SkillPickPreference.ANY, false,
                null, false, rand > 0.7f, 1, random);

        person.setFaction(Factions.INDEPENDENT);
        person.setPostId(Ranks.POST_OFFICER_FOR_HIRE);

        int salary = (int) Misc.getOfficerSalary(person);

        return new OfficerManagerEvent.AvailableOfficer(person, market.getId(), person.getStats().getLevel() * 2000, salary);
    }

    private boolean getRandomBoolean(float p) {
        return random.nextFloat() < p;
    }

    //AI core handling

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Alpha-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Officers can train up to %s, at once or one after the other.", 0.0F, highlight, new String[]{"two personality steps"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Officers can train up to %s, at once or one after the other.", 0.0F, highlight, new String[]{"two personality steps"});
        }
    }

    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Beta-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Beta-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Decreases all training times by %s.", 0f, highlight, new String[]{"1 month"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Decreases all training times by %s.", 0f, highlight, new String[]{"1 month"});
        }
    }

    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Gamma-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Gamma-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Reduces upkeep cost by %s.", 0.0F, highlight, new String[]{(int) ((1.0F - gammaCoreUpkeepRed) * 100.0F) + "%"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Reduces upkeep cost by %s.", 0.0F, highlight, new String[]{(int) ((1.0F - gammaCoreUpkeepRed) * 100.0F) + "%"});
        }
    }

    protected void applyAICoreToIncomeAndUpkeep() {
        if (aiCoreId != null && !aiCoreId.equals(Commodities.ALPHA_CORE) && !aiCoreId.equals(Commodities.GAMMA_CORE)) {
            this.getUpkeep().unmodifyMult("ind_core");
            currentAICoreDayReduction = betacoreReduction;
        } else if ("gamma_core".equals(aiCoreId)) {
            String name = "Gamma Core assigned";
            this.getUpkeep().modifyMult("ind_core", gammaCoreUpkeepRed, name);
            currentAICoreDayReduction = 0;
        } else {
            this.getUpkeep().unmodifyMult("ind_core");
            market.getStability().unmodify("ind_core");
            currentAICoreDayReduction = 0;
        }
    }

    protected void updateAICoreToSupplyAndDemandModifiers() {
    }
}
