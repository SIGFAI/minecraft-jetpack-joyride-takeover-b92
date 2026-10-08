package sigf.mod;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import sigf.kit.Sigf;

/** Real 3D models made of display entities: boxes of vanilla blocks, item models and text, posed by hand. */
final class Gfx {
	private Gfx() {}

	/** One box (or item, or text) of a model. off is the world-space offset of its center from the prop's origin. */
	static final class Part {
		final Display d;
		final boolean centered;
		Vector3f off = new Vector3f();
		Vector3f size = new Vector3f(1, 1, 1);
		Quaternionf rot = new Quaternionf();
		/** Extra translation (used by models that ride an entity). */
		Vector3f shift = new Vector3f();
		boolean flip;

		Part(Display d, boolean centered) { this.d = d; this.centered = centered; }

		/** Applies size and rotation to the entity, easing over ticks. */
		void pose(int ease) {
			Vector3f t = new Vector3f();
			if (!centered) { t.set(size).mul(-0.5f); rot.transform(t); }
			t.add(shift);
			d.setTransformation(new Transformation(t, new Quaternionf(rot), new Vector3f(size), new Quaternionf()));
			d.setTransformationInterpolationDuration(ease);
			flip = !flip;
			d.setTransformationInterpolationDelay(flip ? -1 : 0);
		}
	}

	/** A model: parts around a moving origin. */
	static class Prop {
		final List<Part> parts = new ArrayList<>();
		Vec3 pos;

		Prop(Vec3 pos) { this.pos = pos; }

		Part block(BlockState st, double ox, double oy, double oz, double sx, double sy, double sz, boolean bright) {
			Display.BlockDisplay d = EntityTypes.BLOCK_DISPLAY.create(Sigf.level(), EntitySpawnReason.COMMAND);
			d.setBlockState(st);
			return add(new Part(d, false), ox, oy, oz, sx, sy, sz, bright);
		}

		Part item(ItemStack st, double ox, double oy, double oz, double s) {
			Display.ItemDisplay d = EntityTypes.ITEM_DISPLAY.create(Sigf.level(), EntitySpawnReason.COMMAND);
			d.setItemStack(st);
			d.setItemTransform(ItemDisplayContext.FIXED);
			return add(new Part(d, true), ox, oy, oz, s, s, s, true);
		}

		Part text(Component c, double ox, double oy, double oz, double s) {
			Display.TextDisplay d = EntityTypes.TEXT_DISPLAY.create(Sigf.level(), EntitySpawnReason.COMMAND);
			d.setText(c);
			d.setBackgroundColor(0);
			d.setLineWidth(400);
			d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
			d.setFlags((byte) (Display.TextDisplay.FLAG_SHADOW | Display.TextDisplay.FLAG_SEE_THROUGH));
			return add(new Part(d, true), ox, oy, oz, s, s, s, true);
		}

		private Part add(Part p, double ox, double oy, double oz, double sx, double sy, double sz, boolean bright) {
			p.off.set((float) ox, (float) oy, (float) oz);
			p.size.set((float) sx, (float) sy, (float) sz);
			if (bright) p.d.setBrightnessOverride(new Brightness(15, 15));
			p.d.setViewRange(2f);
			p.d.setPosRotInterpolationDuration(2);
			p.pose(0);
			p.d.snapTo(pos.x + ox, pos.y + oy, pos.z + oz, 0f, 0f);
			Sigf.level().addFreshEntity(p.d);
			parts.add(p);
			return p;
		}

		/** Moves the whole model (the parts keep their offsets). */
		void moveTo(Vec3 p) {
			pos = p;
			for (Part part : parts) part.d.setPos(p.x + part.off.x, p.y + part.off.y, p.z + part.off.z);
		}

		void remove() {
			for (Part part : parts) part.d.discard();
			parts.clear();
		}
	}
}
