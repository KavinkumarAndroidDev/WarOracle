package com.kkdev.waroracle.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.kkdev.waroracle.annotation.LogTag;
import com.kkdev.waroracle.common.URIConstants;
import com.kkdev.waroracle.config.LogTagInterceptor;
import com.kkdev.waroracle.dto.common.ApiResponse;
import com.kkdev.waroracle.dto.feedback.FeedbackRequest;
import com.kkdev.waroracle.dto.feedback.FeedbackResponse;
import com.kkdev.waroracle.service.FeedbackService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @LogTag("SUBMIT_WAR_FEEDBACK")
    @PostMapping(URIConstants.FEEDBACK)
    public ApiResponse submitFeedback(
            @Valid @RequestBody FeedbackRequest request,
            HttpServletRequest httpRequest) {

        log.info("Received request to submit feedback in category: {}", request != null ? request.getCategory() : null);
        String clientIp = httpRequest.getRemoteAddr();
        FeedbackResponse response = feedbackService.submitFeedback(request, clientIp);
        log.info("Successfully dispatched feedback with id: {}", response != null ? response.getId() : null);
        return ApiResponse.success(response, LogTagInterceptor.getCurrentTraceId());
    }

    @LogTag("GET_RECENT_WAR_FEEDBACK")
    @GetMapping(URIConstants.FEEDBACK)
    public ApiResponse getRecentFeedback() {
        log.info("Received request to fetch recent feedback notes");
        List<FeedbackResponse> responses = feedbackService.getRecentFeedback();
        return ApiResponse.success(responses, LogTagInterceptor.getCurrentTraceId());
    }
}
