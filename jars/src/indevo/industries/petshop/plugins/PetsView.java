package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.ui.TextFieldAPI;
import indevo.utils.ui.UiFilter;
import indevo.utils.ui.UiScroll;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// view state of the Pets panel; null selection picks the first pet in the list
public class PetsView {

    public static final String LIST = "list";
    public static final String TALENTS = "talents";
    public static final String ADULTS = "adults";
    public static final int TAB_PETS = 0;
    public static final int TAB_HUSBANDRY = 1;

    public int tab = TAB_PETS;
    public String selected = null;
    public final UiScroll.Offsets scroll = new UiScroll.Offsets();
    final UiFilter.State filter = new UiFilter.State(PetsPanel.SORT_SHIP);

    // Husbandry: the selected habitat (-1 picks the first free one) and the adults picked for it
    int habitat = -1;
    final List<String> pair = new ArrayList<>();
    final UiFilter.State adults = PetHusbandry.newFilter();
    // a ready brood opens its New young popup by itself once, on the first frame of the panel
    boolean youngChecked = false;

    int clicks = 0;
    String answer = null;
    final Map<String, String> lines = new HashMap<>();

    // real seconds the panel has been open, driven by its panel plugin
    float clock = 0f;
    float pettedAt = -1f;
    final List<Heart> hearts = new ArrayList<>();

    TextFieldAPI nameField = null;
    String namePet = null;

    record Heart(float start, float dx, float dy, float size) {
    }

    public PetsView() {
    }

    public PetsView(String selected) {
        this.selected = selected;
    }

    void select(String id) {
        if (id.equals(selected)) return;

        selected = id;
        scroll.reset(TALENTS);
        clicks = 0;
        answer = null;
        pettedAt = -1f;
        hearts.clear();
    }
}
