package com.kkdev.waroracle.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kkdev.waroracle.entity.WarAttackEntity;

@Repository
public interface WarAttackRepository extends JpaRepository<WarAttackEntity, Long>
{
	List<WarAttackEntity> findByWarId(String warId);

	void deleteByWarId(String warId);
}
