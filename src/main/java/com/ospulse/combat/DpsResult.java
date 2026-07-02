package com.ospulse.combat;

/**
 * The output of a {@link DpsCalculator#compute} call.
 */
public final class DpsResult {
    private final int maxHit;
    private final double accuracy;
    private final double dps;
    private final boolean baseEstimate;

    public DpsResult(int maxHit, double accuracy, double dps, boolean baseEstimate) {
        this.maxHit = maxHit;
        this.accuracy = accuracy;
        this.dps = dps;
        this.baseEstimate = baseEstimate;
    }

    /** The largest hit possible with this setup. */
    public int maxHit() {
        return maxHit;
    }

    /** Probability (0..1) that any single attack lands. */
    public double accuracy() {
        return accuracy;
    }

    /** Expected damage per second. */
    public double dps() {
        return dps;
    }

    /** True if one or more unmodelled (Tier B/C+) effects were skipped, so this number is a lower/approximate bound. */
    public boolean baseEstimate() {
        return baseEstimate;
    }

    @Override
    public String toString() {
        return "DpsResult{maxHit=" + maxHit + ", accuracy=" + accuracy + ", dps=" + dps
                + ", baseEstimate=" + baseEstimate + '}';
    }
}
