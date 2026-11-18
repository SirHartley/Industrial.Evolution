package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import indevo.dialogue.sidepanel.EscapeBlockingDialoguePluginAPI;
import indevo.ids.Ids;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.EscapeInterceptListener;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.script.EscBlockerMemoryIterator;
import indevo.utils.helper.MiscIE;

import java.util.Map;

import static indevo.industries.petshop.listener.EscapeInterceptListener.BLOCK_ESC;

// hosts the Pets panel; shop is the docked Animal Center, null in flight
public class PetsDialogPlugin implements InteractionDialogPlugin, EscapeBlockingDialoguePluginAPI {

    private final PetsPanel panel;
    private InteractionDialogAPI dialog;

    public PetsDialogPlugin(PetShop shop, PetsView view) {
        this.panel = new PetsPanel(shop, view, this::leave);
    }

    public static boolean show(PetShop shop, PetsView view) {
        SectorEntityToken target = shop != null ? shop.getMarket().getPrimaryEntity() : Global.getSector().getPlayerFleet();
        return Global.getSector().getCampaignUI().showInteractionDialog(new PetsDialogPlugin(shop, view), target);
    }

    // from a vanilla screen with no dialog of its own (fleet, refit), with the docked Animal Center; onClose refreshes that screen
    public static void over(Pet pet, Runnable onClose) {
        PetsDialogPlugin plugin = new PetsDialogPlugin(dockedShop(), new PetsView(pet.id));
        Global.getSector().getCampaignUI().showInteractionDialogFromCargo(plugin, null, onClose == null ? null : onClose::run);
    }

    private static PetShop dockedShop() {
        MarketAPI market = MiscIE.getCurrentInteractionTargetMarket();
        Industry industry = market != null ? market.getIndustry(Ids.PET_STORE) : null;
        return industry instanceof PetShop shop && shop.isFunctional() ? shop : null;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        if (!Global.getSector().getListenerManager().hasListenerOfClass(EscapeInterceptListener.class))
            Global.getSector().getListenerManager().addListener(new EscapeInterceptListener(), true);

        boolean hasScript = false;
        for (EveryFrameScript s : Global.getSector().getTransientScripts()) {
            if (s instanceof EscBlockerMemoryIterator) {
                hasScript = true;
                break;
            }
        }

        if (!hasScript) Global.getSector().addTransientScript(new EscBlockerMemoryIterator());

        PetStatusManager.getInstance().holdMessages();
        dialog.setPromptText("");
        dialog.getOptionPanel().clearOptions();
        dialog.hideVisualPanel();
        dialog.hideTextPanel();
        dialog.showCustomDialog(PetsPanel.getWidth(), PetsPanel.getHeight(), panel);
    }

    @Override
    public void close() {
        if (panel.isOpen()) panel.cancel();
        else leave();
    }

    public void leave() {
        dialog.dismiss();
        PetStatusManager.getInstance().releaseMessages();
    }

    @Override
    public void advance(float amount) {
        // a popup over the panel takes escape itself
        if (Global.getSector().getCampaignUI().isShowingDialog() && !panel.isPopupOpen())
            Global.getSector().getMemoryWithoutUpdate().set(BLOCK_ESC, this, 1f);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }
}
