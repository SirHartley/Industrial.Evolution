package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.ui.TextFieldAPI;
import indevo.utils.ui.UiFilter;
import indevo.utils.ui.UiScroll;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// view state of the Pets panel; null selection picks the first pet in the list
public class PetsView {

    public static final String LIST = "list";
    public static final String TALENTS = "talents";
    public static final String ADULTS = "adults";
    public static final String CROSS = "cross";
    public static final String TREE = "tree";
    public static final int TAB_PETS = 0;
    public static final int TAB_HUSBANDRY = 1;
    public static final int TAB_TREE = 2;

    public int tab = TAB_PETS;
    public String selected = null;
    public final UiScroll.Offsets scroll = new UiScroll.Offsets();
    final UiFilter.State filter = new UiFilter.State(PetsPanel.SORT_SHIP);

    // Husbandry: the selected habitat (-1 picks the first free one) and the adults picked for it
    int habitat = -1;
    final List<String> pair = new ArrayList<>();
    // the cross row drawn large, by trait id or the look, disposition or malformed key; null picks the best talent
    String crossTrait = null;
    final UiFilter.State adults = PetHusbandry.newFilter();

    // Family tree: whose family, the clicked pet, the pair opened from the cross; the full view shows one species
    String treeRoot = null;
    String treeCard = null;
    final List<String> treePair = new ArrayList<>();
    boolean treeFull = false;
    boolean treeSmall = false;
    String treeSpecies = null;
    // dead branches the player opened, by stub id, until the tab is left
    final Set<String> unfolded = new HashSet<>();
    // how far the tree is dragged sideways
    float treeX = 0f;
    final UiFilter.State find = new UiFilter.State("");

    int clicks = 0;
    String answer = null;
    final Map<String, String> lines = new HashMap<>();

    // real seconds the panel has been open, driven by its panel plugin
    float clock = 0f;
    float pettedAt = -1f;
    final PetReveal reveal = new PetReveal();
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
