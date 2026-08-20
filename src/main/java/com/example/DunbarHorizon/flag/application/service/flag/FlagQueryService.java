package com.example.DunbarHorizon.flag.application.service.flag;

import com.example.DunbarHorizon.flag.application.port.in.FlagQueryUseCase;
import com.example.DunbarHorizon.flag.application.port.in.FlagRole;
import com.example.DunbarHorizon.flag.application.dto.result.FlagDetailResult;
import com.example.DunbarHorizon.flag.application.dto.result.FlagResult;
import com.example.DunbarHorizon.flag.application.dto.info.FlagUserInfo;
import com.example.DunbarHorizon.flag.application.dto.result.ParticipantResult;
import com.example.DunbarHorizon.flag.application.port.out.FlagUserPort;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagParticipant;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagNotFoundException;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlagQueryService implements FlagQueryUseCase {

    private final FlagRepository flagRepository;
    private final FlagUserPort flagUserPort;

    @Override
    public List<FlagResult> getFriendFlags(Long userId) {
        Set<Long> friendIds = flagUserPort.getRelatedUserIds(userId);
        if (friendIds.isEmpty()) return List.of();

        List<Flag> recruitingFlags = flagRepository.findByHostIdsAndDeadlineAfter(friendIds, LocalDateTime.now());
        if (recruitingFlags.isEmpty()) return List.of();

        Set<Long> hostIds = recruitingFlags.stream().map(Flag::getHostId).collect(Collectors.toSet());
        Map<Long, FlagUserInfo> hostInfoMap = flagUserPort.findUserInfosByIds(hostIds);
        List<Long> flagIds = recruitingFlags.stream().map(Flag::getId).toList();
        Map<Long, Integer> countMap = flagRepository.countParticipantsByFlagIds(flagIds);

        return recruitingFlags.stream()
                .map(flag -> FlagResult.of(flag, hostInfoMap.getOrDefault(flag.getHostId(), null),
                        countMap.getOrDefault(flag.getId(), 0)))
                .toList();
    }


    @Override
    public List<FlagResult> getRecentFlags(Long userId) {
        List<Flag> flags = flagRepository.findRecentByUserId(userId, 5);
        if (flags.isEmpty()) return List.of();

        Set<Long> hostIds = flags.stream().map(Flag::getHostId).collect(Collectors.toSet());
        Map<Long, FlagUserInfo> hostInfoMap = flagUserPort.findUserInfosByIds(hostIds);

        List<Long> flagIds = flags.stream().map(Flag::getId).toList();
        Map<Long, Integer> countMap = flagRepository.countParticipantsByFlagIds(flagIds);

        return flags.stream()
                .map(flag -> FlagResult.of(flag, hostInfoMap.get(flag.getHostId()),
                        countMap.getOrDefault(flag.getId(), 0)))
                .toList();
    }

    @Override
    public FlagDetailResult getFlagDetail(Long flagId, Long viewerId) {
        Flag flag = flagRepository.findById(flagId)
                .orElseThrow(() -> new FlagNotFoundException(flagId));

        List<FlagParticipant> flagParticipants = flagRepository.findAllParticipants(flagId);
        List<Long> participantIds = flagParticipants.stream().map(FlagParticipant::getParticipantId).toList();

        Set<Long> allUserIds = new HashSet<>(participantIds);
        allUserIds.add(flag.getHostId());
        Map<Long, FlagUserInfo> userInfoMap = flagUserPort.findUserInfosByIds(allUserIds);

        List<ParticipantResult> participants = flagParticipants.stream()
                .map(p -> ParticipantResult.of(userInfoMap.get(p.getParticipantId()), p.isCanInvite()))
                .toList();

        FlagUserInfo hostInfo = userInfoMap.get(flag.getHostId());

        Flag parentFlag = flag.getParentId() != null
                ? flagRepository.findById(flag.getParentId()).orElse(null)
                : null;

        boolean isHost = flag.getHostId().equals(viewerId);
        return FlagDetailResult.of(flag, hostInfo, parentFlag, participants, isHost);
    }

    @Override
    public List<FlagResult> getFlagsByRole(Long userId, FlagRole role) {
        return switch (role) {
            case HOST -> getHostingFlags(userId);
            case PARTICIPANT -> getParticipatingFlags(userId);
        };
    }

    private List<FlagResult> getHostingFlags(Long userId) {
        List<Flag> managedFlags = flagRepository.findAllByHostId(userId);
        FlagUserInfo myInfo = flagUserPort.findUserInfosByIds(Set.of(userId)).get(userId);

        List<Long> flagIds = managedFlags.stream().map(Flag::getId).toList();
        Map<Long, Integer> countMap = flagRepository.countParticipantsByFlagIds(flagIds);

        return managedFlags.stream()
                .map(flag -> FlagResult.of(flag, myInfo, countMap.getOrDefault(flag.getId(), 0)))
                .toList();
    }

    private List<FlagResult> getParticipatingFlags(Long userId) {
        List<Long> flagIds = flagRepository.findFlagIdsByParticipantId(userId);
        if (flagIds.isEmpty()) return List.of();

        List<Flag> flags = flagRepository.findAllByIdIn(flagIds);
        Set<Long> hostIds = flags.stream().map(Flag::getHostId).collect(Collectors.toSet());
        Map<Long, FlagUserInfo> hostInfoMap = flagUserPort.findUserInfosByIds(hostIds);
        Map<Long, Integer> countMap = flagRepository.countParticipantsByFlagIds(flagIds);

        return flags.stream()
                .map(flag -> FlagResult.of(flag, hostInfoMap.get(flag.getHostId()),
                        countMap.getOrDefault(flag.getId(), 0)))
                .toList();
    }

}