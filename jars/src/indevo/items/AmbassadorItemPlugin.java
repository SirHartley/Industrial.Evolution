package indevo.items;

import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.items.specialitemdata.AmbassadorItemData;

public class AmbassadorItemPlugin extends BaseSpecialItemPlugin {

    PersonAPI person;

    @Override
    public void init(CargoStackAPI stack) {
        super.init(stack);

        if (stack.getSpecialDataIfSpecial() instanceof AmbassadorItemData) {
            person = ((AmbassadorItemData) stack.getSpecialDataIfSpecial()).getPerson();
        } else person = null;
    }

    @Override
    public void render(float x, float y, float w, float h, float alphaMult,
                       float glowMult, SpecialItemRendererAPI renderer) {
    }

    @Override
    public int getPrice(MarketAPI market, SubmarketAPI submarket) {
        return super.getPrice(market, submarket);
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource) {
        float pad = 3f;
        float spad = 5f;
        float opad = 10f;

        if (person == null) return;

        tooltip.addTitle(getName());

        tooltip.addPara("Faction: %s", pad, Misc.getGrayColor(), person.getFaction().getColor(), person.getFaction().getDisplayName());

        TooltipMakerAPI text = tooltip.beginImageWithText(person.getPortraitSprite(), 48);

        text.addPara(person.getNameString() + " is an official faction representative.", pad);
        text.addPara("Treat them well, or face the consequences.", pad);
        tooltip.addImageWithText(opad);


        TooltipMakerAPI crest = tooltip.beginImageWithText(person.getFaction().getCrest(), 48);
        crest.addPara("Take them to their post as soon as possible to avoid a diplomatic incident with " + person.getFaction().getDisplayNameWithArticle(), opad);
        tooltip.addImageWithText(spad);

        tooltip.addPara("Ambassadors should be transported to their destination within two to three months, or you will suffer relationship penalties.", Misc.getGrayColor(), opad);
    }

    @Override
    public String getName() {
        if (person != null) {
            return person.getNameString();
        }
        return super.getName();
    }


}






