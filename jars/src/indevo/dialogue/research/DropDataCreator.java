package indevo.dialogue.research;

import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;

public class DropDataCreator {

    public static SalvageEntityGenDataSpec.DropData createDropData(String group, int chances) {
        SalvageEntityGenDataSpec.DropData d = new SalvageEntityGenDataSpec.DropData();
        d.group = group;
        d.chances = chances;
        return d;
    }
}
