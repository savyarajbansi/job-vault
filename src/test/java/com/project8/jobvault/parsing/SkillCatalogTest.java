package com.project8.jobvault.parsing;

import java.util.List;
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
}
