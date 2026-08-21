package com.cappleapple.instancednotinfinite.content;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.config.CompletionOfferingSelector;
import com.cappleapple.instancednotinfinite.instance.DungeonInstanceManager;
import com.cappleapple.instancednotinfinite.instance.InstanceId;
import com.cappleapple.instancednotinfinite.instance.InstanceOperationException;
import com.cappleapple.instancednotinfinite.instance.InstanceState;
import com.cappleapple.instancednotinfinite.manifestation.DungeonManifestationManager;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationState;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Consumes configured item offerings inside rendered portal volumes and completes their exact instances. */
public final class PortalCompletionOffering {
    private PortalCompletionOffering() {
    }

    public static void tick(MinecraftServer server) {
        String selector = ServerConfig.INSTANCE.portalCompletionOffering.get();
        for (ServerLevel level : server.getAllLevels()) {
            for (ManifestationPortalBlockEntity portal : ManifestationPortalBlockEntity.loadedIn(level)) {
                Optional<InstanceId> instanceId = completableInstance(server, portal);
                if (instanceId.isEmpty()) continue;
                for (ItemEntity item : level.getEntitiesOfClass(
                    ItemEntity.class, portal.interactionBounds(),
                    candidate -> candidate.isAlive()
                        && portal.intersects(candidate.getBoundingBox())
                        && matches(candidate.getItem(), selector))) {
                    if (complete(server, instanceId.get(), item)) return;
                }
            }
        }
    }

    public static boolean matches(ItemStack stack, String selector) {
        if (stack.isEmpty()) return false;
        Optional<CompletionOfferingSelector> parsed = CompletionOfferingSelector.parse(selector);
        if (parsed.isEmpty()) return false;
        ResourceLocation id = ResourceLocation.parse(parsed.orElseThrow().resourceId());
        return parsed.orElseThrow().tag()
            ? stack.is(TagKey.create(Registries.ITEM, id))
            : BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }

    private static Optional<InstanceId> completableInstance(
        MinecraftServer server,
        ManifestationPortalBlockEntity portal
    ) {
        Optional<InstanceId> bound = portal.endpoint() == ManifestationPortalBlockEntity.Endpoint.RETURN
            ? portal.instanceId().map(InstanceId::new)
            : portal.manifestationId()
                .flatMap(id -> DungeonManifestationManager.get(server).get(id))
                .filter(value -> value.state() == ManifestationState.PORTAL_OPEN)
                .map(value -> value.instanceId());
        DungeonInstanceManager instances = DungeonInstanceManager.get(server);
        return bound.filter(id -> instances.get(id)
            .map(instance -> instance.state() == InstanceState.ACTIVE || instance.state() == InstanceState.VACANT)
            .orElse(false));
    }

    private static boolean complete(MinecraftServer server, InstanceId instanceId, ItemEntity item) {
        try {
            DungeonInstanceManager.get(server).complete(instanceId);
            ItemStack remainder = item.getItem();
            ResourceLocation offeredItem = BuiltInRegistries.ITEM.getKey(remainder.getItem());
            remainder.shrink(1);
            if (remainder.isEmpty()) item.discard();
            else item.setItem(remainder);
            InstancedNotInfinite.LOGGER.info(
                "[Dungeon {}] Portal completion offering consumed: {}",
                instanceId.shortId(), offeredItem);
            return true;
        } catch (InstanceOperationException exception) {
            InstancedNotInfinite.LOGGER.debug(
                "Portal completion offering could not complete instance {}: {}",
                instanceId.shortId(), exception.getMessage());
            return false;
        }
    }
}
