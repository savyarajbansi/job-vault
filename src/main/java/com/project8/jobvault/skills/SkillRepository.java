package com.project8.jobvault.skills;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SkillRepository extends JpaRepository<Skill, UUID> {
    Optional<Skill> findByNameIgnoreCase(String name);

    @Query(value = """
            SELECT s.id AS skillId,
                   s.name AS skillName,
                   SUM(weight) AS score
            FROM (
                SELECT jrs.skill_id AS skill_id, 1.0 AS weight
                FROM job_required_skills jrs
                JOIN jobs j ON j.id = jrs.job_id
                WHERE j.status = 'ACTIVE'
                UNION ALL
                SELECT jps.skill_id AS skill_id, 0.5 AS weight
                FROM job_preferred_skills jps
                JOIN jobs j ON j.id = jps.job_id
                WHERE j.status = 'ACTIVE'
            ) weighted
            JOIN skills s ON s.id = weighted.skill_id
            GROUP BY s.id, s.name
            ORDER BY score DESC, s.name ASC
            LIMIT 10
            """, nativeQuery = true)
    List<TrendingSkillRow> findTrendingSkills();
}
