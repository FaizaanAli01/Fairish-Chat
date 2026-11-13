package com.fairish.chat.service;

import com.fairish.chat.dto.ContactRequestResponse;
import com.fairish.chat.dto.ContactResponse;
import com.fairish.chat.dto.PublicUserResponse;
import com.fairish.chat.exception.DuplicateResourceException;
import com.fairish.chat.exception.InvalidRequestStateException;
import com.fairish.chat.exception.ResourceNotFoundException;
import com.fairish.chat.model.ContactRequest;
import com.fairish.chat.model.ContactStatus;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.ContactRequestRepository;
import com.fairish.chat.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Contact request lifecycle and contact relationship queries.
 *
 * <p>Contact state is stored in a single {@link ContactRequest} document per
 * unordered user pair. An accepted request is the authoritative definition of
 * "these two users are contacts"; conversation participation is intentionally
 * not consulted here.</p>
 */
@Service
public class ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactService.class);

    private final ContactRequestRepository contactRequestRepository;
    private final UserRepository userRepository;

    public ContactService(ContactRequestRepository contactRequestRepository, UserRepository userRepository) {
        this.contactRequestRepository = contactRequestRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ContactRequestResponse sendRequest(String currentUserId, String recipientId) {
        if (!StringUtils.hasText(recipientId)) {
            throw new IllegalArgumentException("Recipient ID must not be blank");
        }

        String trimmedRecipientId = recipientId.trim();
        if (currentUserId.equals(trimmedRecipientId)) {
            throw new IllegalArgumentException("You cannot send a contact request to yourself");
        }

        User requester = requireUser(currentUserId);
        User recipient = userRepository.findById(trimmedRecipientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Recipient user not found with id: " + trimmedRecipientId));

        String pairKey = ContactRequest.computePairKey(currentUserId, trimmedRecipientId);
        Optional<ContactRequest> existing = contactRequestRepository.findByPairKey(pairKey);

        if (existing.isEmpty()) {
            Instant now = Instant.now();
            ContactRequest request = ContactRequest.builder()
                    .pairKey(pairKey)
                    .requesterId(currentUserId)
                    .recipientId(trimmedRecipientId)
                    .status(ContactStatus.PENDING)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            try {
                ContactRequest saved = contactRequestRepository.save(request);
                log.info("Contact request created from [{}] to [{}]", currentUserId, trimmedRecipientId);
                return ContactRequestResponse.from(saved, requester, recipient);
            } catch (DuplicateKeyException ex) {
                // A concurrent request for the same pair won the race; fall through
                // and evaluate the existing document instead of failing.
                existing = contactRequestRepository.findByPairKey(pairKey);
                if (existing.isEmpty()) {
                    throw ex;
                }
            }
        }

        ContactRequest request = existing.get();
        switch (request.getStatus()) {
            case ACCEPTED -> throw new DuplicateResourceException("You are already contacts with this user");
            case PENDING -> {
                if (currentUserId.equals(request.getRequesterId())) {
                    throw new DuplicateResourceException("A contact request to this user is already pending");
                }
                throw new DuplicateResourceException("This user has already sent you a contact request");
            }
            case DECLINED, CANCELLED -> {
                // Re-opening a closed request keeps the pair's history in one document.
                Instant now = Instant.now();
                request.setRequesterId(currentUserId);
                request.setRecipientId(trimmedRecipientId);
                request.setStatus(ContactStatus.PENDING);
                request.setRespondedAt(null);
                request.setUpdatedAt(now);
                ContactRequest saved = contactRequestRepository.save(request);
                log.info("Contact request re-opened from [{}] to [{}]", currentUserId, trimmedRecipientId);
                return ContactRequestResponse.from(saved, requester, recipient);
            }
            default -> throw new IllegalStateException("Unsupported contact status: " + request.getStatus());
        }
    }

    @Transactional
    public ContactRequestResponse acceptRequest(String currentUserId, String requestId) {
        ContactRequest request = requireRequest(requestId);
        requireRecipient(request, currentUserId, "accept");
        requirePending(request, "accepted");

        Instant now = Instant.now();
        request.setStatus(ContactStatus.ACCEPTED);
        request.setRespondedAt(now);
        request.setUpdatedAt(now);
        ContactRequest saved = contactRequestRepository.save(request);

        log.info("Contact request [{}] accepted by [{}]", requestId, currentUserId);
        return toResponseWithLoadedUsers(saved);
    }

    @Transactional
    public ContactRequestResponse declineRequest(String currentUserId, String requestId) {
        ContactRequest request = requireRequest(requestId);
        requireRecipient(request, currentUserId, "decline");
        requirePending(request, "declined");

        Instant now = Instant.now();
        request.setStatus(ContactStatus.DECLINED);
        request.setRespondedAt(now);
        request.setUpdatedAt(now);
        ContactRequest saved = contactRequestRepository.save(request);

        log.info("Contact request [{}] declined by [{}]", requestId, currentUserId);
        return toResponseWithLoadedUsers(saved);
    }

    @Transactional
    public void cancelRequest(String currentUserId, String requestId) {
        ContactRequest request = requireRequest(requestId);

        if (!currentUserId.equals(request.getRequesterId())) {
            log.warn("User [{}] attempted to cancel contact request [{}] they did not send",
                    currentUserId, requestId);
            throw new AccessDeniedException("Only the sender can cancel this contact request");
        }
        requirePending(request, "cancelled");

        Instant now = Instant.now();
        request.setStatus(ContactStatus.CANCELLED);
        request.setRespondedAt(now);
        request.setUpdatedAt(now);
        contactRequestRepository.save(request);

        log.info("Contact request [{}] cancelled by [{}]", requestId, currentUserId);
    }

    @Transactional(readOnly = true)
    public List<ContactRequestResponse> getIncomingRequests(String currentUserId) {
        return toResponses(contactRequestRepository
                .findByRecipientIdAndStatusOrderByCreatedAtDesc(currentUserId, ContactStatus.PENDING));
    }

    @Transactional(readOnly = true)
    public List<ContactRequestResponse> getOutgoingRequests(String currentUserId) {
        return toResponses(contactRequestRepository
                .findByRequesterIdAndStatusOrderByCreatedAtDesc(currentUserId, ContactStatus.PENDING));
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> getContacts(String currentUserId) {
        List<ContactRequest> accepted = contactRequestRepository
                .findByStatusAndParticipant(ContactStatus.ACCEPTED, currentUserId);

        Set<String> otherUserIds = accepted.stream()
                .map(request -> request.getOtherParticipantId(currentUserId))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, User> users = userRepository.findAllById(otherUserIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        return accepted.stream()
                .map(request -> {
                    String otherId = request.getOtherParticipantId(currentUserId);
                    User otherUser = otherId != null ? users.get(otherId) : null;
                    if (otherUser == null) {
                        return null;
                    }
                    return ContactResponse.builder()
                            .user(PublicUserResponse.fromUser(otherUser))
                            .acceptedAt(request.getRespondedAt() != null
                                    ? request.getRespondedAt()
                                    : request.getUpdatedAt())
                            .build();
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ContactResponse::getAcceptedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeContact(String currentUserId, String otherUserId) {
        if (!StringUtils.hasText(otherUserId)) {
            throw new IllegalArgumentException("Contact user ID must not be blank");
        }
        String trimmedOtherId = otherUserId.trim();
        if (currentUserId.equals(trimmedOtherId)) {
            throw new IllegalArgumentException("You cannot remove yourself as a contact");
        }

        String pairKey = ContactRequest.computePairKey(currentUserId, trimmedOtherId);
        ContactRequest request = contactRequestRepository.findByPairKey(pairKey)
                .filter(existing -> existing.getStatus() == ContactStatus.ACCEPTED)
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found"));

        if (!request.hasParticipant(currentUserId)) {
            log.warn("User [{}] attempted to remove a contact relationship they are not part of",
                    currentUserId);
            throw new AccessDeniedException("You are not part of this contact relationship");
        }

        contactRequestRepository.delete(request);
        log.info("Contact relationship between [{}] and [{}] removed by [{}]",
                request.getRequesterId(), request.getRecipientId(), currentUserId);
    }

    /**
     * Returns {@code true} only when both users are parties to an accepted
     * contact request. This is the single authorization primitive used for
     * contact-gated resources such as avatars.
     */
    @Transactional(readOnly = true)
    public boolean areAcceptedContacts(String userAId, String userBId) {
        if (!StringUtils.hasText(userAId) || !StringUtils.hasText(userBId) || userAId.equals(userBId)) {
            return false;
        }
        String pairKey = ContactRequest.computePairKey(userAId, userBId);
        return contactRequestRepository.existsByPairKeyAndStatus(pairKey, ContactStatus.ACCEPTED);
    }

    private ContactRequest requireRequest(String requestId) {
        return contactRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Contact request not found with id: " + requestId));
    }

    private void requireRecipient(ContactRequest request, String currentUserId, String action) {
        if (!currentUserId.equals(request.getRecipientId())) {
            log.warn("User [{}] attempted to {} contact request [{}] addressed to another user",
                    currentUserId, action, request.getId());
            throw new AccessDeniedException("Only the recipient can " + action + " this contact request");
        }
    }

    private void requirePending(ContactRequest request, String action) {
        if (request.getStatus() != ContactStatus.PENDING) {
            throw new InvalidRequestStateException(
                    "Only pending contact requests can be " + action);
        }
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    private ContactRequestResponse toResponseWithLoadedUsers(ContactRequest request) {
        User requester = userRepository.findById(request.getRequesterId()).orElse(null);
        User recipient = userRepository.findById(request.getRecipientId()).orElse(null);
        return ContactRequestResponse.from(request, requester, recipient);
    }

    private List<ContactRequestResponse> toResponses(List<ContactRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }

        Set<String> userIds = requests.stream()
                .flatMap(request -> Stream.of(request.getRequesterId(), request.getRecipientId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        return requests.stream()
                .map(request -> ContactRequestResponse.from(request,
                        users.get(request.getRequesterId()),
                        users.get(request.getRecipientId())))
                .collect(Collectors.toList());
    }
}
