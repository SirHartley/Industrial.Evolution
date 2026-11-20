package indevo.dialogue.research.dialogue;

import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;

public class DropDataCreator {

    public static SalvageEntityGenDataSpec.DropData createDropData(String group, int chances) {
        SalvageEntityGenDataSpec.DropData d = new SalvageEntityGenDataSpec.DropData();
        d.group = group;
        d.chances = chances;
        return d;
    }

    // paid for with relics, survival mode drop fractions must not thin it
    public static SalvageEntityGenDataSpec.DropData createPaidDropData(String group, int chances) {
        SalvageEntityGenDataSpec.DropData d = createDropData(group, chances);
        d.unaffectedByExtMode = true;
        return d;
    }
}
