package indevo.economy.plugins;

import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.economy.RelicSpeedUpTarget;
import indevo.utils.helper.RelicHelper;

import static indevo.utils.helper.StringHelper.getDayOrDays;

public class RelicSpeedUpDialog extends RelicConfirmDialog {

    private static final float OPAD = 10f;

    private final RelicSpeedUpTarget target;

    public RelicSpeedUpDialog(RelicSpeedUpTarget target, Runnable onClose) {
        super(target.getMarket(), target.getSpeedUpCost(), onClose);
        this.target = target;
    }

    public static void show(DialogCreatorUI ui, RelicSpeedUpTarget target) {
        new RelicSpeedUpDialog(target, null).show(ui);
    }

    public static void addBody(TooltipMakerAPI info, RelicSpeedUpTarget target) {
        addRequirement(info, target.getMarket(), target.getSpeedUpCost());
        addEffect(info, target);
        addCounter(info, target.getMarket(), OPAD);
    }

    public static void addTooltip(TooltipMakerAPI tooltip, RelicSpeedUpTarget target) {
        addTooltip(tooltip, target, true);
    }

    // panel buttons show the cost and the blocked reason themselves
    public static void addPanelTooltip(TooltipMakerAPI tooltip, RelicSpeedUpTarget target) {
        addTooltip(tooltip, target, false);
    }

    private static void addTooltip(TooltipMakerAPI tooltip, RelicSpeedUpTarget target, boolean full) {
        tooltip.addPara(getEffect(target), 0f);
        if (full) RelicHelper.addCostLine(tooltip, target.getMarket(), target.getSpeedUpCost(), OPAD);
        tooltip.addPara("Can be used once every %s " + target.getSpeedUpScope() + ".", OPAD, Misc.getHighlightColor(),
                Math.round(RelicSpeedUpTarget.COOLDOWN_DAYS) + " days");

        if (full) addBlocker(tooltip, target);

        String lore = target.getSpeedUpLore();
        if (lore != null) tooltip.addPara(lore, Misc.getGrayColor(), OPAD);
    }

    private static void addBlocker(TooltipMakerAPI tooltip, RelicSpeedUpTarget target) {
        float cooldown = target.getSpeedUpCooldownLeft();
        if (cooldown > 0f) {
            int days = (int) Math.ceil(cooldown);
            tooltip.addPara("Unavailable for another %s.", OPAD, Misc.getNegativeHighlightColor(), days + " " + getDayOrDays(days));
        } else if (!target.canSpeedUp()) {
            tooltip.addPara("Too few days left.", Misc.getNegativeHighlightColor(), OPAD);
        }
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

    private static void addEffect(TooltipMakerAPI info, RelicSpeedUpTarget target) {
        int left = target.getSpeedUpDaysLeft();
        int after = target.getSpeedUpDaysAfter();

        info.addPara(getEffect(target) + " Time remaining: %s -> %s.", OPAD, Misc.getHighlightColor(),
                left + " " + getDayOrDays(left), after + " " + getDayOrDays(after));
    }

    private static String getEffect(RelicSpeedUpTarget target) {
        return target.isSpeedUpInstant() ? "Completes recharging immediately." : "Halves the remaining time.";
    }

    @Override
    protected void addDescription(TooltipMakerAPI info) {
        addEffect(info, target);
    }

    @Override
    protected void apply() {
        apply(target);
    }
}
