package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.DiscoverEntityPlugin;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import indevo.console.PetCommand;
import indevo.ids.Ids;
import indevo.industries.petshop.PetMood;
import indevo.industries.petshop.PetWant;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;

import java.util.ArrayList;
import java.util.List;

// pet 2.2: favourites, hearts 4 and 5, wants, lapses and sulking
public final class PetsWantsChecks {
    private static final Logger log = Global.getLogger(PetsWantsChecks.class);

    private PetsWantsChecks() {
    }

    private static PetStatusManager manager() {
        return PetStatusManager.getInstance();
    }

    private static FleetMemberAPI ship(String name) {
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        member.setShipName(name);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
        member.getRepairTracker().setCR(0.7f);
        return member;
    }

    private static Pet pet(String species, String name, FleetMemberAPI on) {
        Pet pet = new Pet(species, name);
        manager().register(pet);
        pet.place(on, null);
        return pet;
    }

    private static Pet get(Ctx c, String key) {
        return manager().get((String) c.get(key));
    }

    private static void cmd(String args) {
        new PetCommand().runCommand(args, BaseCommand.CommandContext.CAMPAIGN_MAP);
    }

    private static String state(Pet p) {
        return p.name + " fav " + p.favourite + " hearts " + p.getHearts() + " bond " + p.bondDays + " want " + p.want + " left " + Math.round(p.wantDaysLeft)
                + " cooldown " + Math.round(p.wantCooldownDays) + " lapses " + p.lapsedWants + " mood " + p.getMood();
    }

    private static void jump(SectorEntityToken to) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        Global.getSector().doHyperspaceTransition(fleet, fleet, new JumpPointAPI.JumpDestination(to, null));
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            if (!c.market("a").hasIndustry(Ids.PET_STORE)) c.market("a").addIndustry(Ids.PET_STORE);
            ((PetShop) c.market("a").getIndustry(Ids.PET_STORE)).finishBuildingOrUpgrading();
            Global.getSector().getPlayerFleet().getCargo().addCommodity("food", 500f);

            int before = manager().getFavourites().size();
            Pet ace = pet("doge", "Ace", ship("ISS Ace Home"));
            c.expect(before > 0 || ace.favourite, "the first pet is starred by itself (" + before + " favourites before)");
            Pet bea = pet("doge", "Bea", ship("ISS Bea Home"));
            Pet cid = pet("doge", "Cid", ship("ISS Cid Home"));
            Pet dot = pet("doge", "Dot", ship("ISS Dot Home"));
            c.expect(!bea.favourite && !dot.favourite, "later pets are not starred by themselves");
            for (Pet p : manager().getFavourites()) if (p != ace) p.setFavourite(false);
            bea.setFavourite(true);
            cid.setFavourite(true);
            c.expect(manager().getFavourites().size() == 3 && !manager().canFavourite(dot) && manager().canFavourite(bea), "3 favourites, a 4th is blocked");
            for (String k : new String[]{"ace", "bea", "cid", "dot"}) c.put(k, manager().getPets().stream().filter(p -> p.name.equalsIgnoreCase(k)).findFirst().get().id);

            cmd("hearts " + dot.id + " 3");
            dot.addBond(1000f);
            c.expect(dot.getHearts() == 3, "a non-favourite stays at 3 hearts (" + dot.bondDays + ")");
            cmd("hearts " + ace.id + " 3");
            int answers = 0;
            while (ace.getHearts() < 5 && answers < 20) {
                ace.addBond(30f);
                answers++;
            }
            c.expect(ace.getHearts() == 5 && answers == 10, "a favourite reaches 5 hearts through 10 answers of +30 (" + answers + ")");
            cmd("hearts " + bea.id + " 3");
            c.put("beaBond", bea.bondDays);
            c.put("dotBond", dot.bondDays);
            c.note(state(bea));
            jump(Global.getSector().getHyperspace().createToken(Global.getSector().getPlayerFleet().getLocationInHyperspace().x + 300f, Global.getSector().getPlayerFleet().getLocationInHyperspace().y));
            c.days(1f);
        });
        steps.add(c -> {
            Pet bea = get(c, "bea");
            c.expect(Global.getSector().getPlayerFleet().isInHyperspace(), "the fleet jumped to hyperspace");
            c.near(bea.bondDays, (Float) c.get("beaBond") + 3f, 0.6f, "a favourite gets +3 bond days for a jump");
            c.near(get(c, "dot").bondDays, (Float) c.get("dotBond"), 0.001f, "a non-favourite at 3 hearts gets nothing from a jump");
            StarSystemAPI system = c.market("a").getStarSystem();
            jump(system.getCenter());
            c.days(1f);
        });
        steps.add(c -> {
            Pet bea = get(c, "bea");
            c.near(bea.bondDays, (Float) c.get("beaBond") + 6f, 1.2f, "and +3 for the jump back");
            Global.getSector().getPlayerFleet().setLocation(c.market("a").getPrimaryEntity().getLocation().x + 200f, c.market("a").getPrimaryEntity().getLocation().y);

            // treat: one favourite food from the cargo it eats from
            String food = bea.getData().getFavouriteFood();
            float had = Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(food);
            float bond = bea.bondDays;
            cmd("want " + bea.id + " treat");
            c.expect(bea.want == PetWant.TREAT && Math.round(bea.wantDaysLeft) == 30, "console opens a treat want for 30 days");
            c.expect(manager().getOpenWant() == bea, "one want open in the fleet");
            manager().treat(bea);
            c.expect(bea.want == null, "the treat answers the want");
            c.near(Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(food), had - 1f, 0.01f, "the treat takes 1 " + food);
            c.near(bea.bondDays, bond + 30f, 0.01f, "an answer gives 30 bond days");
            c.expect(bea.getMood() == PetMood.HAPPY, "an answer makes it Happy (" + bea.getMood() + ")");
            c.near(bea.wantCooldownDays, 60f, 0.01f, "a 60 day cooldown after a want");

            cmd("want " + bea.id + " play");
            bea.play();
            c.expect(bea.want == null, "play answers a play want");
            cmd("want " + bea.id + " attention");
            bea.pet();
            c.expect(bea.want == null, "petting answers attention");
            cmd("want " + bea.id + " comfort");
            bea.pet();
            c.expect(bea.want == null, "petting answers comfort");

            // come along: a discovery through the game's discover plugin
            cmd("want " + bea.id + " come_along");
            SectorEntityToken cache = Global.getSector().getPlayerFleet().getContainingLocation().addCustomEntity(null, "Pet check cache", "supply_cache", Factions.NEUTRAL);
            cache.setDiscoverable(true);
            cache.setLocation(Global.getSector().getPlayerFleet().getLocation().x + 50f, Global.getSector().getPlayerFleet().getLocation().y);
            DiscoverEntityPlugin plugin = Global.getSector().getGenericPlugins().pickPlugin(DiscoverEntityPlugin.class, cache);
            if (plugin != null) plugin.discoverEntity(cache);
            c.expect(plugin != null && bea.want == null, "a discovery answers come along");

            // shore leave: open a real market
            cmd("want " + bea.id + " shore_leave");
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog(c.market("a").getPrimaryEntity());
            c.frames(60);
        });
        steps.add(c -> {
            Pet bea = get(c, "bea");
            c.expect(bea.want == null, "opening a market answers shore leave (" + bea.want + ")");
            if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null) Global.getSector().getCampaignUI().getCurrentInteractionDialog().dismiss();
            c.keepDialog(false);
            c.note(state(bea));
            // the fleet waits 5 days after a want before the next one opens
            c.days(4f);
        });
        steps.add(c -> {
            Pet open = manager().getOpenWant();
            c.expect(open == null, "no new want within 5 days of the last (" + (open == null ? "none" : state(open)) + ")");
            c.days(2f);
        });
        steps.add(c -> {
            Pet open = manager().getOpenWant();
            c.expect(open != null && open != get(c, "bea") && open.favourite, "after 5 days another favourite off cooldown opens a want (" + (open == null ? "none" : state(open)) + ")");
            c.expect(open == null || open.want != PetWant.COMFORT, "a want opened by time is never comfort");
            for (String k : new String[]{"ace", "bea", "cid", "dot"}) c.note(state(get(c, k)));

            // a pet that leaves the fleet drops its want without a lapse
            Pet cid = get(c, "cid");
            cmd("want " + cid.id + " attention");
            cid.place(null, (PetShop) c.market("a").getIndustry(Ids.PET_STORE));
            c.days(0.2f);
        });
        steps.add(c -> {
            Pet cid = get(c, "cid");
            c.expect(cid.want == null && cid.lapsedWants == 0, "a stored pet drops its want, no lapse (" + state(cid) + ")");
            Pet bea = get(c, "bea");
            cmd("want " + bea.id + " attention");
            c.days(30.5f);
        });
        steps.add(c -> {
            Pet bea = get(c, "bea");
            c.expect(bea.want == null && bea.lapsedWants == 1, "a want lapses after 30 days (" + state(bea) + ")");
            cmd("want " + bea.id + " attention");
            c.days(30.5f);
        });
        steps.add(c -> {
            Pet bea = get(c, "bea");
            c.expect(bea.lapsedWants == 2 && bea.getMood() != PetMood.SULKING, "two lapses, not sulking yet (" + state(bea) + ")");
            cmd("want " + bea.id + " attention");
            c.days(30.5f);
        });
        steps.add(c -> {
            Pet bea = get(c, "bea");
            c.expect(bea.lapsedWants == 3 && bea.getMood() == PetMood.SULKING, "three lapses in a row: Sulking (" + state(bea) + ")");
            c.shot("petswants-sulking");
            cmd("want " + bea.id + " play");
            bea.play();
            c.expect(bea.lapsedWants == 0 && bea.getMood() != PetMood.SULKING, "the next answer ends sulking (" + state(bea) + ")");
            bea.lapsedWants = 3;
            c.expect(bea.getMood() == PetMood.SULKING, "sulking again for the unstar case");
            bea.setFavourite(false);
            c.expect(bea.lapsedWants == 0 && bea.getMood() != PetMood.SULKING && !bea.favourite, "an unstar ends sulking (" + state(bea) + ")");
            bea.setFavourite(true);

            // the held state: three favourites, Dot not starred, Ace waits for play
            Pet cid = get(c, "cid");
            cid.place(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy().stream().filter(m -> "ISS Cid Home".equals(m.getShipName())).findFirst().get(), null);
            Pet ace = get(c, "ace");
            String sw = System.getProperty("ie.switches", "");
            cmd("want " + ace.id + " " + (sw.contains("treat") ? "treat" : sw.contains("attention") ? "attention" : sw.contains("comealong") ? "come_along" : "play"));
            for (String k : new String[]{"ace", "bea", "cid", "dot"}) c.note(state(get(c, k)));
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog(c.market("a").getPrimaryEntity());
            c.frames(60);
        });
        steps.add(c -> {
            Global.getSector().addTransientScript(new Watch());
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }

    // logs each favourite and want change while the game is held
    static final class Watch implements com.fs.starfarer.api.EveryFrameScript {
        private String last = "";

        public boolean isDone() {
            return false;
        }

        public boolean runWhilePaused() {
            return true;
        }

        public void advance(float amount) {
            StringBuilder s = new StringBuilder();
            for (String k : new String[]{"ace", "bea", "cid", "dot"}) {
                Object id = Global.getSector().getPersistentData().get("ie_check_" + k);
                Pet p = id == null ? null : manager().get((String) id);
                if (p != null) s.append(p.name).append(" fav ").append(p.favourite).append(" want ").append(p.want).append(" bond ").append(Math.round(p.bondDays)).append(" mood ").append(p.getMood()).append("; ");
            }
            s.append("food ").append(Math.round(Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity("food")));
            String now = s.toString();
            if (!now.equals(last)) log.info("LIVE_CHECK note: wantwatch " + now);
            last = now;
        }
    }
}
