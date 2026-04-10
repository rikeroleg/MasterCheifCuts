package com.masterchefcuts.model;

import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "orders")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Order {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "stripe_payment_intent_id", unique = true)
    private String stripePaymentIntentId;

    private String participantId;
    private String orderDate;
    private String paidAt;
    private String status;
    private Long amountCents;
    private String currency;
    private double totalAmount;
    private String items; // This could be a JSON string or a serialized list of items
    private String deliveryDate;
    private String notes;

    // Delivery address snapshot (copied from buyer at order creation time)
    private String deliveryStreet;
    private String deliveryCity;
    private String deliveryState;
    private String deliveryZip;

}