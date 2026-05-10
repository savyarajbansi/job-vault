package com.project8.jobvault.matching;

import java.util.List;
import java.util.UUID;

public record SkillGapResponse(
        UUID jobId,
        List<String> missingSkills) {
}
