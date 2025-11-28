package com.example.GooRoomBe.account.auth.security.principal;

import com.example.GooRoomBe.account.auth.domain.LocalAuth;
import com.example.GooRoomBe.account.user.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class LocalUserDetails implements UserDetails {
    private final LocalAuth localAuth;

    public LocalUserDetails(LocalAuth localAuth) {
        this.localAuth = localAuth;
    }

    @Override
    public boolean isEnabled() {
        return localAuth.getUser().isActive();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return localAuth.getPassword();
    }

    @Override
    public String getUsername() {
        return localAuth.getUser().getEmail();
    }

    public User getUser() {
        return localAuth.getUser();
    }
}