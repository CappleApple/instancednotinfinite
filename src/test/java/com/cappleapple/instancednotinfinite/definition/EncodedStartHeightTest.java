package com.cappleapple.instancednotinfinite.definition;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncodedStartHeightTest {
    @Test
    void readsCataclysmStyleAbsoluteStartHeight() {
        var encoded = JsonParser.parseString("""
            {"type":"cataclysm:cataclysm_jigsaw","start_height":{"absolute":200}}
            """);
        assertEquals(200, EncodedStartHeight.absolute(encoded).orElseThrow());
    }

    @Test
    void readsIntegratedStrongholdConstantUniformStartHeight() {
        var encoded = JsonParser.parseString("""
            {
              "type":"integrated_api:generic_structure",
              "start_height":{
                "type":"minecraft:uniform",
                "min_inclusive":{"absolute":15},
                "max_inclusive":{"absolute":15}
              }
            }
            """);
        assertEquals(15, EncodedStartHeight.absolute(encoded).orElseThrow());

        assertTrue(EncodedStartHeight.absolute(JsonParser.parseString("""
            {"start_height":{"min_inclusive":{"absolute":15},"max_inclusive":{"absolute":31}}}
            """)).isEmpty(), "a non-constant uniform range has no single authored absolute start height");
    }

    @Test
    void ignoresRelativeAndMissingStartHeights() {
        assertTrue(EncodedStartHeight.absolute(
            JsonParser.parseString("{\"start_height\":{\"above_bottom\":48}}")).isEmpty());
        assertTrue(EncodedStartHeight.absolute(JsonParser.parseString("{}")).isEmpty());
    }
}
