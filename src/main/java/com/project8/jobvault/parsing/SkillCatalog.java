package com.project8.jobvault.parsing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SkillCatalog {
    private final List<SkillMatcher> matchers;

    public SkillCatalog(@Value("${jobvault.parsing.skill-dictionary}") String resourcePath) {
        this.matchers = List.copyOf(loadMatchers(resourcePath));
    }

    public List<String> extractSkills(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = normalizeText(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> matches = new ArrayList<>();
        for (SkillMatcher matcher : matchers) {
            if (matcher.matches(normalized)) {
                matches.add(matcher.canonical());
            }
        }
        return matches;
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
        patterns.add(buildPattern(canonical));
        for (int i = 1; i < parts.length; i++) {
            String synonym = normalizeTerm(parts[i]);
            if (!synonym.isEmpty() && !synonym.equals(canonical)) {
                patterns.add(buildPattern(synonym));
            }
        }
        return new SkillMatcher(canonical, patterns);
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

    private record SkillMatcher(String canonical, List<Pattern> patterns) {
        private boolean matches(String text) {
            for (Pattern pattern : patterns) {
                if (pattern.matcher(text).find()) {
                    return true;
                }
            }
            return false;
        }
    }
}
