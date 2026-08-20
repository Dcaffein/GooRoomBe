package com.example.DunbarHorizon.flag.domain.flag;

import com.example.DunbarHorizon.flag.domain.flag.exception.FlagNotFoundException;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.flag.domain.memorial.repository.FlagMemorialRepository;
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

@ExtendWith(MockitoExtension.class)
class FlagExpiryExemptionPolicyTest {

    @InjectMocks
    private FlagExpiryExemptionPolicy flagExpiryExemptionPolicy;

    @Mock
    private FlagRepository flagRepository;

    @Mock
    private FlagMemorialRepository memorialRepository;

    private static final Long FLAG_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.now();

    private Flag createFlag() {
        Flag flag = Flag.create(1L, "테스트 플래그", "설명", 10,
                FlagSchedule.of(NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3)));
        ReflectionTestUtils.setField(flag, "id", FLAG_ID);
        return flag;
    }

    @Test
    @DisplayName("memorial이 존재하면 autoExpiryExempt가 true로 설정된다")
    void refresh_memorialExists_setTrue() {
        // given
        Flag flag = createFlag();
        given(flagRepository.findById(FLAG_ID)).willReturn(Optional.of(flag));
        given(memorialRepository.existsByFlagId(FLAG_ID)).willReturn(true);

        // when
        flagExpiryExemptionPolicy.refresh(FLAG_ID);

        // then
        assertThat(flag.isAutoExpiryExempt()).isTrue();
    }

    @Test
    @DisplayName("encore가 존재하면 autoExpiryExempt가 true로 설정된다")
    void refresh_encoreExists_setTrue() {
        // given
        Flag flag = createFlag();
        given(flagRepository.findById(FLAG_ID)).willReturn(Optional.of(flag));
        given(memorialRepository.existsByFlagId(FLAG_ID)).willReturn(false);
        given(flagRepository.existsByParentId(FLAG_ID)).willReturn(true);

        // when
        flagExpiryExemptionPolicy.refresh(FLAG_ID);

        // then
        assertThat(flag.isAutoExpiryExempt()).isTrue();
    }

    @Test
    @DisplayName("memorial도 encore도 없으면 autoExpiryExempt가 false로 설정된다")
    void refresh_neitherExists_setFalse() {
        // given
        Flag flag = createFlag();
        ReflectionTestUtils.setField(flag, "autoExpiryExempt", true);
        given(flagRepository.findById(FLAG_ID)).willReturn(Optional.of(flag));
        given(memorialRepository.existsByFlagId(FLAG_ID)).willReturn(false);
        given(flagRepository.existsByParentId(FLAG_ID)).willReturn(false);

        // when
        flagExpiryExemptionPolicy.refresh(FLAG_ID);

        // then
        assertThat(flag.isAutoExpiryExempt()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 flagId로 refresh 호출 시 예외가 발생한다")
    void refresh_flagNotFound_throwsException() {
        // given
        given(flagRepository.findById(FLAG_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> flagExpiryExemptionPolicy.refresh(FLAG_ID))
                .isInstanceOf(FlagNotFoundException.class);
    }
}
