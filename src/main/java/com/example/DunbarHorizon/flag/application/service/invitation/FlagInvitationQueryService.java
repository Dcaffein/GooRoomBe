package com.example.DunbarHorizon.flag.application.service.invitation;

import com.example.DunbarHorizon.flag.application.dto.info.FlagUserInfo;
import com.example.DunbarHorizon.flag.application.dto.FlagInvitationDirection;
import com.example.DunbarHorizon.flag.application.dto.result.FlagInvitationResult;
import com.example.DunbarHorizon.flag.application.port.in.FlagInvitationQueryUseCase;
import com.example.DunbarHorizon.flag.application.port.out.FlagUserPort;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.flag.domain.invitation.FlagInvitation;
import com.example.DunbarHorizon.flag.domain.invitation.repository.FlagInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlagInvitationQueryService implements FlagInvitationQueryUseCase {

    private final FlagInvitationRepository invitationRepository;
    private final FlagRepository flagRepository;
    private final FlagUserPort flagUserPort;

    @Override
    public List<FlagInvitationResult> getInvitations(Long userId, FlagInvitationDirection direction) {
        List<FlagInvitation> invitations = switch (direction) {
            case RECEIVED -> invitationRepository.findByInviteeId(userId);
            case SENT -> invitationRepository.findByInviterId(userId);
        };
        if (invitations.isEmpty()) return List.of();

        Function<FlagInvitation, Long> counterpartIdExtractor = switch (direction) {
            case RECEIVED -> FlagInvitation::getInviterId;
            case SENT -> FlagInvitation::getInviteeId;
        };

        Map<Long, Flag> flagMap = fetchFlagMap(invitations);
        Map<Long, FlagUserInfo> userMap = flagUserPort.findUserInfosByIds(
                invitations.stream().map(counterpartIdExtractor).collect(Collectors.toSet())
        );

        return invitations.stream()
                .filter(invitation -> isRecruiting(flagMap.get(invitation.getFlagId())))
                .filter(invitation -> userMap.containsKey(counterpartIdExtractor.apply(invitation)))
                .map(invitation -> FlagInvitationResult.of(
                        invitation,
                        flagMap.get(invitation.getFlagId()),
                        userMap.get(counterpartIdExtractor.apply(invitation))
                ))
                .toList();
    }

    // 수락할 수 있는 초대만 노출한다. Flag.participate()가 isRecruiting()을 요구하므로
    // 모집이 끝난 플래그의 초대는 목록에 남아 있어도 수락 시 FlagDeadlinePassedException이 된다.
    // flag가 null인 것은 소프트 삭제되어 조회에서 빠진 경우다.
    private static boolean isRecruiting(Flag flag) {
        return flag != null && flag.isRecruiting();
    }

    private Map<Long, Flag> fetchFlagMap(List<FlagInvitation> invitations) {
        Set<Long> flagIds = invitations.stream().map(FlagInvitation::getFlagId).collect(Collectors.toSet());
        return flagRepository.findAllByIdIn(flagIds).stream()
                .collect(Collectors.toMap(Flag::getId, Function.identity()));
    }
}
