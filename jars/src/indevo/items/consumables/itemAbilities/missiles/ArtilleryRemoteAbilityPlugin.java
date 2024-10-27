package indevo.items.consumables.itemAbilities.missiles;

import com.fs.graphics.M;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.artillery.scripts.CampaignAttackScript;
import indevo.industries.artillery.terrain.ArtilleryTerrain;
import indevo.items.consumables.entities.ExplosiveMissileEntityPlugin;
import indevo.items.consumables.particles.SmoothFadingParticleRenderer;
import indevo.utils.helper.StringHelper;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;

public class ArtilleryRemoteAbilityPlugin extends BaseMissileConsumableAbilityPlugin {

    public static final String HAS_BEEN_USED_KEY = "$IndEvo_ArtilleryRemote_hasBeenUsed";

    @Override
    protected void activateImpl() {
        Vector2f mousePos = new Vector2f(
                Global.getSector().getViewport().convertScreenXToWorldX(Global.getSettings().getMouseX()),
                Global.getSector().getViewport().convertScreenYToWorldY(Global.getSettings().getMouseY()));

        //get artillery station
        //Force target loc (may have to do fake entity)

        LocationAPI loc = entity.getContainingLocation();

        for (CampaignTerrainAPI terrain : loc.getTerrainCopy()) {
            CampaignTerrainPlugin plugin = terrain.getPlugin();
            if (plugin instanceof ArtilleryTerrain) {
                CampaignAttackScript script = ((ArtilleryTerrain) plugin).getScript(((ArtilleryTerrain) plugin).getRelatedEntity());
                if (plugin.containsEntity(entity) && !script.disabled) {
                    SectorEntityToken t = loc.createToken(mousePos);
                    loc.addEntity(t);
                    Misc.fadeAndExpire(t, 1f);
                    script.forceTarget(t, 0.1f);
                    break;
                }
            }
        }

        LunaCampaignRenderingPlugin renderer = new SmoothFadingParticleRenderer(4f, Global.getSettings().getSpriteName("fx", "IndEvo_danger_notif"), 100f, mousePos, 0f, Misc.getNegativeHighlightColor(), CampaignEngineLayers.TERRAIN_5);
        LunaCampaignRenderer.addRenderer(renderer);

        Global.getSector().getMemoryWithoutUpdate().set(HAS_BEEN_USED_KEY, true);
    }

    @Override
    public boolean isUsable() {
        boolean hasArty = entity.getContainingLocation().hasTag(Ids.TAG_SYSTEM_HAS_ARTILLERY);

        if (!hasArty) return false; //return first because the follow up is a heavy check

        //check if in arty terrain
        //if yes, get arty script through that
        //if ready, fire

        boolean canFire = false;
        for (CampaignTerrainAPI terrain : entity.getContainingLocation().getTerrainCopy()) {
            CampaignTerrainPlugin plugin = terrain.getPlugin();
            if (plugin instanceof ArtilleryTerrain) {
                CampaignAttackScript script = ((ArtilleryTerrain) plugin).getScript(((ArtilleryTerrain) plugin).getRelatedEntity());
                if (plugin.containsEntity(entity) && !script.disabled) {
                    canFire = true;
                    break;
                }
            }
        }

        return super.isUsable() && canFire;
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        float opad = 10f;
        float spad = 3f;
        Color hl = Misc.getHighlightColor();

        boolean knowsEffect = Global.getSector().getMemoryWithoutUpdate().getBoolean(HAS_BEEN_USED_KEY);
        boolean hasArty = entity.getContainingLocation().hasTag(Ids.TAG_SYSTEM_HAS_ARTILLERY);

        if (knowsEffect) {
            tooltip.addPara("Forces a nearby artillery to %s. The deployment time is slightly unreliable due to age and loading cycles, and it will lock itself after one use.", opad, hl, "fire at the selected location");

            if (!hasArty)
                tooltip.addPara("No functioning artillery platform in range.", opad, Misc.getNegativeHighlightColor());
        } else {
            tooltip.addPara("The remote sometimes beeps in the presence of old domain drones.", opad);
            if (!hasArty)
                tooltip.addPara("It seems to be missing something to connect to.", opad, Misc.getNegativeHighlightColor());
        }
    }
}