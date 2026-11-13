package com.fairish.chat.service;

import com.fairish.chat.dto.ContactRequestResponse;
import com.fairish.chat.dto.ContactResponse;
import com.fairish.chat.exception.DuplicateResourceException;
import com.fairish.chat.exception.InvalidRequestStateException;
import com.fairish.chat.exception.ResourceNotFoundException;
import com.fairish.chat.model.ContactRequest;
import com.fairish.chat.model.ContactStatus;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.ContactRequestRepository;
import com.fairish.chat.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactRequestRepository contactRequestRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ContactService contactService;

    private User alice;
    private User bob;
    private User charlie;
    private ContactRequest pendingRequest;
    private ContactRequest acceptedRequest;

    @BeforeEach
    void setUp() {
        alice = User.builder().id("user-1").username("alice").email("alice@example.com").build();
        bob = User.builder().id("user-2").username("bob").email("bob@example.com").build();
        charlie = User.builder().id("user-3").username("charlie").email("charlie@example.com").build();

        pendingRequest = ContactRequest.builder()
                .id("req-1")
                .pairKey(ContactRequest.computePairKey("user-1", "user-2"))
                .requesterId("user-1")
                .recipientId("user-2")
                .status(ContactStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        acceptedRequest = ContactRequest.builder()
                .id("req-1")
                .pairKey(ContactRequest.computePairKey("user-1", "user-2"))
                .requesterId("user-1")
                .recipientId("user-2")
                .status(ContactStatus.ACCEPTED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .respondedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Pair key is deterministic regardless of argument order")
    void shouldComputeStablePairKey() {
        assertEquals(ContactRequest.computePairKey("user-1", "user-2"),
                ContactRequest.computePairKey("user-2", "user-1"));
        assertThrows(IllegalArgumentException.class,
                () -> ContactRequest.computePairKey("user-1", "user-1"));
    }

    @Test
    @DisplayName("Should create a pending contact request between two registered users")
    void shouldCreatePendingRequest() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(alice));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(bob));
        when(contactRequestRepository.findByPairKey(anyString())).thenReturn(Optional.empty());
        when(contactRequestRepository.save(any(ContactRequest.class))).thenAnswer(invocation -> {
            ContactRequest saved = invocation.getArgument(0);
            saved.setId("req-new");
            return saved;
        });

        ContactRequestResponse response = contactService.sendRequest("user-1", "user-2");

        assertNotNull(response);
        assertEquals("req-new", response.getId());
        assertEquals(ContactStatus.PENDING, response.getStatus());
        assertEquals("alice", response.getRequester().getUsername());
        assertEquals("bob", response.getRecipient().getUsername());
        assertNull(response.getRespondedAt());
        verify(contactRequestRepository).save(any(ContactRequest.class));
    }

    @Test
    @DisplayName("Should reject a contact request to oneself")
    void shouldRejectSelfRequest() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> contactService.sendRequest("user-1", "user-1"));

        assertTrue(ex.getMessage().contains("yourself"));
        verifyNoInteractions(contactRequestRepository);
    }

    @Test
    @DisplayName("Should reject a contact request to a non-existent recipient")
    void shouldRejectMissingRecipient() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(alice));
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> contactService.sendRequest("user-1", "missing"));

        verify(contactRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject a new request when users are already contacts")
    void shouldRejectWhenAlreadyContacts() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(alice));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(bob));
        when(contactRequestRepository.findByPairKey(anyString())).thenReturn(Optional.of(acceptedRequest));

        assertThrows(DuplicateResourceException.class,
                () -> contactService.sendRequest("user-1", "user-2"));

        verify(contactRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject a duplicate pending request in the same direction")
    void shouldRejectDuplicatePendingSameDirection() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(alice));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(bob));
        when(contactRequestRepository.findByPairKey(anyString())).thenReturn(Optional.of(pendingRequest));

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> contactService.sendRequest("user-1", "user-2"));

        assertTrue(ex.getMessage().contains("already pending"));
        verify(contactRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject a request when the other user already has one pending")
    void shouldRejectWhenOppositeRequestPending() {
        ContactRequest reverse = ContactRequest.builder()
                .id("req-1")
                .pairKey(ContactRequest.computePairKey("user-1", "user-2"))
                .requesterId("user-2")
                .recipientId("user-1")
                .status(ContactStatus.PENDING)
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(alice));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(bob));
        when(contactRequestRepository.findByPairKey(anyString())).thenReturn(Optional.of(reverse));

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> contactService.sendRequest("user-1", "user-2"));

        assertTrue(ex.getMessage().contains("already sent you"));
        verify(contactRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should re-open a declined request as pending")
    void shouldReopenDeclinedRequest() {
        ContactRequest declined = ContactRequest.builder()
                .id("req-1")
                .pairKey(ContactRequest.computePairKey("user-1", "user-2"))
                .requesterId("user-1")
                .recipientId("user-2")
                .status(ContactStatus.DECLINED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .respondedAt(Instant.now())
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(alice));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(bob));
        when(contactRequestRepository.findByPairKey(anyString())).thenReturn(Optional.of(declined));
        when(contactRequestRepository.save(any(ContactRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ContactRequestResponse response = contactService.sendRequest("user-1", "user-2");

        assertEquals(ContactStatus.PENDING, response.getStatus());
        assertNull(response.getRespondedAt());
        verify(contactRequestRepository).save(declined);
    }

    @Test
    @DisplayName("Should re-open a cancelled request in the opposite direction")
    void shouldReopenCancelledRequestWithNewDirection() {
        ContactRequest cancelled = ContactRequest.builder()
                .id("req-1")
                .pairKey(ContactRequest.computePairKey("user-1", "user-2"))
                .requesterId("user-2")
                .recipientId("user-1")
                .status(ContactStatus.CANCELLED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(alice));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(bob));
        when(contactRequestRepository.findByPairKey(anyString())).thenReturn(Optional.of(cancelled));
        when(contactRequestRepository.save(any(ContactRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ContactRequestResponse response = contactService.sendRequest("user-1", "user-2");

        assertEquals(ContactStatus.PENDING, response.getStatus());
        assertEquals("user-1", cancelled.getRequesterId());
        assertEquals("user-2", cancelled.getRecipientId());
    }

    @Test
    @DisplayName("Should recover gracefully when a concurrent request wins the race")
    void shouldRecoverFromDuplicateKeyOnCreate() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(alice));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(bob));
        when(contactRequestRepository.findByPairKey(anyString()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(acceptedRequest));
        when(contactRequestRepository.save(any(ContactRequest.class)))
                .thenThrow(new DuplicateKeyException("E11000 duplicate key"));

        assertThrows(DuplicateResourceException.class,
                () -> contactService.sendRequest("user-1", "user-2"));
    }

    @Test
    @DisplayName("Should accept a pending request as the recipient")
    void shouldAcceptPendingRequest() {
        when(contactRequestRepository.findById("req-1")).thenReturn(Optional.of(pendingRequest));
        when(contactRequestRepository.save(any(ContactRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(alice));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(bob));

        ContactRequestResponse response = contactService.acceptRequest("user-2", "req-1");

        assertEquals(ContactStatus.ACCEPTED, response.getStatus());
        assertNotNull(response.getRespondedAt());
        assertEquals("alice", response.getRequester().getUsername());
        assertEquals("bob", response.getRecipient().getUsername());
    }

    @Test
    @DisplayName("Should reject acceptance by anyone other than the recipient")
    void shouldRejectAcceptByNonRecipient() {
        when(contactRequestRepository.findById("req-1")).thenReturn(Optional.of(pendingRequest));

        assertThrows(AccessDeniedException.class,
                () -> contactService.acceptRequest("user-3", "req-1"));

        verify(contactRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject accepting a request that is no longer pending")
    void shouldRejectAcceptWhenNotPending() {
        when(contactRequestRepository.findById("req-1")).thenReturn(Optional.of(acceptedRequest));

        assertThrows(InvalidRequestStateException.class,
                () -> contactService.acceptRequest("user-2", "req-1"));

        verify(contactRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should decline a pending request as the recipient")
    void shouldDeclinePendingRequest() {
        when(contactRequestRepository.findById("req-1")).thenReturn(Optional.of(pendingRequest));
        when(contactRequestRepository.save(any(ContactRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(alice));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(bob));

        ContactRequestResponse response = contactService.declineRequest("user-2", "req-1");

        assertEquals(ContactStatus.DECLINED, response.getStatus());
        assertNotNull(response.getRespondedAt());
    }

    @Test
    @DisplayName("Should reject declining a request by anyone other than the recipient")
    void shouldRejectDeclineByNonRecipient() {
        when(contactRequestRepository.findById("req-1")).thenReturn(Optional.of(pendingRequest));

        assertThrows(AccessDeniedException.class,
                () -> contactService.declineRequest("user-1", "req-1"));

        verify(contactRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should cancel a pending request as the sender")
    void shouldCancelPendingRequest() {
        when(contactRequestRepository.findById("req-1")).thenReturn(Optional.of(pendingRequest));
        when(contactRequestRepository.save(any(ContactRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        contactService.cancelRequest("user-1", "req-1");

        assertEquals(ContactStatus.CANCELLED, pendingRequest.getStatus());
        assertNotNull(pendingRequest.getRespondedAt());
        verify(contactRequestRepository).save(pendingRequest);
    }

    @Test
    @DisplayName("Should reject cancelling a request sent by someone else")
    void shouldRejectCancelByNonRequester() {
        when(contactRequestRepository.findById("req-1")).thenReturn(Optional.of(pendingRequest));

        assertThrows(AccessDeniedException.class,
                () -> contactService.cancelRequest("user-2", "req-1"));

        verify(contactRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when accepting a non-existent request")
    void shouldThrowWhenRequestMissing() {
        when(contactRequestRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> contactService.acceptRequest("user-2", "missing"));
    }

    @Test
    @DisplayName("Should list only pending incoming requests with public user projections")
    void shouldListIncomingRequests() {
        when(contactRequestRepository.findByRecipientIdAndStatusOrderByCreatedAtDesc("user-2", ContactStatus.PENDING))
                .thenReturn(List.of(pendingRequest));
        when(userRepository.findAllById(anySet())).thenReturn(List.of(alice, bob));

        List<ContactRequestResponse> incoming = contactService.getIncomingRequests("user-2");

        assertEquals(1, incoming.size());
        assertEquals("alice", incoming.get(0).getRequester().getUsername());
        assertEquals(ContactStatus.PENDING, incoming.get(0).getStatus());
    }

    @Test
    @DisplayName("Should list outgoing pending requests")
    void shouldListOutgoingRequests() {
        when(contactRequestRepository.findByRequesterIdAndStatusOrderByCreatedAtDesc("user-1", ContactStatus.PENDING))
                .thenReturn(List.of(pendingRequest));
        when(userRepository.findAllById(anySet())).thenReturn(List.of(alice, bob));

        List<ContactRequestResponse> outgoing = contactService.getOutgoingRequests("user-1");

        assertEquals(1, outgoing.size());
        assertEquals("bob", outgoing.get(0).getRecipient().getUsername());
    }

    @Test
    @DisplayName("Should list accepted contacts for a user")
    void shouldListContacts() {
        when(contactRequestRepository.findByStatusAndParticipant(ContactStatus.ACCEPTED, "user-1"))
                .thenReturn(List.of(acceptedRequest));
        when(userRepository.findAllById(anySet())).thenReturn(List.of(bob));

        List<ContactResponse> contacts = contactService.getContacts("user-1");

        assertEquals(1, contacts.size());
        assertEquals("bob", contacts.get(0).getUser().getUsername());
        assertNotNull(contacts.get(0).getAcceptedAt());
    }

    @Test
    @DisplayName("Should remove an accepted contact")
    void shouldRemoveAcceptedContact() {
        when(contactRequestRepository.findByPairKey(anyString())).thenReturn(Optional.of(acceptedRequest));

        contactService.removeContact("user-1", "user-2");

        verify(contactRequestRepository).delete(acceptedRequest);
    }

    @Test
    @DisplayName("Should not remove a relationship that is not accepted")
    void shouldRejectRemovingNonContact() {
        when(contactRequestRepository.findByPairKey(anyString())).thenReturn(Optional.of(pendingRequest));

        assertThrows(ResourceNotFoundException.class,
                () -> contactService.removeContact("user-1", "user-2"));

        verify(contactRequestRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should report accepted contact status only for accepted pairs")
    void shouldReportAcceptedContacts() {
        when(contactRequestRepository.existsByPairKeyAndStatus(anyString(), eq(ContactStatus.ACCEPTED)))
                .thenReturn(true);

        assertTrue(contactService.areAcceptedContacts("user-1", "user-2"));
        assertFalse(contactService.areAcceptedContacts("user-1", "user-1"));
    }

    @Test
    @DisplayName("Should report false when no accepted relationship exists")
    void shouldReportNoContactWhenNotAccepted() {
        when(contactRequestRepository.existsByPairKeyAndStatus(anyString(), eq(ContactStatus.ACCEPTED)))
                .thenReturn(false);

        assertFalse(contactService.areAcceptedContacts("user-1", "user-3"));
    }

    @Test
    @DisplayName("Should treat missing user IDs as non-contacts without querying")
    void shouldTreatMissingIdsAsNonContacts() {
        assertFalse(contactService.areAcceptedContacts(null, "user-2"));
        assertFalse(contactService.areAcceptedContacts("user-1", " "));
        verifyNoInteractions(contactRequestRepository);
    }

    @Test
    @DisplayName("Should not leak another user's email through contact responses")
    void shouldNotExposeEmailInContactProjections() {
        when(contactRequestRepository.findByStatusAndParticipant(ContactStatus.ACCEPTED, "user-1"))
                .thenReturn(List.of(acceptedRequest));
        when(userRepository.findAllById(anySet())).thenReturn(List.of(bob));

        ContactResponse contact = contactService.getContacts("user-1").get(0);

        assertNotNull(contact.getUser());
        assertFalse(contact.getUser().getUsername().isBlank());
        // PublicUserResponse has no email field by design; the type itself is the guarantee.
    }

    @Test
    @DisplayName("Should not allow removing oneself as a contact")
    void shouldRejectRemovingSelf() {
        assertThrows(IllegalArgumentException.class,
                () -> contactService.removeContact("user-1", "user-1"));

        verifyNoInteractions(contactRequestRepository);
    }
}
