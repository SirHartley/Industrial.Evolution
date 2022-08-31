package com.fs.starfarer.api.impl.campaign.rulecmd.researchProjects;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Drops;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_Items;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import com.fs.starfarer.api.plugins.IndEvo_modPlugin;

import java.util.*;

public class IndEvo_ResearchProjectTemplateRepo {

    public static Map<String, IndEvo_ResearchProject> RESEARCH_PROJECTS = new HashMap<String, IndEvo_ResearchProject>() {{
        put(IndEvo_ids.PROJ_SNOWBLIND, new IndEvo_ResearchProject(IndEvo_ids.PROJ_SNOWBLIND,
                "Project Snowblind", 4, false) {

            @Override
            public boolean display() {
                return IndEvo_DoritoGunFoundChecker.isGunFound();
            }

            @Override
            public CargoAPI getRewards() {
                CargoAPI c = Global.getFactory().createCargo(true);
                c.addWeapons("IndEvo_cryoartillery", 2);
                c.addSpecial(new SpecialItemData(Items.WEAPON_BP, "IndEvo_cryoartillery"), 1);

                return c;
            }

            @Override
            public List<IndEvo_RequiredItem> getRequiredItems() {
                List<IndEvo_RequiredItem> list = new ArrayList<>();
                list.add(new IndEvo_RequiredItem("cryoflux", CargoAPI.CargoItemType.WEAPONS, 2f));
                list.add(new IndEvo_RequiredItem("cryoblaster", CargoAPI.CargoItemType.WEAPONS, 2f));

                return list;
            }

            @Override
            public String getShortDesc() {
                return "An attempt to reverse engineer the surreal freezing capabilities of the artifact weapons. What could go wrong?";
            }
        });

        put(IndEvo_ids.PROJ_SPITFIRE, new IndEvo_ResearchProject(IndEvo_ids.PROJ_SPITFIRE, "Project Spitfire", 4, false) {

            @Override
            public boolean display() {
                return IndEvo_DoritoGunFoundChecker.isGunFound();
            }

            @Override
            public CargoAPI getRewards() {
                CargoAPI c = Global.getFactory().createCargo(true);
                c.addWeapons("IndEvo_degrader", 3);
                c.addSpecial(new SpecialItemData(Items.WEAPON_BP, "IndEvo_degrader"), 1);

                return c;
            }

            @Override
            public List<IndEvo_RequiredItem> getRequiredItems() {
                List<IndEvo_RequiredItem> list = new ArrayList<>();
                list.add(new IndEvo_RequiredItem("amsrm", CargoAPI.CargoItemType.WEAPONS, 1.5f));
                list.add(new IndEvo_RequiredItem("resonatormrm", CargoAPI.CargoItemType.WEAPONS, 2f));
                list.add(new IndEvo_RequiredItem("disintegrator", CargoAPI.CargoItemType.WEAPONS, 2f));

                return list;
            }

            @Override
            public String getShortDesc() {
                return "Nothing can punch through armor like a Disintegrator, and the Galatian Academy already has customers lining up for the results of this project.";
            }
        });

        put(IndEvo_ids.PROJ_EUREKA, new IndEvo_ResearchProject(IndEvo_ids.PROJ_EUREKA, "Project Eureka", 4, false) {

            @Override
            public boolean display() {
                return IndEvo_DoritoGunFoundChecker.isGunFound();
            }

            @Override
            public CargoAPI getRewards() {
                CargoAPI c = Global.getFactory().createCargo(true);
                c.addWeapons("IndEvo_causalitygun", 1);
                c.addSpecial(new SpecialItemData(Items.WEAPON_BP, "IndEvo_causalitygun"), 1);

                return c;
            }

            @Override
            public List<IndEvo_RequiredItem> getRequiredItems() {
                List<IndEvo_RequiredItem> list = new ArrayList<>();
                list.add(new IndEvo_RequiredItem("disintegrator", CargoAPI.CargoItemType.WEAPONS, 2f));
                list.add(new IndEvo_RequiredItem("rifttorpedo", CargoAPI.CargoItemType.WEAPONS, 4f));
                list.add(new IndEvo_RequiredItem("realitydisruptor", CargoAPI.CargoItemType.WEAPONS, 4f));

                return list;
            }

            @Override
            public String getShortDesc() {
                return "There used to be a saying, that some things should best be left untouched. Used to. Reality shall bend to our will.";
            }
        });

        put(IndEvo_ids.PROJ_PARALLAX, new IndEvo_ResearchProject(IndEvo_ids.PROJ_PARALLAX, "Project Parallax", 4, false) {

            @Override
            public boolean display() {
                return IndEvo_DoritoGunFoundChecker.isGunFound();
            }

            @Override
            public CargoAPI getRewards() {
                CargoAPI c = Global.getFactory().createCargo(true);
                c.addWeapons("IndEvo_pulsedcarbine", 3);
                c.addSpecial(new SpecialItemData(Items.WEAPON_BP, "IndEvo_pulsedcarbine"), 1);

                return c;
            }

            @Override
            public List<IndEvo_RequiredItem> getRequiredItems() {
                List<IndEvo_RequiredItem> list = new ArrayList<>();
                list.add(new IndEvo_RequiredItem("minipulser", CargoAPI.CargoItemType.WEAPONS, 1.5f));
                list.add(new IndEvo_RequiredItem("shockrepeater", CargoAPI.CargoItemType.WEAPONS, 1.5f));
                list.add(new IndEvo_RequiredItem("vpdriver", CargoAPI.CargoItemType.WEAPONS, 4f));

                return list;
            }

            @Override
            public String getShortDesc() {
                return "The possibilities of this new tech are endless - as is the profit, once the Academy manages to get the components to cooperate.";
            }
        });

        put(IndEvo_ids.PROJ_KEYHOLE, new IndEvo_ResearchProject(IndEvo_ids.PROJ_KEYHOLE, "Project Keyhole", 4, false) {

            @Override
            public boolean display() {
                return IndEvo_DoritoGunFoundChecker.isGunFound();
            }

            @Override
            public CargoAPI getRewards() {
                CargoAPI c = Global.getFactory().createCargo(true);
                c.addWeapons("IndEvo_riftgun", 3);
                c.addSpecial(new SpecialItemData(Items.WEAPON_BP, "IndEvo_riftgun"), 1);

                return c;
            }

            @Override
            public List<IndEvo_RequiredItem> getRequiredItems() {
                List<IndEvo_RequiredItem> list = new ArrayList<>();
                list.add(new IndEvo_RequiredItem("riftlance", CargoAPI.CargoItemType.WEAPONS, 1.5f));
                list.add(new IndEvo_RequiredItem("shockrepeater", CargoAPI.CargoItemType.WEAPONS, 1.5f));
                list.add(new IndEvo_RequiredItem("amsrm", CargoAPI.CargoItemType.WEAPONS, 1.5f));
                list.add(new IndEvo_RequiredItem("riftbeam", CargoAPI.CargoItemType.WEAPONS, 2f));
                list.add(new IndEvo_RequiredItem("riftcascade", CargoAPI.CargoItemType.WEAPONS, 4f));

                return list;
            }

            @Override
            public String getShortDesc() {
                return "The most common of the arcane tech that recently surfaced is still leagues ahead of anything contemporary. Once reverse engineered, this will make a fine addition to any collection.";
            }
        });

        put(IndEvo_ids.PROJ_SPYGLASS, new IndEvo_ResearchProject(IndEvo_ids.PROJ_SPYGLASS, "Project Spyglass", 500, true) {

            @Override
            public boolean display() {
                return true;
            }

            @Override
            public CargoAPI getRewards() {
                List<Random> randomList = getRandomForProj(IndEvo_ids.PROJ_SPYGLASS); //Anti-save-scum

                List<SalvageEntityGenDataSpec.DropData> dropRandom = new ArrayList<>();

                dropRandom.add(IndEvo_DropDataCreator.createDropData(Drops.AI_CORES2, 3));
                dropRandom.add(IndEvo_DropDataCreator.createDropData(Drops.AI_CORES3, 2));
                dropRandom.add(IndEvo_DropDataCreator.createDropData("blueprints_low", 4));
                dropRandom.add(IndEvo_DropDataCreator.createDropData("indEvo_tech", 4));
                dropRandom.add(IndEvo_DropDataCreator.createDropData(Drops.ANY_HULLMOD_MEDIUM, 4));
                dropRandom.add(IndEvo_DropDataCreator.createDropData("rare_tech_low", 1));

                //create 5 rolls and return the best

                float max = 0f;
                CargoAPI extra = null;
                for (Random r : randomList){
                    CargoAPI cargo = SalvageEntity.generateSalvage(r, 1f, 1f, 1f, 1f, null, dropRandom);

                    float val = 0f;
                    for (CargoStackAPI stack : cargo.getStacksCopy()){
                        val += stack.getBaseValuePerUnit() * stack.getSize();
                    }

                    IndEvo_modPlugin.log("drop value " + val);

                    if(val > max) {
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
            public List<IndEvo_RequiredItem> getRequiredItems() {
                List<IndEvo_RequiredItem> list = new ArrayList<>();
                list.add(new IndEvo_RequiredItem(IndEvo_Items.RARE_PARTS, CargoAPI.CargoItemType.RESOURCES, 1f));

                return list;
            }

            @Override
            public String getShortDesc() {
                return "You bring us some old junk, and we trade it for stuff we got lying around. Sound fair?";
            }
        });


        put(IndEvo_ids.PROJ_TRANSISTOR, new IndEvo_ResearchProject(IndEvo_ids.PROJ_TRANSISTOR, "Project Transistor", 80, true) {

            @Override
            public boolean display() {
                return true;
            }

            @Override
            public CargoAPI getRewards() {
                List<Random> randomList = getRandomForProj(IndEvo_ids.PROJ_TRANSISTOR); //Anti-save-scum

                List<SalvageEntityGenDataSpec.DropData> dropRandom = new ArrayList<>();
                dropRandom.add(IndEvo_DropDataCreator.createDropData("indEvo_consumables_always", 5));
                Random random = randomList.get(0);

                return SalvageEntity.generateSalvage(random, 1f, 1f, 1f, 1f, null, dropRandom);
            }

            @Override
            public List<IndEvo_RequiredItem> getRequiredItems() {
                List<IndEvo_RequiredItem> list = new ArrayList<>();
                list.add(new IndEvo_RequiredItem(IndEvo_Items.RARE_PARTS, CargoAPI.CargoItemType.RESOURCES, 1f));

                return list;
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
            for(int i = 0; i < 5; i++){
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
        OUTER: for (Map.Entry<String, IndEvo_ResearchProject> proj : RESEARCH_PROJECTS.entrySet()) {
            if (proj.getValue().display() && !proj.getValue().getProgress().redeemed) {
                for (IndEvo_RequiredItem item : proj.getValue().getRequiredItems()){
                    if(playerCargo.getQuantity(item.type, item.id) > 0) {
                        i++;
                        continue OUTER;
                    }
                }
            };
        }

        return i;
    }
}
