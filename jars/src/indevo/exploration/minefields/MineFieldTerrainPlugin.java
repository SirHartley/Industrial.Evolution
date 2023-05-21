package indevo.exploration.minefields;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.BaseLocation;

import java.util.Random;

import static indevo.exploration.minefields.conditions.MineFieldCondition.PLANET_KEY;

public class MineFieldTerrainPlugin extends MineBeltTerrainPlugin {

    public static class MineFieldParams extends RingParams {
        public float minRadius;
        public float maxRadius;
        public int minMines;
        public int maxMines;
        public float minSize;
        public float maxSize;
        public int numMines;

        public MineFieldParams(float minRadius, float maxRadius,
                               int minMines, int maxMines, float minSize,
                               float maxSize, String name) {
            super(maxRadius, maxRadius / 2f, null, name);
            this.minRadius = minRadius;
            this.maxRadius = maxRadius;
            this.minMines = minMines;
            this.maxMines = maxMines;
            this.minSize = minSize;
            this.maxSize = maxSize;
        }
    }

    public static SectorEntityToken addMineField(SectorEntityToken focus, int minRadius,
                                                 float maxRadius, String optionalName) {

        CampaignTerrainAPI mineBelt = ((BaseLocation) focus.getContainingLocation()).addTerrain("IndEvo_mine_field",
                new MineFieldParams(
                        minRadius,
                        maxRadius,
                        10,
                        50,
                        MIN_MINE_SIZE,
                        MAX_MINE_SIZE,
                        optionalName));

        mineBelt.setCircularOrbit(focus, 0.0F, 0.0F, 100.0F);
        return mineBelt;
    }

    public MineFieldTerrainPlugin.MineFieldParams params;

    public void init(String terrainId, SectorEntityToken entity, Object param) {
        super.init(terrainId, entity, param);
        params = (MineFieldTerrainPlugin.MineFieldParams) param;
        name = params.name;
        if (name == null) {
            name = "Minefield";
        }
        params.numMines = params.minMines;
        if (params.maxMines > params.minMines) {
            params.numMines += new Random().nextInt(params.maxMines - params.minMines);
        }
    }

    @Override
    public void renderOnMap(float factor, float alphaMult) {
    }

    @Override
    public void regenerateAsteroids() {
        createMineField();
    }

    protected void createMineField() {
        if (!(params instanceof MineFieldTerrainPlugin.MineFieldParams)) return;

        Random rand = new Random(Global.getSector().getClock().getTimestamp() + entity.getId().hashCode());

        float fieldRadius = params.minRadius + (params.maxRadius - params.minRadius) * rand.nextFloat();
        params.bandWidthInEngine = fieldRadius;
        params.middleRadius = fieldRadius / 2f;

        LocationAPI location = entity.getContainingLocation();
        if (location == null) return;
        for (int i = 0; i < params.numMines; i++) {
            float size = params.minSize + (params.maxSize - params.minSize) * rand.nextFloat();
            SectorEntityToken mine = addMine(location, size);
            if (this.entity.getMemoryWithoutUpdate().contains(PLANET_KEY)) {
                mine.getMemoryWithoutUpdate().set(PLANET_KEY, entity.getMemoryWithoutUpdate().get(PLANET_KEY));
            }

            mine.setFacing(rand.nextFloat() * 360f);

            float r = rand.nextFloat();
            r = 1f - r * r;

            float currRadius = fieldRadius * r;

            float minOrbitDays = Math.max(1f, currRadius * 0.05f);
            float maxOrbitDays = Math.max(2f, currRadius * 2f * 0.05f);
            float orbitDays = minOrbitDays + rand.nextFloat() * (maxOrbitDays - minOrbitDays);

            float angle = rand.nextFloat() * 360f;
            mine.setCircularOrbit(this.entity, angle, currRadius, orbitDays);
            Misc.setAsteroidSource(mine, this);
        }
        needToCreateMines = false;
    }

    public void advance(float amount) {
        if (needToCreateMines) {
            createMineField();
        }
        super.advance(amount);
    }

    public String getNameForTooltip() {
        return "Minefield";
    }

    @Override
    public void reportAsteroidPersisted(SectorEntityToken mine) {
        if (Misc.getAsteroidSource(mine) == this) {
            params.numMines--;
        }
    }
}
