package com.project8.jobvault.matching;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchingPreferencesTest {

    @Test
    void preferredSectorsAreAnEligibilityFilterWithMultipleValues() {
        assertTrue(MatchingPreferences.sectorMatches("SOFTWARE,IT", "BUSINESS,IT"));
        assertFalse(MatchingPreferences.sectorMatches("SOFTWARE,IT", "BUSINESS,FINANCE"));
        assertTrue(MatchingPreferences.sectorMatches(null, "BUSINESS"));
        assertFalse(MatchingPreferences.sectorMatches("IT", null));
    }

    @Test
    void workModesFollowTheHybridCompatibilityRule() {
        assertTrue(MatchingPreferences.workModeMatches(WorkMode.ON_SITE, WorkMode.ON_SITE));
        assertTrue(MatchingPreferences.workModeMatches(WorkMode.ON_SITE, WorkMode.HYBRID));
        assertFalse(MatchingPreferences.workModeMatches(WorkMode.ON_SITE, WorkMode.REMOTE));
        assertTrue(MatchingPreferences.workModeMatches(WorkMode.REMOTE, WorkMode.REMOTE));
        assertTrue(MatchingPreferences.workModeMatches(WorkMode.REMOTE, WorkMode.HYBRID));
        assertFalse(MatchingPreferences.workModeMatches(WorkMode.REMOTE, WorkMode.ON_SITE));
        assertTrue(MatchingPreferences.workModeMatches(WorkMode.HYBRID, WorkMode.HYBRID));
        assertFalse(MatchingPreferences.workModeMatches(WorkMode.HYBRID, WorkMode.REMOTE));
        assertTrue(MatchingPreferences.workModeMatches(null, WorkMode.REMOTE));
        assertTrue(MatchingPreferences.workModeMatches(WorkMode.REMOTE, null));
    }
}
