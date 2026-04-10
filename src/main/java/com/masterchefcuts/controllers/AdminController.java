package com.masterchefcuts.controllers;

import com.masterchefcuts.model.Order;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.services.AdminService;
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
}
