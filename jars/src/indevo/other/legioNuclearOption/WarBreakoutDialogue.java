package indevo.other.legioNuclearOption;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

public class WarBreakoutDialogue implements EveryFrameScript {
    private boolean done = false;

    public static void show() {
        Global.getSector().getScripts().add(new WarBreakoutDialogue());
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

        String message = "You have used nuclear weapons in combat. Other factions will now deploy their own solutions. What have you done?\n\n" +
                "Caused by using: " + weaponsString;

        Global.getSector().getCampaignUI().showMessageDialog(message);
        Global.getSoundPlayer().playUISound("cr_playership_warning", 1, 1);
    }
}
