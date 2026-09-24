package com.project8.jobvault.matching;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Canonical city values accepted by new seeker and job location updates. */
public enum NepalCity {
    KATHMANDU("Kathmandu"),
    LALITPUR("Lalitpur"),
    BHAKTAPUR("Bhaktapur"),
    POKHARA("Pokhara"),
    BHARATPUR("Bharatpur"),
    BIRATNAGAR("Biratnagar"),
    BIRGUNJ("Birgunj"),
    BUTWAL("Butwal"),
    DHARAN("Dharan"),
    HETAUDA("Hetauda"),
    JANAKPUR("Janakpur"),
    NEPALGUNJ("Nepalgunj"),
    DHANGADHI("Dhangadhi"),
    ITAHARI("Itahari"),
    TULSIPUR("Tulsipur");

    private final String label;

    NepalCity(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(city -> city.label.toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst()
                .map(NepalCity::label)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Location must be one of the supported Nepal cities"));
    }

    public static List<String> labels() {
        return Arrays.stream(values()).map(NepalCity::label).toList();
    }
}
