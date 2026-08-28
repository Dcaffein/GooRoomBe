package com.example.DunbarHorizon.social.application;

import com.example.DunbarHorizon.social.application.service.LabelService;
import com.example.DunbarHorizon.social.application.dto.result.LabelMemberResult;
import com.example.DunbarHorizon.social.application.dto.result.LabelResult;
import com.example.DunbarHorizon.social.domain.label.exception.LabelAuthorizationException;
import com.example.DunbarHorizon.social.domain.socialUser.SocialUser;
import com.example.DunbarHorizon.social.domain.socialUser.repository.SocialUserRepository;
import com.example.DunbarHorizon.social.domain.label.Label;
import com.example.DunbarHorizon.social.domain.label.LabelFactory;
import com.example.DunbarHorizon.social.domain.label.repository.LabelRepository;
import com.example.DunbarHorizon.social.domain.label.LabelMemberEnroller;
import com.example.DunbarHorizon.social.domain.label.LabelNamePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabelServiceTest {

    @InjectMocks
    private LabelService labelService;

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private SocialUserRepository socialUserRepository;

    @Mock
    private LabelNamePolicy labelNamePolicy;

    @Mock
    private LabelMemberEnroller labelMemberEnroller;

    @Mock
    private LabelFactory labelFactory;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Long currentUserId = 1L;
    private final String labelId = "label-uuid-123";
    private Label mockLabel;

    @BeforeEach
    void setUp() {
        mockLabel = mock(Label.class);
    }

    @Test
    @DisplayName("라벨 생성 시 이름 중복 확인과 소유자 조회를 거쳐 저장한다")
    void createLabel_Success() {
        // given
        String name = "친구들";
        SocialUser mockOwner = mock(SocialUser.class);

        given(socialUserRepository.findById(currentUserId)).willReturn(Optional.of(mockOwner));
        given(labelFactory.create(any(), anyString())).willReturn(mock(Label.class));
        given(labelRepository.save(any(Label.class))).willReturn(mockLabel);

        // when
        Label result = labelService.createLabel(currentUserId, name);

        // then
        verify(labelNamePolicy).validateLabelNameUniqueness(currentUserId, name);
        verify(socialUserRepository).findById(currentUserId);
        verify(labelRepository).save(any(Label.class));
        assertThat(result).isEqualTo(mockLabel);
    }

    @Test
    @DisplayName("라벨 삭제 시 소유자 확인 후 삭제를 수행한다")
    void deleteLabel_Success() {
        // given
        SocialUser owner = mock(SocialUser.class);
        given(owner.getId()).willReturn(currentUserId);

        given(labelRepository.findById(labelId)).willReturn(Optional.of(mockLabel));
        given(mockLabel.getOwner()).willReturn(owner);

        // when
        labelService.deleteLabel(currentUserId, labelId);

        // then
        verify(labelRepository).delete(mockLabel);
    }

    @Test
    @DisplayName("라벨에 멤버 추가 시 도메인 서비스에 ID를 전달하고 저장한다")
    void addMemberToLabel_Success() {
        // given
        Long newMemberId = 2L;
        SocialUser owner = mock(SocialUser.class);
        given(owner.getId()).willReturn(currentUserId);

        given(labelRepository.findById(labelId)).willReturn(Optional.of(mockLabel));
        given(mockLabel.getOwner()).willReturn(owner);

        // when
        labelService.addMemberToLabel(currentUserId, labelId, newMemberId);

        // then
        verify(labelMemberEnroller).addNewMember(mockLabel, newMemberId);
        verify(labelRepository).save(mockLabel);
    }

    @Test
    @DisplayName("라벨 정보 업데이트 시 이름이 있으면 반영한다")
    void updateLabel_PartialUpdate() {
        // given
        String newName = "새이름";

        given(labelRepository.findById(labelId)).willReturn(Optional.of(mockLabel));

        // when
        labelService.updateLabel(labelId, currentUserId, newName);

        // then
        verify(labelNamePolicy).changeLabelName(mockLabel, newName);
        verify(labelRepository).save(mockLabel);
    }

    @Test
    @DisplayName("소유자가 라벨을 단건 조회하면 LabelResult를 반환한다")
    void getLabelById_Success() {
        // given
        SocialUser owner = mock(SocialUser.class);
        given(owner.getId()).willReturn(currentUserId);

        given(labelRepository.findById(labelId)).willReturn(Optional.of(mockLabel));
        given(mockLabel.getOwner()).willReturn(owner);
        given(mockLabel.getId()).willReturn(labelId);
        given(mockLabel.getLabelName()).willReturn("친구들");
        given(mockLabel.getMembers()).willReturn(Set.of());

        // when
        LabelResult result = labelService.getLabelById(currentUserId, labelId);

        // then
        assertThat(result.id()).isEqualTo(labelId);
        assertThat(result.labelName()).isEqualTo("친구들");
        assertThat(result.memberCount()).isZero();
    }

    @Test
    @DisplayName("특정 멤버가 속한 내 라벨만 조회한다")
    void getLabelsByMember_Success() {
        // given
        Long memberId = 2L;
        given(labelRepository.findLabelsByOwnerAndMember(currentUserId, memberId))
                .willReturn(List.of(mockLabel));
        given(mockLabel.getId()).willReturn(labelId);
        given(mockLabel.getLabelName()).willReturn("친구들");
        given(mockLabel.getMembers()).willReturn(Set.of());

        // when
        List<LabelResult> result = labelService.getLabelsByMember(currentUserId, memberId);

        // then
        assertThat(result).containsExactly(new LabelResult(labelId, "친구들", 0));
    }

    @Test
    @DisplayName("소유자가 아닌 유저가 라벨을 조회하면 LabelAuthorizationException이 발생한다")
    void getLabelById_ByNonOwner_Fail() {
        // given
        Long nonOwnerId = 99L;
        SocialUser owner = mock(SocialUser.class);
        given(owner.getId()).willReturn(currentUserId);

        given(labelRepository.findById(labelId)).willReturn(Optional.of(mockLabel));
        given(mockLabel.getOwner()).willReturn(owner);

        // when & then
        assertThatThrownBy(() -> labelService.getLabelById(nonOwnerId, labelId))
                .isInstanceOf(LabelAuthorizationException.class);
    }

    @Test
    @DisplayName("소유자가 라벨 멤버 목록을 조회하면 멤버 결과 리스트를 반환한다")
    void getLabelMembers_Success() {
        // given
        SocialUser owner = mock(SocialUser.class);
        given(owner.getId()).willReturn(currentUserId);

        given(labelRepository.findById(labelId)).willReturn(Optional.of(mockLabel));
        given(mockLabel.getOwner()).willReturn(owner);
        given(mockLabel.getMembers()).willReturn(Set.of());

        // when
        List<LabelMemberResult> result = labelService.getLabelMembers(currentUserId, labelId);

        // then
        assertThat(result).isNotNull().isEmpty();
    }
}
