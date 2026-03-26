package net.ai.chatbot.dao;

import net.ai.chatbot.entity.TeamMembership;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMembershipDao extends MongoRepository<TeamMembership, String> {

    List<TeamMembership> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail);

    List<TeamMembership> findByMemberEmail(String memberEmail);

    Optional<TeamMembership> findByOwnerEmailAndMemberEmail(String ownerEmail, String memberEmail);
}
