package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.group.FGRaidAction;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;

import java.util.Random;

// test only: finished raids, so the salvage yards intel lookup runs on the real classes
public final class FakeRaids {
    private FakeRaids() {
    }

    public static class Raid extends RaidIntel {
        public Raid(StarSystemAPI system) {
            super(system, Global.getSector().getFaction(Factions.PIRATES), null);
        }

        @Override
        public boolean isSucceeded() {
            return true;
        }

        @Override
        public float getRaidFPAdjusted() {
            return 100f;
        }

        @Override
        public void advance(float amount) {
        }

        @Override
        public boolean isDone() {
            return false;
        }
    }

    public static class Blockade extends GenericRaidFGI {
        public Blockade(GenericRaidParams params) {
            super(params);
        }

        @Override
        public boolean isSucceeded() {
            return true;
        }

        @Override
        public float getTotalFPSpawned() {
            return 100f;
        }

        @Override
        public void advance(float amount) {
        }

        @Override
        public boolean isDone() {
            return false;
        }
    }

    public static Blockade blockade(StarSystemAPI system, MarketAPI source) {
        GenericRaidFGI.GenericRaidParams params = new GenericRaidFGI.GenericRaidParams(new Random(1L), false);
        params.factionId = Factions.PIRATES;
        params.source = source;
        params.raidParams.where = system;
        params.fleetSizes.add(1);
        return new Blockade(params);
    }
}
