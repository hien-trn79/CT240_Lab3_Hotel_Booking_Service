package service;

public interface PaymentGateway {
    boolean charge(String creditCard, double amount);
}
