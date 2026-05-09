package com.project8.jobvault.parsing;

import java.util.List;

public record ParseResult(String extractedText, List<String> inferredSkills) {
    public ParseResult {
        inferredSkills = inferredSkills == null ? List.of() : List.copyOf(inferredSkills);
    }
}
