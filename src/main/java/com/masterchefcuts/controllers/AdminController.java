package com.masterchefcuts.controllers;

import com.masterchefcuts.dto.ReviewResponse;
import com.masterchefcuts.model.Order;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.services.AdminService;
import com.masterchefcuts.services.AdminSettingsService;
import com.masterchefcuts.services.ReviewService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ReviewService reviewService;
    private final AdminSettingsService adminSettingsService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/users")
    public ResponseEntity<List<Participant>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/api/admin/users/{id}/approve")
    public ResponseEntity<Participant> approve(@PathVariable String id) {
        return ResponseEntity.ok(adminService.setApproved(id, true));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/api/admin/users/{id}/reject")
    public ResponseEntity<Participant> reject(@PathVariable String id) {
        return ResponseEntity.ok(adminService.setApproved(id, false));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/api/admin/listings/{id}")
    public ResponseEntity<Void> deleteListing(@PathVariable Long id) {
        adminService.deleteListing(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/users/{id}")
    public ResponseEntity<Map<String, Object>> getUserDetail(@PathVariable String id) {
        return ResponseEntity.ok(adminService.getUserDetail(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/orders")
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(adminService.getAllOrders());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/admin/orders/{id}/refund")
    public ResponseEntity<Order> refundOrder(
            @PathVariable String id,
            @RequestBody Map<String, String> body) throws StripeException {
        String reason = body != null ? body.getOrDefault("reason", "Admin-initiated refund") : "Admin-initiated refund";
        return ResponseEntity.ok(adminService.issueRefund(id, reason));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/financials/summary")
    public ResponseEntity<Map<String, Object>> getFinancialSummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(adminService.getFinancialSummary(from, to));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/financials/orders")
    public ResponseEntity<List<Map<String, Object>>> getFinancialOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(adminService.getFinancialOrders(status, from, to));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/comments")
    public ResponseEntity<Map<String, Object>> getComments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(adminService.getCommentsPaged(page, size));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/api/admin/comments/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id) {
        adminService.adminDeleteComment(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/reviews")
    public ResponseEntity<Map<String, Object>> getReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(reviewService.getAllForAdmin(page, size));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/api/admin/reviews/{id}/featured")
    public ResponseEntity<ReviewResponse> toggleFeatured(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.toggleFeatured(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/api/admin/reviews/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
        reviewService.adminDeleteReview(id);
        return ResponseEntity.noContent().build();
    }

    // ── Admin Settings ────────────────────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/settings")
    public ResponseEntity<Map<String, Object>> getSettings() {
        return ResponseEntity.ok(adminSettingsService.toMap());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/admin/settings/order-notifications/toggle")
    public ResponseEntity<Map<String, Object>> toggleOrderNotifications() {
        adminSettingsService.toggleAdminOrderNotifications();
        return ResponseEntity.ok(adminSettingsService.toMap());
    }
}
