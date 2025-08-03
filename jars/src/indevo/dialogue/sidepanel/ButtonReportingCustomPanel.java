package indevo.dialogue.sidepanel;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.PositionAPI;
import indevo.industries.petshop.dialogue.PetManagerDialogueDelegate;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class ButtonReportingCustomPanel extends BaseCustomUIPanelPlugin {
    public ButtonReportingDialogueDelegate delegate;
    protected PositionAPI pos;
    public float sideRatio = 0.5f;
    public Color color;
    public float heightOverride = 0f;

    public ButtonReportingCustomPanel(ButtonReportingDialogueDelegate delegate) {
        this.delegate = delegate;
    }

    public ButtonReportingCustomPanel(ButtonReportingDialogueDelegate delegate, Color edgeColour, float heightOverride) {
        this.delegate = delegate;
        this.color = edgeColour;
        this.heightOverride = heightOverride;
    }

    @Override
    public void buttonPressed(Object buttonId) {
        super.buttonPressed(buttonId);
        delegate.reportButtonPressed(buttonId);
    }

    @Override
    public void render(float alphaMult) {
        if (color == null) return;

        float x = pos.getX();
        float y = pos.getY();
        float w = pos.getWidth();
        float h = heightOverride > 0f ? heightOverride : pos.getHeight();

        renderBox(x, y, w, h, alphaMult);
    }

    @Override
    public void positionChanged(PositionAPI pos) {
        this.pos = pos;
    }

    public void renderBox(float x, float y, float w, float h, float alphaMult) {
        float lh = h * sideRatio;
        float lw = w * sideRatio;

        float[] points = new float[]{
                // upper left
                0, h - lh,
                0, h,
                0 + lw, h,

                // upper right
                w - lw, h,
                w, h,
                w, h - lh,

                // lower right
                w, lh,
                w, 0,
                w - lw, 0,

                // lower left
                lw, 0,
                0, 0,
                0, lh
        };

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 0.3f * alphaMult);

        for (int i = 0; i < 4; i++) {
            GL11.glBegin(GL11.GL_LINES);
            {
                int index = i * 6;

                GL11.glVertex2f(points[index] + x, points[index + 1] + y);
                GL11.glVertex2f(points[index + 2] + x, points[index + 3] + y);
                GL11.glVertex2f(points[index + 2] + x, points[index + 3] + y);
                GL11.glVertex2f(points[index + 4] + x, points[index + 5] + y);
            }
            GL11.glEnd();
        }

        GL11.glPopMatrix();
    }

    @Override
    public void renderBelow(float alphaMult) {
    }
}
