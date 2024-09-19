package indevo.items.consumables.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.loading.CampaignPingSpec;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.artillery.entities.VariableExplosionEntityPlugin;
import indevo.items.consumables.entityAbilities.InterdictionMineAbility;

import java.awt.*;

public class InterceptMissileEntityPlugin extends BaseMissileEntityPlugin {

    public static final float DISTANCE_TO_EXPLODE = 10f;
    public static final float INTERDICT_RANGE = 300f;
    public static final float INTERDICT_SECONDS = 6f;
    public static final float STUN_SECONDS = 2f;

    @Override
    public boolean shouldExplode() {
        for (CampaignFleetAPI fleet : entity.getContainingLocation().getFleets())
            if (Misc.getDistance(fleet, entity) < DISTANCE_TO_EXPLODE + fleet.getRadius() && fleet != source) return true;
        return false;
    }

    @Override
    public void onExplosion() {
        CampaignPingSpec custom = new CampaignPingSpec();
        custom.setColor(getTrailColour());
        custom.setWidth(15);
        custom.setRange(INTERDICT_RANGE * 1.3f);
        custom.setDuration(0.5f);
        custom.setAlphaMult(1f);
        custom.setInFraction(0.1f);
        custom.setNum(1);
        Global.getSector().addPing(entity, custom);

        for (CampaignFleetAPI other : entity.getContainingLocation().getFleets()) {
            if (other == entity) continue;
            if (other.isInHyperspaceTransition()) continue;

            float dist = Misc.getDistance(entity.getLocation(), other.getLocation());
            if (dist > INTERDICT_RANGE) continue;

            SectorEntityToken.VisibilityLevel vis = other.getVisibilityLevelToPlayerFleet();

            if (vis == SectorEntityToken.VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS ||
                vis == SectorEntityToken.VisibilityLevel.COMPOSITION_DETAILS) {
                other.addScript(new InterdictionMineAbility.GoSlowScript(other, STUN_SECONDS));
                other.addFloatingText("Slowed & Interdict! (" + (int) Math.round(INTERDICT_SECONDS) + "s)", getTrailColour(), 2f, true);
            }

            float interdictDays = INTERDICT_SECONDS / Global.getSector().getClock().getSecondsPerDay();

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
                cooldown += origCooldown;
                cooldown += extra;
                float max = Math.max(ability.getSpec().getDeactivationCooldown(), 2f);
                if (cooldown > max) cooldown = max;
                ability.setCooldownLeft(cooldown);
            }

            Global.getSoundPlayer().playSound("world_interdict_hit", 1f, 1f, other.getLocation(), other.getVelocity());
        }
    }

    @Override
    public SpriteAPI getMissileSprite() {
        return Global.getSettings().getSprite("IndEvo", "IndEvo_consumable_missile_intercept");
    }

    @Override
    public Color getTrailColour() {
        return new Color(240, 20,240);
    }
}
