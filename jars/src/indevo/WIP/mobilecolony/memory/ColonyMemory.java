package com.fs.starfarer.api.mobileColony.memory;

import com.fs.starfarer.api.Global;

import java.util.LinkedHashMap;
import java.util.Map;

public class ColonyMemory {
    private static final String MEMORY_KEY = "$IndEvo_MobileSystemMemory";

    private static Map<Integer, ColonyMemoryEntry> getMap(){
        Map<String, Object> mem = Global.getSector().getPersistentData();
        Map<Integer, ColonyMemoryEntry> map;

        if (mem.containsKey(MEMORY_KEY)) map = (Map<Integer, ColonyMemoryEntry>) mem.get(MEMORY_KEY);
        else {
            map = new LinkedHashMap<>();
            mem.put(MEMORY_KEY, map);
        }

        return map;
    }

    public static ColonyMemoryEntry get(int i){
        return getMap().get(i);
    }

    public static void add(int i, ColonyMemoryEntry c){
        getMap().put(i, c);
    }

    public static int getNext(){
        int largest = 0;
        for (int i : getMap().keySet()){
            if (i > largest) largest = i;
        }

        return largest;
    }
}
