package iecheck;

import java.util.List;

public final class Groups {
    private Groups() {
    }

    public static List<Step> get(String group) {
        if (group.contains(",")) {
            List<Step> all = new java.util.ArrayList<>();
            for (String one : group.split(",")) all.addAll(get(one));
            return all;
        }

        switch (group) {
            case "commerce":
                return CommerceChecks.steps();
            case "arcmath":
                return ArcMath.steps();
            case "pets":
                return PetsChecks.steps();
            case "ruins":
                return RuinsChecks.steps();
            case "embassy":
                return EmbassyChecks.steps();
            case "colony":
                return ColonyChecks.steps();
            case "industry":
                return IndustryChecks.steps();
            case "path":
                return PathChecks.steps();
            case "senate":
                return SenateChecks.steps();
            case "misc":
                return MiscChecks.steps();
            case "ui0":
                return UiChecks.steps(0);
            case "ui94":
                return UiChecks.steps(94);
            case "parade":
                return ParadeChecks.steps();
            case "meteor":
                return MeteorChecks.steps();
            default:
                throw new IllegalArgumentException("Unknown group " + group);
        }
    }
}
