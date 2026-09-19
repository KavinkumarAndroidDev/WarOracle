package com.kkdev.waroracle.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kkdev.waroracle.entity.SimulationRunEntity;

@Repository
public interface SimulationRunRepository extends JpaRepository<SimulationRunEntity, String>
{
	List<SimulationRunEntity> findByClanTagOrderByCreatedAtDesc(String clanTag);

	List<SimulationRunEntity> findByWarIdOrderByCreatedAtDesc(String warId);
}
