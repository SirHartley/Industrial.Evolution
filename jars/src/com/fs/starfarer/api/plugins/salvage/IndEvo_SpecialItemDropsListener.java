package com.fs.starfarer.api.plugins.salvage;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.ShowLootListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageEntityGeneratorOld;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import com.fs.starfarer.api.plugins.IndEvo_modPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Most of this is stolen from Extra Systems Reloaded, courtesy of @presidentmattdamon
 * The 4chin retards can stop complaining about fucking loot pool dilution now
 */

public class IndEvo_SpecialItemDropsListener implements ShowLootListener {

    public static void register(){
        if (!Global.getSector().getListenerManager().hasListenerOfClass(IndEvo_SpecialItemDropsListener.class)) Global.getSector().getListenerManager().addListener(new IndEvo_SpecialItemDropsListener(), true);
    }

    @Override
    public void reportAboutToShowLootToPlayer(CargoAPI loot, InteractionDialogAPI dialog) {
        SectorEntityToken entity = dialog.getInteractionTarget();

        List<SalvageEntityGenDataSpec.DropData> dropData = getDropDataFromEntity(entity);

        MemoryAPI memory = entity.getMemoryWithoutUpdate();
        long randomSeed = memory.getLong(MemFlags.SALVAGE_SEED);
        Random random = Misc.getRandom(randomSeed, 100);

        List<SalvageEntityGenDataSpec.DropData> dropValue = generateDropValueList(dropData);
        List<SalvageEntityGenDataSpec.DropData> dropRandom = generateDropRandomList(dropData);

        CargoAPI salvage = SalvageEntity.generateSalvage(random,
                1f, 1f, 1f, 1f, dropValue, dropRandom);
        loot.addAll(salvage);
    }

    private static List<SalvageEntityGenDataSpec.DropData> generateDropValueList(List<SalvageEntityGenDataSpec.DropData> dropData) {
        List<SalvageEntityGenDataSpec.DropData> dropValueList = new ArrayList<>();

        //iterate through drop groups to find groups that should add drops
        for (SalvageEntityGenDataSpec.DropData data : dropData) {
            if (data.group == null) continue;
            if (data.value == -1) continue;

            IndEvo_modPlugin.log(String.format("DropData " + data.group +" [%s] value and [%s] valueMult", data.value, data.valueMult));


            if (data.group.equals("rare_tech") || data.group.equals("rare_tech_low")) {

                int value = -1;
                //rare_tech is more valuable tech-wise than rare_tech_low
                if (data.group.equals("rare_tech")) {
                    value = data.value;
                } else {
                    value = Math.round(data.value * 0.5f);
                }

                if (value != -1) {
                    SalvageEntityGenDataSpec.DropData dropValue = new SalvageEntityGenDataSpec.DropData();
                    dropValue.group = "indEvo_tech_addition";
                    dropValue.valueMult = data.valueMult;
                    dropValue.value = value;

                    IndEvo_modPlugin.log(String.format("Added [%s] value and [%s] valueMult to DropData", dropValue.value, dropValue.valueMult));

                    dropValueList.add(dropValue);
                }
            }

            if (data.group.contains("techmining_first_find")) {
                int value = Math.round(data.value);

                if (value != -1) {
                    SalvageEntityGenDataSpec.DropData dropValue = new SalvageEntityGenDataSpec.DropData();
                    dropValue.group = "indEvo_mining_addition";
                    dropValue.valueMult = data.valueMult;
                    dropValue.value = value;

                    IndEvo_modPlugin.log(String.format("Added [%s] value and [%s] valueMult to DropData", dropValue.value, dropValue.valueMult));

                    dropValueList.add(dropValue);
                }
            }
        }

        return dropValueList;
    }

    private static List<SalvageEntityGenDataSpec.DropData> generateDropRandomList(List<SalvageEntityGenDataSpec.DropData> dropData) {
        List<SalvageEntityGenDataSpec.DropData> dropRandomList = new ArrayList<>();

        //group
        //indEvo_tech_addition
        //indEvo_mining_addition
        //techmining_first_find

        //iterate through drop groups to find groups that should add drops
        for (SalvageEntityGenDataSpec.DropData data : dropData) {
            if (data.group == null) continue;
            if (data.chances == -1) continue;

            IndEvo_modPlugin.log(String.format("DropData " + data.group + " [%s] chances and [%s] maxChances", data.chances, data.maxChances));

            if (data.group.contains("_tech")) {
                int chances = -1;
                //rare_tech is more valuable tech-wise than rare_tech_low
                if (data.group.equals("rare_tech")) {
                    chances = data.chances * 4;
                } else if (data.group.equals("rare_tech_low")) {
                    chances = (int) Math.round(data.chances);
                }

                if (chances != -1) {
                    SalvageEntityGenDataSpec.DropData dropRandom = new SalvageEntityGenDataSpec.DropData();
                    dropRandom.group = "indEvo_tech_addition";
                    dropRandom.maxChances = data.maxChances;
                    dropRandom.chances = chances;

                    IndEvo_modPlugin.log(String.format("Added [%s] chances and [%s] maxChances to DropData", dropRandom.chances, dropRandom.maxChances));

                    dropRandomList.add(dropRandom);
                }
            }

            if (data.group.equals("techmining_first_find")) {
                int chances = (int) Math.round(data.chances);

                if (chances != -1) {
                    SalvageEntityGenDataSpec.DropData dropRandom = new SalvageEntityGenDataSpec.DropData();
                    dropRandom.group = "indEvo_mining_addition";
                    dropRandom.maxChances = data.maxChances;
                    dropRandom.chances = chances;

                    IndEvo_modPlugin.log(String.format("Added [%s] chances and [%s] maxChances to DropData", dropRandom.chances, dropRandom.maxChances));

                    dropRandomList.add(dropRandom);
                }
            }

        }

        return dropRandomList;
    }

    private static List<SalvageEntityGenDataSpec.DropData> getDropDataFromEntity(SectorEntityToken entity) {
        List<SalvageEntityGenDataSpec.DropData> dropData = new ArrayList<>();

        //first get drops assigned directly to entity
        if (entity.getDropRandom() != null) {
            dropData.addAll(entity.getDropRandom());
        }

        if (entity.getDropValue() != null) {
            dropData.addAll(entity.getDropValue());
        }

        //then try to get spec from entity and the spec's drops
        String specId = entity.getCustomEntityType();
        if (specId == null || entity.getMemoryWithoutUpdate().contains(MemFlags.SALVAGE_SPEC_ID_OVERRIDE)) {
            specId = entity.getMemoryWithoutUpdate().getString(MemFlags.SALVAGE_SPEC_ID_OVERRIDE);
        }

        if (specId != null
                && SalvageEntityGeneratorOld.hasSalvageSpec(specId)) {
            SalvageEntityGenDataSpec spec = SalvageEntityGeneratorOld.getSalvageSpec(specId);

            //get drop randoms from that spec
            if (spec != null && spec.getDropRandom() != null) {
                dropData.addAll(spec.getDropRandom());
            }

            if (spec != null && spec.getDropValue() != null) {
                dropData.addAll(spec.getDropValue());
            }
        }

        return dropData;
    }
}
