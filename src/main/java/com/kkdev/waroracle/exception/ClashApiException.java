package com.kkdev.waroracle.exception;

import com.kkdev.waroracle.dto.common.ErrorCodes;

public class ClashApiException extends RuntimeException {

    private final ErrorCodes errorCode;

    public ClashApiException(ErrorCodes errorCode) {
        super(errorCode.getErrorDescription());
        this.errorCode = errorCode;
    }

    public ErrorCodes getErrorCode() {
        return errorCode;
    }
}