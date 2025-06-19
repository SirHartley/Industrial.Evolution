package indevo.exploration.meteor.terrain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.terrain.CRLossPerSecondBuff;
import com.fs.starfarer.api.impl.campaign.terrain.FlareManager;
import com.fs.starfarer.api.impl.campaign.terrain.PeakPerformanceBuff;
import com.fs.starfarer.api.impl.campaign.terrain.StarCoronaTerrainPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.entities.SpicyRockEntity;
import indevo.exploration.meteor.renderers.MeteorSwarmWarningPathRenderer;
import indevo.exploration.meteor.renderers.RadiationEffectHandler;
import indevo.utils.helper.StringHelper;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class RadioactiveTerrain extends StarCoronaTerrainPlugin {

    @Override
    public boolean containsEntity(SectorEntityToken other) {
        return getEffectForFleet(other) > 0;
    }

    @Override
    public boolean hasTooltip() {
        return true;
    }

    @Override
    public Color getNameColor() {
        return Misc.interpolateColor(SpicyRockEntity.GLOW_COLOR_1, SpicyRockEntity.GLOW_COLOR_2, Global.getSector().getCampaignUI().getSharedFader().getBrightness());
    }

    @Override
    public String getNameForTooltip() {
        return "Extreme Radioactivity";
    }

    @Override
    public String getTerrainName() {
        return "Extreme Radioactivity - " + StringHelper.getAbsPercentString(getEffectForFleet(Global.getSector().getPlayerFleet()), false);
    }

    public static void addToSystem(StarSystemAPI system){
        StarCoronaTerrainPlugin.CoronaParams params = new StarCoronaTerrainPlugin.CoronaParams(50000, 0, system.getCenter(), 0f, 0f, 1f);
        params.name = "Extreme Radioactivity";

        system.addTerrain("IndEvo_radioactive_field", params);
    }

    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        float pad = 10f;
        float small = 5f;

        tooltip.addTitle(name);
        float nextPad = pad;
        if (expanded) {
            tooltip.addSectionHeading("Travel", Alignment.MID, pad);
            nextPad = small;
        }
        tooltip.addPara("Reduces the combat readiness of " +
                "all ships affected by the radiation at a steady pace.", nextPad);

        if (expanded) {
            tooltip.addSectionHeading("Combat", Alignment.MID, pad);
            tooltip.addPara("Reduces the peak performance time of ships and increases the rate of combat readiness degradation in protracted engagements.", small);
        }

        //tooltip.addPara("Does not stack with other similar terrain effects.", pad);
    }

    public float getEffectForFleet(SectorEntityToken fleet){
        return RadiationEffectHandler.get().getActivityLevel(fleet);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        //ONLY works because the terrain is spawned after the warning sign renderer!
        if (!entity.getContainingLocation().getMemoryWithoutUpdate().contains(MeteorSwarmWarningPathRenderer.MEM_INSTANCE)){
            Misc.fadeAndExpire(entity, 0f);
        }
    }


    public float getIntensityAtPoint(Vector2f point) {
        return getEffectForFleet(Global.getSector().getPlayerFleet());
    }

    public Color getAuroraColorForAngle(float angle) {
        return SpicyRockEntity.GLOW_COLOR_2;
    }

    @Override
    public void applyEffect(SectorEntityToken entity, float days) {
        super.applyEffect(entity, days);

        if (entity instanceof CampaignFleetAPI fleet) {

            float intensity = getEffectForFleet(fleet);
            if (intensity <= 0) return;

            if (fleet.hasTag(Tags.FLEET_IGNORES_CORONA)) return;

            String buffId = getModId();
            float buffDur = 0.1f;

            boolean protectedFromCorona = false;
            if (fleet.isInCurrentLocation() &&
                    Misc.getDistance(fleet, Global.getSector().getPlayerFleet()) < 500) {
                for (SectorEntityToken curr : fleet.getContainingLocation().getCustomEntitiesWithTag(Tags.PROTECTS_FROM_CORONA_IN_BATTLE)) {
                    float dist = Misc.getDistance(curr, fleet);
                    if (dist < curr.getRadius() + fleet.getRadius() + 10f) {
                        protectedFromCorona = true;
                        break;
                    }
                }
            }

            // CR loss and peak time reduction
            for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
                float recoveryRate = member.getStats().getBaseCRRecoveryRatePercentPerDay().getModifiedValue();
                float lossRate = member.getStats().getBaseCRRecoveryRatePercentPerDay().getBaseValue();

                float resistance = member.getStats().getDynamic().getValue(Stats.CORONA_EFFECT_MULT);
                if (protectedFromCorona) resistance = 0f;
                //if (inFlare) loss *= 2f;
                float lossMult = 1f;
                float adjustedLossMult = (0f + intensity * resistance * lossMult * CR_LOSS_MULT_GLOBAL);

                float loss = (-1f * recoveryRate + -1f * lossRate * adjustedLossMult) * days * 0.01f;
                float curr = member.getRepairTracker().getBaseCR();
                if (loss > curr) loss = curr;
                if (resistance > 0) { // not actually resistance, the opposite
                    member.getRepairTracker().applyCREvent(loss, "radiation", "Radiation effect");
                }

                // needs to be applied when resistance is 0 to immediately cancel out the debuffs (by setting them to 0)
                float peakFraction = 1f / Math.max(1.3333f, 1f + intensity);
                float peakLost = 1f - peakFraction;
                peakLost *= resistance;
                float degradationMult = 1f + (intensity * resistance) / 2f;
                member.getBuffManager().addBuffOnlyUpdateStat(new PeakPerformanceBuff(buffId + "_1", 1f - peakLost, buffDur));
                member.getBuffManager().addBuffOnlyUpdateStat(new CRLossPerSecondBuff(buffId + "_2", degradationMult, buffDur));
            }
        }
    }

    @Override
    protected boolean shouldPlayLoopOne() {
        return false;
    }

    @Override
    protected boolean shouldPlayLoopTwo() {
        return false;
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
    }

    @Override
    public float getRenderRange() {
        return 0f;
    }

    @Override
    public boolean containsPoint(Vector2f point, float radius) {
        return false;
    }

    @Override
    protected float getExtraSoundRadius() {
        return 0f;
    }

    public boolean isTooltipExpandable() {
        return true;
    }

    public float getTooltipWidth() {
        return 350f;
    }

    public String getEffectCategory() {
        return null; // to ensure multiple coronas overlapping all take effect
        //return "corona_" + (float) Math.random();
    }

    public float getFlareSkipLargeProbability() {
        return 0f;
    }

    public SectorEntityToken getFlareCenterEntity() {
        return this.entity;
    }

    public boolean hasAIFlag(Object flag) {
        return false;
    }

    public float getMaxEffectRadius(Vector2f locFrom) {
        return 0f;
    }
    public float getMinEffectRadius(Vector2f locFrom) {
        return 0f;
    }

    public float getOptimalEffectRadius(Vector2f locFrom) {
        return 0f;    }

    public boolean canPlayerHoldStationIn() {
        return false;
    }

    public FlareManager getFlareManager() {
        return null;
    }

}
