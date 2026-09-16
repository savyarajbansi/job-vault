package com.project8.jobvault.parsing;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillCatalogTest {

    @Test
    void extractsCanonicalSkillsAndAliasesFromPostingText() {
        SkillCatalog catalog = new SkillCatalog("classpath:skills/skill-dictionary.txt");

        List<String> skills = catalog.extractSkills(
                "Senior C# and .NET engineer. Build REST APIs with Spring Boot and CI/CD.");

        assertEquals(List.of("c#", ".net", "rest", "spring boot", "ci cd"), skills);
    }

    @Test
    void matchesSkillTermsOnlyAtTokenBoundaries() {
        SkillCatalog catalog = new SkillCatalog("classpath:skills/skill-dictionary.txt");

        assertEquals(List.of("javascript"), catalog.extractSkills("Springboard candidates use JavaScript experience."));
    }

    @Test
    void canonicalizesKnownAliasesForLexicalMatching() {
        SkillCatalog catalog = new SkillCatalog("classpath:skills/skill-dictionary.txt");

        assertEquals("javascript spring boot", catalog.canonicalizeText("JS Springboot"));
    }

    @Test
    void handlesHighlyRepetitiveShortTermsWithoutBuildingAnOccurrenceList() {
        SkillCatalog catalog = new SkillCatalog("classpath:skills/skill-dictionary.txt");
        String text = IntStream.range(0, 10_000)
                .mapToObj(ignored -> "go")
                .collect(Collectors.joining(" "));

        assertEquals(List.of("go"), catalog.extractSkills(text));
    }
}
