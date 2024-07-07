package indevo.exploration.distress.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipRecoverySpecialData;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;

import java.awt.*;

import static indevo.ids.Ids.*;

//todo split this up

public class GhostShipSpecial extends BaseSalvageSpecial {

    public static Logger log = Global.getLogger(GhostShipSpecial.class);

    // memory keys for rules.csv
    public static final String CHECK_SPECIAL = "$indevo_checkSalvageSpecialAgain";
    public static final String DESTROY_SHIP = "$indevo_destroyGhostShip";

    // text options
    public static final String BOARD = "board";
    public static final String DEMOLISH = "demolish";
    public static final String NEVERMIND = "nevermind";
    public static final String CONFIRM = "confirm";
    public static final String BOARD_CONTINUE = "board_continue";
    public static final String RECOVER_AFTER_FIGHT = "recover_after_fight";

    // ghost ship hazard levels
    public static enum Danger {
        NONE,
        UNIDENTIFIED,
        TOKEN,
        SIGNIFICANT,
        OVERWHELMING,
    }

    // ghost ship hazard types
    public static enum Type {
        ROGUE_AI,
        HYPERLOST,
        ALIEN,
        PLAGUE,
        NANITE,
        CANNIBAL,
    }

    // ghost ship boarding losses
    public static enum BoardingLosses {
        NONE,
        FEW,
        SOME,
        MANY,
        ALL,
    }

    // static text
    private static final String FAILED_SALVAGE = "You watch in frustration as the inevitable mistakes and accidents caused by a salvage crew stretched too thin compound with the natural hazards of spaceborne recovery ops," +
            " and your engineering team is forced to beat a hasty retreat to their shuttles. As the last boarding craft breaks away from the wreck on a pillar of flame, the hulk shakes itself apart in a series of blinding " +
            "explosions. If the ship was recoverable before, it certainly isn't now.";

    // ghost ship pickers
    public static final WeightedRandomPicker<Danger> DANGERS = new WeightedRandomPicker<>();
    public static final WeightedRandomPicker<Type> TYPES = new WeightedRandomPicker<>();

    static {
        DANGERS.add(Danger.NONE, 2f);
        DANGERS.add(Danger.UNIDENTIFIED, 4f);
        DANGERS.add(Danger.TOKEN, 3f);
        DANGERS.add(Danger.SIGNIFICANT, 2f);
        DANGERS.add(Danger.OVERWHELMING, 1f);
        TYPES.add(Type.ROGUE_AI);
        TYPES.add(Type.HYPERLOST);
        TYPES.add(Type.ALIEN);
        TYPES.add(Type.PLAGUE);
        TYPES.add(Type.NANITE);
        TYPES.add(Type.CANNIBAL);
    }

    public static class GhostShipSpecialData implements SalvageSpecialInteraction.SalvageSpecialData {

        public Danger danger;
        public Type type;
        public CustomCampaignEntityAPI entity;
        public PerShipData shipData;

        public GhostShipSpecialData(CustomCampaignEntityAPI entity, PerShipData ship) {

            log.info("creating GhostShipSpecialData");
            this.shipData = ship;
            this.entity = entity;

            this.danger = DANGERS.pick();
            log.info("picked danger level " + this.danger);

            if (this.danger.equals(Danger.NONE)) {
                this.type = null;
                log.info("not picking a danger type");
            } else {
                this.type = TYPES.pick();
                log.info("picked danger type " + this.type);
            }
        }

        @Override
        public SalvageSpecialInteraction.SalvageSpecialPlugin createSpecialPlugin() {
            return new GhostShipSpecial();
        }
    }

    private GhostShipSpecialData data;
    private boolean recoverable;
    private boolean destroy = false;
    private String secondaryBoardingText = "";
    private String tertiaryBoardingText = "";
    private BoardingLosses losses;

    @Override
    public void init(InteractionDialogAPI dialog, Object specialData) {
        super.init(dialog, specialData);

        data = (GhostShipSpecialData) specialData;
        dialog.getVisualPanel().showImagePortion("illustrations", "terran_orbit", 640, 400, 0, 0, 480, 300);

        addText("Preliminary scans indicate that although the wreck seems cold and lifeless, there are faint energy readings from some of the holds and compartments that suggest the presence of survivors - or danger.");

        options.clearOptions();
        options.addOption("Prepare a boarding party", BOARD);
        options.addOption("Blow the holds and search the wreckage", DEMOLISH);
        options.addOption("Leave it to drift", NEVERMIND);

    }

    public void addHullmod(GhostShipSpecialData data, String hullmod) {

        PerShipData shipData;
        ShipVariantAPI shipVariant;

        if (data != null) {
            shipData = data.shipData;
        } else {
            log.error("GhostShipSpecialData was null, not adding hullmod");
            return;
        }

        if (shipData != null) {
            shipVariant = shipData.variant;
        } else {
            log.error("GhostShipSpecialData's PerShipData was null, not adding hullmod");
            return;
        }

        if (shipVariant != null) {
            shipVariant.addPermaMod(hullmod);
        } else {
            log.error("GhostShipSpecialData's PerShipData's ShipVariantAPI was null, not adding hullmod");
        }
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        Color t = Misc.getTextColor();
        Color h = Misc.getHighlightColor();
        Color b = Misc.getNegativeHighlightColor();

        int marines = playerFleet.getCargo().getMarines();
        int crew = playerFleet.getCargo().getCrew();
        int optimal = (int) data.shipData.variant.getHullSpec().getMinCrew();
        int required = (int) (data.shipData.variant.getHullSpec().getMinCrew() * 0.1f);
        int missingMarines = Math.max(0, optimal - marines);
        int missingCrew = Math.max(0, optimal - crew);

        boolean canBoard = marines >= required && crew >= required;
        boolean haveOptimal = missingMarines + missingCrew <= 0;

        if (BOARD.equals(optionData)) {
            dialog.getVisualPanel().showImagePortion("illustrations", "raid_prepare", 640, 400, 0, 0, 480, 300);

            LabelAPI label = text.addPara("A proper boarding party requires %s crew, and an %s of marines to escort them. "
                    + "You have %s crew and %s marines.",
                    t, haveOptimal ? h : b, "" + optimal, "equal number", "" + crew, "" + marines);
            //label.setHighlight("" + required, "equal number", "" + crew, "" + marines);
            //label.setHighlightColors(haveEnough ? h : b, haveEnough ? h : b);

            if (canBoard && missingCrew > 0) {
                text.addPara("While salvaging can be attempted with less crew, your logistics officer estimates only a %s percent chance that they will sucessfully recover the ship.", b, "" + (100f * (crew / optimal)));
            }

            if (canBoard && missingMarines > 0) {
                text.addPara("Boarding with less than the required number of marines carries a significant risk if the boarding party meets any sort of resistance.", b);
            }

            if (!canBoard) {
                text.addPara("You lack enough marines or crew to even attempt to board and recover the hulk.", b);
            }

            options.clearOptions();
            if (canBoard) {
                options.addOption("Heave to and prepare to board", CONFIRM);
            }
            options.addOption("Nevermind", NEVERMIND);

        } else if (RECOVER_AFTER_FIGHT.equals(optionData)) {

            if (Math.random() < (crew / optimal)) {
                recoverable = true;
                log.info("successfully boarded and recovered ghost ship");
            } else {
                addText(FAILED_SALVAGE);
                recoverable = false;
                destroy = true;
                log.info("WE FUCKED UP, BLEW UP THE GHOST SHIP");
            }

        } else if (CONFIRM.equals(optionData)) {

            // result based on overall danger class (none/unidentified/resistance)
            String notCleared = "While the wreck is cleared without incident, your limited boarding party is unable to properly search every hold and corridor. As your marines  return to their troop carriers, they talk uneasily amongst themselves about strange happenings during the process - the result of systems carrying out last instructions on limited power as they reactivated, or something more sinister?";
            switch (data.danger) {
                case NONE:
                    // no hazard, no ghost ship hullmod unless not properly cleared
                    if (haveOptimal) {
                        addText("The wreck is inert and empty, and you watch squad icons light up green in sequence as your marines successfully complete their checks of various holds and subsections without incident. Your marines return to their troop carriers in an elated mood, talking and joking with one another easily after another job well done.");
                        recoverable = true;
                        log.info("successfully cleared wreck, not a ghost ship");
                    } else {
                        boolean succeed = Math.random() < (crew / optimal);
                        if (succeed) {
                            addText(notCleared);
                            recoverable = true;
                            addHullmod(data, MYSTERY_NOTHING_HULLMOD);
                            log.info("successfully cleared wreck, not a ghost ship but we don't know that since we didn't bring enough people");
                        } else {
                            addText(FAILED_SALVAGE);
                            recoverable = false;
                            destroy = true;
                            log.info("WE FUCKED UP, BLEW UP THE GHOST SHIP");
                        }
                    }
                    break;

                case UNIDENTIFIED:
                    // mysterious hazard, unidentified ghost ship hullmod

                    Type realGhosts = data.type;
                    String hullmod = null;
                    switch (realGhosts) {
                        case ROGUE_AI:
                            hullmod = MYSTERY_ROGUE_AI_HULLMOD;
                            break;
                        case HYPERLOST:
                            hullmod = MYSTERY_HYPERLOST_HULLMOD;
                            break;
                        case ALIEN:
                            hullmod = MYSTERY_ALIEN_HULLMOD;
                            break;
                        case PLAGUE:
                            hullmod = MYSTERY_PLAGUE_HULLMOD;
                            break;
                        case NANITE:
                            hullmod = MYSTERY_NANITE_HULLMOD;
                            break;
                        case CANNIBAL:
                            hullmod = MYSTERY_CANNIBAL_HULLMOD;
                            break;
                    }
                    // sometimes it's not haunted!
                    if (Math.random() < 0.1f) {
                        hullmod = MYSTERY_NOTHING_HULLMOD;
                    }

                    if (haveOptimal) {
                        addText("The wreck appears at first to be inert and empty, but reports slowly trickle in of strange sounds and anomalous sightings as your salvage crews make their way to their stations: Movement caught out of the corner of an eye, machine-like whirring behind sealed bulkheads, and once, most unsettlingly, an almost organic chittering from a supposedly breached hold. Sigils on your tactical readout light up green one after the other as your marines complete their checks, but nobody can shake the feeling that there is something distinctly... off about this vessel.");
                        recoverable = true;
                        addHullmod(data, hullmod);
                        log.info("successfully cleared wreck, mystery " + hullmod + "ghost ship");
                    } else {
                        boolean succeed = Math.random() < (crew / optimal);
                        if (succeed) {
                            addText(notCleared);
                            recoverable = true;
                            addHullmod(data, hullmod);
                            log.info("successfully cleared wreck, mystery " + hullmod + "ghost ship AND we didn't bring enough people");
                        } else {
                            addText(FAILED_SALVAGE);
                            recoverable = false;
                            destroy = true;
                            log.info("WE FUCKED UP, BLEW UP THE GHOST SHIP");
                        }
                    }
                    break;

                default:

                    dialog.getVisualPanel().showImagePortion("illustrations", "_ghost_ship_combat", 640, 400, 0, 0, 480, 300);

                    // roll boarding result
                    losses = BoardingLosses.SOME;
                    switch (data.danger) {
                        case TOKEN:
                            if ((marines / optimal) < Math.random()) {
                                losses = BoardingLosses.SOME;
                                log.info("didn't bring enough marines, failed roll, " + losses + " losses");
                            } else if (missingMarines > 0) {
                                losses = BoardingLosses.FEW;
                                log.info("didn't bring enough marines, passed roll, " + losses + " losses");
                            } else {
                                losses = BoardingLosses.NONE;
                                log.info("brought enough marines, " + losses + " losses");
                            }
                            break;
                        case SIGNIFICANT:
                            if ((marines / optimal) < Math.random()) {
                                losses = BoardingLosses.MANY;
                                log.info("didn't bring enough marines, failed roll, " + losses + " losses");
                            } else if (missingMarines > 0) {
                                losses = BoardingLosses.SOME;
                                log.info("didn't bring enough marines, passed roll, " + losses + " losses");
                            } else {
                                losses = BoardingLosses.FEW;
                                log.info("brought enough marines, " + losses + " losses");
                            }
                            break;
                        case OVERWHELMING:
                            if ((marines / optimal) < Math.random()) {
                                losses = BoardingLosses.ALL;
                                log.info("didn't bring enough marines, failed roll, " + losses + " losses... we fucked up");
                            } else if (missingMarines > 0) {
                                losses = BoardingLosses.MANY;
                                log.info("didn't bring enough marines, passed roll, " + losses + " losses");
                            } else {
                                losses = BoardingLosses.SOME;
                                log.info("brought enough marines, " + losses + " losses");
                            }
                            break;
                    }

                    String boardingText = "Your marines and salvage crew dock with the wrecked ship and quickly cut their way in through the battered exterior, proceeding into its darkened corridors.";
                    boardingText += " "; // space because we're just appending the next bit onto it

                    // set both paragraphs if boarding failed completely
                    if (losses == BoardingLosses.ALL) {
                        dialog.getVisualPanel().showImagePortion("illustrations", "_ghost_ship_death", 640, 400, 0, 0, 480, 300);
                        boardingText += "Abruptly, your comms officer curses and the tactical feed goes dead. Hurried shouts fill the air as your bridge crew works frantically to restore the connection.";
                        secondaryBoardingText = "A few tense minutes pass, the anxious, sick feeling in the pit of your stomach growing with each passing second. Eventually, your comms officer sits back, sweating bullets, and throws a final switch. The tactical console springs back to life - but looking at the tableau of horror painted across its displays, you almost wish it hadn't. Red, everywhere, with revolting streaks and splatters of pink and grey broken only by the shattered remains of your marines' armor. The lone functioning camera feed stutters for a moment, then winks out in a burst of static as if crushed - mercifully cutting off your view.";
                        tertiaryBoardingText = "As you sit in stunned, defeated silence, a disturbing realization dawns on you: You didn't even see what killed them.";

                    } else {
                        // set first paragraph based on resistance type
                        switch (data.type) {
                            case ROGUE_AI:
                                addHullmod(data, ROGUE_AI_HULLMOD);
                                boardingText += "You watch as they clear the first few holds without incident, then suddenly - every camera feed from the troopers cuts out at once, " +
                                        "replaced by complex patterns of lines and color which interweave in a frantic geometric dance. The shapes twist and resolve into a grinning death's " +
                                        "head, rendered in wavering, oversaturated neon that ripples and morphs as if displayed on a malfunctioning holoset. Your comms officer " +
                                        "curses and resets the console in a hiss of static - the feed cuts back in to a chaotic mess of overlapping shouts and gunfire as every automated system left on the ship turns itself to the task of destroying your boarding party.";
                                // set second paragraph based on boarding result
                                switch (losses) {
                                    case NONE:
                                        secondaryBoardingText = "Miraculously, your marine squadrons clear a path to the hulk's central engineering deck without any losses. A team of specialists hurriedly place charges on the automated defense and drone system control circuits, then duck back behind cover as you give the order to detonate. The hull shudders as if in actual pain, emitting shrieks of tortured metal as the few automatons that managed to cut themselves off from the control system in time make a last-ditch assault on your forces - and are easily repelled. Finally, the ship lies still.";
                                        break;
                                    case FEW:
                                        secondaryBoardingText = "Your marine squadrons clear a path to the hulk's central engineering deck, taking only minimal losses along the way. A team of specialists hurriedly place charges on the automated defenses and drone system control circuits, then duck back behind cover as you give the order to detonate. The hull shudders as if in actual pain, emitting shrieks of tortured metal as the few automatons that managed to cut themselves off from the control system in time make a last-ditch assault on your forces - killing a handful of marines and injuring several more, but ultimately being repelled. Finally, the ship lies still.";
                                        break;
                                    case SOME:
                                        secondaryBoardingText = "Your embattled boarding squadrons clear a path to the hulk's central engineering deck under constant attack by bot loaders, droid turrets, and defense drones. A marine specialist hurriedly places charges on the automated defenses and drone system control circuits, then ducks back behind cover as you give the order to detonate. The hull shudders as if in actual pain, emitting shrieks of tortured metal as a sizeable force of automatons that managed to cut themselves off from the control system in time make a last-ditch assault on your forces - ultimately being repelled, albeit at significant cost. Finally, the ship lies still.";
                                        break;
                                    case MANY:
                                        secondaryBoardingText = "Your scraped-together and outnumbered force of marines manage to clear a path to the hulk's central engineering deck, though they take significant losses in doing so. Finally, your sergeant's voice rings out over the command channel. \"Commander, the demo specialist didn't make it - and I'm hit pretty bad myself,\" he winces, flipping a pair of plasma grenades out of the dispenser on his chestplate. \"I'll take out the drone systems - you have the rest of my marines hunker down and hold off what's left, and we'll take this ship yet... It's been an honor, captain.\" He triggers the detonators, immolating himself and the ship's automated defense cores in a blast of white-hot plasma fire. The hull shudders as if in actual pain, emitting shrieks of tortured metal as an overwhelming force of automatons that managed to cut themselves off from the control system in time make a last-ditch assault on your forces - killing a great number of the few marines that were left before they are eventually forced back. Finally, the ship lies still.";
                                        break;
                                }
                                break;
                            case HYPERLOST:
                                addHullmod(data, HYPERLOST_HULLMOD);
                                boardingText += "Minutes pass as the party makes slow progress towards the bridge and engineering centres of the hulk without any evidence of hostiles, but you can't shake the gnawing feeling of anxiety in the pit of your stomach. The entire boarding party seems likewise on edge, and the comms channel buzzes incessantly with reports of strange noises and anomalous sightings - though none followed up on or investigated yield anything but what one would expect from an empty derelict drifting through space.";
                                // set second paragraph based on boarding result
                                switch (losses) {
                                    case NONE:
                                        secondaryBoardingText = "Once the boarders reach the central engineering deck, that changes. Foreboding sounds emanate from all sides of the compartment, and your marine squadrons on their final checks of internal holds and crew chambers begin to call in with similarly unsettling reports. A handful of the ship's original crewmen are reportedly sighted, at different locations, but when pursued seem to vanish before your marines' very eyes. A pained, static-y screaming and wailing is heard throughout the ship, convincing enough that one of your salvage teams cuts open the bulkhead from behind which they heard it, only to find solid-state flux capacitors with neither audio projectors nor room for anything to hide. Nonetheless, your squads finish their sweep and declare the wreck clear.";
                                        break;
                                    case FEW:
                                        secondaryBoardingText = "Once the boarders reach the central engineering deck, that changes. Foreboding sounds emanate from all sides of the compartment, and your marine squadrons on their final checks of internal holds and crew chambers begin to call in with similarly unsettling reports. A handful of the ship's original crewmen are reportedly sighted but when your marines gun them down, other teams call in incoming fire - bodies of your own men are recovered, killed by your own forces' weapons. A pained, static-y screaming and wailing is heard throughout the ship, convincing enough that one of your salvage teams cuts open the bulkhead from behind which they heard it, only to find one of your marines horribly wounded and somehow pinioned in place behind the sealed wall. Nonetheless, your squads finish their sweep and declare the wreck clear.";
                                        break;
                                    case SOME:
                                        secondaryBoardingText = "Once the boarders reach the central engineering deck, that changes. Foreboding sounds emanate from all sides of the compartment, and your marine squadrons on their final checks of internal holds and crew chambers call in with incessant and similarly unsettling reports. An entire squadron of marines drops off the comms net without a trace, and more are found in a sub-hold having apparently stripped off their protective gear and engaged in an orgy of self-destruction - their skin rent with horrible gashes and eyes gouged out with their own thumbs. Nonetheless, your surviving squads finish their checks and report no concrete evidence of hostile action, before quickly beating a retreat back to their shuttles.";
                                        break;
                                    case MANY:
                                        secondaryBoardingText = "That quickly changes, however, and the comms net descends into chaos. Anomalous sightings are reported across the ship - members of the past crew, shuffling, distended shapes with barely-human geometry, and things which defy description and leave all who see them an incoherent, sobbing mess... And then the screaming starts. Several marine squadrons report being fired upon, while others report engaging hostile targets only to recover the bodies and discover them to be your own forces. Three separate marine squadrons disable their comms entirely, and you watch in mute horror as every man and women among them simultaneously removes their heavy armored helmet and, with a blank stare, turns their own weapons against themselves. Nonetheless, the chaos eventually calms enough for your salvage teams to advance, though the few survivors have to be corralled forward at gunpoint to begin the restoration.";
                                        break;
                                }
                                break;
                            case ALIEN:
                                addHullmod(data, ALIEN_HULLMOD);
                                boardingText += "You watch as they clear the first few holds without incident, then suddenly - a blur of violent motion, panicked screams over the comms channel. As the connection dissolves into a chaotic mess of overlapping shouts and gunfire, you manage to make out the panicked, raw voice of one trooper: \"LUDD PRESERVE US! THEY'RE COMING OUT OF THE GODDAMN WALLS!\"";
                                // set second paragraph based on boarding result
                                switch (losses) {
                                    case NONE:
                                        secondaryBoardingText = "Your marines take point and methodically clear the ship from top to bottom, reporting several engagements with small forces of dog-sized, skittering xenomorphs. No match for your boarding parties' CP-rifles and power armor, they are quickly dispatched and the wreck is reported secure for your salvage teams to move in. Your forces suffer no casualties, though some marines return to the flagship with deep scratches in their armor and a wild look in their eyes.";
                                        break;
                                    case FEW:
                                        secondaryBoardingText = "Your marines take point and methodically clear the ship from top to bottom, reporting several engagements with forces of dog-sized, skittering xenomorphs. No match for your boarding parties' CP-rifles and power armor, your forces nonetheless suffer several casualties as squads are briefly overwhelmed by the enemy. Recovered power armor shows deep scratches and in some cases penetration by the creatures' razor-sharp claws and spurs. Eventually, the wreck is secured and your salvage teams are able to move in.";
                                        break;
                                    case SOME:
                                        secondaryBoardingText = "Your marines take point and methodically clear the ship from top to bottom, reporting numerous engagements with large forces of man-sized, skittering xenomorphs. Several teams are swarmed and overwhelmed by the creatures, their power armor punched through or torn apart at the joints and seals by meter-long spurs of bone and razor-sharp claws. Nonetheless, your squads manage to fight their way through and secure the wreck for your salvage teams, though those that return alive are obviously shaken by the experience.";
                                        break;
                                    case MANY:
                                        secondaryBoardingText = "Before your tactical officer can comment, terrified screams arc through the feed as panicked operators desperately scramble to engage the new foe. While most of the cameras show the unidentified threat as nothing more than a blur, a particularly unlucky marine is pushed to the ground and captures a single frame of a hideous grey creature, vaguely humanoid, large fangs extending to reveal a separate mouth. The sickening wet crunch of her skull being punctured is cut off by the termination of the feed, leaving all on the bridge stunned.";
                                        tertiaryBoardingText = "Eventually your forces are able to link up and move forward, deploying motion sensors, flame weapons, and AP mines to clear the halls and ductwork for long enough that your salvage crew can advance and begin the restoration. The few - very few - surviving marines return to your flagship visibly scarred and shaken, many suffering grievous wounds.";
                                        break;
                                }
                                break;
                            case PLAGUE:
                                addHullmod(data, PLAGUE_HULLMOD);
                                boardingText += "Confused mutters, comments building to shouts of disgust start to overlap through the comms channel as the fate of the hulk's former inhabitants becomes clear. Holds and passageways crammed with bodies, corpses in various stages of decay - halted when the vessel lost atmosphere, but resuming slowly as warm air from the salvage shuttles is pumped in.";
                                // set second paragraph based on boarding result
                                switch (losses) {
                                    case NONE:
                                        secondaryBoardingText = "HEV gear and combat prophylactics are quickly deployed by marine medics, and after a brief sweep by their squads to confirm the wreck secure your salvage teams move in to begin the restoration - though it's clear that the ship won't be entirely safe to crew.";
                                        break;
                                    case FEW:
                                        secondaryBoardingText = "HEV gear and combat prophylactics are quickly deployed by marine medics, and after a brief sweep by their squads to confirm the wreck secure your salvage teams move in to begin the restoration. Several marines develop symptoms upon their return to your flagship, and are quickly quarantined - unfortunately you're unable to develop a cure, but losses are minimal.";
                                        break;
                                    case SOME:
                                        secondaryBoardingText = "HEV gear and combat prophylactics are quickly deployed by marine medics, and after a brief sweep by their squads to confirm the wreck secure your salvage teams move in to begin the restoration. Unfortunately, the pathogen appears to still be active and quite a few marines exposed before the danger was known develop symptoms. The affected are rapidly quarantined - the disease, which you suspect of being a rogue bioweapon or engineered plague, doesn't seem to respond to any known form of treatment.";
                                        break;
                                    case MANY:
                                        secondaryBoardingText = "The boarding party begins to show signs of infection before prophylactics and HEV gear can be deployed by their medics, and only a few manage to seal their combat suits in time. Your comms officer has the presence of mind to flick a switch, limiting dissemination of the comm feed to yourself and your bridge crew; you watch in disgust and horror as your marines convulse on the floors of the wreck, black bile oozing from their helmet seals as their bodies undergo a particularly violent and rapid decomposition process even as they scream and sob into the tactical comms. The grim-faced survivors of the marine squadrons - those who were able to deploy hazmat gear - conduct a perfunctory sweep of the ship, and the salvage crew has to be 'encouraged' with threat of court-martial to complete their recovery efforts.";
                                        break;
                                }
                                break;
                            case NANITE:
                                addHullmod(data, NANITE_HULLMOD);
                                boardingText += "As they reach the main engineering sections of the hull, reports begin to filter in of anomalous sightings - surfaces studded with clusters of black cubes, varying in size and orientation, protruding from otherwise seemingly solid bulkheads and corridor walls as if half-pressed into wet clay. Some radiate curving tails of smaller cubes, waving lazily in elegant fractal arcs with the slow spinning of the null-g hulk.";
                                // set second paragraph based on boarding result
                                switch (losses) {
                                    case NONE:
                                        secondaryBoardingText = "Your marines advance through the wreck, giving the scattered areas of strange cubes a wide berth and marking them with warning beacons. Your salvage crews are then able to move in.";
                                        break;
                                    case FEW:
                                        secondaryBoardingText = "Your marines advance through the wreck, stopping briefly to investigate a particularly thick region of the strange cubes. As a tech specialist attempts to remove one for study, there is a blur of motion and she screams in sudden pain as the cube vanishes. Convulsing horribly, she twitches on the floor as her body and armor are rapidly consumed by the multiplying cubes and scattered reports trickle in from other squads across the ship of similar incidents. From then on your marines give the anomalous objects a wide berth, marking infested regions with warning beacons and avoiding them as they finish their sweep of the ship for your salvage teams.";
                                        break;
                                    case SOME:
                                        secondaryBoardingText = "Your marines advance through the wreck, stopping briefly to investigate a particularly thick region of the strange cubes. As a tech specialist attempts to remove one for study, there is a blur of motion and she screams in sudden pain as the cube vanishes. Convulsing horribly, she twitches on the floor as her body and armor are rapidly consumed by the multiplying cubes. The rest of her squad utters shouts of alarm and begins to back away - but before they can move they, too, are consumed by the anomalous objects. You watch in horror, reports trickling in over the comm of similar events occuring throughout the ship. From then on your surviving marines give the anomalous objects a wide berth, marking infested regions with warning beacons and avoiding them as they finish their sweep of the hulk for your salvage teams.";
                                        break;
                                    case MANY:
                                        secondaryBoardingText = "Your marines advance through the wreck through thickets of the anomalous cube objects, penetrating deep into the hull. Suddenly, there is a blur of motion on helmet cams as the cubes spring to life, moving with no visible method of propulsion but preternatural speed. As you watch, squad after squad is consumed by the rapidly multiplying devices, bodies and power armor seemingly transformed to more of the same black squares nigh-instantaneously upon contact. Finally, the chaos ends, the cubes apparently lying dormant once more after turning the bulk of your boarding party into so much fractal geometric debris. A handful of surviving squads emerge from the few untouched holds and chambers they managed to find refuge in, and finish clearing what portions of the ship are free from infestation for your salvage crews to move in.";
                                        break;
                                }
                                break;
                            case CANNIBAL:
                                addHullmod(data, CANNIBAL_HULLMOD);
                                boardingText += "You watch as they clear the first few holds without incident, forcing their way deep into the hull, then suddenly - suit motion sensors blip an alarm, and shouted orders ring out over the command channel. As the point squad rounds a corner, a hail of projectiles fired by primitive chemical firearms forces them to beat a hasty retreat and find cover.";
                                // set second paragraph based on boarding result
                                switch (losses) {
                                    case NONE:
                                        secondaryBoardingText = "Your marines take point and methodically clear the ship from top to bottom, reporting several engagements with small forces of seemingly feral former crew - or perhaps former boarders - armed with slugthrowers and brutal, but effective, improvised weapons. No match for your squads' CP-rifles and power armor, they are quickly dispatched and the wreck is reported secure for your salvage teams to move in. Your forces suffer no casualties, though some marines return to the flagship with damaged armor and the forced bravado common among troops who have just seen combat.";
                                        break;
                                    case FEW:
                                        secondaryBoardingText = "Your marines take point and methodically clear the ship from top to bottom, reporting several engagements with small forces of seemingly feral former crew - or perhaps former boarders - armed with slugthrowers and brutal, but effective, improvised weapons. No match for your squads' CP-rifles and power armor, your forces nonetheless suffer several casualties as a number of the attackers score lucky shots to armor joints and helmet seals. Eventually, the wreck is secured and your salvage teams are able to move in.";
                                        break;
                                    case SOME:
                                        secondaryBoardingText = "Your marines take point and methodically clear the ship from top to bottom, reporting numerous engagements with large forces of seemingly feral former crew - or perhaps former boarders - armed with slugthrowers and brutal, but effective, improvised weapons. Several teams are overwhelmed by the sheer number of attackers, and your marine commander reports their losses in clipped tones over the tactical comm. Nonetheless, your squads manage to fight their way through and secure the wreck for your salvage teams, though those that return alive are obviously shaken by the experience.";
                                        break;
                                    case MANY:
                                        secondaryBoardingText = "It's chaos on the tactical net as squads report engagement left and right by forces of seemingly feral former crew - or perhaps former boarders - armed with slugthrowers and brutal, but effective, improvised weapons. Several marines vanish without a trace, caught in sudden ambushes and vicious booby traps with barely time to let out a terrified scream over the comms, while other squadrons are pinned down, rushed, and brutally hacked to death with kitchen knives and sharpened steel rods. Eventually your forces are able to link up and move forward, deploying heavy weapons and static AP countermeasures to secure the halls for your salvage teams. The few surviving marines return to your flagship visibly scarred and shaken, many suffering grievous wounds.";
                                        break;
                                }
                                tertiaryBoardingText = "The salvage teams report some persistent strange noises in the hull as they go about their work - hurried footsteps, low muttering speech, the rough dragging of metal on metal.";
                                break;
                        }
                    }

                    addText(boardingText);

                    String boardingCall;
                    WeightedRandomPicker<String> calls = new WeightedRandomPicker<>();
                    calls.add("Come on, marines! Do you want to live forever?!");
                    calls.add("Further up and further in!");
                    calls.add("Once more into the breach!");
                    calls.add("Charge, marines! Today is a good day to die!");
                    calls.add("Fix bayonets!");
                    calls.add("Stand fast, marines! Hold the line!");
                    calls.add("Forward, marines! Victory or death!");
                    boardingCall = calls.pick();

                    options.clearOptions();
                    options.addOption(boardingCall, BOARD_CONTINUE);

                    break; // this is for the default: on the switch (danger)
            }

        } else if (BOARD_CONTINUE.equals(optionData)) {
            addText(secondaryBoardingText);
            if (!tertiaryBoardingText.equals("")) {
                addText(tertiaryBoardingText);
            }

            if (losses != BoardingLosses.NONE) {

                int marineLosses = 0;
                int crewLosses = 0;

                switch (losses) {
                    case FEW:
                        marineLosses = (int) ((marines * 0.1f) + (Math.random() * marines * 0.15f));
                        break;
                    case SOME:
                        marineLosses = (int) ((marines * 0.25f) + (Math.random() * marines * 0.5f));
                        break;
                    case MANY:
                        marineLosses = (int) ((marines * 0.7f) + (Math.random() * marines * 0.25f));
                        break;
                    case ALL:
                        marineLosses = optimal;
                        marineLosses = Math.min(marineLosses, playerFleet.getCargo().getMarines());

                }

                marineLosses = Math.max(marineLosses, 2);
                marineLosses = Math.min(marineLosses, playerFleet.getCargo().getMarines());

                playerFleet.getCargo().removeMarines(marineLosses);

                text.addPara("%s marines were lost in the boarding action.", b, "" + marineLosses, "" + crewLosses);
            }

            options.clearOptions();
            if (losses.equals(BoardingLosses.ALL)) {
                options.addOption("Prepare another boarding party", BOARD);
                options.addOption("Blow the holds and search the wreckage", DEMOLISH);
                options.addOption("Nevermind", NEVERMIND);
            } else {
                options.addOption("Continue with the recovery operation", RECOVER_AFTER_FIGHT);
            }

        } else if (DEMOLISH.equals(optionData)) {
            recoverable = false;
            destroy = true;
            log.info("decided to blow up the ghost ship");
            addText("You target several key points in the hulk's structure, and give the order to fire. Bright streaks of ordnance pierce the wreck, spilling the contents of its holds across the local volume in a lazy arc of flash-frozen moisture and errant debris.");

        } else if (NEVERMIND.equals(optionData)) {
            setDone(true);
            setEndWithContinue(false);
            setShowAgain(true);
        }

        if (recoverable) {
            log.info("recovering ghost ship");
            ShipRecoverySpecialData specialData = new ShipRecoverySpecialData(null);
            specialData.addShip(data.shipData);
            log.info("added ship " + data.shipData);
            log.info("variant: " + data.shipData.variant);
            log.info("variant ID: " + data.shipData.variantId);
            Misc.setSalvageSpecial(entity, specialData);
            ShipRecoverySpecial special = new ShipRecoverySpecial();
            special.init(dialog, specialData);
            entity.getMemoryWithoutUpdate().set(CHECK_SPECIAL, true);
            setDone(true);
            setEndWithContinue(true);
            setShowAgain(true);
        } else if (destroy) {
            log.info("destroying ghost ship");
            entity.getMemoryWithoutUpdate().set(DESTROY_SHIP, true);
            setDone(true);
            setEndWithContinue(true);
            setShowAgain(false);
        }
    }
}
