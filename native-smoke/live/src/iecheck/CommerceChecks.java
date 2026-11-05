package iecheck;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import indevo.config.PlayerSettings;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.patches.CommerceBalancePatch;
import lunalib.backend.ui.settings.LunaSettingsLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CommerceChecks {
    private CommerceChecks() {
    }

    private static float percentSum(MutableStat stat) {
        float sum = 0f;
        for (Map.Entry<String, MutableStat.StatMod> mod : stat.getPercentMods().entrySet()) sum += mod.getValue().getValue();
        return sum;
    }

    static void setBalance(boolean on) throws Exception {
        LunaSettingsLoader.getSettings().get(PlayerSettings.MOD_ID).put(Setting.COMMERCE_BALANCE_CHANGES.id, on);
        PlayerSettings.reload();
    }

    private static void balanceChecks(Ctx c, boolean on) {
        MarketAPI a = c.market("a");
        MarketAPI b = c.market("b");
        Industry commerce = a.getIndustry(Industries.COMMERCE);

        Industry second = b.instantiateIndustry(Industries.COMMERCE);
        if (on) {
            c.expect(!second.isAvailableToBuild(), "second commerce in system not buildable");
            c.expect(CommerceBalancePatch.ONE_PER_SYSTEM.equals(second.getUnavailableReason()), "unavailable reason: " + second.getUnavailableReason());
        } else {
            c.expect(second.isAvailableToBuild(), "setting off: second commerce buildable (vanilla)");
        }

        float tariff = a.getTariff().getModifiedValue();
        float income = percentSum(a.getIncomeMult());
        commerce.setAICoreId(Commodities.ALPHA_CORE);
        a.reapplyIndustries();
        float withCore = a.getTariff().getModifiedValue();
        float incomeCore = percentSum(a.getIncomeMult());
        if (on) {
            c.near(withCore, tariff * 0.75f, 0.001f, "1610 alpha core cuts tariff 25%");
            c.near(incomeCore, income, 0.01f, "alpha core gives no income bonus");
        } else {
            c.near(withCore, tariff, 0.001f, "setting off: alpha core leaves tariff");
            c.near(incomeCore, income + 25f, 0.01f, "setting off: alpha core gives vanilla +25% income");
        }

        commerce.setAICoreId(null);
        a.reapplyIndustries();
        c.near(a.getTariff().getModifiedValue(), tariff, 0.001f, "1609 tariff cut goes with the core");

        commerce.setImproved(true);
        a.reapplyIndustries();
        c.near(percentSum(a.getIncomeMult()), income + (on ? 10f : 25f), 0.01f, "improve income bonus");
        commerce.setImproved(false);
        a.reapplyIndustries();

        commerce.setSpecialItem(new SpecialItemData(Items.DEALMAKER_HOLOSUITE, null));
        a.reapplyIndustries();
        c.near(percentSum(a.getIncomeMult()), income + (on ? 25f : 50f), 0.01f, "holosuite income bonus");
        commerce.setSpecialItem(null);
        a.reapplyIndustries();
        c.near(percentSum(a.getIncomeMult()), income, 0.01f, "bonuses gone");
    }

    private static int stock(MarketAPI market) {
        SubmarketAPI open = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
        if (open == null) return -1;
        ((BaseSubmarketPlugin) open.getPlugin()).updateCargoPrePlayerInteraction();
        return Math.round(open.getCargo().getTotalCrew() + open.getCargo().getSpaceUsed() + open.getCargo().getFuel()) + open.getCargo().getMothballedShips().getNumMembers();
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            c.market("a").addIndustry(Industries.COMMERCE);
            c.market("a").reapplyIndustries();
            c.expect(c.market("a").hasIndustry(Industries.COMMERCE), "commerce built on A");
            c.expect(c.market("a").getSubmarket(Submarkets.SUBMARKET_OPEN) != null, "commerce adds open market");
        });
        steps.add(c -> {
            c.expect(CommerceBalancePatch.isActive(), "balance setting on by default");
            balanceChecks(c, true);
        });
        steps.add(c -> {
            setBalance(false);
            c.expect(!CommerceBalancePatch.isActive(), "setting off read back");
            balanceChecks(c, false);
            setBalance(true);
            c.expect(CommerceBalancePatch.isActive(), "setting on read back");
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            float tariff = a.getTariff().getModifiedValue();
            a.getIndustry(Industries.COMMERCE).setAICoreId(Commodities.ALPHA_CORE);
            a.reapplyIndustries();
            a.removeIndustry(Industries.COMMERCE, null, false);
            a.reapplyIndustries();
            c.near(a.getTariff().getModifiedValue(), tariff, 0.001f, "1609 tariff cut goes with the industry");
            a.addIndustry(Industries.COMMERCE);
            a.reapplyIndustries();
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            c.market("b").addIndustry(Ids.SENATE);
            a.addCondition("IndEvo_edict_open_market");
            a.reapplyConditions();
            a.removeCondition("IndEvo_edict_open_market");
            a.reapplyConditions();
            c.expect(a.getSubmarket(Submarkets.SUBMARKET_OPEN) != null, "1505 open market stays after edict ends");
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            c.put("stock", stock(a));
            c.put("index", a.getIndustries().indexOf(a.getIndustry(Industries.COMMERCE)));
            c.note("open market stock " + c.get("stock") + ", list index " + c.get("index"));
            c.reload();
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            c.note("reloaded");
            c.expect(a.getIndustries().indexOf(a.getIndustry(Industries.COMMERCE)) == (Integer) c.get("index"), "1611 commerce keeps its list place");
            c.expect(stock(a) == (Integer) c.get("stock"), "1611 open market stock unchanged after reload (" + stock(a) + ")");
            a.getIndustry(Industries.COMMERCE).setDisrupted(30f);
            c.put("disrupted", a.getIndustry(Industries.COMMERCE).getDisruptedDays());
            c.reload();
        });
        steps.add(c -> {
            Industry commerce = c.market("a").getIndustry(Industries.COMMERCE);
            c.expect(commerce.isDisrupted(), "1611 still disrupted after reload (" + commerce.getDisruptedDays() + " days)");
            c.near(commerce.getDisruptedDays(), (Float) c.get("disrupted"), 2f, "1611 disruption days kept");
        });
        return steps;
    }
}
