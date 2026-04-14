package com.masterchefcuts.controllers;

import com.masterchefcuts.model.Order;
import com.masterchefcuts.repositories.OrderRepository;
import com.masterchefcuts.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @PreAuthorize("hasRole('BUYER')")
    @GetMapping("/api/orders/my")
    public ResponseEntity<List<Order>> myOrders(@AuthenticationPrincipal String buyerId) {
        return ResponseEntity.ok(orderRepository.findByParticipantIdOrderByOrderDateDesc(buyerId));
    }

    /**
     * Get a single order by ID — buyer who placed it or farmer whose listing is in it.
     */
    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<Order> getOrderById(
            @AuthenticationPrincipal String requesterId,
            @PathVariable String orderId) {
        if (requesterId == null) return ResponseEntity.status(401).build();
        try {
            return ResponseEntity.ok(orderService.getOrderById(orderId, requesterId));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all orders for this farmer's listings
     */
    @PreAuthorize("hasRole('FARMER')")
    @GetMapping("/api/orders/farmer")
    public ResponseEntity<List<Order>> farmerOrders(@AuthenticationPrincipal String farmerId) {
        return ResponseEntity.ok(orderService.getFarmerOrders(farmerId));
    }

    /**
     * Update order status (farmer workflow)
     */
    @PreAuthorize("hasRole('FARMER')")
    @PatchMapping("/api/orders/{orderId}/status")
    public ResponseEntity<Order> updateStatus(
            @AuthenticationPrincipal String farmerId,
            @PathVariable String orderId,
            @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        if (newStatus == null || newStatus.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Order updated = orderService.updateOrderStatus(farmerId, orderId, newStatus);
        return ResponseEntity.ok(updated);
    }

    /**
     * Buyer confirms receipt of order
     */
    @PreAuthorize("hasRole('BUYER')")
    @PostMapping("/api/orders/{orderId}/confirm-receipt")
    public ResponseEntity<Order> confirmReceipt(
            @AuthenticationPrincipal String buyerId,
            @PathVariable String orderId) {
        Order updated = orderService.confirmReceipt(buyerId, orderId);
        return ResponseEntity.ok(updated);
    }
}
