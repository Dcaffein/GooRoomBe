package com.example.DunbarHorizon.flag.domain.flag;

import com.example.DunbarHorizon.flag.domain.flag.exception.FlagAuthorizationException;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagInvalidStatusException;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FlagEncoreFactoryTest {

    @InjectMocks private FlagEncoreFactory flagEncoreFactory;
    @Mock private FlagRepository flagRepository;

    private static final Long HOST_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final LocalDateTime NOW = LocalDateTime.now();

    private Flag endedParentFlag() {
        FlagSchedule schedule = FlagSchedule.of(NOW.minusHours(3), NOW.minusHours(2), NOW.minusHours(1));
        Flag flag = Flag.create(HOST_ID, "원본 플래그", "설명", 10, schedule);
        ReflectionTestUtils.setField(flag, "id", 1L);
        return flag;
    }

    @Test
    @DisplayName("호스트가 encore를 생성할 수 있다")
    void encore_ByHost_Success() {
        Flag parent = endedParentFlag();
        given(flagRepository.existsByParentId(parent.getId())).willReturn(false);

        assertThatNoException().isThrownBy(() ->
                flagEncoreFactory.encore(parent, HOST_ID, NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3)));
    }

    @Test
    @DisplayName("종료되지 않은 플래그에는 encore를 생성할 수 없다")
    void encore_ParentNotEnded_ThrowsException() {
        FlagSchedule schedule = FlagSchedule.of(NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3));
        Flag recruitingParent = Flag.create(HOST_ID, "원본 플래그", "설명", 10, schedule);
        ReflectionTestUtils.setField(recruitingParent, "id", 1L);

        assertThatThrownBy(() ->
                flagEncoreFactory.encore(recruitingParent, HOST_ID, NOW.plusHours(4), NOW.plusHours(5), NOW.plusHours(6)))
                .isInstanceOf(FlagInvalidStatusException.class);
    }

    @Test
    @DisplayName("호스트가 아닌 사용자가 encore를 생성하면 FlagAuthorizationException이 발생한다")
    void encore_ByNonHost_ThrowsException() {
        Flag parent = endedParentFlag();

        assertThatThrownBy(() ->
                flagEncoreFactory.encore(parent, OTHER_ID, NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3)))
                .isInstanceOf(FlagAuthorizationException.class);
    }

    @Test
    @DisplayName("이미 encore가 존재하는 플래그에 encore를 생성하면 FlagInvalidStatusException이 발생한다")
    void encore_AlreadyExists_ThrowsException() {
        Flag parent = endedParentFlag();
        given(flagRepository.existsByParentId(parent.getId())).willReturn(true);

        assertThatThrownBy(() ->
                flagEncoreFactory.encore(parent, HOST_ID, NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3)))
                .isInstanceOf(FlagInvalidStatusException.class);
    }
}
