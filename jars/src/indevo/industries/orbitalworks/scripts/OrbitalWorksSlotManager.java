package indevo.industries.orbitalworks.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;
import indevo.industries.orbitalworks.data.OrbitalWorksSlotJob;
import indevo.industries.orbitalworks.hullmods.RelicLattice;
import indevo.utils.helper.MiscIE;
import indevo.utils.timers.NewDayListener;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OrbitalWorksSlotManager implements NewDayListener {

    public static final int DAYS_REQUIRED = 93;

    private final List<OrbitalWorksSlotJob> jobs = new ArrayList<>();

    public static OrbitalWorksSlotManager getInstanceOrRegister() {
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (manager.hasListenerOfClass(OrbitalWorksSlotManager.class)) return manager.getListeners(OrbitalWorksSlotManager.class).get(0);

        OrbitalWorksSlotManager instance = new OrbitalWorksSlotManager();
        manager.addListener(instance);
        return instance;
    }

    public static OrbitalWorksSlotManager getInstance() {
        List<OrbitalWorksSlotManager> list = Global.getSector().getListenerManager().getListeners(OrbitalWorksSlotManager.class);
        return list.isEmpty() ? null : list.get(0);
    }

    public static boolean isWorking(MarketAPI market) {
        Industry works = market.getIndustry(Industries.ORBITALWORKS);
        return works != null && works.isFunctional();
    }

    public OrbitalWorksSlotJob getJob(MarketAPI market) {
        for (OrbitalWorksSlotJob job : jobs) if (job.market == market) return job;
        return null;
    }

    public void start(MarketAPI market, FleetMemberAPI member) {
        jobs.add(new OrbitalWorksSlotJob(market, member));
    }

    public void abort(OrbitalWorksSlotJob job) {
        jobs.remove(job);
        moveToStorage(job.market, job.member);
    }

    @Override
    public void onNewDay() {
        for (OrbitalWorksSlotJob job : new ArrayList<>(jobs)) {
            MarketAPI market = job.market;

            if (!market.isPlayerOwned() || !market.isInEconomy()) {
                jobs.remove(job);
                relocate(job);
                continue;
            }

            if (market.getIndustry(Industries.ORBITALWORKS) == null) {
                onWorksRemoved(market, null);
                continue;
            }

            if (!isWorking(market)) continue;

            job.daysDone++;
            if (job.daysDone >= DAYS_REQUIRED) finish(job);
        }
    }

    // docked shut down goes to the fleet, like vanilla returns items to fleet cargo (BaseIndustry.getCargoForInteractionMode)
    public void onWorksRemoved(MarketAPI market, MarketAPI.MarketInteractionMode mode) {
        OrbitalWorksSlotJob job = getJob(market);
        if (job == null) return;

        jobs.remove(job);
        String title = "Relic Lattice Work at %s stopped: the Orbital Works was removed.";

        if (mode == MarketAPI.MarketInteractionMode.LOCAL) {
            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(job.member);
            sendMessage(title, market, "%s returned to your fleet. The work was lost.", job.member.getShipName());
        } else {
            moveToStorage(market, job.member);
            sendMessage(title, market, "%s was placed in local storage. The work was lost.", job.member.getShipName());
        }
    }

    private void finish(OrbitalWorksSlotJob job) {
        jobs.remove(job);

        FleetMemberAPI member = job.member;
        ShipVariantAPI variant = member.getVariant().clone();
        variant.setOriginalVariant(null);
        variant.setSource(VariantSource.REFIT);
        variant.addPermaMod(RelicLattice.ID, false);
        member.setVariant(variant, false, true);
        member.updateStats();

        moveToStorage(job.market, member);
        sendMessage("Relic Lattice Work finished at %s.", job.market,
                "%s can take one more S-mod and was placed in local storage.", member.getShipName());
    }

    private void relocate(OrbitalWorksSlotJob job) {
        MarketAPI target = null;
        float best = Float.MAX_VALUE;

        for (MarketAPI market : Misc.getPlayerMarkets(true)) {
            if (MiscIE.getStorageCargo(market) == null) continue;

            float dist = Misc.getDistanceLY(job.market.getLocationInHyperspace(), market.getLocationInHyperspace());
            if (dist < best) {
                best = dist;
                target = market;
            }
        }

        String name = job.member.getShipName();
        if (target != null) {
            moveToStorage(target, job.member);
            sendMessage("Relic Lattice Work at %s stopped: the colony was lost.", job.market,
                    "%s was moved to storage at %s. The work was lost.", name, target.getName());
        } else {
            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(job.member);
            sendMessage("Relic Lattice Work at %s stopped: the colony was lost.", job.market,
                    "%s returned to your fleet. The work was lost.", name);
        }
    }

    private void moveToStorage(MarketAPI market, FleetMemberAPI member) {
        CargoAPI cargo = MiscIE.getStorageCargo(market);
        if (cargo == null) {
            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
            return;
        }

        cargo.initMothballedShips(Factions.PLAYER);
        cargo.getMothballedShips().addFleetMember(member);
    }

    private void sendMessage(String title, MarketAPI market, String line, String... highlights) {
        MessageIntel intel = new MessageIntel(title, Misc.getTextColor(), new String[]{market.getName()}, market.getFaction().getBrightUIColor());
        Color[] colors = new Color[highlights.length];
        Arrays.fill(colors, Misc.getHighlightColor());
        intel.addLine(line, Misc.getTextColor(), highlights, colors);
        intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
        intel.setIcon(Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getIconName());
        Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, market);
    }
}
