package com.kkdev.waroracle.dto.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRequest {

    @NotBlank(message = "Category is required")
    @Size(max = 50, message = "Category must not exceed 50 characters")
    private String category;

    @NotBlank(message = "Feedback message cannot be empty")
    @Size(max = 2000, message = "Feedback message must not exceed 2000 characters")
    private String message;

    @Size(max = 100, message = "Contact information must not exceed 100 characters")
    private String contact;
}
