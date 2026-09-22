package com.kkdev.waroracle.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.kkdev.waroracle.dto.model.EmpiricalMatchupStat;
import com.kkdev.waroracle.entity.WarAttackEntity;

@Repository
public interface WarAttackRepository extends JpaRepository<WarAttackEntity, Long>
{
	List<WarAttackEntity> findByWarId(String warId);

	void deleteByWarId(String warId);

	List<WarAttackEntity> findByAttackerTagIn(List<String> attackerTags);

	@Query("SELECT a.thDiff AS thDiff, a.stars AS stars, COUNT(a) AS sampleCount, AVG(a.destructionPercentage) AS avgDestruction FROM WarAttackEntity a GROUP BY a.thDiff, a.stars")
	List<EmpiricalMatchupStat> aggregateGlobalMatchupStats();
}
