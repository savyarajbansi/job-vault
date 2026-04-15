package com.project8.jobvault.skills;

import java.util.UUID;

public record TrendingSkillResponse(
        UUID skillId,
        String skillName,
        double score) {
}
