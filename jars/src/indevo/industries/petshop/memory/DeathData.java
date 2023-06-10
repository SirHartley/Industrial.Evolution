package indevo.industries.petshop.memory;

import indevo.industries.petshop.listener.PetStatusManager;

public class DeathData {
    public String name;
    public String date;
    public float age;
    public float serveTime;
    public PetStatusManager.PetDeathCause cause;
    public boolean inStorage;
    public String marketName;
    public String shipName;

    public DeathData(String name, String date, float age, float serveTime, PetStatusManager.PetDeathCause cause, boolean inStorage, String marketName, String shipName) {
        this.name = name;
        this.date = date;
        this.age = age;
        this.serveTime = serveTime;
        this.cause = cause;
        this.inStorage = inStorage;
        this.marketName = marketName;
        this.shipName = shipName;
    }
}
