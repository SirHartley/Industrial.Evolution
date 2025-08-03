package indevo.submarkets.impl;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.plugins.impl.CoreAutofitPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import indevo.industries.RequisitionCenter;
import indevo.industries.embassy.listeners.AmbassadorPersonManager;
import indevo.submarkets.DynamicSubmarket;
import indevo.utils.helper.MiscIE;

import java.util.*;

public class RequisitionsCenterSubmarketPlugin extends BaseSubmarketPlugin implements DynamicSubmarket {

    public boolean isSetForRemoval = false;
    private boolean playerPaidToUnlock = false;

    public void prepareForRemoval() {
        this.isSetForRemoval = true;
    }

    public void updateCargoPrePlayerInteraction() {
        if (market.isPlayerOwned() && !playerPaidToUnlock) {
            setPlayerPaidToUnlock(true);
        }

        if (!playerPaidToUnlock) return;

        float seconds = Global.getSector().getClock().convertToSeconds(sinceLastCargoUpdate);
        addAndRemoveStockpiledResources(seconds, false, true, true); //This clears commodities for us, uses shouldHaveCommodity/getStockpileLimit
        sinceLastCargoUpdate = 0f;

        if (okToUpdateShipsAndWeapons()) {
            sinceSWUpdate = 0f;

            pruneWeapons(0f); //clears current weapons to make space for new ones

            //if gamma core or no local ambassador
            if (getLocalAICoreId().equals(Commodities.GAMMA_CORE) || !AmbassadorPersonManager.hasAmbassador(market)) {
                //do a mixed selection
                int factAmt = getActiveFactionList(0.35f, Global.getSector().getPlayerFaction()).size();

                int weapons = 5
                        + Math.max(0, market.getSize() - 1 + factAmt)
                        + (Misc.isMilitary(market) ? 5 : 0)
                        + (getLocalAICoreId().equals(Commodities.ALPHA_CORE) ? 5 : 0);

                int fighters = 1
                        + Math.max(0, (market.getSize() - 1 + factAmt) / 2)
                        + (Misc.isMilitary(market) ? 2 : 0);

                addWeapons(weapons, weapons + 5);
                //addFighters(fighters, fighters + 5);

            } else if (AmbassadorPersonManager.hasAmbassador(market)) {

                //do a single faction selection
                FactionAPI faction = AmbassadorPersonManager.getAmbassador(market).getFaction();
                int weapons = 7
                        + Math.max(0, market.getSize() - 1)
                        + (Misc.isMilitary(market) ? 5 : 0)
                        + (getLocalAICoreId().equals(Commodities.ALPHA_CORE) ? 5 : 0);

                int fighters = 3
                        + Math.max(0, (market.getSize() - 1) / 2)
                        + (Misc.isMilitary(market) ? 2 : 0);

                addWeapons(weapons, weapons + 2, faction.getId());
                //addFighters(fighters, fighters + 2, faction.getId());
            }

            getCargo().getMothballedShips().clear();
        }

        getCargo().sort();
    }

    public boolean isParticipatesInEconomy() {
        return false;
    }

    public static List<FactionAPI> getActiveFactionList(float minimumStanding, FactionAPI toFaction) {
        List<FactionAPI> list = new ArrayList<>();
        List<FactionAPI> inactiveFactions = AmbassadorPersonManager.getListOfIncativeFactions();
        Set<String> blacklist = MiscIE.getCSVSetFromMemory(Ids.ORDER_LIST);

        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            if (inactiveFactions.contains(faction)
                    || toFaction.getRelationship(faction.getId()) < minimumStanding
                    || faction == toFaction
                    || blacklist.contains(faction.getId())) {
                continue;
            }

            list.add(faction);
        }

        return list;
    }

    private String getLocalAICoreId() {
        String id = "none";

        if (market.hasIndustry(Ids.REQCENTER) && market.getIndustry(Ids.REQCENTER).getAICoreId() != null) {
            id = market.getIndustry(Ids.REQCENTER).getAICoreId();
        }

        return id;
    }

    public void init(SubmarketAPI submarket) {
        super.init(submarket);
        this.submarket.setFaction(Global.getSector().getFaction("reqSellStorageColour"));
    }

    protected void addWeapons(int min, int max, String factionId) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(itemGenRandom);
        picker.add(factionId, Global.getSector().getFaction(factionId).getRelationship(Global.getSector().getPlayerFaction().getId()));

        addWeapons(min, max, picker);
    }

    protected void addWeapons(int min, int max) {
        FactionAPI playerFaction = Global.getSector().getPlayerFaction();
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(itemGenRandom);
        for (FactionAPI faction : getActiveFactionList(0.35f, playerFaction)) {
            picker.add(faction.getId(), faction.getRelationship(playerFaction.getId()));
        }

        addWeapons(min, max, picker);
    }

    protected void addWeapons(int min, int max, WeightedRandomPicker<String> factionPicker) {
        WeightedRandomPicker<WeaponSpecAPI> picker = new WeightedRandomPicker<>(itemGenRandom);

        WeightedRandomPicker<WeaponSpecAPI> pd = new WeightedRandomPicker<>(itemGenRandom);
        WeightedRandomPicker<WeaponSpecAPI> kinetic = new WeightedRandomPicker<>(itemGenRandom);
        WeightedRandomPicker<WeaponSpecAPI> nonKinetic = new WeightedRandomPicker<>(itemGenRandom);
        WeightedRandomPicker<WeaponSpecAPI> missile = new WeightedRandomPicker<>(itemGenRandom);
        WeightedRandomPicker<WeaponSpecAPI> strike = new WeightedRandomPicker<>(itemGenRandom);

        boolean betaCore = getLocalAICoreId().equals(Commodities.BETA_CORE);
        boolean isPlayerOwned = market.isPlayerOwned();
        FactionAPI playerFaction = Global.getSector().getPlayerFaction();
        Map<String, Float> weaponsInCargoMap = null;
        if (getLocalAICoreId().equals(Commodities.ALPHA_CORE)) weaponsInCargoMap = getWeaponsInCargo();

        for (int i = 0; i < factionPicker.getItems().size(); i++) {

            String factionId = factionPicker.getItems().get(i);

            int maxTier = getMaxTier(Global.getSector().getFaction(factionId).getRelationship(playerFaction.getId()));
            if (betaCore) maxTier++; //increase tier for beta core effect

            float w = factionPicker.getWeight(i);
            if (factionId == null) factionId = market.getFactionId();

            FactionAPI faction = Global.getSector().getFaction(factionId);

            for (String id : faction.getKnownWeapons()) {
                WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(id);

                if (spec.getTier() > maxTier) continue;
                if (betaCore && spec.getTier() < 1) continue;

                float p = 0.5f;
                p *= w;

                if (spec.getTier() > 1 && (betaCore || !isPlayerOwned))
                    p += (1 + spec.getTier()) / spec.getRarity(); //If a beta core is installed or the market is AI controlled add tier to pick chance if it's >2

                picker.add(spec, p);

                String cat = spec.getAutofitCategory();
                if (cat != null && spec.getSize() != WeaponAPI.WeaponSize.LARGE) {
                    if (CoreAutofitPlugin.PD.equals(cat)) {
                        pd.add(spec, p);
                    } else if (CoreAutofitPlugin.STRIKE.equals(cat)) {
                        strike.add(spec, p);
                    } else if (CoreAutofitPlugin.KINETIC.equals(cat)) {
                        kinetic.add(spec, p);
                    } else if (CoreAutofitPlugin.MISSILE.equals(cat) || CoreAutofitPlugin.ROCKET.equals(cat)) {
                        missile.add(spec, p);
                    } else if (CoreAutofitPlugin.HE.equals(cat) || CoreAutofitPlugin.ENERGY.equals(cat)) {
                        nonKinetic.add(spec, p);
                    }
                }
            }
        }

        int num = min + itemGenRandom.nextInt(max - min + 1);

        if (num > 0 && !pd.isEmpty()) {
            pickAndAddWeapons(pd, weaponsInCargoMap);
            num--;
        }
        if (num > 0 && !kinetic.isEmpty()) {
            pickAndAddWeapons(kinetic, weaponsInCargoMap);
            num--;
        }
        if (num > 0 && !missile.isEmpty()) {
            pickAndAddWeapons(missile, weaponsInCargoMap);
            num--;
        }
        if (num > 0 && !nonKinetic.isEmpty()) {
            pickAndAddWeapons(nonKinetic, weaponsInCargoMap);
            num--;
        }
        if (num > 0 && !strike.isEmpty()) {
            pickAndAddWeapons(strike, weaponsInCargoMap);
            num--;
        }


        for (int i = 0; i < num && !picker.isEmpty(); i++) {
            pickAndAddWeapons(picker, weaponsInCargoMap);
        }
    }

    protected void pickAndAddWeapons(WeightedRandomPicker<WeaponSpecAPI> picker, Map<String, Float> weaponsInCargoMap) {
        WeaponSpecAPI spec = picker.pick();
        if (spec == null) return;
        boolean hasAlpha = getLocalAICoreId().equals(Commodities.ALPHA_CORE);

        //If it has an alpha core and the weapon is present too many times in storage, pick a new one.
        //Stop after 20 attempts to avoid endless loop
        if (hasAlpha && weaponsInCargoMap != null) {
            int i = 0;
            while ((weaponsInCargoMap.containsKey(spec.getWeaponId()) && weaponsInCargoMap.get(spec.getWeaponId()) > 10) && i < 20) {
                spec = picker.pick();
                i++;
            }
        }

        int count = 2;
        switch (spec.getSize()) {
            case LARGE:
                count = hasAlpha ? 3 : 2;
                break;
            case MEDIUM:
                count = hasAlpha ? 6 : 4;
                break;
            case SMALL:
                count = hasAlpha ? 10 : 8;
                break;
        }

        count = count + itemGenRandom.nextInt(count + 1) - count / 2;
        getCargo().addWeapons(spec.getWeaponId(), count);
    }

    private Map<String, Float> getWeaponsInCargo() {
        Map<String, Float> weaponList = new HashMap<>();

        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!m.hasSubmarket(Submarkets.SUBMARKET_STORAGE) || MiscIE.getStorageCargo(m).isEmpty()) continue;
            for (CargoAPI.CargoItemQuantity<String> w : MiscIE.getStorageCargo(m).getWeapons()) {
                addToStringMap(w.getItem(), w.getCount(), weaponList);
            }
        }

        return weaponList;
    }

    public static void addToStringMap(String s, int amt, Map<String, Float> map) {
        if (map.containsKey(s)) {
            map.put(s, map.get(s) + amt);
        } else {
            map.put(s, amt * 1f);
        }
    }

    protected void addFighters(int min, int max, int maxTier, WeightedRandomPicker<String> factionPicker) {
        int num = min + itemGenRandom.nextInt(max - min + 1);
        for (int i = 0; i < num; i++) {
            String factionId = factionPicker.pick();
            addFighters(1, 1, maxTier, factionId);
        }
    }

    protected void pruneWeapons(float keepFraction) {
        CargoAPI cargo = getCargo();
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (stack.isWeaponStack() || stack.isFighterWingStack()) {
                float qty = stack.getSize();
                if (qty <= 1) {
                    if (itemGenRandom.nextFloat() > keepFraction) {
                        cargo.removeItems(stack.getType(), stack.getData(), 1);
                    }
                } else {
                    cargo.removeItems(stack.getType(), stack.getData(), Math.round(qty * (1f - keepFraction)));
                }
            }
        }
    }

    protected Object writeReplace() {
        if (okToUpdateShipsAndWeapons()) {
            pruneWeapons(0f);
            getCargo().getMothballedShips().clear();
        }
        return this;
    }

    private int getMaxTier(float standing) {
        if (standing < 0.50) return 0;
        if (standing >= 0.50 && standing < 0.70) return 1;
        if (standing >= 0.70 && standing < 0.90) return 2;
        return 3;
    }

    public boolean shouldHaveCommodity(CommodityOnMarketAPI com) {
        return false; //prohibit commodity spawning
    }

    @Override
    public int getStockpileLimit(CommodityOnMarketAPI com) {
        return 0; //Make extra sure everything gets cleared
    }

    public boolean showInFleetScreen() {
        return false;
    }

    public boolean showInCargoScreen() {
        return !isSetForRemoval;
    }

    @Override
    public boolean isOpenMarket() {
        return true;
    }

    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        return action == TransferAction.PLAYER_SELL;
    }

    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
        return "You can not sell anything here.";
    }

    @Override
    public boolean isTooltipExpandable() { // TODO: 18/06/2020 tooltip expand stuff
        return super.isTooltipExpandable();
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        if (!playerPaidToUnlock) {
            tooltip.addPara("Requires a one-time access fee of %s. Unlike normal markets, it will have large and rare weapons for sale if your standing is good enough.", 10f, Misc.getHighlightColor(),
                    "" + Misc.getDGSCredits(getUnlockCost() * 1f));
            return;
        }

        if (market.hasIndustry(Ids.REQCENTER)) {
            ((RequisitionCenter) market.getIndustry(Ids.REQCENTER)).publicAddAfterDescriptionSection(tooltip, Industry.IndustryTooltipMode.NORMAL);
        }
    }

    @Override
    public float getTariff() {
        return market.isPlayerOwned() ? 0.40f : 0.60f;
    }

    //unlock shit

    public void setPlayerPaidToUnlock(boolean playerPaidToUnlock) {
        this.playerPaidToUnlock = playerPaidToUnlock;
    }

    public OnClickAction getOnClickAction(CoreUIAPI ui) {
        if (playerPaidToUnlock) return OnClickAction.OPEN_SUBMARKET;
        return OnClickAction.SHOW_TEXT_DIALOG;
    }

    private int getUnlockCost() {
        return 35000;
    }

    private boolean canPlayerAffordUnlock() {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        int credits = (int) playerFleet.getCargo().getCredits().get();
        return credits >= getUnlockCost();
    }

    public String getDialogText(CoreUIAPI ui) {
        if (canPlayerAffordUnlock()) {
            return "Gaining access to the Requisitions Center requires a one-time fee of " + getUnlockCost() + " credits.";
        } else {
            return "Gaining access to Requisitions Center requires a one-time fee of " + getUnlockCost() + " credits, which you can't afford.";
        }
    }

    public Highlights getDialogTextHighlights(CoreUIAPI ui) {
        Highlights h = new Highlights();
        h.setText("" + getUnlockCost());
        if (canPlayerAffordUnlock()) {
            h.setColors(Misc.getHighlightColor());
        } else {
            h.setColors(Misc.getNegativeHighlightColor());
        }
        return h;
    }

    public DialogOption[] getDialogOptions(CoreUIAPI ui) {
        if (canPlayerAffordUnlock()) {
            return new DialogOption[]{
                    new DialogOption("Pay", new Script() {
                        public void run() {
                            CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
                            playerFleet.getCargo().getCredits().subtract(getUnlockCost());
                            playerPaidToUnlock = true;
                        }
                    }),
                    new DialogOption("Never mind", null)
            };
        } else {
            return new DialogOption[]{
                    new DialogOption("Never mind", null)
            };
        }
    }
}