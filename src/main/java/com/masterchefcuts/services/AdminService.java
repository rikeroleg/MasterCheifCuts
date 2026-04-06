package com.masterchefcuts.services;

import com.masterchefcuts.model.Order;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.OrderRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final ParticipantRepo participantRepo;
    private final ListingRepository listingRepository;
    private final ClaimRepository claimRepository;
    private final OrderRepository orderRepository;

    public List<Participant> getAllUsers() {
        return participantRepo.findAll();
    }

    @Transactional
    public Participant setApproved(String userId, boolean approved) {
        Participant p = participantRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        p.setApproved(approved);
        return participantRepo.save(p);
    }

    @Transactional
    public void deleteListing(Long listingId) {
        listingRepository.deleteById(listingId);
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
}
