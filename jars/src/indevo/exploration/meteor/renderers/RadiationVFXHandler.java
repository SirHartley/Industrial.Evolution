package indevo.exploration.meteor.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import indevo.exploration.meteor.entities.SpicyRockEntity;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.dark.shaders.util.ShaderLib;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.awt.Color;
import java.io.IOException;
import java.util.EnumSet;

public class RadiationVFXHandler implements LunaCampaignRenderingPlugin {

    // saved through LunaCampaignRenderer, keep field names and types
    private transient Integer shader = 0;
    private EnumSet<CampaignEngineLayers> layers = EnumSet.of(CampaignEngineLayers.ABOVE);
    private float effectlevel = 0f;

    private static RadiationVFXHandler getInstance() {
        RadiationVFXHandler renderer = (RadiationVFXHandler) LunaCampaignRenderer.getRendererOfClass(RadiationVFXHandler.class);
        if (renderer == null) {
            renderer = new RadiationVFXHandler();
            LunaCampaignRenderer.addRenderer(renderer);
        }
        return renderer;
    }

    public static void setLevel(float level) {
        RadiationVFXHandler renderer = getInstance();
        renderer.effectlevel = level;
        if (level <= 0) LunaCampaignRenderer.removeRenderer(renderer);
    }

    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return layers;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (shader == null || shader == 0) {
            try {
                shader = ShaderLib.loadShader(
                        Global.getSettings().loadText("data/shaders/indevo_baseVertex.shader"),
                        Global.getSettings().loadText("data/shaders/indevo_Fragment.shader"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (shader != 0) {
                GL20.glUseProgram(shader);
                GL20.glUniform1i(GL20.glGetUniformLocation(shader, "tex"), 0);
                GL20.glUniform1i(GL20.glGetUniformLocation(shader, "noiseTex1"), 1);
                GL20.glUseProgram(0);
            }
        }

        if (layer != CampaignEngineLayers.ABOVE) return;

        // screen texture can be unloaded if graphicslib shaders are disabled, causing a black screen
        if (ShaderLib.getScreenTexture() == 0) return;

        effectlevel = MathUtils.clamp(effectlevel, 0f, 1f);

        ShaderLib.beginDraw(shader);
        GL20.glUniform1f(GL20.glGetUniformLocation(shader, "level"), effectlevel);

        Color color = SpicyRockEntity.GLOW_COLOR_2;
        GL20.glUniform3f(GL20.glGetUniformLocation(shader, "colorMult"), color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ShaderLib.getScreenTexture());
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        // might fix incompatibilities with odd drivers
        GL20.glValidateProgram(shader);
        if (GL20.glGetProgrami(shader, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
            ShaderLib.exitDraw();
            return;
        }

        GL11.glDisable(GL11.GL_BLEND);
        ShaderLib.screenDraw(ShaderLib.getScreenTexture(), GL13.GL_TEXTURE0);
        ShaderLib.exitDraw();
    }
}
