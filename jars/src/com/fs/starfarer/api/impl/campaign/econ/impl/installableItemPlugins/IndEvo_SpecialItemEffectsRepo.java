package com.fs.starfarer.api.impl.campaign.econ.impl.installableItemPlugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_StringHelper;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.*;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_Items;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.HashMap;
import java.util.Map;

import static com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo.COLD_OR_EXTREME_COLD;

public class IndEvo_SpecialItemEffectsRepo {

    public static void addItemEffectsToVanillaRepo() {
        ItemEffectsRepo.ITEM_EFFECTS.putAll(ITEM_EFFECTS);
    }

    public static void initEffectListeners() {
        ListenerManagerAPI l = Global.getSector().getListenerManager();

        if (!l.hasListenerOfClass(IndEvo_SupCom.SuperComputerFactor.class)) {
            l.addListener(new IndEvo_SupCom.SuperComputerFactor(), true);
        }

        if (!l.hasListenerOfClass(IndEvo_senate.SenateFactor.class)) {
            l.addListener(new IndEvo_senate.SenateFactor(), true);
        }

        if (!l.hasListenerOfClass(IndEvo_ComArray.RelayFactor.class)) {
            l.addListener(new IndEvo_ComArray.RelayFactor(), true);
        }

        if (!l.hasListenerOfClass(IndEvo_AdInfra.BureauFactor.class)) {
            l.addListener(new IndEvo_AdInfra.BureauFactor(), true);
        }
    }

    public static float SIMULATOR_BASE_INCREASE = 0.1f;
    public static int RANGE_LY_TEN = 12;
    public static float NEURAL_COMPOUNDS_UNREST_RED = 0.5f;
    public static String TRANSMITTER_UNLOCK_KEY = "$IndEvo_RelayUnlocked";
    public static int LOG_CORE_MAX_BONUS = 4;
    public static int LOG_CORE_COMMODITY_BONUS = 1;

    public static Map<String, InstallableItemEffect> ITEM_EFFECTS = new HashMap<String, InstallableItemEffect>() {{

        put(IndEvo_Items.LOG_CORE, new BaseInstallableItemEffect(IndEvo_Items.LOG_CORE) {
            //Removes the Requirement for AI cores, counts all in 10 ly radius

            public void apply(Industry industry) {
            }

            public void unapply(Industry industry) {
            }

            protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
                                                  InstallableIndustryItemPlugin.InstallableItemDescriptionMode mode, String pre, float pad) {

                String s = IndEvo_StringHelper.getAbsPercentString(NEURAL_COMPOUNDS_UNREST_RED, false) + "%";

                text.addPara(pre + "All industries within " + RANGE_LY_TEN + "LY wil be counted for Bureau bonuses," +
                                " and the maximum bonus effect is increased to " + LOG_CORE_MAX_BONUS,
                        pad, Misc.getHighlightColor(),
                        new String[]{RANGE_LY_TEN + "LY", LOG_CORE_MAX_BONUS + ""});
            }
        });

        /* experimental log core effect
        put(IndEvo_Items.LOG_CORE, new BaseInstallableItemEffect(IndEvo_Items.LOG_CORE) {
            //Removes the Requirement for AI cores, counts all in 10 ly radius

            public void apply(Industry industry) {
                if (industry != null && !industry.getId().equals(IndEvo_ids.ADINFRA)){
                    IndEvo_LogCoreCond cond = IndEvo_LogCoreCond.getLogCoreCond(industry.getMarket());

                    if(cond.hasTakenOver) return;

                    industry.getSupplyBonus().modifyFlat(IndEvo_Items.LOG_CORE, cond.monthsWithActiveCore, Global.getSettings().getSpecialItemSpec(IndEvo_Items.LOG_CORE).getName());
                }
            }

            public void unapply(Industry industry) {
                industry.getSupplyBonus().unmodify(IndEvo_Items.LOG_CORE);
            }

            protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
                                                  InstallableIndustryItemPlugin.InstallableItemDescriptionMode mode, String pre, float pad) {
                if(industry == null){
                    addParaCent(text, "Centralization Bureau: " , pad);
                    addParaOther(text, "Any other: ", pad);
                } else if(industry.getId().equals(IndEvo_ids.ADINFRA)){
                    addParaCent(text, pre, pad);
                } else addParaOther(text, pre, pad);

            }

            protected void addParaCent(TooltipMakerAPI text, String pre, float pad){
                text.addPara(pre + "All industries within " + RANGE_LY_TEN + "LY wil be counted for Bureau bonuses," +
                                " and the maximum bonus effect is increased to " + LOG_CORE_MAX_BONUS,
                        pad, Misc.getHighlightColor(),
                        new String[]{RANGE_LY_TEN + "LY", LOG_CORE_MAX_BONUS + ""});
            }

            protected void addParaOther(TooltipMakerAPI text, String pre, float pad){
                text.addPara(pre + "Increases all commodity output by " + LOG_CORE_COMMODITY_BONUS + " unit. Bonus increases by 1 each month.",
                        pad, Misc.getHighlightColor(),
                        new String[]{LOG_CORE_COMMODITY_BONUS + "unit", "increases by 1 each month"});
            }
        });*/

        put(IndEvo_Items.SIMULATOR, new BaseInstallableItemEffect(IndEvo_Items.SIMULATOR) {
            public void apply(Industry industry) {
            }

            public void unapply(Industry industry) {
            }

            @Override
            public String[] getSimpleReqs(Industry industry) {
                return new String[]{COLD_OR_EXTREME_COLD};
            }

            protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
                                                  InstallableIndustryItemPlugin.InstallableItemDescriptionMode mode, String pre, float pad) {

                String s = IndEvo_StringHelper.getAbsPercentString(SIMULATOR_BASE_INCREASE, false) + "%";

                text.addPara(pre + "Applies the Supercomputer Effect to any friendly colony within " + RANGE_LY_TEN + "LY" +
                                " and increases the effect by " + s,
                        pad, Misc.getHighlightColor(),
                        new String[]{RANGE_LY_TEN + "LY", s});
            }
        });

        put(IndEvo_Items.TRANSMITTER, new BaseInstallableItemEffect(IndEvo_Items.TRANSMITTER) {
            public void apply(Industry industry) {
                if (!(industry instanceof IndEvo_ComArray))
                    Global.getSector().getMemoryWithoutUpdate().set(TRANSMITTER_UNLOCK_KEY, true);
            }

            public void unapply(Industry industry) {
                if (!(industry instanceof IndEvo_ComArray))
                    Global.getSector().getMemoryWithoutUpdate().unset(TRANSMITTER_UNLOCK_KEY);
            }

            protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
                                                  InstallableIndustryItemPlugin.InstallableItemDescriptionMode mode, String pre, float pad) {
                if (industry == null) {
                    text.addPara(pre + "In a Military Base / High Command: removes the Military Base construction requirement for Relays in the entire sector.\n" +
                                    "In an Interstellar Relay: Improves the Comm Relay stability bonus to +2",
                            pad, Misc.getHighlightColor(),
                            new String[]{"without a military base", "stability bonus to +2"});
                }
                if (industry instanceof IndEvo_ComArray) {
                    text.addPara(pre + "Improves the Comm Relay into the Domain-Era equivalent",
                            pad, Misc.getHighlightColor(),
                            new String[]{"Improves the Comm Relay"});

                } else if (industry instanceof MilitaryBase) {
                    text.addPara(pre + "Removes the Military Base construction requirement for Relays in the entire sector.",
                            pad, Misc.getHighlightColor(),
                            new String[]{"without a military base"});

                }
            }
        });

        put(IndEvo_Items.NEUAL_COMPOUNDS, new BaseInstallableItemEffect(IndEvo_Items.NEUAL_COMPOUNDS) {

            public void apply(Industry industry) {
            }

            public void unapply(Industry industry) {
            }

            protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
                                                  InstallableIndustryItemPlugin.InstallableItemDescriptionMode mode, String pre, float pad) {

                String s = IndEvo_StringHelper.getAbsPercentString(NEURAL_COMPOUNDS_UNREST_RED, false) + "%";

                text.addPara(pre + "Allows you to issue Edicts on any friendly colony within " + RANGE_LY_TEN + "LY" +
                                " and reduces the unrest after preliminary removal by " + s,
                        pad, Misc.getHighlightColor(),
                        new String[]{RANGE_LY_TEN + "LY", s});
            }
        });

        //todo past here is not done
        /*
         put(IndEvo_Items.INTERFACES, new BaseInstallableItemEffect(IndEvo_Items.INTERFACES) {
            //Allows officer cloning ("Mind copy/Transfer to another officer)

            public void apply(Industry industry) {
            }

            public void unapply(Industry industry) {
            }

            protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
                                                  InstallableIndustryItemPlugin.InstallableItemDescriptionMode mode, String pre, float pad) {

                text.addPara(pre + "you should not have this. don't come crying when it crashes.",
                        pad);
            }
        });

        put(IndEvo_Items.NANITES, new BaseInstallableItemEffect(IndEvo_Items.NANITES) {
            //unlocks option to select specific D-Mod to repair, reduces cost?

            public void apply(Industry industry) {
            }

            public void unapply(Industry industry) {
            }

            protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
                                                  InstallableIndustryItemPlugin.InstallableItemDescriptionMode mode, String pre, float pad) {

                text.addPara(pre + "you should not have this. don't come crying when it crashes.",
                        pad);
            }
        });

        put(IndEvo_Items.SALVAGE_DRONES, new BaseInstallableItemEffect(IndEvo_Items.SALVAGE_DRONES) {
            //counts battles in neighbouring systems, SP option always active

            public void apply(Industry industry) {
            }

            public void unapply(Industry industry) {
            }

            protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
                                                  InstallableIndustryItemPlugin.InstallableItemDescriptionMode mode, String pre, float pad) {

                text.addPara(pre + "you should not have this. don't come crying when it crashes.",
                        pad);
            }
        });


          put(IndEvo_Items.ANALYSER, new BaseInstallableItemEffect(IndEvo_Items.ANALYSER) {
            //Halves Research time, increases points per hull by 10%
            //broken because the hub already has an installable item and I forgot (blueprints!)

            public void apply(Industry industry) {
            }

            public void unapply(Industry industry) {
            }

            protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
                                                  InstallableIndustryItemPlugin.InstallableItemDescriptionMode mode, String pre, float pad) {

                String s1 = IndEvo_StringHelper.getAbsPercentString(ANALYSER_RESEARCH_TIME_RED_MULT, false) + "%";
                String s2 = IndEvo_StringHelper.getAbsPercentString(ANALYSER_POINT_INCREASE_MULT, false) + "%";

                text.addPara(pre + "Increases the Research Progress gained by deconstructing a hull by " + s2 +
                                " and reduces research time by " + s1,
                        pad, Misc.getHighlightColor(),
                        new String[]{s2, s1});
            }
        });*/
    }};
}
