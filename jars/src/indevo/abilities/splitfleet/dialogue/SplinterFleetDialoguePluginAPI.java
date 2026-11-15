package indevo.abilities.splitfleet.dialogue;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.util.Misc;

public interface SplinterFleetDialoguePluginAPI {
    public void showBaseOptions();

    public InteractionDialogAPI getDialog();

    default void notice(String text) {
        getDialog().getTextPanel().addPara(text, Misc.getNegativeHighlightColor());
    }
}
