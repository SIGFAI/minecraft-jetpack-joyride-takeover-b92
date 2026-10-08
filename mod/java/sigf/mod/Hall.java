package sigf.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import sigf.kit.Sigf;

/**
 * The laboratory tunnel: a long hazard-striped corridor running east from the start, built in 16-block sections
 * as the runner advances. Interior is 11 wide (z +-5) and 16 tall; floor surface at y0.
 */
final class Hall {
	static int x0, y0, z0;
	static final int HALF = 5, HEIGHT = 16;
	/** First section index not built yet. */
	static int built = -1;
	static boolean ready;

	private Hall() {}

	static void init(int x, int y, int z) {
		x0 = x; y0 = y; z0 = z; built = -1; ready = true;
	}

	/** Builds sections until the hall reaches x. */
	static void buildTo(double x, int maxSections) {
		int target = (int) Math.floor((x - x0) / 16.0);
		for (int n = 0; n < maxSections && built <= target; n++) section(built++);
	}

	static void section(int s) {
		int xs = x0 + 16 * s;
		int from = s == -1 ? 8 : 0, to = 16;
		if (s == -1) xs = x0 - 16;
		for (int i = (s == -1 ? 8 : 0); i < to; i++) {
			int x = xs + i;
			for (int dz = -HALF - 1; dz <= HALF + 1; dz++)
				for (int dy = -1; dy <= HEIGHT; dy++)
					put(x, dy, dz, pick(x, dy, dz, s));
		}
		if (s == -1) {
			// back wall with the start sign
			for (int dz = -HALF - 1; dz <= HALF + 1; dz++)
				for (int dy = -1; dy <= HEIGHT; dy++)
					put(x0 - 9, dy, dz, (dy >= 5 && dy <= 9 && Math.abs(dz) <= 4) ? Blocks.CONCRETE.black().defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState());
		}
	}

	private static void put(int x, int dy, int dz, BlockState st) {
		Sigf.level().setBlock(new BlockPos(x, y0 + dy, z0 + dz), st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	private static BlockState b(Block b) { return b.defaultBlockState(); }

	static BlockState pick(int x, int dy, int dz, int s) {
		int ax = Math.abs(dz);
		int lx = Math.floorMod(x - x0, 16);
		boolean hazard = ((x + dz * 0) >> 1) % 2 == 0;
		if (dy == -1) { // floor
			if (ax >= HALF) return hazard ? b(Blocks.CONCRETE.yellow()) : b(Blocks.CONCRETE.black());
			if (ax == 3 && lx % 6 == 3) return b(Blocks.SEA_LANTERN);
			if (ax == 0 && lx % 4 < 2) return b(Blocks.CONCRETE.white());
			return ((x >> 1) + dz) % 2 == 0 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.SMOOTH_STONE);
		}
		if (dy == HEIGHT) { // ceiling
			if (ax >= HALF) return hazard ? b(Blocks.CONCRETE.yellow()) : b(Blocks.CONCRETE.black());
			if (lx % 4 == 2 && (ax == 0 || ax == 3)) return b(Blocks.SEA_LANTERN);
			return b(Blocks.CONCRETE.lightGray());
		}
		if (ax <= HALF) return b(Blocks.AIR);
		// side walls
		if (lx == 0) { // support pillar
			if (dy == HEIGHT - 2) return b(Blocks.SEA_LANTERN);
			return b(Blocks.IRON_BLOCK);
		}
		if ((dy == 5 || dy == 11) && lx % 6 == 3) return b(Blocks.SEA_LANTERN);
		if (dy <= 2) {
			boolean stripe = ((x + dy) % 4 + 4) % 4 < 2;
			return stripe ? b(Blocks.CONCRETE.yellow()) : b(Blocks.CONCRETE.black());
		}
		int type = Math.floorMod(s, 5);
		switch (type) {
			case 0: // windows onto the outside
				if (dy >= 6 && dy <= 12 && lx >= 3 && lx <= 12) return (dy == 6 || dy == 12 || lx == 3 || lx == 12) ? b(Blocks.IRON_BLOCK) : b(Blocks.GLASS);
				break;
			case 1: // glowing monitor wall
				if (dy >= 6 && dy <= 11 && lx >= 2 && lx <= 13) {
					if (dy == 6 || dy == 11 || lx == 2 || lx == 13) return b(Blocks.CONCRETE.black());
					return (lx + dy) % 3 == 0 ? b(Blocks.SEA_LANTERN) : b(Blocks.CONCRETE.cyan());
				}
				break;
			case 2: // copper pipes
				if (lx % 4 == 2) return dy % 5 == 0 ? b(Blocks.IRON_BLOCK) : b(Blocks.COPPER_BLOCK.weathering().unaffected());
				break;
			case 3: // red warning band
				if (dy >= 7 && dy <= 8) return (lx % 2 == 0) ? b(Blocks.CONCRETE.red()) : b(Blocks.CONCRETE.white());
				break;
			default:
				if (dy >= 5 && dy <= 12 && lx >= 5 && lx <= 10) return (dy + lx) % 2 == 0 ? b(Blocks.CONCRETE.blue()) : b(Blocks.CONCRETE.lightBlue());
		}
		return dy % 6 == 3 ? b(Blocks.CONCRETE.gray()) : b(Blocks.CONCRETE.lightGray());
	}

	/** Signs standing in the first section. */
	static void signs() {
		Gfx.Prop p = new Gfx.Prop(new net.minecraft.world.phys.Vec3(x0 + 26, y0 + 12.5, z0 + 0.5));
		p.text(Component.literal("JETPACK JOYRIDE").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD), 0, 0.7, 0, 3.2);
		p.text(Component.literal("LABORATORIES").withStyle(net.minecraft.ChatFormatting.WHITE, net.minecraft.ChatFormatting.BOLD), 0, -0.3, 0, 2.2);
		p.parts.forEach(pt -> pt.d.setBillboardConstraints(net.minecraft.world.entity.Display.BillboardConstraints.FIXED));
		for (var pt : p.parts) { pt.rot.rotationY((float) Math.toRadians(-90)); pt.pose(0); }
	}
}
