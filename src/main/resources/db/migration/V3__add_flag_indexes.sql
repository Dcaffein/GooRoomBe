-- flag 도메인 인덱스. 근거 쿼리는 harness/tasks/task-96-mysql-index-coverage.md §2.

-- 우선순위 1 — flag_participants
-- countByFlagIdIn이 목록 조회 네 경로에서 전부 호출되는데 flag_id에 인덱스가 없었다.
CREATE INDEX idx_flag_participants_flag_participant
    ON flag_participants (flag_id, participant_id);
CREATE INDEX idx_flag_participants_participant_flag
    ON flag_participants (participant_id, flag_id);

-- 우선순위 2 — 배치 쿼리. 범위를 좁히는 조건이 없어 flags 전체를 훑는다.
CREATE INDEX idx_flags_end_date_time ON flags (end_date_time);
CREATE INDEX idx_flags_deleted_at ON flags (deleted_at);

-- 우선순위 3
CREATE INDEX idx_flags_host_deadline ON flags (host_id, deadline);
CREATE INDEX idx_flag_invitations_invitee_created
    ON flag_invitations (invitee_id, created_at);
CREATE INDEX idx_flag_invitations_inviter_created
    ON flag_invitations (inviter_id, created_at);
CREATE INDEX idx_flag_invitations_flag_invitee
    ON flag_invitations (flag_id, invitee_id);
CREATE INDEX idx_flag_comments_flag_id ON flag_comments (flag_id);
CREATE INDEX idx_flag_comments_parent_id ON flag_comments (parent_id);
CREATE INDEX idx_flag_memorials_flag_id ON flag_memorials (flag_id);
