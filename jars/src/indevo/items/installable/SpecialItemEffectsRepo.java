package indevo.items.installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.InstallableItemEffect;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import indevo.ids.ItemIds;
import indevo.industries.CentralizationBureau;
import indevo.industries.Supercomputer;
import indevo.industries.relay.industry.MilitaryRelay;
import indevo.industries.senate.industry.Senate;

import java.util.HashMap;
import java.util.Map;

public class SpecialItemEffectsRepo {

    public static void addItemEffectsToVanillaRepo() {
        ItemEffectsRepo.ITEM_EFFECTS.putAll(ITEM_EFFECTS);
    }

    public static void initEffectListeners() {
        ListenerManagerAPI l = Global.getSector().getListenerManager();

        if (!l.hasListenerOfClass(Supercomputer.SuperComputerFactor.class)) {
            l.addListener(new Supercomputer.SuperComputerFactor(), true);
        }

        if (!l.hasListenerOfClass(Senate.SenateFactor.class)) {
            l.addListener(new Senate.SenateFactor(), true);
        }

        if (!l.hasListenerOfClass(MilitaryRelay.RelayFactor.class)) {
            l.addListener(new MilitaryRelay.RelayFactor(), true);
        }

        if (!l.hasListenerOfClass(CentralizationBureau.BureauFactor.class)) {
            l.addListener(new CentralizationBureau.BureauFactor(), true);
        }
    }

    public static float SIMULATOR_BASE_INCREASE = 0.1f;
    public static int RANGE_LY_TWELVE = 12;
    public static float NEURAL_COMPOUNDS_UNREST_RED = 0.5f;
    public static String TRANSMITTER_UNLOCK_KEY = "$IndEvo_RelayUnlocked";
    public static int LOG_CORE_MAX_BONUS = 4;

    public static Map<String, InstallableItemEffect> ITEM_EFFECTS = new HashMap<>();

    static {
        ITEM_EFFECTS.put(ItemIds.FORGETEMPLATE, new ForgeTemplateEffect(ItemIds.FORGETEMPLATE));
        ITEM_EFFECTS.put(ItemIds.EMPTYFORGETEMPLATE, new ForgeTemplateEffect(ItemIds.EMPTYFORGETEMPLATE));
        ITEM_EFFECTS.put(ItemIds.BROKENFORGETEMPLATE, new ForgeTemplateEffect(ItemIds.BROKENFORGETEMPLATE));
        ITEM_EFFECTS.put(ItemIds.AMBASSADOR, new AmbassadorEffect(ItemIds.AMBASSADOR));
        ITEM_EFFECTS.put(ItemIds.VPC, new VPCEffect(ItemIds.VPC));
        ITEM_EFFECTS.put(Items.WORMHOLE_ANCHOR, new WormholeAnchorEffect(Items.WORMHOLE_ANCHOR));
        ITEM_EFFECTS.put(ItemIds.LOG_CORE, new LogCoreEffect(ItemIds.LOG_CORE));
        ITEM_EFFECTS.put(ItemIds.SIMULATOR, new SimulatorEffect(ItemIds.SIMULATOR));
        ITEM_EFFECTS.put(ItemIds.TRANSMITTER, new TransmitterEffect(ItemIds.TRANSMITTER));
        ITEM_EFFECTS.put(ItemIds.NEUAL_COMPOUNDS, new NeuralCompoundsEffect(ItemIds.NEUAL_COMPOUNDS));
    }
}
