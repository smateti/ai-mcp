package com.example.test6.ejb;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.PostActivate;
import javax.ejb.PrePassivate;
import javax.ejb.Remove;
import javax.ejb.Stateful;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Stateful Session Bean implementing a shopping cart.
 *
 * The container creates a unique instance per client. The cartItems list
 * is the conversational state that survives across method calls within
 * the same session. The container may passivate (serialize) and activate
 * (deserialize) this bean between calls.
 */
@Stateful
public class ShoppingCart implements ShoppingCartLocal, Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ShoppingCart.class.getName());

    private String cartId;
    private List<CartItem> cartItems;

    @PostConstruct
    public void init() {
        cartId = UUID.randomUUID().toString().substring(0, 8);
        cartItems = new ArrayList<>();
        LOG.info("ShoppingCart created: cartId=" + cartId);
    }

    @PreDestroy
    public void destroy() {
        LOG.info("ShoppingCart destroyed: cartId=" + cartId
                + ", items=" + (cartItems != null ? cartItems.size() : 0));
    }

    @PrePassivate
    public void beforePassivate() {
        LOG.info("ShoppingCart passivating: cartId=" + cartId);
    }

    @PostActivate
    public void afterActivate() {
        LOG.info("ShoppingCart activated: cartId=" + cartId);
    }

    @Override
    public void addItem(String itemName, double price, int quantity) {
        for (CartItem item : cartItems) {
            if (item.getItemName().equals(itemName)) {
                item.setQuantity(item.getQuantity() + quantity);
                LOG.info("Updated quantity for '" + itemName + "' in cart " + cartId);
                return;
            }
        }
        cartItems.add(new CartItem(itemName, price, quantity));
        LOG.info("Added '" + itemName + "' to cart " + cartId);
    }

    @Override
    public boolean removeItem(String itemName) {
        boolean removed = cartItems.removeIf(item -> item.getItemName().equals(itemName));
        if (removed) {
            LOG.info("Removed '" + itemName + "' from cart " + cartId);
        }
        return removed;
    }

    @Override
    public void updateQuantity(String itemName, int newQuantity) {
        for (CartItem item : cartItems) {
            if (item.getItemName().equals(itemName)) {
                if (newQuantity <= 0) {
                    cartItems.remove(item);
                } else {
                    item.setQuantity(newQuantity);
                }
                return;
            }
        }
        throw new IllegalArgumentException("Item not found in cart: " + itemName);
    }

    @Override
    public List<CartItem> getCartContents() {
        return Collections.unmodifiableList(new ArrayList<>(cartItems));
    }

    @Override
    public double getCartTotal() {
        double total = 0.0;
        for (CartItem item : cartItems) {
            total += item.getSubtotal();
        }
        return total;
    }

    @Override
    public int getItemCount() {
        return cartItems.size();
    }

    @Override
    public void clearCart() {
        cartItems.clear();
        LOG.info("Cart cleared: cartId=" + cartId);
    }

    @Override
    @Remove
    public String checkout() {
        double total = getCartTotal();
        int count = cartItems.size();
        String summary = String.format("Order confirmed! Cart %s: %d items, total $%.2f",
                cartId, count, total);
        LOG.info("Checkout: " + summary);
        return summary;
    }
}
