package com.example.GooRoomBe.social.friend.api;

import com.example.GooRoomBe.social.friend.api.dto.FriendRequestResponseDto;
import com.example.GooRoomBe.social.friend.api.dto.FriendUpdateRequestDto;
import com.example.GooRoomBe.social.friend.api.dto.FriendRequestCreateDto;
import com.example.GooRoomBe.social.friend.api.dto.FriendRequestUpdateDto;
import com.example.GooRoomBe.social.friend.application.FriendRequestService;
import com.example.GooRoomBe.social.friend.application.FriendshipService;
import com.example.GooRoomBe.social.friend.domain.FriendRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FriendController {

    private final FriendshipService friendShipService;
    private final FriendRequestService friendRequestService;

    @PostMapping("/friend-requests")
    public ResponseEntity<FriendRequestResponseDto> sendFriendRequest(
            @AuthenticationPrincipal String currentUserId,
            @RequestBody @Valid FriendRequestCreateDto friendRequestCreateDto) {

        FriendRequest newRequest = friendRequestService.createFriendRequest(currentUserId, friendRequestCreateDto.receiverId());

        FriendRequestResponseDto response = FriendRequestResponseDto.from(newRequest);

        return ResponseEntity
                .created(URI.create("/api/v1/friend-requests/" + newRequest.getId()))
                .body(response);
    }

    @PatchMapping("/friend-requests/{requestId}")
    public ResponseEntity<Void> updateFriendRequest(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable String requestId,
            @RequestBody @Valid FriendRequestUpdateDto friendRequestUpdateDto) {

        friendRequestService.updateFriendRequest(requestId, currentUserId, friendRequestUpdateDto.status());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/friend-requests/{requestId}")
    public ResponseEntity<Void> deleteFriendRequest(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable @Valid String requestId) {

        friendRequestService.cancelFriendRequest(requestId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/friends/{friendId}")
    public ResponseEntity<Void> updateFriend(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable String friendId,
            @RequestBody @Valid FriendUpdateRequestDto friendUpdateRequestDto) {

        friendShipService.updateFriendProps(currentUserId, friendId, friendUpdateRequestDto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/friends/{friendId}")
    public ResponseEntity<Void> deleteFriendship(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable @Valid String friendId) {

        friendShipService.deleteFriendShip(currentUserId, friendId);
        return ResponseEntity.noContent().build();
    }

}