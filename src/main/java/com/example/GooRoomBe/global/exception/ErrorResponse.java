package com.example.GooRoomBe.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final String error;
    private final String message;
    private final Map<String, String> validation;

    @Builder
    public ErrorResponse(String error, String message, Map<String, String> validation) {
        this.error = error;
        this.message = message;
        this.validation = validation;
    }
}