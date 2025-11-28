package com.example.GooRoomBe.social.label.repository;

import com.example.GooRoomBe.social.label.domain.Label;
import com.example.GooRoomBe.social.socialUser.SocialUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static com.example.GooRoomBe.social.common.SocialSchemaConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

@DataNeo4jTest
@Testcontainers
@ActiveProfiles("test")
class LabelRepositoryTest {

    @Container
    @ServiceConnection
    static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:5");

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private Neo4jClient neo4jClient;

    private final String OWNER_ID = "user-owner";
    private final String OTHER_ID = "user-other";
    private final String LABEL_UNIVERSITY = "university";
    private final String LABEL_CLUB = "club";

    @BeforeEach
    void setUp() {
        labelRepository.deleteAll();
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        // 데이터 셋업
        // Owner -> [OWNS] -> Label(university), Label(Family)
        // Other -> [OWNS] -> Label(club)
        String cypher = String.format("""
            CREATE (owner:%s {id: $ownerId})
            CREATE (other:%s {id: $otherId})
            
            CREATE (l1:%s {id: 'l1', labelName: $labelUniversity, exposure: true})
            CREATE (l2:%s {id: 'l2', labelName: $labelClub, exposure: false})
            CREATE (l3:%s {id: 'l3', labelName: 'Hobby', exposure: true})
            
            // 관계 설정 (User)-[:OWNS]->(Label)
            CREATE (owner)-[:%s]->(l1)
            CREATE (owner)-[:%s]->(l2)
            CREATE (other)-[:%s]->(l3)
            """,
                SOCIAL_USER, SOCIAL_USER,
                LABEL, LABEL, LABEL,
                OWNS, OWNS, OWNS
        );

        neo4jClient.query(cypher)
                .bindAll(Map.of(
                        "ownerId", OWNER_ID,
                        "otherId", OTHER_ID,
                        "labelUniversity", LABEL_UNIVERSITY,
                        "labelClub", LABEL_CLUB
                ))
                .run();
    }

    @Test
    @DisplayName("existsByOwner...: 소유자 ID와 라벨 이름으로 존재 여부를 정확히 판단한다")
    void existsByOwner_IdAndLabelName_Test() {
        // 존재하는 경우
        boolean exists = labelRepository.existsByOwner_IdAndLabelName(OWNER_ID, LABEL_UNIVERSITY);
        assertThat(exists).isTrue();

        // 이름은 맞지만 소유자가 다른 경우
        boolean notExistsOwner = labelRepository.existsByOwner_IdAndLabelName(OTHER_ID, LABEL_UNIVERSITY);
        assertThat(notExistsOwner).isFalse();

        // 소유자는 맞지만 이름이 없는 경우
        boolean notExistsName = labelRepository.existsByOwner_IdAndLabelName(OWNER_ID, "GhostLabel");
        assertThat(notExistsName).isFalse();
    }

    @Test
    @DisplayName("findAllByOwner: 특정 소유자의 라벨 목록만 조회한다")
    void findAllByOwner_Test() {
        // Given
        SocialUser owner = BeanUtils.instantiateClass(SocialUser.class);
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);

        // When
        List<Label> labels = labelRepository.findAllByOwner_Id(owner.getId());

        // Then
        // Owner는 University, Club 2개를 가지고 있음 (Other의 Hobby는 안 나와야 함)
        assertThat(labels).hasSize(2);
        assertThat(labels)
                .extracting(Label::getLabelName)
                .containsExactlyInAnyOrder(LABEL_UNIVERSITY, LABEL_CLUB);
    }
}