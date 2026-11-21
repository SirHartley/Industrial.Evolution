package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.academy.industry.Academy;
import indevo.industries.petshop.PetGenetics;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.utils.ui.*;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// tab Adoption at an NPC Animal Center: this month's offers as a table, the card of the picked one
public class PetAdoption {

    static final float ROW_W = PetsPanel.LIST_W - 10f;
    static final float ROW_H = 48f;
    static final float ROW_PITCH = 52f;
    static final float NAME_X = 54f;
    static final float NAME_W = 236f;

    static final String PET = "Pet";
    static final String DISPOSITION = "Disposition";
    static final String AGE = "Age";
    static final String PRICE = "Price";
    static final String[] COLUMNS = {PET, DISPOSITION, AGE, PRICE};
    static final float[] COLUMN_X = {8f, 300f, 396f, 456f};
    static final float[] COLUMN_W = {200f, 90f, 60f, 86f};
    static final Alignment[] COLUMN_ALIGN = {Ui.LEFT, Ui.LEFT, Ui.LEFT, Ui.RIGHT};
    static final float[] DOWN = {0f, 0f, 8f, 0f, 4f, 5f};
    static final float[] UP = {0f, 5f, 8f, 5f, 4f, 0f};

    static final String AFFORDABLE = "Affordable";
    static final String ADULT = "Adult";
    static final String YOUNG = "Young";
    static final List<UiFilter.Item> TOGGLES = List.of(
            new UiFilter.Item(AFFORDABLE, 96f),
            new UiFilter.Item(ADULT, 64f),
            new UiFilter.Item(YOUNG, 66f));

    private final PetsPanel panel;
    private final PetsView view;
    private final PetShop shop;

    PetAdoption(PetsPanel panel, PetsView view) {
        this.panel = panel;
        this.view = view;
        this.shop = panel.shop();
    }

    void build(CustomPanelAPI p, float w, float h) {
        List<Pet> offers = shop.getOffers();
        PetStatusManager.getInstance().seeOffers(offers);
        List<Pet> shown = shown(offers);
        Pet selected = selected(offers, shown);

        addList(p, offers, shown, selected);
        Ui.vrule(p, PetsPanel.LIST_W + 5f, PetsPanel.BODY_Y, h - PetsPanel.BODY_Y);
        if (selected != null) new PetCard(panel, view).buildOffer(p, PetsPanel.CARD_X, PetsPanel.BODY_Y, w - PetsPanel.CARD_X, h - PetsPanel.BODY_Y, selected);
    }

    // the picked offer keeps its card while the filter hides its row
    private Pet selected(List<Pet> offers, List<Pet> shown) {
        for (Pet offer : offers) if (offer.id.equals(view.offer)) return offer;
        Pet first = !shown.isEmpty() ? shown.get(0) : null;
        view.offer = first != null ? first.id : null;
        return first;
    }

    private void addList(CustomPanelAPI p, List<Pet> offers, List<Pet> shown, Pet selected) {
        float y = PetsPanel.BODY_Y;
        String days = String.valueOf(PetShop.getDaysToNewOffers());
        Ui.heading(p, 0f, y, ROW_W, "On offer", "New pets in " + days + (days.equals("1") ? " day" : " days"), days);
        y += Ui.HEADING_H + 4f;

        if (offers.isEmpty()) {
            addHead(p, y);
            Ui.text(p, 0f, y + Ui.HEADING_H + 4f, ROW_W, "Nothing on offer this month.", Ui.gray());
            return;
        }

        y += UiFilter.bar(p, 2f, y, view.offers, "Name or species", null, TOGGLES, null, List.of(), panel::filtered, panel::refresh) + UiFilter.BELOW;
        addHead(p, y);
        y += Ui.HEADING_H + 4f;

        if (shown.isEmpty()) {
            Ui.text(p, 0f, y, ROW_W, "No pet matches.", Ui.gray());
            return;
        }

        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        for (int i = 0; i < shown.size(); i++) row(p, y + i * ROW_PITCH, shown.get(i), shown.get(i) == selected, credits);
    }

    // a click on a column head sorts by it, a second click turns the order
    private void addHead(CustomPanelAPI p, float y) {
        CustomPanelAPI head = Ui.thead(p, 0f, y, ROW_W, COLUMNS, COLUMN_X, COLUMN_W, COLUMN_ALIGN);
        for (int i = 0; i < COLUMNS.length; i++) {
            String column = COLUMNS[i];
            CustomPanelAPI click = head.createCustomPanel(COLUMN_W[i], Ui.HEADING_H, new PetCard.Click(() -> {
                if (column.equals(view.offerSort)) view.offerReversed = !view.offerReversed;
                else {
                    view.offerSort = column;
                    view.offerReversed = false;
                }
                panel.refresh();
            }, false));
            head.addComponent(click).inTL(COLUMN_X[i], 0f);

            if (!column.equals(view.offerSort)) continue;
            float tw = Ui.width(column, Ui.ORB12);
            float ax = COLUMN_ALIGN[i] == Ui.RIGHT ? COLUMN_X[i] + COLUMN_W[i] - tw - 14f : COLUMN_X[i] + tw + 6f;
            float[] arrow = view.offerReversed ? UP : DOWN;
            Ui.painted(head, ax, 7f, 8f, 5f, null, (pos, a) -> UiDraw.fan(UiDraw.left(pos), UiDraw.bottom(pos) - 3f, 8f, 8f, arrow, 4f, 2.5f, Ui.base(), a));
        }
    }

    private void row(CustomPanelAPI p, float y, Pet offer, boolean selected, float credits) {
        CustomPanelAPI row = UiButtons.row(p, 0f, y, ROW_W, ROW_H, selected, true, null, () -> {
            view.offer = offer.id;
            view.scroll.reset(PetsView.TALENTS);
            panel.refresh();
        });
        UiImages.img(row, 4f, 4f, 40f, 40f, offer.getIcon());

        String name = Ui.fit(offer.name, Ui.INS15, NAME_W);
        Ui.text(row, NAME_X, 6f, NAME_W, name);
        if (!name.equals(offer.name)) Ui.tooltip(p, row, null, offer.name);
        Ui.text(row, NAME_X, 25f, NAME_W, Ui.fit(PetCard.species(offer), Ui.INS15, NAME_W), Ui.gray());

        String disposition = offer.sheet.disposition;
        Ui.text(row, COLUMN_X[1], 15f, COLUMN_W[1], Misc.ucFirst(disposition), Academy.COLOURS_BY_PERSONALITY.get(disposition));
        Ui.text(row, COLUMN_X[2], 15f, COLUMN_W[2], ageLabel(offer));

        float price = shop.getOfferPrice(offer);
        Color color = price > credits ? Ui.neg() : Ui.hl();
        Ui.text(row, COLUMN_X[3], 15f, COLUMN_W[3], Ui.credits(price), Ui.INS15, color, Ui.RIGHT);
    }

    private static String ageLabel(Pet pet) {
        return pet.isYoung() ? YOUNG : pet.isOld() ? "Old" : ADULT;
    }

    private List<Pet> shown(List<Pet> offers) {
        UiFilter.State filter = view.offers;
        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        boolean adult = filter.on.contains(ADULT);
        boolean young = filter.on.contains(YOUNG);

        List<Pet> shown = new ArrayList<>();
        for (Pet offer : offers) {
            if (!filter.matches(offer.name, offer.getData().species, offer.getLookName())) continue;
            if (filter.on.contains(AFFORDABLE) && shop.getOfferPrice(offer) > credits) continue;
            if ((adult || young) && !(offer.isYoung() ? young : adult)) continue;
            shown.add(offer);
        }

        Comparator<Pet> name = Comparator.comparing(pet -> pet.name, String.CASE_INSENSITIVE_ORDER);
        shown.sort(switch (view.offerSort) {
            case DISPOSITION -> Comparator.comparingInt((Pet pet) -> PetGenetics.DISPOSITIONS.indexOf(pet.sheet.disposition)).thenComparing(name);
            case AGE -> Comparator.comparingDouble((Pet pet) -> -pet.age).thenComparing(name);
            case PRICE -> Comparator.comparingDouble((Pet pet) -> shop.getOfferPrice(pet)).thenComparing(name);
            default -> name;
        });
        if (view.offerReversed) Collections.reverse(shown);
        return shown;
    }
}
