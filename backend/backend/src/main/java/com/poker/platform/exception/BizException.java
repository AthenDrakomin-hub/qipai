package com.poker.platform.exception;

/**
 * 业务异常
 */
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(String msg) {
        super(msg);
        this.code = 500;
    }

    public BizException(Integer code, String msg) {
        super(msg);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
