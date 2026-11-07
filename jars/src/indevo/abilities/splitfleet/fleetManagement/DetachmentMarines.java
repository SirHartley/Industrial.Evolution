package indevo.abilities.splitfleet.fleetManagement;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker.PersonnelAtEntity;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker.PersonnelData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

// marine xp of a detachment is held by the vanilla personnel tracker under the detachment fleet entity
public class DetachmentMarines {

    // counts are the cargo counts before the marines move, call tracker update() after the cargo moved
    public static void move(CampaignFleetAPI detachment, int detachmentCount, int playerCount, int toDetachment) {
        if (toDetachment == 0) return;

        PlayerFleetPersonnelTracker tracker = PlayerFleetPersonnelTracker.getInstance();
        PersonnelData player = tracker.getMarineData();
        PersonnelData det = getData(detachment, detachmentCount);
        player.numMayHaveChanged(playerCount, false);

        if (toDetachment > 0) PlayerFleetPersonnelTracker.transferPersonnel(player, det, toDetachment, null);
        else PlayerFleetPersonnelTracker.transferPersonnel(det, player, -toDetachment, null);
    }

    public static void moveToPlayer(CampaignFleetAPI detachment, int playerCount) {
        int count = detachment.getCargo().getMarines();
        move(detachment, count, playerCount, -count);
    }

    public static void moveToStorage(CampaignFleetAPI detachment, SubmarketAPI storage) {
        if (storage == null) return;

        int count = detachment.getCargo().getMarines();
        PersonnelData det = getData(detachment, count);
        PersonnelAtEntity at = PlayerFleetPersonnelTracker.getInstance().getDroppedOffAt(Commodities.MARINES, storage.getMarket().getPrimaryEntity(), storage, true);
        PlayerFleetPersonnelTracker.transferPersonnel(det, at.data, count, null);
    }

    // doCleanup drops holders of dead entities, this just does it at once
    public static void clear(CampaignFleetAPI detachment) {
        PlayerFleetPersonnelTracker tracker = PlayerFleetPersonnelTracker.getInstance();
        PersonnelAtEntity pae = tracker.getDroppedOffAt(Commodities.MARINES, detachment, null, false);
        if (pae != null) tracker.getDroppedOff().remove(pae);
        tracker.update();
    }

    private static PersonnelData getData(CampaignFleetAPI detachment, int count) {
        PersonnelData data = PlayerFleetPersonnelTracker.getInstance().getDroppedOffAt(Commodities.MARINES, detachment, null, true).data;
        data.numMayHaveChanged(count, false);
        return data;
    }
}
