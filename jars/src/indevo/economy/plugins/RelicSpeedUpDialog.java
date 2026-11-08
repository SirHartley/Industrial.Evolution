package indevo.economy.plugins;

import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.economy.RelicSpeedUpTarget;
import indevo.utils.helper.RelicHelper;

import static indevo.utils.helper.StringHelper.getDayOrDays;

public class RelicSpeedUpDialog extends BaseCustomDialogDelegate {

    public static final float WIDTH = 500f;
    public static final float HEIGHT = 200f;

    private final RelicSpeedUpTarget target;
    private final Runnable onClose;
    private final boolean canPay;

    private CustomDialogCallback callback;
    private boolean open = false;

    public RelicSpeedUpDialog(RelicSpeedUpTarget target, Runnable onClose) {
        this.target = target;
        this.onClose = onClose;
        this.canPay = RelicHelper.canAfford(target.getMarket(), target.getSpeedUpCost());
    }

    public static void show(DialogCreatorUI ui, RelicSpeedUpTarget target) {
        ui.showDialog(WIDTH, HEIGHT, new RelicSpeedUpDialog(target, null));
    }

    public static void addBody(TooltipMakerAPI info, RelicSpeedUpTarget target) {
        float opad = 10f;
        float spad = 5f;
        int left = target.getSpeedUpDaysLeft();
        int after = target.getSpeedUpDaysAfter();

        info.addSectionHeading(target.getSpeedUpLabel(), Alignment.MID, 0f);
        info.addPara("Available %s every %s " + target.getSpeedUpScope() + ".", spad, Misc.getHighlightColor(),
                "once", Math.round(RelicSpeedUpTarget.COOLDOWN_DAYS) + " days");
        info.addPara("Time remaining: %s", opad, Misc.getHighlightColor(), left + " " + getDayOrDays(left));
        info.addPara("With relics: %s", spad, Misc.getPositiveHighlightColor(), after + " " + getDayOrDays(after));
        RelicHelper.addCostLine(info, target.getMarket(), target.getSpeedUpCost(), spad);
    }

    public static void addTooltip(TooltipMakerAPI tooltip, RelicSpeedUpTarget target) {
        float opad = 10f;

        tooltip.addPara(getEffect(target), 0f);
        RelicHelper.addCostLine(tooltip, target.getMarket(), target.getSpeedUpCost(), opad);

        float cooldown = target.getSpeedUpCooldownLeft();
        if (cooldown > 0f) {
            int days = (int) Math.ceil(cooldown);
            tooltip.addPara("Unavailable for another %s.", opad, Misc.getNegativeHighlightColor(), days + " " + getDayOrDays(days));
        } else if (!target.canSpeedUp()) {
            tooltip.addPara("Too few days left.", Misc.getNegativeHighlightColor(), opad);
        }

        String lore = target.getSpeedUpLore();
        if (lore != null) tooltip.addPara(lore, Misc.getGrayColor(), opad);
    }

    public static boolean isAvailable(RelicSpeedUpTarget target) {
        return target.canSpeedUp() && target.getSpeedUpCooldownLeft() <= 0f;
    }

    public static boolean apply(RelicSpeedUpTarget target) {
        int cost = target.getSpeedUpCost();
        if (!isAvailable(target) || !RelicHelper.canAfford(target.getMarket(), cost)) return false;

        int days = target.getSpeedUpDaysLeft() - target.getSpeedUpDaysAfter();
        RelicHelper.take(target.getMarket(), cost);
        target.speedUp(days);
        target.startSpeedUpCooldown();
        return true;
    }

    private static String getEffect(RelicSpeedUpTarget target) {
        return target.isSpeedUpInstant() ? "Completes recharging immediately." : "Halves the remaining time.";
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.callback = callback;
        this.open = true;

        TooltipMakerAPI info = panel.createUIElement(WIDTH, HEIGHT, false);
        info.setParaInsigniaLarge();
        addBody(info, target);
        panel.addUIElement(info).inTL(0f, 0f);
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
        if (canPay) apply(target);
        if (onClose != null) onClose.run();
    }

    @Override
    public void customDialogCancel() {
        open = false;
        if (onClose != null) onClose.run();
    }
}
