package indevo.achievements;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import indevo.exploration.meteor.entities.MeteorEntity;
import indevo.industries.artillery.scripts.CampaignAttackScript;
import indevo.utils.ModPlugin;
import org.magiclib.achievements.MagicAchievement;
import org.magiclib.achievements.MagicAchievementManager;
import org.magiclib.achievements.MagicTargetListAchievement;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class Achievements {

    public static final String YOU_MONSTER = "IndEvo_ach_youMonster";
    public static final String FULL_LIFE = "IndEvo_ach_fullLife";
    public static final String IMMORTAL_HAMPTER = "IndEvo_ach_immortalHampter";
    public static final String SPEEDRUN = "IndEvo_ach_speedrun";
    public static final String WARSHIP = "IndEvo_ach_warship";
    public static final String PRIORITIES = "IndEvo_ach_priorities";
    public static final String GOOD_CAPTAIN = "IndEvo_ach_goodCaptain";
    public static final String ONE_JOB = "IndEvo_ach_oneJob";
    public static final String MINESWEEPER = "IndEvo_ach_minesweeper";
    public static final String COLLECT_THEM_ALL = "IndEvo_ach_collectThemAll";
    public static final String DANGER_CLOSE = "IndEvo_ach_dangerClose";
    public static final String DINOSAUR = "IndEvo_ach_dinosaur";
    public static final String NO_DRILLING_CREW = "IndEvo_ach_noDrillingCrew";
    public static final String VISIBILITY_ZERO = "IndEvo_ach_visibilityZero";
    public static final String SAFETY_THIRD = "IndEvo_ach_safetyThird";
    public static final String WORLD_PEACE = "IndEvo_ach_worldPeace";
    public static final String FENG_SHUI = "IndEvo_ach_fengShui";
    public static final String ITS_A_FEATURE = "IndEvo_ach_itsAFeature";
    public static final String BURNOUT = "IndEvo_ach_burnout";
    public static final String MUSEUM = "IndEvo_ach_museum";
    public static final String FREE_SHIPPING = "IndEvo_ach_freeShipping";
    public static final String TOURIST_TRAP = "IndEvo_ach_touristTrap";
    public static final String IDENTITY_CRISIS = "IndEvo_ach_identityCrisis";
    public static final String FLIP_FLOP = "IndEvo_ach_flipFlop";
    public static final String DIPLOMATIC_BAGGAGE = "IndEvo_ach_diplomaticBaggage";
    public static final String REFUND_POLICY = "IndEvo_ach_refundPolicy";
    public static final String RELIC_HOARD = "IndEvo_ach_relicHoard";
    public static final String LUDDS_SAKE = "IndEvo_ach_luddsSake";
    public static final String DELEGATION = "IndEvo_ach_delegation";

    public static final int WARSHIP_PETS = 10;
    public static final float HAMSTER_DAYS = 5 * 365f;
    public static final int MUSEUM_DMODS = 5;
    public static final String PERSONALITIES_KEY = "$IndEvo_ach_personalities";

    private static final String KEYS = "IndEvo_keys";
    private static final Map<String, Integer> GOALS = Map.of(
            MINESWEEPER, 25,
            LUDDS_SAKE, 10,
            RELIC_HOARD, 2000);

    private static final String[] PERSONALITIES = {"timid", "cautious", "steady", "aggressive", "reckless"};
    private static final String[] WORLD_SYMBOLS = {"IndEvo_LightPalace", "IndEvo_DarkPalace", "IndEvo_Memorial", "IndEvo_LeisureOrbitals",
            "IndEvo_Megachurch", "IndEvo_IcePalace", "IndEvo_LavaFortress", "IndEvo_LifeTree", "IndEvo_CloudPainter"};

    private Achievements() {
    }

    public static void complete(String id) {
        MagicAchievement a = get(id);
        if (a == null) return;

        a.completeAchievement();
        a.saveChanges();
        ModPlugin.log("Achievement completed: " + id);
    }

    public static void add(String id, int amount) {
        MagicAchievement a = get(id);
        if (a == null) return;

        float now = progress(a) + amount;
        if (now >= goal(id)) complete(id);
        else a.setProgress(now);
    }

    // a key counts once, so the progress is the number of different keys
    public static void addKey(String id, String key) {
        MagicAchievement a = get(id);
        if (a == null) return;

        Object stored = a.getAchievementMemory().get(KEYS);
        Set<String> keys = new LinkedHashSet<>();
        if (stored instanceof String s && !s.isEmpty()) keys.addAll(Arrays.asList(s.split(",")));
        if (!keys.add(key)) return;

        a.getAchievementMemory().put(KEYS, String.join(",", keys));
        if (keys.size() >= goal(id)) complete(id);
        else a.setProgress((float) keys.size());
    }

    public static void setTarget(String id, String key) {
        if (!(get(id) instanceof MagicTargetListAchievement a)) return;
        if (!a.getTargets().containsKey(key) || a.getTargets().get(key).isComplete()) return;

        a.setTargetComplete(key);
        a.saveChanges();
    }

    public static void addPersonality(PersonAPI officer, String oldId, String newId) {
        String stored = officer.getMemoryWithoutUpdate().getString(PERSONALITIES_KEY);
        Set<String> held = new LinkedHashSet<>();
        if (stored != null && !stored.isEmpty()) held.addAll(Arrays.asList(stored.split(",")));
        held.add(oldId);
        held.add(newId);
        officer.getMemoryWithoutUpdate().set(PERSONALITIES_KEY, String.join(",", held));

        if (held.containsAll(Arrays.asList(PERSONALITIES))) complete(IDENTITY_CRISIS);
    }

    public static void artilleryHit(String type, boolean remote) {
        setTarget(COLLECT_THEM_ALL, type);
        if (remote) complete(DANGER_CLOSE);
    }

    public static void missileCaught(SectorEntityToken source, SectorEntityToken target) {
        if (source == null || !source.isPlayerFleet() || target.hasTag(Tags.FADING_OUT_AND_EXPIRING)) return;
        if (target.getCustomPlugin() instanceof MeteorEntity meteor && !meteor.colliding) complete(NO_DRILLING_CREW);
    }

    public static float cargoValue(CargoAPI cargo) {
        float value = 0f;
        for (CargoStackAPI stack : cargo.getStacksCopy()) value += stack.getBaseValuePerUnit() * stack.getSize();
        if (cargo.getMothballedShips() != null) {
            for (FleetMemberAPI m : cargo.getMothballedShips().getMembersListCopy()) value += m.getBaseValue();
        }

        return value;
    }

    public static int goal(String id) {
        return GOALS.getOrDefault(id, 0);
    }

    public static Map<String, String> targets(String id) {
        Map<String, String> targets = new LinkedHashMap<>();
        if (COLLECT_THEM_ALL.equals(id)) {
            targets.put(CampaignAttackScript.TYPE_RAILGUN, "Railgun");
            targets.put(CampaignAttackScript.TYPE_MORTAR, "Mortar");
            targets.put(CampaignAttackScript.TYPE_MISSILE, "Missile battery");
        } else if (TOURIST_TRAP.equals(id)) {
            for (String industry : WORLD_SYMBOLS) targets.put(industry, Global.getSettings().getIndustrySpec(industry).getName());
        }

        return targets;
    }

    public static float progress(MagicAchievement a) {
        Float p = a.getProgress();
        return p == null || p.isNaN() ? 0f : p;
    }

    // null while magiclib achievements are off, done or not loaded
    private static MagicAchievement get(String id) {
        MagicAchievementManager manager = MagicAchievementManager.getInstance();
        if (!manager.areAchievementsEnabled()) return null;

        MagicAchievement a = manager.getAchievement(id);
        return a == null || a.isComplete() ? null : a;
    }
}
