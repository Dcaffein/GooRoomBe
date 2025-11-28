package com.example.GooRoomBe.account.auth.security.service;

import com.example.GooRoomBe.account.auth.domain.LocalAuth;
import com.example.GooRoomBe.account.auth.repository.LocalAuthRepository;
import com.example.GooRoomBe.account.auth.security.principal.LocalUserDetails;
import com.example.GooRoomBe.account.user.domain.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocalUserDetailsService implements UserDetailsService {

    private final LocalAuthRepository authRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        LocalAuth auth = authRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("해당 이메일을 사용하는 LocalAuth를 찾을 수 없습니다: " + email));

        return new LocalUserDetails(auth);
    }
}