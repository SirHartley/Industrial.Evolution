package indevo.exploration.meteor.scripts;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.entities.IcyRockEntity;
import indevo.exploration.meteor.entities.MeteorEntity;
import indevo.exploration.meteor.helper.MeteorFactory;
import indevo.exploration.meteor.movement.EmptyMovement;
import org.lwjgl.util.vector.Vector2f;

public class IceroidImpact extends MeteorImpact{

    public IceroidImpact(SectorEntityToken target, SectorEntityToken meteor, boolean dealDamage) {
        super(target, meteor, dealDamage);
    }

    public void spawnAsteroid(float angle, float mult, float relativeSpeed, float size) {
        SectorEntityToken asteroid = MeteorFactory.spawn(fleet.getContainingLocation(), new MeteorEntity.MeteorData(size, new EmptyMovement()), MeteorSwarmManager.MeteroidShowerType.ICEROID);
        asteroid.setFacing((float) Math.random() * 360f);
        asteroid.addTag(IcyRockEntity.TAG_NO_SPLINTERS);

        Vector2f spawnVelocity = Misc.getUnitVectorAtDegreeAngle(angle + 180f);
        // Use relativeSpeed instead of fleet speed for more realistic asteroid fling velocity
        spawnVelocity.scale(relativeSpeed + (20f + 20f * (float) Math.random()) * mult);

        asteroid.getVelocity().set(spawnVelocity);

        Vector2f spawnLocation = Misc.getUnitVectorAtDegreeAngle(angle + 180f);
        spawnLocation.scale(fleet.getRadius());
        Vector2f.add(spawnLocation, fleet.getLocation(), spawnLocation);
        asteroid.setLocation(spawnLocation.x, spawnLocation.y);

        MeteorEntity plugin = (MeteorEntity) asteroid.getCustomPlugin();
        plugin.angleRotation = Math.signum(plugin.angleRotation) * (50f + 50f * (float) Math.random());

        Misc.fadeInOutAndExpire(asteroid, 0.2f, 1f + (float) Math.random(), 1f);
    }
}
