package com.example.GooRoomBe.social.label.domain.service;

import com.example.GooRoomBe.social.label.domain.Label;
import com.example.GooRoomBe.social.label.exception.LabelNameDuplicateException;
import com.example.GooRoomBe.social.label.repository.LabelRepository;
import com.example.GooRoomBe.social.socialUser.SocialUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LabelNameServiceTest {

    @Mock
    private LabelRepository labelRepository;

    @InjectMocks
    private LabelNameService labelNameService;

    @Mock
    private Label label;

    @Mock
    private SocialUser owner;

    @Test
    @DisplayName("기존 이름과 동일한 이름으로 변경하면 아무 일도 일어나지 않는다 (DB 조회 X)")
    void changeLabelName_SameName_DoNothing() {
        // given
        String currentName = "Work";
        given(label.getLabelName()).willReturn(currentName);

        // when
        labelNameService.changeLabelName(label, currentName);

        // then
        verify(labelRepository, never()).existsByOwner_IdAndLabelName(any(), any());
        verify(label, never()).applyNewLabelName(any());
    }

    @Test
    @DisplayName("중복되지 않은 새로운 이름이라면 변경에 성공한다")
    void changeLabelName_Success() {
        // given
        String currentName = "Work";
        String newName = "Life";
        String ownerId = "user1";

        given(label.getLabelName()).willReturn(currentName);
        given(label.getOwner()).willReturn(owner);
        given(owner.getId()).willReturn(ownerId);

        // 중복 아님
        given(labelRepository.existsByOwner_IdAndLabelName(ownerId, newName)).willReturn(false);

        // when
        labelNameService.changeLabelName(label, newName);

        // then
        verify(label).applyNewLabelName(newName);
    }

    @Test
    @DisplayName("이미 존재하는 이름으로 변경하려 하면 예외가 발생한다")
    void changeLabelName_Fail_Duplicate() {
        // given
        String currentName = "Work";
        String newName = "Life"; // 이미 있는 이름이라고 가정
        String ownerId = "user1";

        given(label.getLabelName()).willReturn(currentName);
        given(label.getOwner()).willReturn(owner);
        given(owner.getId()).willReturn(ownerId);

        // 중복됨!
        given(labelRepository.existsByOwner_IdAndLabelName(ownerId, newName)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> labelNameService.changeLabelName(label, newName))
                .isInstanceOf(LabelNameDuplicateException.class);

        verify(label, never()).applyNewLabelName(any());
    }
}