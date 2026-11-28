package indevo.utils.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.ui.Fonts;
import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;

public class DevZoomReadout implements CampaignUIRenderingListener {

    // vanilla maxCampaignZoom, fixed so 100% stays the vanilla limit when a mod raises it
    private static final float VANILLA_MAX_ZOOM = 3f;
    private static final float RIGHT_PAD = 2f;
    private static final float TOP = 24f;
    private static final Color TEXT = new Color(255, 255, 255, 100);

    private LazyFont.DrawableString text;
    private LazyFont.DrawableString shadow;
    private boolean failed = false;
    private int shown = -1;

    public static void register() {
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (manager.hasListenerOfClass(DevZoomReadout.class)) return;
        manager.addListener(new DevZoomReadout(), true);
    }

    @Override
    public void renderInUICoordsBelowUI(ViewportAPI viewport) {
    }

    @Override
    public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {
    }

    // fires in CampaignEngine.renderInScreenCoords, right before vanilla draws the fps line in the same coords
    @Override
    public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) {
        if (!Global.getSettings().isDevMode() || !load()) return;

        int percent = Math.round(Global.getSector().getCampaignUI().getZoomFactor() / VANILLA_MAX_ZOOM * 100f);
        if (percent != shown) {
            shown = percent;
            String s = String.format("Zoom: %3d%%", percent);
            text.setText(s);
            shadow.setText(s);
        }

        float x = Global.getSettings().getScreenWidth() - text.getWidth() - RIGHT_PAD;
        float y = Global.getSettings().getScreenHeight() - TOP;
        shadow.draw(x + 1f, y - 1f);
        text.draw(x, y);
    }

    private boolean load() {
        if (text != null) return true;
        if (failed) return false;

        try {
            LazyFont font = LazyFont.loadFont(Fonts.DEFAULT_SMALL);
            text = font.createText("", TEXT, font.getBaseHeight());
            shadow = font.createText("", Color.BLACK, font.getBaseHeight());
            return true;
        } catch (Exception e) {
            failed = true;
            Global.getLogger(DevZoomReadout.class).warn("IndEvo: no font for the dev zoom readout", e);
            return false;
        }
    }
}
