package com.example.DunbarHorizon.flag.application.service.flag;

import com.example.DunbarHorizon.flag.application.dto.info.FlagUserInfo;
import com.example.DunbarHorizon.flag.application.dto.result.FlagDetailResult;
import com.example.DunbarHorizon.flag.application.dto.result.FlagResult;
import com.example.DunbarHorizon.flag.application.port.in.FlagRole;
import com.example.DunbarHorizon.flag.application.port.out.FlagUserPort;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagParticipant;
import com.example.DunbarHorizon.flag.domain.flag.FlagSchedule;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagNotFoundException;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FlagQueryServiceTest {

    @InjectMocks private FlagQueryService flagQueryService;

    @Mock private FlagRepository flagRepository;
    @Mock private FlagUserPort flagUserPort;

    private static final Long HOST_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.now();

    private Flag createRecruitingFlag(Long id) {
        FlagSchedule schedule = FlagSchedule.of(NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4));
        Flag flag = Flag.create(HOST_ID, "테스트 플래그", "설명", 10, schedule);
        ReflectionTestUtils.setField(flag, "id", id);
        return flag;
    }

    private Flag createEncoreFlag(Long id, Long parentId) {
        Flag flag = createRecruitingFlag(id);
        ReflectionTestUtils.setField(flag, "parentId", parentId);
        return flag;
    }

    private FlagUserInfo userInfo(Long userId) {
        return new FlagUserInfo(userId, "사용자" + userId, null);
    }

    // ===== 최근 Flag 조회 =====

    @Test
    @DisplayName("최근 Flag 조회 시 host + 참여 Flag 합산 결과가 반환된다")
    void getRecentFlags_ReturnsCombinedFlags() {
        Long participantUserId = 2L;
        Flag hostedFlag = createRecruitingFlag(1L);
        Flag participatedFlag = createRecruitingFlag(2L);
        ReflectionTestUtils.setField(participatedFlag, "hostId", participantUserId);

        given(flagRepository.findRecentByUserId(HOST_ID, 5)).willReturn(List.of(hostedFlag, participatedFlag));
        given(flagRepository.countParticipantsByFlagIds(anyCollection())).willReturn(Map.of(1L, 2, 2L, 1));
        given(flagUserPort.findUserInfosByIds(anySet()))
                .willReturn(Map.of(HOST_ID, userInfo(HOST_ID), participantUserId, userInfo(participantUserId)));

        List<FlagResult> result = flagQueryService.getRecentFlags(HOST_ID);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("최근 Flag가 없으면 빈 리스트가 반환된다")
    void getRecentFlags_NoFlags_ReturnsEmpty() {
        given(flagRepository.findRecentByUserId(HOST_ID, 5)).willReturn(List.of());

        List<FlagResult> result = flagQueryService.getRecentFlags(HOST_ID);

        assertThat(result).isEmpty();
    }

    // ===== 친구 Flag 조회 =====

    @Test
    @DisplayName("친구 Flag 조회 시 마감이 남은 친구들의 플래그가 반환된다")
    void getFriendFlags_ReturnsFriendFlagsWithRemainingDeadline() {
        Long friendId = 2L;
        Set<Long> friendIds = Set.of(friendId, 3L);
        Flag friendFlag = createRecruitingFlag(1L);
        ReflectionTestUtils.setField(friendFlag, "hostId", friendId);

        given(flagUserPort.getRelatedUserIds(HOST_ID)).willReturn(friendIds);
        given(flagRepository.findByHostIdsAndDeadlineAfter(eq(friendIds), any(LocalDateTime.class)))
                .willReturn(List.of(friendFlag));
        given(flagRepository.countParticipantsByFlagIds(anyCollection())).willReturn(Map.of(1L, 4));
        // 친구 전원이 아니라 실제 호스트만 조회한다
        given(flagUserPort.findUserInfosByIds(Set.of(friendId))).willReturn(Map.of(friendId, userInfo(friendId)));

        List<FlagResult> result = flagQueryService.getFriendFlags(HOST_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).participantCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("친구가 없으면 조회 쿼리를 실행하지 않고 빈 리스트가 반환된다")
    void getFriendFlags_NoFriends_ReturnsEmptyWithoutQuery() {
        given(flagUserPort.getRelatedUserIds(HOST_ID)).willReturn(Set.of());

        List<FlagResult> result = flagQueryService.getFriendFlags(HOST_ID);

        assertThat(result).isEmpty();
        verify(flagRepository, never()).findByHostIdsAndDeadlineAfter(anySet(), any(LocalDateTime.class));
    }

    // ===== 목록 조회 =====

    @Test
    @DisplayName("HOST 역할로 조회하면 호스팅 중인 플래그 목록이 반환된다")
    void getFlagsByRole_Host_ReturnsManagedFlags() {
        Flag flag = createRecruitingFlag(1L);
        given(flagRepository.findAllByHostId(HOST_ID)).willReturn(List.of(flag));
        given(flagRepository.countParticipantsByFlagIds(anyCollection())).willReturn(Map.of(1L, 3));
        given(flagUserPort.findUserInfosByIds(Set.of(HOST_ID))).willReturn(Map.of(HOST_ID, userInfo(HOST_ID)));

        List<FlagResult> result = flagQueryService.getFlagsByRole(HOST_ID, FlagRole.HOST);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("테스트 플래그");
        assertThat(result.get(0).participantCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("PARTICIPANT 역할로 조회하면 참여 중인 플래그 목록이 반환된다")
    void getFlagsByRole_Participant_ReturnsParticipatingFlags() {
        Flag flag = createRecruitingFlag(1L);
        given(flagRepository.findFlagIdsByParticipantId(HOST_ID)).willReturn(List.of(1L));
        given(flagRepository.findAllByIdIn(List.of(1L))).willReturn(List.of(flag));
        given(flagRepository.countParticipantsByFlagIds(anyCollection())).willReturn(Map.of(1L, 1));
        given(flagUserPort.findUserInfosByIds(anySet())).willReturn(Map.of(HOST_ID, userInfo(HOST_ID)));

        List<FlagResult> result = flagQueryService.getFlagsByRole(HOST_ID, FlagRole.PARTICIPANT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).participantCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("참여 플래그가 없으면 빈 리스트가 반환된다")
    void getFlagsByRole_ParticipantNoFlags_ReturnsEmpty() {
        given(flagRepository.findFlagIdsByParticipantId(HOST_ID)).willReturn(List.of());

        List<FlagResult> result = flagQueryService.getFlagsByRole(HOST_ID, FlagRole.PARTICIPANT);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("참여자가 없는 플래그는 participantCount가 0이다")
    void getFlagsByRole_NoParticipants_CountIsZero() {
        Flag flag = createRecruitingFlag(1L);
        given(flagRepository.findAllByHostId(HOST_ID)).willReturn(List.of(flag));
        given(flagRepository.countParticipantsByFlagIds(anyCollection())).willReturn(Map.of());
        given(flagUserPort.findUserInfosByIds(anySet())).willReturn(Map.of(HOST_ID, userInfo(HOST_ID)));

        List<FlagResult> result = flagQueryService.getFlagsByRole(HOST_ID, FlagRole.HOST);

        assertThat(result.get(0).participantCount()).isEqualTo(0);
    }

    // ===== 상세 조회 =====

    @Test
    @DisplayName("존재하지 않는 플래그를 상세 조회하면 FlagNotFoundException이 발생한다")
    void getFlagDetail_NotFound_ThrowsException() {
        given(flagRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> flagQueryService.getFlagDetail(999L, HOST_ID))
                .isInstanceOf(FlagNotFoundException.class);
    }

    @Test
    @DisplayName("참여자가 없는 플래그 상세 조회 시 빈 participants와 null parentFlag가 반환된다")
    void getFlagDetail_NoParticipantsNoParent_ReturnsEmptyParticipantsAndNullParent() {
        Flag flag = createRecruitingFlag(1L);
        given(flagRepository.findById(1L)).willReturn(Optional.of(flag));
        given(flagRepository.findAllParticipants(1L)).willReturn(List.of());
        given(flagUserPort.findUserInfosByIds(anySet())).willReturn(Map.of(HOST_ID, userInfo(HOST_ID)));

        FlagDetailResult result = flagQueryService.getFlagDetail(1L, HOST_ID);

        assertThat(result.participants()).isEmpty();
        assertThat(result.participantCount()).isEqualTo(0);
        assertThat(result.parentFlag()).isNull();
        assertThat(result.parentFlagId()).isNull();
    }

    @Test
    @DisplayName("참여자가 있는 플래그 상세 조회 시 participants 목록과 participantCount가 반환된다")
    void getFlagDetail_WithParticipants_ReturnsParticipantList() {
        Long participantId = 2L;
        Flag flag = createRecruitingFlag(1L);

        FlagParticipant mockParticipant = mock(FlagParticipant.class);
        given(mockParticipant.getParticipantId()).willReturn(participantId);
        given(mockParticipant.isCanInvite()).willReturn(true);

        given(flagRepository.findById(1L)).willReturn(Optional.of(flag));
        given(flagRepository.findAllParticipants(1L)).willReturn(List.of(mockParticipant));
        given(flagUserPort.findUserInfosByIds(anySet()))
                .willReturn(Map.of(HOST_ID, userInfo(HOST_ID), participantId, userInfo(participantId)));

        FlagDetailResult result = flagQueryService.getFlagDetail(1L, HOST_ID);

        assertThat(result.participants()).hasSize(1);
        assertThat(result.participantCount()).isEqualTo(1);
        assertThat(result.participants().get(0).id()).isEqualTo(participantId);
        assertThat(result.participants().get(0).nickname()).isEqualTo("사용자2");
        assertThat(result.participants().get(0).canInvite()).isTrue();
    }

    @Test
    @DisplayName("Encore 플래그 상세 조회 시 parentFlag 정보가 반환된다")
    void getFlagDetail_EncoreFlag_ReturnsParentFlagInfo() {
        Flag parentFlag = createRecruitingFlag(10L);
        Flag encoreFlag = createEncoreFlag(1L, 10L);

        given(flagRepository.findById(1L)).willReturn(Optional.of(encoreFlag));
        given(flagRepository.findAllParticipants(1L)).willReturn(List.of());
        given(flagRepository.findById(10L)).willReturn(Optional.of(parentFlag));
        given(flagUserPort.findUserInfosByIds(anySet())).willReturn(Map.of(HOST_ID, userInfo(HOST_ID)));

        FlagDetailResult result = flagQueryService.getFlagDetail(1L, HOST_ID);

        assertThat(result.parentFlagId()).isEqualTo(10L);
        assertThat(result.parentFlag()).isNotNull();
        assertThat(result.parentFlag().id()).isEqualTo(10L);
        assertThat(result.parentFlag().title()).isEqualTo("테스트 플래그");
    }

    @Test
    @DisplayName("Encore 플래그인데 parentFlag가 삭제된 경우 parentFlag는 null이다")
    void getFlagDetail_EncoreFlagParentDeleted_ReturnsNullParentFlag() {
        Flag encoreFlag = createEncoreFlag(1L, 10L);

        given(flagRepository.findById(1L)).willReturn(Optional.of(encoreFlag));
        given(flagRepository.findAllParticipants(1L)).willReturn(List.of());
        given(flagRepository.findById(10L)).willReturn(Optional.empty());
        given(flagUserPort.findUserInfosByIds(anySet())).willReturn(Map.of(HOST_ID, userInfo(HOST_ID)));

        FlagDetailResult result = flagQueryService.getFlagDetail(1L, HOST_ID);

        assertThat(result.parentFlagId()).isEqualTo(10L);
        assertThat(result.parentFlag()).isNull();
    }

    @Test
    @DisplayName("viewerId가 hostId와 같으면 isHost가 true이다")
    void getFlagDetail_ViewerIsHost_IsHostTrue() {
        Flag flag = createRecruitingFlag(1L);
        given(flagRepository.findById(1L)).willReturn(Optional.of(flag));
        given(flagRepository.findAllParticipants(1L)).willReturn(List.of());
        given(flagUserPort.findUserInfosByIds(anySet())).willReturn(Map.of(HOST_ID, userInfo(HOST_ID)));

        FlagDetailResult result = flagQueryService.getFlagDetail(1L, HOST_ID);

        assertThat(result.isHost()).isTrue();
    }

    @Test
    @DisplayName("viewerId가 hostId와 다르면 isHost가 false이다")
    void getFlagDetail_ViewerIsNotHost_IsHostFalse() {
        Long viewerId = 99L;
        Flag flag = createRecruitingFlag(1L);
        given(flagRepository.findById(1L)).willReturn(Optional.of(flag));
        given(flagRepository.findAllParticipants(1L)).willReturn(List.of());
        given(flagUserPort.findUserInfosByIds(anySet())).willReturn(Map.of(HOST_ID, userInfo(HOST_ID)));

        FlagDetailResult result = flagQueryService.getFlagDetail(1L, viewerId);

        assertThat(result.isHost()).isFalse();
    }
}
