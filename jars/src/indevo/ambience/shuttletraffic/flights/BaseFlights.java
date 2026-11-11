package indevo.ambience.shuttletraffic.flights;

import com.fs.starfarer.api.campaign.FactionAPI;
import indevo.ambience.shuttletraffic.Shuttle;
import indevo.ambience.shuttletraffic.ShuttleAnchor;
import indevo.ambience.shuttletraffic.ShuttleFactionRepo;
import indevo.ambience.shuttletraffic.ShuttleHull;
import indevo.ambience.shuttletraffic.ShuttleLook;
import indevo.ambience.shuttletraffic.ShuttleNetwork;
import indevo.ambience.shuttletraffic.ShuttleProfile;
import indevo.ambience.shuttletraffic.ShuttleRoute;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import indevo.ambience.shuttletraffic.SpeedClass;
import indevo.ambience.shuttletraffic.movement.ShuttlePath;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

abstract class BaseFlights {

    protected static final float LANE_JITTER = 10f;

    protected final ShuttleTraffic traffic;
    protected final ShuttleNetwork network;
    protected final Random random;
    protected final ShuttlePaths paths;

    protected BaseFlights(ShuttleTraffic traffic, ShuttlePaths paths) {
        this.traffic = traffic;
        this.network = traffic.getNetwork();
        this.random = traffic.getRandom();
        this.paths = paths;
    }

    protected Shuttle create(ShuttleHull hull, ShuttleRoute route, FactionAPI faction, ShuttleAnchor from, ShuttleAnchor to, ShuttlePath path, SpeedClass speed,
                             float speedMult, float jitter, ShuttleLook look, Set<Shuttle.Trait> traits) {
        ShuttleProfile profile = ShuttleFactionRepo.get(faction);
        float spread = 1f + (random.nextFloat() * 2f - 1f) * profile.speedSpread();
        float civil = speed == SpeedClass.CIVIL ? profile.speed() : 1f;
        return new Shuttle(hull, route, faction, from, to, path, from.getEnd(), to.getEnd(), speed, speedMult * spread * civil,
                paths.spread(jitter * profile.jitter()), random.nextFloat(), profile.dark() ? ShuttleLook.SMUGGLER : look, traits);
    }

    protected boolean canFly(ShuttleAnchor from, ShuttleAnchor to) {
        float days = traffic.getDays();
        return from.isUsable(traffic.getLocation()) && to.isUsable(traffic.getLocation()) && !from.threatened && !to.threatened
                && (from.role == ShuttleAnchor.Role.REMNANT || from.isSafe(days)) && !network.isSwarmed(from.getLocation(), to.getLocation(), 0f);
    }

    // formations skip the docking queue
    protected static Set<Shuttle.Trait> leaderTraits(Set<Shuttle.Trait> traits, int count) {
        if (count <= 1) return traits;

        Set<Shuttle.Trait> set = EnumSet.copyOf(traits);
        set.remove(Shuttle.Trait.QUEUES);
        return set;
    }

    protected Shuttle follower(Shuttle leader, ShuttleHull hull, ShuttleLook look, float slotBack, float slotSide, float noise, boolean straggler) {
        Shuttle follower = new Shuttle(hull, null, leader.faction, leader.getFrom(), leader.getTo(), null, leader.getLaunchEnd(), leader.getLandEnd(),
                leader.getSpeedClass(), leader.getSpeedMult(), 0f, random.nextFloat(), look, leaderTraits(leader.getTraits(), 2));
        follower.follow(leader, slotBack, slotSide, noise, straggler);
        return follower;
    }

    protected static List<Shuttle> single(Shuttle shuttle) {
        return shuttle == null ? Collections.emptyList() : List.of(shuttle);
    }
}
