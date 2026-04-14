package com.masterchefcuts.services;

import com.masterchefcuts.dto.CommentResponse;
import com.masterchefcuts.model.Comment;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.CommentRepository;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final ListingRepository listingRepository;
    private final ParticipantRepo participantRepo;

    public List<CommentResponse> getComments(Long listingId) {
        return commentRepository.findByListingIdWithAuthor(listingId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getCommentsPaged(Long listingId, int page, int size) {
        Page<Comment> result = commentRepository.findByListingIdWithAuthor(listingId, PageRequest.of(page, size));
        return Map.of(
                "content", result.getContent().stream().map(this::toResponse).collect(Collectors.toList()),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "hasNext", result.hasNext()
        );
    }

    public CommentResponse addComment(Long listingId, String authorId, String body) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        Participant author = participantRepo.findById(authorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Comment comment = Comment.builder()
                .listing(listing)
                .author(author)
                .body(stripHtml(body))
                .build();

        return toResponse(commentRepository.save(comment));
    }

    public void deleteComment(Long commentId, String requesterId, String requesterRole) {
        Comment comment = commentRepository.findByIdWithAuthor(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(requesterRole);
        boolean isOwner = comment.getAuthor().getId().equals(requesterId);
        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to delete this comment");
        }

        commentRepository.delete(comment);
    }

    private String stripHtml(String input) {
        if (input == null) return "";
        return input.replaceAll("<[^>]*>", "").trim();
    }

    private CommentResponse toResponse(Comment c) {
        return CommentResponse.builder()
                .id(c.getId())
                .authorId(c.getAuthor().getId())
                .authorName(c.getAuthor().getFirstName() + " " + c.getAuthor().getLastName())
                .body(c.getBody())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
