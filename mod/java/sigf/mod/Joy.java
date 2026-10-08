package sigf.mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import sigf.kit.Sigf;

/** Jetpack Joyride inside Minecraft: the runner, the machine gun jetpack, coins, zappers, missiles and scientists. */
final class Joy {
	private Joy() {}

	// --- state ---
	static final Map<UUID, St> stats = new HashMap<>();
	static final List<Coin> coins = new ArrayList<>();
	static final List<Zapper> zappers = new ArrayList<>();
	static final List<Missile> missiles = new ArrayList<>();
	static final List<Gfx.Prop> flashes = new ArrayList<>();
	static final Random rnd = new Random(92);
	static boolean running, bot;
	static long now;
	static double nextX;
	static long nextMissileAt;
	static int patternNo;

	static final class St {
		Vec3 prev;
		double startX;
		int coins, lastMilestone, combo;
		long lastCoin, zapUntil, lastShot;
		JetRig rig;
		boolean thrust;
	}

	static St st(ServerPlayer p) { return stats.computeIfAbsent(p.getUUID(), k -> new St()); }

	static boolean hasJetpack(Player p) { return p.getItemBySlot(EquipmentSlot.CHEST).is(SigfMod.JETPACK); }
	static boolean runner(ServerPlayer p) { return !p.isSpectator() && p.isAlive(); }

	static double floorY() { return Hall.y0; }
	static double midZ() { return Hall.z0 + 0.5; }

	// =====================================================================================================
	// The jetpack worn on the back
	// =====================================================================================================
	static final class JetRig extends Gfx.Prop {
		final Gfx.Part muzzle, plate, tankL, tankR, capL, capR, nozL, nozR, gun, band, flameL, flameR;
		float lastYaw = 999;

		JetRig(Vec3 pos) {
			super(pos);
			plate = block(Blocks.CONCRETE.gray().defaultBlockState(), 0, 1.02, -0.22, 0.46, 0.46, 0.12, false);
			tankL = block(Blocks.IRON_BLOCK.defaultBlockState(), -0.13, 1.06, -0.34, 0.21, 0.62, 0.21, false);
			tankR = block(Blocks.IRON_BLOCK.defaultBlockState(), 0.13, 1.06, -0.34, 0.21, 0.62, 0.21, false);
			capL = block(Blocks.CONCRETE.red().defaultBlockState(), -0.13, 1.42, -0.34, 0.23, 0.1, 0.23, false);
			capR = block(Blocks.CONCRETE.red().defaultBlockState(), 0.13, 1.42, -0.34, 0.23, 0.1, 0.23, false);
			nozL = block(Blocks.CONCRETE.black().defaultBlockState(), -0.13, 0.70, -0.34, 0.15, 0.14, 0.15, false);
			nozR = block(Blocks.CONCRETE.black().defaultBlockState(), 0.13, 0.70, -0.34, 0.15, 0.14, 0.15, false);
			gun = block(Blocks.GOLD_BLOCK.defaultBlockState(), 0, 0.82, -0.28, 0.13, 0.22, 0.2, false);
			band = block(Blocks.CONCRETE.yellow().defaultBlockState(), 0, 1.3, -0.1, 0.5, 0.08, 0.34, false);
			flameL = block(Blocks.CONCRETE.orange().defaultBlockState(), -0.13, 0.5, -0.34, 0.12, 0.01, 0.12, true);
			flameR = block(Blocks.CONCRETE.orange().defaultBlockState(), 0.13, 0.5, -0.34, 0.12, 0.01, 0.12, true);
			muzzle = block(Blocks.CONCRETE.yellow().defaultBlockState(), 0, 0.62, -0.28, 0.01, 0.01, 0.01, true);
		}

		void update(ServerPlayer p, boolean thrust, Vec3 vel) {
			float yaw = p.yBodyRot;
			float flameLen = thrust ? 0.45f + rnd.nextFloat() * 0.45f : 0.01f;
			boolean turned = Math.abs(yaw - lastYaw) > 1.5f;
			lastYaw = yaw;
			Quaternionf q = new Quaternionf().rotationY((float) Math.toRadians(-yaw));
			for (Gfx.Part pt : parts) {
				Vector3f local = localOff(pt);
				if (pt == flameL || pt == flameR) { pt.size.y = flameLen; local.y = 0.63f - flameLen / 2; }
				if (pt == muzzle) { float m = thrust && now % 2 == 0 ? 0.28f : 0.01f; pt.size.set(m, m, m); }
				pt.off.set(q.transform(new Vector3f(local)));
				pt.rot.set(q);
				if (turned || pt == flameL || pt == flameR || pt == muzzle) pt.pose(pt == muzzle ? 0 : 2);
			}
			// the server sees the player a few ticks late: lead the model by the player's speed
			moveTo(p.position().add(vel.scale(2.5)));
		}

		private Vector3f localOff(Gfx.Part pt) {
			if (pt == plate) return new Vector3f(0, 1.02f, -0.22f);
			if (pt == tankL) return new Vector3f(-0.13f, 1.06f, -0.34f);
			if (pt == tankR) return new Vector3f(0.13f, 1.06f, -0.34f);
			if (pt == capL) return new Vector3f(-0.13f, 1.42f, -0.34f);
			if (pt == capR) return new Vector3f(0.13f, 1.42f, -0.34f);
			if (pt == nozL) return new Vector3f(-0.13f, 0.70f, -0.34f);
			if (pt == nozR) return new Vector3f(0.13f, 0.70f, -0.34f);
			if (pt == gun) return new Vector3f(0, 0.82f, -0.28f);
			if (pt == muzzle) return new Vector3f(0, 0.62f, -0.28f);
			if (pt == band) return new Vector3f(0, 1.3f, -0.1f);
			if (pt == flameL) return new Vector3f(-0.13f, 0.5f, -0.34f);
			return new Vector3f(0.13f, 0.5f, -0.34f);
		}
	}

	// =====================================================================================================
	// Coins
	// =====================================================================================================
	static final class Coin extends Gfx.Prop {
		Vec3 vel = Vec3.ZERO;
		boolean physics;
		int age;
		final Gfx.Part part;

		Coin(Vec3 pos) {
			super(pos);
			part = item(new ItemStack(SigfMod.COIN), 0, 0, 0, 0.9);
		}
	}

	static void coin(Vec3 pos) {
		if (coins.size() > 260) return;
		coins.add(new Coin(pos));
	}

	/** Coins burst out of a spot (a dead scientist, a wrecked zapper). */
	static void popCoins(Vec3 at, int n) {
		for (int i = 0; i < n; i++) {
			Coin c = new Coin(at.add(0, 0.5, 0));
			c.physics = true;
			c.vel = new Vec3((rnd.nextDouble() - 0.3) * 0.4, 0.35 + rnd.nextDouble() * 0.3, (rnd.nextDouble() - 0.5) * 0.5);
			coins.add(c);
		}
	}

	static void tickCoins(List<ServerPlayer> ps) {
		boolean spin = now % 4 == 0;
		double minX = Double.MAX_VALUE;
		for (ServerPlayer p : ps) minX = Math.min(minX, p.getX());
		for (var it = coins.iterator(); it.hasNext(); ) {
			Coin c = it.next();
			c.age++;
			if (c.physics) {
				Vec3 v = c.vel.add(0, -0.04, 0).scale(0.98);
				Vec3 np = c.pos.add(v);
				if (np.y < floorY() + 0.6) { np = new Vec3(np.x, floorY() + 0.6, np.z); v = new Vec3(v.x * 0.7, -v.y * 0.5, v.z * 0.7); }
				c.vel = v;
				c.moveTo(np);
			}
			if (spin) {
				float a = (float) Math.toRadians((now / 4 * 80 + (c.age / 40) * 0) % 360 + (Math.abs(c.hashCode()) % 4) * 20);
				c.part.rot.rotationY(a);
				c.part.pose(4);
			}
			boolean taken = false;
			for (ServerPlayer p : ps) {
				if (!runner(p)) continue;
				Vec3 pc = p.position().add(0, 1.0, 0);
				double d = pc.distanceTo(c.pos);
				if (c.physics && c.age > 12 && d < 5) c.vel = c.vel.add(pc.subtract(c.pos).normalize().scale(0.12));
				if (d < 1.7) { collect(p, c); taken = true; break; }
			}
			if (taken || c.pos.x < minX - 25 || c.age > 2400) { c.remove(); it.remove(); }
		}
	}

	static void collect(ServerPlayer p, Coin c) {
		St s = st(p);
		s.combo = now - s.lastCoin < 20 ? Math.min(s.combo + 1, 14) : 0;
		s.lastCoin = now;
		s.coins++;
		ServerLevel lv = Sigf.level();
		lv.sendParticles(new DustParticleOptions(0xFFD21E, 1.2f), c.pos.x, c.pos.y, c.pos.z, 8, 0.25, 0.25, 0.25, 0.02);
		lv.sendParticles(ParticleTypes.END_ROD, c.pos.x, c.pos.y, c.pos.z, 3, 0.2, 0.2, 0.2, 0.05);
		Sigf.sound(SigfMod.COIN_GET, c.pos, 0.7f, 0.9f + s.combo * 0.07f);
		if (s.coins % 10 == 0) {
			p.giveExperiencePoints(3);
		}
		if (s.coins % 5 == 0) p.getInventory().add(new ItemStack(SigfMod.COIN, 5));
	}

	// =====================================================================================================
	// Zappers: two nodes with a lightning sheet between them, in the y/z plane across the tunnel
	// =====================================================================================================
	static final class Zapper extends Gfx.Prop {
		double cx, cy, cz, half, ang, spinRate;
		int hp = 30;
		static final int SEGS = 7;
		final Gfx.Part nodeA, nodeB, glowA, glowB;
		final Gfx.Part[] glow = new Gfx.Part[SEGS], core = new Gfx.Part[SEGS];

		Zapper(double x, double y, double z, double half, double ang, double spinRate) {
			super(new Vec3(x, y, z));
			this.cx = x; this.cy = y; this.cz = z; this.half = half; this.ang = ang; this.spinRate = spinRate;
			nodeA = block(Blocks.IRON_BLOCK.defaultBlockState(), 0, 0, 0, 1.1, 1.1, 1.1, false);
			nodeB = block(Blocks.IRON_BLOCK.defaultBlockState(), 0, 0, 0, 1.1, 1.1, 1.1, false);
			glowA = block(Blocks.GLOWSTONE.defaultBlockState(), 0, 0, 0, 1.25, 0.65, 0.65, true);
			glowB = block(Blocks.GLOWSTONE.defaultBlockState(), 0, 0, 0, 1.25, 0.65, 0.65, true);
			for (int i = 0; i < SEGS; i++) {
				glow[i] = block(Blocks.CONCRETE.yellow().defaultBlockState(), 0, 0, 0, 0.5, 0.4, 1, true);
				core[i] = block(Blocks.CONCRETE.white().defaultBlockState(), 0, 0, 0, 0.3, 0.12, 1, true);
			}
			layout();
		}

		double ay() { return cy + Math.sin(ang) * half; }
		double az() { return cz + Math.cos(ang) * half; }
		double by() { return cy - Math.sin(ang) * half; }
		double bz() { return cz - Math.cos(ang) * half; }

		void layout() {
			double ay = ay(), az = az(), by = by(), bz = bz();
			nodeA.off.set(0, (float) (ay - cy), (float) (az - cz));
			glowA.off.set(nodeA.off);
			nodeB.off.set(0, (float) (by - cy), (float) (bz - cz));
			glowB.off.set(nodeB.off);
			// a jagged bolt from node A to node B, new every tick
			double len = Math.hypot(ay - by, az - bz);
			double ny = (az - bz) / len, nz = -(ay - by) / len; // perpendicular in the y/z plane
			double[] py = new double[SEGS + 1], pz = new double[SEGS + 1];
			for (int i = 0; i <= SEGS; i++) {
				double t = i / (double) SEGS, j = (i == 0 || i == SEGS) ? 0 : (rnd.nextDouble() - 0.5) * 1.1;
				py[i] = ay + (by - ay) * t + ny * j - cy;
				pz[i] = az + (bz - az) * t + nz * j - cz;
			}
			for (int i = 0; i < SEGS; i++) {
				double dy = py[i + 1] - py[i], dz = pz[i + 1] - pz[i], l = Math.hypot(dy, dz) + 0.12;
				Quaternionf q = new Quaternionf().rotationTo(0, 0, 1, 0, (float) dy, (float) dz);
				float th = 0.7f + rnd.nextFloat() * 0.6f;
				glow[i].off.set(0, (float) ((py[i] + py[i + 1]) / 2), (float) ((pz[i] + pz[i + 1]) / 2));
				glow[i].rot.set(q); glow[i].size.set(0.5f, 0.42f * th, (float) l); glow[i].pose(1);
				core[i].off.set(glow[i].off);
				core[i].rot.set(q); core[i].size.set(0.3f, 0.12f * th, (float) l); core[i].pose(1);
			}
			moveTo(new Vec3(cx, cy, cz));
		}

		/** Distance from (y,z) to the sheet. */
		double dist(Vec3 p) {
			double ax = az(), ay = ay(), bx = bz(), by = by();
			double vx = bx - ax, vy = by - ay, wx = p.z - ax, wy = p.y - ay;
			double t = Math.max(0, Math.min(1, (wx * vx + wy * vy) / (vx * vx + vy * vy)));
			return Math.hypot(p.z - (ax + vx * t), p.y - (ay + vy * t));
		}

		boolean touches(Vec3 p, double r) { return Math.abs(p.x - cx) < 0.9 + r && dist(p) < 0.5 + r; }
	}

	static Zapper zapper(double x, double y, double z, double half, double ang, double spin) {
		Zapper zp = new Zapper(x, y, z, half, ang, spin);
		zappers.add(zp);
		return zp;
	}

	static void tickZappers(List<ServerPlayer> ps) {
		ServerLevel lv = Sigf.level();
		double minX = Double.MAX_VALUE;
		for (ServerPlayer p : ps) minX = Math.min(minX, p.getX());
		for (var it = zappers.iterator(); it.hasNext(); ) {
			Zapper z = it.next();
			z.ang += z.spinRate;
			z.layout();
			for (int i = 0; i < 7; i++) {
				double t = rnd.nextDouble();
				lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, z.cx + (rnd.nextDouble() - 0.5) * 0.6,
					z.ay() + (z.by() - z.ay()) * t, z.az() + (z.bz() - z.az()) * t, 1, 0.2, 0.2, 0.2, 0.1);
			}
			if (now % 30 == (z.hashCode() & 7)) Sigf.sound(SigfMod.ZAPPER, new Vec3(z.cx, z.cy, z.cz), 0.5f, 0.9f + rnd.nextFloat() * 0.3f);
			for (ServerPlayer p : ps) {
				if (!runner(p)) continue;
				St s = st(p);
				if (now >= s.zapUntil && z.touches(p.position().add(0, 0.9, 0), 0.4)) {
					s.zapUntil = now + 30;
					zapHit(p, new Vec3(z.cx, z.cy, z.cz));
				}
			}
			for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, new AABB(z.cx - 2, z.cy - z.half - 2, z.cz - z.half - 2, z.cx + 2, z.cy + z.half + 2, z.cz + z.half + 2),
					e -> !(e instanceof Player) && e.isAlive())) {
				if (z.touches(e.position().add(0, e.getBbHeight() / 2, 0), 0.3) && e.tickCount % 10 == 0) {
					e.hurtServer(lv, lv.damageSources().lightningBolt(), 4f);
					lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 1, e.getZ(), 12, 0.3, 0.5, 0.3, 0.2);
				}
			}
			if (z.hp <= 0 || z.cx < minX - 25) {
				if (z.hp <= 0) {
					Sigf.log("zapper destroyed");
					Vec3 c = new Vec3(z.cx, z.cy, z.cz);
					lv.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 1.5, 1.5, 1.5, 0);
					lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 60, 2, 2, 2, 0.5);
					Sigf.sound(SoundEvents.GENERIC_EXPLODE.value(), c, 0.8f, 1.4f);
					popCoins(c, 8);
				}
				z.remove();
				it.remove();
			}
		}
	}

	static void zapHit(ServerPlayer p, Vec3 from) {
		Sigf.log("zap hit");
		ServerLevel lv = Sigf.level();
		Vec3 pp = p.position().add(0, 1, 0);
		lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, pp.x, pp.y, pp.z, 50, 0.4, 0.7, 0.4, 0.4);
		Sigf.sound(SoundEvents.LIGHTNING_BOLT_THUNDER, pp, 0.6f, 1.6f);
		Sigf.sound(SigfMod.ZAPPER, pp, 1.0f, 1.0f);
		p.hurtServer(lv, lv.damageSources().lightningBolt(), 5f);
		p.setDeltaMovement(-0.55, 0.15, (p.getZ() - from.z) * 0.1);
		p.syncVelocity = true;
		p.sendSystemMessage(Component.literal("ZAPPED!").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), true);
	}

	// =====================================================================================================
	// Missiles: a warning, then a rocket along the lane
	// =====================================================================================================
	static final class Missile extends Gfx.Prop {
		int hp = 3;
		final Gfx.Part flame;

		Missile(Vec3 pos) {
			super(pos);
			block(Blocks.CONCRETE.lightGray().defaultBlockState(), 0, 0, 0, 1.5, 0.5, 0.5, false);
			block(Blocks.CONCRETE.red().defaultBlockState(), -0.85, 0, 0, 0.3, 0.36, 0.36, false);
			block(Blocks.CONCRETE.red().defaultBlockState(), 0.5, 0, 0, 0.5, 0.06, 1.1, false);
			block(Blocks.CONCRETE.red().defaultBlockState(), 0.5, 0, 0, 0.5, 1.1, 0.06, false);
			block(Blocks.CONCRETE.black().defaultBlockState(), 0.8, 0, 0, 0.14, 0.34, 0.34, false);
			flame = block(Blocks.CONCRETE.orange().defaultBlockState(), 1.3, 0, 0, 0.8, 0.28, 0.28, true);
		}
	}

	/** A red "!" in the lane, then the rocket. */
	static void incoming(double y, double z, double ahead) {
		ServerPlayer lead = lead();
		if (lead == null) return;
		double x = lead.getX() + ahead;
		Vec3 warn = new Vec3(x, y, z);
		Gfx.Prop w = new Gfx.Prop(warn);
		Gfx.Part t = w.text(Component.literal("!").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), 0, 0, 0, 14);
		Gfx.Part box = w.block(Blocks.CONCRETE.red().defaultBlockState(), 0, 0, 0, 0.3, 2.2, 2.2, true);
		flashes.add(w);
		Sigf.sound(SigfMod.MISSILE, lead.position(), 1.0f, 1.0f);
		Sigf.sound(SoundEvents.NOTE_BLOCK_BIT.value(), lead.position(), 1.2f, 2.0f);
		for (int i = 0; i < 4; i++) {
			int k = i;
			Sigf.after(0.3 * i, () -> Sigf.sound(SoundEvents.NOTE_BLOCK_BIT.value(), Sigf.host() == null ? warn : Sigf.host().position(), 1.0f, k % 2 == 0 ? 2f : 1.6f));
		}
		Sigf.after(1.4, () -> {
			w.remove();
			flashes.remove(w);
			ServerPlayer l = lead();
			double sx = (l == null ? x : l.getX()) + 65;
			Missile m = new Missile(new Vec3(sx, y, z));
			missiles.add(m);
			Sigf.sound(SigfMod.MISSILE, m.pos, 1.2f, 0.8f);
		});
	}

	static void tickMissiles(List<ServerPlayer> ps) {
		ServerLevel lv = Sigf.level();
		double minX = Double.MAX_VALUE;
		for (ServerPlayer p : ps) minX = Math.min(minX, p.getX());
		for (Gfx.Prop w : flashes) {
			boolean on = (now / 3) % 2 == 0;
			for (Gfx.Part pt : w.parts) { if (pt.d instanceof net.minecraft.world.entity.Display.TextDisplay) continue; pt.size.x = 0.4f; pt.size.y = on ? 2.4f : 0.5f; pt.size.z = on ? 2.4f : 0.5f; pt.pose(1); }
		}
		for (var it = missiles.iterator(); it.hasNext(); ) {
			Missile m = it.next();
			m.moveTo(m.pos.add(-0.95, 0, 0));
			float f = 0.5f + rnd.nextFloat();
			m.flame.size.x = 0.8f * f; m.flame.off.x = 0.9f + 0.4f * f; m.flame.pose(1);
			lv.sendParticles(ParticleTypes.FLAME, m.pos.x + 1.6, m.pos.y, m.pos.z, 3, 0.05, 0.1, 0.1, 0.05);
			lv.sendParticles(ParticleTypes.LARGE_SMOKE, m.pos.x + 2.0, m.pos.y, m.pos.z, 2, 0.1, 0.1, 0.1, 0.02);
			boolean boom = m.hp <= 0 || m.pos.x < minX - 30;
			for (ServerPlayer p : ps) {
				if (runner(p) && p.position().add(0, 0.9, 0).distanceTo(m.pos) < 1.9) boom = true;
			}
			if (boom) {
				boolean shot = m.hp <= 0;
				Sigf.log(shot ? "missile shot down" : "missile exploded");
				lv.explode(null, m.pos.x, m.pos.y, m.pos.z, 3.2f, Level.ExplosionInteraction.NONE);
				lv.sendParticles(ParticleTypes.EXPLOSION_EMITTER, m.pos.x, m.pos.y, m.pos.z, 1, 0, 0, 0, 0);
				lv.sendParticles(ParticleTypes.FLAME, m.pos.x, m.pos.y, m.pos.z, 80, 1.2, 1.2, 1.2, 0.15);
				if (shot) popCoins(m.pos, 4);
				for (ServerPlayer p : ps) {
					if (!runner(p) || p.position().distanceTo(m.pos) > 5) continue;
					p.setDeltaMovement(-0.6, 0.3, 0);
					p.syncVelocity = true;
				}
				m.remove();
				it.remove();
			}
		}
	}

	// =====================================================================================================
	// Scientists
	// =====================================================================================================
	static void scientists(double x, int n) {
		for (int i = 0; i < n; i++) {
			Vec3 at = new Vec3(x + rnd.nextDouble() * 6, floorY(), midZ() + (rnd.nextDouble() - 0.5) * 8);
			Villager v = Sigf.spawn(EntityTypes.VILLAGER, at);
			if (v == null) continue;
			v.addTag("jj_sci");
			v.setCustomName(Component.literal("Scientist").withStyle(ChatFormatting.WHITE));
			v.setCustomNameVisible(true);
			v.setPersistenceRequired();
			v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.62);
			coats.put(v.getUUID(), new Coat(at));
		}
	}

	/** The white lab coat, goggles and clipboard worn by a scientist villager. */
	static final class Coat extends Gfx.Prop {
		final Gfx.Part coat, goggles, band, clip;
		float lastYaw = 999;

		Coat(Vec3 pos) {
			super(pos);
			coat = block(Blocks.CONCRETE.white().defaultBlockState(), 0, 0.80, 0, 0.72, 0.96, 0.5, false);
			goggles = block(Blocks.CONCRETE.black().defaultBlockState(), 0, 1.64, 0.27, 0.5, 0.12, 0.08, false);
			band = block(Blocks.CONCRETE.lime().defaultBlockState(), 0, 1.0, 0.27, 0.74, 0.07, 0.04, true);
			clip = block(Blocks.OAK_PLANKS.defaultBlockState(), 0.34, 0.75, 0.3, 0.12, 0.3, 0.22, false);
		}

		void follow(Villager v) {
			float yaw = v.yBodyRot;
			if (Math.abs(yaw - lastYaw) > 2f) {
				lastYaw = yaw;
				Quaternionf q = new Quaternionf().rotationY((float) Math.toRadians(-yaw));
				Vector3f[] loc = { new Vector3f(0, 0.80f, 0), new Vector3f(0, 1.64f, 0.27f), new Vector3f(0, 1.0f, 0.27f), new Vector3f(0.34f, 0.75f, 0.3f) };
				for (int i = 0; i < parts.size(); i++) {
					Gfx.Part pt = parts.get(i);
					pt.off.set(q.transform(new Vector3f(loc[i])));
					pt.rot.set(q);
					pt.pose(2);
				}
			}
			moveTo(v.position());
		}
	}

	static final Map<UUID, Coat> coats = new HashMap<>();

	static void tickScientists(List<ServerPlayer> ps) {
		for (var it = coats.entrySet().iterator(); it.hasNext(); ) {
			var en = it.next();
			Entity e = Sigf.level().getEntity(en.getKey());
			if (e instanceof Villager v && v.isAlive()) en.getValue().follow(v);
			else { en.getValue().remove(); it.remove(); }
		}
		if (now % 15 != 0) return;
		ServerLevel lv = Sigf.level();
		double minX = Double.MAX_VALUE;
		for (ServerPlayer p : ps) minX = Math.min(minX, p.getX());
		for (Villager v : lv.getEntitiesOfClass(Villager.class, new AABB(Hall.x0 - 20, Hall.y0 - 2, Hall.z0 - 8, Hall.x0 + 100000, Hall.y0 + 20, Hall.z0 + 8), e -> e.entityTags().contains("jj_sci") && e.isAlive())) {
			if (v.getX() < minX - 30) { v.discard(); continue; }
			double tz = midZ() + (rnd.nextDouble() - 0.5) * 8.5;
			double tx = v.getX() + (rnd.nextDouble() - 0.5) * 7;
			v.getNavigation().moveTo(tx, floorY(), tz, 1.5);
		}
	}

	static void onDeath(LivingEntity e) {
		if (!e.entityTags().contains("jj_sci")) return;
		ServerLevel lv = Sigf.level();
		lv.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1, e.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
		lv.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0);
		popCoins(e.position().add(0, 0.5, 0), 6);
	}

	// =====================================================================================================
	// The gun
	// =====================================================================================================
	static final Vec3[] NOZ = { new Vec3(-0.13, 0.7, -0.34), new Vec3(0.13, 0.7, -0.34) };

	static void fire(ServerPlayer p) {
		ServerLevel lv = Sigf.level();
		double yaw = Math.toRadians(p.yBodyRot);
		Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 origin = p.position().add(fwd.scale(-0.35)).add(0, 0.8, 0);
		// shell casing
		lv.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.GOLD_NUGGET), origin.x, origin.y + 0.3, origin.z, 1, 0.1, 0.1, 0.1, 0.1);
		Vec3 dir = fwd.scale(0.9).add(0, -1, 0).normalize().add((rnd.nextDouble() - 0.5) * 0.28, 0, (rnd.nextDouble() - 0.5) * 0.28).normalize();
		// lock on to an incoming missile or a zapper ahead
		Vec3 lock = null;
		Object tgt = null;
		double best = 20;
		for (Missile m : missiles) {
			double dx = m.pos.x - p.getX();
			if (dx > 1 && dx < best && Math.abs(m.pos.y - p.getY() - 1) < 4 && Math.abs(m.pos.z - p.getZ()) < 4) { best = dx; lock = m.pos; tgt = m; }
		}
		if (tgt == null) for (Zapper z : zappers) {
			double dx = z.cx - p.getX();
			if (dx > 2 && dx < 9 && dx < best) { best = dx; lock = new Vec3(z.cx, z.cy, z.cz); tgt = z; }
		}
		Vec3 end;
		Object hit = null;
		if (lock != null) {
			end = lock;
			dir = lock.subtract(origin).normalize();
			hit = tgt;
		} else {
			end = origin.add(dir.scale(24));
			BlockHitResult br = lv.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (br.getType() == HitResult.Type.BLOCK) {
				end = br.getLocation();
				if (lv.getBlockState(br.getBlockPos()).is(Blocks.GLASS)) {
					lv.destroyBlock(br.getBlockPos(), false);
					Sigf.sound(SoundEvents.GLASS_BREAK, end, 0.5f, 1.3f);
				}
			}
			Entity victim = null;
			double bd = origin.distanceTo(end);
			AABB box = new AABB(origin, end).inflate(1);
			for (Entity e : lv.getEntities(p, box, e -> e instanceof LivingEntity && !(e instanceof Player) && e.isAlive())) {
				Optional<Vec3> h = e.getBoundingBox().inflate(0.35).clip(origin, end);
				if (h.isPresent()) {
					double d = origin.distanceTo(h.get());
					if (d < bd) { bd = d; victim = e; end = h.get(); }
				}
			}
			hit = victim;
			if (victim == null) {
				BlockPos bp = BlockPos.containing(end.add(dir.scale(0.1)));
				BlockState bs = lv.getBlockState(bp);
				if (!bs.isAir()) lv.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, bs), end.x, end.y, end.z, 6, 0.15, 0.1, 0.15, 0.05);
				lv.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 2, 0.1, 0.1, 0.1, 0.01);
				lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y + 0.1, end.z, 3, 0.1, 0.1, 0.1, 0.2);
			}
		}
		// tracer
		double len = origin.distanceTo(end);
		DustParticleOptions yellow = new DustParticleOptions(0xFFE020, 1.5f);
		for (double d = 0.5; d < len; d += 0.45) {
			Vec3 q = origin.add(dir.scale(d));
			lv.sendParticles(yellow, q.x, q.y, q.z, 1, 0, 0, 0, 0);
		}
		// damage
		if (hit instanceof Missile m) {
			m.hp--;
			lv.sendParticles(ParticleTypes.CRIT, m.pos.x, m.pos.y, m.pos.z, 12, 0.4, 0.3, 0.3, 0.3);
			Sigf.sound(SoundEvents.ANVIL_LAND, m.pos, 0.3f, 2f);
		} else if (hit instanceof Zapper z) {
			z.hp--;
			lv.sendParticles(ParticleTypes.CRIT, z.cx, z.cy, z.cz, 10, 0.5, 0.5, 0.5, 0.3);
			Sigf.sound(SoundEvents.ANVIL_LAND, new Vec3(z.cx, z.cy, z.cz), 0.3f, 1.8f);
		} else if (hit instanceof LivingEntity e) {
			e.hurtServer(lv, lv.damageSources().playerAttack(p), 3.0f);
			lv.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 10, 0.3, 0.4, 0.3, 0.3);
			lv.sendParticles(ParticleTypes.DAMAGE_INDICATOR, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 2, 0.2, 0.2, 0.2, 0.1);
		}
	}

	// =====================================================================================================
	// Per-player tick: thrust, gun, flame, HUD
	// =====================================================================================================
	static ServerPlayer lead() {
		ServerPlayer best = null;
		for (ServerPlayer p : Sigf.players()) if (runner(p) && (best == null || p.getX() > best.getX())) best = p;
		if (best == null && !Sigf.players().isEmpty()) best = Sigf.players().getFirst();
		return best;
	}

	static void tickPlayer(ServerPlayer p) {
		St s = st(p);
		Vec3 pos = p.position();
		Vec3 prev = s.prev == null ? pos : s.prev;
		s.prev = pos;
		if (p.isSpectator() || !p.isAlive()) {
			if (s.rig != null) { s.rig.remove(); s.rig = null; }
			return;
		}
		if (!hasJetpack(p)) {
			if (s.rig != null) { s.rig.remove(); s.rig = null; }
			return;
		}
		if (s.rig == null) s.rig = new JetRig(pos);
		boolean jump = p.getLastClientInput().jump();
		Vec3 botVel = null;
		if (bot && p == Sigf.host()) {
			botVel = Bot.step(p, s, pos.subtract(prev));
			jump = Bot.jump;
		}
		boolean thrust = jump && pos.y < Hall.y0 + Hall.HEIGHT - 1.2;
		s.thrust = thrust;
		ServerLevel lv = Sigf.level();
		Vec3 vel = pos.subtract(prev);
		if (botVel != null) vel = botVel;
		if (botVel != null) {
			p.setDeltaMovement(botVel);
			p.syncVelocity = true;
			p.resetFallDistance();
		} else if (thrust) {
			p.setDeltaMovement(vel.x * 0.91, Math.min(Math.max(vel.y, -0.1) + 0.16, 0.5), vel.z * 0.91);
			p.syncVelocity = true;
			p.resetFallDistance();
		}
		if (thrust) {
			double yaw = Math.toRadians(p.yBodyRot);
			Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
			for (Vec3 n : NOZ) {
				double sx = n.x * Math.cos(yaw) * -1, sz = n.x * Math.sin(yaw) * -1;
				Vec3 np = pos.add(fwd.scale(n.z)).add(sx, n.y - 0.1, sz);
				lv.sendParticles(ParticleTypes.SMALL_FLAME, np.x, np.y - 0.3, np.z, 1, 0.03, 0.05, 0.03, 0.04);
				lv.sendParticles(ParticleTypes.SMOKE, np.x, np.y - 0.4, np.z, 1, 0.05, 0.1, 0.05, 0.02);
			}
			if (now % 2 == 0) fire(p);
			if (now - s.lastShot >= 8) { s.lastShot = now; Sigf.sound(SigfMod.JET_GUN, pos, 0.8f, 1f); }
		}
		s.rig.update(p, thrust, vel);
		// distance, milestones
		int dist = (int) Math.max(0, pos.x - Hall.x0);
		int ms = dist / 100;
		if (ms > s.lastMilestone && ms > 0) {
			s.lastMilestone = ms;
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(2, 24, 8));
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(ms * 100 + " M!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
			Sigf.sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, pos, 1f, 1f);
		}
		if (now % 4 == 0) {
			p.sendSystemMessage(Component.literal("DISTANCE " + dist + " m").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
				.append(Component.literal("    COINS " + s.coins).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)), true);
		}
	}

	// =====================================================================================================
	// Course generator
	// =====================================================================================================
	static void tickCourse(ServerPlayer lead) {
		if (!running || lead == null) return;
		while (nextX < lead.getX() + 70) {
			pattern(nextX);
		}
		if (now >= nextMissileAt && patternNo > 3) {
			nextMissileAt = now + 20 * (8 + rnd.nextInt(6));
			incoming(lead.getY() + (rnd.nextDouble() - 0.5) * 3, Math.max(Hall.z0 - 4, Math.min(Hall.z0 + 4, lead.getZ())) + (rnd.nextDouble() - 0.5) * 2, 24);
		}
	}

	static double ry(double lo, double hi) { return Hall.y0 + lo + rnd.nextDouble() * (hi - lo); }

	static void pattern(double x) {
		int k = patternNo++;
		int kind = k == 0 ? 0 : k == 1 ? 3 : k == 2 ? 1 : rnd.nextInt(6);
		double z0 = midZ();
		switch (kind) {
			case 0 -> { // sine wave of coins
				double base = ry(5, 11);
				for (int i = 0; i < 16; i++) coin(new Vec3(x + i * 1.6, base + Math.sin(i * 0.55) * 3.5, z0));
				nextX = x + 16 * 1.6 + 8;
			}
			case 1 -> { // zapper gate, coins past it
				double y = ry(5, 11);
				zapper(x, y, z0, 4.2, rnd.nextBoolean() ? 0 : Math.PI / 2 + rnd.nextDouble() * 0.5, 0);
				for (int i = 0; i < 8; i++) coin(new Vec3(x + 6 + i * 1.5, y + (i % 2) * 1.2, z0 + (i - 4) * 0.6));
				nextX = x + 22;
			}
			case 2 -> { // spinning zapper
				zapper(x, ry(6, 11), z0, 4.4, 0, 0.09);
				nextX = x + 20;
			}
			case 3 -> { // scientists on the floor, with coins above them
				scientists(x, 4);
				for (int i = 0; i < 10; i++) coin(new Vec3(x + 4 + i * 1.3, floorY() + 5 + Math.abs(Math.sin(i * 0.5)) * 2, z0));
				nextX = x + 30;
			}
			case 4 -> { // ring of coins
				double cy = ry(6, 10);
				for (int i = 0; i < 14; i++) {
					double a = i / 14.0 * Math.PI * 2;
					coin(new Vec3(x + 3, cy + Math.cos(a) * 3.2, z0 + Math.sin(a) * 3.2));
				}
				coin(new Vec3(x + 3, cy, z0));
				nextX = x + 14;
			}
			default -> { // two zappers and a coin ladder
				zapper(x, ry(4, 7), z0 + 1.5, 3.5, Math.PI / 2, 0);
				zapper(x + 7, ry(9, 12), z0 - 1.5, 3.5, 0.2, 0);
				for (int i = 0; i < 12; i++) coin(new Vec3(x + 14 + i * 1.2, floorY() + 2 + i * 0.9, z0));
				nextX = x + 32;
			}
		}
	}

	static void reset() {
		for (Coin c : coins) c.remove();
		coins.clear();
		for (Zapper z : zappers) z.remove();
		zappers.clear();
		for (Missile m : missiles) m.remove();
		missiles.clear();
		for (Gfx.Prop w : flashes) w.remove();
		flashes.clear();
	}

	static void tick() {
		now++;
		List<ServerPlayer> ps = Sigf.players();
		if (!Hall.ready) return;
		ServerPlayer lead = lead();
		if (lead != null) Hall.buildTo(lead.getX() + 110, 2);
		for (ServerPlayer p : ps) tickPlayer(p);
		tickCourse(lead);
		tickCoins(ps);
		tickZappers(ps);
		tickMissiles(ps);
		tickScientists(ps);
	}
}
