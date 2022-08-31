package com.fs.starfarer.api.impl.campaign.terrain;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class IndEvo_MineImpact implements EveryFrameScript {

    //currently a copy of AsteroidImpact

    public static float DURATION_SECONDS = 0.3f;
    public static float EXPLOSION_SIZE = 150f;

    protected CampaignFleetAPI fleet;
    protected float elapsed;
    protected Vector2f dV;

    protected boolean done = false;
    protected SectorEntityToken explosion = null;
    protected float delay = 0.1f;
    protected float delay2 = 1f;

    Vector2f mineLoc;

    public IndEvo_MineImpact(CampaignFleetAPI fleet) {
        this.fleet = fleet;

        //System.out.println("ADDING IMPACT TO " + fleet.getName() + " " + fleet.getId());

        Vector2f v = fleet.getVelocity();
        float angle = Misc.getAngleInDegrees(v);
        float speed = v.length();
        if (speed < 10) angle = fleet.getFacing();

        float mult = Misc.getFleetRadiusTerrainEffectMult(fleet);

        //float arc = 120f;
        float arc = 120f - 60f * mult; // larger fleets suffer more direct collisions that slow them down more

        angle += (float) Math.random() * arc - arc / 2f;
        angle += 180f;

        if (fleet.isInCurrentLocation()) {
            Vector2f test = Global.getSector().getPlayerFleet().getLocation();
            float dist = Misc.getDistance(test, fleet.getLocation());
            if (dist < HyperspaceTerrainPlugin.STORM_STRIKE_SOUND_RANGE) {
                float volumeMult = 0.75f;
                volumeMult *= 0.5f + 0.5f * mult;
                if (volumeMult > 0) {
                    Global.getSoundPlayer().playSound("hit_heavy", 1f, 1f * volumeMult, fleet.getLocation(), Misc.ZERO);
                }
            }

            float size = 10f + (float) Math.random() * 6f;
            size *= 0.67f;

            SectorEntityToken mine = IndEvo_MineBeltTerrainPlugin.addMine(fleet.getContainingLocation(), size);
            mine.setFacing((float) Math.random() * 360f);

            Vector2f al = Misc.getUnitVectorAtDegreeAngle(angle + 180f);
            al.scale(fleet.getRadius());
            Vector2f.add(al, fleet.getLocation(), al);
            mine.setLocation(al.x, al.y);

            //mine.setLocation(fleet.getLocation().x, fleet.getLocation().y);
            //float sign = Math.signum(mine.getRotation());
            //mine.setRotation(sign * (50f + 50f * (float) Math.random()));

            this.mineLoc = mine.getLocation();

            Misc.fadeInOutAndExpire(mine, 0.1f, 0f, 0.1f);

            //mult = 1f;
            Vector2f iv = fleet.getVelocity();
            iv = new Vector2f(iv);
            iv.scale(0.7f);
            float glowSize = 100f + 100f * mult + 50f * (float) Math.random();
            Color color = new Color(255, 165, 100, 255);
            Misc.addHitGlow(fleet.getContainingLocation(), al, iv, glowSize, color);
        }

        dV = Misc.getUnitVectorAtDegreeAngle(angle);

        float impact = speed * 1f * (0.5f + mult * 0.5f);
        dV.scale(impact);
        dV.scale(1f / DURATION_SECONDS);
    }

    public void advance(float amount) {
        delay -= amount;
        elapsed += amount;

        if (elapsed < DURATION_SECONDS) {
            fleet.setOrbit(null);
            Vector2f v = fleet.getVelocity();
            fleet.setVelocity(v.x + dV.x * amount, v.y + dV.y * amount);
        }

        if (delay <= 0 && explosion == null && mineLoc != null) {
            LocationAPI cl = fleet.getContainingLocation();
            Vector2f loc = mineLoc;

            float size = EXPLOSION_SIZE;
            Color color = new Color(255, 165, 80);
            IndEvo_MineExplosionEntityPlugin.MineExplosionParams params = new IndEvo_MineExplosionEntityPlugin.MineExplosionParams(fleet, color, cl, loc, size, 1f);

            int caps = 0;
            for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
                if (m.getHullSpec().getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP) caps++;
            }

            //NPC fleets are allowed to cheat cause they be dumb af
            params.damage = caps > 3 && fleet.isPlayerFleet() ? ExplosionEntityPlugin.ExplosionFleetDamage.MEDIUM : ExplosionEntityPlugin.ExplosionFleetDamage.LOW;

            explosion = cl.addCustomEntity(Misc.genUID(), "Explosion",
                    "IndEvo_mine_explosion", Factions.NEUTRAL, params);
            explosion.setLocation(loc.x, loc.y);
        }

        if (explosion != null) {
            delay2 -= amount;
            if (!explosion.isAlive() || delay2 <= 0) {
                done = true;
            }
        }

    }

    public boolean isDone() {
        return done;
    }

    public boolean runWhilePaused() {
        return false;
    }

}