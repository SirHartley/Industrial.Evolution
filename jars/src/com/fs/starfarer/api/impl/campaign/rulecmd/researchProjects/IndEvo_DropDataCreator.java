package com.fs.starfarer.api.impl.campaign.rulecmd.researchProjects;

import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;

public class IndEvo_DropDataCreator {

    public static SalvageEntityGenDataSpec.DropData createDropData(String group, int chances) {
        SalvageEntityGenDataSpec.DropData d = new SalvageEntityGenDataSpec.DropData();
        d.group = group;
        d.chances = chances;
        return d;
    }
}
