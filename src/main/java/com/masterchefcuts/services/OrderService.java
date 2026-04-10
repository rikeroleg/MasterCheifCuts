package com.masterchefcuts.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.enums.NotificationType;
import com.masterchefcuts.enums.OrderStatus;
import com.masterchefcuts.model.Order;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.OrderRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;
    private final ParticipantRepo participantRepo;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    /**
     * Get all orders for listings owned by this farmer
     */
    public List<Order> getFarmerOrders(String farmerId) {
        List<Order> allOrders = orderRepository.findAll();
        
        return allOrders.stream()
                .filter(order -> {
                    // Only show paid or later status orders
                    String status = order.getStatus();
                    if (status == null) return false;
                    Set<String> visibleStatuses = Set.of(
                        "PAID", "ACCEPTED", "PROCESSING", "READY", "COMPLETED"
                    );
                    if (!visibleStatuses.contains(status.toUpperCase())) return false;
                    
                    // Check if farmer owns any listing in this order
                    return farmerOwnsOrderListings(farmerId, order);
                })
                .sorted((a, b) -> {
                    // Sort by status priority, then by date
                    int priorityA = getStatusPriority(a.getStatus());
                    int priorityB = getStatusPriority(b.getStatus());
                    if (priorityA != priorityB) return priorityA - priorityB;
                    return b.getOrderDate().compareTo(a.getOrderDate());
                })
                .collect(Collectors.toList());
    }

    private int getStatusPriority(String status) {
        if (status == null) return 99;
        return switch (status.toUpperCase()) {
            case "PAID" -> 1;  // Needs attention first
            case "ACCEPTED" -> 2;
            case "PROCESSING" -> 3;
            case "READY" -> 4;
            case "COMPLETED" -> 5;
            default -> 99;
        };
    }

    private boolean farmerOwnsOrderListings(String farmerId, Order order) {
        try {
            String itemsJson = order.getItems();
            if (itemsJson == null || itemsJson.isBlank()) return false;

            List<Map<String, Object>> items = objectMapper.readValue(itemsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            return items.stream()
                    .map(i -> i.get("listingId"))
                    .filter(Objects::nonNull)
                    .map(id -> Long.parseLong(String.valueOf(id)))
                    .anyMatch(listingId -> listingRepository.findById(listingId)
                            .map(l -> farmerId.equals(l.getFarmer().getId()))
                            .orElse(false));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Update order status with validation and notifications
     */
    @Transactional
    public Order updateOrderStatus(String farmerId, String orderId, String newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // Verify farmer owns this order's listings
        if (!farmerOwnsOrderListings(farmerId, order)) {
            throw new IllegalArgumentException("You are not the seller for this order");
        }

        String currentStatus = order.getStatus();
        OrderStatus targetStatus;
        try {
            targetStatus = OrderStatus.valueOf(newStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + newStatus);
        }

        // Validate status transition
        if (!isValidTransition(currentStatus, targetStatus)) {
            throw new IllegalArgumentException(
                    "Cannot transition from " + currentStatus + " to " + targetStatus);
        }

        order.setStatus(targetStatus.name());
        order.setNotes(appendNote(order.getNotes(), 
                "Status changed to " + targetStatus.name() + " by farmer at " + LocalDateTime.now()));
        orderRepository.save(order);

        auditService.log(farmerId, "ORDER_STATUS_CHANGED", orderId);

        // Send notification to buyer
        notifyBuyerOfStatusChange(order, targetStatus);

        return order;
    }

    private boolean isValidTransition(String current, OrderStatus target) {
        if (current == null) return false;
        
        return switch (current.toUpperCase()) {
            case "PAID" -> target == OrderStatus.ACCEPTED;
            case "ACCEPTED" -> target == OrderStatus.PROCESSING;
            case "PROCESSING" -> target == OrderStatus.READY;
            case "READY" -> target == OrderStatus.COMPLETED;
            default -> false;
        };
    }

    private void notifyBuyerOfStatusChange(Order order, OrderStatus newStatus) {
        if (order.getParticipantId() == null) return;

        Participant buyer = participantRepo.findById(order.getParticipantId()).orElse(null);
        if (buyer == null) return;

        String icon;
        String title;
        String body;
        NotificationType type;

        switch (newStatus) {
            case ACCEPTED -> {
                icon = "✅";
                title = "Order Accepted";
                body = "Your order has been accepted by the farmer and will be processed soon.";
                type = NotificationType.ORDER_ACCEPTED;
            }
            case PROCESSING -> {
                icon = "🔪";
                title = "Order Processing";
                body = "Your order is now being processed. We'll notify you when it's ready.";
                type = NotificationType.ORDER_PROCESSING;
            }
            case READY -> {
                icon = "📦";
                title = "Order Ready!";
                body = "Your order is ready for pickup! Contact the farmer to arrange collection.";
                type = NotificationType.ORDER_READY;
            }
            case COMPLETED -> {
                icon = "🎉";
                title = "Order Complete";
                body = "Your order has been marked as complete. Thank you for your purchase!";
                type = NotificationType.ORDER_COMPLETED;
            }
            default -> {
                return;
            }
        }

        notificationService.send(buyer, type, icon, title, body, null, order.getId());
    }

    /**
     * Buyer confirms order receipt
     */
    @Transactional
    public Order confirmReceipt(String buyerId, String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (!buyerId.equals(order.getParticipantId())) {
            throw new IllegalArgumentException("This is not your order");
        }

        if (!"READY".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Order must be in READY status to confirm receipt");
        }

        order.setStatus(OrderStatus.COMPLETED.name());
        order.setNotes(appendNote(order.getNotes(), 
                "Receipt confirmed by buyer at " + LocalDateTime.now()));
        orderRepository.save(order);

        auditService.log(buyerId, "ORDER_CONFIRMED", orderId);

        // Notify farmer
        notifyFarmerOfCompletion(order);

        return order;
    }

    private void notifyFarmerOfCompletion(Order order) {
        try {
            String itemsJson = order.getItems();
            if (itemsJson == null || itemsJson.isBlank()) return;

            List<Map<String, Object>> items = objectMapper.readValue(itemsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            // Get unique listing IDs and notify each farmer
            items.stream()
                    .map(i -> i.get("listingId"))
                    .filter(Objects::nonNull)
                    .map(id -> Long.parseLong(String.valueOf(id)))
                    .distinct()
                    .forEach(listingId -> {
                        listingRepository.findById(listingId).ifPresent(listing -> {
                            Participant farmer = listing.getFarmer();
                            if (farmer != null) {
                                notificationService.send(farmer, 
                                        NotificationType.ORDER_COMPLETED,
                                        "✅", 
                                        "Order Completed",
                                        "Buyer has confirmed receipt of their order.",
                                        listingId,
                                        order.getId());
                            }
                        });
                    });
        } catch (Exception e) {
            // Don't fail if notification fails
        }
    }

    private String appendNote(String existingNotes, String note) {
        if (existingNotes == null || existingNotes.isBlank()) {
            return note;
        }
        return existingNotes + " | " + note;
    }
}
