package indevo.industries.petshop.item;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.petshop.dialogue.PetPickerInteractionDialoguePlugin;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;

import java.awt.*;

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

        if (true) return;

        float cx = x + w / 2f;
        float cy = y + h / 2f;

        float blX = cx - 30f;
        float blY = cy - 15f;
        float tlX = cx - 20f;
        float tlY = cy + 26f;
        float trX = cx + 23f;
        float trY = cy + 26f;
        float brX = cx + 15f;
        float brY = cy - 18f;

        String hullId = stack.getSpecialDataIfSpecial().getData();

        boolean known = Global.getSector().getPlayerFaction().knowsShip(hullId);

        float mult = 1f;
        //if (known) mult = 0.5f;

        Color bgColor = Global.getSector().getPlayerFaction().getDarkUIColor();
        bgColor = Misc.setAlpha(bgColor, 255);

        //float b = Global.getSector().getCampaignUI().getSharedFader().getBrightness() * 0.25f;
        renderer.renderBGWithCorners(bgColor, blX, blY, tlX, tlY, trX, trY, brX, brY,
                alphaMult * mult, glowMult * 0.5f * mult, false);
        renderer.renderShipWithCorners(hullId, null, blX, blY, tlX, tlY, trX, trY, brX, brY,
                alphaMult * mult, glowMult * 0.5f * mult, !known);


        SpriteAPI overlay = Global.getSettings().getSprite("ui", "bpOverlayShip");
        overlay.setColor(Color.green);
        overlay.setColor(Global.getSector().getPlayerFaction().getBrightUIColor());
        overlay.setAlphaMult(alphaMult);
        overlay.setNormalBlend();
        renderer.renderScanlinesWithCorners(blX, blY, tlX, tlY, trX, trY, brX, brY, alphaMult, false);


        if (known) {
            renderer.renderBGWithCorners(Color.black, blX, blY, tlX, tlY, trX, trY, brX, brY,
                    alphaMult * 0.5f, 0f, false);
        }


        overlay.renderWithCorners(blX, blY, tlX, tlY, trX, trY, brX, brY);
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
            return pet.species + " cryochamber";
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

    public boolean isFleetCargo(){
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
                if (!done) Global.getSector().getCampaignUI().showInteractionDialog(new PetPickerInteractionDialoguePlugin(pet), null);
                done = true;
            }
        });
    }
}
