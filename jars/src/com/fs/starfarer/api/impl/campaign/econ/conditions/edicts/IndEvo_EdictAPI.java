package com.fs.starfarer.api.impl.campaign.econ.conditions.edicts;

import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

public interface IndEvo_EdictAPI {
    void printEdictFlavourText(TextPanelAPI text);

    void printEdictEffectText(TextPanelAPI text, MarketAPI market);

    void printEdictRuntimeText(TextPanelAPI text);

    void printRemovalPenaltyText(TextPanelAPI text);

    String getUnavailableReason(MarketAPI market);

    String getShortDesc();

    boolean isPresenceConditionMet(MarketAPI market);

    int getMinRuntime();

    int getRemainingDays();

    int getRemovalPenaltyUnrestDays();

    void removeWithPenalty();

    void removeWithoutPenalty();
}
