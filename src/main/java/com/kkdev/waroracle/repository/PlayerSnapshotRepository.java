package com.kkdev.waroracle.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kkdev.waroracle.entity.PlayerSnapshotEntity;

@Repository
public interface PlayerSnapshotRepository extends JpaRepository<PlayerSnapshotEntity, Long>
{
	List<PlayerSnapshotEntity> findByPlayerTagOrderBySnapshotTimeDesc(String playerTag);
}
