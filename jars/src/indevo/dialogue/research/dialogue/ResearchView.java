package indevo.dialogue.research.dialogue;

import indevo.utils.ui.UiScroll;

import java.util.HashSet;
import java.util.Set;

// view state of the Research Tracts side panel: projects with Info open, scroll offset of the card list
public class ResearchView {

    public static final String CARDS = "cards";

    public final Set<String> info = new HashSet<>();
    public final UiScroll.Offsets scroll = new UiScroll.Offsets();

    public ResearchView() {
    }

    public ResearchView(String... openInfo) {
        info.addAll(Set.of(openInfo));
    }
}
