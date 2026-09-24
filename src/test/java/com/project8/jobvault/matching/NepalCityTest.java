package com.project8.jobvault.matching;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NepalCityTest {
    @Test
    void normalizesSupportedCityLabels() {
        assertEquals("Kathmandu", NepalCity.normalize("  kathmandu "));
        assertEquals("Pokhara", NepalCity.normalize("Pokhara"));
    }

    @Test
    void rejectsLocationsOutsideTheSupportedList() {
        assertThrows(IllegalArgumentException.class, () -> NepalCity.normalize("Austin, TX"));
    }
}
