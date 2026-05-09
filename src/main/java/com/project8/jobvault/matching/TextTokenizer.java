package com.project8.jobvault.matching;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class TextTokenizer {
    private static final Pattern NON_TOKEN = Pattern.compile("[^a-z0-9]+");

    private final Set<String> stopwords;

    public TextTokenizer(Set<String> stopwords) {
        if (stopwords == null || stopwords.isEmpty()) {
            this.stopwords = Set.of();
        } else {
            Set<String> normalized = new HashSet<>();
            for (String stopword : stopwords) {
                if (stopword == null) {
                    continue;
                }
                String normalizedStopword = normalize(stopword);
                if (!normalizedStopword.isEmpty()) {
                    normalized.add(normalizedStopword);
                }
            }
            this.stopwords = Collections.unmodifiableSet(normalized);
        }
    }

    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        String[] parts = normalized.split("\\s+");
        List<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!stopwords.contains(part)) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private static String normalize(String input) {
        String lowered = input.toLowerCase(Locale.ROOT);
        return NON_TOKEN.matcher(lowered).replaceAll(" ").trim();
    }
}
