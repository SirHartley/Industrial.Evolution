package indevo.exploration.fog.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTiledTerrain;
import com.fs.starfarer.api.combat.ViewportAPI;
import indevo.exploration.fog.sim.FogGrid;
import indevo.exploration.fog.sim.FogNoise;
import org.dark.shaders.util.ShaderLib;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public final class FogRenderer {

    // dark, body and light shade of fog in a system without a nebula
    public static final float[][] GREY = {{0.08f, 0.085f, 0.095f}, {0.27f, 0.28f, 0.30f}, {0.64f, 0.65f, 0.68f}};
    public static final float NEBULA_BODY = 0.44f;
    public static final float NEBULA_DARK = 0.28f;
    public static final float NEBULA_LIGHT = 0.9f;
    public static final float NEBULA_LIGHT_WHITE = 0.3f;
    public static final int MAX_HOLES = 16;

    private static final String VERTEX_PATH = "data/shaders/indevo_fogVertex.shader";
    private static final String FRAGMENT_PATH = "data/shaders/indevo_fogFragment.shader";

    // gl objects live for the whole application, shared by every fog
    private static boolean loaded = false;
    private static int program = 0;
    private static int texture = 0;
    private static ByteBuffer pixels;
    private static final FloatBuffer HOLES = BufferUtils.createFloatBuffer(MAX_HOLES * 3);
    private static int uAnchor, uGO, uGS, uTime, uCell, uAlpha, uTex, uHoles, uHN, uC0, uC1, uC2;

    private FogRenderer() {
    }

    // fog takes the average colour of the system's nebula texture, grey without one
    public static float[][] coloursFor(LocationAPI loc) {
        for (CampaignTerrainAPI terrain : loc.getTerrainCopy()) {
            if (!Terrain.NEBULA.equals(terrain.getType()) || !(terrain.getPlugin() instanceof BaseTiledTerrain nebula)) continue;
            BaseTiledTerrain.TileParams params = nebula.getParams();
            if (params == null || params.cat == null || params.key == null) continue;
            Color avg = Global.getSettings().getSprite(params.cat, params.key).getAverageColor();
            if (avg == null) continue;
            float r = avg.getRed() / 255f, g = avg.getGreen() / 255f, b = avg.getBlue() / 255f;
            float max = Math.max(r, Math.max(g, b));
            if (max <= 0.01f) continue;
            r /= max;
            g /= max;
            b /= max;
            float[] body = {r * NEBULA_BODY, g * NEBULA_BODY, b * NEBULA_BODY};
            float[] dark = {body[0] * NEBULA_DARK, body[1] * NEBULA_DARK, body[2] * NEBULA_DARK};
            float[] light = new float[3];
            float[] hue = {r, g, b};
            for (int i = 0; i < 3; i++) light[i] = hue[i] * NEBULA_LIGHT * (1f - NEBULA_LIGHT_WHITE) + NEBULA_LIGHT_WHITE;
            return new float[][]{dark, body, light};
        }
        return GREY;
    }

    public static void render(FogGrid grid, Vector2f anchor, ViewportAPI viewport, float[] holes, int holeCount, float time, float[][] colours) {
        float inset = grid.cell * 2f;
        float x0 = Math.max(anchor.x + grid.ox + inset, viewport.getLLX());
        float y0 = Math.max(anchor.y + grid.oy + inset, viewport.getLLY());
        float x1 = Math.min(anchor.x + grid.ox + grid.size - inset, viewport.getLLX() + viewport.getVisibleWidth());
        float y1 = Math.min(anchor.y + grid.oy + grid.size - inset, viewport.getLLY() + viewport.getVisibleHeight());
        if (x0 >= x1 || y0 >= y1) return;

        load();
        if (program == 0) renderPlain(grid, anchor, viewport.getAlphaMult(), holes, holeCount, colours, x0, y0, x1, y1);
        else renderShader(grid, anchor, viewport.getAlphaMult(), holes, holeCount, time, colours, x0, y0, x1, y1);
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        String vert, frag;
        try {
            vert = Global.getSettings().loadText(VERTEX_PATH);
            frag = Global.getSettings().loadText(FRAGMENT_PATH);
        } catch (IOException e) {
            throw new IllegalStateException("IndEvo fog shader files missing", e);
        }

        program = ShaderLib.loadShader(vert, frag);
        if (program == 0) {
            Global.getLogger(FogRenderer.class).warn("IndEvo fog shader did not compile, drawing plain fog");
            return;
        }

        uAnchor = GL20.glGetUniformLocation(program, "uAnchor");
        uGO = GL20.glGetUniformLocation(program, "uGO");
        uGS = GL20.glGetUniformLocation(program, "uGS");
        uTime = GL20.glGetUniformLocation(program, "uTime");
        uCell = GL20.glGetUniformLocation(program, "uCell");
        uAlpha = GL20.glGetUniformLocation(program, "uAlpha");
        uTex = GL20.glGetUniformLocation(program, "uTex");
        uHoles = GL20.glGetUniformLocation(program, "uHoles");
        uHN = GL20.glGetUniformLocation(program, "uHN");
        uC0 = GL20.glGetUniformLocation(program, "uC0");
        uC1 = GL20.glGetUniformLocation(program, "uC1");
        uC2 = GL20.glGetUniformLocation(program, "uC2");
    }

    private static void renderShader(FogGrid grid, Vector2f anchor, float alphaMult, float[] holes, int holeCount, float time,
                                     float[][] colours, float x0, float y0, float x1, float y1) {
        int n = grid.n;
        if (texture == 0) {
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        }
        if (pixels == null || pixels.capacity() != n * n * 4) pixels = BufferUtils.createByteBuffer(n * n * 4);
        grid.pack(pixels);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, n, n, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);

        GL20.glUseProgram(program);
        GL20.glUniform2f(uAnchor, anchor.x, anchor.y);
        GL20.glUniform2f(uGO, grid.ox, grid.oy);
        GL20.glUniform1f(uGS, grid.size);
        GL20.glUniform1f(uTime, time);
        GL20.glUniform1f(uCell, grid.cell);
        GL20.glUniform1f(uAlpha, alphaMult);
        GL20.glUniform1i(uTex, 0);
        HOLES.clear();
        for (int i = 0; i < MAX_HOLES * 3; i++) HOLES.put(i < holeCount * 3 ? holes[i] : 0f);
        HOLES.flip();
        GL20.glUniform3(uHoles, HOLES);
        GL20.glUniform1f(uHN, holeCount);
        GL20.glUniform3f(uC0, colours[0][0], colours[0][1], colours[0][2]);
        GL20.glUniform3f(uC1, colours[1][0], colours[1][1], colours[1][2]);
        GL20.glUniform3f(uC2, colours[2][0], colours[2][1], colours[2][2]);

        // shader writes premultiplied colour
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x0, y0);
        GL11.glVertex2f(x1, y0);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x0, y1);
        GL11.glEnd();

        GL20.glUseProgram(0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void renderPlain(FogGrid grid, Vector2f anchor, float alphaMult, float[] holes, int holeCount,
                                    float[][] colours, float x0, float y0, float x1, float y1) {
        int n = grid.n;
        float cell = grid.cell;
        int i0 = Math.max(1, (int) Math.floor((x0 - anchor.x - grid.ox) / cell - 0.5f));
        int i1 = Math.min(n - 2, (int) Math.ceil((x1 - anchor.x - grid.ox) / cell - 0.5f));
        int j0 = Math.max(1, (int) Math.floor((y0 - anchor.y - grid.oy) / cell - 0.5f));
        int j1 = Math.min(n - 2, (int) Math.ceil((y1 - anchor.y - grid.oy) / cell - 0.5f));
        float[] body = colours[1], light = colours[2];
        float r = body[0] + (light[0] - body[0]) * 0.3f, g = body[1] + (light[1] - body[1]) * 0.3f, b = body[2] + (light[2] - body[2]) * 0.3f;

        boolean textured = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBegin(GL11.GL_QUADS);
        for (int j = j0; j < j1; j++) {
            for (int i = i0; i < i1; i++) {
                plainVertex(grid, anchor, i, j, alphaMult, holes, holeCount, r, g, b);
                plainVertex(grid, anchor, i + 1, j, alphaMult, holes, holeCount, r, g, b);
                plainVertex(grid, anchor, i + 1, j + 1, alphaMult, holes, holeCount, r, g, b);
                plainVertex(grid, anchor, i, j + 1, alphaMult, holes, holeCount, r, g, b);
            }
        }
        GL11.glEnd();
        if (textured) GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void plainVertex(FogGrid grid, Vector2f anchor, int i, int j, float alphaMult, float[] holes, int holeCount,
                                    float r, float g, float b) {
        float x = grid.ox + (i + 0.5f) * grid.cell, y = grid.oy + (j + 0.5f) * grid.cell;
        float d = Math.min(1f, grid.d[j * grid.n + i]);
        float c = Math.min(1f, d / 0.85f);
        float a = (float) Math.pow(c * c * (3 - 2 * c), 1.1) * 0.85f * alphaMult;
        for (int h = 0; h < holeCount; h++) {
            float hr = holes[h * 3 + 2];
            a *= (float) FogNoise.smooth(hr * 0.45, hr * 1.5, Math.hypot(x - holes[h * 3], y - holes[h * 3 + 1]));
        }
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex2f(anchor.x + x, anchor.y + y);
    }
}
