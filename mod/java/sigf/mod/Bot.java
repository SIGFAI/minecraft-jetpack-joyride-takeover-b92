package sigf.mod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The demo pilot: runs the tunnel, chases coins, hovers over scientists and sidesteps missiles. */
final class Bot {
	private Bot() {}

	static boolean jump;
	static long startTick = -1, sciSince = -1, sciIgnoreUntil = 0;

	static Vec3 step(ServerPlayer p, Joy.St s, Vec3 mv) {
		if (startTick < 0) startTick = Joy.now;
		double t = (Joy.now - startTick) / 20.0;
		double px = p.getX(), py = p.getY(), pz = p.getZ();
		double lo = Hall.z0 + 0.5 - 4, hi = Hall.z0 + 0.5 + 4;
		double ty = Hall.y0 + 6.5 + 2.2 * Math.sin(t * 0.6), tz = Hall.z0 + 0.5, vx = 0.30;

		Villager sci = null;
		for (Villager v : p.level().getEntitiesOfClass(Villager.class, new AABB(px - 3, Hall.y0 - 1, Hall.z0 - 7, px + 16, Hall.y0 + 4, Hall.z0 + 8), e -> e.isAlive() && e.entityTags().contains("jj_sci"))) {
			if (sci == null || v.getX() < sci.getX()) sci = v;
		}
		Joy.Coin target = null;
		for (Joy.Coin c : Joy.coins) {
			if (c.physics) continue;
			double dx = c.pos.x - px;
			if (dx > 1.5 && dx < 14 && (target == null || c.pos.x < target.pos.x)) target = c;
		}
		if (sci != null && Joy.now < sciIgnoreUntil) sci = null;
		if (sci == null) sciSince = -1;
		else if (sciSince < 0) sciSince = Joy.now;
		else if (Joy.now - sciSince > 120) { sciIgnoreUntil = Joy.now + 500; sciSince = -1; sci = null; }
		if (sci != null) {
			ty = Hall.y0 + 4.5;
			tz = sci.getZ();
			vx = Math.max(0.04, Math.min(0.3, (sci.getX() - 3.6 - px) * 0.1));
		} else if (target != null) {
			ty = target.pos.y - 0.6;
			tz = target.pos.z;
		}
		for (Joy.Missile m : Joy.missiles) {
			double dx = m.pos.x - px;
			if (dx > 0 && dx < 30 && Math.abs(m.pos.z - pz) < 2.4 && Math.abs(m.pos.y - py - 1) < 2.6) {
				tz = m.pos.z > pz ? Math.max(lo, pz - 4) : Math.min(hi, pz + 4);
			}
		}
		tz = Math.max(lo, Math.min(hi, tz));
		ty = Math.max(Hall.y0 + 0.5, Math.min(Hall.y0 + 13, ty));
		double want = t < 2.2 ? -0.08 : Math.max(-0.3, Math.min(0.42, (ty - py) * 0.22));
		double vy = mv.y + (want - mv.y) * 0.4;
		jump = t > 2.2 && vy > -0.03;
		double vz = Math.max(-0.22, Math.min(0.22, (tz - pz) * 0.2));
		return new Vec3(vx, vy, vz);
	}
}
