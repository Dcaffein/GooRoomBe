package com.example.DunbarHorizon.support;

import com.example.DunbarHorizon.account.application.port.in.LoginUseCase;
import com.example.DunbarHorizon.account.application.port.in.SignupUseCase;
import com.example.DunbarHorizon.account.application.port.in.UserProfileUpdateUseCase;
import com.example.DunbarHorizon.account.application.port.in.VerificationUseCase;
import com.example.DunbarHorizon.account.application.port.out.ProfileImageStoragePort;
import com.example.DunbarHorizon.buzz.application.port.out.ImageStoragePort;
import com.example.DunbarHorizon.notification.application.NotificationService;
import com.example.DunbarHorizon.buzz.application.port.in.BuzzCommandUseCase;
import com.example.DunbarHorizon.buzz.application.port.in.BuzzQueryUseCase;
import com.example.DunbarHorizon.flag.application.port.in.*;
import com.example.DunbarHorizon.global.security.JwtAuthenticationFilter;
import com.example.DunbarHorizon.global.security.JwtTokenProvider;
import com.example.DunbarHorizon.global.security.AuthCookieManager;
import com.example.DunbarHorizon.social.application.port.in.*;
import com.example.DunbarHorizon.social.application.port.in.LabelCommandUseCase;
import com.example.DunbarHorizon.social.application.port.in.LabelQueryUseCase;
import com.example.DunbarHorizon.notification.application.NotificationService;
import com.example.DunbarHorizon.trace.application.port.in.TraceCommandUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@AutoConfigureMockMvc(addFilters = false)
@Import(TestSecurityConfig.class)
public abstract class BaseControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean protected JwtTokenProvider jwtTokenProvider;
    @MockitoBean protected JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean protected AuthCookieManager authCookieManager;

    @MockitoBean protected SignupUseCase signupUseCase;
    @MockitoBean protected LoginUseCase loginUseCase;
    @MockitoBean protected VerificationUseCase verificationUseCase;
    @MockitoBean protected UserProfileUpdateUseCase userProfileUpdateUseCase;
    @MockitoBean protected com.example.DunbarHorizon.account.application.port.in.UserQueryUseCase userQueryUseCase;

    @MockitoBean protected ProfileImageStoragePort profileImageStoragePort;
    @MockitoBean protected ImageStoragePort imageStoragePort;

    @MockitoBean protected BuzzCommandUseCase buzzCommandUseCase;
    @MockitoBean protected BuzzQueryUseCase buzzQueryUseCase;

    @MockitoBean protected FlagHostUseCase flagHostUseCase;
    @MockitoBean protected FlagModificationUseCase flagModificationUseCase;
    @MockitoBean protected FlagParticipationUseCase flagParticipationUseCase;
    @MockitoBean protected FlagQueryUseCase flagQueryUseCase;
    @MockitoBean protected FlagInvitationUseCase flagInvitationUseCase;
    @MockitoBean protected FlagInvitationQueryUseCase flagInvitationQueryUseCase;

    @MockitoBean protected FlagCommentCommandUseCase flagCommentCommandUseCase;
    @MockitoBean protected FlagCommentQueryUseCase flagCommentQueryUseCase;

    @MockitoBean protected FlagMemorialCommandUseCase flagMemorialCommandUseCase;
    @MockitoBean protected FlagMemorialQueryUseCase flagMemorialQueryUseCase;

    @MockitoBean protected FriendRequesterActionUseCase friendRequesterActionUseCase;
    @MockitoBean protected FriendRequestReceiverActionUseCase friendRequestReceiverActionUseCase;
    @MockitoBean protected FriendRequestQueryUseCase friendRequestQueryUseCase;

    @MockitoBean protected FriendshipCommandUseCase friendshipCommandUseCase;
    @MockitoBean protected FriendshipQueryUseCase friendshipQueryUseCase;

    @MockitoBean protected LabelCommandUseCase labelCommandUseCase;
    @MockitoBean protected LabelQueryUseCase labelQueryUseCase;

    @MockitoBean protected SocialExpansionQueryUseCase socialExpansionQueryUseCase;
    @MockitoBean protected SocialNetworkQueryUseCase socialNetworkQueryUseCase;
    @MockitoBean protected SocialConnectionPathQueryUseCase socialConnectionPathQueryUseCase;
    @MockitoBean protected com.example.DunbarHorizon.social.application.port.in.SocialUserQueryUseCase socialUserQueryUseCase;

    @MockitoBean protected TraceCommandUseCase traceCommandUseCase;

    // TODO: NotificationController가 구체 서비스를 직접 주입받음 — 포트 인터페이스 분리 필요 (별도 task)
    @MockitoBean protected NotificationService notificationService;

}