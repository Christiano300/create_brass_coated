package com.injir.create_brass_coated.blocks.girder;

import com.injir.create_brass_coated.Create_Brass_Coated;
import com.injir.create_brass_coated.blocks.BrassBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.utility.Color;
import com.simibubi.create.foundation.utility.Iterate;
import com.simibubi.create.foundation.utility.Pair;
import com.simibubi.create.foundation.utility.VecHelper;
import com.simibubi.create.foundation.utility.placement.IPlacementHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BrassGirderWrenchBehavior {

	@OnlyIn(Dist.CLIENT)
	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult result))
			return;

		ClientLevel world = mc.level;
		BlockPos pos = result.getBlockPos();
		Player player = mc.player;
		ItemStack heldItem = player.getMainHandItem();

		if (player.isSteppingCarefully())
			return;

		if (!BrassBlocks.BRASS_GIRDER.has(world.getBlockState(pos)))
			return;

		if (!AllItems.WRENCH.isIn(heldItem))
			return;

		Pair<Direction, Action> dirPair = getDirectionAndAction(result, world, pos);
		if (dirPair == null)
			return;

		Vec3 center = VecHelper.getCenterOf(pos);
		Vec3 edge = center.add(Vec3.atLowerCornerOf(dirPair.getFirst()
			.getNormal())
			.scale(0.4));
		Axis[] axes = Arrays.stream(Iterate.axes)
			.filter(axis -> axis != dirPair.getFirst()
				.getAxis())
			.toArray(Axis[]::new);

		double normalMultiplier = dirPair.getSecond() == Action.PAIR ? 4 : 1;
		Vec3 corner1 = edge
			.add(Vec3.atLowerCornerOf(Direction.fromAxisAndDirection(axes[0], Direction.AxisDirection.POSITIVE)
				.getNormal())
				.scale(0.3))
			.add(Vec3.atLowerCornerOf(Direction.fromAxisAndDirection(axes[1], Direction.AxisDirection.POSITIVE)
				.getNormal())
				.scale(0.3))
			.add(Vec3.atLowerCornerOf(dirPair.getFirst()
				.getNormal())
				.scale(0.1 * normalMultiplier));

		normalMultiplier = dirPair.getSecond() == Action.HORIZONTAL ? 9 : 2;
		Vec3 corner2 = edge
			.add(Vec3.atLowerCornerOf(Direction.fromAxisAndDirection(axes[0], Direction.AxisDirection.NEGATIVE)
				.getNormal())
				.scale(0.3))
			.add(Vec3.atLowerCornerOf(Direction.fromAxisAndDirection(axes[1], Direction.AxisDirection.NEGATIVE)
				.getNormal())
				.scale(0.3))
			.add(Vec3.atLowerCornerOf(dirPair.getFirst()
				.getOpposite()
				.getNormal())
				.scale(0.1 * normalMultiplier));

		CreateClient.OUTLINER.showAABB("brass_girderWrench", new AABB(corner1, corner2))
			.lineWidth(1 / 32f)
			.colored(new Color(127, 127, 127));
	}

	@Nullable
	private static Pair<Direction, Action> getDirectionAndAction(BlockHitResult result, Level world, BlockPos pos) {
		List<Pair<Direction, Action>> validDirections = getValidDirections(world, pos);

		if (validDirections.isEmpty())
			return null;

		List<Direction> directions = IPlacementHelper.orderedByDistance(pos, result.getLocation(),
			validDirections.stream()
				.map(Pair::getFirst)
				.toList());

		if (directions.isEmpty())
			return null;

		Direction dir = directions.get(0);
		return validDirections.stream()
			.filter(pair -> pair.getFirst() == dir)
			.findFirst()
			.orElseGet(() -> Pair.of(dir, Action.SINGLE));
	}

	public static List<Pair<Direction, Action>> getValidDirections(BlockGetter level, BlockPos pos) {
		BlockState blockState = level.getBlockState(pos);

		if (!BrassBlocks.BRASS_GIRDER.has(blockState))
			return Collections.emptyList();

		return Arrays.stream(Iterate.directions)
			.<Pair<Direction, Action>>mapMulti((direction, consumer) -> {
				BlockState other = level.getBlockState(pos.relative(direction));

				if (!blockState.getValue(BrassGirderBlock.X) && !blockState.getValue(BrassGirderBlock.Z))
					return;

				// up and down
				if (direction.getAxis() == Axis.Y) {
					// no other girder in target dir
					if (!BrassBlocks.BRASS_GIRDER.has(other)) {
						if (!blockState.getValue(BrassGirderBlock.X) ^ !blockState.getValue(BrassGirderBlock.Z))
							consumer.accept(Pair.of(direction, Action.SINGLE));
						return;
					}
					// this girder is a pole or cross
					if (blockState.getValue(BrassGirderBlock.X) == blockState.getValue(BrassGirderBlock.Z))
						return;
					// other girder is a pole or cross
					if (other.getValue(BrassGirderBlock.X) == other.getValue(BrassGirderBlock.Z))
						return;
					// toggle up/down connection for both
					consumer.accept(Pair.of(direction, Action.PAIR));

					return;
				}

//					if (AllBlocks.METAL_GIRDER.has(other))
//						consumer.accept(Pair.of(direction, Action.HORIZONTAL));

			})
			.toList();
	}

	public static boolean handleClick(Level level, BlockPos pos, BlockState state, BlockHitResult result) {
		Pair<Direction, Action> dirPair = getDirectionAndAction(result, level, pos);
		if (dirPair == null)
			return false;
		if (level.isClientSide)
			return true;
		if (!state.getValue(BrassGirderBlock.X) && !state.getValue(BrassGirderBlock.Z))
			return false;

		Direction dir = dirPair.getFirst();

		BlockPos otherPos = pos.relative(dir);
		BlockState other = level.getBlockState(otherPos);

		if (dir == Direction.UP) {
			level.setBlock(pos, postProcess(state.cycle(BrassGirderBlock.TOP)), 2 | 16);
			if (dirPair.getSecond() == Action.PAIR && BrassBlocks.BRASS_GIRDER.has(other))
				level.setBlock(otherPos, postProcess(other.cycle(BrassGirderBlock.BOTTOM)), 2 | 16);
			return true;
		}

		if (dir == Direction.DOWN) {
			level.setBlock(pos, postProcess(state.cycle(BrassGirderBlock.BOTTOM)), 2 | 16);
			if (dirPair.getSecond() == Action.PAIR && BrassBlocks.BRASS_GIRDER.has(other))
				level.setBlock(otherPos, postProcess(other.cycle(BrassGirderBlock.TOP)), 2 | 16);
			return true;
		}

//		if (dirPair.getSecond() == Action.HORIZONTAL) {
//			BooleanProperty property = dir.getAxis() == Direction.Axis.X ? GirderBlock.X : GirderBlock.Z;
//			level.setBlock(pos, state.cycle(property), 2 | 16);
//
//			return true;
//		}

		return true;
	}

	private static BlockState postProcess(BlockState newState) {
		if (newState.getValue(BrassGirderBlock.TOP) && newState.getValue(BrassGirderBlock.BOTTOM))
			return newState;
		if (newState.getValue(BrassGirderBlock.AXIS) != Axis.Y)
			return newState;
		return newState.setValue(BrassGirderBlock.AXIS, newState.getValue(BrassGirderBlock.X) ? Axis.X : Axis.Z);
	}

	private enum Action {
		SINGLE, PAIR, HORIZONTAL
	}

}
