package indevo.economy;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

public interface RelicSpeedUpTarget {

    String MEM_COOLDOWN_PREFIX = "$IndEvo_relicSpeedUp_";
    float COOLDOWN_DAYS = 60f;
    int RELICS_PER_DAY = 1;
    int RELICS_PER_DAY_INSTANT = 3;

    MarketAPI getMarket();

    String getSpeedUpId();

    String getSpeedUpLabel();

    String getSpeedUpLore();

    int getSpeedUpDaysLeft();

    int getSpeedUpDaysTotal();

    void speedUp(int days);

    default boolean isSpeedUpInstant() {
        return false;
    }

    default String getSpeedUpScope() {
        return "on this timer";
    }

    default boolean canSpeedUp() {
        return getSpeedUpDaysLeft() > 1;
    }

    default int getSpeedUpDaysAfter() {
        int left = getSpeedUpDaysLeft();
        return isSpeedUpInstant() ? 0 : left - left / 2;
    }

    default int getSpeedUpCost() {
        return getSpeedUpDaysLeft() * (isSpeedUpInstant() ? RELICS_PER_DAY_INSTANT : RELICS_PER_DAY);
    }

    default float getSpeedUpCooldownLeft() {
        MemoryAPI mem = getMarket().getMemoryWithoutUpdate();
        String key = MEM_COOLDOWN_PREFIX + getSpeedUpId();
        return mem.contains(key) ? mem.getExpire(key) : 0f;
    }

    default void startSpeedUpCooldown() {
        getMarket().getMemoryWithoutUpdate().set(MEM_COOLDOWN_PREFIX + getSpeedUpId(), true, COOLDOWN_DAYS);
    }
}
