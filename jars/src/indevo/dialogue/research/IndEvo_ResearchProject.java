package indevo.dialogue.research;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

public abstract class IndEvo_ResearchProject implements IndEvo_ResearchProjectAPI {

    private String id;
    private String name;
    private boolean repeatable;
    private int requiredPoints;

    public IndEvo_ResearchProject(String id, String name, int requiredPoints, boolean repeatable) {
        this.id = id;
        this.repeatable = repeatable;
        this.requiredPoints = requiredPoints;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public int getRequiredPoints() {
        return requiredPoints;
    }

    public String getName() {
        return name;
    }

    public Progress getProgress() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        String key = "$IndEvo_ResearchProgress_" + id;

        if (mem.contains(key)) return (Progress) mem.get(key);
        else {
            Progress p = new Progress();
            mem.set(key, p);
            return p;
        }
    }

    //create a list with templates
    //create a memory with progress on each of these templates
    //getProgress method in each template obj

    public static class Progress {
        public boolean redeemed = false;
        public float points = 0f;
    }
}
