package indevo.abilities.skills.scripts

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.CampaignState
import com.fs.starfarer.campaign.econ.Market
import com.fs.starfarer.campaign.ui.marketinfo.IndustryListPanel
import com.fs.state.AppDriver
import indevo.ids.Ids
import indevo.utils.helper.ReflectionUtils
import org.lazywizard.lazylib.MathUtils

class MicromanagementSkillEffectScript : EveryFrameScript {

    var frames = 0
    var newIndustryPanel: CustomPanelAPI? = null

    companion object {
        var reset = false

        @JvmStatic
        fun register(){
            if(!Global.getSector().hasScript(MicromanagementSkillEffectScript::class.java)) Global.getSector().addScript(
                MicromanagementSkillEffectScript()
            );
        }

        @JvmStatic
        fun getMarket() : MarketAPI? {
            var state = AppDriver.getInstance().currentState

            if (state !is CampaignState) return null;

            var core: UIPanelAPI? = null

            var managementPanel: UIPanelAPI? = null
            var industryPanel: UIPanelAPI? = null

            var dialog = ReflectionUtils.invoke("getEncounterDialog", state)
            if (dialog != null) {
                core = ReflectionUtils.invoke("getCoreUI", dialog) as UIPanelAPI?
            }

            if (core == null) {
                core = ReflectionUtils.invoke("getCore", state) as UIPanelAPI?
            }

            if (core != null) {
                val tab = ReflectionUtils.invoke("getCurrentTab", core)
                if (tab is UIPanelAPI) {
                    val intelCore = tab.getChildrenCopy()?.find { ReflectionUtils.hasMethodOfName("getOutpostPanelParams", it) }
                    if (intelCore is UIPanelAPI) {
                        val intelSubcore = intelCore.getChildrenCopy().find { ReflectionUtils.hasMethodOfName("showOverview", it) }
                        if (intelSubcore is UIPanelAPI) {
                            managementPanel = intelSubcore.getChildrenCopy().find { ReflectionUtils.hasMethodOfName("recreateWithEconUpdate", it) } as UIPanelAPI?
                            if (managementPanel != null) {
                                industryPanel = managementPanel.getChildrenCopy().find { it is IndustryListPanel } as? IndustryListPanel
                            }
                        }
                    }
                }
            }

            if (industryPanel != null) {
                return ReflectionUtils.get("market", industryPanel) as MarketAPI
            }

            return null;
        }

        private fun UIPanelAPI.getChildrenCopy(): List<UIComponentAPI> {
            return ReflectionUtils.invoke("getChildrenCopy", this) as List<UIComponentAPI>
        }
    }

    init {
        reset = false
    }

    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return true
    }

    override fun advance(amount: Float) {
        if (Global.getSector().memoryWithoutUpdate.getBoolean("\$IndEvo_BaseRemoteAllowed")) return

        frames++
        frames = MathUtils.clamp(frames, 0, 10)

        if (!Global.getSector().isPaused) return
        if (frames < 2) return

        val market = getMarket();

        if (market != null){
            if (market.admin.stats.hasSkill(Ids.MICROMANAGEMENT) && (AdminGovernTimeTracker.getInstanceOrRegister().getValueForMarket(market.id) > 93 || Global.getSettings().isDevMode)) {
                Global.getSettings().setBoolean("allowRemoteIndustryItemManagement", true)
            } else Global.getSettings().setBoolean("allowRemoteIndustryItemManagement", false);
        }
    }
}



