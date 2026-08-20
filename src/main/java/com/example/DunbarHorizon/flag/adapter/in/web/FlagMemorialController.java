package com.example.DunbarHorizon.flag.adapter.in.web;

import com.example.DunbarHorizon.flag.adapter.in.web.dto.MemorialCreateRequest;
import com.example.DunbarHorizon.flag.application.port.in.FlagMemorialCommandUseCase;
import com.example.DunbarHorizon.flag.application.port.in.FlagMemorialQueryUseCase;
import com.example.DunbarHorizon.flag.application.dto.result.MemorialListResult;
import com.example.DunbarHorizon.flag.adapter.in.web.dto.MemorialUpdateRequest;
import com.example.DunbarHorizon.global.annotation.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/flags/{flagId}/memorials")
@RequiredArgsConstructor
public class FlagMemorialController {

    private final FlagMemorialCommandUseCase memorialCommandUseCase;
    private final FlagMemorialQueryUseCase memorialQueryUseCase;

    @PostMapping
    public ResponseEntity<Long> createMemorial(
            @PathVariable Long flagId,
            @CurrentUserId Long currentUserId,
            @RequestBody @Valid MemorialCreateRequest request
    ) {
        Long memorialId = memorialCommandUseCase.createMemorial(
                flagId, currentUserId, request.content()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(memorialId);
    }

    @GetMapping
    public ResponseEntity<MemorialListResult> getMemorials(
            @PathVariable Long flagId,
            @CurrentUserId Long currentUserId
    ) {
        return ResponseEntity.ok(memorialQueryUseCase.getMemorials(flagId, currentUserId));
    }

    @GetMapping("/count")
    public ResponseEntity<Long> getMemorialCount(@PathVariable Long flagId) {
        return ResponseEntity.ok(memorialQueryUseCase.getMemorialCount(flagId));
    }

    @PatchMapping("/{memorialId}")
    public ResponseEntity<Void> updateMemorial(
            @PathVariable Long flagId,
            @PathVariable Long memorialId,
            @CurrentUserId Long currentUserId,
            @RequestBody @Valid MemorialUpdateRequest request
    ) {
        memorialCommandUseCase.updateMemorial(flagId, memorialId, currentUserId, request.content());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{memorialId}")
    public ResponseEntity<Void> deleteMemorial(
            @PathVariable Long flagId,
            @PathVariable Long memorialId,
            @CurrentUserId Long currentUserId
    ) {
        memorialCommandUseCase.deleteMemorial(flagId, memorialId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
