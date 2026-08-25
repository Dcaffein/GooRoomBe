package com.example.DunbarHorizon.flag.application.service.memorial;

import com.example.DunbarHorizon.flag.application.port.in.FlagMemorialCommandUseCase;
import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagNotFoundException;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.flag.domain.memorial.FlagMemorial;
import com.example.DunbarHorizon.flag.domain.memorial.FlagMemorialFactory;
import com.example.DunbarHorizon.flag.domain.memorial.event.MemorialDeletedEvent;
import com.example.DunbarHorizon.flag.domain.memorial.exception.FlagMemorialNotFoundException;
import com.example.DunbarHorizon.flag.domain.memorial.repository.FlagMemorialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FlagMemorialCommandService implements FlagMemorialCommandUseCase {

    private final FlagRepository flagRepository;
    private final FlagMemorialRepository memorialRepository;
    private final FlagMemorialFactory memorialFactory;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Long createMemorial(Long flagId, Long userId, String content) {
        Flag flag = flagRepository.findById(flagId)
                .orElseThrow(() -> new FlagNotFoundException(flagId));

        FlagMemorial newMemorial = memorialFactory.create(flag, userId, content);

        FlagMemorial saved = memorialRepository.save(newMemorial);

        return saved.getId();
    }

    @Override
    public void updateMemorial(Long flagId, Long memorialId, Long requesterId, String content) {
        FlagMemorial memorial = memorialRepository.findById(memorialId)
                .orElseThrow(() -> new FlagMemorialNotFoundException(memorialId));
        validateBelongsToFlag(memorial, flagId);

        memorial.updateContent(requesterId, content);
        memorialRepository.save(memorial);
    }

    @Override
    public void deleteMemorial(Long flagId, Long memorialId, Long requesterId) {
        FlagMemorial memorial = memorialRepository.findById(memorialId)
                .orElseThrow(() -> new FlagMemorialNotFoundException(memorialId));
        validateBelongsToFlag(memorial, flagId);

        memorial.validateDeletion(requesterId);

        memorialRepository.delete(memorial);
        eventPublisher.publishEvent(new MemorialDeletedEvent(memorial.getFlagId()));
    }

    private void validateBelongsToFlag(FlagMemorial memorial, Long flagId) {
        if (!flagId.equals(memorial.getFlagId())) {
            throw new FlagMemorialNotFoundException(memorial.getId());
        }
    }
}
