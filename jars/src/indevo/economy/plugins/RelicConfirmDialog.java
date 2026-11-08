package indevo.economy.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.RelicHelper;

import java.awt.Color;

public abstract class RelicConfirmDialog extends BaseCustomDialogDelegate {

    public static final float WIDTH = 450f;

    private static final float OPAD = 10f;
    private static final float BUTTON_H = 25f;
    private static final float COUNTER_PAD = 4f;

    protected final MarketAPI market;
    protected final int cost;
    private final boolean canPay;
    private final Runnable onClose;

    private CustomDialogCallback callback;
    private boolean open = false;

    protected RelicConfirmDialog(MarketAPI market, int cost, Runnable onClose) {
        this.market = market;
        this.cost = cost;
        this.onClose = onClose;
        this.canPay = RelicHelper.canAfford(market, cost);
    }

    protected abstract void addDescription(TooltipMakerAPI info);

    protected abstract void apply();

    public static void addRequirement(TooltipMakerAPI info, MarketAPI market, int cost) {
        Color bad = Misc.getNegativeHighlightColor();
        String name = RelicHelper.getName();
        int available = RelicHelper.getAvailable(market);

        info.setParaInsigniaLarge();
        info.addPara("This action requires %s.", 0f, Misc.getHighlightColor(), cost + " " + name);
        if (available <= 0) info.addPara("No " + name + " available", bad, OPAD);
        else if (available < cost) info.addPara("Only %s " + name + " available", OPAD, bad, bad, available + "");
        info.setParaFontDefault();
    }

    public static void addCounter(TooltipMakerAPI info, MarketAPI market, float pad) {
        info.setParaSmallInsignia();
        info.addPara(RelicHelper.getName() + ": %s", pad, Misc.getGrayColor(), Misc.getHighlightColor(), RelicHelper.getAvailable(market) + "");
        info.setParaFontDefault();
    }

    public void show(DialogCreatorUI ui) {
        ui.showDialog(WIDTH, getHeight(), this);
    }

    public void show(InteractionDialogAPI dialog) {
        dialog.showCustomDialog(WIDTH, getHeight(), this);
    }

    public float getHeight() {
        CustomPanelAPI scratch = Global.getSettings().createCustom(WIDTH, 1000f, null);
        TooltipMakerAPI info = scratch.createUIElement(WIDTH, 1000f, false);
        addBody(info);
        return info.getHeightSoFar() + OPAD;
    }

    private void addBody(TooltipMakerAPI info) {
        addRequirement(info, market, cost);
        addDescription(info);
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.callback = callback;
        this.open = true;

        float h = panel.getPosition().getHeight();
        TooltipMakerAPI info = panel.createUIElement(WIDTH, h, false);
        addBody(info);
        panel.addUIElement(info).inTL(0f, 0f);

        // the dialog puts its buttons in a row below this panel, the counter goes left of them like the vanilla story point dialog
        TooltipMakerAPI counter = panel.createUIElement(WIDTH / 2f, BUTTON_H, false);
        addCounter(counter, market, COUNTER_PAD);
        panel.addUIElement(counter).inBL(0f, -(OPAD + BUTTON_H));
    }

    public boolean isOpen() {
        return open;
    }

    public void cancel() {
        callback.dismissCustomDialog(1);
    }

    @Override
    public boolean hasCancelButton() {
        return canPay;
    }

    @Override
    public String getConfirmText() {
        return canPay ? "Confirm" : "Return";
    }

    @Override
    public String getCancelText() {
        return "Cancel";
    }

    @Override
    public void customDialogConfirm() {
        open = false;
        if (canPay && RelicHelper.canAfford(market, cost)) apply();
        if (onClose != null) onClose.run();
    }

    @Override
    public void customDialogCancel() {
        open = false;
        if (onClose != null) onClose.run();
    }
}
