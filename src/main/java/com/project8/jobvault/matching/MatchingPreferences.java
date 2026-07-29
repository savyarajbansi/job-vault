package com.project8.jobvault.matching;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class MatchingPreferences {
    private MatchingPreferences() {
    }

    public static List<String> parseSectors(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    public static String joinSectors(Collection<String> sectors) {
        if (sectors == null) {
            return null;
        }
        String joined = sectors.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.joining(","));
        return joined.isBlank() ? null : joined;
    }

    public static boolean sectorMatches(String preferredCsv, String jobCsv) {
        Set<String> preferred = new LinkedHashSet<>(parseSectors(preferredCsv));
        if (preferred.isEmpty()) {
            return true;
        }
        Set<String> job = new LinkedHashSet<>(parseSectors(jobCsv));
        return preferred.stream().anyMatch(job::contains);
    }

    public static boolean workModeMatches(WorkMode preference, WorkMode jobMode) {
        if (preference == null || jobMode == null) {
            return true;
        }
        return switch (preference) {
            case ON_SITE -> jobMode == WorkMode.ON_SITE || jobMode == WorkMode.HYBRID;
            case REMOTE -> jobMode == WorkMode.REMOTE || jobMode == WorkMode.HYBRID;
            case HYBRID -> jobMode == WorkMode.HYBRID;
        };
    }
}
