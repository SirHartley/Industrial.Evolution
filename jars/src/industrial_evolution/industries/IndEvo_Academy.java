package industrial_evolution.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import industrial_evolution.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.characters.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.plugins.OfficerLevelupPlugin;
import industrial_evolution.campaign.ids.IndEvo_ids;
import industrial_evolution.plugins.timers.IndEvo_newDayListener;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.DynamicStatsAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.awt.*;
import java.util.List;
import java.util.*;

import static industrial_evolution.campaign.rulecmd.academyRules.IndEvo_AcademyVariables.ACADEMY_MARKET_ID;

public class IndEvo_Academy extends BaseIndustry implements IndEvo_newDayListener, EconomyTickListener {

    public final String OFFICER_IS_TRAINING_KEY = "$IndEvo_officerIsTraining";
    public final String ADMIN_IS_TRAINING_KEY = "$IndEvo_adminIsTraining";

    //officer handling vars
    protected PersonAPI officerInTraining = null;
    protected trainingDirection currentDirection = trainingDirection.UNSET;
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

    public enum trainingDirection {
        UNSET,
        BETTER,
        WEAKER
    }

    //debug-Logger
    private static void debugMessage(String Text) {
        boolean DEBUG = false; //set to false once done
        if (DEBUG) {
            Global.getLogger(IndEvo_Academy.class).info(Text);
        }
    }

    @Override
    public void apply() {
        super.apply(true);
        Global.getSector().getListenerManager().addListener(this, true);
        if (!market.getId().equals(ACADEMY_MARKET_ID)) increaseOfficerQuality();
    }

    @Override
    public void unapply() {
        super.unapply();
        Global.getSector().getListenerManager().removeListener(this);
        unmodifyOfficerQualityIncrease();
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
            if (!market.getId().equals(ACADEMY_MARKET_ID)) {
                //spawn officers/admins if the market has none:
                if (!marketHasHireableOfficer(false) && getRandomBoolean(Global.getSettings().getFloat("dailyOfficerSpawnChance"))) {
                    createHireablePerson(false);
                }

                if (!marketHasHireableOfficer(true) && getRandomBoolean(Global.getSettings().getFloat("dailyAdminSpawnChance"))) {
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

        float total = calculateFeeStorageCost() + calculateSalaryStorageCost(); //only one sum per month or it will overwrite each other!
        chargePlayer(total, true);
    }

    @Override
    public void reportEconomyMonthEnd() {

    }

    @Override
    public boolean isAvailableToBuild() {
        return Global.getSettings().getBoolean("Academy");
    }

    @Override
    public boolean showWhenUnavailable() {
        return Global.getSettings().getBoolean("Academy");
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
            String newPersonality = getNextPersonalityForTooltip();
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
            return Global.getSettings().getFloat("monthlyAIStorageCost") * personAmount;
        }

        return 0;
    }

    public static boolean playerCanAffordCost(float cost) {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        return playerFleet.getCargo().getCredits().get() >= cost;
    }

    private void chargePlayer(float amount, boolean onMonthEnd) {
        //this clears the upkeep every time it is called for AI colonies, so only call it once!

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (!onMonthEnd) {
            playerFleet.getCargo().getCredits().subtract(amount);
        } else if (!market.isPlayerOwned()) {
            MonthlyReport report = SharedData.getData().getCurrentReport();

            MonthlyReport.FDNode marketsNode = report.getNode(MonthlyReport.OUTPOSTS);
            marketsNode.name = "Colonies";
            marketsNode.custom = MonthlyReport.OUTPOSTS;
            marketsNode.tooltipCreator = report.getMonthlyReportTooltip();

            MonthlyReport.FDNode mNode = report.getNode(marketsNode, market.getId());
            mNode.name = market.getName() + " (" + market.getSize() + ")";
            mNode.custom = market;

            MonthlyReport.FDNode indNode = report.getNode(mNode, "industries");

            for (Industry curr : market.getIndustries()) {
                if (!curr.getId().equals(IndEvo_ids.REPAIRDOCKS)) { //Have to exclude repdock or player will not be billed repairs
                    MonthlyReport.FDNode iNode = report.getNode(indNode, curr.getId());
                    iNode.income = 0;
                    iNode.upkeep = 0;
                }
            }

            MonthlyReport.FDNode academyNode = report.getNode(mNode, "Academy");
            academyNode.name = "Academy Storage";
            academyNode.mapEntity = market.getPrimaryEntity();
            academyNode.tooltipCreator = report.getMonthlyReportTooltip();
            academyNode.icon = getCurrentImage();
            academyNode.custom = this;
            academyNode.custom2 = this;

            MonthlyReport.FDNode iNode = report.getNode(academyNode, this.getId());
            iNode.name = getCurrentName();
            iNode.upkeep = 0;
            iNode.custom = this;
            iNode.mapEntity = market.getPrimaryEntity();

            iNode.upkeep += amount;
        } else {
            MonthlyReport report = SharedData.getData().getCurrentReport();
            MonthlyReport.FDNode marketsNode = report.getNode(MonthlyReport.OUTPOSTS);
            MonthlyReport.FDNode mNode = report.getNode(marketsNode, market.getId());
            MonthlyReport.FDNode indNode = report.getNode(mNode, "industries");
            MonthlyReport.FDNode iNode = report.getNode(indNode, getId());

            iNode.upkeep += amount;
        }
    }

    //officer quality increases

    public static final float OFFICER_PROBABILITY_MULT = 1.5f;
    public static final float OFFICER_MAX_LEVEL_MULT = 1.5f;
    public static final float OFFICER_MAX_LEVEL_FOREIGN_MULT = 1.3f;

    private void increaseOfficerQuality() {
        for (MarketAPI market : IndEvo_IndustryHelper.getMarketsInLocation(this.market.getContainingLocation(), this.market.getFactionId())) {
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
        for (MarketAPI market : IndEvo_IndustryHelper.getMarketsInLocation(this.market.getContainingLocation(), this.market.getFactionId())) {
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

    public int getRemainingAdminTrainingDays() {
        return getAdminTrainingDays() - adminDaysPassed;
    }

    public int getAdminTrainingDays() {
        return Global.getSettings().getInt("adminTrainingDayCount") + 1 - currentAICoreDayReduction;
    }

    public PersonAPI getAdminInTraining() {
        return adminInTraining;
    }

    public void setAdminInTraining(PersonAPI person) {
        if (canTrainAdminPerson(person)
                && isAdminInPlayerFleet(person)) {

            Global.getSector().getCharacterData().removeAdmin(person);
            adminInTraining = person;
            market.getMemory().set(ADMIN_IS_TRAINING_KEY, true);
        } else if (canTrainAdminPerson(person) && adminStorage.contains(person)) {
            adminStorage.remove(person);
            adminInTraining = person;
            market.getMemory().set(ADMIN_IS_TRAINING_KEY, true);
        }

        chargePlayer(Global.getSettings().getInt("adminTrainingCost"), false);
    }

    public void abortAdminTraining(boolean moveToStorage_InsteadOfFleet) {
        if (adminInTraining != null) {
            if (moveToStorage_InsteadOfFleet) {
                moveAdminInTrainingToStorage();
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
        int skillCount = 0;
        boolean notInstalled = true;

        //Admin has less than 2 skills
        List<String> allSkillIds = Global.getSettings().getSortedSkillIds();
        for (String skillId : allSkillIds) {
            SkillSpecAPI skill = Global.getSettings().getSkillSpec(skillId);
            if (skill.isAdminSkill() && personHasSkill(person, skill)) {
                skillCount++;
            }
        }

        for (MarketAPI market : Misc.getFactionMarkets(Global.getSector().getPlayerFaction())) {
            if (market.getAdmin().getId().equals(person.getId())) {
                notInstalled = false;
            }
        }

        return skillCount < 2 && notInstalled;
    }

    public void addRandomAdminSkill(PersonAPI person) {
        if (random == null) random = new Random();

        person.getStats().setSkipRefresh(true);
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);

        List<String> allSkillIds = Global.getSettings().getSortedSkillIds();
        for (String skillId : allSkillIds) {
            SkillSpecAPI skill = Global.getSettings().getSkillSpec(skillId);
            if (skill.isAdminSkill()
                    && !personHasSkill(person, skill)
                    && !skill.getTags().contains("ai_core_only")
                    && !skill.getTags().contains("player_only")
                    && !skill.getId().equals("ocua_Arc_Cognition")) {
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
        Global.getSector().getCharacterData().removeAdmin(person);
        adminStorage.add(person);
    }

    public ArrayList<PersonAPI> getAdminStorage() {
        return adminStorage;
    }

    public void returnAdmin(PersonAPI person) {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        int max = playerFleet.getCommander().getStats().getAdminNumber().getModifiedInt();

        if (Global.getSector().getCharacterData().getAdmins().size() < max) {
            Global.getSector().getCharacterData().addAdmin(person);
            adminStorage.remove(person);
        }
    }

    //Officer training

    public static boolean playerHasOfficerCapacity() {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        int max = playerFleet.getCommander().getStats().getOfficerNumber().getModifiedInt();
        int i = 0;
        for (OfficerDataAPI data : playerFleet.getFleetData().getOfficersCopy()){
            if (data.getPerson().isPlayer() || Misc.isMercenary(data.getPerson())) continue;

            i++;
        }

        return i <= max;
    }

    public static boolean personHasSkill(PersonAPI person, SkillSpecAPI skill) {
        for (MutableCharacterStatsAPI.SkillLevelAPI skillLevelAPI : person.getStats().getSkillsCopy()) {
            if (skillLevelAPI.getSkill() == skill) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOfficerTrainingDirectionAllowed(PersonAPI person, trainingDirection direction) {
        String personality = person.getPersonalityAPI().getId();
        boolean notTooWeak = !(personality.equals(Personalities.TIMID) && direction.equals(trainingDirection.WEAKER));
        boolean notTooStrong = !(personality.equals(Personalities.RECKLESS) && direction.equals(trainingDirection.BETTER));

        return notTooStrong && notTooWeak;
    }

    public int getRemainingOfficerTrainingDays() {
        return getOfficerTrainingDays() - officerDaysPassed;
    }

    public int getOfficerTrainingDays() {
        return Global.getSettings().getInt("personalityTrainingDayCount") + 1 - currentAICoreDayReduction;
    }

    private void trainOfficerInTraining() {
        if (officerInTraining != null) {

            if (officerDaysPassed >= getOfficerTrainingDays() || Global.getSettings().isDevMode()) {
                HashMap<String, Color> colourByPersonality = new HashMap<>();
                colourByPersonality.put(Personalities.TIMID, Color.BLUE);
                colourByPersonality.put(Personalities.CAUTIOUS, Color.CYAN);
                colourByPersonality.put(Personalities.STEADY, Color.GREEN);
                colourByPersonality.put(Personalities.AGGRESSIVE, Color.ORANGE);
                colourByPersonality.put(Personalities.RECKLESS, Color.RED);

                String oldPersonality = officerInTraining.getPersonalityAPI().getDisplayName();

                changeOfficerPersonality(currentDirection);

                String newPersonality = officerInTraining.getPersonalityAPI().getDisplayName();

                moveOfficerInTrainingToStorage();

                currentDirection = null;
                officerDaysPassed = 0;
                market.getMemory().set(OFFICER_IS_TRAINING_KEY, false);

                MessageIntel intel = new MessageIntel("An officer has finished training at %s.",
                        Misc.getTextColor(), new String[]{(market.getName())}, market.getFaction().getBrightUIColor());
                intel.addLine("Personality changed from %s to %s.", Misc.getTextColor(), new String[]{oldPersonality, newPersonality}, colourByPersonality.get(oldPersonality.toLowerCase()), colourByPersonality.get(newPersonality.toLowerCase()));
                intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
                intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "trainingdone"));
                Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, market);

            } else {
                officerDaysPassed++;
            }
        }
    }

    private boolean isOfficerInPlayerFleet(PersonAPI person) {
        return Global.getSector().getPlayerFleet().getFleetData().getOfficerData(person) != null;
    }

    public trainingDirection getCurrentDirection() {
        return currentDirection;
    }

    private void changeOfficerPersonality(trainingDirection direction) {
        if (officerInTraining == null) {
            return;
        }

        ArrayList<String> personalities = new ArrayList<>();
        personalities.add(Personalities.TIMID);
        personalities.add(Personalities.CAUTIOUS);
        personalities.add(Personalities.STEADY);
        personalities.add(Personalities.AGGRESSIVE);
        personalities.add(Personalities.RECKLESS);

        PersonAPI person = officerInTraining;
        int currentIndex = personalities.indexOf(person.getPersonalityAPI().getId());
        int nextIndex;

        if (currentIndex == -1) {
            debugMessage("officer personality is not valid");
            return;
        }

        switch (direction) {
            case BETTER:
                nextIndex = currentIndex < 4 ? currentIndex + 1 : currentIndex;
                person.setPersonality(personalities.get(nextIndex));
                applyTrainingMemoryTag(person);
                break;
            case WEAKER:
                nextIndex = currentIndex > 0 ? currentIndex - 1 : currentIndex;
                person.setPersonality(personalities.get(nextIndex));
                applyTrainingMemoryTag(person);
                break;
            default:
                break;
        }
    }

    public String getNextPersonalityForTooltip() {
        ArrayList<String> personalities = new ArrayList<>();
        personalities.add(Personalities.TIMID);
        personalities.add(Personalities.CAUTIOUS);
        personalities.add(Personalities.STEADY);
        personalities.add(Personalities.AGGRESSIVE);
        personalities.add(Personalities.RECKLESS);

        PersonAPI person = officerInTraining;
        int currentIndex = personalities.indexOf(person.getPersonalityAPI().getId());
        int nextIndex;

        if (currentIndex == -1) {
            debugMessage("officer personality is not valid");
            return null;
        }

        switch (currentDirection) {
            case BETTER:
                nextIndex = currentIndex < 4 ? currentIndex + 1 : currentIndex;
                return personalities.get(nextIndex);
            case WEAKER:
                nextIndex = currentIndex > 0 ? currentIndex - 1 : currentIndex;
                return personalities.get(nextIndex);
            default:
                break;
        }

        return null;
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
                returnOfficer(officerInTraining);

            } else {
                officerStorage.add(officerInTraining);
            }

            officerDaysPassed = 0;
            currentDirection = null;
            officerInTraining = null;
            market.getMemory().set(OFFICER_IS_TRAINING_KEY, false);
        }
    }

    private void applyTrainingMemoryTag(PersonAPI person) {
        if (!person.getMemoryWithoutUpdate().getKeys().contains(trainLevelKey)) {
            person.getMemory().set(trainLevelKey, 1f);
        } else {
            person.getMemory().set(trainLevelKey, person.getMemoryWithoutUpdate().getFloat(trainLevelKey) + 1f);
        }
    }

    public boolean isOfficerTrainingAllowed(PersonAPI person) {
        if (Misc.isUnremovable(person)) return false;

        boolean alphaInstalled = getAICoreId() != null && getAICoreId().equals(Commodities.ALPHA_CORE);
        float trainLevel = person.getMemoryWithoutUpdate().getKeys().contains(trainLevelKey) ? person.getMemoryWithoutUpdate().getFloat(trainLevelKey) : 0f;

        return trainLevel == 0f
                || (trainLevel < 2f && alphaInstalled);
    }

    public boolean isOfficerTrainingAllowed(PersonAPI person, trainingDirection direction) {
        boolean alphaInstalled = getAICoreId() != null && getAICoreId().equals(Commodities.ALPHA_CORE);
        float trainLevel = person.getMemoryWithoutUpdate().getKeys().contains(trainLevelKey) ? person.getMemoryWithoutUpdate().getFloat(trainLevelKey) : 0f;

        return trainLevel == 0f
                || (trainLevel < 2f && alphaInstalled)
                || isOfficerTrainingDirectionAllowed(person, direction);
    }

    public void setOfficerForTraining(PersonAPI person, trainingDirection direction) {
        if (isOfficerTrainingAllowed(person, direction) && isOfficerInPlayerFleet(person)) {
            removeOfficerFromPlayerFleet(person);

            officerInTraining = person;
            currentDirection = direction;
            market.getMemory().set(OFFICER_IS_TRAINING_KEY, true);
        } else if (isOfficerTrainingAllowed(person, direction) && officerStorage.contains(person)) {

            officerStorage.remove(person);
            officerInTraining = person;
            currentDirection = direction;
            market.getMemory().set(OFFICER_IS_TRAINING_KEY, true);
        }

        chargePlayer(Global.getSettings().getInt("personalityTrainingCost"), false);
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
            text.addPara(pre + "Enables the Academy to %s via training.", 0.0F, highlight, new String[]{"change officer personality a second time"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Enables the Academy to %s via training.", 0.0F, highlight, new String[]{"change officer personality a second time"});
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
            currentAICoreDayReduction = betacoreReduction;
        } else if ("gamma_core".equals(aiCoreId)) {
            String name = "Gamma Core assigned";
            this.getUpkeep().modifyMult("ind_core", gammaCoreUpkeepRed, name);
        } else {
            this.getUpkeep().unmodifyMult("ind_core");
            market.getStability().unmodify("ind_core");
            currentAICoreDayReduction = 0;
        }
    }

    protected void updateAICoreToSupplyAndDemandModifiers() {
    }
}
