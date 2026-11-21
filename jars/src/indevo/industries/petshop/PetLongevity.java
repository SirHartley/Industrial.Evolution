package indevo.industries.petshop;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.utils.ModPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// longevity treatment outside the pet: the syringe, where it is placed, Nomios, the life left wording
public final class PetLongevity {

    public static final String NOMIOS = "nomios";
    static final String PLACED_KEY = "$IndEvo_longevitySyringesPlaced";

    private PetLongevity() {
    }

    public static String syringeName() {
        return Global.getSettings().getSpecialItemSpec(ItemIds.LONGEVITY_SYRINGE).getName();
    }

    public static boolean hasSyringe() {
        return syringeCount() > 0;
    }

    public static String syringeIcon() {
        return Global.getSettings().getSpecialItemSpec(ItemIds.LONGEVITY_SYRINGE).getIconName();
    }

    public static int syringeCount() {
        return Math.round(Global.getSector().getPlayerFleet().getCargo().getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(ItemIds.LONGEVITY_SYRINGE, null)));
    }

    // living pets on a player ship or in the menagerie
    public static List<Pet> withFleet() {
        List<Pet> list = new ArrayList<>();
        for (Pet pet : PetStatusManager.getInstance().getPets()) if (pet.isWithPlayer()) list.add(pet);
        return list;
    }

    public static boolean hasPetWithFleet() {
        for (Pet pet : PetStatusManager.getInstance().getPets()) if (pet.isWithPlayer()) return true;
        return false;
    }

    // life left as the player reads it, never the day count; the two short steps are the LIFE_WARNING_DAYS
    public static String lifeLeft(float days) {
        if (days < PetConstants.LIFE_WARNING_DAYS[1]) return "A few days";
        if (days < PetConstants.LIFE_WARNING_DAYS[0]) return "A few weeks";
        if (days < 320f) return "About " + Math.max(2, Math.round(days / 30f)) + " months";
        return "About " + cycles(Math.max(1, Math.round(days / 365f)));
    }

    public static boolean isShort(float days) {
        return days < PetConstants.LIFE_WARNING_DAYS[0];
    }

    public static String cycles(int n) {
        return n + (n == 1 ? " cycle" : " cycles");
    }

    // null when Nomios is gone or lost its cryosanctum
    public static MarketAPI getNomios() {
        MarketAPI market = Global.getSector().getEconomy().getMarket(NOMIOS);
        return market != null && market.hasIndustry(Industries.CRYOSANCTUM) ? market : null;
    }

    // once per game, new or loaded: salvaged centers expire, so a save only reaches the ones still there; labs fill up a short count
    public static void placeSyringes() {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        if (memory.getBoolean(PLACED_KEY)) return;
        memory.set(PLACED_KEY, true);

        List<SectorEntityToken> centers = entities(Ids.ABANDONED_PETSHOP_ENTITY);
        List<SectorEntityToken> labs = entities(Ids.LAB_ENTITY);
        List<SectorEntityToken> picked = new ArrayList<>(centers.subList(0, Math.min(PetConstants.PLACED_SYRINGES, centers.size())));
        for (int i = 0; picked.size() < PetConstants.PLACED_SYRINGES && i < labs.size(); i++) picked.add(labs.get(i));

        for (SectorEntityToken entity : picked) {
            CargoAPI cargo = Global.getFactory().createCargo(true);
            cargo.addSpecial(new SpecialItemData(ItemIds.LONGEVITY_SYRINGE, null), 1);
            BaseSalvageSpecial.addExtraSalvage(entity, cargo);
        }

        ModPlugin.log("Longevity syringes placed: " + picked.size() + " of " + PetConstants.PLACED_SYRINGES + ", pet centers " + centers.size() + ", labs " + labs.size());
    }

    private static List<SectorEntityToken> entities(String type) {
        List<SectorEntityToken> list = new ArrayList<>();
        for (SectorEntityToken token : Global.getSector().getEntitiesWithTag(Tags.SALVAGEABLE)) {
            if (type.equals(token.getCustomEntityType())) list.add(token);
        }
        Collections.shuffle(list, Misc.random);
        return list;
    }
}
