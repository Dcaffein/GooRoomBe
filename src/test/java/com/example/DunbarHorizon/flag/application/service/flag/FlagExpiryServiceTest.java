package com.example.DunbarHorizon.flag.application.service.flag;

import com.example.DunbarHorizon.flag.application.port.out.FlagMaintenancePort;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FlagExpiryServiceTest {

    @InjectMocks private FlagExpiryService flagExpiryService;
    @Mock private FlagRepository flagRepository;
    @Mock private FlagMaintenancePort maintenancePort;

    @Test
    @DisplayName("만료 임계값이 현재 시각 기준 24시간 전으로 계산된다")
    void labelExpiredFlags_ThresholdIs24HoursBefore() {
        // given
        given(flagRepository.expireAllExceedingThreshold(any(), any())).willReturn(0);
        LocalDateTime before = LocalDateTime.now().minusHours(Flag.EXPIRATION_THRESHOLD_HOURS);

        // when
        flagExpiryService.expireEndedFlags();

        // then
        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(flagRepository).expireAllExceedingThreshold(thresholdCaptor.capture(), nowCaptor.capture());

        LocalDateTime threshold = thresholdCaptor.getValue();
        assertThat(threshold).isBeforeOrEqualTo(before.plusSeconds(1));
        assertThat(threshold).isAfterOrEqualTo(before.minusSeconds(1));

        LocalDateTime now = nowCaptor.getValue();
        assertThat(now).isAfterOrEqualTo(threshold);
        assertThat(now).isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(1));
    }

    @Test
    @DisplayName("만료된 플래그가 있으면 expireAllExceedingThreshold가 호출된다")
    void labelExpiredFlags_CallsRepository() {
        // given
        given(flagRepository.expireAllExceedingThreshold(any(), any())).willReturn(3);

        // when
        flagExpiryService.expireEndedFlags();

        // then
        verify(flagRepository).expireAllExceedingThreshold(any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("종료된 플래그의 초대를 정리한 뒤에 플래그를 소프트 삭제한다")
    void expireEndedFlags_PurgesInvitationsBeforeSoftDelete() {
        // given
        given(maintenancePort.purgeInvitationsOfEndedFlags(any())).willReturn(2);
        given(flagRepository.expireAllExceedingThreshold(any(), any())).willReturn(1);

        // when
        flagExpiryService.expireEndedFlags();

        // then
        // 순서가 뒤집히면 방금 소프트 삭제된 플래그가 초대 삭제 쿼리의 서브쿼리에서 빠진다.
        InOrder inOrder = inOrder(maintenancePort, flagRepository);
        inOrder.verify(maintenancePort).purgeInvitationsOfEndedFlags(any(LocalDateTime.class));
        inOrder.verify(flagRepository).expireAllExceedingThreshold(any(), any());
    }

    @Test
    @DisplayName("초대 정리와 플래그 만료가 동일한 임계값을 사용한다")
    void expireEndedFlags_SharesThreshold() {
        // given
        given(maintenancePort.purgeInvitationsOfEndedFlags(any())).willReturn(0);
        given(flagRepository.expireAllExceedingThreshold(any(), any())).willReturn(0);

        // when
        flagExpiryService.expireEndedFlags();

        // then
        ArgumentCaptor<LocalDateTime> invitationThreshold = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> flagThreshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(maintenancePort).purgeInvitationsOfEndedFlags(invitationThreshold.capture());
        verify(flagRepository).expireAllExceedingThreshold(flagThreshold.capture(), any());

        assertThat(invitationThreshold.getValue()).isEqualTo(flagThreshold.getValue());
    }
}
