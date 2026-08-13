package com.example.DunbarHorizon.social.adapter.in.web;

import com.example.DunbarHorizon.global.annotation.CurrentUserId;
import com.example.DunbarHorizon.social.application.dto.result.AnchorExpansionResult;
import com.example.DunbarHorizon.social.application.dto.result.ConnectionPathResult;
import com.example.DunbarHorizon.social.application.dto.result.MutualFriendEdgeResult;
import com.example.DunbarHorizon.social.application.dto.result.NodeGraphResult;
import com.example.DunbarHorizon.social.application.port.in.SocialConnectionPathQueryUseCase;
import com.example.DunbarHorizon.social.application.port.in.SocialExpansionQueryUseCase;
import com.example.DunbarHorizon.social.application.port.in.SocialNetworkQueryUseCase;
import com.example.DunbarHorizon.social.domain.friend.DunbarCircle;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/networks")
public class SocialQueryController {

    private final SocialExpansionQueryUseCase expansionQueryUseCase;
    private final SocialNetworkQueryUseCase networkQueryUseCase;
    private final SocialConnectionPathQueryUseCase connectionPathQueryUseCase;

    /**
     * 메인 홈 네트워크 (Soft Morphing 적용으로 1개의 API로 통합)
     * 프론트엔드는 슬라이더 위치에 따라 circleSize(SUPPORT, SYMPATHY, KINSHIP, DUNBAR)를 보냄
     */
    @GetMapping("/me")
    public ResponseEntity<List<NodeGraphResult>> getFriendsNetwork(
            @CurrentUserId Long currentUserId,
            @RequestParam(defaultValue = "DUNBAR") DunbarCircle circleSize
    ) {
        return ResponseEntity.ok(networkQueryUseCase.getFriendsNetwork(currentUserId, circleSize));
    }

    /**
     * 라벨 네트워크
     * 라벨 크기와 무관하게 최대 150명(Dunbar's Ceiling)까지 단일 뷰로 제공
     */
    @GetMapping("/labels/{labelId}")
    public ResponseEntity<List<NodeGraphResult>> getLabelNetwork(
            @CurrentUserId Long currentUserId,
            @PathVariable String labelId
    ) {
        return ResponseEntity.ok(networkQueryUseCase.getLabelNetwork(currentUserId, labelId));
    }

    /**
     * 1-Hop 친구를 기존 네트워크에 수동 추가
     * 클라이언트가 현재 화면의 skeleton ID 목록을 전달하여 동적 컨텍스트 반영 및 보안 검증
     */
    @GetMapping("/mutual/one-hop")
    public ResponseEntity<List<MutualFriendEdgeResult>> getOneHopMutualFriendEdges(
            @CurrentUserId Long currentUserId,
            @RequestParam Long targetId,
            @RequestParam List<Long> skeletonIds
    ) {
        return ResponseEntity.ok(networkQueryUseCase.getNewNodeEdges(currentUserId, targetId, skeletonIds));
    }

    /**
     * 2-Hop 유저와 기존 네트워크와의 접점
     * 클라이언트가 현재 화면의 skeleton ID 목록을 전달하여 동적 컨텍스트 반영 및 보안 검증
     */
    @GetMapping("/mutual/two-hop")
    public ResponseEntity<List<Long>> getTwoHopMutualFriends(
            @CurrentUserId Long currentUserId,
            @RequestParam Long targetId,
            @RequestParam List<Long> skeletonIds
    ) {
        return ResponseEntity.ok(networkQueryUseCase.getNetworkContactsOfTwoHop(currentUserId, targetId, skeletonIds));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<AnchorExpansionResult>> getTwoHopRecommendation(
            @CurrentUserId Long currentUserId,
            @RequestParam Long anchorId
    ) {
        return ResponseEntity.ok(expansionQueryUseCase.getRecommendationsByAnchor(currentUserId, anchorId));
    }

    @GetMapping("/path")
    public ResponseEntity<ConnectionPathResult> getConnectionPath(
            @CurrentUserId Long currentUserId,
            @RequestParam Long targetId
    ) {
        return ResponseEntity.ok(connectionPathQueryUseCase.getConnectionPath(currentUserId, targetId));
    }
}