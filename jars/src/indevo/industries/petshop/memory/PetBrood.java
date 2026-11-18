package indevo.industries.petshop.memory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// one busy habitat of an Animal Center: the pair, the brood time, then the young until each is placed
public class PetBrood {

    public final int habitat;
    public final String parentA;
    public final String parentB;
    // clock timestamp the brood started
    public final long start;
    public final float days;
    // null while it runs; rolled when the time is up, registered only once placed
    public List<Pet> young = null;
    // young ids opened on the New young popup
    public final Set<String> opened = new HashSet<>();
    // the New young popup opened by itself once
    public boolean shown = false;

    public PetBrood(int habitat, String parentA, String parentB, long start, float days) {
        this.habitat = habitat;
        this.parentA = parentA;
        this.parentB = parentB;
        this.start = start;
        this.days = days;
    }

    public boolean isReady() {
        return young != null;
    }

    public boolean has(Pet pet) {
        return pet.id.equals(parentA) || pet.id.equals(parentB);
    }

    public List<Pet> getYoung() {
        return young != null ? young : new ArrayList<>();
    }
}
