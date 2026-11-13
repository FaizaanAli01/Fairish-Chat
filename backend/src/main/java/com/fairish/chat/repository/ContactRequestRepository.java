package com.fairish.chat.repository;

import com.fairish.chat.model.ContactRequest;
import com.fairish.chat.model.ContactStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactRequestRepository extends MongoRepository<ContactRequest, String> {

    Optional<ContactRequest> findByPairKey(String pairKey);

    boolean existsByPairKeyAndStatus(String pairKey, ContactStatus status);

    List<ContactRequest> findByRecipientIdAndStatusOrderByCreatedAtDesc(String recipientId, ContactStatus status);

    List<ContactRequest> findByRequesterIdAndStatusOrderByCreatedAtDesc(String requesterId, ContactStatus status);

    @Query("{ 'status': ?0, $or: [ { 'requesterId': ?1 }, { 'recipientId': ?1 } ] }")
    List<ContactRequest> findByStatusAndParticipant(ContactStatus status, String userId);
}
