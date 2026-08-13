package com.example.DunbarHorizon.account.application.port.out;

import com.example.DunbarHorizon.account.domain.HashedPassword;

public interface PasswordHasher {

    HashedPassword hash(String rawPassword);

    boolean matches(String rawPassword, HashedPassword hashedPassword);
}
