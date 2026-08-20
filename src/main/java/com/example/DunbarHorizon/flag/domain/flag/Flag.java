package com.example.DunbarHorizon.flag.domain.flag;

import com.example.DunbarHorizon.flag.domain.flag.event.FlagDeletedEvent;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagEncoreEvent;
import com.example.DunbarHorizon.flag.domain.flag.event.FlagMeetingChangedEvent;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagAuthorizationException;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagDeadlinePassedException;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagFullCapacityException;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagInvalidCapacityException;
import com.example.DunbarHorizon.flag.domain.flag.exception.FlagInvalidStatusException;
import com.example.DunbarHorizon.global.common.BaseTimeAggregateRoot;
import com.example.DunbarHorizon.global.common.SoftDeletable;
import com.example.DunbarHorizon.global.util.UuidUtil;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@DynamicUpdate
@Table(name = "flags", uniqueConstraints = {
        @UniqueConstraint(name = "uq_flags_parent_id", columnNames = "parent_id")
})
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Flag extends BaseTimeAggregateRoot implements SoftDeletable {

    public static final int EXPIRATION_THRESHOLD_HOURS = 24;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Getter
    private Long id;

    @Getter
    private Long hostId;

    @Getter
    private String title;

    @Column(columnDefinition = "TEXT") @Getter
    private String description;

    @Getter
    private Integer capacity;

    @Embedded @Getter
    private FlagSchedule schedule;

    @Getter
    @Column(columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID groupId;

    @Getter
    private Long parentId;

    @Getter
    @Column(name = "is_preserved")
    private boolean autoExpiryExempt = false;

    @Getter
    private LocalDateTime deletedAt;

    private Flag(Long hostId, String title, String description, Integer capacity,
                 FlagSchedule schedule, Long parentId, UUID groupId) {
        validateBasicInfo(hostId, title);
        validateCapacity(capacity);

        this.hostId = hostId;
        this.title = title;
        this.description = description;
        this.capacity = capacity;
        this.schedule = schedule;
        this.parentId = parentId;
        this.groupId = groupId;
    }

    public static Flag create(Long hostId, String title, String description,
                              Integer capacity, FlagSchedule schedule) {
        UUID newGroupId = UuidUtil.createV7();
        return new Flag(hostId, title, description, capacity, schedule, null, newGroupId);
    }

    Flag createEncore(Long hostId, LocalDateTime deadline, LocalDateTime start, LocalDateTime end) {
        if (!this.isEnded()) {
            throw new FlagInvalidStatusException("종료된 플래그만 앵코르를 생성할 수 있습니다.");
        }

        FlagSchedule newSchedule = FlagSchedule.of(deadline, start, end);
        Flag encoreFlag = new Flag(hostId, this.title, this.description,
                this.capacity, newSchedule, this.id, this.groupId);

        encoreFlag.registerEvent(new FlagEncoreEvent(this.id, hostId, this.title));
        return encoreFlag;
    }

    FlagParticipant participate(Long userId, int currentCount) {
        if (this.hostId.equals(userId)) {
            throw new FlagAuthorizationException("호스트는 참여자 명단에 등록될 수 없습니다.");
        }

        if (!this.isRecruiting()) {
            throw new FlagDeadlinePassedException();
        }

        if (this.capacity != null && currentCount >= this.capacity) {
            throw new FlagFullCapacityException();
        }

        return new FlagParticipant(this.id, userId);
    }

    void unparticipate(FlagParticipant participant, Long requesterId) {
        if (!participant.getParticipantId().equals(requesterId)) {
            throw new FlagAuthorizationException("본인의 참여만 취소할 수 있습니다.");
        }

        if (!this.calculateCurrentStatus().isBeforeActivity()) {
            throw new FlagInvalidStatusException("모집 기간이 종료된 이후에는 참여를 취소할 수 없습니다.");
        }
    }

    public void delete(Long requesterId) {
        validateHost(requesterId);
        if (isDeleted()) {
            throw new FlagInvalidStatusException("이미 삭제된 플래그입니다.");
        }
        softDelete();
        registerEvent(new FlagDeletedEvent(
                this.id,
                this.hostId,
                this.parentId,
                this.title,
                calculateCurrentStatus()
        ));
    }

    public void updateBasicInfo(Long requesterId, String title, String description) {
        validateHost(requesterId);
        validateNotEnded();

        if (title == null || title.isBlank()) {
            throw new FlagInvalidStatusException("타이틀 정보는 필수입니다.");
        }

        this.title = title;
        this.description = description;
    }

    public void updateCapacity(Long requesterId, Integer newCapacity, int currentParticipantCount) {
        validateHost(requesterId);
        validateNotEnded();
        validateNewCapacity(newCapacity, currentParticipantCount);

        this.capacity = newCapacity;
    }

    public void reschedule(Long requesterId, FlagSchedule newSchedule) {
        validateHost(requesterId);
        validateNotEnded();

        if (!calculateCurrentStatus().isBeforeActivity()) {
            throw new FlagInvalidStatusException("모임 시작 후에는 시간을 수정할 수 없습니다.");
        }

        if (isMeetingTimeChanged(this.schedule, newSchedule)) {
            registerEvent(new FlagMeetingChangedEvent(this.id, this.title,
                    newSchedule.getStartDateTime(), newSchedule.getEndDateTime()));
        }

        this.schedule = newSchedule;
    }

    public void closeRecruitment(Long requesterId) {
        validateHost(requesterId);
        if (!isRecruiting()) {
            throw new FlagInvalidStatusException("현재 모집 중인 상태가 아닙니다.");
        }

        this.schedule = this.schedule.withDeadline(LocalDateTime.now());
    }

    void updateAutoExpiryExempt(boolean value) {
        this.autoExpiryExempt = value;
    }

    public void severParentLink() {
        this.parentId = null;
    }

    public FlagStatus calculateCurrentStatus() {
        return schedule.calculateStatus(LocalDateTime.now());
    }

    public boolean isEnded() { return calculateCurrentStatus().isEnded(); }

    public boolean isRecruiting() { return calculateCurrentStatus().isRecruiting(); }

    public void grantInvitePermission(Long requesterId, FlagParticipant participant) {
        validateHost(requesterId);
        participant.grantInvitePermission();
    }

    public void revokeInvitePermission(Long requesterId, FlagParticipant participant) {
        validateHost(requesterId);
        participant.revokeInvitePermission();
    }

    private void validateHost(Long userId) {
        if (!this.hostId.equals(userId)) throw new FlagAuthorizationException("호스트 권한이 없습니다.");
    }

    private void validateNotEnded() {
        if (isEnded()) throw new FlagInvalidStatusException("종료된 플래그는 수정할 수 없습니다.");
    }

    private void validateBasicInfo(Long hostId, String title) {
        if (hostId == null) throw new FlagInvalidStatusException("호스트 정보는 필수입니다.");
        if (title == null || title.isBlank()) throw new FlagInvalidStatusException("타이틀 정보는 필수입니다.");
    }

    private void validateCapacity(Integer capacity) {
        if (capacity == null) return;
        if (capacity < 1) throw new FlagInvalidCapacityException("인원 제한은 최소 1명 이상이어야 합니다.");
    }

    private void validateNewCapacity(Integer newCapacity, int currentCount) {
        validateCapacity(newCapacity);

        if (newCapacity < currentCount) {
            throw new FlagInvalidStatusException(
                    String.format("현재 참여 인원(%d명)보다 적은 수로 정원을 변경할 수 없습니다.", currentCount)
            );
        }
    }

    private boolean isMeetingTimeChanged(FlagSchedule oldSchedule, FlagSchedule newSchedule) {
        return !oldSchedule.getStartDateTime().equals(newSchedule.getStartDateTime()) ||
                !oldSchedule.getEndDateTime().equals(newSchedule.getEndDateTime());
    }

    @Override
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    @Override
    public boolean isDeleted() {
        return deletedAt != null;
    }

}