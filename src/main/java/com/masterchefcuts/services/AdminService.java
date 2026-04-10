package com.masterchefcuts.services;

import com.masterchefcuts.model.Order;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.OrderRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final ParticipantRepo participantRepo;
    private final ListingRepository listingRepository;
    private final ClaimRepository claimRepository;
    private final OrderRepository orderRepository;
    private final RefundService refundService;
    private final AuditService auditService;

    public List<Participant> getAllUsers() {
        return participantRepo.findAll();
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByOrderDateDesc();
    }

    @Transactional
    public Order issueRefund(String orderId, String reason) throws StripeException {
        Order order = refundService.issueRefund(orderId, reason, true);
        auditService.log(currentActorId(), "REFUND_ORDER", orderId);
        return order;
    }

    @Transactional
    public Participant setApproved(String userId, boolean approved) {
        Participant p = participantRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        p.setApproved(approved);
        Participant saved = participantRepo.save(p);
        auditService.log(currentActorId(), approved ? "APPROVE_USER" : "REJECT_USER", userId);
        return saved;
    }

    @Transactional
    public void deleteListing(Long listingId) {
        listingRepository.deleteById(listingId);
        auditService.log(currentActorId(), "ADMIN_DELETE_LISTING", String.valueOf(listingId));
    }

    private String currentActorId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? (String) auth.getPrincipal() : null;
    }

    public Map<String, Object> getUserDetail(String userId) {
        Participant p = participantRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<Order> orders = orderRepository.findByParticipantIdOrderByOrderDateDesc(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("id", p.getId());
        result.put("firstName", p.getFirstName());
        result.put("lastName", p.getLastName());
        result.put("email", p.getEmail());
        result.put("phone", p.getPhone());
        result.put("role", p.getRole().name());
        result.put("shopName", p.getShopName());
        result.put("street", p.getStreet());
        result.put("apt", p.getApt());
        result.put("city", p.getCity());
        result.put("state", p.getState());
        result.put("zipCode", p.getZipCode());
        result.put("approved", p.isApproved());
        result.put("emailVerified", p.isEmailVerified());
        result.put("totalSpent", p.getTotalSpent());
        result.put("notificationPreference", p.getNotificationPreference());
        result.put("orders", orders);
        return result;
    }

    public Map<String, Object> getStats() {
        long totalUsers    = participantRepo.count();
        long totalListings = listingRepository.count();
        long totalClaims   = claimRepository.count();
        long pendingFarmers = participantRepo.findAll().stream()
                .filter(p -> p.getRole().name().equals("FARMER") && !p.isApproved()).count();
        return Map.of(
                "totalUsers", totalUsers,
                "totalListings", totalListings,
                "totalClaims", totalClaims,
                "pendingFarmers", pendingFarmers
        );
    }

    private static final List<String> PAID_STATUSES = List.of(
            "PAID", "ACCEPTED", "PROCESSING", "READY", "COMPLETED"
    );

    public Map<String, Object> getFinancialSummary(String from, String to) {
        List<Order> paid = orderRepository.findAllByOrderByOrderDateDesc().stream()
                .filter(o -> PAID_STATUSES.contains(o.getStatus() == null ? "" : o.getStatus().toUpperCase()))
                .filter(o -> dateAfterOrNull(o.getOrderDate(), from))
                .filter(o -> dateBeforeOrNull(o.getOrderDate(), to))
                .collect(Collectors.toList());
        double total = paid.stream().mapToDouble(Order::getTotalAmount).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRevenue",  total);
        result.put("platformFees",  total * 0.15);
        result.put("farmerPayouts", total * 0.85);
        result.put("orderCount",    paid.size());
        return result;
    }

    public List<Map<String, Object>> getFinancialOrders(String status, String from, String to) {
        return orderRepository.findAllByOrderByOrderDateDesc().stream()
                .filter(o -> {
                    String s = o.getStatus() != null ? o.getStatus().toUpperCase() : "";
                    if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL"))
                        return PAID_STATUSES.contains(s);
                    return s.equals(status.toUpperCase());
                })
                .filter(o -> dateAfterOrNull(o.getOrderDate(), from))
                .filter(o -> dateBeforeOrNull(o.getOrderDate(), to))
                .map(o -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",           o.getId());
                    m.put("orderDate",    o.getOrderDate());
                    m.put("status",       o.getStatus());
                    m.put("totalAmount",  o.getTotalAmount());
                    m.put("platformFee",  o.getTotalAmount() * 0.15);
                    m.put("farmerPayout", o.getTotalAmount() * 0.85);
                    m.put("participantId",o.getParticipantId());
                    String buyerName = participantRepo.findById(o.getParticipantId() != null ? o.getParticipantId() : "")
                            .map(p -> (p.getFirstName() + " " + p.getLastName()).trim())
                            .orElse("—");
                    m.put("buyerName", buyerName);
                    return m;
                })
                .collect(Collectors.toList());
    }

    private boolean dateAfterOrNull(String dateStr, String from) {
        if (from == null || from.isBlank() || dateStr == null || dateStr.length() < 10) return true;
        return dateStr.substring(0, 10).compareTo(from) >= 0;
    }

    private boolean dateBeforeOrNull(String dateStr, String to) {
        if (to == null || to.isBlank() || dateStr == null || dateStr.length() < 10) return true;
        return dateStr.substring(0, 10).compareTo(to) <= 0;
    }
}
