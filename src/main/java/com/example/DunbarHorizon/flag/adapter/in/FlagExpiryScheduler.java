package com.example.DunbarHorizon.flag.adapter.in;

import com.example.DunbarHorizon.flag.application.service.flag.FlagExpiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FlagExpiryScheduler {

    private final FlagExpiryService expiryService;

    @Scheduled(cron = "0 0 0/6 * * *")
    public void runExpiry() {
        expiryService.expireEndedFlags();
    }
}