package com.example.booking.pg.service;

public class TransactionDeclinedException extends RuntimeException {

    public TransactionDeclinedException(String reason) {
        super(reason);
    }

}
