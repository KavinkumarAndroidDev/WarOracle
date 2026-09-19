package com.kkdev.waroracle.dto.feedback;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackResponse {

    private Long id;
    private String category;
    private String message;
    private String contact;
    private LocalDateTime createdAt;
    private String status;
}
