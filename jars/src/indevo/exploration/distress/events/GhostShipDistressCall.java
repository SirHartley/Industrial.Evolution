package indevo.exploration.distress.events;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.DerelictShipData;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipCondition;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.utils.ModPlugin;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

import java.util.Random;

import static com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.pickVariant;

public class GhostShipDistressCall implements EveryFrameScript {

    public static Logger log = Global.getLogger(GhostShipDistressCall.class);
    public static final String EVENT_ID = "$indevo_ghostship"; // needs to match distress_call_id in distress_call_data.csv
    public static final int MIN_CREW_FOR_GHOST_SHIP = 50;

    private float duration = 60f; // days
    private boolean setup = false;
    private boolean done = false;
    private SectorEntityToken entity;

    @Override
    public void advance(float amount) {
        if (!setup) setup();
        if (done) return;

        float days = Global.getSector().getClock().convertToDays(amount);
        duration -= days;
        if (duration <= 0) {
            setDone();
            Misc.makeUnimportant(entity, EVENT_ID);
        }
    }

    protected void setup() {
        DistressCallManager manager = DistressCallManager.getInstanceOrRegister();
        StarSystemAPI system = manager.getSystem(EVENT_ID);

        if (system != null){
            ModPlugin.log("setting up distress call in " + system);
            spawnWreck(system);
            setup = true;
        } else {
            ModPlugin.log("Failed to set up distress call " + getClass().getName());
            setDone();
        }
    }

    protected void spawnWreck(StarSystemAPI system) {
        SectorEntityToken jumpPoint = Misc.getDistressJumpPoint(system);

        if (jumpPoint == null) {
            setDone();
            return;
        }

        WeightedRandomPicker<String> factions = SalvageSpecialAssigner.getNearbyFactions(null, system.getLocation(), 20f, 1, 0.5f);

        String variantId = null;
        ShipVariantAPI variant = null;
        int counter = 0;
        int max = 100;
        while (variant == null && counter < max) {
            String faction = factions.pick();
            variantId = pickVariant(faction, new Random(),
                    ShipRoles.COMBAT_MEDIUM, 30f,
                    ShipRoles.CARRIER_MEDIUM, 30f,
                    ShipRoles.COMBAT_LARGE, 10f,
                    ShipRoles.COMBAT_CAPITAL, 10f,
                    ShipRoles.CARRIER_LARGE, 10f
            );

            ShipVariantAPI test = Global.getSettings().getVariant(variantId);
            if (test != null && test.getHullSpec().getMinCrew() >= MIN_CREW_FOR_GHOST_SHIP && !test.getTags().contains(Tags.VARIANT_UNBOARDABLE)) {
                variant = test;
            }

            counter++;
        }

        if (variant == null) {
            ModPlugin.log("tried to pick new faction and ghost ship and failed, aborting");
            setDone();
            return;
        }

        ShipCondition condition = Math.random() > 0.7f ? ShipCondition.PRISTINE : ShipCondition.GOOD;
        PerShipData shipData = new PerShipData(variantId, condition);
        DerelictShipData params = new DerelictShipData(shipData, false);

        params.durationDays = duration;
        entity = BaseThemeGenerator.addSalvageEntity(system, Entities.WRECK, Factions.NEUTRAL, params);
        entity.addTag(Tags.EXPIRES);

        DerelictShipEntityPlugin plugin = new DerelictShipEntityPlugin();
        plugin.init(entity, params);

        float radius = MathUtils.getRandomNumberInRange(300,800);
        float maxRadius = Math.max(300, jumpPoint.getCircularOrbitRadius() * 0.33f);
        if (radius > maxRadius) radius = maxRadius;

        float orbitDays = radius / (5f + StarSystemGenerator.random.nextFloat() * 20f);
        entity.setCircularOrbit(jumpPoint, MathUtils.getRandomNumberInRange(0,360), radius, orbitDays);

        shipData.variant = shipData.variant.clone();

        GhostShipSpecial.GhostShipSpecialData special = new GhostShipSpecial.GhostShipSpecialData((CustomCampaignEntityAPI) entity, shipData);
        Misc.setSalvageSpecial(entity, special);
        Misc.makeImportant(entity, EVENT_ID);

        ModPlugin.log("added entity " + entity.getName() + " in system " + system);
    }

    public void setDone() {
        this.done = true;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

}
