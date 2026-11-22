package indevo.industries.petshop.item;

import com.fs.starfarer.api.GameState;
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
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.ItemIds;
import indevo.industries.academy.industry.Academy;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.plugins.PetCard;
import indevo.industries.petshop.plugins.PetPlaceDialog;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;
import java.util.Random;

public class PetItemPlugin extends BaseSpecialItemPlugin {

    // item data: the species id for a new pet, <species id>:<pet id> for a registered young in a Cryochamber
    public static final String SEPARATOR = ":";

    protected PetData pet;
    protected String youngId;

    @Override
    public void init(CargoStackAPI stack) {
        super.init(stack);
        String data = stack.getSpecialDataIfSpecial().getData();
        int split = data != null ? data.indexOf(SEPARATOR) : -1;
        pet = PetDataRepo.get(split >= 0 ? data.substring(0, split) : data);
        youngId = split >= 0 ? data.substring(split + 1) : null;
    }

    public static String chamberData(Pet pet) {
        return pet.typeID + SEPARATOR + pet.id;
    }

    // the registered pet in a Cryochamber stack, null for any other stack
    public static Pet chamberPet(CargoStackAPI stack) {
        if (!stack.isSpecialStack() || !ItemIds.PET_CHAMBER.equals(stack.getSpecialDataIfSpecial().getId())) return null;
        String data = stack.getSpecialDataIfSpecial().getData();
        int split = data != null ? data.indexOf(SEPARATOR) : -1;
        return split >= 0 ? PetStatusManager.getInstance().get(data.substring(split + 1)) : null;
    }

    // null in the codex and for a new pet
    protected Pet young() {
        return youngId != null && Global.getSector() != null && Global.getSector().getPlayerFleet() != null ? PetStatusManager.getInstance().get(youngId) : null;
    }

    @Override
    public void render(float x, float y, float w, float h, float alphaMult,
                       float glowMult, SpecialItemRendererAPI renderer) {

        if (pet == null) return;

        Pet young = young();
        SpriteAPI sprite = Global.getSettings().getSprite(young != null ? young.getIcon() : pet.icon);
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
        Pet young = young();
        if (young != null) return Math.round(PetStatusManager.getInstance().getSaleValue(young));
        if (pet != null) {
            return (int) (pet.value);
        }

        return super.getPrice(market, submarket);
    }

    @Override
    public String getName() {
        Pet young = young();
        if (young != null) return pet.species + " Cryochamber: " + young.name;
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

        Pet young = young();
        if (young != null) {
            String disposition = Misc.ucFirst(young.sheet.disposition);
            tooltip.addPara("Contains %s, a young %s, %s.", opad, new Color[]{h, h, Academy.COLOURS_BY_PERSONALITY.get(young.sheet.disposition)},
                    young.name, PetCard.species(young), disposition);
            tooltip.addPara("It does not age in the Cryochamber.", opad);
            addCostLabel(tooltip, opad, transferHandler, stackSource);
            if (isFleetCargo()) tooltip.addPara("Right-click to open", b, opad);
            else tooltip.addPara("Can only be opened from fleet cargo", n, opad);
            return;
        }

        // the codex shows item tooltips outside a campaign too
        if (this.pet != null && Global.getCurrentState() == GameState.CAMPAIGN) PetStatusManager.getInstance().seeSpecies(this.pet.id);

        Pair<String, Color> rpair = pet.getRarityDesc();

        tooltip.addPara("Contains a %s", opad, h, pet.species);
        tooltip.addPara("Rarity: %s", pad, rpair.two, rpair.one);
        tooltip.addPara(pet.desc, opad);
        tooltip.addPara(pet.species + "s tend to live for about " + getYearDescription(Math.round(pet.maxLife / 31f)), opad);

        addCostLabel(tooltip, opad, transferHandler, stackSource);

        if (isFleetCargo()) tooltip.addPara("Right-click to assign", b, opad);
        else tooltip.addPara("Can only be assigned from fleet cargo", n, opad);
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
        return isFleetCargo() && !isInvalid();
    }

    public boolean isInvalid() {
        // codex stacks have no cargo and null data
        return pet == null && stack.getCargo() != null;
    }

    public static boolean isFleetCargo() {
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
        if (pet == null) throw new IllegalStateException("IndEvo pet box without valid pet data, id " + stack.getSpecialDataIfSpecial().getData());
        if (youngId == null) {
            PetPlaceDialog.chamber(pet, helper).show(() -> {});
            return;
        }

        Pet young = young();
        if (young == null || !young.inChamber) throw new IllegalStateException("IndEvo Cryochamber holds no young in a chamber, id " + youngId);
        PetPlaceDialog.chamber(young, helper).show(() -> {});
    }
}
