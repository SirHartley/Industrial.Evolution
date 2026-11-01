package indevo.industries.petshop.item;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.industries.petshop.dialogue.PetPickerInteractionDialoguePlugin;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;
import java.util.Random;

public class PetItemPlugin extends BaseSpecialItemPlugin {

    public static final String BOX_ACTIVE = "$petBoxActive";
    protected PetData pet;

    @Override
    public void init(CargoStackAPI stack) {
        super.init(stack);
        pet = PetDataRepo.get(stack.getSpecialDataIfSpecial().getData());
    }

    @Override
    public void render(float x, float y, float w, float h, float alphaMult,
                       float glowMult, SpecialItemRendererAPI renderer) {

        if (pet == null) return;

        SpriteAPI sprite = Global.getSettings().getSprite(pet.icon);
        float dim = 30f;

        Color bgColor = Global.getSector().getPlayerFaction().getDarkUIColor();
        bgColor = Misc.setAlpha(bgColor, 255);
        float pad = 10f;

        y = y + dim + pad;
        x = x + w - dim - pad;

        float blX = x;
        float blY = y - dim;
        float tlX = x;
        float tlY = y;
        float trX = x + dim;
        float trY = y;
        float brX = x + dim;
        float brY = y - dim;

        renderer.renderBGWithCorners(bgColor, blX - 1, blY - 1, tlX - 1, tlY + 1, trX + 1, trY + 1, brX + 1, brY - 1, 1f, 0f, false);
        sprite.renderWithCorners(blX, blY, tlX, tlY, trX, trY, brX, brY);

    }

    @Override
    public String resolveDropParamsToSpecificItemData(String params, Random random) throws JSONException {
        //item_:{tags:[modspec], p:{tier:3, tags:[engines]}	unresolved

        float minRarity = 0;
        float maxRarity = 99;

        WeightedRandomPicker<PetData> picker = new WeightedRandomPicker<>(random);

        JSONObject json = new JSONObject(params);

        String tier = json.optString("tier");
        switch (tier) {
            case "rare":
                maxRarity = 0.4f;
                break;
            case "uncommon":
                minRarity = 0.4f;
                maxRarity = 0.8f;
                break;
            case "common":
                minRarity = 0.8f;
                break;
        }

        for (PetData data : PetDataRepo.getAll()) {
            if (data.isNoDrop()) continue;

            if (isBetween(data.rarity, minRarity, maxRarity)) picker.add(data, data.rarity);
        }

        return picker.pick().id;
    }

    private boolean isBetween(float check, float lower, float higher) {
        return (check >= lower && check <= higher);
    }

    @Override
    public int getPrice(MarketAPI market, SubmarketAPI submarket) {
        if (pet != null) {
            return (int) (pet.value);
        }

        return super.getPrice(market, submarket);
    }

    @Override
    public String getName() {
        if (pet != null) {
            //return ship.getHullName() + " Blueprint";
            return pet.species + " Cryochamber";
        }
        return super.getName();
    }

    @Override
    public String getDesignType() {
        return "Cryochamber";
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource);

        float pad = 3f;
        float opad = 10f;
        float small = 5f;
        Color h = Misc.getHighlightColor();
        Color n = Misc.getNegativeHighlightColor();
        Color b = Misc.getButtonTextColor();
        b = Misc.getPositiveHighlightColor();

        if (isInvalid()) {
            tooltip.addPara("Invalid item: there is no pet with the id %s. Please report this.", opad, n, String.valueOf(stack.getSpecialDataIfSpecial().getData()));
            return;
        }

        PetData pet = this.pet != null ? this.pet : PetDataRepo.get("hampter");

        Pair<String, Color> rpair = pet.getRarityDesc();

        tooltip.addPara("Contains a %s", opad, h, pet.species);
        tooltip.addPara("Rarity: %s", pad, rpair.two, rpair.one);
        tooltip.addPara(pet.desc, opad);
        tooltip.addPara(pet.species + "s tend to live for about " + getYearDescription(Math.round(pet.maxLife / 31f)), opad);

        addCostLabel(tooltip, opad, transferHandler, stackSource);

        boolean isActive = Global.getSector().getMemoryWithoutUpdate().contains(BOX_ACTIVE);

        if (isFleetCargo() && !isActive) tooltip.addPara("Right-click to assign", b, opad);
        else if (!isFleetCargo()) tooltip.addPara("Can only be assigned from fleet cargo", n, opad);
        else if (isActive) tooltip.addPara("Finish assigning your current pet to activate this!", n, opad);
        else tooltip.addPara("Can not be used in this situation", n, opad);
    }

    public static String getYearDescription(int months) {
        if (months < 12) {
            return "less than one year";
        } else if (months == 12) {
            return "one year";
        } else {
            int years = months / 12;
            return years + (years == 1 ? " year" : " years");
        }
    }

    @Override
    public boolean hasRightClickAction() {
        return isFleetCargo() && !Global.getSector().getMemoryWithoutUpdate().contains(BOX_ACTIVE);
    }

    public boolean isInvalid() {
        // codex stacks have no cargo and null data
        return pet == null && stack.getCargo() != null;
    }

    public boolean isFleetCargo() {
        boolean isOpen = CoreUITabId.CARGO.equals(Global.getSector().getCampaignUI().getCurrentCoreTab());
        boolean noInteraction = Global.getSector().getCampaignUI().getCurrentInteractionDialog() == null;

        return isOpen && noInteraction;
    }

    @Override
    public boolean shouldRemoveOnRightClickAction() {
        return false;
    }

    @Override
    public void performRightClickAction(RightClickActionHelper helper) {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        if (mem.contains(BOX_ACTIVE)) return;
        if (pet == null) throw new IllegalStateException("IndEvo pet box without valid pet data, id " + stack.getSpecialDataIfSpecial().getData());

        mem.set(BOX_ACTIVE, null, 0f);
        Global.getSector().getCampaignUI().showInteractionDialogFromCargo(new PetPickerInteractionDialoguePlugin(pet, helper), null, () -> mem.unset(BOX_ACTIVE));
    }
}
