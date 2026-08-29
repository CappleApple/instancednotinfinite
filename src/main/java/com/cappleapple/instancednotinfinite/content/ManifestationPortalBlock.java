package com.cappleapple.instancednotinfinite.content;

import com.cappleapple.instancednotinfinite.manifestation.DungeonManifestationManager;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationState;
import com.cappleapple.instancednotinfinite.manifestation.PortalSounds;
import com.cappleapple.instancednotinfinite.instance.DungeonInstanceManager;
import com.cappleapple.instancednotinfinite.instance.InstanceId;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ManifestationPortalBlock extends BaseEntityBlock {
    public static final MapCodec<ManifestationPortalBlock> CODEC = simpleCodec(ManifestationPortalBlock::new);
    public ManifestationPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ManifestationPortalBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof ManifestationPortalBlockEntity portal) {
            return PortalInteractionShape.voxelShape(
                pos, portal.rotationDegrees(), portal.portalWidth(), portal.portalHeight(), portal.portalDepth());
        }
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof ServerPlayer player
            && level.getBlockEntity(pos) instanceof ManifestationPortalBlockEntity portal
            && portal.intersects(player.getBoundingBox())) {
            tryActivate(level, pos, player);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            PortalSounds.playAmbient(level, pos, random);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            tryActivate(level, pos, serverPlayer);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    public static void tryActivate(Level level, BlockPos pos, ServerPlayer player) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof ManifestationPortalBlockEntity portal)) return;
        if (player.isOnPortalCooldown()) return;
        if (portal.endpoint() == ManifestationPortalBlockEntity.Endpoint.RETURN) {
            portal.instanceId().ifPresent(id -> returnFromInstance(player, id, pos));
            return;
        }
        portal.manifestationId().flatMap(id -> DungeonManifestationManager.get(player.getServer()).get(id)).ifPresent(value -> {
            if (value.state() != ManifestationState.PORTAL_OPEN) return;
            try {
                ServerLevel departedLevel = player.serverLevel();
                DungeonInstanceManager.get(player.getServer()).enterFromPortal(
                    player, value.instanceId(), pos, portal.rotationDegrees());
                player.setPortalCooldown();
                PortalSounds.playWalkThrough(player, departedLevel, pos);
            } catch (Exception exception) {
                player.sendSystemMessage(Component.literal("Portal is not ready: " + exception.getMessage()));
            }
        });
    }

    private static void returnFromInstance(ServerPlayer player, java.util.UUID rawInstanceId, BlockPos portalPos) {
        DungeonInstanceManager manager = DungeonInstanceManager.get(player.getServer());
        InstanceId instanceId = new InstanceId(rawInstanceId);
        boolean correctDimension = manager.get(instanceId)
            .filter(instance -> instance.dimensionId().equals(player.level().dimension().location()))
            .isPresent();
        if (!correctDimension) {
            player.sendSystemMessage(Component.literal("This return portal is not bound to the current dungeon instance"));
            return;
        }
        ServerLevel departedLevel = player.serverLevel();
        if (manager.leave(player)) {
            player.setPortalCooldown();
            PortalSounds.playWalkThrough(player, departedLevel, portalPos);
        } else {
            player.sendSystemMessage(Component.literal("No saved return location is available for this dungeon visit"));
        }
    }
}
