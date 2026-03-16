package indevo.exploration.minefields;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DisabledArea {
    public float radius;
    public float duration;
    SectorEntityToken entity;

    public boolean isExpired = false;

    public DisabledArea(float radius, float duration, SectorEntityToken entity) {
        this.radius = radius;
        this.duration = duration;
        this.entity = entity;
    }

    public void init() {
        MemoryAPI mem = entity.getContainingLocation().getMemoryWithoutUpdate();
        if (!mem.contains(MineBeltTerrainPlugin.LOCATION_DISABLED_AREA_MEMORY))
            mem.set(MineBeltTerrainPlugin.LOCATION_DISABLED_AREA_MEMORY, new ArrayList<>(Collections.singletonList(this)));
        else ((List<DisabledArea>) mem.get(MineBeltTerrainPlugin.LOCATION_DISABLED_AREA_MEMORY)).add(this);
    }

    public boolean isExpired() {
        return isExpired;
    }

    public String getBeltId() {
        return entity.getOrbitFocus().getId();
    }

    public void advance(float amt) {
        duration -= amt;
        if (duration <= 0 && !isExpired) remove();
    }

    public void remove() {
        isExpired = true;
        entity.getContainingLocation().removeEntity(entity);

        MemoryAPI mem = entity.getContainingLocation().getMemoryWithoutUpdate();
        ((List<DisabledArea>) mem.get(MineBeltTerrainPlugin.LOCATION_DISABLED_AREA_MEMORY)).remove(this);
    }

    public boolean contains(SectorEntityToken t) {
        return Misc.getDistance(entity.getLocation(), t.getLocation()) <= radius;
    }
}
