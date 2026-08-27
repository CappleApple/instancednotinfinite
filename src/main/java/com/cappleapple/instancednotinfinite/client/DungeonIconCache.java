package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/** Lazy LRU of actual offscreen miniature textures, generated at most once per client tick. */
public final class DungeonIconCache {
    private static final Map<ResourceLocation, CachedIcon> CACHE = new LinkedHashMap<>(16, 0.75F, true);
    private static final Map<ResourceLocation, ClientManifestation> RESTORED = new LinkedHashMap<>();
    private static final Set<ResourceLocation> PENDING = new LinkedHashSet<>();
    private static long serial;

    private DungeonIconCache() {
    }

    /** Returns a finished icon or queues one and lets the caller draw its portal-cube fallback this frame. */
    public static Optional<ResourceLocation> request(ResourceLocation dungeonId) {
        CachedIcon cached = CACHE.get(dungeonId);
        if (cached != null) return Optional.of(cached.texture());
        if (source(dungeonId).isPresent()) {
            PENDING.add(dungeonId);
        } else {
            PersistentDungeonMiniatureCache.request(dungeonId);
        }
        return Optional.empty();
    }

    /** Returns the retained structure mesh used by the true 3D item renderer. */
    public static Optional<ClientManifestation> requestMiniature(ResourceLocation dungeonId) {
        CachedIcon cached = CACHE.get(dungeonId);
        if (cached != null && HologramMeshCache.isCurrent(cached.value())) {
            return Optional.of(cached.value());
        }
        Optional<ClientManifestation> source = source(dungeonId);
        if (source.isPresent() && HologramMeshCache.isCurrent(source.get())) {
            return source;
        }
        request(dungeonId);
        return Optional.empty();
    }

    static void prime(ClientManifestation value) {
        if (value.generationComplete()) PENDING.add(value.dungeonId());
    }

    static void tick() {
        if (PENDING.isEmpty()) return;
        Iterator<ResourceLocation> iterator = PENDING.iterator();
        ResourceLocation dungeonId = iterator.next();
        iterator.remove();
        source(dungeonId).ifPresentOrElse(value -> {
            HologramMeshCache.requestBuild(value);
            if (!value.generationComplete() || !HologramMeshCache.isCurrent(value)) {
                PENDING.add(dungeonId);
                return;
            }
            generate(dungeonId, value);
        }, () -> PersistentDungeonMiniatureCache.request(dungeonId));
    }

    static void invalidate(ClientManifestation value) {
        CachedIcon removed = CACHE.remove(value.dungeonId());
        if (removed != null) {
            release(removed);
            if (!removed.manifestationId().equals(value.id())
                && !ClientManifestationStore.contains(removed.manifestationId())) {
                HologramMeshCache.remove(removed.manifestationId());
            }
        }
        PENDING.add(value.dungeonId());
    }

    static boolean retains(UUID manifestationId) {
        return CACHE.values().stream().anyMatch(icon -> icon.manifestationId().equals(manifestationId));
    }

    public static void clear() {
        CACHE.values().forEach(DungeonIconCache::release);
        CACHE.clear();
        RESTORED.clear();
        PENDING.clear();
    }

    static void resourcesReloaded() {
        CACHE.forEach((dungeonId, icon) -> {
            if (!ClientManifestationStore.contains(icon.manifestationId())) {
                RESTORED.put(dungeonId, icon.value());
            }
        });
        CACHE.values().forEach(DungeonIconCache::release);
        CACHE.clear();
        PENDING.clear();
        PENDING.addAll(RESTORED.keySet());
    }

    static void loadedFromDisk(ResourceLocation dungeonId, ClientManifestation value) {
        if (ClientManifestationStore.findByDungeon(dungeonId).isEmpty()) {
            RESTORED.put(dungeonId, value);
            PENDING.add(dungeonId);
        }
    }

    private static Optional<ClientManifestation> source(ResourceLocation dungeonId) {
        return ClientManifestationStore.findByDungeon(dungeonId)
            .or(() -> Optional.ofNullable(RESTORED.get(dungeonId)));
    }

    private static void generate(ResourceLocation dungeonId, ClientManifestation value) {
        if (ClientManifestationStore.contains(value.id())) {
            PersistentDungeonMiniatureCache.persist(value);
            RESTORED.remove(dungeonId);
        }
        Minecraft minecraft = Minecraft.getInstance();
        int resolution = ServerConfig.INSTANCE.iconResolution.get();
        TextureTarget target = null;
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            target = new TextureTarget(resolution, resolution, true, Minecraft.ON_OSX);
            target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0.0F, 1.0F, 1.0F, 0.0F, -10.0F, 10.0F),
                VertexSorting.ORTHOGRAPHIC_Z);
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            DungeonMiniatureRenderer.renderIcon(value, new PoseStack());

            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                InstancedNotInfinite.MOD_ID, "generated/dungeon_icon_" + serial++);
            minecraft.getTextureManager().register(texture, new TargetTexture(target));
            CachedIcon previous = CACHE.put(dungeonId, new CachedIcon(value.id(), texture, value));
            if (previous != null) release(previous);
            target = null; // TextureManager now owns the render target through TargetTexture.
            enforceLimit();
        } catch (RuntimeException exception) {
            InstancedNotInfinite.LOGGER.warn("Could not generate cached icon for {}", dungeonId, exception);
        } finally {
            if (target != null) target.destroyBuffers();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.viewport(0, 0, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        }
    }

    private static void enforceLimit() {
        int limit = ServerConfig.INSTANCE.iconCacheLimit.get();
        while (CACHE.size() > limit) {
            Iterator<Map.Entry<ResourceLocation, CachedIcon>> iterator = CACHE.entrySet().iterator();
            Map.Entry<ResourceLocation, CachedIcon> eldest = iterator.next();
            iterator.remove();
            release(eldest.getValue());
            if (!ClientManifestationStore.contains(eldest.getValue().manifestationId())) {
                HologramMeshCache.remove(eldest.getValue().manifestationId());
                RESTORED.remove(eldest.getKey());
                PersistentDungeonMiniatureCache.allowReload(eldest.getKey());
            }
        }
    }

    private static void release(CachedIcon icon) {
        Minecraft.getInstance().getTextureManager().release(icon.texture());
    }

    private record CachedIcon(UUID manifestationId, ResourceLocation texture, ClientManifestation value) {
    }

    /** Adopts an existing framebuffer color texture and destroys the whole target on release. */
    private static final class TargetTexture extends AbstractTexture {
        private final TextureTarget target;

        private TargetTexture(TextureTarget target) {
            this.target = target;
            this.id = target.getColorTextureId();
        }

        @Override
        public void load(ResourceManager resourceManager) throws IOException {
            this.id = target.getColorTextureId();
        }

        @Override
        public void releaseId() {
            if (target.frameBufferId >= 0) target.destroyBuffers();
            this.id = NOT_ASSIGNED;
        }
    }
}
