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
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;
    private String productId;
    private String productName;
    private Long orderId;
    private String participantId;
    private int quantity;
    private double totalPrice;
    private double pricePerLb;
    private String addedDate;
    
}