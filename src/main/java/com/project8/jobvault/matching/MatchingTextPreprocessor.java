package com.project8.jobvault.matching;

import com.project8.jobvault.parsing.SkillCatalog;
import java.util.List;
import org.springframework.stereotype.Component;

/** Prepares lexical matching text while leaving embedding text untouched. */
@Component
public class MatchingTextPreprocessor {
    private final SkillCatalog skillCatalog;
    private final TextTokenizer tokenizer = new TextTokenizer(MatchingStopwords.DEFAULT);

    public MatchingTextPreprocessor(SkillCatalog skillCatalog) {
        this.skillCatalog = skillCatalog;
    }

    public List<String> tokenize(String text) {
        return tokenizer.tokenizeWithBigrams(skillCatalog.canonicalizeText(text));
    }
}
