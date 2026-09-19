package com.kkdev.waroracle.service;

import java.util.List;

import com.kkdev.waroracle.dto.feedback.FeedbackRequest;
import com.kkdev.waroracle.dto.feedback.FeedbackResponse;

public interface FeedbackService {

    FeedbackResponse submitFeedback(FeedbackRequest request, String clientIp);

    List<FeedbackResponse> getRecentFeedback();
}
