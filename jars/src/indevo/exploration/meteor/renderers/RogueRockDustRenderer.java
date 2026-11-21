package indevo.exploration.meteor.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import indevo.exploration.meteor.entities.IceTreasureoidEntity;
import indevo.exploration.meteor.entities.IcyRockEntity;
import indevo.exploration.meteor.entities.MeteorEntity;
import indevo.exploration.meteor.entities.SpicyRockEntity;
import indevo.exploration.meteor.entities.TreasuradoidEntity;
import indevo.exploration.meteor.entities.TreasuroidEntity;
import indevo.exploration.meteor.rogue.RogueAsteroidSpawner;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.List;
import java.util.*;

public class RogueRockDustRenderer implements LunaCampaignRenderingPlugin {

    public enum Kind {
        ASTEROID(new Color(172, 154, 132), new Color(150, 138, 122), null, 0f),
        ICE(new Color(186, 220, 240), new Color(178, 208, 226), new Color(232, 246, 255), 1.2f),
        IRRADIATED(new Color(150, 206, 92), new Color(140, 160, 110), null, 0f),
        TREASURE(new Color(236, 190, 96), new Color(200, 170, 100), new Color(255, 224, 150), 4f);

        public final Color dust;
        public final Color chip;
        public final Color glint;
        public final float glintsPerSecond;

        Kind(Color dust, Color chip, Color glint, float glintsPerSecond) {
            this.dust = dust;
            this.chip = chip;
            this.glint = glint;
            this.glintsPerSecond = glintsPerSecond;
        }
    }

    // dust and chips below the rocks (ASTEROIDS), glints above
    private static final EnumSet<CampaignEngineLayers> LAYERS = EnumSet.of(CampaignEngineLayers.TERRAIN_4, CampaignEngineLayers.TERRAIN_5);

    public static final float TAU = (float) (Math.PI * 2);
    public static final int PUFFS = 3;
    public static final float PUFF_ALPHA = 0.17f;
    public static final float CHIP_ALPHA = 0.9f;
    public static final float GLINT_ALPHA = 0.7f;
    public static final float GLINT_DRIFT = 14f;
    public static final float GLINT_MIN_PIXELS = 2.2f;
    public static final float VIEW_MARGIN_MULT = 3f;
    public static final float VIEW_MARGIN = 100f;
    public static final float SCAN_INTERVAL = 0.5f;

    // chip outline as (angle in rad, radius factor), from the mockup
    private static final float[][] CHIP_SHAPE = {{0f, 1f}, {1.5f, 0.7f}, {3.1f, 0.95f}, {4.6f, 0.6f}};

    public static class Puff {
        public float angle, dist, orbit, size, rot, spin, phase;
        public int cell;
    }

    public static class Chip {
        public float angle, dist, orbit, wobble, size, rot, spin, phase, brightness;
    }

    public static class Glint {
        public float x, y, vx, vy, age, life, size;
    }

    public static class RockLook {
        public final SectorEntityToken rock;
        public final Kind kind;
        public final float size;
        public final List<Puff> puffs = new ArrayList<>();
        public final List<Chip> chips = new ArrayList<>();
        public final List<Glint> glints = new ArrayList<>();
        public float glintBudget;

        public RockLook(SectorEntityToken rock, Kind kind, float size, Random random) {
            this.rock = rock;
            this.kind = kind;
            this.size = size;

            for (int i = 0; i < PUFFS; i++) {
                Puff p = new Puff();
                p.angle = random.nextFloat() * TAU;
                p.dist = size * lerp(0.45f, 1.15f, random.nextFloat());
                p.orbit = (random.nextFloat() - 0.5f) * 0.5f;
                p.size = size * lerp(1.6f, 2.5f, random.nextFloat()) + 22f;
                p.rot = random.nextFloat() * TAU;
                p.spin = (random.nextFloat() - 0.5f) * 0.3f;
                p.phase = random.nextFloat() * TAU;
                p.cell = random.nextInt(16);
                puffs.add(p);
            }

            int chipCount = 3 + (int) (size / 30f);
            for (int i = 0; i < chipCount; i++) {
                Chip c = new Chip();
                c.angle = random.nextFloat() * TAU;
                c.dist = size * lerp(0.75f, 1.9f, random.nextFloat()) + 8f;
                c.orbit = (random.nextBoolean() ? -1f : 1f) * lerp(0.08f, 0.35f, random.nextFloat());
                c.wobble = lerp(3f, 10f, random.nextFloat());
                float s = random.nextFloat();
                c.size = lerp(3f, 8f, s * s);
                c.rot = random.nextFloat() * TAU;
                c.spin = (random.nextFloat() - 0.5f) * 3f;
                c.phase = random.nextFloat() * TAU;
                c.brightness = lerp(0.5f, 1f, random.nextFloat());
                chips.add(c);
            }
        }
    }

    private final Map<SectorEntityToken, RockLook> looks = new LinkedHashMap<>();
    private final Random random = new Random();
    private LocationAPI location;
    private float time = 0f;
    private float scanTimer = SCAN_INTERVAL;

    private transient SpriteAPI dustSprite;
    private transient SpriteAPI glowSprite;

    public static void register() {
        if (LunaCampaignRenderer.hasRendererOfClass(RogueRockDustRenderer.class)) return;
        LunaCampaignRenderer.addTransientRenderer(new RogueRockDustRenderer());
    }

    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return LAYERS;
    }

    @Override
    public void advance(float amount) {
        if (Global.getSector().isPaused()) return;

        time += amount;
        scanTimer += amount;

        LocationAPI current = Global.getSector().getCurrentLocation();
        // no callback when an entity enters a location, and rocks of a loaded save or another system need a look too
        if (current != location || scanTimer >= SCAN_INTERVAL) {
            scanTimer = 0f;
            scan(current);
        }

        ViewportAPI viewport = Global.getSector().getViewport();
        for (RockLook look : looks.values()) advanceGlints(look, amount, viewport);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (looks.isEmpty() || location != Global.getSector().getCurrentLocation()) return;

        if (layer == CampaignEngineLayers.TERRAIN_4) {
            renderDust(viewport);
            renderChips(viewport);
        } else {
            renderGlints(viewport);
        }
    }

    private void scan(LocationAPI current) {
        if (current != location) looks.clear();
        location = current;
        if (current == null) return;

        Set<SectorEntityToken> seen = new HashSet<>();
        for (SectorEntityToken rock : current.getEntitiesWithTag(RogueAsteroidSpawner.TAG_ROGUE_ROCK)) {
            seen.add(rock);
            if (!looks.containsKey(rock)) looks.put(rock, createLook(rock));
        }

        looks.keySet().retainAll(seen);
    }

    private static RockLook createLook(SectorEntityToken rock) {
        Object plugin = rock.getCustomPlugin();
        float size = plugin instanceof MeteorEntity meteor ? meteor.size : rock.getRadius();
        return new RockLook(rock, getKind(plugin), size, new Random(rock.getId().hashCode()));
    }

    public static Kind getKind(Object plugin) {
        if (plugin instanceof TreasuroidEntity || plugin instanceof IceTreasureoidEntity || plugin instanceof TreasuradoidEntity) return Kind.TREASURE;
        if (plugin instanceof IcyRockEntity) return Kind.ICE;
        if (plugin instanceof SpicyRockEntity) return Kind.IRRADIATED;
        return Kind.ASTEROID;
    }

    private void advanceGlints(RockLook look, float amount, ViewportAPI viewport) {
        Iterator<Glint> it = look.glints.iterator();
        while (it.hasNext()) {
            Glint g = it.next();
            g.age += amount;
            if (g.age >= g.life) {
                it.remove();
                continue;
            }
            g.x += g.vx * amount;
            g.y += g.vy * amount;
        }

        if (look.kind.glintsPerSecond <= 0f || viewport == null || !isNearView(look, viewport)) {
            look.glintBudget = 0f;
            return;
        }

        look.glintBudget += look.kind.glintsPerSecond * amount;
        while (look.glintBudget >= 1f) {
            look.glintBudget -= 1f;
            Glint g = new Glint();
            float a = random.nextFloat() * TAU;
            float d = (float) Math.sqrt(random.nextFloat()) * look.size * 0.5f;
            float drift = (random.nextFloat() * 2f - 1f) * GLINT_DRIFT;
            float da = random.nextFloat() * TAU;
            g.x = (float) Math.cos(a) * d;
            g.y = (float) Math.sin(a) * d;
            g.vx = (float) Math.cos(da) * drift;
            g.vy = (float) Math.sin(da) * drift;
            g.life = lerp(0.45f, 1.1f, random.nextFloat());
            g.size = lerp(3f, 6f, random.nextFloat());
            look.glints.add(g);
        }
    }

    private void renderDust(ViewportAPI viewport) {
        if (dustSprite == null) dustSprite = Global.getSettings().getSprite("misc", "nebula_particles");

        dustSprite.setNormalBlend();
        dustSprite.setTexWidth(0.25f);
        dustSprite.setTexHeight(0.25f);

        for (RockLook look : looks.values()) {
            float alpha = getAlpha(look, viewport);
            if (alpha <= 0f || !isNearView(look, viewport)) continue;

            float x0 = look.rock.getLocation().x;
            float y0 = look.rock.getLocation().y;
            dustSprite.setColor(look.kind.dust);

            for (Puff p : look.puffs) {
                float a = p.angle + p.orbit * time;
                float d = p.dist * (1f + 0.15f * (float) Math.sin(time * 0.5f + p.phase));
                float pulse = 0.75f + 0.25f * (float) Math.sin(time * 0.6f + p.phase);

                dustSprite.setTexX((p.cell % 4) * 0.25f);
                dustSprite.setTexY((p.cell / 4) * 0.25f);
                dustSprite.setSize(p.size, p.size);
                dustSprite.setAngle((float) Math.toDegrees(p.rot + p.spin * time));
                dustSprite.setAlphaMult(alpha * PUFF_ALPHA * pulse);
                dustSprite.renderAtCenter(x0 + (float) Math.cos(a) * d, y0 + (float) Math.sin(a) * d);
            }
        }
    }

    private void renderChips(ViewportAPI viewport) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBegin(GL11.GL_TRIANGLES);

        float minRadius = 0.7f * viewport.getViewMult();
        for (RockLook look : looks.values()) {
            float alpha = getAlpha(look, viewport);
            if (alpha <= 0f || !isNearView(look, viewport)) continue;

            float x0 = look.rock.getLocation().x;
            float y0 = look.rock.getLocation().y;
            Color col = look.kind.chip;

            for (Chip c : look.chips) {
                float a = c.angle + c.orbit * time;
                float d = c.dist + (float) Math.sin(time * 0.6f + c.phase) * c.wobble;
                float x = x0 + (float) Math.cos(a) * d;
                float y = y0 + (float) Math.sin(a) * d;
                float r = Math.max(minRadius, c.size / 2f) * 1.25f;
                float rot = c.rot + c.spin * time;

                GL11.glColor4f(col.getRed() / 255f * c.brightness, col.getGreen() / 255f * c.brightness, col.getBlue() / 255f * c.brightness, CHIP_ALPHA * alpha);
                chipVertex(x, y, r, rot, 0);
                chipVertex(x, y, r, rot, 1);
                chipVertex(x, y, r, rot, 2);
                chipVertex(x, y, r, rot, 0);
                chipVertex(x, y, r, rot, 2);
                chipVertex(x, y, r, rot, 3);
            }
        }

        GL11.glEnd();
        GL11.glPopAttrib();
    }

    private static void chipVertex(float x, float y, float r, float rot, int i) {
        float a = rot + CHIP_SHAPE[i][0];
        float rr = r * CHIP_SHAPE[i][1];
        GL11.glVertex2f(x + (float) Math.cos(a) * rr, y + (float) Math.sin(a) * rr);
    }

    private void renderGlints(ViewportAPI viewport) {
        if (glowSprite == null) glowSprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

        glowSprite.setAdditiveBlend();
        float minSize = GLINT_MIN_PIXELS * viewport.getViewMult();

        for (RockLook look : looks.values()) {
            if (look.glints.isEmpty()) continue;
            float alpha = getAlpha(look, viewport);
            if (alpha <= 0f) continue;

            float x0 = look.rock.getLocation().x;
            float y0 = look.rock.getLocation().y;
            glowSprite.setColor(look.kind.glint);

            for (Glint g : look.glints) {
                float u = g.age / g.life;
                float size = Math.max(minSize, g.size * 1.6f);
                glowSprite.setSize(size, size);
                glowSprite.setAlphaMult(alpha * GLINT_ALPHA * (float) Math.pow(Math.sin(Math.PI * u), 0.7));
                glowSprite.renderAtCenter(x0 + g.x, y0 + g.y);
            }
        }
    }

    // matches the alpha the engine gives the rock sprite, so dust fades with a breaking rock
    private static float getAlpha(RockLook look, ViewportAPI viewport) {
        if (!look.rock.isAlive()) return 0f;
        return viewport.getAlphaMult() * look.rock.getSensorFaderBrightness() * look.rock.getSensorContactFaderBrightness();
    }

    private static boolean isNearView(RockLook look, ViewportAPI viewport) {
        return viewport.isNearViewport(look.rock.getLocation(), look.size * VIEW_MARGIN_MULT + VIEW_MARGIN);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
