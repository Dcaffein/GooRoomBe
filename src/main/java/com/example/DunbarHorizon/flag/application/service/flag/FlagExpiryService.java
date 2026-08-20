package com.example.DunbarHorizon.flag.application.service.flag;

import com.example.DunbarHorizon.flag.domain.flag.Flag;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlagExpiryService {

    private final FlagRepository flagRepository;

    @Transactional
    public void expireEndedFlags() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusHours(Flag.EXPIRATION_THRESHOLD_HOURS);

        int count = flagRepository.expireAllExceedingThreshold(threshold, now);

        if (count > 0) {
            log.info("자동 만료 완료: {}건의 플래그를 소프트 삭제", count);
        }
    }
}