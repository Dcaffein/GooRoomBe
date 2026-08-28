package com.example.DunbarHorizon.trace.adapter.in.web;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Profile("local")
@RestController
@RequestMapping("/api/dev/traces")
@RequiredArgsConstructor
public class TraceDevController {

    private final EntityManager entityManager;

    /**
     * POST /api/dev/traces/seed?countTwoUserId={id}&countThreeUserId={id}
     *
     * countTwoUserId → count 2, countThreeUserId → count 3, isRevealed=false
     * 기존 레코드(revealed 포함)가 있어도 위 상태로 덮어씀.
     * lastVisitedAt을 어제로 세팅해 다음 호출에서 카운트 증가 가능하도록 함.
     * revealed 트리거: countTwoUserId가 1번 더 방문하면 양쪽 모두 3 이상 → reveal.
     */
    @PostMapping("/seed")
    @Transactional
    public ResponseEntity<Map<String, Object>> seedTrace(
            @RequestParam Long countTwoUserId,
            @RequestParam Long countThreeUserId) {

        Long minId = Math.min(countTwoUserId, countThreeUserId);
        Long maxId = Math.max(countTwoUserId, countThreeUserId);

        // minId = userA, maxId = userB
        int userACount = countTwoUserId.equals(minId) ? 2 : 3;
        int userBCount = countTwoUserId.equals(minId) ? 3 : 2;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusDays(1);

        entityManager.createNativeQuery("""
                INSERT INTO traces
                
    
                
                    
                    
                    
                    
                    
                    
                    
                    
                    (user_a_id, user_b_id, useracount, userbcount,
                     useralast_visited_at, userblast_visited_at,
                     is_revealed, revealed_at, last_traced_at, version,
                     created_at, updated_at)
                VALUES
                    (:minId, :maxId, :userACount, :userBCount,
                     :yesterday, :yesterday,
                     false, null, :yesterday, 0,
                     :now, :now)
                ON DUPLICATE KEY UPDATE
                    useracount           = :userACount,
                    userbcount           = :userBCount,
                    useralast_visited_at = :yesterday,
                    userblast_visited_at = :yesterday,
                    is_revealed          = false,
                    revealed_at          = null,
                    last_traced_at       = :yesterday,
                    version              = version + 1,
                    updated_at          = :now
                """)
                .setParameter("minId", minId)
                .setParameter("maxId", maxId)
                .setParameter("userACount", userACount)
                .setParameter("userBCount", userBCount)
                .setParameter("now", now)
                .setParameter("yesterday", yesterday)
                .executeUpdate();

        return ResponseEntity.ok(Map.of(
                "message", "Trace seeded",
                "countTwoUserId", countTwoUserId,
                "countTwoUserCount", 2,
                "countThreeUserId", countThreeUserId,
                "countThreeUserCount", 3,
                "isRevealed", false,
                "triggerReveal", "POST /api/v1/traces {\"targetId\":" + countThreeUserId + "} as user " + countTwoUserId
        ));
    }
}
