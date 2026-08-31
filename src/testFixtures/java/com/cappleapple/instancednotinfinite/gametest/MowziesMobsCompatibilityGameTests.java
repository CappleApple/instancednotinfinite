package com.cappleapple.instancednotinfinite.gametest;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.definition.AutomaticDungeonResolver;
import com.cappleapple.instancednotinfinite.definition.DefinitionResolver;
import com.cappleapple.instancednotinfinite.definition.EnvironmentType;
import com.cappleapple.instancednotinfinite.definition.ResolutionException;
import com.cappleapple.instancednotinfinite.definition.ResolvedDungeonDefinition;
import com.cappleapple.instancednotinfinite.instance.DungeonInstance;
import com.cappleapple.instancednotinfinite.instance.DungeonInstanceManager;
import com.cappleapple.instancednotinfinite.instance.InstanceOperationException;
import com.cappleapple.instancednotinfinite.structure.DungeonStructurePlacer;
import com.cappleapple.instancednotinfinite.structure.PlacementException;
import com.cappleapple.instancednotinfinite.terrain.DungeonChunkGenerator;
import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(InstancedNotInfinite.MOD_ID)
public final class MowziesMobsCompatibilityGameTests {
    private static final ResourceLocation MONASTERY = ResourceLocation.parse("mowziesmobs:monastery");
    private static final ResourceLocation WROUGHT_CHAMBER = ResourceLocation.parse("mowziesmobs:wrought_chamber");
    private static final TagKey<Biome> MOUNTAIN_PEAKS = TagKey.create(
        Registries.BIOME, ResourceLocation.parse("c:is_mountain/peak"));

    private MowziesMobsCompatibilityGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        if (!ModList.get().isLoaded("mowziesmobs")) return List.of();
        return List.of(
            new TestFunction(
                "mowzie_monastery", "instancednotinfinite.monastery_uses_mountain_surface_instance",
                "instancednotinfinite_integration:empty", 1800, 0L, true,
                MowziesMobsCompatibilityGameTests::monasteryUsesMountainSurfaceInstance),
            new TestFunction(
                "mowzie_wrought_chamber", "instancednotinfinite.wrought_chamber_gets_controlled_cave_anchor",
                "instancednotinfinite_integration:empty", 1800, 0L, true,
                MowziesMobsCompatibilityGameTests::wroughtChamberGetsControlledCaveAnchor));
    }

    private static void monasteryUsesMountainSurfaceInstance(GameTestHelper helper) {
        var level = helper.getLevel();
        Structure monastery = requireStructure(helper, MONASTERY);
        try {
            var option = AutomaticDungeonResolver.resolve(
                level.registryAccess(), MONASTERY, List.of("gametest"), null, 32, 16, 256);
            helper.assertValueEqual(option.definition().environment(), EnvironmentType.SURFACE,
                "Mowzie monastery followed its technical decoration step into underground terrain");
            assertAllBiomesMatch(helper, option.definition().biomes().stream().map(rule -> rule.id()).toList(), MOUNTAIN_PEAKS,
                "Mowzie monastery retained a non-mountain automatic biome");

            ResolvedDungeonDefinition resolved = DefinitionResolver.resolve(
                level.registryAccess(), level.getStructureManager(), option.definition(), 73L);
            helper.assertTrue(resolved.biome().is(MOUNTAIN_PEAKS),
                "Resolved monastery biome does not match Mowzie's configured mountain-peak set");
            DungeonChunkGenerator generator = generator(level, resolved, 73L);
            var prepared = new DungeonStructurePlacer().prepare(level, resolved, generator, 73L, true);
            helper.assertTrue(prepared.worldgenStart() != null && prepared.worldgenStart().isValid(),
                "Controlled placement did not produce a valid monastery start");
            helper.assertFalse(prepared.authoredPieceBounds().isEmpty(), "Generated monastery start had no pieces");
        } catch (ResolutionException | PlacementException exception) {
            throw new IllegalStateException("Could not prepare the mountain-surface monastery start", exception);
        }

        DungeonInstance created = createAndDeleteFullInstance(helper, MONASTERY, monastery, EnvironmentType.SURFACE, true);
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(DungeonInstanceManager.get(level.getServer()).get(created.id()).isEmpty(),
                "Waiting for full monastery instance cleanup"))
            .thenSucceed();
    }

    private static void wroughtChamberGetsControlledCaveAnchor(GameTestHelper helper) {
        var level = helper.getLevel();
        Structure chamber = requireStructure(helper, WROUGHT_CHAMBER);
        try {
            var option = AutomaticDungeonResolver.resolve(
                level.registryAccess(), WROUGHT_CHAMBER, List.of("gametest"), null, 32, 16, 256);
            helper.assertValueEqual(option.definition().environment(), EnvironmentType.CAVE,
                "Wrought Chamber lost its underground cave terrain");
            ResolvedDungeonDefinition resolved = DefinitionResolver.resolve(
                level.registryAccess(), level.getStructureManager(), option.definition(), 91L);
            DungeonChunkGenerator generator = generator(level, resolved, 91L);
            var prepared = new DungeonStructurePlacer().prepare(level, resolved, generator, 91L, true);
            helper.assertTrue(prepared.worldgenStart() != null && prepared.worldgenStart().isValid(),
                "Controlled placement did not produce a valid Wrought Chamber start");
            helper.assertFalse(prepared.authoredPieceBounds().isEmpty(), "Generated Wrought Chamber start had no pieces");
        } catch (ResolutionException | PlacementException exception) {
            throw new IllegalStateException("Could not prepare the controlled Wrought Chamber start", exception);
        }

        DungeonInstance created = createAndDeleteFullInstance(helper, WROUGHT_CHAMBER, chamber, EnvironmentType.CAVE, false);
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(DungeonInstanceManager.get(level.getServer()).get(created.id()).isEmpty(),
                "Waiting for full Wrought Chamber instance cleanup"))
            .thenSucceed();
    }

    private static DungeonInstance createAndDeleteFullInstance(
        GameTestHelper helper,
        ResourceLocation id,
        Structure structure,
        EnvironmentType expectedEnvironment,
        boolean requireMountainBiome
    ) {
        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        List<String> previous = List.copyOf(ServerConfig.INSTANCE.structures.get());
        DungeonInstance instance = null;
        try {
            ServerConfig.INSTANCE.structures.set(List.of(id.toString()));
            manager.rebuildCatalogue();
            instance = manager.create(id);
            var plan = instance.plan().orElseThrow();
            var instanceLevel = helper.getLevel().getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, instance.dimensionId()));
            helper.assertTrue(instanceLevel != null, "Full " + id + " instance did not retain its runtime level");
            helper.assertValueEqual(plan.definition().environment(), expectedEnvironment,
                "Full " + id + " instance used the wrong terrain handler");
            if (requireMountainBiome) {
                Holder<Biome> biome = instanceLevel.registryAccess().registryOrThrow(Registries.BIOME)
                    .getHolderOrThrow(ResourceKey.create(Registries.BIOME, instance.biomeId()));
                helper.assertTrue(biome.is(MOUNTAIN_PEAKS),
                    "Full monastery instance selected a biome outside Mowzie's mountain-peak set");
                helper.assertTrue(GenerationPlan.usesSurfaceApproach(plan.definition().environment()),
                    "Full monastery instance used an underground approach");
            }
            helper.assertTrue(instanceLevel.getChunk(0, 0).getStartForStructure(structure).isValid(),
                "Full " + id + " placement did not register its retained structure start");
            helper.assertTrue(instanceLevel.getBlockState(plan.entryPosition())
                    .getCollisionShape(instanceLevel, plan.entryPosition()).isEmpty(),
                "Full " + id + " placement left its resolved entry obstructed");
            manager.delete(instance.id());
            return instance;
        } catch (InstanceOperationException exception) {
            if (instance != null) delete(manager, instance);
            throw new IllegalStateException("Could not create the full " + id + " instance", exception);
        } catch (RuntimeException exception) {
            if (instance != null) delete(manager, instance);
            throw exception;
        } finally {
            ServerConfig.INSTANCE.structures.set(previous);
            manager.rebuildCatalogue();
        }
    }

    private static Structure requireStructure(GameTestHelper helper, ResourceLocation id) {
        Structure structure = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE).get(id);
        helper.assertTrue(structure != null, "Mowzie's Mobs loaded without structure " + id);
        return structure;
    }

    private static DungeonChunkGenerator generator(
        net.minecraft.server.level.ServerLevel level,
        ResolvedDungeonDefinition resolved,
        long seed
    ) {
        var noise = level.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS)
            .getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        return new DungeonChunkGenerator(resolved.biome(), noise, GenerationPlan.fallback(seed, resolved.definition()));
    }

    private static void assertAllBiomesMatch(
        GameTestHelper helper,
        List<String> biomeIds,
        TagKey<Biome> tag,
        String message
    ) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        helper.assertFalse(biomeIds.isEmpty(), "Automatic Mowzie biome set was empty");
        for (String rawId : biomeIds) {
            Holder<Biome> biome = registry.getHolderOrThrow(
                ResourceKey.create(Registries.BIOME, ResourceLocation.parse(rawId)));
            helper.assertTrue(biome.is(tag), message + ": " + rawId);
        }
    }

    private static void delete(DungeonInstanceManager manager, DungeonInstance instance) {
        try {
            manager.delete(instance.id());
        } catch (InstanceOperationException cleanup) {
            throw new IllegalStateException("Could not clean up Mowzie instance", cleanup);
        }
    }
}
