package indevo.industries.petshop.plugins;

import indevo.industries.petshop.memory.Pet;

import java.util.List;

// smoke probe in the panel's package: reads the package-private reveal state of a PetsView
public final class RevealProbe {
    private RevealProbe() {
    }

    // "idle", "NAME reading C of N" while rows are read, "NAME done" during the glow
    public static String state(PetsView view, List<Pet> pets) {
        PetReveal r = view.reveal;
        for (Pet p : pets) {
            if (p == null || !r.on(p)) continue;
            return r.reading(p) ? p.name + " reading " + r.count() + " of " + r.total() : p.name + " done";
        }
        return "idle";
    }
}
