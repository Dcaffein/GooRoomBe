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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
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

    private static final int PROFILE_FLAG_LIMIT = 5;

    private final FlagRepository flagRepository;
    private final FlagUserPort flagUserPort;

    @Override
    public Slice<FlagResult> getFeedFlags(Long userId, Pageable pageable) {
        Set<Long> friendIds = flagUserPort.getRelatedUserIds(userId);
        if (friendIds.isEmpty()) return new SliceImpl<>(List.of(), pageable, false);

        Slice<Flag> flags = flagRepository.findByHostIdsAndDeadlineAfter(
                friendIds, LocalDateTime.now(), pageable
        );
        return toResultSlice(flags);
    }

    @Override
    public List<FlagResult> getProfileFlags(Long userId) {
        return toResults(flagRepository.findByHostIdOrParticipantId(userId, PROFILE_FLAG_LIMIT));
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
    public Slice<FlagResult> getFlagsByRole(Long userId, FlagRole role, Pageable pageable) {
        Slice<Flag> flags = switch (role) {
            case HOST -> flagRepository.findAllByHostId(userId, pageable);
            case PARTICIPANT -> flagRepository.findByParticipantId(userId, pageable);
        };
        return toResultSlice(flags);
    }

    private Slice<FlagResult> toResultSlice(Slice<Flag> flags) {
        return new SliceImpl<>(toResults(flags.getContent()), flags.getPageable(), flags.hasNext());
    }

    private List<FlagResult> toResults(List<Flag> flags) {
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

}
