package com.example.GooRoomBe.social.label.domain;

import com.example.GooRoomBe.social.label.exception.InvalidLabelNameException;
import com.example.GooRoomBe.social.label.exception.LabelAuthorizationException;
import com.example.GooRoomBe.social.socialUser.SocialUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.example.GooRoomBe.social.common.SocialSchemaConstants.HAS_MEMBER;
import static com.example.GooRoomBe.social.common.SocialSchemaConstants.OWNS;


@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Node("Label")
public class Label {
    @Id
    private String id;

    private String labelName;

    private boolean exposure;

    @Relationship(type = OWNS, direction = Relationship.Direction.INCOMING)
    private SocialUser owner;

    @Relationship(type = HAS_MEMBER, direction = Relationship.Direction.OUTGOING)
    private Set<SocialUser> members = new HashSet<>();

    public Label(SocialUser owner, String labelName, boolean exposure) {
        this.id = UUID.randomUUID().toString();
        this.owner = owner;
        this.labelName = labelName;
        this.exposure = exposure;
    }

    public void applyNewMember(SocialUser newMember) {
        this.members.add(newMember);
    }

    public void removeMember(SocialUser memberToRemove) {
        members.remove(memberToRemove);
    }

    public void replaceMembers(Set<SocialUser> validMembers) {
        this.members.clear();
        this.members.addAll(validMembers);
    }

    public void applyNewLabelName(String newLabelName) {
        if (newLabelName.isBlank()) {
            throw new InvalidLabelNameException();
        }
        this.labelName = newLabelName;
    }

    public void updateExposure(String currentUserId, boolean exposure) {
        if (!currentUserId.equals(this.owner.getId())) {
            throw new LabelAuthorizationException( "해당 유저는 Label에 대한 권한이 없습니다 : "+ currentUserId);
        }
        this.exposure = exposure;
    }
}