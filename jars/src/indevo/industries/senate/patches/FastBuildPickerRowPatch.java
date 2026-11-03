package indevo.industries.senate.patches;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.senate.conditions.Edict_FastBuild;
import org.lwjgl.opengl.GL11;
import patchlib.api.context.AfterContext;
import patchlib.api.context.Context;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;

import java.awt.Color;

// build menu row, obf com.fs.starfarer.campaign.ui.marketinfo.oO0oOOO... on 0.98a-RC8, only class there with getSortBuildTime
@Patch(target = @ClassMatch(
        packageName = "com.fs.starfarer.campaign.ui.marketinfo",
        methodMatches = @MethodMatch(methodName = "getSortBuildTime")))
public class FastBuildPickerRowPatch {

    private static final String LABEL_KEY = "IndEvo_fastBuildDaysLabel";
    private static final String OLD_DAYS_KEY = "IndEvo_fastBuildOldDays";
    private static final String GAP = "  ";

    // the days column label is created as days + " " and centred right after, so the text changes before layout
    @RedirectMethodCall(
            target = @MethodMatch(methodName = "sizeChanged", parameterCount = 2),
            call = @MethodMatch(methodName = "create", parameterTypes = {String.class, String.class, Color.class}),
            owner = @ClassMatch(subtype = LabelAPI.class))
    public static void redirectDaysLabel(MethodCallContext context) {
        Integer days = getVanillaDays(context, context.getCallArg(0));
        if (days == null) {
            context.setResult(context.call());
            return;
        }

        String oldDays = String.valueOf(days);
        String newDays = String.valueOf(Edict_FastBuild.getReducedDays(days));
        Object color = context.getCallArg(2);
        Object result = context.call(oldDays + GAP + newDays, context.getCallArg(1), Misc.getGrayColor());

        if (result instanceof LabelAPI label) {
            int start = oldDays.length() + GAP.length();
            label.setHighlight(start, start + newDays.length() - 1);
            if (color instanceof Color highlight) label.setHighlightColor(highlight);

            context.getData().putWeak(LABEL_KEY, label);
            context.getData().put(OLD_DAYS_KEY, oldDays);
        }

        context.setResult(result);
    }

    @After(target = @MethodMatch(methodName = "renderImpl", parameterTypes = float.class))
    public static void afterRender(AfterContext context) {
        LabelAPI label = context.getData().get(LABEL_KEY);
        String oldDays = context.getData().get(OLD_DAYS_KEY);
        if (label == null || oldDays == null || label.getText() == null || !label.getText().startsWith(oldDays + GAP)) return;

        PositionAPI pos = label.getPosition();
        float x = pos.getX();
        float y = (int) pos.getCenterY() + 0.5f;
        float width = label.computeTextWidth(oldDays);
        float alphaMult = context.getArg(0) instanceof Float alpha ? alpha : 1f;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(1f);
        Misc.setColor(Misc.getGrayColor(), alphaMult);

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width, y);
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    private static Integer getVanillaDays(Context context, Object text) {
        if (!(text instanceof String str) || str.isEmpty() || !Character.isDigit(str.charAt(0))) return null;
        if (!Edict_FastBuild.isActive(getMarket(context)) || !context.hasMethod("getSortBuildTime") || !context.hasMethod("getIndustry")) return null;

        Object industry = context.getMethod("getIndustry").call();
        if (industry instanceof Industry ind && ind.getSpec() != null && ind.getSpec().hasTag(Industries.TAG_PARENT)) return null;

        Object days = context.getMethod("getSortBuildTime").call();
        if (!(days instanceof Integer value) || value <= 1 || !str.trim().equals(String.valueOf(value))) return null;

        return value;
    }

    private static MarketAPI getMarket(Context context) {
        if (!context.hasField("market")) return null;
        Object market = context.getField("market").get();
        return market instanceof MarketAPI m ? m : null;
    }
}
