package com.example.DunbarHorizon.flag.application.service.invitation;

import com.example.DunbarHorizon.flag.application.dto.info.FlagUserInfo;
import com.example.DunbarHorizon.flag.application.dto.result.ReceivedFlagInvitationResult;
import com.example.DunbarHorizon.flag.application.dto.result.SentFlagInvitationResult;
import com.example.DunbarHorizon.flag.application.port.out.FlagUserPort;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagSchedule;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.flag.domain.invitation.FlagInvitation;
import com.example.DunbarHorizon.flag.domain.invitation.repository.FlagInvitationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FlagInvitationQueryServiceTest {

    @InjectMocks private FlagInvitationQueryService queryService;

    @Mock private FlagInvitationRepository invitationRepository;
    @Mock private FlagRepository flagRepository;
    @Mock private FlagUserPort flagUserPort;

    private static final Long FLAG_ID = 1L;
    private static final Long OTHER_FLAG_ID = 2L;
    private static final Long INVITER_ID = 10L;
    private static final Long INVITEE_ID = 20L;
    private static final LocalDateTime NOW = LocalDateTime.now();

    private static final FlagSchedule RECRUITING =
            FlagSchedule.of(NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3));
    private static final FlagSchedule WAITING =
            FlagSchedule.of(NOW.minusHours(1), NOW.plusHours(1), NOW.plusHours(2));
    private static final FlagSchedule IN_ACTIVITY =
            FlagSchedule.of(NOW.minusHours(3), NOW.minusHours(2), NOW.plusHours(1));
    private static final FlagSchedule ENDED =
            FlagSchedule.of(NOW.minusHours(3), NOW.minusHours(2), NOW.minusHours(1));

    private static Stream<Arguments> notRecruitingSchedules() {
        return Stream.of(
                Arguments.of("WAITING (마감 경과, 모임 미시작)", WAITING),
                Arguments.of("IN_ACTIVITY (모임 진행 중)", IN_ACTIVITY),
                Arguments.of("ENDED (모임 종료)", ENDED)
        );
    }

    private FlagInvitation buildInvitation(Long id) {
        return buildInvitation(id, FLAG_ID);
    }

    private FlagInvitation buildInvitation(Long id, Long flagId) {
        FlagInvitation inv = FlagInvitation.create(flagId, INVITER_ID, INVITEE_ID);
        ReflectionTestUtils.setField(inv, "id", id);
        ReflectionTestUtils.setField(inv, "createdAt", NOW);
        return inv;
    }

    private Flag buildFlag() {
        return buildFlag(FLAG_ID, RECRUITING);
    }

    private Flag buildFlag(Long flagId, FlagSchedule schedule) {
        Flag flag = Flag.create(INVITER_ID, "플래그 제목", "플래그 설명", 10, schedule);
        ReflectionTestUtils.setField(flag, "id", flagId);
        return flag;
    }

    @Test
    @DisplayName("받은 초대 목록을 flagTitle, inviterNickname 포함하여 반환한다")
    void getReceived_ReturnsMappedResults() {
        // given
        FlagInvitation invitation = buildInvitation(1L);
        Flag flag = buildFlag();
        FlagUserInfo inviterInfo = new FlagUserInfo(INVITER_ID, "초대자닉네임", null);

        given(invitationRepository.findByInviteeId(INVITEE_ID)).willReturn(List.of(invitation));
        given(flagRepository.findAllByIdIn(Set.of(FLAG_ID))).willReturn(List.of(flag));
        given(flagUserPort.findUserInfosByIds(Set.of(INVITER_ID))).willReturn(Map.of(INVITER_ID, inviterInfo));

        // when
        List<ReceivedFlagInvitationResult> results = queryService.getReceived(INVITEE_ID);

        // then
        assertThat(results).hasSize(1);
        ReceivedFlagInvitationResult result = results.get(0);
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.flagId()).isEqualTo(FLAG_ID);
        assertThat(result.flagTitle()).isEqualTo("플래그 제목");
        assertThat(result.flagDescription()).isEqualTo("플래그 설명");
        assertThat(result.inviterNickname()).isEqualTo("초대자닉네임");
    }

    @Test
    @DisplayName("보낸 초대 목록을 flagTitle, inviteeNickname 포함하여 반환한다")
    void getSent_ReturnsMappedResults() {
        // given
        FlagInvitation invitation = buildInvitation(2L);
        Flag flag = buildFlag();
        FlagUserInfo inviteeInfo = new FlagUserInfo(INVITEE_ID, "수신자닉네임", null);

        given(invitationRepository.findByInviterId(INVITER_ID)).willReturn(List.of(invitation));
        given(flagRepository.findAllByIdIn(Set.of(FLAG_ID))).willReturn(List.of(flag));
        given(flagUserPort.findUserInfosByIds(Set.of(INVITEE_ID))).willReturn(Map.of(INVITEE_ID, inviteeInfo));

        // when
        List<SentFlagInvitationResult> results = queryService.getSent(INVITER_ID);

        // then
        assertThat(results).hasSize(1);
        SentFlagInvitationResult result = results.get(0);
        assertThat(result.id()).isEqualTo(2L);
        assertThat(result.flagId()).isEqualTo(FLAG_ID);
        assertThat(result.flagTitle()).isEqualTo("플래그 제목");
        assertThat(result.inviteeNickname()).isEqualTo("수신자닉네임");
    }

    @Test
    @DisplayName("초대가 없으면 빈 목록을 반환한다")
    void getReceived_WhenEmpty_ReturnsEmptyList() {
        given(invitationRepository.findByInviteeId(INVITEE_ID)).willReturn(List.of());

        assertThat(queryService.getReceived(INVITEE_ID)).isEmpty();
    }

    @Test
    @DisplayName("플래그가 삭제되었으면 해당 초대는 결과에서 제외된다")
    void getReceived_WhenFlagDeleted_ExcludesResult() {
        // given
        FlagInvitation invitation = buildInvitation(1L);
        FlagUserInfo inviterInfo = new FlagUserInfo(INVITER_ID, "초대자닉네임", null);

        given(invitationRepository.findByInviteeId(INVITEE_ID)).willReturn(List.of(invitation));
        given(flagRepository.findAllByIdIn(Set.of(FLAG_ID))).willReturn(List.of()); // 삭제된 플래그
        given(flagUserPort.findUserInfosByIds(Set.of(INVITER_ID))).willReturn(Map.of(INVITER_ID, inviterInfo));

        // when
        List<ReceivedFlagInvitationResult> results = queryService.getReceived(INVITEE_ID);

        // then
        assertThat(results).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notRecruitingSchedules")
    @DisplayName("모집 중이 아닌 플래그의 받은 초대는 결과에서 제외된다")
    void getReceived_WhenFlagNotRecruiting_ExcludesResult(String label, FlagSchedule schedule) {
        // given
        FlagInvitation invitation = buildInvitation(1L);
        Flag flag = buildFlag(FLAG_ID, schedule);
        FlagUserInfo inviterInfo = new FlagUserInfo(INVITER_ID, "초대자닉네임", null);

        given(invitationRepository.findByInviteeId(INVITEE_ID)).willReturn(List.of(invitation));
        given(flagRepository.findAllByIdIn(Set.of(FLAG_ID))).willReturn(List.of(flag));
        given(flagUserPort.findUserInfosByIds(Set.of(INVITER_ID))).willReturn(Map.of(INVITER_ID, inviterInfo));

        // when
        List<ReceivedFlagInvitationResult> results = queryService.getReceived(INVITEE_ID);

        // then
        assertThat(results).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notRecruitingSchedules")
    @DisplayName("모집 중이 아닌 플래그의 보낸 초대는 결과에서 제외된다")
    void getSent_WhenFlagNotRecruiting_ExcludesResult(String label, FlagSchedule schedule) {
        // given
        FlagInvitation invitation = buildInvitation(2L);
        Flag flag = buildFlag(FLAG_ID, schedule);
        FlagUserInfo inviteeInfo = new FlagUserInfo(INVITEE_ID, "수신자닉네임", null);

        given(invitationRepository.findByInviterId(INVITER_ID)).willReturn(List.of(invitation));
        given(flagRepository.findAllByIdIn(Set.of(FLAG_ID))).willReturn(List.of(flag));
        given(flagUserPort.findUserInfosByIds(Set.of(INVITEE_ID))).willReturn(Map.of(INVITEE_ID, inviteeInfo));

        // when
        List<SentFlagInvitationResult> results = queryService.getSent(INVITER_ID);

        // then
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("모집 중인 초대와 만료된 초대가 섞여 있으면 모집 중인 것만 반환한다")
    void getReceived_WhenMixed_ReturnsOnlyRecruiting() {
        // given
        FlagInvitation recruitingInvitation = buildInvitation(1L, FLAG_ID);
        FlagInvitation endedInvitation = buildInvitation(2L, OTHER_FLAG_ID);
        FlagUserInfo inviterInfo = new FlagUserInfo(INVITER_ID, "초대자닉네임", null);

        given(invitationRepository.findByInviteeId(INVITEE_ID))
                .willReturn(List.of(recruitingInvitation, endedInvitation));
        given(flagRepository.findAllByIdIn(Set.of(FLAG_ID, OTHER_FLAG_ID)))
                .willReturn(List.of(buildFlag(FLAG_ID, RECRUITING), buildFlag(OTHER_FLAG_ID, ENDED)));
        given(flagUserPort.findUserInfosByIds(Set.of(INVITER_ID))).willReturn(Map.of(INVITER_ID, inviterInfo));

        // when
        List<ReceivedFlagInvitationResult> results = queryService.getReceived(INVITEE_ID);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(1L);
        assertThat(results.get(0).flagId()).isEqualTo(FLAG_ID);
    }
}
