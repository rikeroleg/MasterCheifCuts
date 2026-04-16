package com.masterchefcuts.services;

import com.masterchefcuts.dto.CommentResponse;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Comment;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.CommentRepository;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private ParticipantRepo participantRepo;

    @InjectMocks private CommentService commentService;

    private Participant author;
    private Listing listing;

    @BeforeEach
    void setUp() {
        author = Participant.builder()
                .id("user-1").firstName("Alice").lastName("Smith")
                .role(Role.BUYER).email("alice@test.com").password("pass")
                .street("1 Main").city("City").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        listing = Listing.builder()
                .id(1L).farmer(author).breed("Angus")
                .weightLbs(500).pricePerLb(10.0).zipCode("12345")
                .postedAt(LocalDateTime.now()).build();
    }

    // ── addComment ────────────────────────────────────────────────────────────

    @Test
    void addComment_success_savesAndReturnsDto() {
        Comment saved = Comment.builder()
                .id(1L).listing(listing).author(author)
                .body("Great listing!").createdAt(LocalDateTime.now()).build();

        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(participantRepo.findById("user-1")).thenReturn(Optional.of(author));
        when(commentRepository.save(any(Comment.class))).thenReturn(saved);

        CommentResponse response = commentService.addComment(1L, "user-1", "Great listing!");

        assertThat(response).isNotNull();
        assertThat(response.getBody()).isEqualTo("Great listing!");
        assertThat(response.getAuthorName()).isEqualTo("Alice Smith");
    }

    @Test
    void addComment_stripsScriptTag() {
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(participantRepo.findById("user-1")).thenReturn(Optional.of(author));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        commentService.addComment(1L, "user-1", "<script>alert('xss')</script>safe text");

        verify(commentRepository).save(argThat(c ->
                !c.getBody().contains("<script>") && c.getBody().contains("safe text")));
    }

    @Test
    void addComment_stripsAllHtmlTags() {
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(participantRepo.findById("user-1")).thenReturn(Optional.of(author));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        commentService.addComment(1L, "user-1", "<b>Bold</b> and <i>italic</i> text");

        verify(commentRepository).save(argThat(c -> c.getBody().equals("Bold and italic text")));
    }

    @Test
    void addComment_plainText_savedUnchanged() {
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(participantRepo.findById("user-1")).thenReturn(Optional.of(author));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        commentService.addComment(1L, "user-1", "Just plain text");

        verify(commentRepository).save(argThat(c -> c.getBody().equals("Just plain text")));
    }

    @Test
    void addComment_throwsWhenListingNotFound() {
        when(listingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.addComment(99L, "user-1", "body"))
                .hasMessageContaining("Listing not found");
    }

    @Test
    void addComment_throwsWhenUserNotFound() {
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(participantRepo.findById("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.addComment(1L, "user-1", "body"))
                .hasMessageContaining("User not found");
    }

    // ── deleteComment ─────────────────────────────────────────────────────────

    @Test
    void deleteComment_byOwner_succeeds() {
        Comment comment = Comment.builder().id(1L).listing(listing).author(author).body("text").build();
        when(commentRepository.findByIdWithAuthor(1L)).thenReturn(Optional.of(comment));

        commentService.deleteComment(1L, "user-1", "BUYER");

        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_byAdmin_succeeds() {
        Participant other = Participant.builder()
                .id("other-1").firstName("Bob").lastName("Jones")
                .role(Role.BUYER).email("bob@test.com").password("pass")
                .street("2 St").city("City").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();
        Comment comment = Comment.builder().id(1L).listing(listing).author(other).body("text").build();
        when(commentRepository.findByIdWithAuthor(1L)).thenReturn(Optional.of(comment));

        commentService.deleteComment(1L, "user-1", "ADMIN");

        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_notOwnerOrAdmin_throwsForbidden() {
        Participant other = Participant.builder()
                .id("other-1").firstName("Bob").lastName("Jones")
                .role(Role.BUYER).email("bob@test.com").password("pass")
                .street("2 St").city("City").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();
        Comment comment = Comment.builder().id(1L).listing(listing).author(other).body("text").build();
        when(commentRepository.findByIdWithAuthor(1L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.deleteComment(1L, "user-1", "BUYER"))
                .hasMessageContaining("Not allowed");
    }

    @Test
    void deleteComment_throwsWhenNotFound() {
        when(commentRepository.findByIdWithAuthor(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.deleteComment(99L, "user-1", "BUYER"))
                .hasMessageContaining("Comment not found");
    }

    // ── getComments ───────────────────────────────────────────────────────────

    @Test
    void getComments_returnsMappedList() {
        Comment c = Comment.builder().id(1L).listing(listing).author(author)
                .body("Nice listing!").createdAt(LocalDateTime.now()).build();
        when(commentRepository.findByListingIdWithAuthor(1L)).thenReturn(List.of(c));

        List<CommentResponse> result = commentService.getComments(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBody()).isEqualTo("Nice listing!");
        assertThat(result.get(0).getAuthorName()).isEqualTo("Alice Smith");
    }

    @Test
    void getComments_returnsEmptyList() {
        when(commentRepository.findByListingIdWithAuthor(99L)).thenReturn(List.of());

        assertThat(commentService.getComments(99L)).isEmpty();
    }

    // ── getCommentsPaged ──────────────────────────────────────────────────────

    @Test
    void getCommentsPaged_returnsPageMap() {
        Comment c = Comment.builder().id(1L).listing(listing).author(author)
                .body("Paged comment").createdAt(LocalDateTime.now()).build();
        Page<Comment> page = new PageImpl<>(List.of(c), PageRequest.of(0, 10), 1);
        when(commentRepository.findByListingIdWithAuthor(eq(1L), any(Pageable.class))).thenReturn(page);

        Map<String, Object> result = commentService.getCommentsPaged(1L, 0, 10);

        assertThat(result).containsKey("content");
        assertThat((List<?>) result.get("content")).hasSize(1);
        assertThat(result.get("page")).isEqualTo(0);
        assertThat(result.get("totalElements")).isEqualTo(1L);
        assertThat(result.get("hasNext")).isEqualTo(false);
    }

    @Test
    void getCommentsPaged_emptyPage_returnsEmptyContent() {
        Page<Comment> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(commentRepository.findByListingIdWithAuthor(eq(2L), any(Pageable.class))).thenReturn(emptyPage);

        Map<String, Object> result = commentService.getCommentsPaged(2L, 0, 10);

        assertThat((List<?>) result.get("content")).isEmpty();
        assertThat(result.get("totalElements")).isEqualTo(0L);
    }
}
