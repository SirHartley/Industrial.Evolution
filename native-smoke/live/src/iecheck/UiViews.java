package iecheck;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.List;

// one panel view: open it from code, audit the drawn tree, capture it, close it
public final class UiViews {
    private static final Logger log = Global.getLogger(UiViews.class);

    public interface Open {
        void open(Ctx c) throws Exception;
    }

    public interface Act {
        void act(Ctx c, UiAudit.Found panel) throws Exception;
    }

    private UiViews() {
    }

    public static void view(List<Step> steps, String name, Open open) {
        view(steps, name, open, null);
    }

    // act runs on the drawn panel before the audit (clicks, scrolling), then the panel is drawn again
    public static void view(List<Step> steps, String name, Open open, Act act) {
        steps.add(c -> {
            c.keepDialog(true);
            UiAudit.dismissDialog();
            c.frames(10);
        });
        steps.add(c -> {
            open.open(c);
            c.frames(45);
        });
        if (act != null) steps.add(c -> {
            UiAudit.Found found = UiAudit.find();
            if (found == null && side != null) found = UiAudit.findSide(side[0], side[1]);
            c.expect(found != null, name + " panel opens");
            if (found != null) act.act(c, found);
            c.frames(20);
        });
        steps.add(c -> audit(c, name));
        steps.add(c -> {
            UiAudit.Found found = UiAudit.find();
            if (found != null) UiAudit.close(found);
            c.frames(20);
        });
        steps.add(c -> {
            UiAudit.dismissDialog();
            c.frames(5);
        });
    }

    // width and height of a side panel to audit when no custom dialog is open
    public static float[] side = null;

    public static void audit(Ctx c, String name) {
        UiAudit.Found found = UiAudit.find();
        if (found == null && side != null) found = UiAudit.findSide(side[0], side[1]);
        if (found == null) {
            Object d = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
            log.info("LIVE_CHECK note: no panel, tree " + UiAudit.dump(d, 0) + " screen " + UiAudit.dump(UiAudit.screen(), 0));
            c.expect(false, name + " panel is open");
            return;
        }

        List<String> findings = UiAudit.run(found);
        log.info("LIVE_CHECK note: rect " + name + " " + UiAudit.rect(found) + ", " + UiAudit.stats);
        for (String finding : findings) log.info("LIVE_CHECK note: AUDIT " + name + ": " + finding);
        for (String note : UiAudit.accepted) log.info("LIVE_CHECK note: AUDIT " + name + ": " + note);
        c.expect(findings.isEmpty(), name + " audit, " + findings.size() + " findings");
        c.shot(name);
    }
}
