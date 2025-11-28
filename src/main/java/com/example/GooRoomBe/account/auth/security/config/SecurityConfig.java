package com.example.GooRoomBe.account.auth.security.config;

import com.example.GooRoomBe.account.auth.security.handler.OAuth2AuthenticationSuccessHandler;
import com.example.GooRoomBe.account.auth.security.provider.CustomAuthenticationProvider;
import com.example.GooRoomBe.account.auth.security.filter.JsonUsernamePasswordAuthenticationFilter;
import com.example.GooRoomBe.account.auth.security.handler.LoginFailureHandler;
import com.example.GooRoomBe.account.auth.security.handler.LoginSuccessHandler;
import com.example.GooRoomBe.account.auth.security.filter.JwtAuthenticationFilter;
import com.example.GooRoomBe.account.auth.security.service.CustomOAuth2UserService;
import com.example.GooRoomBe.account.auth.security.handler.CustomAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public JsonUsernamePasswordAuthenticationFilter jsonUsernamePasswordAuthenticationFilter(
            AuthenticationManager authenticationManager,
            LoginSuccessHandler loginSuccessHandler,
            LoginFailureHandler loginFailureHandler,
            ObjectMapper objectMapper
    ) {
        JsonUsernamePasswordAuthenticationFilter jsonAuthFilter = new JsonUsernamePasswordAuthenticationFilter(objectMapper);
        jsonAuthFilter.setFilterProcessesUrl("/api/v1/auth/login");
        jsonAuthFilter.setAuthenticationManager(authenticationManager);
        jsonAuthFilter.setAuthenticationSuccessHandler(loginSuccessHandler);
        jsonAuthFilter.setAuthenticationFailureHandler(loginFailureHandler);
        return jsonAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AuthenticationManager authenticationManager,
                                           JsonUsernamePasswordAuthenticationFilter jsonAuthFilter,
                                           CustomAuthenticationProvider customAuthenticationProvider,
                                           JwtAuthenticationFilter jwtAuthenticationFilter,
                                           CustomOAuth2UserService customOAuth2UserService,
                                           OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler,
                                           CustomAuthenticationEntryPoint customAuthenticationEntryPoint
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http
                .authenticationProvider(customAuthenticationProvider)
                .addFilterAt(jsonAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, JsonUsernamePasswordAuthenticationFilter.class);

        // OAuth2 로그인 설정
        http
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2AuthenticationSuccessHandler));

        // URL 권한 설정
        http
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/oauth2/**",
                                "/error"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
                        .anyRequest().authenticated());

        // 예외 처리 설정
        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(customAuthenticationEntryPoint)
        );

        return http.build();
    }
}