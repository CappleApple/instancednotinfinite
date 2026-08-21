package com.cappleapple.instancednotinfinite.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HologramMeshPlannerTest {
    private static final int[][] DIRECTIONS = {
        {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    @Test
    void adjacentVoxelsDropTheirSharedFaces() {
        Set<Point> occupied = Set.of(new Point(0, 0, 0), new Point(1, 0, 0));

        int first = faces(new Point(0, 0, 0), occupied);
        int second = faces(new Point(1, 0, 0), occupied);

        assertEquals(5, Integer.bitCount(first));
        assertEquals(5, Integer.bitCount(second));
    }

    @Test
    void fullyEnclosedVoxelEmitsNoDirectionalFaces() {
        Set<Point> occupied = new HashSet<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) occupied.add(new Point(x, y, z));
            }
        }

        assertEquals(0, faces(new Point(0, 0, 0), occupied));
        assertEquals(3, Integer.bitCount(faces(new Point(1, 1, 1), occupied)));
    }

    @Test
    void revealBucketsClampAndAdvanceWithoutPerFaceTests() {
        assertEquals(0, HologramMeshPlanner.bucket(-1.0, 64));
        assertEquals(0, HologramMeshPlanner.bucket(0.0, 64));
        assertEquals(32, HologramMeshPlanner.bucket(0.5, 64));
        assertEquals(63, HologramMeshPlanner.bucket(1.0, 64));
        assertEquals(0, HologramMeshPlanner.visibleBucketCount(-0.001F, 64));
        assertEquals(1, HologramMeshPlanner.visibleBucketCount(0.0F, 64));
        assertEquals(1, HologramMeshPlanner.visibleBucketCount(0.001F, 64));
        assertEquals(33, HologramMeshPlanner.visibleBucketCount(0.5F, 64));
        assertEquals(64, HologramMeshPlanner.visibleBucketCount(1.0F, 64));
    }

    private static int faces(Point position, Set<Point> occupied) {
        return HologramMeshPlanner.visibleFaceMask(DIRECTIONS.length, face -> {
            int[] direction = DIRECTIONS[face];
            return occupied.contains(new Point(
                position.x + direction[0], position.y + direction[1], position.z + direction[2]));
        });
    }

    private record Point(int x, int y, int z) {
    }
}
