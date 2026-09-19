package com.kkdev.waroracle.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kkdev.waroracle.entity.ClanEntity;

@Repository
public interface ClanRepository extends JpaRepository<ClanEntity, String>
{
}
