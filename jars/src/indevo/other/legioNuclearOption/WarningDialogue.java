package indevo.other.legioNuclearOption;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

public class WarningDialogue implements EveryFrameScript {
    private boolean done = false;

    public static void show() {
        Global.getSector().getScripts().add(new WarningDialogue());
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (!done) {
            showWarning();
            done = true;
        }
    }

    public void showWarning() {
        StringBuilder weaponsString = new StringBuilder();

        for (String s : NuclearOptionManager.getFittedIllegalWeaponIds()) {
            if (weaponsString.length() > 0) weaponsString.append(", ");
            weaponsString.append(Global.getSettings().getWeaponSpec(s).getWeaponName());
        }

        String message = "You have fit weapons to your fleet that are considered a nuclear option. Other factions might respond in kind if you decide to use them in combat.\n\n" +
                "Affected weapons: " + weaponsString;

        Global.getSector().getCampaignUI().showMessageDialog(message);
        Global.getSoundPlayer().playUISound("cr_allied_critical", 1, 1);
    }
}
