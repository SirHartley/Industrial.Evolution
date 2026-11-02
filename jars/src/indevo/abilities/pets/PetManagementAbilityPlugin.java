package indevo.abilities.pets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CharacterDataAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.config.Setting;
import indevo.dialogue.research.ResearchProjectTemplateRepo;
import indevo.ids.Ids;
import indevo.industries.petshop.dialogue.PetShopDialogPlugin;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;

import java.io.IOException;

public class PetManagementAbilityPlugin extends BaseDurationAbility {

    public static void applySetting() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        CharacterDataAPI character = Global.getSector().getCharacterData();

        if (!Setting.PETS.enabled()) {
            player.removeAbility(Ids.ABILITY_PET_MANAGEMENT);
            character.removeAbility(Ids.ABILITY_PET_MANAGEMENT);
            return;
        }

        if (!player.hasAbility(Ids.ABILITY_PET_MANAGEMENT)) {
            player.addAbility(Ids.ABILITY_PET_MANAGEMENT);
            character.addAbility(Ids.ABILITY_PET_MANAGEMENT);
        }

        if (ResearchProjectTemplateRepo.RESEARCH_PROJECTS.get(Ids.PROJ_NAVI).getProgress().redeemed) PetDataRepo.get("fairy").tags.remove(PetData.TAG_NO_SELL);

        for (PetData data : PetDataRepo.getAll()) {
            try {
                Global.getSettings().loadTexture(data.icon);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void activateImpl() {
        Global.getSector().getCampaignUI().showInteractionDialog(new PetShopDialogPlugin(null), Global.getSector().getPlayerFleet());
    }

    @Override
    public boolean isUsable() {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        return !playerFleet.isInHyperspaceTransition()
                && !isOnCooldown()
                && disableFrames <= 0;
    }

    @Override
    public boolean showActiveIndicator() {
        return false;
    }

    @Override
    public boolean hasTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {

        tooltip.addTitle("Pet Control Panel");

        float pad = 10.0F;
        tooltip.addPara("Click to manage the pets living aboard your ships.", pad);
    }

    @Override
    public boolean isTooltipExpandable() {
        return false;
    }

    @Override
    protected void applyEffect(float v, float v1) {
    }

    @Override
    protected void deactivateImpl() {
    }

    @Override
    protected void cleanupImpl() {
    }

}