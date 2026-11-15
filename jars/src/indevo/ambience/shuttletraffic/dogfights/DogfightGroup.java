package indevo.ambience.shuttletraffic.dogfights;

import java.util.ArrayList;
import java.util.List;

// a lone fighter, a pair or a vic; wingmen keep formation until the leader engages
public class DogfightGroup {

    public final int side;
    public final WingType.Role role;
    public final List<Dogfighter> members = new ArrayList<>(3);

    public DogfightGroup(int side, WingType.Role role) {
        this.side = side;
        this.role = role;
    }

    public Dogfighter leader() {
        for (Dogfighter member : members) if (member.isFighting()) return member;
        return null;
    }

    public boolean isAlive() {
        return leader() != null;
    }
}
