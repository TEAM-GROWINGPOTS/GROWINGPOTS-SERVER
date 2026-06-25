package com.growingpots.global.response.success;

import org.springframework.http.HttpStatus;

public interface SuccessType {
    HttpStatus getStatus();
    String getCode();
    String getMessage();
}