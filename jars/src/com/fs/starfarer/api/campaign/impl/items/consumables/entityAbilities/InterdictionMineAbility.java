package com.fs.starfarer.api.campaign.impl.items.consumables.entityAbilities;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Pings;
import com.fs.starfarer.api.loading.CampaignPingSpec;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class InterdictionMineAbility extends BaseDurationAbility {

    public static class IPReactionScript implements EveryFrameScript {
        float delay;
        boolean done;
        CampaignFleetAPI other;
        SectorEntityToken token;
        float activationDays;
        /**
         * fleet is using IP, other is reacting.
         * @param token
         * @param other
         * @param activationDays
         */
        public IPReactionScript(SectorEntityToken token, CampaignFleetAPI other, float activationDays) {
            this.token = token;
            this.other = other;
            this.activationDays = activationDays;
            delay = 0.3f + 0.3f * (float) Math.random();
            //delay = 0f;
        }
        public void advance(float amount) {
            if (done) return;

            delay -= amount;
            if (delay > 0) return;

            SectorEntityToken.VisibilityLevel level = token.getVisibilityLevelTo(other);
            if (level == SectorEntityToken.VisibilityLevel.NONE || level == SectorEntityToken.VisibilityLevel.SENSOR_CONTACT) {
                done = true;
                return;
            }

            if (!(other.getAI() instanceof ModularFleetAIAPI)) {
                done = true;
                return;
            }
            ModularFleetAIAPI ai = (ModularFleetAIAPI) other.getAI();


            float dist = Misc.getDistance(token.getLocation(), other.getLocation());
            float speed = Math.max(1f, other.getTravelSpeed());
            float eta = dist / speed;

            float rushTime = activationDays * Global.getSector().getClock().getSecondsPerDay();
            rushTime += 0.5f + 0.5f * (float) Math.random();

            MemoryAPI mem = other.getMemoryWithoutUpdate();
            CampaignFleetAPI pursueTarget = mem.getFleet(FleetAIFlags.PURSUIT_TARGET);

            if (eta < rushTime && pursueTarget == token) {
                done = true;
                return;
            }

            float range = InterdictionMineAbility.getRange();
            float getAwayTime = 1f + (range - dist) / speed;
            AbilityPlugin sb = other.getAbility(Abilities.SENSOR_BURST);
            if (getAwayTime > rushTime && sb != null && sb.isUsable() && (float) Math.random() > 0.67f) {
                sb.activate();
                done = true;
                return;
            }

            //float avoidRange = Math.min(dist, getRange(other));
            float avoidRange = getRange() + 100f;
            ai.getNavModule().avoidLocation(token.getContainingLocation(),
                    token.getLocation(), avoidRange, avoidRange + 50f, activationDays + 0.01f);

            ai.getNavModule().avoidLocation(token.getContainingLocation(),
                    //fleet.getLocation(), dist, dist + 50f, activationDays + 0.01f);
                    Misc.getPointAtRadius(token.getLocation(), avoidRange * 0.5f), avoidRange, avoidRange * 1.5f + 50f, activationDays + 0.05f);

            done = true;
        }

        public boolean isDone() {
            return done;
        }
        public boolean runWhilePaused() {
            return false;
        }
    }

    public static final float DEFAULT_STRENGTH = 800f;
    public static final float BASE_RANGE = 600f;
    public static final float BASE_SECONDS = 6f;
    public static final float STRENGTH_PER_SECOND = 200f;

    public static float getRange() {
        return BASE_RANGE;
    }

    @Override
    protected String getActivationText() {
        return "Interdiction pulse";
    }

    protected Boolean primed = null;
    protected Float elapsed = null;
    protected Integer numFired = null;

    @Override
    protected void activateImpl() {
        Global.getSector().addPing(entity, Pings.INTERDICT);

        float range = getRange();
        for (CampaignFleetAPI other : entity.getContainingLocation().getFleets()) {
            if (other == entity) continue;

            float dist = Misc.getDistance(entity.getLocation(), other.getLocation());
            if (dist > range + 500f) continue;

            other.addScript(new InterdictionMineAbility.IPReactionScript(entity, other, getActivationDays()));
        }

        primed = true;
    }

    protected void showRangePing(float amount) {
        SectorEntityToken.VisibilityLevel vis = entity.getVisibilityLevelToPlayerFleet();
        if (vis == SectorEntityToken.VisibilityLevel.NONE || vis == SectorEntityToken.VisibilityLevel.SENSOR_CONTACT) return;


        boolean fire = false;
        if (elapsed == null) {
            elapsed = 0f;
            numFired = 0;
            fire = true;
        }
        elapsed += amount;
        if (elapsed > 0.5f && numFired < 4) {
            elapsed -= 0.5f;
            fire = true;
        }

        if (fire) {
            numFired++;

            float range = getRange();
            CampaignPingSpec custom = new CampaignPingSpec();
            custom.setColor(getColor());
            custom.setWidth(7);
            custom.setMinRange(range - 100f);
            custom.setRange(200);
            custom.setDuration(2f);
            custom.setAlphaMult(0.25f);
            custom.setInFraction(0.2f);
            custom.setNum(1);

            Global.getSector().addPing(entity, custom);
        }

    }

    public static class GoSlowScript implements EveryFrameScript {

        float amt = 0f;
        CampaignFleetAPI fleet;

        public GoSlowScript(CampaignFleetAPI fleet) {
            this.fleet = fleet;
        }

        @Override
        public boolean isDone() {
            return amt > 1f;
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }

        @Override
        public void advance(float amount) {
            if(!isDone()){
                amt += amount;
                fleet.goSlowOneFrame(true);;
            }
        }
    }

    public Color getColor(){
        return new Color(255, 30, 0, 255);
    }

    @Override
    protected void applyEffect(float amount, float level) {
        if (level > 0 && level < 1 && amount > 0) {
            showRangePing(amount);
            return;
        }

        float range = getRange();

        boolean playedHit = !(entity.isInCurrentLocation() && entity.isVisibleToPlayerFleet());
        if (level == 1 && primed != null) {

            if (entity.isInCurrentLocation()) {
                Global.getSector().getMemoryWithoutUpdate().set(MemFlags.GLOBAL_INTERDICTION_PULSE_JUST_USED_IN_CURRENT_LOCATION, true, 0.1f);
            }

            CampaignPingSpec custom = new CampaignPingSpec();
            custom.setColor(getColor());
            custom.setWidth(15);
            custom.setRange(range * 1.3f);
            custom.setDuration(0.5f);
            custom.setAlphaMult(1f);
            custom.setInFraction(0.1f);
            custom.setNum(1);
            Global.getSector().addPing(entity, custom);

            for (CampaignFleetAPI other : entity.getContainingLocation().getFleets()) {
                if (other == entity) continue;
                if (other.isInHyperspaceTransition()) continue;

                float dist = Misc.getDistance(entity.getLocation(), other.getLocation());
                if (dist > range) continue;

                float interdictSeconds = getInterdictSeconds(other);
                if (interdictSeconds > 0 && interdictSeconds < 1f) interdictSeconds = 1f;

                SectorEntityToken.VisibilityLevel vis = other.getVisibilityLevelToPlayerFleet();
                if (vis == SectorEntityToken.VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS ||
                        vis == SectorEntityToken.VisibilityLevel.COMPOSITION_DETAILS) {
                    if (interdictSeconds <= 0) {
                        other.addFloatingText("Interdict avoided!" , getColor(), 1f, true);
                        continue;
                    } else {
                        other.addScript(new GoSlowScript(other));
                        other.addFloatingText("Interdict! (" + (int) Math.round(interdictSeconds) + "s)" , getColor(), 1f, true);
                    }
                }

                float interdictDays = interdictSeconds / Global.getSector().getClock().getSecondsPerDay();

                for (AbilityPlugin ability : other.getAbilities().values()) {
                    if (!ability.getSpec().hasTag(Abilities.TAG_BURN + "+") &&
                            !ability.getId().equals(Abilities.INTERDICTION_PULSE)) continue;

                    float origCooldown = ability.getCooldownLeft();
                    float extra = 0;
                    if (ability.isActiveOrInProgress()) {
                        extra += ability.getSpec().getDeactivationCooldown() * ability.getProgressFraction();
                        ability.deactivate();

                    }

                    if (!ability.getSpec().hasTag(Abilities.TAG_BURN + "+")) continue;

                    float cooldown = interdictDays;
                    //cooldown = Math.max(cooldown, origCooldown);
                    cooldown += origCooldown;
                    cooldown += extra;
                    float max = Math.max(ability.getSpec().getDeactivationCooldown(), 2f);
                    if (cooldown > max) cooldown = max;
                    ability.setCooldownLeft(cooldown);
                }

                if (!playedHit) {
                    Global.getSoundPlayer().playSound("world_interdict_hit", 1f, 1f, other.getLocation(), other.getVelocity());
                    //playedHit = true;
                }
            }

            primed = null;
            elapsed = null;
            numFired = null;
        }

    }

    public static float getInterdictSeconds(CampaignFleetAPI other) {
        float offense = DEFAULT_STRENGTH;
        float defense = other.getSensorRangeMod().computeEffective(other.getSensorStrength());
        float diff = offense - defense;

        float extra = diff / STRENGTH_PER_SECOND;

        float total = BASE_SECONDS + extra;
        if (total < 0f) total = 0f;
        return total;// / Global.getSector().getClock().getSecondsPerDay();
    }

    @Override
    protected void deactivateImpl() {
        cleanupImpl();
    }

    @Override
    protected void cleanupImpl() {
        Misc.fadeAndExpire(entity);
        primed = null;
    }


    @Override
    public boolean isUsable() {
        return super.isUsable() &&
                entity != null;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
    }

    public boolean hasTooltip() {
        return true;
    }


    @Override
    public void fleetLeftBattle(BattleAPI battle, boolean engagedInHostilities) {
        if (engagedInHostilities) {
            deactivate();
        }
    }

    @Override
    public void fleetOpenedMarket(MarketAPI market) {
        deactivate();
    }

}