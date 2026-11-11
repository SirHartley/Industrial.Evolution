package livetest;

import com.fs.starfarer.api.Global;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

/**
 * Example console command. Print the exact state the bug or feature depends on, so one window capture
 * of the console proves it. Command names should use only lowercase letters and digits: background
 * typing cannot produce characters that need Shift, such as '_'.
 */
public class StatusCommand implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        Console.showMessage("Credits: " + Math.round(Global.getSector().getPlayerFleet().getCargo().getCredits().get())
                + "\nDate: " + Global.getSector().getClock().getDateString());
        return CommandResult.SUCCESS;
    }
}
