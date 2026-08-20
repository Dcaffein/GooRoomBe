package com.example.DunbarHorizon.flag.application.service.flag;

import com.example.DunbarHorizon.flag.application.port.in.command.FlagCapacityUpdateCommand;
import com.example.DunbarHorizon.flag.application.port.in.command.FlagDetailsUpdateCommand;
import com.example.DunbarHorizon.flag.application.port.in.command.FlagScheduleUpdateCommand;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagSchedule;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagAuthorizationException;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagNotFoundException;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagInvalidStatusException;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagScheduleInvalidException;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FlagManagementServiceTest {

    @InjectMocks private FlagManagementService flagManagementService;

    @Mock private FlagRepository flagRepository;

    private static final Long HOST_ID = 1L;
    private static final Long OTHER_ID = 99L;
    private static final LocalDateTime NOW = LocalDateTime.now();

    private Flag recruitingFlag() {
        FlagSchedule schedule = FlagSchedule.of(NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4));
        Flag flag = Flag.create(HOST_ID, "원래 제목", "원래 설명", 10, schedule);
        ReflectionTestUtils.setField(flag, "id", 1L);
        return flag;
    }

    @Test
    @DisplayName("호스트가 플래그 상세정보를 수정할 수 있다")
    void modifyFlagDetails_ByHost_Success() {
        // given
        Flag flag = recruitingFlag();
        given(flagRepository.findById(1L)).willReturn(Optional.of(flag));
        FlagDetailsUpdateCommand command = new FlagDetailsUpdateCommand(1L, HOST_ID, "새 제목", "새 설명");

        // when
        flagManagementService.modifyFlagDetails(command);

        // then
        assertThat(flag.getTitle()).isEqualTo("새 제목");
        assertThat(flag.getDescription()).isEqualTo("새 설명");
    }

    @Test
    @DisplayName("호스트가 아닌 사용자가 플래그 상세정보를 수정하면 FlagAuthorizationException이 발생한다")
    void modifyFlagDetails_ByNonHost_ThrowsException() {
        // given
        Flag flag = recruitingFlag();
        given(flagRepository.findById(1L)).willReturn(Optional.of(flag));
        FlagDetailsUpdateCommand command = new FlagDetailsUpdateCommand(1L, OTHER_ID, "새 제목", "새 설명");

        // when / then
        assertThatThrownBy(() -> flagManagementService.modifyFlagDetails(command))
                .isInstanceOf(FlagAuthorizationException.class);
    }

    @Test
    @DisplayName("존재하지 않는 플래그를 수정하면 FlagNotFoundException이 발생한다")
    void modifyFlagDetails_FlagNotFound_ThrowsException() {
        // given
        given(flagRepository.findById(999L)).willReturn(Optional.empty());
        FlagDetailsUpdateCommand command = new FlagDetailsUpdateCommand(999L, HOST_ID, "제목", "설명");

        // when / then
        assertThatThrownBy(() -> flagManagementService.modifyFlagDetails(command))
                .isInstanceOf(FlagNotFoundException.class);
    }

    @Test
    @DisplayName("호스트가 현재 참여 인원 이상으로 정원을 변경할 수 있다")
    void modifyFlagCapacity_ByHost_Success() {
        // given
        Flag flag = recruitingFlag();
        given(flagRepository.countParticipants(1L)).willReturn(3);
        given(flagRepository.findByIdForUpdate(1L)).willReturn(Optional.of(flag));
        FlagCapacityUpdateCommand command = new FlagCapacityUpdateCommand(1L, HOST_ID, 5);

        // when
        flagManagementService.modifyFlagCapacity(command);

        // then
        assertThat(flag.getCapacity()).isEqualTo(5);
    }

    @Test
    @DisplayName("정원 변경은 Flag를 잠근 뒤에 참여자를 센다")
    void modifyFlagCapacity_LocksBeforeCounting() {
        // given — 잠금 밖에서 세면 그 사이 참여가 끼어들어 정원보다 참여자가 많아질 수 있다
        Flag flag = recruitingFlag();
        given(flagRepository.findByIdForUpdate(1L)).willReturn(Optional.of(flag));
        given(flagRepository.countParticipants(1L)).willReturn(3);
        FlagCapacityUpdateCommand command = new FlagCapacityUpdateCommand(1L, HOST_ID, 5);

        // when
        flagManagementService.modifyFlagCapacity(command);

        // then
        InOrder inOrder = inOrder(flagRepository);
        inOrder.verify(flagRepository).findByIdForUpdate(1L);
        inOrder.verify(flagRepository).countParticipants(1L);
    }

    @Test
    @DisplayName("현재 참여 인원보다 적은 정원으로 변경하면 FlagInvalidStatusException이 발생한다")
    void modifyFlagCapacity_BelowCurrentCount_ThrowsException() {
        // given
        Flag flag = recruitingFlag();
        given(flagRepository.countParticipants(1L)).willReturn(5);
        given(flagRepository.findByIdForUpdate(1L)).willReturn(Optional.of(flag));
        FlagCapacityUpdateCommand command = new FlagCapacityUpdateCommand(1L, HOST_ID, 3);

        // when / then
        assertThatThrownBy(() -> flagManagementService.modifyFlagCapacity(command))
                .isInstanceOf(FlagInvalidStatusException.class);
    }

    @Test
    @DisplayName("유효하지 않은 일정(종료 < 시작)으로 일정 변경하면 FlagScheduleInvalidException이 발생한다")
    void reschedule_InvalidSchedule_ThrowsException() {
        // given: end before start
        FlagScheduleUpdateCommand command = new FlagScheduleUpdateCommand(
                1L, HOST_ID, null, NOW.plusHours(4), NOW.plusHours(3)
        );

        // when / then — FlagSchedule.of() throws before flag lookup
        assertThatThrownBy(() -> flagManagementService.reschedule(command))
                .isInstanceOf(FlagScheduleInvalidException.class);
    }

    @Test
    @DisplayName("호스트가 정상 일정으로 일정을 변경할 수 있다")
    void reschedule_Success() {
        // given
        Flag flag = recruitingFlag();
        given(flagRepository.findById(1L)).willReturn(Optional.of(flag));

        LocalDateTime newStart = NOW.plusHours(5);
        LocalDateTime newEnd = NOW.plusHours(6);
        FlagScheduleUpdateCommand command = new FlagScheduleUpdateCommand(1L, HOST_ID, NOW.plusHours(4), newStart, newEnd);

        // when
        flagManagementService.reschedule(command);

        // then
        assertThat(flag.getSchedule().getStartDateTime()).isEqualTo(newStart);
        assertThat(flag.getSchedule().getEndDateTime()).isEqualTo(newEnd);
    }

    @Test
    @DisplayName("호스트가 플래그를 삭제(closeFlag)하면 softDelete된다")
    void closeFlag_ByHost_Success() {
        // given
        Flag flag = recruitingFlag();
        given(flagRepository.findById(1L)).willReturn(Optional.of(flag));
        given(flagRepository.save(any(Flag.class))).willReturn(flag);

        // when
        flagManagementService.closeFlag(1L, HOST_ID);

        // then
        assertThat(flag.isDeleted()).isTrue();
        verify(flagRepository).save(flag);
    }

    @Test
    @DisplayName("호스트가 아닌 사용자가 플래그를 삭제하면 FlagAuthorizationException이 발생한다")
    void closeFlag_ByNonHost_ThrowsException() {
        // given
        Flag flag = recruitingFlag();
        given(flagRepository.findById(1L)).willReturn(Optional.of(flag));

        // when / then
        assertThatThrownBy(() -> flagManagementService.closeFlag(1L, OTHER_ID))
                .isInstanceOf(FlagAuthorizationException.class);
    }
}
