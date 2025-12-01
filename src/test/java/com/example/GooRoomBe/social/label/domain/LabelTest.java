package com.example.GooRoomBe.social.label.domain;

import com.example.GooRoomBe.social.label.exception.InvalidLabelNameException;
import com.example.GooRoomBe.social.label.exception.LabelAuthorizationException;
import com.example.GooRoomBe.social.socialUser.SocialUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class LabelTest {

    @Mock
    private SocialUser owner;

    @Mock
    private SocialUser member1;

    @Mock
    private SocialUser member2;

    private Label label;
    private final String OWNER_ID = "owner-id";

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(owner.getId()).thenReturn(OWNER_ID);
        label = new Label(owner, "My Label", true);
    }

    @Test
    @DisplayName("라벨 생성 시 ID가 부여되고 멤버 목록은 비어있다")
    void constructor_ShouldInitializeCorrectly() {
        assertThat(label.getId()).isNotNull();
        assertThat(label.getLabelName()).isEqualTo("My Label");
        assertThat(label.isExposure()).isTrue();
        assertThat(label.getOwner()).isEqualTo(owner);
        assertThat(label.getMembers()).isEmpty();
    }

    @Test
    @DisplayName("멤버 추가 및 삭제가 정상적으로 동작한다")
    void member_AddAndRemove() {
        // 1. 추가
        label.applyNewMember(member1);
        assertThat(label.getMembers()).hasSize(1);
        assertThat(label.getMembers()).contains(member1);

        // 2. 중복 추가 시도 (Set 특성상 늘어나지 않아야 함)
        label.applyNewMember(member1);
        assertThat(label.getMembers()).hasSize(1);

        // 3. 삭제
        label.removeMember(member1);
        assertThat(label.getMembers()).isEmpty();
    }

    @Test
    @DisplayName("replaceMembers: 기존 멤버를 모두 지우고 새로운 멤버로 교체한다")
    void replaceMembers_ShouldClearAndAddAll() {
        // Given: 기존 멤버 존재
        label.applyNewMember(member1);

        // When: 새로운 멤버 셋으로 교체
        Set<SocialUser> newMembers = Set.of(member2);
        label.replaceMembers(newMembers);

        // Then: member1은 없고 member2만 있어야 함
        assertThat(label.getMembers()).hasSize(1);
        assertThat(label.getMembers()).contains(member2);
        assertThat(label.getMembers()).doesNotContain(member1);
    }

    @Test
    @DisplayName("라벨 이름 변경 성공")
    void updateLabelName_Success() {
        label.applyNewLabelName("New Name");
        assertThat(label.getLabelName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("공백이나 빈 문자열로 이름을 변경하려 하면 예외가 발생한다")
    void updateLabelName_Fail_Blank() {
        assertThatThrownBy(() -> label.applyNewLabelName(""))
                .isInstanceOf(InvalidLabelNameException.class);

        assertThatThrownBy(() -> label.applyNewLabelName("   "))
                .isInstanceOf(InvalidLabelNameException.class);
    }

    @Test
    @DisplayName("updateExposure: 소유자(Owner)만 노출 여부를 변경할 수 있다")
    void updateExposure_OwnerCanUpdate() {
        // When
        label.updateExposure(OWNER_ID, false);

        // Then
        assertThat(label.isExposure()).isFalse();
    }

    @Test
    @DisplayName("updateExposure: 소유자가 아닌 다른 사용자는 변경할 수 없다 (권한 예외)")
    void updateExposure_OthersCannotUpdate() {
        String otherUserId = "other-id";

        // When & Then
        assertThatThrownBy(() -> label.updateExposure(otherUserId, false))
                .isInstanceOf(LabelAuthorizationException.class)
                .hasMessageContaining(otherUserId); // 에러 메시지에 ID가 포함되었는지 확인
    }
}