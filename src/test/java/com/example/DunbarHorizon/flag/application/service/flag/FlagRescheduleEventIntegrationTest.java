package com.example.DunbarHorizon.flag.application.service.flag;

import com.example.DunbarHorizon.flag.application.port.in.FlagHostUseCase;
import com.example.DunbarHorizon.flag.application.port.in.FlagModificationUseCase;
import com.example.DunbarHorizon.flag.application.port.in.command.FlagHostCommand;
import com.example.DunbarHorizon.flag.application.port.in.command.FlagScheduleUpdateCommand;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagMeetingChangedEvent;
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

/**
 * 이 검증만 컨텍스트를 띄우는 이유는 목 리포지토리가 도메인 이벤트를 발행하지 않기 때문이다.
 * 단위 테스트는 이벤트가 등록된 것까지만 볼 수 있어 발행 유실을 잡지 못한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestContainerConfig.class, FlagRescheduleEventIntegrationTest.RecordingConfig.class})
class FlagRescheduleEventIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class RecordingConfig {
        @Bean
        MeetingChangedRecorder meetingChangedRecorder() {
            return new MeetingChangedRecorder();
        }
    }

    static class MeetingChangedRecorder {
        private final List<FlagMeetingChangedEvent> received = new CopyOnWriteArrayList<>();

        @EventListener
        void on(FlagMeetingChangedEvent event) {
            received.add(event);
        }

        List<FlagMeetingChangedEvent> received() {
            return received;
        }

        void clear() {
            received.clear();
        }
    }

    @Autowired private FlagHostUseCase flagHostUseCase;
    @Autowired private FlagModificationUseCase flagModificationUseCase;
    @Autowired private MeetingChangedRecorder recorder;

    private static final Long HOST_ID = 4103L;
    private static final String TITLE = "일정 변경 테스트";

    private LocalDateTime base;
    private Long flagId;

    @BeforeEach
    void setUp() {
        recorder.clear();
        base = LocalDateTime.now().withNano(0);
        flagId = flagHostUseCase.hostFlag(new FlagHostCommand(
                HOST_ID, TITLE, "설명", 10,
                base.plusHours(2), base.plusHours(3), base.plusHours(4)));
    }

    @Test
    @DisplayName("모임 시간을 바꾸면 FlagMeetingChangedEvent가 발행된다")
    void reschedule_publishesMeetingChangedEvent() {
        // given
        LocalDateTime newStart = base.plusHours(5);
        LocalDateTime newEnd = base.plusHours(6);

        // when
        flagModificationUseCase.reschedule(new FlagScheduleUpdateCommand(
                flagId, HOST_ID, base.plusHours(4), newStart, newEnd));

        // then
        assertThat(recorder.received()).hasSize(1);
        FlagMeetingChangedEvent event = recorder.received().get(0);
        assertThat(event.flagId()).isEqualTo(flagId);
        assertThat(event.flagTitle()).isEqualTo(TITLE);
        assertThat(event.startDateTime()).isEqualTo(newStart);
        assertThat(event.endDateTime()).isEqualTo(newEnd);
    }

    @Test
    @DisplayName("모임 시간이 그대로면 FlagMeetingChangedEvent가 발행되지 않는다")
    void reschedule_sameMeetingTime_publishesNothing() {
        // when — 마감만 앞당기고 시작·종료는 그대로 둔다
        flagModificationUseCase.reschedule(new FlagScheduleUpdateCommand(
                flagId, HOST_ID, base.plusHours(1), base.plusHours(3), base.plusHours(4)));

        // then
        assertThat(recorder.received()).isEmpty();
    }
}
