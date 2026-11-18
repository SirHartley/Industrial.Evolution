package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.FleetUtils;
import indevo.abilities.splitfleet.fleetManagement.Behaviour.FleetBehaviour;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMemory;
import indevo.abilities.splitfleet.fleetManagement.LoadoutMemory;
import indevo.abilities.splitfleet.fleetManagement.LoadoutMemory.Loadout;
import indevo.console.PetCommand;
import indevo.ids.Ids;
import indevo.industries.petshop.hullmods.PetHullmod;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;

import java.util.ArrayList;
import java.util.List;

// pet 2.4: signature talents, the Captain person, Tinkerer and Brood deck room, detachments
public final class PetsSignatureChecks {
    private static final Logger log = Global.getLogger(PetsSignatureChecks.class);
    static CampaignFleetAPI parked;

    private PetsSignatureChecks() {
    }

    private static PetStatusManager manager() {
        return PetStatusManager.getInstance();
    }

    private static FleetMemberAPI ship(String variant, String name) {
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
        member.setShipName(name);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
        member.getRepairTracker().setCR(0.7f);
        return member;
    }

    private static FleetMemberAPI member(String name) {
        for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) if (name.equals(m.getShipName())) return m;
        for (CampaignFleetAPI d : DetachmentMemory.getFleetMap().values()) {
            if (d == null) continue;
            for (FleetMemberAPI m : d.getFleetData().getMembersListCopy()) if (name.equals(m.getShipName())) return m;
        }
        return null;
    }

    private static Pet pet(Ctx c, String key, String variant, String shipName, Object... idGrade) {
        Pet pet = new Pet("doge", key);
        manager().register(pet);
        pet.place(ship(variant, shipName), null);
        for (String id : new ArrayList<>(pet.sheet.traits.keySet())) pet.setTrait(id, 0);
        for (int i = 0; i < idGrade.length; i += 2) cmd("trait " + pet.id + " " + idGrade[i] + " " + idGrade[i + 1]);
        cmd("hearts " + pet.id + " 1");
        c.put(key, pet.id);
        return pet;
    }

    private static Pet get(Ctx c, String key) {
        return manager().get((String) c.get(key));
    }

    private static void cmd(String args) {
        new PetCommand().runCommand(args, BaseCommand.CommandContext.CAMPAIGN_MAP);
    }

    private static MutableShipStatsAPI stats(Pet pet) {
        pet.assignedFleetMember.updateStats();
        return pet.assignedFleetMember.getStats();
    }

    private static float pct(MutableStat s, String id) {
        MutableStat.StatMod m = s.getPercentStatMod(id);
        return m == null ? 0f : m.value;
    }

    private static float pct(StatBonus s, String id) {
        MutableStat.StatMod m = s.getPercentBonus(id);
        return m == null ? 0f : m.value;
    }

    private static float flat(StatBonus s, String id) {
        MutableStat.StatMod m = s.getFlatBonus(id);
        return m == null ? 0f : m.value;
    }

    private static float mult(StatBonus s, String id) {
        MutableStat.StatMod m = s.getMultBonus(id);
        return m == null ? 1f : m.value;
    }

    private static int sModCap(FleetMemberAPI m) {
        return Math.round(m.getStats().getDynamic().getMod(Stats.MAX_PERMANENT_HULLMODS_MOD).computeEffective(Misc.MAX_PERMA_MODS));
    }

    private static boolean listed(PersonAPI p) {
        for (OfficerDataAPI od : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) if (od.getPerson() == p) return true;
        return false;
    }

    private static int skills(PersonAPI p) {
        int n = 0;
        for (com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI s : p.getStats().getSkillsCopy()) {
            if (s.getLevel() > 0f && s.getSkill().isCombatOfficerSkill()) n++;
        }
        return n;
    }

    private static String captainState(Pet cap) {
        FleetMemberAPI m = cap.assignedFleetMember;
        PersonAPI seat = m == null ? null : m.getCaptain();
        return "seat " + (seat == null ? "none" : seat.getNameString() + (seat == cap.captain ? " (pet)" : "") + " default " + seat.isDefault()) + ", pet captain " + (cap.captain == null ? "none" : cap.captain.getNameString() + " level " + cap.captain.getStats().getLevel());
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            if (!c.market("a").hasIndustry(Ids.PET_STORE)) c.market("a").addIndustry(Ids.PET_STORE);
            ((PetShop) c.market("a").getIndustry(Ids.PET_STORE)).finishBuildingOrUpgrading();
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            fleet.getCargo().addCommodity("food", 500f);
            fleet.getCargo().addCrew(500);
            fleet.getCargo().getCredits().add(200000f);

            // signature stats on the ship, each pet at 1 heart with one talent
            String h = null;
            Pet time = pet(c, "Time", "wolf_Assault", "ISS Time", "time_skip", 2);
            h = time.getData().hullmod;
            c.put("hullmod", h);
            c.near(pct(stats(time).getTimeMult(), h), 6f, 0.01f, "Time skip 2 grades: time flow +6%");
            Pet small = pet(c, "Small", "wolf_Assault", "ISS Small", "small_target", 2);
            c.near(mult(stats(small).getDynamic().getMod(Stats.DEPLOYMENT_POINTS_MOD), h), 0.9f, 0.0001f, "Small target 2 grades: deployment cost x0.9");
            c.note("Small ship deployment points " + small.assignedFleetMember.getDeploymentPointsCost() + " (a plain wolf costs " + Global.getSettings().getHullSpec("wolf").getSuppliesToRecover() + " to recover)");
            Pet mend = pet(c, "Mend", "wolf_Assault", "ISS Mend", "mender", 2);
            c.near(flat(stats(mend).getDynamic().getMod(PetHullmod.MENDER), h), 0.5f, 0.0001f, "Mender 2 grades: hull repair 0.5% a second");
            ship("wolf_Assault", "ISS Ctrl");
            Pet over = pet(c, "Over", "wolf_Assault", "ISS Over", "overcharge", 3);
            MutableShipStatsAPI so = stats(over);
            c.near(pct(so.getSystemRegenBonus(), h), 30f, 0.01f, "Overcharge 3 grades: system regen +30%");
            c.near(flat(so.getSystemUsesBonus(), h), 1f, 0.01f, "Overcharge 3 grades: one more system use");
            Pet over2 = pet(c, "Over2", "wolf_Assault", "ISS Over2", "overcharge", 2);
            c.near(flat(stats(over2).getSystemUsesBonus(), h), 0f, 0.01f, "Overcharge 2 grades: +20% regen, no extra use yet");
            Pet skel = pet(c, "Skel", "wolf_Assault", "ISS Skel", "skeleton_crew", 10);
            MutableShipStatsAPI ss = stats(skel);
            c.near(mult(ss.getMinCrewMod(), h), 0f, 0.0001f, "Skeleton crew 10 grades: minimum crew x0");
            c.expect(skel.assignedFleetMember.getMinCrew() < 0.5f, "Skeleton crew 10 grades: the ship needs no crew (" + skel.assignedFleetMember.getMinCrew() + ")");
            Pet skel3 = pet(c, "Skel3", "wolf_Assault", "ISS Skel3", "skeleton_crew", 3);
            c.note("Skeleton crew 3 grades: min crew " + skel3.assignedFleetMember.getMinCrew() + " (wolf base " + Global.getSettings().getHullSpec("wolf").getMinCrew() + ")");
            Pet spite = pet(c, "Spite", "wolf_Assault", "ISS Spite", "spite", 2);
            MutableShipStatsAPI sp = stats(spite);
            c.near(pct(sp.getDynamic().getStat(Stats.EXPLOSION_RADIUS_MULT), h), 100f, 0.01f, "Spite 2 grades: explosion radius +100%");
            c.near(pct(sp.getDynamic().getStat(Stats.EXPLOSION_DAMAGE_MULT), h), 100f, 0.01f, "Spite 2 grades: explosion damage +100%");

            // Tinkerer: one more s-mod slot at 5 grades, the cheapest s-mod over the limit goes when it leaves
            Pet tink = pet(c, "Tink", "wolf_Assault", "ISS Tink");
            FleetMemberAPI tm = tink.assignedFleetMember;
            int cap0 = sModCap(tm);
            cmd("trait " + tink.id + " tinkerer 5");
            tm.updateStats();
            int cap1 = sModCap(tm);
            c.expect(cap1 == cap0 + 1, "Tinkerer 5 grades: s-mod limit " + cap0 + " -> " + cap1);
            ShipVariantAPI tv = tm.getVariant();
            String[] mods = {"hardenedshieldemitter", "auxiliarythrusters", "reinforcedhull", "fluxdistributor", "unstable_injector", "fluxcoil"};
            List<String> added = new ArrayList<>();
            for (String id : mods) {
                if (added.size() >= cap1) break;
                if (Global.getSettings().getHullModSpec(id) == null || tv.hasHullMod(id)) continue;
                tv.addPermaMod(id, true);
                added.add(id);
            }
            tm.updateStats();
            String cheapest = added.stream().min((a, b) -> Global.getSettings().getHullModSpec(a).getCostFor(tv.getHullSize()) - Global.getSettings().getHullModSpec(b).getCostFor(tv.getHullSize())).get();
            c.note("Tink s-mods " + tv.getSMods() + ", cheapest " + cheapest + ", Misc.getCurrSpecialMods " + Misc.getCurrSpecialMods(tv) + ", hullmods " + tv.getHullMods() + ", cap " + sModCap(tm));
            cmd("trait " + tink.id + " tinkerer 4");
            c.note("after tinkerer 4: cap " + sModCap(tm) + ", s-mods " + tv.getSMods() + ", member variant same " + (tm.getVariant() == tv));
            c.expect(tv.getSMods().size() == cap0 && !tv.getSMods().contains(cheapest), "Tinkerer down to 4 grades: back to " + cap0 + " s-mods, the cheapest (" + cheapest + ") went (" + tv.getSMods() + ")");
            c.note("after tinkerer 4: hullmods " + tv.getHullMods() + ", perma " + tv.getPermaMods() + ", Misc.getCurrSpecialMods " + Misc.getCurrSpecialMods(tv));
            // the same with the trait removed, and with the pet moving off a ship, without a stats update in between
            for (String way : new String[]{"remove", "move"}) {
                Pet t2 = pet(c, "Tink" + way, "wolf_Assault", "ISS Tink " + way, "tinkerer", 5);
                FleetMemberAPI m2 = t2.assignedFleetMember;
                ShipVariantAPI v2 = m2.getVariant();
                int added2 = 0;
                for (String id : mods) {
                    if (added2 >= cap1) break;
                    if (Global.getSettings().getHullModSpec(id) == null || v2.hasHullMod(id)) continue;
                    v2.addPermaMod(id, true);
                    added2++;
                }
                c.note(way + ": before, cap " + sModCap(m2) + ", s-mods " + v2.getSMods());
                if (way.equals("remove")) cmd("trait " + t2.id + " tinkerer 0");
                else t2.place(ship("wolf_Assault", "ISS Tink new " + way), null);
                m2.updateStats();
                c.expect(v2.getSMods().size() == cap0, "Tinkerer " + (way.equals("remove") ? "removed" : "pet moved off the ship") + ": back to " + cap0 + " s-mods (" + v2.getSMods() + ", cap " + sModCap(m2) + ")");
            }

            // Brood deck: one more bay at 4 grades, a wing in it goes to the cargo when it leaves
            Pet brood = pet(c, "Brood", "drover_Strike", "ISS Brood");
            FleetMemberAPI bm = brood.assignedFleetMember;
            bm.updateStats();
            int bays0 = (int) bm.getStats().getNumFighterBays().getModifiedValue();
            cmd("trait " + brood.id + " brood_deck 4");
            bm.updateStats();
            int bays1 = (int) bm.getStats().getNumFighterBays().getModifiedValue();
            c.expect(bays1 == bays0 + 1, "Brood deck 4 grades: fighter bays " + bays0 + " -> " + bays1);
            bm.getVariant().setWingId(bays1 - 1, "talon_wing");
            int talons = fleet.getCargo().getNumFighters("talon_wing");
            cmd("trait " + brood.id + " brood_deck 3");
            c.expect(bm.getVariant().getWingId(bays1 - 1) == null && fleet.getCargo().getNumFighters("talon_wing") == talons + 1, "Brood deck down to 3 grades: the wing in the lost bay went to the cargo (" + bm.getVariant().getWings() + ")");
            c.days(0.3f);
            c.shot("petssig-room-messages");
        });
        steps.add(c -> {
            // Captain: takes the empty seat with the pet's name, portrait and disposition, round(value) skills
            Pet cap = pet(c, "Cap", "wolf_Assault", "ISS Pod Captain", "captain", 3);
            FleetMemberAPI m = cap.assignedFleetMember;
            PersonAPI p = cap.captain;
            c.expect(p != null && m.getCaptain() == p, "Captain takes the empty seat (" + captainState(cap) + ")");
            if (p == null) return;
            c.expect("Cap".equals(p.getName().getFirst()) && cap.getData().icon.equals(p.getPortraitSprite()) && cap.sheet.disposition.equals(p.getPersonalityAPI().getId()), "Captain has the pet's name, portrait and disposition (" + p.getNameString() + ", " + p.getPortraitSprite() + ", " + p.getPersonalityAPI().getId() + ")");
            c.expect(p.getStats().getLevel() == 2 && skills(p) == 2, "Captain 3 grades: 1.5 rounds to 2 skills (level " + p.getStats().getLevel() + ", skills " + skills(p) + ")");
            c.expect(!p.isDefault() && p.hasTag(Ids.TAG_PET_CAPTAIN) && !listed(p), "Captain is a real captain, tagged, not in the officer list");
            cmd("trait " + cap.id + " captain 7");
            c.expect(p.getStats().getLevel() == 4 && skills(p) == 4, "Captain 7 grades: 3.5 rounds to 4 skills (" + skills(p) + ")");
            c.put("capLevel", (float) p.getStats().getLevel());
            c.put("officers", (float) Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy().size());
            // the fleet screen's unassign
            m.setCaptain(null);
            c.days(0.1f);
        });
        steps.add(c -> {
            Pet cap = get(c, "Cap");
            FleetMemberAPI m = cap.assignedFleetMember;
            c.expect(m.getCaptain() == cap.captain, "after an unassign the pet sits back down (" + captainState(cap) + ")");
            PersonAPI officer = OfficerManagerEvent.createOfficer(Global.getSector().getPlayerFaction(), 2);
            Global.getSector().getPlayerFleet().getFleetData().addOfficer(officer);
            m.setCaptain(officer);
            c.put("officerId", officer.getId());
            c.days(0.1f);
        });
        steps.add(c -> {
            Pet cap = get(c, "Cap");
            FleetMemberAPI m = cap.assignedFleetMember;
            c.expect(m.getCaptain() != null && m.getCaptain().getId().equals(c.get("officerId")), "an officer keeps the seat (" + captainState(cap) + ")");
            PersonAPI officer = m.getCaptain();
            m.setCaptain(null);
            Global.getSector().getPlayerFleet().getFleetData().removeOfficer(officer);
            c.days(0.1f);
        });
        steps.add(c -> {
            Pet cap = get(c, "Cap");
            FleetMemberAPI m = cap.assignedFleetMember;
            c.expect(m.getCaptain() == cap.captain, "with the officer gone the pet sits back down (" + captainState(cap) + ")");
            // a transport detachment strips officers; the pet sits back down there
            Loadout l = new Loadout();
            l.addToMembersList(m);
            l.behaviour = FleetBehaviour.TRANSPORT;
            l.transportTargetMarket = c.market("b").getId();
            l.targetCargo.addSupplies(200f);
            l.targetCargo.addFuel(100f);
            l.targetCargo.addCrew(100);
            LoadoutMemory.setLoadout(1, l);
            FleetUtils.createAndSpawnFleet(l, 1);
            CampaignFleetAPI det = DetachmentMemory.getDetachment(1);
            c.expect(det != null && det.getFleetData().getMembersListCopy().contains(m), "the captain's ship is in a transport detachment");
            PersonAPI seat = m.getCaptain();
            c.note("right after the transport split: seat " + (seat == null ? "none" : seat.getNameString() + " default " + seat.isDefault()));
            c.days(0.2f);
        });
        steps.add(c -> {
            Pet cap = get(c, "Cap");
            FleetMemberAPI m = member("ISS Pod Captain");
            c.expect(m != null && m.getCaptain() == cap.captain, "in the transport detachment the pet sits back down (" + captainState(cap) + ")");
            c.expect(!listed(cap.captain), "the pet captain is not in the officer list");
            c.days(4f);
        });
        steps.add(c -> {
            Pet cap = get(c, "Cap");
            MonthlyReport report = SharedData.getData().getCurrentReport();
            MonthlyReport.FDNode node = report.getNode(report.getNode(MonthlyReport.FLEET), MonthlyReport.OFFICERS);
            List<String> names = new ArrayList<>();
            for (MonthlyReport.FDNode o : node.getChildren().values()) names.add(o.name);
            c.expect(!node.getChildren().containsKey(cap.captain.getId()), "detachment payroll bills no pet captain (" + names + ")");
            FleetUtils.mergeDetachment(1);
            c.days(0.2f);
        });
        steps.add(c -> {
            Pet cap = get(c, "Cap");
            FleetMemberAPI m = member("ISS Pod Captain");
            c.expect(m != null && Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy().contains(m) && m.getCaptain() == cap.captain, "after the merge the pet still commands its ship (" + captainState(cap) + ")");
            int day = Global.getSector().getClock().getDay();
            c.days(32f - day + 1.5f);
        });
        steps.add(c -> {
            Pet cap = get(c, "Cap");
            MonthlyReport report = SharedData.getData().getPreviousReport();
            MonthlyReport.FDNode node = report.getNode(report.getNode(MonthlyReport.FLEET), MonthlyReport.OFFICERS);
            List<String> names = new ArrayList<>();
            for (MonthlyReport.FDNode o : node.getChildren().values()) names.add(o.name + " " + Math.round(o.upkeep));
            c.expect(!node.getChildren().containsKey(cap.captain.getId()), "month end: no salary line for the pet captain (" + names + ")");
            c.expect(cap.assignedFleetMember.getCaptain() == cap.captain, "the pet captain is still seated at month end");

            // the fight: every signature ship and the captain's ship, which is lost
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            FleetMemberAPI keeper = member("ISS Ctrl");
            player.getFleetData().setFlagship(keeper);
            parked = Global.getFactory().createEmptyFleet(Factions.PLAYER, "Pet check parking", true);
            for (FleetMemberAPI mm : player.getFleetData().getMembersListCopy()) {
                String n = mm.getShipName();
                if (n != null && (n.startsWith("ISS Pod") || n.equals("ISS Time") || n.equals("ISS Mend") || n.equals("ISS Ctrl") || n.equals("ISS Over") || n.equals("ISS Spite") || n.equals("ISS Small"))) continue;
                player.getFleetData().removeFleetMember(mm);
                parked.getFleetData().addFleetMember(mm);
            }
            player.getFleetData().setSyncNeeded();
            player.getFleetData().syncIfNeeded();
            CampaignFleetAPI enemy = Global.getFactory().createEmptyFleet(Factions.PIRATES, "Pet check pirates", true);
            enemy.getFleetData().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "hound_Standard"));
            enemy.getFleetData().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "hound_Standard"));
            enemy.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
            enemy.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
            player.getContainingLocation().addEntity(enemy);
            enemy.setLocation(player.getLocation().x + 100f, player.getLocation().y);
            enemy.getFleetData().setSyncNeeded();
            enemy.getFleetData().syncIfNeeded();
            PetsFightChecks.fought = false;
            PetsHeartsChecks.fid = new FleetInteractionDialogPluginImpl();
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog(PetsHeartsChecks.fid, enemy);
            c.frames(60);
        });
        for (int i = 0; i < 4; i++) steps.add(c -> { if (!PetsFightChecks.fought) PetsHeartsChecks.pickPublic(c, PetsHeartsChecks.INTO_BATTLE); });
        for (int i = 0; i < 5; i++) steps.add(c -> PetsHeartsChecks.pickPublic(c, PetsHeartsChecks.OUT_OF_BATTLE));
        steps.add(c -> {
            c.expect(PetsFightChecks.fought, "a real battle ran");
            Pet cap = get(c, "Cap");
            float officers = (Float) c.get("officers");
            c.expect(Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy().size() == Math.round(officers), "after the fight the officer list is unchanged (" + Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy().size() + ")");
            c.note("captain's pet after the fight: " + (cap.isDead() ? "dead " + cap.deathData.cause : cap.isInMenagerie() ? "menagerie" : "on " + cap.assignedFleetMember.getShipName()) + ", captain " + (cap.captain == null ? "none" : "level " + cap.captain.getStats().getLevel()));
            c.expect(cap.captain == null || cap.captain.getStats().getLevel() == Math.round((Float) c.get("capLevel")), "the pet captain gained no XP (level unchanged)");
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            for (FleetMemberAPI mm : parked.getFleetData().getMembersListCopy()) {
                parked.getFleetData().removeFleetMember(mm);
                player.getFleetData().addFleetMember(mm);
            }
            player.getFleetData().setSyncNeeded();
            player.getFleetData().syncIfNeeded();
            c.keepDialog(false);
            c.frames(30);
        });
        steps.add(c -> {
            // the held state: a new captain pet on a ship with no officer for the fleet screen
            pet(c, "Cap2", "wolf_Assault", "ISS Cap Two", "captain", 3);
            c.days(0.2f);
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog(c.market("a").getPrimaryEntity());
            c.frames(60);
        });
        steps.add(c -> {
            log.info("LIVE_CHECK hold");
            c.frames(100000000);
        });
        return steps;
    }
}
