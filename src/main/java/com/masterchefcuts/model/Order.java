package com.masterchefcuts.model;

import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;

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
    private Long id;
    private String participantId;
    private String orderDate;
    private String status;
    private double totalAmount;
    private String items; // This could be a JSON string or a serialized list of items
    private String deliveryDate;
    private String notes;
    
}