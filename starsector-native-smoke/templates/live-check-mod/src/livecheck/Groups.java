package livecheck;

import java.util.ArrayList;
import java.util.List;

public final class Groups {
    private Groups() {
    }

    // comma separated groups run one after the other in one game, far faster than one boot per group.
    // the runner script reads the case labels below to check group names before it starts the game
    public static List<Step> get(String group) {
        List<Step> all = new ArrayList<>();
        for (String one : group.split(",")) {
            switch (one) {
                case "example":
                    all.addAll(ExampleChecks.steps());
                    break;
                default:
                    throw new IllegalArgumentException("Unknown group " + one);
            }
        }
        return all;
    }
}
