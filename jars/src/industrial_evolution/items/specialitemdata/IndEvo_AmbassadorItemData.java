package industrial_evolution.items.specialitemdata;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.characters.PersonAPI;

public class IndEvo_AmbassadorItemData extends SpecialItemData {

    private PersonAPI person;

    public IndEvo_AmbassadorItemData(String id, String data, PersonAPI var) {
        super(id, data);
        this.person = var;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = super.hashCode();
        result = prime * result + ((person == null) ? 0 : person.hashCode());
        return result;
    }

    public PersonAPI getPerson() {
        return person;
    }

    public void setPerson(PersonAPI person) {
        this.person = person;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        IndEvo_AmbassadorItemData other = (IndEvo_AmbassadorItemData) obj;
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

        if (person == null) {
            return other.person == null;
        } else return person.equals(other.person);

    }
}
