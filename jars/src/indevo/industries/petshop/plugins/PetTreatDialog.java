package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SpecialItemPlugin.RightClickActionHelper;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.PetLongevity;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.utils.ui.*;

import java.awt.*;
import java.util.List;
import java.util.*;

// longevity treatment popup: a syringe from the cargo adds its fixed cycles, Nomios sells 1 to 5 cycles for materials
public class PetTreatDialog extends BaseCustomDialogDelegate {

    static final float W = 560f;
    static final float HEAD_Y = 34f;
    static final float ROW_H = 48f;
    static final float ROW_PITCH = 52f;
    static final float ROW_GAP = 4f;
    static final int ROWS_SHOWN = 5;
    static final float NAME_X = 54f;
    static final float LINE_H = 18f;
    static final float REASON_H = 17f;
    static final float TOGGLE_H = 26f;
    static final float TOGGLE_GAP = 5f;
    static final float TIP_W = 300f;
    static final String PETS = "pets";

    static final String PET = "Pet";
    static final String AGE = "Age";
    static final String LIFE = "Life left";
    static final String TREATMENTS = "Treatments";
    static final String[] COLUMNS = {PET, AGE, LIFE, TREATMENTS};
    static final float[] COLUMN_W = {200f, 80f, 110f, 90f};
    static final Alignment[] COLUMN_ALIGN = {Ui.LEFT, Ui.LEFT, Ui.LEFT, Ui.RIGHT};
    static final String[] COLUMN_TIPS = {null, null, "An estimate. The real life span can be a little shorter or longer.", "Longevity treatments this pet has had."};
    static final float[] DOWN = {0f, 0f, 8f, 0f, 4f, 5f};
    static final float[] UP = {0f, 5f, 8f, 5f, 4f, 0f};

    static final String FAVOURITE = "Favourite";
    static final String TREATED = "Treated";
    static final List<UiFilter.Item> TOGGLES = List.of(new UiFilter.Item(FAVOURITE, 88f), new UiFilter.Item(TREATED, 78f));

    static final String[] MATERIALS = {Commodities.ORGANS, Commodities.DRUGS};
    static final int[] PRICES = {PetConstants.NOMIOS_ORGANS, PetConstants.NOMIOS_DRUGS};

    static final String NOMIOS_IN = "An attendant in grey shows you to a room that smells of nothing at all. A slate on the table lists what a treatment requires. "
            + "Nobody says what the materials are for.";
    static final String NOMIOS_DONE = "The attendant takes %1$s and the materials through a door without a handle. Some time later the door opens again "
            + "and %1$s is returned to you.";
    static final String NOMIOS_BYE = "\"We look forward to your next visit, Captain.\"";
    static final String NOMIOS_CANCEL = "The attendant inclines their head. The slate goes dark.";

    private final RightClickActionHelper syringe;
    private final InteractionDialogAPI market;
    private final Map<String, MemoryAPI> memoryMap;
    private final List<Pet> pets;
    private final UiScroll.Offsets scroll = new UiScroll.Offsets();
    private final UiFilter.State filter = new UiFilter.State(LIFE);
    // nomios: the most cycles the fleet can pay now, 0 for none
    private final int affordable;
    private InteractionDialogAPI dialog;
    private UiRoot root;
    private Pet picked;
    private String sort = LIFE;
    private boolean reversed = false;
    private int cycles = 1;

    private PetTreatDialog(RightClickActionHelper syringe, InteractionDialogAPI market, Map<String, MemoryAPI> memoryMap) {
        this.syringe = syringe;
        this.market = market;
        this.memoryMap = memoryMap;
        this.pets = PetLongevity.withFleet();
        this.affordable = market != null ? affordable() : 0;
        List<Pet> shown = shown();
        this.picked = shown.isEmpty() ? null : shown.get(0);
    }

    // the syringe is taken on Confirm, so Cancel leaves it in the cargo
    public static PetTreatDialog syringe(RightClickActionHelper helper) {
        return new PetTreatDialog(helper, null, null);
    }

    public static PetTreatDialog nomios(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        return new PetTreatDialog(null, dialog, memoryMap);
    }

    // callers check PetLongevity.hasPetWithFleet first, the popup never opens empty
    public void show() {
        if (picked == null) throw new IllegalStateException("IndEvo longevity treatment opened without a pet with the fleet");

        if (isNomios()) {
            dialog = market;
            market.getTextPanel().addPara(NOMIOS_IN);
            market.showCustomDialog(W, height(), this);
            return;
        }

        PetStatusManager manager = PetStatusManager.getInstance();
        manager.holdMessages(Global.getSector().getCampaignUI().getCurrentInteractionDialog());
        UiHost.over(d -> {
            dialog = d;
            d.showCustomDialog(W, height(), this);
        }, manager::releaseMessages);
    }

    private boolean isNomios() {
        return market != null;
    }

    // the filter row only shows while the list scrolls
    private boolean hasFilter() {
        return pets.size() > ROWS_SHOWN;
    }

    private float listTop() {
        return HEAD_Y + Ui.HEADING_H + 4f + (hasFilter() ? UiFilter.H + UiFilter.BELOW : 0f) + Ui.HEADING_H + 4f;
    }

    private float listHeight() {
        return Math.min(ROWS_SHOWN, pets.size()) * ROW_PITCH - ROW_GAP;
    }

    // the popup can not grow, so the treatment block keeps its place when the filter fold opens
    private float treatTop() {
        return listTop() + listHeight() + PetsPanel.HEAD_GAP;
    }

    private float height() {
        float y = treatTop() + Ui.HEADING_H + 4f;
        if (!isNomios()) return y + LINE_H + 4f + UiData.COST_ROW_H + 4f + REASON_H;
        return y + LINE_H + 8f + TOGGLE_H + 4f + REASON_H + 8f + Ui.HEADING_H + 4f + 3 * UiData.COST_ROW_H;
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        root = new UiRoot(panel, scroll, this::build);
        root.refresh();
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return new BaseCustomUIPanelPlugin() {
            @Override
            public void advance(float amount) {
                if (root != null && filter.poll()) filtered();
            }
        };
    }

    private void filtered() {
        scroll.reset(PETS);
        root.refresh();
    }

    private void refresh() {
        root.refresh();
    }

    private void build(CustomPanelAPI p, float w, float h) {
        Ui.text(p, 0f, 0f, w, isNomios() ? "Longevity treatment" : "Use Longevity Syringe", Ui.INS21, Ui.text(), Ui.LEFT);
        String count = String.valueOf(pets.size());
        Ui.heading(p, 0f, HEAD_Y, w, "Pets with the fleet", count, count);

        float y = HEAD_Y + Ui.HEADING_H + 4f;
        float lw = hasFilter() ? w - UiScroll.GUTTER : w;
        if (hasFilter()) y += UiFilter.bar(p, 0f, y, filter, "Name or species", "Filters of this list.", TOGGLES, null, List.of(), this::filtered, this::refresh) + UiFilter.BELOW;
        addHead(p, y, lw);
        y += Ui.HEADING_H + 4f;

        List<Pet> shown = shown();
        if (!shown.isEmpty() && !shown.contains(picked)) picked = shown.get(0);

        if (shown.isEmpty()) Ui.text(p, 0f, y + 2f, lw, "No pet matches.", Ui.gray());
        else if (hasFilter()) {
            int rows = filter.open ? ROWS_SHOWN - 1 : ROWS_SHOWN;
            UiScroll.Column column = UiScroll.list(p, 0f, y, w, rows * ROW_PITCH - ROW_GAP, scroll, PETS);
            for (int i = 0; i < shown.size(); i++) row(column.block(ROW_H, i == 0 ? 0f : ROW_GAP), 0f, column.width(), shown.get(i));
            column.end();
        } else for (int i = 0; i < shown.size(); i++) row(p, y + i * ROW_PITCH, lw, shown.get(i));

        float left = left(picked);
        if (isNomios()) addNomios(p, treatTop(), w, left);
        else addSyringe(p, treatTop(), w, left);
    }

    private static float[] columnX(float lw) {
        return new float[]{8f, 262f, 346f, lw - 98f};
    }

    // a click on a column head sorts by it, a second click turns the order
    private void addHead(CustomPanelAPI p, float y, float lw) {
        float[] xs = columnX(lw);
        CustomPanelAPI head = Ui.thead(p, 0f, y, lw, COLUMNS, xs, COLUMN_W, COLUMN_ALIGN);
        for (int i = 0; i < COLUMNS.length; i++) {
            String column = COLUMNS[i];
            CustomPanelAPI click = head.createCustomPanel(COLUMN_W[i], Ui.HEADING_H, new PetCard.Click(() -> {
                if (column.equals(sort)) reversed = !reversed;
                else {
                    sort = column;
                    reversed = false;
                }
                scroll.reset(PETS);
                refresh();
            }, false));
            head.addComponent(click).inTL(xs[i], 0f);
            if (COLUMN_TIPS[i] != null) Ui.tooltip(head, click, null, COLUMN_TIPS[i]);

            if (!column.equals(sort)) continue;
            float tw = Ui.width(column, Ui.ORB12);
            float ax = COLUMN_ALIGN[i] == Ui.RIGHT ? xs[i] + COLUMN_W[i] - tw - 14f : xs[i] + tw + 6f;
            float[] arrow = reversed ? UP : DOWN;
            Ui.painted(head, ax, 7f, 8f, 5f, null, (pos, a) -> UiDraw.fan(UiDraw.left(pos), UiDraw.bottom(pos) - 3f, 8f, 8f, arrow, 4f, 2.5f, Ui.base(), a));
        }
    }

    private void row(CustomPanelAPI p, float y, float lw, Pet pet) {
        CustomPanelAPI row = UiButtons.row(p, 0f, y, lw, ROW_H, pet == picked, true, null, () -> {
            picked = pet;
            refresh();
        });
        UiImages.img(row, 4f, 4f, 40f, 40f, pet.getIcon());

        float[] xs = columnX(lw);
        float nw = xs[1] - NAME_X - 8f;
        String name = Ui.fit(pet.name, Ui.INS15, nw);
        Ui.text(row, NAME_X, 6f, nw, name);
        if (!name.equals(pet.name)) Ui.tooltip(p, row, null, pet.name);
        Ui.text(row, NAME_X, 25f, nw, Ui.fit(PetCard.species(pet), Ui.INS15, nw), Ui.gray());

        float left = left(pet);
        Ui.text(row, xs[1], 15f, COLUMN_W[1], pet.getSpan());
        Ui.text(row, xs[2], 15f, COLUMN_W[2], PetLongevity.lifeLeft(left), PetLongevity.isShort(left) ? Ui.neg() : Ui.text());
        Ui.text(row, xs[3], 15f, COLUMN_W[3], String.valueOf(pet.treatments), Ui.INS15, pet.treatments > 0 ? Ui.text() : Ui.gray(), Ui.RIGHT);
    }

    private void addSyringe(CustomPanelAPI p, float y, float w, float left) {
        int add = picked.getSyringeCycles();
        String plus = "+" + add;
        Ui.heading(p, 0f, y, w, "Treatment", plus + (add == 1 ? " cycle" : " cycles"), plus);
        y += Ui.HEADING_H + 4f;

        UiData.change(p, 0f, y, w, LINE_H, LIFE, PetLongevity.lifeLeft(left), PetLongevity.lifeLeft(left + add * 365f));
        y += LINE_H + 4f;

        UiData.costrow(p, 0f, y, w, PetLongevity.syringeName(), PetLongevity.syringeIcon(), "1", String.valueOf(PetLongevity.syringeCount()), true);
        y += UiData.COST_ROW_H + 4f;

        // the line keeps its height for every pet, so nothing moves on a pick
        if (!picked.getData().isHamster()) return;
        String n = String.valueOf(PetConstants.HAMSTER_TREATMENT_CYCLES);
        Ui.runs(p, 0f, y, w, Ui.INS15, Ui.text(), Ui.LEFT, "Hamsters gain " + PetLongevity.cycles(PetConstants.HAMSTER_TREATMENT_CYCLES) + " per syringe.",
                new String[]{n}, new Color[]{Ui.hl()});
    }

    private void addNomios(CustomPanelAPI p, float y, float w, float left) {
        Ui.heading(p, 0f, y, w, "Treatment", Ui.LEFT);
        y += Ui.HEADING_H + 4f;

        if (affordable > 0) UiData.change(p, 0f, y, w, LINE_H, LIFE, PetLongevity.lifeLeft(left), PetLongevity.lifeLeft(left + cycles * 365f));
        else UiData.value(p, 0f, y, w, LINE_H, LIFE, PetLongevity.lifeLeft(left), PetLongevity.isShort(left) ? Ui.neg() : Ui.text(), new String[0], new Color[0]);
        y += LINE_H + 8f;

        int max = PetConstants.NOMIOS_MAX_CYCLES;
        float bw = (float) Math.floor((w - (max - 1) * TOGGLE_GAP) / max);
        for (int n = 1; n <= max; n++) {
            int count = n;
            boolean can = n <= affordable;
            ButtonAPI b = UiButtons.area(p, (n - 1) * (bw + TOGGLE_GAP), y, bw, TOGGLE_H, PetLongevity.cycles(n), can && n == cycles, can, null, Ui.CENTER, () -> {
                cycles = count;
                refresh();
            });
            if (!can) Ui.tooltip(p, b, TIP_W, TooltipMakerAPI.TooltipLocation.BELOW, t -> priceTip(t, count));
        }
        y += TOGGLE_H + 4f;

        if (affordable < max) Ui.text(p, 0f, y, w, need(affordable + 1), Ui.neg());
        y += REASON_H + 8f;

        Ui.heading(p, 0f, y, w, "Materials", Ui.LEFT);
        y += Ui.HEADING_H + 4f;
        costRows(p, 0f, y, w, affordable > 0 ? cycles : 1);
    }

    // a grey cycle toggle: its price against what the fleet has
    private static void priceTip(TooltipMakerAPI t, int n) {
        // the default 0.85 lets the cost rows under it show through, the vanilla ship tooltip uses 1 too
        t.setBgAlpha(1f);
        float w = TIP_W - 12f;
        t.addPara(PetLongevity.cycles(n), Ui.hl(), 0f);
        CustomPanelAPI rows = Global.getSettings().createCustom(w, 3 * UiData.COST_ROW_H, null);
        costRows(rows, 0f, 0f, w, n);
        t.addCustom(rows, 8f);
    }

    private static void costRows(CustomPanelAPI p, float x, float y, float w, int n) {
        CargoAPI cargo = cargo();
        for (int i = 0; i < MATERIALS.length; i++) {
            int price = PRICES[i] * n;
            float have = cargo.getCommodityQuantity(MATERIALS[i]);
            UiData.costrow(p, x, y + i * UiData.COST_ROW_H, w, commodityName(MATERIALS[i]), UiImages.commoditySprite(MATERIALS[i]),
                    Misc.getWithDGS(price), Misc.getWithDGS(have), have >= price);
        }

        float credits = PetConstants.NOMIOS_CREDITS * n;
        float have = cargo.getCredits().get();
        UiData.costrow(p, x, y + MATERIALS.length * UiData.COST_ROW_H, w, "Credits", UiImages.creditsSprite(), Ui.credits(credits), Ui.credits(have), have >= credits);
    }

    // the red line under the toggles: what the first grey one lacks, the amount when one material is short, the names when several are
    private static String need(int n) {
        CargoAPI cargo = cargo();
        List<String> names = new ArrayList<>();
        float missing = 0f;
        for (int i = 0; i < MATERIALS.length; i++) {
            float lack = PRICES[i] * n - cargo.getCommodityQuantity(MATERIALS[i]);
            if (lack <= 0f) continue;
            names.add(commodityName(MATERIALS[i]));
            missing = lack;
        }

        float lack = PetConstants.NOMIOS_CREDITS * n - cargo.getCredits().get();
        if (lack > 0f) {
            names.add("credits");
            missing = lack;
        }

        String cycles = PetLongevity.cycles(n);
        if (names.size() == 1) return "Needs " + Misc.getWithDGS((float) Math.ceil(missing)) + " more " + names.get(0) + " for " + cycles;
        return "Needs more " + String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1) + " for " + cycles;
    }

    private static int affordable() {
        CargoAPI cargo = cargo();
        int can = PetConstants.NOMIOS_MAX_CYCLES;
        for (int i = 0; i < MATERIALS.length; i++) can = Math.min(can, (int) (cargo.getCommodityQuantity(MATERIALS[i]) / PRICES[i]));
        return Math.max(0, Math.min(can, (int) (cargo.getCredits().get() / PetConstants.NOMIOS_CREDITS)));
    }

    private static CargoAPI cargo() {
        return Global.getSector().getPlayerFleet().getCargo();
    }

    private static String commodityName(String id) {
        return Global.getSettings().getCommoditySpec(id).getName();
    }

    private static float left(Pet pet) {
        return Math.max(0f, pet.getLifetime() - pet.age);
    }

    private List<Pet> shown() {
        boolean favourite = filter.on.contains(FAVOURITE);
        boolean treated = filter.on.contains(TREATED);

        List<Pet> shown = new ArrayList<>();
        for (Pet pet : pets) {
            if (!filter.matches(pet.name, pet.getData().species, pet.getLookName())) continue;
            if (favourite && !pet.favourite) continue;
            if (treated && pet.treatments == 0) continue;
            shown.add(pet);
        }

        Comparator<Pet> name = Comparator.comparing(pet -> pet.name, String.CASE_INSENSITIVE_ORDER);
        shown.sort(switch (sort) {
            case PET -> name;
            case AGE -> Comparator.comparingDouble((Pet pet) -> -pet.age).thenComparing(name);
            case TREATMENTS -> Comparator.comparingInt((Pet pet) -> -pet.treatments).thenComparing(name);
            default -> Comparator.comparingDouble(PetTreatDialog::left).thenComparing(name);
        });
        if (reversed) Collections.reverse(shown);
        return shown;
    }

    @Override
    public boolean hasCancelButton() {
        return !isNomios() || affordable > 0;
    }

    // the game's Confirm can not be greyed, so a Nomios popup that can buy nothing has only Close
    @Override
    public String getConfirmText() {
        return isNomios() && affordable == 0 ? "Close" : "Confirm";
    }

    @Override
    public String getCancelText() {
        return "Cancel";
    }

    @Override
    public void customDialogConfirm() {
        if (!isNomios()) {
            int add = picked.getSyringeCycles();
            picked.treat(add);
            syringe.removeFromClickedStackFirst(1);
            String gain = "+" + PetLongevity.cycles(add);
            PetStatusManager.getInstance().message("%s: %s of life", Misc.getTextColor(), picked.name, gain, Misc.getHighlightColor(), Misc.getPositiveHighlightColor());
            dialog.dismiss();
            return;
        }

        if (affordable == 0) {
            leaveNomios();
            return;
        }

        TextPanelAPI text = market.getTextPanel();
        picked.treat(cycles);
        text.addPara(String.format(NOMIOS_DONE, picked.name));
        text.highlightInLastPara(Misc.getHighlightColor(), picked.name);
        text.addPara(NOMIOS_BYE);

        CargoAPI cargo = cargo();
        for (int i = 0; i < MATERIALS.length; i++) {
            int amount = PRICES[i] * cycles;
            cargo.removeCommodity(MATERIALS[i], amount);
            AddRemoveCommodity.addCommodityLossText(MATERIALS[i], amount, text);
        }
        int credits = PetConstants.NOMIOS_CREDITS * cycles;
        cargo.getCredits().subtract(credits);
        AddRemoveCommodity.addCreditsLossText(credits, text);

        FireAll.fire(null, market, memoryMap, "PopulateOptions");
    }

    @Override
    public void customDialogCancel() {
        if (isNomios()) leaveNomios();
        else dialog.dismiss();
    }

    private void leaveNomios() {
        market.getTextPanel().addPara(NOMIOS_CANCEL);
        FireAll.fire(null, market, memoryMap, "PopulateOptions");
    }
}
