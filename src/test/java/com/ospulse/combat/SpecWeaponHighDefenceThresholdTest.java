package com.ospulse.combat;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;

/**
 * Proves {@link SpecWeaponSelector#HIGH_DEFENCE_THRESHOLD}'s derivation
 * claim against the ACTUAL shipped {@code monsters.min.json.gz}, not just
 * against the one-off script used to compute it (see {@code
 * feedback_test_shipped_data_not_just_model} — a well-formed derivation can
 * still encode the wrong number if the script and the shipped data drift).
 * Recomputes the 90th percentile of the base-name-deduplicated defence-level
 * distribution directly from {@link MonsterRepository} and asserts it still
 * equals the constant. If the bundled monster set is ever refreshed, this
 * test fails loudly instead of leaving a silently stale threshold.
 */
public class SpecWeaponHighDefenceThresholdTest {
    static {
        BundledGson.set(new com.google.gson.Gson());
    }

    @Test
    public void thresholdIsThe90thPercentileOfDistinctMonsterDefenceLevels() {
        // Dedupe by base name, keeping each distinct monster's highest-defence
        // phase — mirrors how the one-off derivation script treated multi-phase
        // entries (e.g. Zulrah's three forms, a boss's enrage phase).
        Map<String, Integer> maxDefenceByBaseName = new HashMap<>();
        for (Monster monster : MonsterRepository.getInstance().all()) {
            String baseName = MonsterCombatRequirementRepository.baseNameOf(monster.name().toLowerCase(Locale.ROOT));
            maxDefenceByBaseName.merge(baseName, monster.defenceLevel(), Math::max);
        }

        int[] sorted = maxDefenceByBaseName.values().stream().mapToInt(Integer::intValue).sorted().toArray();
        int n = sorted.length;
        int p90Index = (int) Math.round(0.90 * (n - 1));
        int p90 = sorted[p90Index];

        assertEquals("re-derived p90 of the shipped monster defence distribution no longer matches "
                        + "SpecWeaponSelector.HIGH_DEFENCE_THRESHOLD — see that constant's javadoc for how to re-derive it",
                SpecWeaponSelector.HIGH_DEFENCE_THRESHOLD, p90);
    }
}
