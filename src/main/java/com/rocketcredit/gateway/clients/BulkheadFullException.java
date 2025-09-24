package com.rocketcredit.gateway.clients;

public class BulkheadFullException extends RuntimeException {
    public BulkheadFullException() { super("BULKHEAD_FULL"); }
}

