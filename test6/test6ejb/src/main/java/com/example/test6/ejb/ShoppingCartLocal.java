package com.example.test6.ejb;

import javax.ejb.Local;
import java.util.List;

@Local
public interface ShoppingCartLocal {

    void addItem(String itemName, double price, int quantity);

    boolean removeItem(String itemName);

    void updateQuantity(String itemName, int newQuantity);

    List<CartItem> getCartContents();

    double getCartTotal();

    int getItemCount();

    void clearCart();

    /**
     * Checkout the cart. The @Remove annotation on the implementation
     * causes the container to destroy this stateful bean instance after return.
     */
    String checkout();
}
