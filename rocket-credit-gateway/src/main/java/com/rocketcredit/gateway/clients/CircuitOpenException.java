package com.rocketcredit.gateway.clients;

public class CircuitOpenException extends RuntimeException {
    public CircuitOpenException() { super("CIRCUIT_OPEN"); }
}

