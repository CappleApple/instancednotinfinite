package com.cappleapple.instancednotinfinite.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cappleapple.instancednotinfinite.manifestation.AnimationMode;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationScoreMath;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class HologramRevealTimingTest {
    @Test
    void allAnimatedModesKeepRevealingThroughoutTheLoadingBar() {
        for (AnimationMode mode : AnimationMode.values()) {
            if (mode == AnimationMode.NONE || mode == AnimationMode.RANDOM_MODE) continue;
            for (long seed : new long[]{42L, -3399384581734563066L}) {
                double[] scores = new double[128 * 128];
                for (int x = 0; x < 128; x++) {
                    for (int z = 0; z < 128; z++) {
                        scores[x * 128 + z] = ManifestationScoreMath.score(
                            x, 4, z, 0, 0, 0, 127, 31, 127, mode, seed);
                    }
                }
                double minimum = Arrays.stream(scores).min().orElseThrow();
                double maximum = Arrays.stream(scores).max().orElseThrow();
                int[] counts = new int[64];
                HologramRevealPlan.Builder builder = new HologramRevealPlan.Builder(64, true);
                for (double score : scores) {
                    int bucket = HologramMeshPlanner.bucket(score, minimum, maximum, 64);
                    counts[bucket]++;
                    builder.add(bucket, score);
                }
                HologramRevealPlan plan = builder.build();
                for (float progress : new float[]{0.25F, 0.5F, 0.75F, 0.9F}) {
                    int visible = plan.visibleBucketCount(progress, Double.NEGATIVE_INFINITY);
                    int revealed = Arrays.stream(counts, 0, visible).sum();
                    double fraction = revealed / (double)scores.length;
                    assertTrue(Math.abs(progress - fraction) < 0.08,
                        mode + " at " + progress + " revealed " + fraction + " for seed " + seed);
                }
                int almostDone = plan.visibleBucketCount(Math.nextDown(1.0F), Double.NEGATIVE_INFINITY);
                assertTrue(Arrays.stream(counts, 0, almostDone).sum() < scores.length, mode.name());
                int done = plan.visibleBucketCount(1.0F, Double.NEGATIVE_INFINITY);
                assertEquals(scores.length, Arrays.stream(counts, 0, done).sum());
            }
        }
    }

    @Test
    void onlyRenderedBucketsSetTheFinishTime() {
        HologramRevealPlan.Builder builder = new HologramRevealPlan.Builder(64, true);
        builder.add(10, 0.2);
        builder.add(20, 0.4);
        HologramRevealPlan plan = builder.build();
        assertEquals(0, plan.visibleBucketCount(-0.01F, Double.NEGATIVE_INFINITY));
        assertEquals(11, plan.visibleBucketCount(0.0F, Double.NEGATIVE_INFINITY));
        assertEquals(11, plan.visibleBucketCount(0.999F, Double.NEGATIVE_INFINITY));
        assertEquals(21, plan.visibleBucketCount(1.0F, Double.NEGATIVE_INFINITY));
    }

    @Test
    void streamingMoreEarlyScoresDoesNotHideTheRevealedFront() {
        HologramRevealPlan.Builder builder = new HologramRevealPlan.Builder(64, true);
        for (int index = 20; index <= 23; index++) builder.add(index, index / 64.0);
        HologramRevealPlan first = builder.build();
        double revealed = first.revealedScore(first.visibleBucketCount(0.6F, Double.NEGATIVE_INFINITY));
        for (int index = 0; index < 100; index++) builder.add(0, 0.0);
        HologramRevealPlan next = builder.build();
        assertTrue(next.revealedScore(next.visibleBucketCount(0.6F, Double.NEGATIVE_INFINITY)) < revealed);
        int visible = next.visibleBucketCount(0.6F, revealed);
        assertTrue(next.revealedScore(visible) >= revealed);
        assertTrue(visible < next.visibleBucketCount(1.0F, revealed));
    }

    @Test
    void noAnimationStillShowsTheWholeModelImmediately() {
        HologramRevealPlan.Builder builder = new HologramRevealPlan.Builder(64, false);
        builder.add(0, 0.0);
        builder.add(63, 1.0);
        assertEquals(64, builder.build().visibleBucketCount(0.0F, Double.NEGATIVE_INFINITY));
    }

    @Test
    void emptyAndSingleBucketModelsHaveDefinedCompletion() {
        HologramRevealPlan.Builder builder = new HologramRevealPlan.Builder(64, true);
        assertEquals(0, builder.build().visibleBucketCount(1.0F, Double.NEGATIVE_INFINITY));
        builder.add(0, 0.4);
        assertEquals(0, builder.build().visibleBucketCount(0.999F, Double.NEGATIVE_INFINITY));
        assertEquals(1, builder.build().visibleBucketCount(1.0F, Double.NEGATIVE_INFINITY));
    }
}
