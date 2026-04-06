package com.masterchefcuts.services;

import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
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

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
