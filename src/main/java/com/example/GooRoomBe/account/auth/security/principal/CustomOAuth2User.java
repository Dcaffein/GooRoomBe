package com.example.GooRoomBe.account.auth.security.principal;

import com.example.GooRoomBe.account.auth.domain.OAuth;
import com.example.GooRoomBe.account.user.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.Collection;
import java.util.Map;

public class CustomOAuth2User extends DefaultOAuth2User {
    private final OAuth oauth;

    /**
     * Constructs a {@code DefaultOAuth2User} using the provided parameters.
     *
     * @param authorities      the authorities granted to the user
     * @param attributes       the attributes about the user
     * @param nameAttributeKey the key used to access the user's &quot;name&quot; from the
     * {@link #getAttributes()} map
     * @param oauth             OAuth 엔티티
     */
    public CustomOAuth2User(Collection<? extends GrantedAuthority> authorities,
                            Map<String, Object> attributes, String nameAttributeKey,
                            OAuth oauth) {
        super(authorities, attributes, nameAttributeKey);
        this.oauth = oauth;
    }

    public User getUser() {
        return oauth.getUser();
    }
}

