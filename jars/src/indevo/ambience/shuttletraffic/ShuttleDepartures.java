package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.SectorEntityToken;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

// craft leaving one pad within the window, so routes sharing a pad never send a conga line
public class ShuttleDepartures {

    public static final int CAP = 3;
    public static final float WINDOW = 10f;

    private final Map<SectorEntityToken, ArrayDeque<Float>> departures = new HashMap<>();

    public int getRoom(ShuttleAnchor anchor, float time) {
        if (anchor == null || anchor.entity == null) return CAP;

        ArrayDeque<Float> times = departures.get(anchor.entity);
        if (times == null) return CAP;

        while (!times.isEmpty() && time - times.peekFirst() >= WINDOW) times.pollFirst();
        return Math.max(0, CAP - times.size());
    }

    public void add(ShuttleAnchor anchor, int craft, float time) {
        if (anchor == null || anchor.entity == null || craft <= 0) return;

        ArrayDeque<Float> times = departures.computeIfAbsent(anchor.entity, e -> new ArrayDeque<>());
        for (int i = 0; i < craft; i++) times.addLast(time);
    }

    public void prune(float time) {
        Iterator<ArrayDeque<Float>> it = departures.values().iterator();
        while (it.hasNext()) {
            ArrayDeque<Float> times = it.next();
            while (!times.isEmpty() && time - times.peekFirst() >= WINDOW) times.pollFirst();
            if (times.isEmpty()) it.remove();
        }
    }

    public void clear() {
        departures.clear();
    }
}
