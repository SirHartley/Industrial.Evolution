package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.changeling.conditions.GovernmentCondition;
import indevo.industries.changeling.dialogue.ChangelingIndustryDialogueDelegate;
import indevo.industries.changeling.industry.ChangelingTarget;
import indevo.industries.changeling.industry.Specialization;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.industries.changeling.industry.population.Governments;
import indevo.industries.changeling.industry.population.UnderworldSubIndustry;
import indevo.utils.ui.UiHost;

import java.util.ArrayList;
import java.util.List;

// test only: the government and specialization picker views, audited and captured, and the population tooltip forms
public final class GovernmentUiChecks {
    private static final float TIP_W = 400f;
    private static final float TIP_GAP = 20f;

    private GovernmentUiChecks() {
    }

    // hover form left, F1 form right, built by the industry's own createTooltip
    private static class TooltipPair extends BaseCustomDialogDelegate {
        private final Industry industry;
        private final Runnable onLeave;

        TooltipPair(Industry industry, Runnable onLeave) {
            this.industry = industry;
            this.onLeave = onLeave;
        }

        @Override
        public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
            for (int i = 0; i < 2; i++) {
                TooltipMakerAPI element = panel.createUIElement(TIP_W, panel.getPosition().getHeight(), false);
                industry.createTooltip(Industry.IndustryTooltipMode.NORMAL, element, i == 1);
                panel.addUIElement(element).inTL(i * (TIP_W + TIP_GAP), 0f);
            }
        }

        @Override
        public boolean hasCancelButton() {
            return false;
        }

        @Override
        public void customDialogConfirm() {
            onLeave.run();
        }

        @Override
        public void customDialogCancel() {
            onLeave.run();
        }
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(GovernmentUiChecks::setup);
        // the blocked reasons are the mod's own texts, the board shows the long one cut on the tile and whole in its tooltip
        steps.add(c -> UiAudit.acceptLong("Requires Rich Farmland or better", "Can not build a bunker in lava", "Unavailable on space stations"));

        UiViews.view(steps, "gov-open", c -> {
            credits(110000f);
            open(c, GovernmentCondition.TARGET, Industries.POPULATION, null);
        });
        UiViews.view(steps, "gov-picked", c -> open(c, GovernmentCondition.TARGET, Industries.POPULATION, "helldivers"));
        UiViews.view(steps, "gov-clicked", c -> open(c, GovernmentCondition.TARGET, Industries.POPULATION, null),
                (c, panel) -> c.expect(UiAudit.clickRow(panel.content(), "Corporate Governance"), "a click on a tile picks it"));
        UiViews.view(steps, "gov-poor", c -> {
            credits(0f);
            open(c, GovernmentCondition.TARGET, Industries.POPULATION, null);
        });
        UiViews.view(steps, "gov-current", c -> {
            credits(110000f);
            ColonyChecks.government(c.market("a"), "underworld");
            open(c, GovernmentCondition.TARGET, Industries.POPULATION, null);
        });
        UiViews.view(steps, "gov-mining", c -> {
            MarketAPI a = c.market("a");
            a.addIndustry(Industries.MINING);
            a.addIndustry(Industries.REFINING);
            a.reapplyIndustries();
            open(c, Specialization.MINING, Industries.MINING, "volatile_mining");
        });
        UiViews.view(steps, "gov-refining", c -> open(c, Specialization.REFINING, Industries.REFINING, null));
        UiViews.view(steps, "gov-worst", c -> {
            c.market("a").setName("Konstantinopel Orbital Habitat Ring");
            UiAudit.acceptLong("Konstantinopel Orbital Habitat Ring");
            credits(987654321f);
            open(c, GovernmentCondition.TARGET, Industries.POPULATION, "corpos");
        });

        steps.add(c -> {
            MarketAPI a = c.market("a");
            MarketAPI b = c.market("b");
            Industry popA = a.getIndustry(Industries.POPULATION);
            Industry popB = b.getIndustry(Industries.POPULATION);
            c.expect(popA.isTooltipExpandable(), "governed population tooltip is expandable through the patch");
            c.expect(!popB.isTooltipExpandable(), "population without a government keeps the plain tooltip");

            String lore = GovernmentCondition.getDescriptionOverride(popA);
            String hover = GovernmentCondition.getDescriptionOverride(popA, false);
            c.expect(hover != null && hover.contains(a.getName()) && !hover.equals(lore), "hover keeps the vanilla size line (" + hover + ")");
            c.expect(lore != null && lore.equals(GovernmentCondition.getDescriptionOverride(popA, true)), "F1 shows the government lore");
            for (SubIndustryData data : Governments.LIST) {
                if (!Governments.BASE_ID.equals(data.id)) c.expect(!data.effects.isEmpty(), data.name + " has effect lines");
            }

            String plain = tooltip(popB, false);
            c.expect(!plain.contains("Government"), "no Government section without a government");

            String empty = tooltip(popA, false);
            c.expect(empty.contains("Extorted in the past 30 days") && empty.contains("No donations yet, check back later."), "underworld hover keeps its empty extortion part");
            c.expect(!empty.contains("Base stability") && !empty.contains(lore), "underworld hover has no effect lines and no lore");
            String full = tooltip(popA, true);
            c.expect(full.contains("Base stability") && full.contains(lore) && full.contains("Extorted in the past 30 days"), "underworld F1 has lore, effect lines and the ongoing part");

            UnderworldSubIndustry underworld = (UnderworldSubIndustry) GovernmentCondition.getGovernment(a);
            String[] goods = {Commodities.FOOD, Commodities.SUPPLIES, Commodities.DOMESTIC_GOODS, Commodities.DRUGS, Commodities.FUEL, Commodities.ORE,
                    Commodities.METALS, Commodities.HEAVY_MACHINERY, Commodities.ORGANICS, Commodities.VOLATILES};
            for (int i = 0; i < goods.length; i++) underworld.stolenGoodsList.add(new UnderworldSubIndustry.TimedCommodityQuantity(goods[i], 2400f / (i + 1)));

            ColonyChecks.government(b, "helldivers");
            for (String variant : new String[]{"hammerhead_Balanced", "enforcer_Assault"}) {
                Misc.getStorageCargo(b).getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant));
            }
            c.days(1.2f);
        });
        shot(steps, "gov-tip-underworld", c -> tooltips(c, "a"));
        steps.add(c -> {
            Industry popB = c.market("b").getIndustry(Industries.POPULATION);
            String hover = tooltip(popB, false);
            c.note("managed democracy hover: " + hover.replace('\n', '|'));
            c.expect(hover.contains("Extra patrols: 1 ") && hover.contains("Hellpod installation progress"), "managed democracy hover shows patrols and the refit table");
            c.expect(hover.contains("Hammerhead") && hover.contains("Enforcer"), "stored destroyers are in the refit table");
            c.expect(hover.contains("Permanent in "), "hover shows the days until permanent");
            c.expect(!hover.contains("Stability never falls below"), "managed democracy hover has no effect lines");
            c.expect(tooltip(popB, true).contains("Stability never falls below"), "managed democracy F1 has the effect lines");
        });
        shot(steps, "gov-tip-helldivers", c -> tooltips(c, "b"));
        steps.add(c -> ColonyChecks.government(c.market("b"), "warhammer"));
        shot(steps, "gov-tip-forge", c -> tooltips(c, "b"));
        steps.add(c -> c.keepDialog(false));
        return steps;
    }

    static void setup(Ctx c) {
        Colony.pair(c, 3);
        MarketAPI a = c.market("a");
        Colony.jump(a.getContainingLocation(), a.getPrimaryEntity().getLocation().x + 300f, a.getPrimaryEntity().getLocation().y);
        c.note("planet types " + a.getPlanetEntity().getTypeId() + " and " + c.market("b").getPlanetEntity().getTypeId());
    }

    private static void credits(float amount) {
        Global.getSector().getPlayerFleet().getCargo().getCredits().set(amount);
    }

    private static void open(Ctx c, ChangelingTarget target, String industryId, String picked) {
        c.expect(ChangelingIndustryDialogueDelegate.show(c.market("a").getIndustry(industryId), target, picked), "picker opens");
    }

    private static String tooltip(Industry industry, boolean expanded) {
        TooltipMakerAPI tooltip = Ui.tooltip();
        industry.createTooltip(Industry.IndustryTooltipMode.NORMAL, tooltip, expanded);
        return Ui.text(tooltip);
    }

    private static void tooltips(Ctx c, String key) {
        MarketAPI market = c.market(key);
        Industry pop = market.getIndustry(Industries.POPULATION);
        float h = Global.getSettings().getScreenHeight() - 155f;
        c.expect(UiHost.show(market.getPrimaryEntity(), dialog -> dialog.showCustomDialog(2f * TIP_W + TIP_GAP, h, new TooltipPair(pop, dialog::dismiss))), "tooltip pair opens");
    }

    // a view that is only captured: vanilla tooltip content is not a kit panel, the audit does not apply
    private static void shot(List<Step> steps, String name, UiViews.Open open) {
        steps.add(c -> {
            c.keepDialog(true);
            UiAudit.dismissDialog();
            c.frames(10);
        });
        steps.add(c -> {
            open.open(c);
            c.frames(120);
        });
        steps.add(c -> c.shot(name));
        steps.add(c -> {
            UiAudit.Found found = UiAudit.find();
            if (found != null) UiAudit.close(found);
            c.frames(20);
        });
        steps.add(c -> {
            UiAudit.dismissDialog();
            c.frames(5);
        });
    }
}
