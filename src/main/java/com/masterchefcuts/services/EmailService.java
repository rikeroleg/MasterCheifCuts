package com.masterchefcuts.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Order;
import com.masterchefcuts.model.Participant;
import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final Resend resend;
    private final ObjectMapper objectMapper;

    @Value("${resend.from}")
    private String from;

    @Async
    public void sendClaimConfirmation(Participant buyer, Listing listing, String cutLabel) {
        String subject = "✅ You claimed the " + cutLabel + " cut!";
        String body = "Hi " + buyer.getFirstName() + ",\n\n"
                + "You've successfully claimed the " + cutLabel + " cut from:\n"
                + "  Animal: " + listing.getBreed() + " " + listing.getAnimalType() + "\n"
                + "  Farm: " + listing.getSourceFarm() + "\n"
                + "  Price: $" + String.format("%.2f", listing.getPricePerLb()) + "/lb\n\n"
                + "You'll receive another email once the farmer sets the processing date.\n\n"
                + "— MasterChef Cuts";
        send(buyer.getEmail(), subject, body);
    }

    @Async
    public void sendPoolFullToBuyers(List<Participant> buyers, Listing listing) {
        String subject = "🎉 The " + listing.getBreed() + " pool is full!";
        for (Participant buyer : buyers) {
            String body = "Hi " + buyer.getFirstName() + ",\n\n"
                    + "Great news — the " + listing.getBreed() + " " + listing.getAnimalType()
                    + " listing from " + listing.getFarmer().getShopName() + " is fully claimed!\n\n"
                    + "The farmer will set a processing date soon. We'll email you when that happens.\n\n"
                    + "— MasterChef Cuts";
            send(buyer.getEmail(), subject, body);
        }
    }

    @Async
    public void sendPoolFullToFarmer(Participant farmer, Listing listing) {
        String subject = "🎉 Your " + listing.getBreed() + " listing is fully claimed!";
        String body = "Hi " + farmer.getFirstName() + ",\n\n"
                + "All cuts on your " + listing.getBreed() + " " + listing.getAnimalType()
                + " listing have been claimed!\n\n"
                + "Please log in to set a processing date so buyers know when to expect their cuts.\n\n"
                + "— MasterChef Cuts";
        send(farmer.getEmail(), subject, body);
    }

    @Async
    public void sendProcessingDateSet(List<Participant> buyers, Listing listing, LocalDate date) {
        String formattedDate = date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
        String subject = "🗓 Processing date set — " + listing.getBreed() + " " + listing.getAnimalType();
        for (Participant buyer : buyers) {
            String body = "Hi " + buyer.getFirstName() + ",\n\n"
                    + "The processing date for your " + listing.getBreed() + " "
                    + listing.getAnimalType() + " cut has been set:\n\n"
                    + "  Processing date: " + formattedDate + "\n"
                    + "  Farm: " + listing.getSourceFarm() + "\n"
                    + "  Farmer: " + listing.getFarmer().getShopName() + "\n\n"
                    + "Please coordinate pickup details directly with the farmer.\n\n"
                    + "— MasterChef Cuts";
            send(buyer.getEmail(), subject, body);
        }
    }

    @Async
    public void sendPasswordReset(String to, String firstName, String token) {
        String subject = "🔑 Reset your MasterChef Cuts password";
        String body = "Hi " + firstName + ",\n\n"
                + "We received a request to reset your password. Click the link below (valid for 1 hour):\n\n"
                + "http://localhost:5173/reset-password?token=" + token + "\n\n"
                + "If you didn't request this, ignore this email — your password won't change.\n\n"
                + "— MasterChef Cuts";
        send(to, subject, body);
    }

    @Async
    public void sendEmailVerification(String to, String firstName, String token) {
        String subject = "[MasterChef Cuts] Verify your email address";
        String body = "Hi " + firstName + ",\n\n"
                + "Please verify your email address by clicking the link below:\n\n"
                + "http://localhost:5173/verify-email?token=" + token + "\n\n"
                + "This link is valid for 24 hours. If you did not create an account, ignore this email.\n\n"
                + "-- MasterChef Cuts";
        send(to, subject, body);
    }

    @Async
    public void sendOrderConfirmationToBuyer(Order order, Participant buyer) {
        try {
            String subject = "✅ Order Confirmed — MasterChef Cuts #" + order.getId().substring(0, 8).toUpperCase();
            String html = buildBuyerConfirmationHtml(order, buyer);
            sendHtml(buyer.getEmail(), subject, html);
        } catch (Exception e) {
            log.error("Failed to send order confirmation to buyer {}: {}", buyer.getEmail(), e.getMessage());
        }
    }
    public void sendNewOrderToFarmer(Order order, Listing listing, Participant farmer) {
        try {
            String subject = "💵 New Order Received — MasterChef Cuts #" + order.getId().substring(0, 8).toUpperCase();
            String html = buildFarmerOrderHtml(order, listing, farmer);
            sendHtml(farmer.getEmail(), subject, html);
        } catch (Exception e) {
            log.error("Failed to send new order email to farmer {}: {}", farmer.getEmail(), e.getMessage());
        }
    }

    private String buildBuyerConfirmationHtml(Order order, Participant buyer) {
        List<Map<String, Object>> items = parseItems(order.getItems());
        String itemRows = buildItemRows(items);
        String processingDate = order.getDeliveryDate() != null ? order.getDeliveryDate() : "To be confirmed by farmer";
        String total = String.format("$%.2f", order.getTotalAmount());
        String orderId = order.getId().substring(0, 8).toUpperCase();

        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;background:#f9f6f0;padding:24px;'>"
                + "<div style='max-width:560px;margin:0 auto;background:#fff;border-radius:12px;overflow:hidden;border:1px solid #e0d0b0;'>"
                + "<div style='background:linear-gradient(135deg,#8b3a00,#c07828);padding:28px 32px;'>"
                + "<h1 style='color:#fff;margin:0;font-size:1.4rem;'>Order Confirmed! 🎉</h1>"
                + "<p style='color:rgba(255,255,255,0.8);margin:6px 0 0;font-size:0.9rem;'>Order #" + orderId + "</p>"
                + "</div>"
                + "<div style='padding:28px 32px;'>"
                + "<p style='color:#3a1800;font-size:0.95rem;'>Hi " + buyer.getFirstName() + ",</p>"
                + "<p style='color:#5a3000;font-size:0.88rem;line-height:1.6;'>Your payment was received and your order is now confirmed. "
                + "The farmer will accept and begin processing soon — you'll receive an email and notification at each step.</p>"
                + "<h3 style='color:#6b2c00;font-size:0.8rem;letter-spacing:0.1em;text-transform:uppercase;margin:24px 0 10px;'>Order Summary</h3>"
                + "<table style='width:100%;border-collapse:collapse;font-size:0.88rem;'>"
                + "<thead><tr style='background:#f5e8c8;'>"
                + "<th style='padding:10px 12px;text-align:left;color:#6b2c00;font-weight:700;'>Cut</th>"
                + "<th style='padding:10px 12px;text-align:left;color:#6b2c00;font-weight:700;'>Animal / Breed</th>"
                + "<th style='padding:10px 12px;text-align:right;color:#6b2c00;font-weight:700;'>Price</th>"
                + "</tr></thead><tbody>"
                + itemRows
                + "</tbody></table>"
                + "<div style='margin-top:16px;padding:14px 16px;background:#fdf3e0;border-radius:8px;border:1px solid #e0c880;display:flex;justify-content:space-between;'>"
                + "<span style='font-weight:700;color:#3a1800;'>Total Paid</span>"
                + "<span style='font-weight:900;color:#3a1800;font-size:1.05rem;'>" + total + "</span>"
                + "</div>"
                + "<div style='margin-top:20px;padding:14px 16px;background:#f0f8f4;border-radius:8px;border:1px solid #b0d8b8;'>"
                + "<p style='margin:0;font-size:0.85rem;color:#2a5c3a;'><strong>Processing date:</strong> " + processingDate + "</p>"
                + "</div>"
                + "<p style='margin-top:24px;font-size:0.82rem;color:#8a6040;line-height:1.5;'>"
                + "Questions? Reply to this email or log in to your account to track your order status.</p>"
                + "<p style='margin-top:4px;font-size:0.82rem;color:#8a6040;'>— The MasterChef Cuts Team</p>"
                + "</div></div></body></html>";
    }

    private String buildFarmerOrderHtml(Order order, Listing listing, Participant farmer) {
        List<Map<String, Object>> items = parseItems(order.getItems());
        String itemRows = buildItemRows(items);
        String total = String.format("$%.2f", order.getTotalAmount());
        String orderId = order.getId().substring(0, 8).toUpperCase();
        String buyerName = "A buyer";

        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;background:#f9f6f0;padding:24px;'>"
                + "<div style='max-width:560px;margin:0 auto;background:#fff;border-radius:12px;overflow:hidden;border:1px solid #e0d0b0;'>"
                + "<div style='background:linear-gradient(135deg,#1a5c2e,#27ae60);padding:28px 32px;'>"
                + "<h1 style='color:#fff;margin:0;font-size:1.4rem;'>New Order Received! 💵</h1>"
                + "<p style='color:rgba(255,255,255,0.8);margin:6px 0 0;font-size:0.9rem;'>Order #" + orderId + "</p>"
                + "</div>"
                + "<div style='padding:28px 32px;'>"
                + "<p style='color:#1a3a1a;font-size:0.95rem;'>Hi " + farmer.getFirstName() + ",</p>"
                + "<p style='color:#2a5a2a;font-size:0.88rem;line-height:1.6;'>" + buyerName + " has paid for an order from your "
                + "<strong>" + listing.getBreed() + " " + listing.getAnimalType() + "</strong> listing. "
                + "Please log in and accept the order to begin processing.</p>"
                + "<h3 style='color:#1a4a1a;font-size:0.8rem;letter-spacing:0.1em;text-transform:uppercase;margin:24px 0 10px;'>Order Details</h3>"
                + "<table style='width:100%;border-collapse:collapse;font-size:0.88rem;'>"
                + "<thead><tr style='background:#e0f5e8;'>"
                + "<th style='padding:10px 12px;text-align:left;color:#1a4a1a;font-weight:700;'>Cut</th>"
                + "<th style='padding:10px 12px;text-align:left;color:#1a4a1a;font-weight:700;'>Animal / Breed</th>"
                + "<th style='padding:10px 12px;text-align:right;color:#1a4a1a;font-weight:700;'>Price</th>"
                + "</tr></thead><tbody>"
                + itemRows
                + "</tbody></table>"
                + "<div style='margin-top:16px;padding:14px 16px;background:#e8f8ee;border-radius:8px;border:1px solid #80c890;display:flex;justify-content:space-between;'>"
                + "<span style='font-weight:700;color:#1a3a1a;'>Order Total</span>"
                + "<span style='font-weight:900;color:#1a3a1a;font-size:1.05rem;'>" + total + "</span>"
                + "</div>"
                + "<div style='margin-top:20px;padding:14px 16px;background:#fffbe8;border-radius:8px;border:1px solid #e8d840;'>"
                + "<p style='margin:0;font-size:0.85rem;color:#5a4a00;'><strong>Action required:</strong> Log in to MasterChef Cuts and accept this order to begin the fulfillment process.</p>"
                + "</div>"
                + "<p style='margin-top:24px;font-size:0.82rem;color:#4a6a4a;line-height:1.5;'>Your payout (85% of the order total) will be released once the buyer confirms receipt.</p>"
                + "<p style='margin-top:4px;font-size:0.82rem;color:#4a6a4a;'>— The MasterChef Cuts Team</p>"
                + "</div></div></body></html>";
    }

    private String buildItemRows(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return "<tr><td colspan='3' style='padding:10px 12px;color:#8a6040;'>No items found</td></tr>";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            String label = String.valueOf(item.getOrDefault("cutLabel", item.getOrDefault("label", "Cut")));
            String breed = item.get("breed") != null ? String.valueOf(item.get("breed")) : "";
            String animalType = item.get("animalType") != null ? String.valueOf(item.get("animalType")) : "";
            String animalInfo = breed.isEmpty() && animalType.isEmpty() ? "—" :
                    (breed + (animalType.isEmpty() ? "" : " " + animalType)).trim();
            String price = item.get("price") != null
                    ? String.format("$%.2f", ((Number) item.get("price")).doubleValue()) : "—";
            String bg = i % 2 == 0 ? "#fff" : "#fdf7ee";
            sb.append("<tr style='background:").append(bg).append(";'>")
              .append("<td style='padding:10px 12px;color:#3a1800;font-weight:600;'>").append(escapeHtml(label)).append("</td>")
              .append("<td style='padding:10px 12px;color:#5a3000;'>").append(escapeHtml(animalInfo)).append("</td>")
              .append("<td style='padding:10px 12px;text-align:right;color:#3a1800;font-weight:700;'>").append(price).append("</td>")
              .append("</tr>");
        }
        return sb.toString();
    }

    private List<Map<String, Object>> parseItems(String itemsJson) {
        try {
            if (itemsJson == null || itemsJson.isBlank()) return List.of();
            return objectMapper.readValue(itemsJson, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    @Async
    public void sendOrderAccepted(Order order, Participant buyer) {
        String subject = "✅ Your order has been accepted — MasterChef Cuts";
        String body = "Hi " + buyer.getFirstName() + ",\n\n"
                + "Great news! The farmer has accepted your order #"
                + order.getId().substring(0, 8).toUpperCase() + ".\n\n"
                + "Your cuts are now being processed. We'll notify you when your order is ready for pickup.\n\n"
                + "— MasterChef Cuts";
        send(buyer.getEmail(), subject, body);
    }

    @Async
    public void sendOrderReady(Order order, Participant buyer) {
        String subject = "📦 Your order is ready for pickup — MasterChef Cuts";
        String body = "Hi " + buyer.getFirstName() + ",\n\n"
                + "Your order #" + order.getId().substring(0, 8).toUpperCase() + " is ready for pickup!\n\n"
                + "Please contact the farmer to arrange collection of your cuts. Once you have received your "
                + "order, log in and confirm receipt so the farmer can receive their payout.\n\n"
                + "— MasterChef Cuts";
        send(buyer.getEmail(), subject, body);
    }

    @Async
    public void sendOrderCompleted(Order order, Participant buyer) {
        String subject = "🎉 Order complete — thank you! — MasterChef Cuts";
        String body = "Hi " + buyer.getFirstName() + ",\n\n"
                + "Your order #" + order.getId().substring(0, 8).toUpperCase()
                + " has been marked as complete. Thank you for shopping with MasterChef Cuts!\n\n"
                + "Enjoyed your purchase? Consider leaving a review for the farmer on your listing page.\n\n"
                + "— MasterChef Cuts";
        send(buyer.getEmail(), subject, body);
    }

    @Async
    public void sendFarmerApproved(Participant farmer) {
        String subject = "✅ Your MasterChef Cuts farmer account has been approved!";
        String body = "Hi " + farmer.getFirstName() + ",\n\n"
                + "Great news — your MasterChef Cuts farmer account has been approved! "
                + "You can now log in and start posting listings for buyers to claim.\n\n"
                + "Log in at: http://localhost:5173/login\n\n"
                + "— MasterChef Cuts";
        send(farmer.getEmail(), subject, body);
    }

    private void sendHtml(String to, String subject, String html) {
        try {
            resend.emails().send(CreateEmailOptions.builder()
                    .from(from)
                    .to(to)
                    .subject(subject)
                    .html(html)
                    .build());
        } catch (Exception e) {
            log.error("Failed to send HTML email to {}: {}", to, e.getMessage());
        }
    }

    private void send(String to, String subject, String body) {
        try {
            resend.emails().send(CreateEmailOptions.builder()
                    .from(from)
                    .to(to)
                    .subject(subject)
                    .text(body)
                    .build());
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
