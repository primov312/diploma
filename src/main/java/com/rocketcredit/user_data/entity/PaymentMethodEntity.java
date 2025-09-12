package com.rocketcredit.user_data.entity;

import jakarta.persistence.*;


@Entity
@Table(name = "paymentMethods")
public class PaymentMethodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "userId", nullable = false)
    private Long userId;

    @Column(name = "token")
    private String token;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void userId(Long userId) {
        this.userId = userId;
    }
    public PaymentMethodEntity setUserId(Long userId) {
        this.userId = userId; return this;
    }

    public String getToken() { return token; }
    public PaymentMethodEntity setToken(String token) {
        this.token = token; return this;
    }
}