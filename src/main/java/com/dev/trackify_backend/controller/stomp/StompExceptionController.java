package com.dev.trackify_backend.controller.stomp;

import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.NoSuchElementException;

@ControllerAdvice
public class StompExceptionController {

    public record ErrorPayload(String code, String message, long timeStamp) {}

    @MessageExceptionHandler(NoSuchElementException.class)
    @SendToUser("/queue/errors")
    public ErrorPayload handleNoSuchElementException(NoSuchElementException e) {
        return new ErrorPayload("NOT_FOUND", e.getMessage(), System.currentTimeMillis());
    }

    @MessageExceptionHandler(RuntimeException.class)
    @SendToUser("/queue/errors")
    public ErrorPayload handleRuntimeException(RuntimeException e) {
        return new ErrorPayload("INTERNAL_ERROR", e.getMessage(), System.currentTimeMillis());
    }

    @MessageExceptionHandler(Throwable.class)
    @SendToUser("/queue/errors")
    public ErrorPayload handleAny(Throwable e) {
        return new ErrorPayload("INTERNAL_ERROR", "메시지 처리 중 오류가 발생했습니다.", System.currentTimeMillis());
    }
}
