package indevo.industries.changeling.script;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

public abstract class DelayedCampaignNotificationScript implements EveryFrameScript, CampaignNotificationMessager {
        float delayInDays;
        float timePassed = 0f;
        boolean done = false;

        public DelayedCampaignNotificationScript(float delayInDays) {
            this.delayInDays = delayInDays;
        }

        public void register(){
            Global.getSector().addScript(this);
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }

        @Override
        public void advance(float amount) {
            if (done) return;
            timePassed += amount;

            if (Global.getSector().getClock().convertToDays(timePassed) > delayInDays){
                showMessage();
                done = true;
            }
        }
}
