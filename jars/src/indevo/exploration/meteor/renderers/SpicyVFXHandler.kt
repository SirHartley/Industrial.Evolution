package indevo.exploration.meteor.renderers

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignEngineLayers
import com.fs.starfarer.api.combat.ViewportAPI
import indevo.exploration.meteor.entities.SpicyRockEntity
import indevo.utils.ModPlugin
import lunalib.lunaUtil.campaign.LunaCampaignRenderer
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin
import org.dark.shaders.util.ShaderLib
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL20
import java.util.*

class SpicyVFXHandler : LunaCampaignRenderingPlugin {
    companion object {

        @JvmStatic
        private fun getInstance() : SpicyVFXHandler {
            var renderer = LunaCampaignRenderer.getRendererOfClass(SpicyVFXHandler::class.java) as SpicyVFXHandler?
            if (renderer == null) {
                renderer = SpicyVFXHandler()
                LunaCampaignRenderer.addRenderer(renderer)
            }
            return renderer
        }

        @JvmStatic
        fun setLevel(level: Float) {
            var renderer = getInstance()

            renderer.effectlevel = level

            if (level <= 0) {
                LunaCampaignRenderer.removeRenderer(renderer)
            }
        }


    }

    @Transient
    var shader: Int? = 0

    var layers = EnumSet.of(CampaignEngineLayers.ABOVE)

    var effectlevel = 0f

    init {

    }

    override fun isExpired(): Boolean {
        return false
    }

    override fun advance(amount: Float) {
    }

    override fun getActiveLayers(): EnumSet<CampaignEngineLayers> {
        return layers
    }

    override fun render(layer: CampaignEngineLayers?, viewport: ViewportAPI?) {

        if (shader == null || shader == 0) {
            shader = ShaderLib.loadShader(
                Global.getSettings().loadText("data/shaders/indevo_baseVertex.shader"),
                Global.getSettings().loadText("data/shaders/indevo_Fragment.shader"))
            if (shader != 0) {
                GL20.glUseProgram(shader!!)

                GL20.glUniform1i(GL20.glGetUniformLocation(shader!!, "tex"), 0)
                GL20.glUniform1i(GL20.glGetUniformLocation(shader!!, "noiseTex1"), 1)

                GL20.glUseProgram(0)
            } else {
                var test = ""
            }
        }

        if (layer == CampaignEngineLayers.ABOVE) {
            var playerfleet = Global.getSector().playerFleet

            //Screen texture can be unloaded if graphicslib shaders are disabled, causing a blackscreen
            if (ShaderLib.getScreenTexture() != 0) {
                //Shader

                effectlevel = MathUtils.clamp(effectlevel, 0f, 1f)

                ShaderLib.beginDraw(shader!!);
                GL20.glUniform1f(GL20.glGetUniformLocation(shader!!, "level"), effectlevel)

                var color = SpicyRockEntity.GLOW_COLOR_2

                GL20.glUniform3f(GL20.glGetUniformLocation(shader!!, "colorMult"), color.red / 255f, color.green / 255f, color.blue / 255f)

                GL13.glActiveTexture(GL13.GL_TEXTURE0 + 0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, ShaderLib.getScreenTexture());

                //Reset Texture
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + 0)

                //Might Fix Incompatibilities with odd drivers
                GL20.glValidateProgram(shader!!)
                if (GL20.glGetProgrami(shader!!, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
                    ShaderLib.exitDraw()
                    return
                }

                GL11.glDisable(GL11.GL_BLEND);
                ShaderLib.screenDraw(ShaderLib.getScreenTexture(), GL13.GL_TEXTURE0 + 0)
                ShaderLib.exitDraw()

            }
        }
    }
}