package sigf.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import sigf.kit.Sigf;

/** Jetpack Joyride Takeover: the laboratory tunnel, the machine gun jetpack, coins, zappers, missiles and scientists. */
public final class SigfMod implements ModInitializer {
	public static Item JETPACK, COIN;
	public static SoundEvent JET_GUN, COIN_GET, ZAPPER, MISSILE;

	@Override
	public void onInitialize() {
		JETPACK = Sigf.item("jetpack", p -> new Item(p.stacksTo(1).component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).build())));
		COIN = Sigf.item("coin", p -> new Item(p.stacksTo(99)));
		JET_GUN = Sigf.registerSound("jet_gun");
		COIN_GET = Sigf.registerSound("coin_get");
		ZAPPER = Sigf.registerSound("zapper");
		MISSILE = Sigf.registerSound("missile");

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> join(handler.getPlayer())));
		ServerPlayerEvents.AFTER_RESPAWN.register((old, p, alive) -> equip(p));
		ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> Joy.onDeath(e));
		// the jetpack's thrust cancels fall damage
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amount) -> !(e instanceof ServerPlayer p && src.is(DamageTypes.FALL) && Joy.hasJetpack(p)));
		Sigf.every(0.05, Joy::tick);

		// The demo: the autopilot runs the tunnel from the first second.
		Sigf.demo(0, () -> { Joy.running = true; Joy.bot = true; Joy.nextX = Hall.x0 + 12; face(); });
		Sigf.demo(0.5, () -> Sigf.title("JETPACK JOYRIDE", "TAKEOVER", 3));
		Sigf.demo(16, () -> Sigf.title("", "MISSILE INCOMING!", 1.6));
		Sigf.demo(16.5, () -> Joy.incoming(Sigf.host().getY(), Sigf.host().getZ(), 24));
		// a tough zapper right in the runner's path: the bot flies into it and gets zapped
		Sigf.demo(27, () -> { var h = Sigf.host(); Joy.zapper(h.getX() + 20, h.getY() + 0.9, h.getZ(), 4.5, 0, 0).hp = 400; });
		Sigf.demo(42, () -> Joy.incoming(Sigf.host().getY() + 1.5, Sigf.host().getZ() + 1, 24));
		// the finale: a missile barrage across the whole tunnel
		Sigf.demo(56, () -> Sigf.title("", "MISSILE BARRAGE!", 1.6));
		Sigf.demo(56.5, () -> {
			var h = Sigf.host();
			for (int i = 0; i < 5; i++) Joy.incoming(Hall.y0 + 3 + i * 2.5, Hall.z0 + 0.5 + (i - 2) * 1.9, 26 + i);
		});
	}

	static void face() {
		ServerPlayer p = Sigf.host();
		if (p != null) p.connection.teleport(p.getX(), p.getY(), p.getZ(), -90f, 22f);
	}

	static void equip(ServerPlayer p) {
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(JETPACK));
	}

	static void join(ServerPlayer p) {
		if (!Hall.ready) {
			Hall.init((int) Math.floor(p.getX()), (int) Math.floor(p.getY()), (int) Math.floor(p.getZ()));
			Hall.signs();
		}
		equip(p);
		if (Sigf.isPlayer()) {
			Sigf.after(1, () -> face());
			Sigf.after(1.5, () -> Sigf.title("JETPACK JOYRIDE", "Hold JUMP to fly and shoot!", 4));
			Sigf.after(5, () -> { Joy.running = true; Joy.nextX = Hall.x0 + 12; });
		} else if (!Sigf.isDemo()) {
			Sigf.after(4, () -> { Joy.running = true; Joy.nextX = Hall.x0 + 12; });
		}
	}
}
