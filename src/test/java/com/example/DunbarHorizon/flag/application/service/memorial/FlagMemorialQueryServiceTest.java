package com.example.DunbarHorizon.flag.application.service.memorial;

import com.example.DunbarHorizon.flag.application.dto.info.FlagUserInfo;
import com.example.DunbarHorizon.flag.application.dto.result.MemorialListResult;
import com.example.DunbarHorizon.flag.application.dto.result.MemorialResult;
import com.example.DunbarHorizon.flag.application.port.out.FlagUserPort;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagAuthorizationException;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagNotFoundException;
import com.example.DunbarHorizon.flag.domain.flag.repository.FlagRepository;
import com.example.DunbarHorizon.flag.domain.memorial.FlagMemorial;
import com.example.DunbarHorizon.flag.domain.memorial.repository.FlagMemorialRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FlagMemorialQueryServiceTest {

    @InjectMocks private FlagMemorialQueryService service;

    @Mock private FlagMemorialRepository memorialRepository;
    @Mock private FlagRepository flagRepository;
    @Mock private FlagUserPort flagUserPort;

    private static final Long FLAG_ID = 1L;
    private static final Long HOST_ID = 99L;
    private static final Long VIEWER_ID = 10L;
    private static final Long WRITER_ID = 20L;

    @Test
    @DisplayName("존재하지 않는 플래그를 조회하면 FlagNotFoundException이 발생한다")
    void getMemorials_플래그없음_예외() {
        given(flagRepository.findHostIdById(FLAG_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMemorials(FLAG_ID, VIEWER_ID))
                .isInstanceOf(FlagNotFoundException.class);
    }

    @Test
    @DisplayName("비참여자가 조회하면 FlagAuthorizationException이 발생한다")
    void getMemorials_비참여자_예외() {
        given(flagRepository.findHostIdById(FLAG_ID)).willReturn(Optional.of(HOST_ID));
        given(flagRepository.isParticipating(FLAG_ID, VIEWER_ID)).willReturn(false);

        assertThatThrownBy(() -> service.getMemorials(FLAG_ID, VIEWER_ID))
                .isInstanceOf(FlagAuthorizationException.class);
    }

    @Test
    @DisplayName("후기가 없으면 locked=false, 빈 배열을 반환한다")
    void getMemorials_후기없음_empty반환() {
        given(flagRepository.findHostIdById(FLAG_ID)).willReturn(Optional.of(HOST_ID));
        given(flagRepository.isParticipating(FLAG_ID, VIEWER_ID)).willReturn(true);
        given(memorialRepository.findAllByFlagId(FLAG_ID)).willReturn(List.of());

        MemorialListResult result = service.getMemorials(FLAG_ID, VIEWER_ID);

        assertThat(result.locked()).isFalse();
        assertThat(result.memorials()).isEmpty();
        verify(flagUserPort, never()).findUserInfosByIds(any());
    }

    @Test
    @DisplayName("후기가 있으나 본인이 안 남겼으면 locked=true, 빈 배열을 반환한다")
    void getMemorials_본인미작성_locked반환() {
        FlagMemorial othersMemorial = mock(FlagMemorial.class);
        given(othersMemorial.getWriterId()).willReturn(WRITER_ID);

        given(flagRepository.findHostIdById(FLAG_ID)).willReturn(Optional.of(HOST_ID));
        given(flagRepository.isParticipating(FLAG_ID, VIEWER_ID)).willReturn(true);
        given(memorialRepository.findAllByFlagId(FLAG_ID)).willReturn(List.of(othersMemorial));

        MemorialListResult result = service.getMemorials(FLAG_ID, VIEWER_ID);

        assertThat(result.locked()).isTrue();
        assertThat(result.memorials()).isEmpty();
        verify(flagUserPort, never()).findUserInfosByIds(any());
    }

    @Test
    @DisplayName("본인이 후기를 남겼으면 locked=false, 전체 후기 목록을 반환한다")
    void getMemorials_본인작성완료_목록반환() {
        FlagMemorial mine = memorialOf(1L, VIEWER_ID, "저도 즐거웠어요!");
        FlagMemorial others = memorialOf(2L, WRITER_ID, "즐거웠어요!");

        given(flagRepository.findHostIdById(FLAG_ID)).willReturn(Optional.of(HOST_ID));
        given(flagRepository.isParticipating(FLAG_ID, VIEWER_ID)).willReturn(true);
        given(memorialRepository.findAllByFlagId(FLAG_ID)).willReturn(List.of(mine, others));
        given(flagUserPort.findUserInfosByIds(any())).willReturn(Map.of(
                VIEWER_ID, new FlagUserInfo(VIEWER_ID, "나", null),
                WRITER_ID, new FlagUserInfo(WRITER_ID, "홍길동", null)
        ));

        MemorialListResult result = service.getMemorials(FLAG_ID, VIEWER_ID);

        assertThat(result.locked()).isFalse();
        assertThat(result.memorials()).hasSize(2);
        assertThat(result.memorials()).extracting(MemorialResult::nickname)
                .containsExactly("나", "홍길동");
    }

    @Test
    @DisplayName("작성자 정보가 없으면 기본 닉네임으로 반환한다")
    void getMemorials_작성자정보없음_기본닉네임반환() {
        FlagMemorial mine = memorialOf(1L, VIEWER_ID, "후기 내용");

        given(flagRepository.findHostIdById(FLAG_ID)).willReturn(Optional.of(HOST_ID));
        given(flagRepository.isParticipating(FLAG_ID, VIEWER_ID)).willReturn(true);
        given(memorialRepository.findAllByFlagId(FLAG_ID)).willReturn(List.of(mine));
        given(flagUserPort.findUserInfosByIds(any())).willReturn(Map.of());

        MemorialListResult result = service.getMemorials(FLAG_ID, VIEWER_ID);

        assertThat(result.memorials().get(0).nickname()).isEqualTo("알 수 없는 사용자");
    }

    private FlagMemorial memorialOf(Long id, Long writerId, String content) {
        FlagMemorial memorial = mock(FlagMemorial.class);
        given(memorial.getWriterId()).willReturn(writerId);
        given(memorial.getId()).willReturn(id);
        given(memorial.getContent()).willReturn(content);
        given(memorial.getCreatedAt()).willReturn(LocalDateTime.now());
        return memorial;
    }
}
