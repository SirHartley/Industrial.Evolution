package indevo.industries.derelicts.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.ids.Ids;
import indevo.industries.SharedSubmarketUser;
import indevo.industries.derelicts.utils.IndustrySpecView;
import indevo.items.installable.NamedItemSlotPlugin;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static indevo.industries.derelicts.industry.Ruins.INDUSTRY_ID_MEMORY_KEY;

public class BaseForgeTemplateUser extends SharedSubmarketUser {
    //this is the base for any industry using forge templates - any extension MUST call super.apply/unapply, respectively

    public static Logger log = Global.getLogger(BaseForgeTemplateUser.class);

    private transient IndustrySpecView specView = null;

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }

    @Override
    public boolean isAvailableToBuild() {
        String id = market.getMemoryWithoutUpdate().getString(INDUSTRY_ID_MEMORY_KEY);

        boolean check = (id != null && getId().equals(id))
                && (market.hasIndustry(Ids.RUINS) && !(market.getIndustry(Ids.RUINS).isUpgrading())); //no ruins id specified or wrong general ID

        return check && super.isAvailableToBuild();
    }

    // the csv downgrade only keeps these out of the build list, an instance never offers it
    @Override
    public IndustrySpecAPI getSpec() {
        IndustrySpecAPI base = super.getSpec();
        specView = IndustrySpecView.of(specView, base, base.getUpgrade(), null);
        return specView;
    }

    @Override
    public List<InstallableIndustryItemPlugin> getInstallableItems() {
        List<InstallableIndustryItemPlugin> list = new ArrayList<>();
        if (!super.getInstallableItems().isEmpty()) list.add(new NamedItemSlotPlugin(this, "Forge Template",
                "Forge Templates have different uses depending on the structure they are installed in."));

        return list;
    }

    @Override
    public boolean canDowngrade() {
        return false;
    }

    @Override
    public boolean showShutDown() {
        return false;
    }

    public void apply() {
        super.apply(true);

        //needed for Forge template item tooltips
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        if (!memory.contains("$" + getId())) {
            Global.getSector().getMemoryWithoutUpdate().set("$" + getId(), true);
        }
    }

    @Override
    public boolean isLegalOnSharedSubmarket(CargoStackAPI stack) {
        return false;
    }

    @Override
    public void addTooltipLine(TooltipMakerAPI tooltip, boolean expanded) {

    }
}
