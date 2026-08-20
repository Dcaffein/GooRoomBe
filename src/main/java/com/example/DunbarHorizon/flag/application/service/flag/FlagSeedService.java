package com.example.DunbarHorizon.flag.application.service.flag;

import com.example.DunbarHorizon.flag.adapter.in.web.FlagSeedController.FlagSeedRequest;
import com.example.DunbarHorizon.flag.adapter.in.web.FlagSeedController.MemorialSeedItem;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import com.example.DunbarHorizon.global.util.UuidUtil;
import java.util.ArrayList;
import java.util.List;

@Profile("local")
@Service
@RequiredArgsConstructor
@Transactional
public class FlagSeedService {

    private final JdbcTemplate jdbc;

    public List<Long> seed(FlagSeedRequest request) {
        List<Long> ids = new ArrayList<>();
        for (var item : request.flags()) {
            Long flagId = insertFlag(request.hostUserId(), item);
            insertParticipants(flagId, item.participantUserIds());
            if (item.memorials() != null) {
                insertMemorials(flagId, item.memorials());
            }
            ids.add(flagId);
        }
        return ids;
    }

    private Long insertFlag(Long hostId, FlagSeedRequest.FlagSeedItem item) {
        String sql = """
                INSERT INTO flags
                  (host_id, title, description, capacity,
                   deadline, start_date_time, end_date_time,
                   group_id, parent_id, auto_expiry_exempt, created_at, updated_at)
                VALUES (?, ?, NULL, ?, ?, ?, ?, UNHEX(REPLACE(?, '-', '')), NULL, false, NOW(), NOW())
                """;

        LocalDateTime deadline = item.schedule().deadline() != null
                ? item.schedule().deadline()
                : item.schedule().startDateTime().minusMinutes(1);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, hostId);
            ps.setString(2, item.title());
            ps.setInt(3, item.capacity());
            ps.setObject(4, deadline);
            ps.setObject(5, item.schedule().startDateTime());
            ps.setObject(6, item.schedule().endDateTime());
            ps.setString(7, UuidUtil.createV7().toString());
            return ps;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    private void insertParticipants(Long flagId, List<Long> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) return;
        String sql = """
                INSERT INTO flag_participant (flag_id, participant_id, can_invite, created_at, updated_at)
                VALUES (?, ?, false, NOW(), NOW())
                """;
        for (Long pid : participantIds) {
            jdbc.update(sql, flagId, pid);
        }
    }

    private void insertMemorials(Long flagId, List<MemorialSeedItem> memorials) {
        String sql = """
                INSERT INTO flag_memorial (flag_id, writer_id, content, created_at, updated_at)
                VALUES (?, ?, ?, NOW(), NOW())
                """;
        for (MemorialSeedItem m : memorials) {
            jdbc.update(sql, flagId, m.writerUserId(), m.content());
        }
    }
}
