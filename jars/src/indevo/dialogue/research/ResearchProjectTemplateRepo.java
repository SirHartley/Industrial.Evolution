package indevo.dialogue.research;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Drops;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.intel.events.ht.HyperspaceTopographyEventIntel;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.utils.ModPlugin;
import indevo.utils.helper.Settings;

import java.util.*;

public class ResearchProjectTemplateRepo {

    //fairy

    public static Map<String, ResearchProject> RESEARCH_PROJECTS = new HashMap<String, ResearchProject>() {{

        put(Ids.PROJ_PROSPECTOR, new ResearchProject(Ids.PROJ_PROSPECTOR,
                "Project Prospector", 200, false) {

            public static final int POINTS_ON_COMPLETION = 50;

            @Override
            public boolean isRepeatable() {
                return true;
            }

            @Override
            public boolean display() {
                HyperspaceTopographyEventIntel intel = HyperspaceTopographyEventIntel.get();

                if (intel == null) return false;
                int progress = intel.getProgress();
                return progress > 100 && progress < 1000;
            }

            @Override
            public CargoAPI getRewards() {
                CargoAPI c = Global.getFactory().createCargo(true);
                c.addSpecial(new SpecialItemData(Items.TOPOGRAPHIC_DATA, null), 1);

                HyperspaceTopographyEventIntel.addFactorCreateIfNecessary(new HyperspaceTopographyProjectFactor(POINTS_ON_COMPLETION), null);

                return c;
            }

            @Override
            public void addTooltipOutputOnCompletion(TooltipMakerAPI tooltip) {
                tooltip.addPara(HyperspaceTopographyEventIntel.get().getSmallDescriptionTitle() + " progress gained: %s", 10f, Misc.getPositiveHighlightColor(), "" + POINTS_ON_COMPLETION);
            }

            @Override
            public List<RequiredItem> getRequiredItems() {
                //outputs 50k so needs 100k input for 5% progress
                List<RequiredItem> list = new ArrayList<>();
                list.add(new RequiredItem(Commodities.SURVEY_DATA_1, CargoAPI.CargoItemType.RESOURCES, 1f));
                list.add(new RequiredItem(Commodities.SURVEY_DATA_2, CargoAPI.CargoItemType.RESOURCES, 3f));
                list.add(new RequiredItem(Commodities.SURVEY_DATA_3, CargoAPI.CargoItemType.RESOURCES, 5f));
                list.add(new RequiredItem(Commodities.SURVEY_DATA_4, CargoAPI.CargoItemType.RESOURCES, 10f));
                list.add(new RequiredItem(Commodities.SURVEY_DATA_5, CargoAPI.CargoItemType.RESOURCES, 30f));

                return list;
            }

            @Override
            public String getLongDesc() {
                return "An ongoing project researching the influence of topological data on hyperspace wells. Completing it will provide some insight on hyperspace topology.";
            }

            @Override
            public String getShortDesc() {
                return "Further your knowledge of the ethereal by contributing data about the mundane.";
            }
        });

        put(Ids.PROJ_NAVI, new ResearchProject(Ids.PROJ_NAVI,
                "Project Navi", 200, false) {

            @Override
            public boolean display() {
                return Settings.getBoolean(Settings.PETS);
            }

            @Override
            public CargoAPI getRewards() {
                CargoAPI c = Global.getFactory().createCargo(true);
                c.addSpecial(new SpecialItemData(ItemIds.PET_CHAMBER, "fairy"), 1);

                return c;
            }

            @Override
            public void addTooltipOutputOnCompletion(TooltipMakerAPI tooltip) {
                tooltip.addPara(Misc.ucFirst("The " + PetDataRepo.get("fairy").species) + " will now be available for purchase at the " + Global.getSettings().getIndustrySpec(Ids.PET_STORE).getName(), 10f);
            }

            @Override
            public List<RequiredItem> getRequiredItems() {
                List<RequiredItem> list = new ArrayList<>();
                list.add(new RequiredItem(ItemIds.RARE_PARTS, CargoAPI.CargoItemType.RESOURCES, 1f));
                list.add(new RequiredItem(ItemIds.PARTS, CargoAPI.CargoItemType.RESOURCES, 0.1f));
                list.add(new RequiredItem(Items.DRONE_REPLICATOR, CargoAPI.CargoItemType.SPECIAL, 200f));

                return list;
            }

            @Override
            public String getLongDesc() {
                return "A one-off project attempting to create clockwork-mechanical life by a graduate student. While this may not instill confidence, remember that better things have come from worse circumstances in the past.";
            }

            @Override
            public String getShortDesc() {
                return "Hey! Hey! Hey! Captain! You want a fairy, right? Right? Hey!";
            }
        });

        put(Ids.PROJ_SNOWBLIND, new ResearchProject(Ids.PROJ_SNOWBLIND,
                "Project Snowblind", 4, false) {

            @Override
            public boolean display() {
                return DoritoGunFoundChecker.isGunFound();
            }

            @Override
            public CargoAPI getRewards() {
                CargoAPI c = Global.getFactory().createCargo(true);
                c.addWeapons("IndEvo_cryoartillery", 2);
                c.addSpecial(new SpecialItemData(Items.WEAPON_BP, "IndEvo_cryoartillery"), 1);

                return c;
            }

            @Override
            public List<RequiredItem> getRequiredItems() {
                List<RequiredItem> list = new ArrayList<>();
                list.add(new RequiredItem("cryoflux", CargoAPI.CargoItemType.WEAPONS, 2f));
                list.add(new RequiredItem("cryoblaster", CargoAPI.CargoItemType.WEAPONS, 2f));

                return list;
            }

            @Override
            public String getLongDesc() {
                return "A one-off project attempting to unravel the secrets of the artifact-tech cryogenic weapons. Completion may give access to exotic firing solutions.";
            }

            @Override
            public String getShortDesc() {
                return "An attempt to reverse engineer the surreal freezing capabilities of the artifact weapons. What could go wrong?";
            }
        });

        put(Ids.PROJ_SPITFIRE, new ResearchProject(Ids.PROJ_SPITFIRE, "Project Spitfire", 4, false) {

            @Override
            public boolean display() {
                return DoritoGunFoundChecker.isGunFound();
            }

            @Override
            public CargoAPI getRewards() {
                CargoAPI c = Global.getFactory().createCargo(true);
                c.addWeapons("IndEvo_degrader", 3);
                c.addSpecial(new SpecialItemData(Items.WEAPON_BP, "IndEvo_degrader"), 1);

                return c;
            }

            @Override
            public List<RequiredItem> getRequiredItems() {
                List<RequiredItem> list = new ArrayList<>();
                list.add(new RequiredItem("amsrm", CargoAPI.CargoItemType.WEAPONS, 1.5f));
                list.add(new RequiredItem("resonatormrm", CargoAPI.CargoItemType.WEAPONS, 2f));
                list.add(new RequiredItem("disintegrator", CargoAPI.CargoItemType.WEAPONS, 2f));

                return list;
            }

            @Override
            public String getLongDesc() {
                return "A one-off project attempting to unravel the secrets of the artifact-tech anti-armor weapons. Completion may give access to exotic firing solutions.";
            }

            @Override
            public String getShortDesc() {
                return "Nothing can punch through armor like a Disintegrator, and the Galatian Academy already has customers lining up for the results of this project.";
            }
        });

        put(Ids.PROJ_EUREKA, new ResearchProject(Ids.PROJ_EUREKA, "Project Eureka", 4, false) {

            @Override
            public boolean display() {
                return DoritoGunFoundChecker.isGunFound();
            }

            @Override
            public CargoAPI getRewards() {
                CargoAPI c = Global.getFactory().createCargo(true);
                c.addWeapons("IndEvo_causalitygun", 1);
                c.addSpecial(new SpecialItemData(Items.WEAPON_BP, "IndEvo_causalitygun"), 1);

                return c;
            }

            @Override
            public List<RequiredItem> getRequiredItems() {
                List<RequiredItem> list = new ArrayList<>();
                list.add(new RequiredItem("disintegrator", CargoAPI.CargoItemType.WEAPONS, 2f));
                list.add(new RequiredItem("rifttorpedo", CargoAPI.CargoItemType.WEAPONS, 4f));
                list.add(new RequiredItem("realitydisruptor", CargoAPI.CargoItemType.WEAPONS, 4f));

                return list;
            }

            @Override
            public String getLongDesc() {
                return "Behold, death.";
            }

            @Override
            public String getShortDesc() {
                return "There used to be a saying, that some things should best be left untouched. Used to. Reality shall bend to our will.";
            }
        });

        put(Ids.PROJ_PARALLAX, new ResearchProject(Ids.PROJ_PARALLAX, "Project Parallax", 4, false) {

            @Override
            public boolean display() {
                return DoritoGunFoundChecker.isGunFound();
            }

            @Override
            public CargoAPI getRewards() {
                CargoAPI c = Global.getFactory().createCargo(true);
                c.addWeapons("IndEvo_pulsedcarbine", 3);
                c.addSpecial(new SpecialItemData(Items.WEAPON_BP, "IndEvo_pulsedcarbine"), 1);

                return c;
            }

            @Override
            public List<RequiredItem> getRequiredItems() {
                List<RequiredItem> list = new ArrayList<>();
                list.add(new RequiredItem("minipulser", CargoAPI.CargoItemType.WEAPONS, 1.5f));
                list.add(new RequiredItem("shockrepeater", CargoAPI.CargoItemType.WEAPONS, 1.5f));
                list.add(new RequiredItem("vpdriver", CargoAPI.CargoItemType.WEAPONS, 4f));

                return list;
            }

            @Override
            public String getLongDesc() {
                return "A one-off project attempting to unravel the secrets of the artifact-tech projectile weapons. Completion may give access to exotic firing solutions.";
            }

            @Override
            public String getShortDesc() {
                return "The possibilities of this new tech are endless - as is the profit, once the Academy manages to get the components to cooperate.";
            }
        });

        put(Ids.PROJ_KEYHOLE, new ResearchProject(Ids.PROJ_KEYHOLE, "Project Keyhole", 4, false) {

            @Override
            public boolean display() {
                return DoritoGunFoundChecker.isGunFound();
            }

            @Override
            public CargoAPI getRewards() {
                CargoAPI c = Global.getFactory().createCargo(true);
                c.addWeapons("IndEvo_riftgun", 3);
                c.addSpecial(new SpecialItemData(Items.WEAPON_BP, "IndEvo_riftgun"), 1);

                return c;
            }

            @Override
            public List<RequiredItem> getRequiredItems() {
                List<RequiredItem> list = new ArrayList<>();
                list.add(new RequiredItem("riftlance", CargoAPI.CargoItemType.WEAPONS, 1.5f));
                list.add(new RequiredItem("shockrepeater", CargoAPI.CargoItemType.WEAPONS, 1.5f));
                list.add(new RequiredItem("amsrm", CargoAPI.CargoItemType.WEAPONS, 1.5f));
                list.add(new RequiredItem("riftbeam", CargoAPI.CargoItemType.WEAPONS, 2f));
                list.add(new RequiredItem("riftcascade", CargoAPI.CargoItemType.WEAPONS, 4f));

                return list;
            }

            @Override
            public String getLongDesc() {
                return "A one-off project attempting to unravel the secrets of the artifact-tech rift weapons. Completion may give access to exotic firing solutions.";
            }

            @Override
            public String getShortDesc() {
                return "The most common of the arcane tech that recently surfaced is still leagues ahead of anything contemporary. Once reverse engineered, this will make a fine addition to any collection.";
            }
        });

        put(Ids.PROJ_SPYGLASS, new ResearchProject(Ids.PROJ_SPYGLASS, "Project Spyglass", 500, true) {

            @Override
            public boolean display() {
                return true;
            }

            @Override
            public CargoAPI getRewards() {
                List<Random> randomList = getRandomForProj(Ids.PROJ_SPYGLASS); //Anti-save-scum

                List<SalvageEntityGenDataSpec.DropData> dropRandom = new ArrayList<>();

                dropRandom.add(DropDataCreator.createDropData(Drops.AI_CORES2, 3));
                dropRandom.add(DropDataCreator.createDropData(Drops.AI_CORES3, 2));
                dropRandom.add(DropDataCreator.createDropData("blueprints_low", 4));
                dropRandom.add(DropDataCreator.createDropData("indEvo_tech", 4));
                dropRandom.add(DropDataCreator.createDropData(Drops.ANY_HULLMOD_MEDIUM, 4));
                dropRandom.add(DropDataCreator.createDropData("rare_tech_low", 1));

                //create 5 rolls and return the best

                float max = 0f;
                CargoAPI extra = null;
                for (Random r : randomList) {
                    CargoAPI cargo = SalvageEntity.generateSalvage(r, 1f, 1f, 1f, 1f, null, dropRandom);

                    float val = 0f;
                    for (CargoStackAPI stack : cargo.getStacksCopy()) {
                        val += stack.getBaseValuePerUnit() * stack.getSize();
                    }

                    ModPlugin.log("drop value " + val);

                    if (val > max) {
                        extra = cargo;
                        max = val;
                    }
                }

                //1% chance to reward 9.999 units of a random, cheap weapon like vulcans
                //Add a stupid flavour text like "Look man, we don't know what else to do with that, the boss has a serious hoarding problem"
                if (new Random().nextFloat() > 0.99) {
                    extra.clear();
                    extra.addWeapons("vulcan", 9999);
                }

                return extra;
            }

            @Override
            public List<RequiredItem> getRequiredItems() {
                List<RequiredItem> list = new ArrayList<>();
                list.add(new RequiredItem(ItemIds.RARE_PARTS, CargoAPI.CargoItemType.RESOURCES, 1f));

                return list;
            }

            @Override
            public String getLongDesc() {
                return "An ongoing project trading relic components for artifact-level inventory no longer needed by the Academy. Who knows what you'll get?";
            }

            @Override
            public String getShortDesc() {
                return "You bring us some old junk, and we trade it for stuff we got lying around. Sound fair?";
            }
        });


        put(Ids.PROJ_TRANSISTOR, new ResearchProject(Ids.PROJ_TRANSISTOR, "Project Transistor", 80, true) {

            @Override
            public boolean display() {
                return true;
            }

            @Override
            public CargoAPI getRewards() {
                List<Random> randomList = getRandomForProj(Ids.PROJ_TRANSISTOR); //Anti-save-scum

                List<SalvageEntityGenDataSpec.DropData> dropRandom = new ArrayList<>();
                dropRandom.add(DropDataCreator.createDropData("indEvo_consumables_always", 5));
                Random random = randomList.get(0);

                return SalvageEntity.generateSalvage(random, 1f, 1f, 1f, 1f, null, dropRandom);
            }

            @Override
            public List<RequiredItem> getRequiredItems() {
                List<RequiredItem> list = new ArrayList<>();
                list.add(new RequiredItem(ItemIds.RARE_PARTS, CargoAPI.CargoItemType.RESOURCES, 1f));

                return list;
            }

            @Override
            public String getLongDesc() {
                return "A clear display of the mercantile spirit of the academy, this \"research project\" is really just trading new consumable items for relic components. It's a solid deal.";
            }

            @Override
            public String getShortDesc() {
                return "Five brand new pieces of tech for random scrap you picked up! Greatest deal of your life, right?";
            }
        });
    }};

    public static List<Random> getRandomForProj(String id) {
        String key = "$IndEvo_ProjRand_" + id;
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (mem.contains(key)) return (List<Random>) mem.get(key);
        else {
            List<Random> l = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Random r = new Random();
                l.add(r);
            }

            mem.set(key, l);
            return l;
        }
    }

    public static int getNumProjectsPlayerCanContribute() {
        int i = 0;
        CargoAPI playerCargo = Global.getSector().getPlayerFleet().getCargo();
        OUTER:
        for (Map.Entry<String, ResearchProject> proj : RESEARCH_PROJECTS.entrySet()) {
            if (proj.getValue().display() && !proj.getValue().getProgress().redeemed) {
                for (RequiredItem item : proj.getValue().getRequiredItems()) {
                    if (ResearchProjectDialoguePlugin.getQuantity(playerCargo, item) > 0) {
                        i++;
                        continue OUTER;
                    }
                }
            }
            ;
        }

        return i;
    }
}
