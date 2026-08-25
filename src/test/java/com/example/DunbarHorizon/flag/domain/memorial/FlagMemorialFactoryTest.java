package com.example.DunbarHorizon.flag.domain.memorial;

import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagSchedule;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagInvalidStatusException;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.flag.domain.memorial.exception.FlagMemorialAuthorizationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FlagMemorialFactoryTest {

    @InjectMocks private FlagMemorialFactory flagMemorialFactory;
    @Mock private FlagRepository flagRepository;

    private static final Long FLAG_ID = 1L;
    private static final Long HOST_ID = 10L;
    private static final Long PARTICIPANT_ID = 20L;
    private static final Long STRANGER_ID = 99L;
    private static final LocalDateTime NOW = LocalDateTime.now();

    private Flag flagWith(FlagSchedule schedule) {
        Flag flag = Flag.create(HOST_ID, "플래그", "설명", 10, schedule);
        ReflectionTestUtils.setField(flag, "id", FLAG_ID);
        return flag;
    }

    private Flag endedFlag() {
        return flagWith(FlagSchedule.of(NOW.minusHours(3), NOW.minusHours(2), NOW.minusHours(1)));
    }

    @Test
    @DisplayName("종료된 플래그에는 호스트가 후기를 작성할 수 있다")
    void create_ByHostOnEndedFlag_Success() {
        assertThatNoException().isThrownBy(() ->
                flagMemorialFactory.create(endedFlag(), HOST_ID, "좋았습니다"));
    }

    @Test
    @DisplayName("모집 중인 플래그에는 후기를 작성할 수 없다")
    void create_OnRecruitingFlag_ThrowsException() {
        Flag recruiting = flagWith(FlagSchedule.of(NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3)));

        assertThatThrownBy(() -> flagMemorialFactory.create(recruiting, HOST_ID, "좋았습니다"))
                .isInstanceOf(FlagInvalidStatusException.class);
    }

    @Test
    @DisplayName("진행 중인 플래그에는 후기를 작성할 수 없다")
    void create_OnInActivityFlag_ThrowsException() {
        Flag inActivity = flagWith(FlagSchedule.of(NOW.minusHours(2), NOW.minusHours(1), NOW.plusHours(1)));

        assertThatThrownBy(() -> flagMemorialFactory.create(inActivity, HOST_ID, "좋았습니다"))
                .isInstanceOf(FlagInvalidStatusException.class);
    }

    @Test
    @DisplayName("참여자는 후기를 작성할 수 있다")
    void create_ByParticipant_Success() {
        given(flagRepository.isParticipating(FLAG_ID, PARTICIPANT_ID)).willReturn(true);

        assertThatNoException().isThrownBy(() ->
                flagMemorialFactory.create(endedFlag(), PARTICIPANT_ID, "좋았습니다"));
    }

    @Test
    @DisplayName("참여하지 않은 사용자는 후기를 작성할 수 없다")
    void create_ByStranger_ThrowsException() {
        given(flagRepository.isParticipating(FLAG_ID, STRANGER_ID)).willReturn(false);

        assertThatThrownBy(() -> flagMemorialFactory.create(endedFlag(), STRANGER_ID, "좋았습니다"))
                .isInstanceOf(FlagMemorialAuthorizationException.class);
    }
}
