package com.example.DunbarHorizon.flag.adapter.in;

import com.example.DunbarHorizon.flag.application.service.flag.FlagPurgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FlagPurgeScheduler {

    private final FlagPurgeService purgeService;

    @Scheduled(cron = "0 0 3 * * *")
    public void runPurge() {
        purgeService.purgeExpiredFlags();
    }
}