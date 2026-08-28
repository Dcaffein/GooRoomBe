package com.example.DunbarHorizon.social.application.service;

import com.example.DunbarHorizon.social.application.dto.result.ConnectionPathResult;
import com.example.DunbarHorizon.social.application.port.out.SocialConnectionPathRepository;
import com.example.DunbarHorizon.social.domain.friend.repository.FriendshipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SocialConnectionPathQueryServiceTest {

    @Mock
    private SocialConnectionPathRepository connectionPathRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @InjectMocks
    private SocialConnectionPathQueryService service;

    @Test
    @DisplayName("상한 3을 리포지토리에 전달하고 전체 수와 목록을 응답에 담는다")
    void getConnectionPath_상한_전달() {
        // given
        given(friendshipRepository.existsFriendshipBetween(1L, 99L)).willReturn(false);
        given(connectionPathRepository.findIntermediaries(1L, 99L, 3))
                .willReturn(new ConnectionPathResult.Intermediaries(
                        List.of(new ConnectionPathResult.IntermediaryResult(2L, "중개인2")), 12));

        // when
        ConnectionPathResult result = service.getConnectionPath(1L, 99L);

        // then
        verify(connectionPathRepository).findIntermediaries(1L, 99L, 3);
        assertThat(result.direct()).isFalse();
        assertThat(result.totalCount()).isEqualTo(12);
        assertThat(result.intermediaries()).extracting(ConnectionPathResult.IntermediaryResult::userId)
                .containsExactly(2L);
    }

    @Test
    @DisplayName("이미 친구인 대상이어도 공통 친구를 조회한다")
    void getConnectionPath_직접_친구여도_조회한다() {
        // given
        given(friendshipRepository.existsFriendshipBetween(1L, 99L)).willReturn(true);
        given(connectionPathRepository.findIntermediaries(1L, 99L, 3))
                .willReturn(new ConnectionPathResult.Intermediaries(
                        List.of(new ConnectionPathResult.IntermediaryResult(2L, "중개인2")), 12));

        // when
        ConnectionPathResult result = service.getConnectionPath(1L, 99L);

        // then
        verify(connectionPathRepository).findIntermediaries(1L, 99L, 3);
        assertThat(result.direct()).isTrue();
        assertThat(result.totalCount()).isEqualTo(12);
        assertThat(result.intermediaries()).hasSize(1);
    }

    @Test
    @DisplayName("자기 자신을 대상으로 하면 조회 없이 빈 결과를 반환한다")
    void getConnectionPath_자기_자신() {
        // when
        ConnectionPathResult result = service.getConnectionPath(1L, 1L);

        // then
        assertThat(result.direct()).isFalse();
        assertThat(result.totalCount()).isZero();
        assertThat(result.intermediaries()).isEmpty();
        verifyNoInteractions(connectionPathRepository, friendshipRepository);
    }
}
