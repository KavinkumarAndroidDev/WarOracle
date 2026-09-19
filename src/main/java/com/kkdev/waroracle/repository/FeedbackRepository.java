package com.kkdev.waroracle.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kkdev.waroracle.entity.FeedbackEntity;

@Repository
public interface FeedbackRepository extends JpaRepository<FeedbackEntity, Long> {

    List<FeedbackEntity> findTop20ByOrderByCreatedAtDesc();
}
