package com.fs.starfarer.api.campaign.impl.items.specItemDataExt;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.combat.ShipVariantAPI;

public class IndEvo_ForgeTemplateData extends SpecialItemData {

    private final ShipVariantAPI variant;

    public IndEvo_ForgeTemplateData(String id, String data, ShipVariantAPI var) {
        super(id, data);
        this.variant = var;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = super.hashCode();
        result = prime * result + ((variant == null) ? 0 : variant.hashCode());
        return result;
    }

    public ShipVariantAPI getVariant() {
        return variant;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        IndEvo_ForgeTemplateData other = (IndEvo_ForgeTemplateData) obj;
        if (getData() == null) {
            if (other.getData() != null)
                return false;
        } else if (!getData().equals(other.getData()))
            return false;

        if (getId() == null) {
            if (other.getId() != null)
                return false;
        } else if (!getId().equals(other.getId()))
            return false;

        if (variant == null) {
            return other.variant == null;
        } else return variant.equals(other.variant);

    }
}
