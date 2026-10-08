package sigf.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/** Follow camera behind the runner, like a chase cam in the tunnel. */
public final class SigfModClient implements ClientModInitializer {
	private static boolean set;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) { set = false; return; }
			if (!set && !mc.player.isSpectator()) {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				set = true;
			}
		});
	}
}
