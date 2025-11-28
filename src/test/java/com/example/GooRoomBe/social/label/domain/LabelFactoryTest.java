package com.example.GooRoomBe.social.label.domain;

import com.example.GooRoomBe.social.common.exception.SocialUserNotFoundException;
import com.example.GooRoomBe.social.label.domain.service.LabelNameService;
import com.example.GooRoomBe.social.label.exception.LabelNameDuplicateException;
import com.example.GooRoomBe.social.socialUser.SocialUser;
import com.example.GooRoomBe.social.socialUser.SocialUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabelFactoryTest {

    @Mock
    private SocialUserRepository socialUserRepository;

    @Mock
    private LabelNameService labelNameService;

    @InjectMocks
    private LabelFactory labelFactory;

    private final String OWNER_ID = "user-1";
    private final String LABEL_NAME = "Family";

    @Test
    @DisplayName("성공: 이름 중복이 없고 유저가 존재하면 라벨을 생성한다")
    void createLabel_Success() {
        // given
        SocialUser owner = mock(SocialUser.class);

        //  유저 조회 성공 설정
        when(socialUserRepository.getUser(OWNER_ID)).thenReturn(owner);

        // 이름 검증 성공 설정 (void 메서드이므로 아무 일도 안 일어나면 성공임 - doNothing이 기본값)

        // when
        Label label = labelFactory.createLabel(OWNER_ID, LABEL_NAME, true);

        // then
        // 객체가 잘 생성되었는지 확인
        assertThat(label).isNotNull();
        assertThat(label.getId()).isNotNull(); // UUID 생성 확인
        assertThat(label.getLabelName()).isEqualTo(LABEL_NAME);
        assertThat(label.getOwner()).isEqualTo(owner);
        assertThat(label.isExposure()).isTrue();
        assertThat(label.getMembers()).isEmpty(); // 초기 멤버는 비어있어야 함

        //  협력 객체들이 제대로 호출되었는지 확인 (
        verify(labelNameService).validateLabelNameUniqueness(OWNER_ID, LABEL_NAME);
        verify(socialUserRepository).getUser(OWNER_ID);
    }

    @Test
    @DisplayName("실패: 이미 존재하는 라벨 이름이면 예외가 발생한다")
    void createLabel_Fail_DuplicateName() {
        // given
        // 이름 검증 시 예외 발생 설정
        doThrow(new LabelNameDuplicateException(OWNER_ID, LABEL_NAME))
                .when(labelNameService).validateLabelNameUniqueness(OWNER_ID, LABEL_NAME);

        // when & then
        assertThatThrownBy(() -> labelFactory.createLabel(OWNER_ID, LABEL_NAME, true))
                .isInstanceOf(LabelNameDuplicateException.class);

        // 이름 검증에서 실패했으므로, 유저 조회 로직은 실행되지 않아야 함 (Fail-Fast)
        verify(socialUserRepository, never()).getUser(anyString());
    }

    @Test
    @DisplayName("실패: 존재하지 않는 유저 ID라면 예외가 발생한다")
    void createLabel_Fail_UserNotFound() {
        // given
        // 유저 조회 시 예외 발생 설정
        when(socialUserRepository.getUser(OWNER_ID))
                .thenThrow(new SocialUserNotFoundException(OWNER_ID));

        // when & then
        assertThatThrownBy(() -> labelFactory.createLabel(OWNER_ID, LABEL_NAME, true))
                .isInstanceOf(SocialUserNotFoundException.class);

        // 이름 검증은 통과했어야 함 (순서상 먼저니까)
        verify(labelNameService).validateLabelNameUniqueness(OWNER_ID, LABEL_NAME);
    }
}