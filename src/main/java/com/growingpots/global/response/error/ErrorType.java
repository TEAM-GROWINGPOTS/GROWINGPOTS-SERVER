package com.growingpots.global.response.error;

import org.springframework.http.HttpStatus;

public interface ErrorType {
    HttpStatus getStatus();
    String getCode();
    String getMessage();
}