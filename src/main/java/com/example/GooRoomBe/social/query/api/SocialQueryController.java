package com.example.GooRoomBe.social.query.api;

import com.example.GooRoomBe.social.query.SocialQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/social/")
public class SocialQueryController {

    private final SocialQueryService socialQueryService;

    /**
     * 1-hop 친구 네트워크(친구 목록 + 각 친구와 함께 아는 친구 ID 목록)를 조회
     *
     * @param userId (인증) 현재 로그인한 사용자 ID
     * @return 1-hop 네트워크 DTO 리스트
     */
    @GetMapping("/network")
    public ResponseEntity<List<OneHopsNetworkDto>> getMyOneHopsNetwork(
            @AuthenticationPrincipal String userId
    ) {
        List<OneHopsNetworkDto> network = socialQueryService.getOneHopsNetwork(userId);
        return ResponseEntity.ok(network);
    }

    /**
     * 2-hop 친구 추천 목록 조회
     *
     * @param userId (인증) 현재 로그인한 사용자 ID
     * @return 2-hop 추천 DTO 리스트
     */
    @GetMapping("/suggestions")
    public ResponseEntity<List<FriendSuggestionDto>> getMyTwoHopSuggestions(
            @AuthenticationPrincipal String userId
    ) {
        List<FriendSuggestionDto> suggestions = socialQueryService.getTwoHopSuggestion(userId);
        return ResponseEntity.ok(suggestions);
    }
}