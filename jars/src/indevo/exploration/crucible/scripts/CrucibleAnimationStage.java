package indevo.exploration.crucible.scripts;

public abstract class CrucibleAnimationStage {
    public final float runtime;
    public float timePassed = 0f;
    public float timePassedTotal = 0f;
    public final float delayBySeconds;
    private boolean runOnce = false;

    public CrucibleAnimationStage(float runtime, float delayBySeconds) {
        this.runtime = runtime;
        this.delayBySeconds = delayBySeconds;
    }

    public boolean isDone(){
        return timePassed >= runtime;
    }

    public void advance(float amt){
        timePassedTotal += amt;
        if (timePassedTotal >= delayBySeconds && !isDone()){
            timePassed += amt;

            if (!runOnce){
                runOnce = true;
                runOnce();
            }

            run(amt);
        }
    }

    abstract void run(float amt);
    abstract void runOnce();
}
