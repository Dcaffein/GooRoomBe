package com.example.GooRoomBe.social.label.repository;

import com.example.GooRoomBe.social.label.domain.Label;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.List;

public interface LabelRepository extends Neo4jRepository<Label, String> {
    boolean existsByOwner_IdAndLabelName(String ownerId, String labelName);

    List<Label> findAllByOwner_Id(String ownerId);
}
