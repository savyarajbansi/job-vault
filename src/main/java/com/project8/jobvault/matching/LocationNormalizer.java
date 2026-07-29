package com.project8.jobvault.matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Converts common free-form locations into a comparable city/region key. */
final class LocationNormalizer {
    private static final Map<String, String> CITY_ALIASES = aliases(
            entry("austin", "austin"),
            entry("new york", "new york"), entry("new york city", "new york"), entry("nyc", "new york"),
            entry("san francisco", "san francisco"), entry("sf", "san francisco"),
            entry("los angeles", "los angeles"), entry("la", "los angeles"),
            entry("washington dc", "washington dc"), entry("washington d c", "washington dc"),
            entry("chicago", "chicago"), entry("boston", "boston"), entry("seattle", "seattle"),
            entry("kathmandu", "kathmandu"), entry("lalitpur", "lalitpur"), entry("patan", "lalitpur"),
            entry("pokhara", "pokhara"), entry("london", "london"), entry("paris", "paris"),
            entry("berlin", "berlin"), entry("toronto", "toronto"), entry("vancouver", "vancouver"),
            entry("sydney", "sydney"), entry("melbourne", "melbourne"), entry("singapore", "singapore"),
            entry("dubai", "dubai"), entry("bangalore", "bengaluru"), entry("bengaluru", "bengaluru"),
            entry("mumbai", "mumbai"), entry("delhi", "new delhi"), entry("new delhi", "new delhi"));

    private static final Map<String, String> REGION_ALIASES = aliases(
            entry("us", "united states"), entry("usa", "united states"),
            entry("united states", "united states"), entry("america", "united states"),
            entry("tx", "united states"), entry("texas", "united states"),
            entry("ny", "united states"), entry("new york state", "united states"),
            entry("ca", "united states"), entry("california", "united states"),
            entry("wa", "united states"), entry("washington state", "united states"),
            entry("il", "united states"), entry("illinois", "united states"),
            entry("ma", "united states"), entry("massachusetts", "united states"),
            entry("nepal", "nepal"), entry("uk", "united kingdom"),
            entry("united kingdom", "united kingdom"), entry("england", "united kingdom"),
            entry("canada", "canada"), entry("australia", "australia"),
            entry("france", "france"), entry("germany", "germany"),
            entry("india", "india"), entry("uae", "united arab emirates"),
            entry("united arab emirates", "united arab emirates"));

    private static final List<String> CITY_ALIASES_BY_LENGTH = sortedKeys(CITY_ALIASES);
    private static final List<String> REGION_ALIASES_BY_LENGTH = sortedKeys(REGION_ALIASES);
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");

    private LocationNormalizer() {
    }

    static LocationKey normalize(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String[] parts = location.split(",", 2);
        String cleaned = clean(location);
        String city = findAlias(cleaned, CITY_ALIASES_BY_LENGTH, CITY_ALIASES);
        if (city == null) {
            String firstPart = clean(parts[0]);
            city = firstPart.isBlank() ? null : firstPart;
        }
        String region = findAlias(cleaned, REGION_ALIASES_BY_LENGTH, REGION_ALIASES);
        if (region == null && parts.length > 1) {
            String secondPart = clean(parts[1]);
            region = secondPart.isBlank() ? null : secondPart;
        }
        return city == null ? null : new LocationKey(city, region);
    }

    static boolean matches(String left, String right) {
        LocationKey first = normalize(left);
        LocationKey second = normalize(right);
        if (first == null || second == null || !first.city().equals(second.city())) {
            return false;
        }
        return first.region() == null || second.region() == null || first.region().equals(second.region());
    }

    private static String clean(String value) {
        return NON_ALPHANUMERIC.matcher(value.toLowerCase(Locale.ROOT).trim())
                .replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    private static String findAlias(String value, List<String> aliases, Map<String, String> canonicalByAlias) {
        for (String alias : aliases) {
            if (containsPhrase(value, alias)) {
                return canonicalByAlias.get(alias);
            }
        }
        return null;
    }

    private static boolean containsPhrase(String value, String phrase) {
        List<String> valueTokens = Arrays.asList(value.split(" "));
        List<String> phraseTokens = Arrays.asList(phrase.split(" "));
        if (phraseTokens.size() > valueTokens.size()) {
            return false;
        }
        for (int start = 0; start <= valueTokens.size() - phraseTokens.size(); start++) {
            if (valueTokens.subList(start, start + phraseTokens.size()).equals(phraseTokens)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> aliases(Map.Entry<String, String>... entries) {
        Map<String, String> aliases = new HashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            aliases.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(aliases);
    }

    private static Map.Entry<String, String> entry(String alias, String canonical) {
        return Map.entry(alias, canonical);
    }

    private static List<String> sortedKeys(Map<String, String> aliases) {
        List<String> keys = new ArrayList<>(aliases.keySet());
        keys.sort((left, right) -> Integer.compare(right.length(), left.length()));
        return List.copyOf(keys);
    }

    record LocationKey(String city, String region) {
        LocationKey {
            Objects.requireNonNull(city);
        }
    }
}
