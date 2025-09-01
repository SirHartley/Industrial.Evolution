package indevo.industries.warehouses.data;

public class WarehouseSubmarketData {
    public String submarketID;
    public String submarketName;
    public boolean showInFleetScreen;
    public boolean showInCargoScreen;
    public boolean linked;

    public WarehouseSubmarketData(String submarketID, String submarketName, boolean showInFleetScreen, boolean showInCargoScreen, boolean linked) {
        this.submarketID = submarketID;
        this.submarketName = submarketName;
        this.showInFleetScreen = showInFleetScreen;
        this.showInCargoScreen = showInCargoScreen;
        this.linked = linked;
    }
}
