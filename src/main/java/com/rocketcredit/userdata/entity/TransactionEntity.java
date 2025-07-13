package com.rocketcredit.userdata.entity;


import java.time.LocalDate;

import javax.persistence.*;

@Entity
@Table(name = "transactions")
public class TransactionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "userId", nullable = false)
    private Long userId;
    
    @Column(nullable = false)
    private LocalDate date;
    
    @Column(nullable = false)
    private Double amount;
    
    @Column(nullable = false)
    private String method;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getMethod() {return method;}
    public void setMethod(String method) { this.method = method;}
}
