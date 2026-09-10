package com.cappleapple.instancednotinfinite.client;

import java.util.Arrays;

/** Maps the populated mesh buckets onto the full animation clock without per-frame sorting. */
final class HologramRevealPlan {
    private final int[] counts;
    private final double[] minimumScores;
    private final double[] maximumScores;
    private final double[] starts;
    private final boolean animated;
    private final int lastBucket;

    private HologramRevealPlan(Builder builder) {
        counts = builder.counts.clone();
        minimumScores = builder.minimumScores.clone();
        maximumScores = builder.maximumScores.clone();
        animated = builder.animated;
        starts = new double[counts.length];
        int last = -1;
        long total = 0;
        for (int index = 0; index < counts.length; index++) {
            total += counts[index];
            if (counts[index] > 0) last = index;
        }
        lastBucket = last;
        long beforeLast = last < 0 ? 0 : total - counts[last];
        long preceding = 0;
        for (int index = 0; index < counts.length; index++) {
            starts[index] = beforeLast == 0 ? 1.0 : preceding / (double)beforeLast;
            preceding += counts[index];
        }
    }

    int visibleBucketCount(float progress, double previouslyRevealedScore) {
        if (lastBucket < 0 || progress < 0.0F) return 0;
        if (!animated || progress >= 1.0F) return lastBucket + 1;
        int visible = 0;
        // Keep the final populated bucket for completion, including one-bucket models.
        for (int index = 0; index < lastBucket; index++) {
            if (counts[index] > 0 && (starts[index] <= progress
                || minimumScores[index] <= previouslyRevealedScore)) {
                visible = index + 1;
            }
        }
        return visible;
    }

    double revealedScore(int visibleBuckets) {
        double score = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < visibleBuckets; index++) {
            score = Math.max(score, maximumScores[index]);
        }
        return score;
    }

    static final class Builder {
        private final int[] counts;
        private final double[] minimumScores;
        private final double[] maximumScores;
        private final boolean animated;

        Builder(int bucketCount, boolean animated) {
            if (bucketCount < 1) throw new IllegalArgumentException("bucketCount must be positive");
            counts = new int[bucketCount];
            minimumScores = new double[bucketCount];
            maximumScores = new double[bucketCount];
            Arrays.fill(minimumScores, Double.POSITIVE_INFINITY);
            Arrays.fill(maximumScores, Double.NEGATIVE_INFINITY);
            this.animated = animated;
        }

        void add(int bucket, double rawScore) {
            counts[bucket]++;
            minimumScores[bucket] = Math.min(minimumScores[bucket], rawScore);
            maximumScores[bucket] = Math.max(maximumScores[bucket], rawScore);
        }

        HologramRevealPlan build() {
            return new HologramRevealPlan(this);
        }
    }
}
