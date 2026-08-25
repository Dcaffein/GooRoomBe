package com.example.DunbarHorizon.flag.application.service.flag;

import com.example.DunbarHorizon.flag.application.port.in.FlagModificationUseCase;
import com.example.DunbarHorizon.flag.application.port.in.command.FlagCapacityUpdateCommand;
import com.example.DunbarHorizon.flag.application.port.in.command.FlagDetailsUpdateCommand;
import com.example.DunbarHorizon.flag.application.port.in.command.FlagScheduleUpdateCommand;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.FlagSchedule;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagNotFoundException;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FlagModificationService implements FlagModificationUseCase {
    private final FlagRepository flagRepository;

    @Override
    public void modifyFlagDetails(FlagDetailsUpdateCommand command) {
        Flag flag = getFlagOrThrow(command.flagId());
        flag.updateBasicInfo(command.hostId(), command.title(), command.description());
    }

    @Override
    public void modifyFlagCapacity(FlagCapacityUpdateCommand command) {
        // 참여 경로(FlagParticipationManager)와 같은 순서로 잠근 뒤 센다.
        // 잠금 밖에서 센 값을 쓰면 그 사이 참여가 끼어들어 정원보다 참여자가 많아질 수 있다.
        Flag flag = flagRepository.findByIdForUpdate(command.flagId())
                .orElseThrow(() -> new FlagNotFoundException(command.flagId()));
        int currentCount = flagRepository.countParticipants(command.flagId());
        flag.updateCapacity(command.hostId(), command.capacity(), currentCount);
    }

    @Override
    public void reschedule(FlagScheduleUpdateCommand command) {
        FlagSchedule newSchedule = FlagSchedule.of(command.deadline(), command.startDateTime(), command.endDateTime());
        Flag flag = getFlagOrThrow(command.flagId());
        flag.reschedule(command.hostId(), newSchedule);
        flagRepository.save(flag);
    }

    @Override
    public void closeRecruitment(Long flagId, Long hostId) {
        Flag flag = flagRepository.findByIdForUpdate(flagId)
                .orElseThrow(() -> new FlagNotFoundException(flagId));
        flag.closeRecruitment(hostId);
    }

    @Override
    public void closeFlag(Long flagId, Long userId) {
        Flag flag = getFlagOrThrow(flagId);
        flag.delete(userId);
        flagRepository.save(flag);
    }

    private Flag getFlagOrThrow(Long id) {
        return flagRepository.findById(id).orElseThrow(() -> new FlagNotFoundException(id));
    }
}
