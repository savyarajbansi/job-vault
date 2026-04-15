package com.project8.jobvault.skills;

import java.math.BigDecimal;
import java.util.UUID;

public interface TrendingSkillRow {
    UUID getSkillId();

    String getSkillName();

    BigDecimal getScore();
}
