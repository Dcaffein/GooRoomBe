package com.example.DunbarHorizon.social.adapter.in.web;

import com.example.DunbarHorizon.global.annotation.CurrentUserId;
import com.example.DunbarHorizon.social.adapter.in.web.dto.FriendRequestCreateRequest;
import com.example.DunbarHorizon.social.adapter.in.web.dto.FriendRequestStatusUpdateRequest;
import com.example.DunbarHorizon.social.application.dto.result.FriendRequestResult;
import com.example.DunbarHorizon.social.application.dto.FriendRequestDirection;
import com.example.DunbarHorizon.social.application.port.in.FriendRequestReceiverActionUseCase;
import com.example.DunbarHorizon.social.application.port.in.FriendRequestQueryUseCase;
import com.example.DunbarHorizon.social.application.port.in.FriendRequesterActionUseCase;
import com.example.DunbarHorizon.social.domain.friend.FriendRequestStatus;
import com.example.DunbarHorizon.social.domain.friend.FriendRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/friend-requests")
@RequiredArgsConstructor
public class FriendRequestController {

    private final FriendRequesterActionUseCase requesterActionUseCase;
    private final FriendRequestReceiverActionUseCase receiverActionUseCase;
    private final FriendRequestQueryUseCase queryUseCase;

    @GetMapping
    public ResponseEntity<List<FriendRequestResult>> getRequests(
            @CurrentUserId Long currentUserId,
            @RequestParam String direction,
            @RequestParam(required = false) FriendRequestStatus status) {

        return ResponseEntity.ok(queryUseCase.getRequests(
                currentUserId, FriendRequestDirection.from(direction), status));
    }

    @PostMapping
    public ResponseEntity<FriendRequestResult> sendFriendRequest(
            @CurrentUserId Long currentUserId,
            @RequestBody @Valid FriendRequestCreateRequest request) {

        FriendRequest newRequest = requesterActionUseCase.sendRequest(currentUserId, request.receiverId());
        FriendRequestResult response = FriendRequestResult.from(newRequest);

        return ResponseEntity
                .created(URI.create(
                        "/api/v1/friend-requests/" + newRequest.getReceiver().getId()))
                .body(response);
    }

    @DeleteMapping("/{counterpartId}")
    public ResponseEntity<Void> cancelFriendRequest(
            @CurrentUserId Long currentUserId,
            @PathVariable Long counterpartId) {

        requesterActionUseCase.cancelRequest(counterpartId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{counterpartId}")
    public ResponseEntity<Void> updateFriendRequestStatus(
            @CurrentUserId Long currentUserId,
            @PathVariable Long counterpartId,
            @RequestBody @Valid FriendRequestStatusUpdateRequest request) {

        receiverActionUseCase.updateStatus(currentUserId, counterpartId, request.status());
        return ResponseEntity.noContent().build();
    }
}
