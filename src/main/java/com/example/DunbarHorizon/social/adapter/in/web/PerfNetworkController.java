package com.example.DunbarHorizon.social.adapter.in.web;

import com.example.DunbarHorizon.social.application.dto.result.NodeGraphResult;
import com.example.DunbarHorizon.social.application.port.in.SocialNetworkQueryUseCase;

import java.util.List;
import com.example.DunbarHorizon.social.domain.friend.DunbarCircle;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Profile("perf")
@RequestMapping("/api/perf/networks")
public class PerfNetworkController {

    private final SocialNetworkQueryUseCase networkQueryUseCase;

    @GetMapping("/{userId}")
    public ResponseEntity<List<NodeGraphResult>> getFriendsNetwork(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "DUNBAR") DunbarCircle circleSize
    ) {
        return ResponseEntity.ok(networkQueryUseCase.getFriendsNetwork(userId, circleSize));
    }
}
