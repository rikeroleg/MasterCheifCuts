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
@Table(name = "purchases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Purchase {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;
    private String participantId;
    private String purchaseDate;
    private double totalAmount;
    private String items; // This could be a JSON string or a serialized list of items
    private String status;
    private String notes;
    
}