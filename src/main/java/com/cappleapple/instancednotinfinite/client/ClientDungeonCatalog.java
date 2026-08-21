package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.content.ManifestationTargetComponent;
import com.cappleapple.instancednotinfinite.content.ModContent;
import java.lang.reflect.Method;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Client copy of the server's resolved automatic and datapack dungeon catalogue. */
public final class ClientDungeonCatalog {
    private static volatile List<ResourceLocation> dungeonIds = List.of();
    private static volatile Map<ResourceLocation, PortalPreviewColors> portalPreviews = Map.of();
    private static volatile Map<ResourceLocation, List<ResourceLocation>> structurePools = Map.of();
    private static volatile UUID worldId;
    private static volatile long revision;
    private static volatile long emiAppliedRevision = -1;
    private static volatile long emiRequestedRevision = -1;

    private ClientDungeonCatalog() {
    }

    public static List<ResourceLocation> dungeonIds() {
        return dungeonIds;
    }

    public static List<ItemStack> itemStacks() {
        java.util.ArrayList<ItemStack> result = new java.util.ArrayList<>();
        dungeonIds.stream().map(ClientDungeonCatalog::itemStack).forEach(result::add);
        structurePools.keySet().stream().sorted().map(ClientDungeonCatalog::poolItemStack).forEach(result::add);
        return List.copyOf(result);
    }

    public static Optional<PortalPreviewColors> portalPreview(ResourceLocation dungeonId) {
        return Optional.ofNullable(portalPreviews.get(dungeonId));
    }

    public static List<PortalPreviewColors> portalPreviews(List<ResourceLocation> ids) {
        return ids.stream().map(portalPreviews::get).filter(java.util.Objects::nonNull).toList();
    }

    public static List<PortalPreviewColors> portalPreviews() {
        return portalPreviews(dungeonIds);
    }

    public static UUID worldId() {
        return worldId;
    }

    public static void update(
        UUID newWorldId,
        List<com.cappleapple.instancednotinfinite.network.DungeonCatalogPayload.DungeonEntry> dungeons,
        List<com.cappleapple.instancednotinfinite.network.DungeonCatalogPayload.StructurePoolEntry> pools
    ) {
        Map<ResourceLocation, PortalPreviewColors> normalizedPreviews = new LinkedHashMap<>();
        dungeons.stream().sorted(java.util.Comparator.comparing(
                com.cappleapple.instancednotinfinite.network.DungeonCatalogPayload.DungeonEntry::dungeonId))
            .forEach(entry -> normalizedPreviews.put(entry.dungeonId(), new PortalPreviewColors(
                entry.dungeonId(), entry.portalInnerColor(), entry.portalOuterColor())));
        List<ResourceLocation> normalized = dungeons.stream()
            .filter(com.cappleapple.instancednotinfinite.network.DungeonCatalogPayload.DungeonEntry::exactCatalyst)
            .map(com.cappleapple.instancednotinfinite.network.DungeonCatalogPayload.DungeonEntry::dungeonId)
            .distinct().sorted().toList();
        Map<ResourceLocation, List<ResourceLocation>> normalizedPools = new LinkedHashMap<>();
        pools.stream().sorted(java.util.Comparator.comparing(
                com.cappleapple.instancednotinfinite.network.DungeonCatalogPayload.StructurePoolEntry::tagId))
            .forEach(pool -> normalizedPools.put(pool.tagId(), pool.dungeonIds()));
        boolean worldChanged = !newWorldId.equals(worldId);
        if (!worldChanged && normalized.equals(dungeonIds) && normalizedPreviews.equals(portalPreviews)
            && normalizedPools.equals(structurePools)) return;
        worldId = newWorldId;
        PersistentDungeonMiniatureCache.setWorld(newWorldId);
        dungeonIds = normalized;
        portalPreviews = Map.copyOf(normalizedPreviews);
        structurePools = Map.copyOf(normalizedPools);
        revision++;
        refreshRecipeViewers();
    }

    public static void clear() {
        worldId = null;
        dungeonIds = List.of();
        portalPreviews = Map.of();
        structurePools = Map.of();
        revision = 0;
        emiAppliedRevision = -1;
        emiRequestedRevision = -1;
        PersistentDungeonMiniatureCache.clearSession();
    }

    public static ItemStack itemStack(ResourceLocation dungeonId) {
        ItemStack stack = new ItemStack(ModContent.MANIFESTATION_CATALYST.get());
        stack.set(ModContent.MANIFESTATION_TARGET.get(), ManifestationTargetComponent.dungeon(dungeonId));
        return stack;
    }

    public static ItemStack poolItemStack(ResourceLocation tagId) {
        ItemStack stack = new ItemStack(ModContent.MANIFESTATION_CATALYST.get());
        stack.set(ModContent.MANIFESTATION_TARGET.get(), ManifestationTargetComponent.structurePool(tagId));
        return stack;
    }

    public static List<ResourceLocation> structurePoolMembers(ResourceLocation tagId) {
        return structurePools.getOrDefault(tagId, List.of());
    }

    private static void refreshRecipeViewers() {
        if (ModList.get().isLoaded("jei")) {
            invokeStatic("com.cappleapple.instancednotinfinite.compat.jei.InstancedNotInfiniteJeiPlugin", "catalogChanged");
        }
    }

    /** Called by the optional EMI plugin after it has consumed the current synchronized catalogue. */
    public static void markEmiCatalogApplied() {
        emiAppliedRevision = revision;
    }

    static void tick() {
        if (!ModList.get().isLoaded("emi")
            || emiAppliedRevision >= revision
            || emiRequestedRevision >= revision
            || !emiIsLoaded()) return;
        // EMI's index is immutable after a plugin reload. Waiting for isLoaded prevents
        // login synchronization from racing its normal recipe/tag initialization.
        emiRequestedRevision = revision;
        invokeStatic("dev.emi.emi.runtime.EmiReloadManager", "reload");
    }

    private static boolean emiIsLoaded() {
        try {
            Class<?> type = Class.forName("dev.emi.emi.runtime.EmiReloadManager");
            Method method = type.getMethod("isLoaded");
            return Boolean.TRUE.equals(method.invoke(null));
        } catch (ReflectiveOperationException | LinkageError exception) {
            InstancedNotInfinite.LOGGER.warn("Could not inspect optional EMI reload state", exception);
            return false;
        }
    }

    private static void invokeStatic(String className, String methodName) {
        try {
            Class<?> type = Class.forName(className);
            Method method = type.getMethod(methodName);
            method.invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            InstancedNotInfinite.LOGGER.warn("Could not refresh optional recipe viewer through {}.{}", className, methodName, exception);
        }
    }

    public record PortalPreviewColors(ResourceLocation dungeonId, int innerColor, int outerColor) {
    }
}
