package indevo.industries.petshop.item;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.industries.petshop.dialogue.PetPickerInteractionDialoguePlugin;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.LazyLib;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.Random;

public class PetItemPlugin extends BaseSpecialItemPlugin {

    protected PetData pet;

    @Override
    public void init(CargoStackAPI stack) {
        super.init(stack);
        pet = PetDataRepo.get(stack.getSpecialDataIfSpecial().getData());
    }

    @Override
    public void render(float x, float y, float w, float h, float alphaMult,
                       float glowMult, SpecialItemRendererAPI renderer) {

        String petId = stack.getSpecialDataIfSpecial().getData();

        if (petId == null) return;

        String imageName = PetDataRepo.get(petId).icon;
        SpriteAPI sprite = Global.getSettings().getSprite(imageName);
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

        renderer.renderBGWithCorners(bgColor, blX - 1, blY - 1, tlX - 1, tlY + 1, trX + 1, trY + 1, brX + 1, brY - 1,1f,0f,false);
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
        switch (tier){
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

        for (PetData data : PetDataRepo.getAll()){
            if (data.tags.contains("no_drop")) continue;

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

        PetData pet = PetDataRepo.get(stack.getSpecialDataIfSpecial().getData());

        tooltip.addPara("Contains a newborn %s", opad, h, pet.species);
        tooltip.addPara(pet.desc, opad);

        addCostLabel(tooltip, opad, transferHandler, stackSource);

        if (isFleetCargo()) tooltip.addPara("Right-click to assign", b, opad);
        else tooltip.addPara("Can only be assigned from fleet cargo", n, opad);
    }

    @Override
    public boolean hasRightClickAction() {
        return isFleetCargo();
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
    public void performRightClickAction() {

        try {
            Robot r = new Robot();

            r.keyPress(27);
            r.keyRelease(27);

        } catch (AWTException e) {
            e.printStackTrace();
        }

        Global.getSector().addScript(new EveryFrameScript() {
            boolean done = false;

            @Override
            public boolean isDone() {
                return done;
            }

            @Override
            public boolean runWhilePaused() {
                return false;
            }

            @Override
            public void advance(float amount) {
                if (!done)
                    Global.getSector().getCampaignUI().showInteractionDialog(new PetPickerInteractionDialoguePlugin(pet), null);
                done = true;
            }
        });
    }
}
