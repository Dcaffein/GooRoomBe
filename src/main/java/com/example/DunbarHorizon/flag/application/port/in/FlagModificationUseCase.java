package com.example.DunbarHorizon.flag.application.port.in;

import com.example.DunbarHorizon.flag.application.port.in.command.FlagCapacityUpdateCommand;
import com.example.DunbarHorizon.flag.application.port.in.command.FlagDetailsUpdateCommand;
import com.example.DunbarHorizon.flag.application.port.in.command.FlagScheduleUpdateCommand;

public interface FlagModificationUseCase {
    void modifyFlagDetails(FlagDetailsUpdateCommand command);
    void modifyFlagCapacity(FlagCapacityUpdateCommand command);
    void reschedule(FlagScheduleUpdateCommand command);
    void closeRecruitment(Long flagId, Long hostId);
    void closeFlag(Long flagId, Long userId);
}