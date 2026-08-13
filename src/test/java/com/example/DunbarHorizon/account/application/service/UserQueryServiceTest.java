package com.example.DunbarHorizon.account.application.service;

import com.example.DunbarHorizon.account.application.dto.MyProfileResult;
import com.example.DunbarHorizon.account.application.dto.UserProfileInfo;
import com.example.DunbarHorizon.account.application.port.out.ProfileImageStoragePort;
import com.example.DunbarHorizon.account.domain.User;
import com.example.DunbarHorizon.account.domain.UserStatus;
import com.example.DunbarHorizon.account.domain.exception.UserNotFoundException;
import com.example.DunbarHorizon.account.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @InjectMocks private UserQueryService userQueryService;
    @Mock private UserRepository userRepository;
    @Mock private ProfileImageStoragePort profileImageStoragePort;

    @Test
    @DisplayName("ACTIVE 유저가 존재하면 UserProfileInfo를 반환한다")
    void findActiveUserByEmail_ActiveUser_ReturnsProfile() {
        // given
        User user = User.builder().email("test@test.com").nickname("tester").status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", 1L);
        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));

        // when
        Optional<UserProfileInfo> result = userQueryService.findActiveUserByEmail("test@test.com");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(1L);
        assertThat(result.get().nickname()).isEqualTo("tester");
    }

    @Test
    @DisplayName("비활성 유저는 이메일로 조회되지 않는다")
    void findActiveUserByEmail_InactiveUser_ReturnsEmpty() {
        // given
        User user = User.builder().email("test@test.com").nickname("tester").status(UserStatus.DORMANT).build();
        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));

        // when
        Optional<UserProfileInfo> result = userQueryService.findActiveUserByEmail("test@test.com");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("등록되지 않은 이메일이면 빈 Optional을 반환한다")
    void findActiveUserByEmail_NoUser_ReturnsEmpty() {
        // given
        given(userRepository.findByEmail("notfound@test.com")).willReturn(Optional.empty());

        // when
        Optional<UserProfileInfo> result = userQueryService.findActiveUserByEmail("notfound@test.com");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("유저 ID로 내 프로필 조회 시 email 포함 MyProfileResult를 반환한다")
    void getMyProfile_ActiveUser_ReturnsMyProfileResult() {
        // given
        User user = User.builder().email("me@test.com").nickname("나").status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", 1L);
        given(userRepository.findActivatedUser(1L)).willReturn(Optional.of(user));

        // when
        MyProfileResult result = userQueryService.getMyProfile(1L);

        // then
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo("me@test.com");
        assertThat(result.nickname()).isEqualTo("나");
    }

    @Test
    @DisplayName("유저가 존재하지 않으면 UserNotFoundException을 던진다")
    void getMyProfile_UserNotFound_ThrowsUserNotFoundException() {
        // given
        given(userRepository.findActivatedUser(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userQueryService.getMyProfile(99L))
                .isInstanceOf(UserNotFoundException.class);
    }
}
