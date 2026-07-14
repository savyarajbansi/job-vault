package com.project8.jobvault.parsing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SkillCatalog {
    private final List<SkillMatcher> matchers;
    private final Map<String, String> canonicalByTerm;

    public SkillCatalog(@Value("${jobvault.parsing.skill-dictionary}") String resourcePath) {
        this.matchers = List.copyOf(loadMatchers(resourcePath));
        Map<String, String> aliases = new HashMap<>();
        for (SkillMatcher matcher : matchers) {
            for (String term : matcher.terms()) {
                aliases.put(term, matcher.canonical());
            }
        }
        this.canonicalByTerm = Map.copyOf(aliases);
    }

    public List<String> extractSkills(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = normalizeText(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        List<SkillMatch> candidates = new ArrayList<>();
        for (SkillMatcher matcher : matchers) {
            matcher.findMatches(normalized, candidates);
        }
        candidates.sort(Comparator.comparingInt(SkillMatch::start)
                .thenComparing(Comparator.comparingInt(SkillMatch::length).reversed()));

        List<SkillMatch> accepted = new ArrayList<>();
        for (SkillMatch candidate : candidates) {
            boolean overlaps = accepted.stream().anyMatch(existing ->
                    candidate.start() < existing.end() && existing.start() < candidate.end());
            if (!overlaps) {
                accepted.add(candidate);
            }
        }

        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (SkillMatch match : accepted) {
            unique.add(match.canonical());
        }
        return List.copyOf(unique);
    }

    /**
     * Converts a user-entered or persisted skill name to the catalog's
     * canonical name. Unknown skills remain normalized rather than being
     * discarded, so employers can still add skills outside the dictionary.
     */
    public String canonicalize(String value) {
        String normalized = normalizeTerm(value);
        if (normalized.isEmpty()) {
            return "";
        }
        return canonicalByTerm.getOrDefault(normalized, normalized);
    }

    private static List<SkillMatcher> loadMatchers(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        List<SkillMatcher> loaded = new ArrayList<>();
        try (InputStream inputStream = openResource(resourcePath);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                SkillMatcher matcher = parseLine(trimmed);
                if (matcher != null) {
                    loaded.add(matcher);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load skill dictionary: " + resourcePath, ex);
        }
        return loaded;
    }

    private static SkillMatcher parseLine(String line) {
        String[] parts = line.split("\\|");
        if (parts.length == 0) {
            return null;
        }
        String canonical = normalizeTerm(parts[0]);
        if (canonical.isEmpty()) {
            return null;
        }
        List<Pattern> patterns = new ArrayList<>();
        List<String> terms = new ArrayList<>();
        terms.add(canonical);
        patterns.add(buildPattern(canonical));
        for (int i = 1; i < parts.length; i++) {
            String synonym = normalizeTerm(parts[i]);
            if (!synonym.isEmpty() && !synonym.equals(canonical)) {
                terms.add(synonym);
                patterns.add(buildPattern(synonym));
            }
        }
        return new SkillMatcher(canonical, terms, patterns);
    }

    private static Pattern buildPattern(String term) {
        String escaped = Pattern.quote(term);
        return Pattern.compile("(?<![a-z0-9])" + escaped + "(?![a-z0-9])");
    }

    private static String normalizeText(String text) {
        String lowered = text.toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(lowered.length());
        boolean lastSpace = false;
        for (int i = 0; i < lowered.length(); i++) {
            char c = lowered.charAt(i);
            if (isAllowedChar(c)) {
                normalized.append(c);
                lastSpace = false;
            } else if (!lastSpace) {
                normalized.append(' ');
                lastSpace = true;
            }
        }
        return normalized.toString().trim();
    }

    private static String normalizeTerm(String term) {
        return normalizeText(term);
    }

    private static boolean isAllowedChar(char c) {
        return Character.isLetterOrDigit(c) || c == '+' || c == '#' || c == '.';
    }

    private static InputStream openResource(String resourcePath) throws IOException {
        if (resourcePath.startsWith("classpath:")) {
            String path = resourcePath.substring("classpath:".length());
            String normalized = path.startsWith("/") ? path.substring(1) : path;
            InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(normalized);
            if (stream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return stream;
        }
        return Files.newInputStream(Path.of(resourcePath));
    }

    private record SkillMatcher(String canonical, List<String> terms, List<Pattern> patterns) {
        private void findMatches(String text, List<SkillMatch> output) {
            for (Pattern pattern : patterns) {
                var matcher = pattern.matcher(text);
                while (matcher.find()) {
                    output.add(new SkillMatch(canonical, matcher.start(), matcher.end()));
                }
            }
        }
    }

    private record SkillMatch(String canonical, int start, int end) {
        private int length() {
            return end - start;
        }
    }
}
