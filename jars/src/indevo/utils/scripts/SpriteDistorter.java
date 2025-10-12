package indevo.utils.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class SpriteDistorter extends BaseCustomEntityPlugin {

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);

        if (true) return;

        SpriteAPI sprite = Global.getSettings().getSprite(""); //get the sprite however

        int rows = 10;
        int columns = 10;
        float pxBetweenRows = sprite.getHeight() / rows;
        float pxBetweenColumns = sprite.getWidth() / columns;
        float coordsBetweenRows = sprite.getTextureHeight() / rows;
        float coordsBetweenColumns = sprite.getTextureWidth() / columns;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        sprite.bindTexture();
        GL11.glBegin(GL11.GL_QUADS);
        Misc.setColor(Color.WHITE);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {

                float texXOffset = 0; //calculate an offset for the tex x coord
                float texYOffset = 0; //calculate an offset for the tex y coord

                //bl corner
                GL11.glTexCoord2f((x * coordsBetweenColumns) + texXOffset, (y * coordsBetweenRows) + texYOffset);
                GL11.glVertex2f(x * pxBetweenColumns, y * pxBetweenRows);

                //br corner
                GL11.glTexCoord2f((x * (coordsBetweenColumns + 1)) + texXOffset, (y * coordsBetweenRows) + texYOffset);
                GL11.glVertex2f(x * (pxBetweenColumns + 1), y * pxBetweenRows);

                //tr corner
                GL11.glTexCoord2f((x * (coordsBetweenColumns + 1)) + texXOffset, (y * (coordsBetweenRows + 1)) + texYOffset);
                GL11.glVertex2f(x * (coordsBetweenColumns + 1), y * (coordsBetweenRows + 1));

                //tl corner
                GL11.glTexCoord2f((x * coordsBetweenColumns) + texXOffset, (y * (coordsBetweenRows + 1)) + texYOffset);
                GL11.glVertex2f(x * coordsBetweenColumns, y * (coordsBetweenRows + 1));
            }
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }
}
