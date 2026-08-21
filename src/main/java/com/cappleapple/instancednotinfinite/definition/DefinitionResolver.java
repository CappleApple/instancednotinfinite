package com.cappleapple.instancednotinfinite.definition;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class DefinitionResolver {
    private DefinitionResolver() {
    }

    public static ResolvedDungeonDefinition resolve(
        RegistryAccess access,
        StructureTemplateManager templates,
        DungeonDefinition definition,
        long seed
    ) throws ResolutionException {
        ResourceLocation structureId = ResourceLocation.tryParse(definition.structure());
        if (structureId == null) {
            throw new ResolutionException("Invalid structure id " + definition.structure());
        }
        StructureKind kind = resolveStructureKind(access, templates, structureId, definition.structureKind());
        BiomeSelector.Selection biome = BiomeSelector.select(access, definition, seed);
        return new ResolvedDungeonDefinition(definition, structureId, kind, biome.holder(), biome.id());
    }

    private static StructureKind resolveStructureKind(
        RegistryAccess access,
        StructureTemplateManager templates,
        ResourceLocation id,
        StructureKind requested
    ) throws ResolutionException {
        Registry<Structure> structures = access.registryOrThrow(Registries.STRUCTURE);
        boolean worldgen = structures.containsKey(id);
        boolean template = templates.get(id).isPresent();
        if (requested == StructureKind.WORLDGEN && !worldgen) {
            throw new ResolutionException("Unknown worldgen structure " + id);
        }
        if (requested == StructureKind.TEMPLATE && !template) {
            throw new ResolutionException("Unknown structure template " + id);
        }
        if (requested == StructureKind.AUTO) {
            if (worldgen) {
                return StructureKind.WORLDGEN;
            }
            if (template) {
                return StructureKind.TEMPLATE;
            }
            throw new ResolutionException("No worldgen structure or structure template exists at " + id);
        }
        return requested;
    }
}
