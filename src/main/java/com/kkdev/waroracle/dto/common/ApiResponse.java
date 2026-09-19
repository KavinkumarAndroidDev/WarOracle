package com.kkdev.waroracle.dto.common;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse
{

    private String status;
    private Integer errorcode;
    private String internalErrorMessage;
    private Map<String, String> errorField;
    private Object responseObject;
    private String traceId;

    public static ApiResponse success(Object responseObject, String traceId)
    {
        return ApiResponse.builder()
                .status("SUCCESS")
                .responseObject(responseObject)
                .traceId(traceId)
                .build();
    }

    public static ApiResponse failure(Integer errorcode, String internalErrorMessage, String traceId)
    {
        return ApiResponse.builder()
                .status("FAILED")
                .errorcode(errorcode)
                .internalErrorMessage(internalErrorMessage)
                .traceId(traceId)
                .build();
    }

    public static ApiResponse failure(Integer errorcode, String internalErrorMessage, Map<String, String> errorField, String traceId)
    {
        return ApiResponse.builder()
                .status("FAILED")
                .errorcode(errorcode)
                .internalErrorMessage(internalErrorMessage)
                .errorField(errorField)
                .traceId(traceId)
                .build();
    }
}
