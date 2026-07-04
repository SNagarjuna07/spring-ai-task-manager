package com.nagarjuna.toolcalling.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "tasks")
@Data
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private boolean done;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    public void save() {
        createdAt = Instant.now();
    }
}
