package indevo.exploration.minefields;

import com.fs.starfarer.api.campaign.SectorEntityToken;

public interface MineAPI extends SectorEntityToken {

    float getRotation();

    void setRotation(float rotation);

}