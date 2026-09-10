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
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SkillCatalog {
    private final Map<String, String> canonicalByTerm;
    private final Pattern extractionPattern;

    public SkillCatalog(@Value("${jobvault.parsing.skill-dictionary}") String resourcePath) {
        List<SkillDefinition> definitions = loadMatchers(resourcePath);
        Map<String, String> aliases = new HashMap<>();
        for (SkillDefinition definition : definitions) {
            for (String term : definition.terms()) {
                aliases.put(term, definition.canonical());
            }
        }
        this.canonicalByTerm = Map.copyOf(aliases);
        this.extractionPattern = buildExtractionPattern(this.canonicalByTerm.keySet());
    }

    public List<String> extractSkills(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = normalizeText(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        var matcher = extractionPattern.matcher(normalized);
        while (matcher.find()) {
            String canonical = canonicalByTerm.get(matcher.group(1));
            if (canonical != null) {
                unique.add(canonical);
            }
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

    private static List<SkillDefinition> loadMatchers(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        List<SkillDefinition> loaded = new ArrayList<>();
        try (InputStream inputStream = openResource(resourcePath);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                SkillDefinition definition = parseLine(trimmed);
                if (definition != null) {
                    loaded.add(definition);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load skill dictionary: " + resourcePath, ex);
        }
        return loaded;
    }

    private static SkillDefinition parseLine(String line) {
        String[] parts = line.split("\\|");
        if (parts.length == 0) {
            return null;
        }
        String canonical = normalizeTerm(parts[0]);
        if (canonical.isEmpty()) {
            return null;
        }
        List<String> terms = new ArrayList<>();
        terms.add(canonical);
        for (int i = 1; i < parts.length; i++) {
            String synonym = normalizeTerm(parts[i]);
            if (!synonym.isEmpty() && !synonym.equals(canonical)) {
                terms.add(synonym);
            }
        }
        return new SkillDefinition(canonical, terms);
    }

    private static Pattern buildExtractionPattern(Iterable<String> terms) {
        List<String> orderedTerms = new ArrayList<>();
        for (String term : terms) {
            orderedTerms.add(term);
        }
        orderedTerms.sort(Comparator.comparingInt(String::length).reversed().thenComparing(String::compareTo));
        if (orderedTerms.isEmpty()) {
            // The dictionary is expected to contain entries, but keeping a
            // non-matching pattern makes an empty/misconfigured dictionary a
            // safe, predictable no-op.
            return Pattern.compile("(?!x)x");
        }
        String alternatives = orderedTerms.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        return Pattern.compile("(?<![a-z0-9])(" + alternatives + ")(?![a-z0-9])");
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

    private record SkillDefinition(String canonical, List<String> terms) {
    }
}
