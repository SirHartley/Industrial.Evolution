package com.fs.starfarer.api.splinterFleet.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.ai.AbilityAIPlugin;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.splinterFleet.plugins.abilityAIs.DetachmentEburnAbilityPlugin;
import com.fs.starfarer.api.splinterFleet.plugins.abilityAIs.DetachmentGoDarkAbilityPlugin;
import com.fs.starfarer.api.splinterFleet.plugins.abilityAIs.DetachmentSustainedBurnAbilityAI;
import com.fs.starfarer.api.splinterFleet.plugins.abilityAIs.DetachmentTransponderAbilityAI;
import com.fs.starfarer.api.splinterFleet.plugins.fleetManagement.DetachmentMemory;
import com.fs.starfarer.api.splinterFleet.plugins.fleetManagement.LoadoutMemory;

public class SplinterFleetCampignPlugin extends BaseCampaignPlugin {

    @Override
    public String getId() {
        return "SplinterFleetCampaignPlugin";
    }

    @Override
    public boolean isTransient() {
        return true;
    }

    @Override
    public PluginPick<AbilityAIPlugin> pickAbilityAI(AbilityPlugin ability, ModularFleetAIAPI ai) {
        if (ability == null) return null;
        String id = ability.getId();
        if (id == null) return null;
        CampaignFleetAPI fleet = ai.getFleet();
        if (fleet == null) return null;

        if (id.equals(Abilities.SUSTAINED_BURN) && FleetUtils.isDetachmentFleet(fleet)) {
            DetachmentSustainedBurnAbilityAI abilityAI = new DetachmentSustainedBurnAbilityAI();
            abilityAI.init(ability);
            return new PluginPick<AbilityAIPlugin>(abilityAI, PickPriority.MOD_SET);

        } else if (id.equals(Abilities.EMERGENCY_BURN) && FleetUtils.isDetachmentFleet(fleet)) {
            DetachmentEburnAbilityPlugin abilityAI = new DetachmentEburnAbilityPlugin();
            abilityAI.init(ability);
            return new PluginPick<AbilityAIPlugin>(abilityAI, PickPriority.MOD_SET);

        } else if (id.equals(Abilities.GO_DARK) && FleetUtils.isDetachmentFleet(fleet)) {
            DetachmentGoDarkAbilityPlugin abilityAI = new DetachmentGoDarkAbilityPlugin();
            abilityAI.init(ability);
            return new PluginPick<AbilityAIPlugin>(abilityAI, PickPriority.MOD_SET);

        } else if (id.equals(Abilities.TRANSPONDER) && FleetUtils.isDetachmentFleet(fleet)) {
            DetachmentTransponderAbilityAI abilityAI = new DetachmentTransponderAbilityAI();
            abilityAI.init(ability);
            return new PluginPick<AbilityAIPlugin>(abilityAI, PickPriority.MOD_SET);
        }

        return null;
    }

    public static void clear() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.unset(DetachmentMemory.DETACHMENT_STORE_MEMORY_KEY);
        mem.unset(LoadoutMemory.LOADOUT_STORE_MEMORY_KEY);
    }

}
