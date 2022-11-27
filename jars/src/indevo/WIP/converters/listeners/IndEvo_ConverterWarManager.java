package indevo.converters.listeners;

import indevo.plugins.timers.IndEvo_newDayListener;

public class IndEvo_ConverterWarManager implements IndEvo_newDayListener {

    //converters take over a colony if a paperclip core is installed in any industry other than a centralization bureau
    //planet descriptions change depending on time it has been since takeover
    //3 months since takeover - there is still a black market, but not an open one, description says "survivors willing to trade"
    //6 months - No market, description "Resistance groups swarm comms with requests for rescure" mention children and women or something
    //past 9 months "Only static greets you on the once active resistance comms channels. You can see nanite swarms covering broken cities,
    //like grotesque snow on a silent grave."

    //When an active converter colony exists, set up resource extraction outposts and manufacturing hubs that self destruct once the hive is killed,
    // but otherwise supply it with stuff, Since we will have to move the converters to their own economy or they'll crash it
    //These hubs spawn a few patrols (scaling with amt. of stations in system, less patrols the more stations as to not spam it)
    //via custom industry, more get established over time (maybe with a limit...)
    //start taking over in-system planets immediately (core took its time to plot, after all...)
    //once that is done, start spawning the mining and manuf. hubs to reach self-sufficiency
    //Specifically spawn a ship building hub that affects ship quality when destroyed
    //Ressource Extractor hubs affect ship quantity
    //once in system is done, invade other systems and repeat the cycle for each system

    //the player can free the planets by destroying the orbital station and "bombing" the hubs, or invading them with sufficient marine strengh?
    //if a takeover is fresh, they might also be able to help the local resistance somehow
    //all of them cause the planet to revert (unrest, disable buildings ect)
    //planet can not be taken over again after that

    //the invasion for each system ends once there is no !Planet! with converter presence, hubs don't count
    //systems are on a lockout timer and will not be targetted for an expansion for 3 months

    //will have to steal the invasion intel from nex to make the fleets go

    //Reward for defeating:
    //Converters build teleporters on the planets they have taken over to allow instant transfers (Transmat Node)
    //Network only functions if you are largest power in a system with a dyson sphere
    //Dyson sphere gets built if a system under converter control achieves the maximum efficiency (all planets under conv. control, all outposts built, +6 months)


    @Override
    public void onNewDay() {

    }

}
