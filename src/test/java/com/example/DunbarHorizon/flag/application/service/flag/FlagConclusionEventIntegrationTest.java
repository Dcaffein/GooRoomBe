package com.example.DunbarHorizon.flag.application.service.flag;

import com.example.DunbarHorizon.flag.application.port.in.FlagHostUseCase;
import com.example.DunbarHorizon.flag.application.port.in.FlagMemorialCommandUseCase;
import com.example.DunbarHorizon.flag.application.port.in.command.FlagHostCommand;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagExpiryExemptedEvent;
import com.example.DunbarHorizon.support.TestContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestContainerConfig.class, FlagConclusionEventIntegrationTest.RecordingConfig.class})
class FlagConclusionEventIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class RecordingConfig {
        @Bean
        ExemptionRecorder exemptionRecorder() {
            return new ExemptionRecorder();
        }
    }

    static class ExemptionRecorder {
        private final List<FlagExpiryExemptedEvent> received = new CopyOnWriteArrayList<>();

        @EventListener
        void on(FlagExpiryExemptedEvent event) {
            received.add(event);
        }

        List<FlagExpiryExemptedEvent> received() {
            return received;
        }

        void clear() {
            received.clear();
        }
    }

    @Autowired private FlagHostUseCase flagHostUseCase;
    @Autowired private FlagMemorialCommandUseCase memorialCommandUseCase;
    @Autowired private ExemptionRecorder recorder;

    private static final Long HOST_ID = 4201L;

    private Long endedFlagId;

    @BeforeEach
    void setUp() {
        recorder.clear();
        LocalDateTime base = LocalDateTime.now().withNano(0);
        endedFlagId = flagHostUseCase.hostFlag(new FlagHostCommand(
                HOST_ID, "종료된 모임", "설명", 10,
                base.minusHours(4), base.minusHours(3), base.minusHours(1)));
    }

    @Test
    @DisplayName("후기가 달려 만료 면제가 붙으면 이벤트가 발행된다")
    void memorialGrantsExemption_publishesEvent() {
        // when
        memorialCommandUseCase.createMemorial(endedFlagId, HOST_ID, "좋았습니다");

        // then
        assertThat(recorder.received()).hasSize(1);
        assertThat(recorder.received().get(0).flagId()).isEqualTo(endedFlagId);
        assertThat(recorder.received().get(0).hostId()).isEqualTo(HOST_ID);
        assertThat(recorder.received().get(0).parentId()).isNull();
    }

    @Test
    @DisplayName("이미 면제 상태면 후기가 더 달려도 발행되지 않는다")
    void secondMemorial_publishesNothingMore() {
        // given
        memorialCommandUseCase.createMemorial(endedFlagId, HOST_ID, "첫 후기");
        recorder.clear();

        // when
        memorialCommandUseCase.createMemorial(endedFlagId, HOST_ID, "두 번째 후기");

        // then
        assertThat(recorder.received()).isEmpty();
    }
}
