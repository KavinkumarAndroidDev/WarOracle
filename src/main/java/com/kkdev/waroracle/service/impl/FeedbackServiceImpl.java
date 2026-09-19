package com.kkdev.waroracle.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkdev.waroracle.dto.feedback.FeedbackRequest;
import com.kkdev.waroracle.dto.feedback.FeedbackResponse;
import com.kkdev.waroracle.entity.FeedbackEntity;
import com.kkdev.waroracle.repository.FeedbackRepository;
import com.kkdev.waroracle.service.FeedbackService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackRepository feedbackRepository;

    @Override
    @Transactional
    public FeedbackResponse submitFeedback(FeedbackRequest request, String clientIp) {
        log.info("Processing feedback submission in category: {}", request.getCategory());

        FeedbackEntity entity = FeedbackEntity.builder()
                .category(request.getCategory().trim())
                .message(request.getMessage().trim())
                .contact(request.getContact() != null ? request.getContact().trim() : null)
                .clientIp(clientIp)
                .build();

        FeedbackEntity saved = feedbackRepository.save(entity);
        log.info("Feedback successfully persisted with id: {}", saved.getId());

        return mapToResponse(saved, "DISPATCHED");
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackResponse> getRecentFeedback() {
        return feedbackRepository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(e -> mapToResponse(e, "RECEIVED"))
                .collect(Collectors.toList());
    }

    private FeedbackResponse mapToResponse(FeedbackEntity entity, String status) {
        return FeedbackResponse.builder()
                .id(entity.getId())
                .category(entity.getCategory())
                .message(entity.getMessage())
                .contact(entity.getContact())
                .createdAt(entity.getCreatedAt())
                .status(status)
                .build();
    }
}
