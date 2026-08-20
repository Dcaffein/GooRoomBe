package com.example.DunbarHorizon.flag.application.eventListener;

import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagExpiryExemptionPolicy;
import com.example.DunbarHorizon.flag.domain.flag.FlagSchedule;
import com.example.DunbarHorizon.flag.domain.flag.FlagStatus;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagDeletedEvent;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlagDeletionEventListenerTest {

    @InjectMocks
    private FlagDeletionEventListener listener;

    @Mock private FlagRepository flagRepository;
    @Mock private FlagExpiryExemptionPolicy flagExpiryExemptionPolicy;
    @Mock private ApplicationEventPublisher eventPublisher;

    private static final Long FLAG_ID = 1L;
    private static final Long HOST_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.now();

    private FlagDeletedEvent recruitingEvent() {
        return new FlagDeletedEvent(FLAG_ID, HOST_ID, null, "테스트 플래그", FlagStatus.RECRUITING);
    }

    private FlagDeletedEvent endedEventWithParent(Long parentId) {
        return new FlagDeletedEvent(FLAG_ID, HOST_ID, parentId, "테스트 플래그", FlagStatus.ENDED);
    }

    @Test
    @DisplayName("soft delete된 Flag를 findById로 재조회하지 않는다")
    void handleFlagDeletion_doesNotFindDeletedFlag() {
        // given
        given(flagRepository.findByParentId(FLAG_ID)).willReturn(Optional.empty());
        given(flagRepository.findAllParticipantIds(FLAG_ID)).willReturn(List.of());

        // when
        listener.handleFlagDeletion(recruitingEvent());

        // then
        verify(flagRepository, never()).findById(FLAG_ID);
    }

    @Test
    @DisplayName("encore가 존재하면 parentId 연결을 끊는다")
    void handleFlagDeletion_sevensEncoreParentLink() {
        // given
        Flag encoreFlag = Flag.create(HOST_ID, "앵콜", "설명", 5,
                FlagSchedule.of(NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3)));
        ReflectionTestUtils.setField(encoreFlag, "parentId", FLAG_ID);

        given(flagRepository.findByParentId(FLAG_ID)).willReturn(Optional.of(encoreFlag));
        given(flagRepository.findAllParticipantIds(FLAG_ID)).willReturn(List.of());

        // when
        listener.handleFlagDeletion(recruitingEvent());

        // then
        assertThat(encoreFlag.getParentId()).isNull();
    }

    @Test
    @DisplayName("parentId가 있으면 부모 Flag의 보존 상태 재계산을 시도한다")
    void handleFlagDeletion_refreshesParentExemption() {
        // given
        Long parentId = 99L;
        FlagDeletedEvent event = endedEventWithParent(parentId);

        given(flagRepository.findByParentId(FLAG_ID)).willReturn(Optional.empty());
        given(flagRepository.findAllParticipantIds(FLAG_ID)).willReturn(List.of());

        // when
        listener.handleFlagDeletion(event);

        // then
        verify(flagExpiryExemptionPolicy).refresh(parentId);
    }
}
