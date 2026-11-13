package com.fairish.chat.controller;

import com.fairish.chat.dto.ContactRequestResponse;
import com.fairish.chat.dto.ContactResponse;
import com.fairish.chat.dto.SendContactRequestRequest;
import com.fairish.chat.exception.ResourceNotFoundException;
import com.fairish.chat.model.User;
import com.fairish.chat.repository.UserRepository;
import com.fairish.chat.security.UserPrincipal;
import com.fairish.chat.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;
    private final UserRepository userRepository;

    public ContactController(ContactService contactService, UserRepository userRepository) {
        this.contactService = contactService;
        this.userRepository = userRepository;
    }

    @PostMapping("/requests")
    public ResponseEntity<ContactRequestResponse> sendRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SendContactRequestRequest request) {
        ContactRequestResponse response = contactService.sendRequest(
                extractUserId(userDetails), request.getRecipientId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/requests/incoming")
    public ResponseEntity<List<ContactRequestResponse>> getIncomingRequests(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(contactService.getIncomingRequests(extractUserId(userDetails)));
    }

    @GetMapping("/requests/outgoing")
    public ResponseEntity<List<ContactRequestResponse>> getOutgoingRequests(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(contactService.getOutgoingRequests(extractUserId(userDetails)));
    }

    @PostMapping("/requests/{id}/accept")
    public ResponseEntity<ContactRequestResponse> acceptRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") String id) {
        return ResponseEntity.ok(contactService.acceptRequest(extractUserId(userDetails), id));
    }

    @PostMapping("/requests/{id}/decline")
    public ResponseEntity<ContactRequestResponse> declineRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") String id) {
        return ResponseEntity.ok(contactService.declineRequest(extractUserId(userDetails), id));
    }

    @DeleteMapping("/requests/{id}")
    public ResponseEntity<Void> cancelRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") String id) {
        contactService.cancelRequest(extractUserId(userDetails), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<ContactResponse>> getContacts(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(contactService.getContacts(extractUserId(userDetails)));
    }

    @DeleteMapping("/{contactUserId}")
    public ResponseEntity<Void> removeContact(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("contactUserId") String contactUserId) {
        contactService.removeContact(extractUserId(userDetails), contactUserId);
        return ResponseEntity.noContent().build();
    }

    private String extractUserId(UserDetails userDetails) {
        if (userDetails instanceof UserPrincipal principal) {
            return principal.getId();
        }
        return userRepository.findByUsernameIgnoreCase(userDetails.getUsername())
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));
    }
}
