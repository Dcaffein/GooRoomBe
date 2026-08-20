package com.example.DunbarHorizon.flag.application.service.invitation;

import com.example.DunbarHorizon.flag.application.dto.info.FlagUserInfo;
import com.example.DunbarHorizon.flag.application.dto.result.ReceivedFlagInvitationResult;
import com.example.DunbarHorizon.flag.application.dto.result.SentFlagInvitationResult;
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
    public List<ReceivedFlagInvitationResult> getReceived(Long inviteeId) {
        List<FlagInvitation> invitations = invitationRepository.findByInviteeId(inviteeId);
        if (invitations.isEmpty()) return List.of();

        Map<Long, Flag> flagMap = fetchFlagMap(invitations, FlagInvitation::getFlagId);
        Map<Long, FlagUserInfo> userMap = flagUserPort.findUserInfosByIds(
                invitations.stream().map(FlagInvitation::getInviterId).collect(Collectors.toSet())
        );

        return invitations.stream()
                .filter(inv -> flagMap.containsKey(inv.getFlagId()) && userMap.containsKey(inv.getInviterId()))
                .map(inv -> ReceivedFlagInvitationResult.of(inv, flagMap.get(inv.getFlagId()), userMap.get(inv.getInviterId())))
                .toList();
    }

    @Override
    public List<SentFlagInvitationResult> getSent(Long inviterId) {
        List<FlagInvitation> invitations = invitationRepository.findByInviterId(inviterId);
        if (invitations.isEmpty()) return List.of();

        Map<Long, Flag> flagMap = fetchFlagMap(invitations, FlagInvitation::getFlagId);
        Map<Long, FlagUserInfo> userMap = flagUserPort.findUserInfosByIds(
                invitations.stream().map(FlagInvitation::getInviteeId).collect(Collectors.toSet())
        );

        return invitations.stream()
                .filter(inv -> flagMap.containsKey(inv.getFlagId()) && userMap.containsKey(inv.getInviteeId()))
                .map(inv -> SentFlagInvitationResult.of(inv, flagMap.get(inv.getFlagId()), userMap.get(inv.getInviteeId())))
                .toList();
    }

    private Map<Long, Flag> fetchFlagMap(List<FlagInvitation> invitations, Function<FlagInvitation, Long> idExtractor) {
        Set<Long> flagIds = invitations.stream().map(idExtractor).collect(Collectors.toSet());
        return flagRepository.findAllByIdIn(flagIds).stream()
                .collect(Collectors.toMap(Flag::getId, Function.identity()));
    }
}
