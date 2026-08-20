package com.example.DunbarHorizon.flag.application.service.flag;

import com.example.DunbarHorizon.flag.application.port.out.FlagMaintenancePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FlagPurgeServiceTest {

    @InjectMocks private FlagPurgeService flagPurgeService;

    @Mock private FlagMaintenancePort maintenancePort;

    private static final int BUFFER_HOURS = 12;
    private static final int BATCH_SIZE = 5000;

    @Test
    @DisplayName("퍼지 대상이 있으면 그 목록으로 물리 삭제를 요청한다")
    void purgeExpiredFlags_WithTargets_DelegatesToPort() {
        // given
        List<Long> targets = List.of(1L, 2L, 3L);
        given(maintenancePort.findIdsReadyForHardDelete(any(), anyInt())).willReturn(targets);

        // when
        flagPurgeService.purgeExpiredFlags();

        // then
        verify(maintenancePort).purgeFlagsAndRelatedData(targets);
    }

    @Test
    @DisplayName("퍼지 대상이 없으면 물리 삭제를 요청하지 않는다")
    void purgeExpiredFlags_NoTargets_SkipsPurge() {
        // given
        given(maintenancePort.findIdsReadyForHardDelete(any(), anyInt())).willReturn(List.of());

        // when
        flagPurgeService.purgeExpiredFlags();

        // then
        verify(maintenancePort, never()).purgeFlagsAndRelatedData(any());
    }

    @Test
    @DisplayName("버퍼만큼 과거 시점과 배치 상한을 포트에 넘긴다")
    void purgeExpiredFlags_PassesBufferAndBatchSize() {
        // given
        LocalDateTime before = LocalDateTime.now().minusHours(BUFFER_HOURS);
        given(maintenancePort.findIdsReadyForHardDelete(any(), anyInt())).willReturn(List.of());

        // when
        flagPurgeService.purgeExpiredFlags();

        // then
        ArgumentCaptor<LocalDateTime> bufferTime = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(maintenancePort).findIdsReadyForHardDelete(bufferTime.capture(), eq(BATCH_SIZE));

        LocalDateTime after = LocalDateTime.now().minusHours(BUFFER_HOURS);
        assertThat(bufferTime.getValue())
                .isBetween(before, after);
    }
}
