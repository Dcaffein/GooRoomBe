package com.example.DunbarHorizon.flag.domain.flag;

import com.example.DunbarHorizon.flag.domain.flag.exception.FlagAuthorizationException;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagNotFoundException;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagParticipantNotFoundException;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagParticipationDuplicateException;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class FlagParticipationManagerTest {

    @InjectMocks private FlagParticipationManager policy;

    @Mock private FlagRepository flagRepository;
    @Mock private FriendshipChecker friendshipChecker;

    private static final Long FLAG_ID = 1L;
    private static final Long HOST_ID = 1L;
    private static final Long USER_ID = 2L;
    private static final LocalDateTime NOW = LocalDateTime.now();

    private Flag recruitingFlag() {
        FlagSchedule schedule = FlagSchedule.of(NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4));
        Flag flag = Flag.create(HOST_ID, "테스트 플래그", "설명", 10, schedule);
        ReflectionTestUtils.setField(flag, "id", FLAG_ID);
        return flag;
    }

    @Test
    @DisplayName("친구인 사용자가 정상적으로 플래그에 참여할 수 있다")
    void participate_Success() {
        // given
        Flag flag = recruitingFlag();
        given(flagRepository.findHostIdById(FLAG_ID)).willReturn(Optional.of(HOST_ID));
        given(friendshipChecker.areFriends(HOST_ID, USER_ID)).willReturn(true);
        given(flagRepository.findByIdForUpdate(FLAG_ID)).willReturn(Optional.of(flag));
        given(flagRepository.isParticipating(FLAG_ID, USER_ID)).willReturn(false);
        given(flagRepository.countParticipants(FLAG_ID)).willReturn(0);

        // when
        FlagParticipant result = policy.participate(FLAG_ID, USER_ID);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getParticipantId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("존재하지 않는 플래그에 참여하면 FlagNotFoundException이 발생한다")
    void participate_FlagNotFound_ThrowsException() {
        // given
        given(flagRepository.findHostIdById(999L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> policy.participate(999L, USER_ID))
                .isInstanceOf(FlagNotFoundException.class);
    }

    @Test
    @DisplayName("호스트와 친구가 아닌 사용자가 참여하면 FlagAuthorizationException이 발생한다")
    void participate_NotFriend_ThrowsException() {
        // given
        given(flagRepository.findHostIdById(FLAG_ID)).willReturn(Optional.of(HOST_ID));
        given(friendshipChecker.areFriends(HOST_ID, USER_ID)).willReturn(false);

        // when / then
        assertThatThrownBy(() -> policy.participate(FLAG_ID, USER_ID))
                .isInstanceOf(FlagAuthorizationException.class);
    }

    @Test
    @DisplayName("이미 참여 중인 사용자가 다시 참여하면 FlagParticipationDuplicateException이 발생한다")
    void participate_Duplicate_ThrowsException() {
        // given
        Flag flag = recruitingFlag();
        given(flagRepository.findHostIdById(FLAG_ID)).willReturn(Optional.of(HOST_ID));
        given(friendshipChecker.areFriends(HOST_ID, USER_ID)).willReturn(true);
        given(flagRepository.findByIdForUpdate(FLAG_ID)).willReturn(Optional.of(flag));
        given(flagRepository.isParticipating(FLAG_ID, USER_ID)).willReturn(true);

        // when / then
        assertThatThrownBy(() -> policy.participate(FLAG_ID, USER_ID))
                .isInstanceOf(FlagParticipationDuplicateException.class);
    }

    @Test
    @DisplayName("참여자가 정상적으로 플래그를 탈퇴할 수 있다")
    void unparticipate_Success() {
        // given
        Flag flag = recruitingFlag();
        FlagParticipant participant = mock(FlagParticipant.class);
        given(participant.getParticipantId()).willReturn(USER_ID);

        given(flagRepository.findById(FLAG_ID)).willReturn(Optional.of(flag));
        given(flagRepository.findParticipant(FLAG_ID, USER_ID))
                .willReturn(Optional.of(participant));

        // when / then
        assertThatNoException().isThrownBy(() -> policy.unparticipate(FLAG_ID, USER_ID));
    }

    @Test
    @DisplayName("참여하지 않은 사용자가 탈퇴하면 FlagParticipantNotFoundException이 발생한다")
    void unparticipate_ParticipantNotFound_ThrowsException() {
        // given
        Flag flag = recruitingFlag();
        given(flagRepository.findById(FLAG_ID)).willReturn(Optional.of(flag));
        given(flagRepository.findParticipant(FLAG_ID, USER_ID))
                .willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> policy.unparticipate(FLAG_ID, USER_ID))
                .isInstanceOf(FlagParticipantNotFoundException.class);
    }
}
