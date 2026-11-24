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
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import indevo.config.Setting;
import indevo.dialogue.sidepanel.EscapeBlockingDialoguePluginAPI;
import indevo.ids.Ids;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.EscapeInterceptListener;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.script.EscBlockerMemoryIterator;
import indevo.industries.ratsettlement.RatSettlements;
import indevo.utils.helper.MiscIE;

import java.util.Map;
import java.util.function.Consumer;

// hosts the Pets panel; shop is the docked Animal Center, null in flight
public class PetsDialogPlugin implements InteractionDialogPlugin, EscapeBlockingDialoguePluginAPI {

    private static final String LINK = "IndEvo_petLink";

    private final PetsPanel panel;
    // the dialog under this one, it takes the panel's reports when it closes
    private final InteractionDialogAPI under;
    private final Consumer<InteractionDialogAPI> onReturn;
    private InteractionDialogAPI dialog;
    private boolean returnPending = false;

    private record Link(String pet, int tab, SectorEntityToken course) {
    }

    public PetsDialogPlugin(PetShop shop, PetsView view) {
        this(shop, view, null);
    }

    // return hands the dialog to onReturn instead of closing it, for hosts that are not rules (rat settlements)
    public PetsDialogPlugin(PetShop shop, PetsView view, Consumer<InteractionDialogAPI> onReturn) {
        this.panel = new PetsPanel(shop, view, this::leave);
        this.under = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        this.onReturn = onReturn;
    }

    public static boolean show(PetShop shop, PetsView view) {
        SectorEntityToken target = shop != null ? shop.getMarket().getPrimaryEntity() : Global.getSector().getPlayerFleet();
        return Global.getSector().getCampaignUI().showInteractionDialog(new PetsDialogPlugin(shop, view), target);
    }

    // a pet message's click target: a token that lives only in the message list, PetCampaignPlugin hands it to follow
    public static SectorEntityToken link(Pet pet) {
        return token(new Link(pet.id, PetsView.TAB_PETS, null));
    }

    public static SectorEntityToken link(int tab) {
        return token(new Link(null, tab, null));
    }

    public static SectorEntityToken course(SectorEntityToken to) {
        return token(new Link(null, PetsView.TAB_PETS, to));
    }

    private static SectorEntityToken token(Link link) {
        SectorEntityToken token = Global.getSector().getHyperspace().createToken(0f, 0f);
        token.setFaction(Factions.PLAYER);
        token.addTag(LINK);
        token.getCustomData().put(LINK, link);
        return token;
    }

    // a link to a place lays in the course and opens nothing
    public static InteractionDialogPlugin follow(SectorEntityToken token) {
        if (token == null || !token.hasTag(LINK) || !Setting.PETS.enabled()) return null;
        if (!(token.getCustomData().get(LINK) instanceof Link link)) return null;

        if (link.course() != null) {
            if (!link.course().isExpired()) Global.getSector().layInCourseFor(link.course());
            return null;
        }

        Pet pet = link.pet() != null ? PetStatusManager.getInstance().get(link.pet()) : null;
        return new PetsDialogPlugin(null, pet != null ? PetsView.of(pet) : PetsView.at(link.tab()));
    }

    // from a vanilla screen with no dialog of its own (fleet, refit), with the docked Animal Center; onClose refreshes that screen
    public static void over(Pet pet, Runnable onClose) {
        PetsDialogPlugin plugin = new PetsDialogPlugin(dockedShop(), new PetsView(pet.id));
        Global.getSector().getCampaignUI().showInteractionDialogFromCargo(plugin, null, onClose == null ? null : onClose::run);
    }

    private static PetShop dockedShop() {
        MarketAPI market = MiscIE.getCurrentInteractionTargetMarket();
        if (RatSettlements.isSettlementMarket(market)) return RatSettlements.getHabitat();

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

        PetStatusManager.getInstance().holdMessages(under);
        PetStatusManager.getInstance().checkUnlocks();
        dialog.setPromptText("");
        dialog.getOptionPanel().clearOptions();
        dialog.hideVisualPanel();
        dialog.hideTextPanel();
        dialog.showCustomDialog(PetsPanel.getWidth(), PetsPanel.getHeight(), panel);
    }

    @Override
    public void close() {
        if (panel.isOpen()) {
            if (!panel.skipTour()) panel.cancel();
        } else leave();
    }

    public void leave() {
        if (onReturn != null) {
            returnPending = true;
            return;
        }

        dialog.dismiss();
        PetStatusManager.getInstance().releaseMessages();
    }

    @Override
    public void advance(float amount) {
        // a popup over the panel takes escape itself
        if (Global.getSector().getCampaignUI().isShowingDialog() && !panel.isPopupOpen())
            EscapeInterceptListener.block(this);

        // a frame after the custom dialog is gone, the host must not redraw from inside its dismiss
        if (returnPending) {
            returnPending = false;
            onReturn.accept(dialog);
            PetStatusManager.getInstance().releaseMessages();
        }
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
