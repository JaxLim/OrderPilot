package com.orderpilot.common.error;

public class BizException extends RuntimeException {

    private final ErrorCode code;

    public BizException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    public static BizException notFound(String what, Object id) {
        return new BizException(ErrorCode.NOT_FOUND, what + " not found: " + id);
    }

    public static BizException invalidState(String message) {
        return new BizException(ErrorCode.INVALID_STATE, message);
    }
}
