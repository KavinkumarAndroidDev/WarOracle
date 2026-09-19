package com.kkdev.waroracle.dto.common;

import lombok.Data;

@Data
public class ErrorResponse
{
	private String reason;
	private String message;
	private String type;
}
