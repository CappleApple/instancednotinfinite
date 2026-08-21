package com.cappleapple.instancednotinfinite.definition;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredStructureSelectorTest {
    @Test
    void combinesDirectIdsAndTagsThenDeduplicatesAndExcludes() {
        Set<String> known = Set.of("minecraft:igloo", "minecraft:mineshaft", "example:crypt");
        Map<String, List<String>> tags = Map.of(
            "example:dungeons", List.of("minecraft:igloo", "example:crypt", "example:crypt"));

        ConfiguredStructureSelector.Result result = ConfiguredStructureSelector.resolve(
            List.of("minecraft:igloo", "minecraft:mineshaft", "minecraft:igloo"),
            List.of("example:dungeons", "example:dungeons"),
            List.of("minecraft:mineshaft"), known::contains, tag -> Optional.ofNullable(tags.get(tag)));

        assertEquals(List.of("example:crypt", "minecraft:igloo"), result.structureIds());
        assertEquals(List.of("direct", "tag #example:dungeons"), result.sources().get("minecraft:igloo"));
    }

    @Test
    void unknownIdsAndTagsAreDiagnosticsInsteadOfFailures() {
        ConfiguredStructureSelector.Result result = ConfiguredStructureSelector.resolve(
            List.of("example:missing", "not an id"), List.of("example:missing_tag"), List.of(),
            ignored -> false, ignored -> Optional.empty());

        assertTrue(result.structureIds().isEmpty());
        assertEquals(3, result.diagnostics().size());
    }
}
