package com.cappleapple.instancednotinfinite.manifestation;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Resolves server-configured sound event IDs at playback time so other mods may supply them. */
public final class PortalSounds {
    private static final Set<String> WARNED_MISSING_SOUND_EVENTS = ConcurrentHashMap.newKeySet();

    private PortalSounds() {
    }

    public static void playGeneration(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ServerConfig.INSTANCE.generationSound, ServerConfig.INSTANCE.generationSoundVolume, 1.0F);
    }

    public static void playOpen(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ServerConfig.INSTANCE.portalOpenSound, ServerConfig.INSTANCE.portalOpenSoundVolume, 1.0F);
    }

    public static void playAmbient(Level level, BlockPos pos, RandomSource random) {
        float volume = configuredVolume(ServerConfig.INSTANCE.portalAmbientSoundVolume);
        if (volume <= 0.0F) return;
        resolve(ServerConfig.INSTANCE.portalAmbientSound).ifPresent(sound -> level.playLocalSound(
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5,
            sound,
            SoundSource.BLOCKS,
            volume,
            random.nextFloat() * 0.4F + 0.8F,
            false));
    }

    public static void playWalkThrough(ServerPlayer player, ServerLevel departedLevel, BlockPos departedPos) {
        float volume = configuredVolume(ServerConfig.INSTANCE.portalWalkThroughSoundVolume);
        if (volume <= 0.0F) return;
        resolve(ServerConfig.INSTANCE.portalWalkThroughSound).ifPresent(sound -> {
            // Nearby observers hear the departure at the portal; the traveler receives a
            // targeted copy at arrival so the dimension switch cannot swallow it.
            departedLevel.playSound(player, departedPos, sound, SoundSource.BLOCKS, volume, 1.0F);
            player.playNotifySound(sound, SoundSource.BLOCKS, volume, 1.0F);
        });
    }

    public static void playClosing(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ServerConfig.INSTANCE.portalClosingSound, ServerConfig.INSTANCE.portalClosingSoundVolume, 0.9F);
    }

    public static void playClosed(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ServerConfig.INSTANCE.portalClosedSound, ServerConfig.INSTANCE.portalClosedSoundVolume, 0.8F);
    }

    private static void playAt(
        ServerLevel level,
        BlockPos pos,
        ModConfigSpec.ConfigValue<String> configuredSound,
        ModConfigSpec.DoubleValue configuredVolume,
        float pitch
    ) {
        float volume = configuredVolume(configuredVolume);
        if (volume <= 0.0F) return;
        resolve(configuredSound).ifPresent(sound ->
            level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch));
    }

    private static float configuredVolume(ModConfigSpec.DoubleValue configuredVolume) {
        try {
            return configuredVolume.get().floatValue();
        } catch (IllegalStateException notLoadedYet) {
            return configuredVolume.getDefault().floatValue();
        }
    }

    private static Optional<SoundEvent> resolve(ModConfigSpec.ConfigValue<String> configuredSound) {
        String raw;
        try {
            raw = configuredSound.get();
        } catch (IllegalStateException notLoadedYet) {
            raw = configuredSound.getDefault();
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        Optional<SoundEvent> sound = id == null ? Optional.empty() : BuiltInRegistries.SOUND_EVENT.getOptional(id);
        if (sound.isEmpty() && WARNED_MISSING_SOUND_EVENTS.add(raw)) {
            InstancedNotInfinite.LOGGER.warn(
                "Configured manifestation sound event {} is not registered; that sound will be skipped", raw);
        }
        return sound;
    }
}
