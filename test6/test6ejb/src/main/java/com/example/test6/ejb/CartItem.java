package com.example.test6.ejb;

import java.io.Serializable;

public class CartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String itemName;
    private double price;
    private int quantity;

    public CartItem() {
    }

    public CartItem(String itemName, double price, int quantity) {
        this.itemName = itemName;
        this.price = price;
        this.quantity = quantity;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getSubtotal() {
        return price * quantity;
    }

    @Override
    public String toString() {
        return String.format("CartItem{name='%s', price=%.2f, qty=%d, subtotal=%.2f}",
                itemName, price, quantity, getSubtotal());
    }
}
