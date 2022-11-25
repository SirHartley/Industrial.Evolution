package industrial_evolution.industries;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.econ.impl.OrbitalStation;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

public class IndEvo_OrbitalStation extends OrbitalStation {

    @Override
    protected void ensureStationEntityIsSetOrCreated() {
        if (stationEntity == null) {
            for (SectorEntityToken entity : market.getConnectedEntities()) {
                if (entity.hasTag(Tags.STATION) && !entity.hasTag("no_orbital_station")) {
                    stationEntity = entity;
                    usingExistingStation = true;
                    break;
                }
            }
        }

        if (stationEntity == null) {
            stationEntity = market.getContainingLocation().addCustomEntity(
                    null, market.getName() + " Station", Entities.STATION_BUILT_FROM_INDUSTRY, market.getFactionId());
            SectorEntityToken primary = market.getPrimaryEntity();
            float orbitRadius = primary.getRadius() + 150f;
            stationEntity.setCircularOrbitWithSpin(primary, (float) Math.random() * 360f, orbitRadius, orbitRadius / 10f, 5f, 5f);
            market.getConnectedEntities().add(stationEntity);
            stationEntity.setMarket(market);
        }
    }
}
