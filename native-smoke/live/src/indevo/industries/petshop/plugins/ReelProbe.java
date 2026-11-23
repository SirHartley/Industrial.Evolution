package indevo.industries.petshop.plugins;

import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;

// test only, in the panel's package: a Pets view opened on the full family tree of one pet, for the sizzle reel
public final class ReelProbe {
    private ReelProbe() {
    }

    public static PetsView tree(String petId) {
        PetsView view = new PetsView(petId);
        Pet pet = PetStatusManager.getInstance().get(petId);
        view.tab = PetsView.TAB_TREE;
        view.treeRoot = petId;
        view.treeFull = true;
        view.treeSpecies = pet == null ? null : pet.typeID;
        return view;
    }

    public static boolean playing(PetsView view) {
        return view.play != null;
    }
}
