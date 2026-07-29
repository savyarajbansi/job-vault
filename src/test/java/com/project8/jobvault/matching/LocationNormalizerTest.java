package com.project8.jobvault.matching;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationNormalizerTest {
    @Test
    void matchesCityAliasesAndRegionAliases() {
        assertTrue(LocationNormalizer.matches("Austin, TX", "Austin, Texas"));
        assertTrue(LocationNormalizer.matches("NYC, USA", "New York, NY"));
        assertTrue(LocationNormalizer.matches("Kathmandu", "Kathmandu, Nepal"));
    }

    @Test
    void rejectsDifferentCitiesEvenWhenTheyShareARegion() {
        assertFalse(LocationNormalizer.matches("Austin, TX", "Dallas, TX"));
        assertFalse(LocationNormalizer.matches("New York, NY", "New York, Canada"));
    }
}
