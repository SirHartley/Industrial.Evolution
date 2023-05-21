package indevo.exploration.minefields.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainPlugin;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.abilities.InterdictionPulseAbility;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.minefields.MineBeltTerrainPlugin;
import org.lwjgl.util.vector.Vector2f;

public class InterdictionPulseAbilityListener extends BaseCampaignEventListener {

    public static void register() {
        Global.getSector().addTransientListener(new InterdictionPulseAbilityListener(false));
    }

    public InterdictionPulseAbilityListener(boolean permaRegister) {
        super(permaRegister);
    }

    public static final float MINE_INTERDICTION_DISABLE_DUR = 5f;

    @Override
    public void reportPlayerDeactivatedAbility(AbilityPlugin ability, Object param) {
        super.reportPlayerDeactivatedAbility(ability, param);

        if (!ability.getSpec().getId().equals(Abilities.INTERDICTION_PULSE)) return;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        float range = InterdictionPulseAbility.getRange(fleet);

        for (CampaignTerrainAPI t : fleet.getContainingLocation().getTerrainCopy()) {
            CampaignTerrainPlugin plugin = t.getPlugin();
            if (plugin instanceof MineBeltTerrainPlugin) {
                MineBeltTerrainPlugin mineBeltTerrainPlugin = (MineBeltTerrainPlugin) plugin;

                if (circlesIntersect(t.getLocation(), mineBeltTerrainPlugin.params.middleRadius + mineBeltTerrainPlugin.params.bandWidthInEngine / 2, fleet.getLocation(), range)) {
                    mineBeltTerrainPlugin.generateDisabledArea(fleet, range, MINE_INTERDICTION_DISABLE_DUR);
                }
            }
        }
    }

    public static boolean circlesIntersect(Vector2f center1, float radius1, Vector2f center2, float radius2) {
        float distance = Misc.getDistance(center1, center2);
        return distance <= (radius1 + radius2);
    }
}
