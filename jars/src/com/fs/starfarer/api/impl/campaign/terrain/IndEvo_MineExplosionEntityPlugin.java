package com.fs.starfarer.api.impl.campaign.terrain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class IndEvo_MineExplosionEntityPlugin extends ExplosionEntityPlugin {

    public static class MineExplosionParams extends ExplosionEntityPlugin.ExplosionParams {
        public CampaignFleetAPI alwaysHit;

        public MineExplosionParams(CampaignFleetAPI alwaysHit, Color color, LocationAPI where, Vector2f loc, float radius, float durationMult) {
            super(color, where, loc, radius, durationMult);
            this.alwaysHit = alwaysHit;
        }
    }

    public void applyDamageToFleets() {
        if (params.damage == null || params.damage == ExplosionFleetDamage.NONE) {
            return;
        }

        float shockwaveDist = 0f;
        for (ParticleData p : particles) {
            shockwaveDist = Math.max(shockwaveDist, p.offset.length());
        }

        for (CampaignFleetAPI fleet : entity.getContainingLocation().getFleets()) {
            String id = fleet.getId();
            if (damagedAlready.contains(id)) continue;
            float dist = Misc.getDistance(fleet, entity);
            if (dist < shockwaveDist) {
                float damageMult = 1f - (dist / params.radius);
                if (damageMult > 1f) damageMult = 1f;
                if (damageMult < 0.5f) damageMult = 0.5f;
                if (dist < entity.getRadius() + params.radius * 0.1f) damageMult = 1f;

                damagedAlready.add(id);
                applyDamageToFleet(fleet, damageMult);
            }
        }

        MineExplosionParams params = (MineExplosionParams) this.params;

        //if this fires the player was likely too fast, so they eat full damage.
        if (!damagedAlready.contains(params.alwaysHit.getId())) {
            damagedAlready.add(params.alwaysHit.getId());
            applyDamageToFleet(params.alwaysHit, 1f);
        }

    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if(sprite == null) sprite = Global.getSettings().getSprite("misc", "nebula_particles");
        super.render(layer, viewport);
    }
}
