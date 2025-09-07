package com.masterchefcuts.model;

import java.util.Objects;
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
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;
    private String name;
    private String product;
    private String cutType;
    private String description;
    private double pricePerLb;
    private double stockPerLb;
    private String origin;
    private String grade;
    private String isFeatured;

}