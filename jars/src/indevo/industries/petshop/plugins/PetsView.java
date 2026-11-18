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

    public String selected = null;
    public final UiScroll.Offsets scroll = new UiScroll.Offsets();
    final UiFilter.State filter = new UiFilter.State(PetsPanel.SORT_SHIP);

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
        clicks = 0;
        answer = null;
        pettedAt = -1f;
        hearts.clear();
    }
}
