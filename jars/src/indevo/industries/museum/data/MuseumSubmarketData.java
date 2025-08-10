package indevo.industries.museum.data;

public class MuseumSubmarketData {
    public String submarketID;
    public String submarketName;
    public boolean showInFleetScreen;
    public boolean showInCargoScreen;

    public MuseumSubmarketData(String submarketID, String submarketName, boolean showInFleetScreen, boolean showInCargoScreen) {
        this.submarketID = submarketID;
        this.submarketName = submarketName;
        this.showInFleetScreen = showInFleetScreen;
        this.showInCargoScreen = showInCargoScreen;
    }
}
