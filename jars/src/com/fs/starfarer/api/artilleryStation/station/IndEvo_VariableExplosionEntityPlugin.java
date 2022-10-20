package com.fs.starfarer.api.artilleryStation.station;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;

public class IndEvo_VariableExplosionEntityPlugin extends ExplosionEntityPlugin {

    public static final float FADEOUT_FRACTION = 0.5f;
    public float blastwaveRadius;
    public float blastwaveDuration;
    public float elapsed = 0f;
    public transient SpriteAPI blastWaveSprite;

    public static class VariableExplosionParams extends ExplosionEntityPlugin.ExplosionParams {
        public float baseDamageMult;
        public String sound;
        public boolean withBlastwave;

        public VariableExplosionParams(String sound, boolean withBlastwave, float damageMult, Color color, LocationAPI where, Vector2f loc, float radius, float durationMult) {
            super(color, where, loc, radius, durationMult);
            this.sound = sound;
            this.withBlastwave = withBlastwave;
            this.baseDamageMult = damageMult;
        }
    }

    Object readResolve() {
        sprite = Global.getSettings().getSprite("misc", "nebula_particles");
        blastWaveSprite = Global.getSettings().getSprite("graphics/fx/shields256.png");
        return this;
    }

    public void init(SectorEntityToken entity, Object pluginParams) {
        this.entity = entity;
        this.params = (VariableExplosionParams) pluginParams;
        readResolve();

        VariableExplosionParams params = (VariableExplosionParams) pluginParams;
        if (params.where.isCurrentLocation()) {
            String explosionSoundID = params.sound != null ? params.sound : "gate_explosion";
            Global.getSoundPlayer().playSound(explosionSoundID, MathUtils.getRandomNumberInRange(0.9f, 1.1f), 1f, params.loc, Misc.ZERO);
        }

        float baseSize = params.radius * 0.08f;
        maxParticleSize = baseSize * 2f;

        float fullArea = (float) (Math.PI * params.radius * params.radius);
        float particleArea = (float) (Math.PI * baseSize * baseSize);

        int count = (int) Math.round(fullArea / particleArea * 1f);

        float durMult = 2f;
        durMult = params.durationMult;

        for (int i = 0; i < count; i++) {
            float size = baseSize * (1f + (float) Math.random());

            Color randomColor = new Color(Misc.random.nextInt(256),
                    Misc.random.nextInt(256), Misc.random.nextInt(256), params.color.getAlpha());
            Color adjustedColor = Misc.interpolateColor(params.color, randomColor, 0.2f);
            adjustedColor = params.color;
            ParticleData data = new ParticleData(adjustedColor, size,
                    (0.25f + (float) Math.random()) * 2f * durMult, 3f);

            float r = (float) Math.random();
            float dist = params.radius * 0.2f * (0.1f + r * 0.9f);
            float dir = (float) Math.random() * 360f;
            data.setOffset(dir, dist, dist);

            dir = Misc.getAngleInDegrees(data.offset);
            data.swImpact = (float) Math.random();
            if (i > count / 2) data.swImpact = 1;

            particles.add(data);
        }

        Vector2f loc = new Vector2f(params.loc);
        loc.x -= params.radius * 0.01f;
        loc.y += params.radius * 0.01f;

        float b = 1f;
        params.where.addHitParticle(loc, new Vector2f(), params.radius * 1f, b, 1f * durMult, params.color);
        loc = new Vector2f(params.loc);
        params.where.addHitParticle(loc, new Vector2f(), params.radius * 0.4f, 0.5f, 1f * durMult, Color.white);

        shockwaveAccel = baseSize * 70f / durMult;
        shockwaveRadius = -params.radius * 0.5f;
        shockwaveSpeed = params.radius * 2f / durMult;
        shockwaveDuration = params.radius * 2f / shockwaveSpeed;
        shockwaveWidth = params.radius * 0.5f;


        blastwaveRadius = params.radius * 1.3f;
        blastwaveDuration = 0.1f + 0.3f * durMult;

    }

    public void advance(float amount) {
        super.advance(amount);
        elapsed += amount;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if(sprite == null) sprite = Global.getSettings().getSprite("misc", "nebula_particles");
        if(blastWaveSprite == null) blastWaveSprite = Global.getSettings().getSprite("graphics/fx/shields256.png");

        if(((VariableExplosionParams) params).withBlastwave){
            //blastwave
            float alphaMult = viewport.getAlphaMult();
            alphaMult *= entity.getSensorFaderBrightness();
            alphaMult *= entity.getSensorContactFaderBrightness();
            if (alphaMult <= 0) return;

            float fraction = MathUtils.clamp(elapsed / blastwaveDuration, 0f, 1f);
            float size = blastwaveRadius * fraction;
            float fadeoutDurStart = blastwaveDuration * FADEOUT_FRACTION;
            float alpha = elapsed > fadeoutDurStart ? 1 - MathUtils.clamp((elapsed - fadeoutDurStart) / (blastwaveDuration - fadeoutDurStart), 0f, 1f) : 1f;

            blastWaveSprite.setAdditiveBlend();
            blastWaveSprite.setColor(Color.WHITE);
            blastWaveSprite.setSize(size, size);
            blastWaveSprite.setAlphaMult(alpha);
            blastWaveSprite.setColor(params.color.brighter().brighter());
            blastWaveSprite.renderAtCenter(entity.getLocation().x, entity.getLocation().y);
        }

        super.render(layer, viewport);
    }
}
