package indevo.exploration.meteor;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.relay.industry.MilitaryRelay;
import indevo.utils.ModPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MeteorSwarmScript implements EveryFrameScript {
    public StarSystemAPI system;
    public float intensity; //1 to 3
    public float meteorSpawnDuration;
    public Vector2f arcLoc1;
    public Vector2f arcLoc2;
    public Vector2f arcLoc3;
    public float width;

    private String seed;
    private Random random;

    List<MeteorEntity> meteorList = new ArrayList<>();
    private float timePassed = 0;

    IntervalUtil interval = new IntervalUtil(1f, 7f);

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        //add to system
        //spawn warning icons via MeteorSwarWrnRenderer
        //spawn exclusion zone for NPCs
        //spawn meteors outside of viewpoint
        //make em move
        //make em despawn

        if (Global.getSettings().isDevMode()){

            CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
            if (playerFleet.isInHyperspace() || playerFleet.getStarSystem().getCenter() == null) return;

            SectorEntityToken center = playerFleet.getStarSystem().getCenter();
            interval.advance(amount);

            if (interval.intervalElapsed()){
                ModPlugin.log("spawning meteor");

                float size = (float) (130f - (70f * Math.random()));
                MeteorEntity.MeteorData data = new MeteorEntity.MeteorData(size, center.getLocation(), Misc.getDistance(playerFleet.getLocation(), center.getLocation()),0,380, (1 - size / 130f) * 1000f);
                MeteorEntity.spawn(center.getContainingLocation(), data);
            }
        }
    }

    public void spawnSwarm(){

    }
}
