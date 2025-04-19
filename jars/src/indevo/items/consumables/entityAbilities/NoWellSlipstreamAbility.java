package indevo.items.consumables.entityAbilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.abilities.GenerateSlipsurgeAbility;
import com.fs.starfarer.api.impl.campaign.ids.Pings;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2;
import com.fs.starfarer.api.util.DelayedActionScript;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

import static indevo.items.consumables.itemAbilities.missiles.SurgeAbilityPlugin.TEMP_ABILITY_ID;

public class NoWellSlipstreamAbility extends GenerateSlipsurgeAbility {

    public float angle = 0f;

    public void activateAtAngle(float angle){
        this.angle = angle;
        activateImpl();
    }

    @Override
    protected void activateImpl() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return;

        primed = true;

        float offset = 100f;
        Vector2f from = Misc.getUnitVectorAtDegreeAngle(angle);
        from.scale(offset + fleet.getRadius());
        Vector2f.add(from, fleet.getLocation(), from);
        startLoc = from;

        SectorEntityToken token = fleet.getContainingLocation().createToken(startLoc);
        Global.getSector().addPing(token, Pings.SLIPSURGE);

        fleet.getContainingLocation().addScript(new ExpandStormRadiusScript(16000f));

        deductCost();

        fleet.addScript(new DelayedActionScript(0.2f) {
            @Override
            public void doAction() {
                generateSlipstream();
            }
        });
    }

    protected void generateSlipstream() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null || !fleet.isInHyperspace()) return;

        float strength = 1f;
        float offset = 100f;
        Vector2f from = startLoc;

        SlipstreamTerrainPlugin2.SlipstreamParams2 params = new SlipstreamTerrainPlugin2.SlipstreamParams2();

        params.enteringSlipstreamTextOverride = "Entering slipsurge";
        params.enteringSlipstreamTextDurationOverride = 0.1f;
        params.forceNoWindVisualEffectOnFleets = true;

        float width = 600f;
        float length = 1000f;

        length += strength * 500f;
        params.burnLevel = Math.round(400f + strength * strength * 500f);
        params.accelerationMult = 20f + strength * strength * 280f;
        params.baseWidth = width;
        params.widthForMaxSpeed = 400f;
        params.widthForMaxSpeedMinMult = 0.34f;

        params.slowDownInWiderSections = true;
        params.minSpeed = Misc.getSpeedForBurnLevel(params.burnLevel - params.burnLevel/8);
        params.maxSpeed = Misc.getSpeedForBurnLevel(params.burnLevel + params.burnLevel/8);
        params.lineLengthFractionOfSpeed = 2000f / ((params.maxSpeed + params.minSpeed) * 0.5f);

        float lineFactor = 0.1f;
        params.minSpeed *= lineFactor;
        params.maxSpeed *= lineFactor;
        params.maxBurnLevelForTextureScroll = (int) (params.burnLevel * 0.1f);

        params.particleFadeInTime = 0.01f;
        params.areaPerParticle = 1000f;

        Vector2f to = Misc.getUnitVectorAtDegreeAngle(angle);
        to.scale(offset + fleet.getRadius() + length);
        Vector2f.add(to, startLoc, to);

        CampaignTerrainAPI slipstream = (CampaignTerrainAPI) fleet.getContainingLocation().addTerrain(Terrain.SLIPSTREAM, params);
        slipstream.addTag(Tags.SLIPSTREAM_VISIBLE_IN_ABYSS);
        slipstream.setLocation(from.x, from.y);

        SlipstreamTerrainPlugin2 plugin = (SlipstreamTerrainPlugin2) slipstream.getPlugin();

        float spacing = 100f;
        float incr = spacing / length;

        Vector2f diff = Vector2f.sub(to, from, new Vector2f());
        for (float f = 0; f <= 1f; f += incr) {
            Vector2f curr = new Vector2f(diff);
            curr.scale(f);
            Vector2f.add(curr, from, curr);
            plugin.addSegment(curr, width - Math.min(300f, 300f * (float)Math.sqrt(f)));
        }

        plugin.recomputeIfNeeded();

        plugin.despawn(1.5f, 0.2f, new Random());

        slipstream.addScript(new SlipsurgeFadeInScript(plugin));
        fleet.addScript(new SlipsurgeEffectScript(fleet, plugin));
    }

    @Override
    public boolean isUsable() {
        return true;
    }
}
