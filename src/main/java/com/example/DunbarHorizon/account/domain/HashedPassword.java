package com.example.DunbarHorizon.account.domain;

public record HashedPassword(String value) {

    public HashedPassword {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("해시 값은 비어 있을 수 없습니다.");
        }
    }
}
