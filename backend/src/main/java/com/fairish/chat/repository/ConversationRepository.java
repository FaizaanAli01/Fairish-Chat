package com.fairish.chat.repository;

import com.fairish.chat.model.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    Optional<Conversation> findByParticipantKey(String participantKey);

    boolean existsByParticipantKey(String participantKey);

    List<Conversation> findByParticipantIdsContainingOrderByUpdatedAtDesc(String participantId);
}
