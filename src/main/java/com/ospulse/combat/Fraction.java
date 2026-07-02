package com.ospulse.combat;

/**
 * A small exact-rational helper used for the OSRS combat maths' many
 * documented gear-bonus fractions (7/6, 6/5, 23/20, ...). Using integer
 * numerator/denominator arithmetic instead of {@code double} avoids
 * floating-point representation error changing a floor() result at an exact
 * boundary (e.g. 6 * 7/6 must floor to exactly 7, not 6 via a
 * 6.999999999-style rounding artifact) — correctness of the floor steps is
 * the entire point of this package.
 */
final class Fraction {
    static final Fraction ONE = new Fraction(1, 1);

    final long numerator;
    final long denominator;

    Fraction(long numerator, long denominator) {
        if (denominator == 0) {
            throw new IllegalArgumentException("denominator cannot be 0");
        }
        this.numerator = numerator;
        this.denominator = denominator;
    }

    /** floor(value * this) using exact integer division (value assumed non-negative). */
    long applyFloor(long value) {
        return Math.floorDiv(value * numerator, denominator);
    }

    /** Combines two fractions multiplicatively (used when two multiplicative bonuses legitimately stack). */
    Fraction times(Fraction other) {
        return new Fraction(this.numerator * other.numerator, this.denominator * other.denominator);
    }

    double asDouble() {
        return (double) numerator / (double) denominator;
    }

    /** True if this fraction is exactly 1 (no bonus) — all fractions in this package are constructed in this simple canonical form. */
    boolean isOne() {
        return numerator == denominator;
    }
}
