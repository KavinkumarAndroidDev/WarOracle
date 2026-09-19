package com.kkdev.waroracle.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kkdev.waroracle.entity.ClanWarEntity;

@Repository
public interface ClanWarRepository extends JpaRepository<ClanWarEntity, String>
{
	List<ClanWarEntity> findByClanTagOrderByStartTimeDesc(String clanTag);

	Optional<ClanWarEntity> findByWarId(String warId);
}
