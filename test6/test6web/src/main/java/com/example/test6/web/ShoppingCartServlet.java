package com.example.test6.web;

import com.example.test6.ejb.CartItem;
import com.example.test6.ejb.ShoppingCartLocal;

import javax.ejb.EJB;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@WebServlet(urlPatterns = {"/cart"})
public class ShoppingCartServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @EJB
    private ShoppingCartLocal shoppingCart;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        String action = req.getParameter("action");

        out.println("<!DOCTYPE html>");
        out.println("<html><head><title>Shopping Cart</title>");
        out.println("<style>");
        out.println("body { font-family: Arial, sans-serif; margin: 40px; }");
        out.println("table { border-collapse: collapse; width: 60%; }");
        out.println("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
        out.println("th { background-color: #4CAF50; color: white; }");
        out.println(".actions { margin: 20px 0; }");
        out.println(".actions a { margin-right: 10px; padding: 8px 16px; ");
        out.println("  background: #4CAF50; color: white; text-decoration: none; }");
        out.println("form input, form button { padding: 6px; margin: 4px; }");
        out.println("</style>");
        out.println("</head><body>");
        out.println("<h1>Stateful EJB Shopping Cart</h1>");

        // Process actions
        if ("add".equals(action)) {
            String name = req.getParameter("name");
            String priceStr = req.getParameter("price");
            String qtyStr = req.getParameter("qty");
            if (name != null && priceStr != null && qtyStr != null) {
                try {
                    double price = Double.parseDouble(priceStr);
                    int qty = Integer.parseInt(qtyStr);
                    shoppingCart.addItem(name, price, qty);
                    out.println("<p style='color:green'>Added " + qty + "x " + name + " to cart.</p>");
                } catch (NumberFormatException e) {
                    out.println("<p style='color:red'>Invalid price or quantity.</p>");
                }
            }
        } else if ("remove".equals(action)) {
            String name = req.getParameter("name");
            if (name != null) {
                boolean removed = shoppingCart.removeItem(name);
                out.println(removed
                        ? "<p style='color:green'>Removed " + name + " from cart.</p>"
                        : "<p style='color:red'>Item not found: " + name + "</p>");
            }
        } else if ("clear".equals(action)) {
            shoppingCart.clearCart();
            out.println("<p style='color:green'>Cart cleared.</p>");
        } else if ("checkout".equals(action)) {
            String result = shoppingCart.checkout();
            out.println("<p style='color:blue'><strong>" + result + "</strong></p>");
            out.println("<p>The stateful bean has been removed by the container (@Remove).</p>");
            out.println("</body></html>");
            return;
        }

        // Display cart contents
        List<CartItem> items = shoppingCart.getCartContents();
        out.println("<h2>Cart Contents (" + shoppingCart.getItemCount() + " items)</h2>");

        if (items.isEmpty()) {
            out.println("<p>Your cart is empty.</p>");
        } else {
            out.println("<table>");
            out.println("<tr><th>Item</th><th>Price</th><th>Qty</th><th>Subtotal</th><th>Action</th></tr>");
            for (CartItem item : items) {
                out.printf("<tr><td>%s</td><td>$%.2f</td><td>%d</td><td>$%.2f</td>" +
                                "<td><a href='cart?action=remove&name=%s'>Remove</a></td></tr>%n",
                        item.getItemName(), item.getPrice(), item.getQuantity(),
                        item.getSubtotal(), item.getItemName());
            }
            out.printf("<tr><td colspan='3'><strong>Total</strong></td>" +
                    "<td><strong>$%.2f</strong></td><td></td></tr>%n", shoppingCart.getCartTotal());
            out.println("</table>");
        }

        // Add item form
        out.println("<h3>Add Item</h3>");
        out.println("<form method='get' action='cart'>");
        out.println("<input type='hidden' name='action' value='add'/>");
        out.println("Name: <input type='text' name='name' required/> ");
        out.println("Price: <input type='text' name='price' required/> ");
        out.println("Qty: <input type='text' name='qty' value='1' required/> ");
        out.println("<button type='submit'>Add to Cart</button>");
        out.println("</form>");

        // Quick-add sample items
        out.println("<h3>Quick Add</h3>");
        out.println("<div class='actions'>");
        out.println("<a href='cart?action=add&name=Laptop&price=999.99&qty=1'>Add Laptop ($999.99)</a>");
        out.println("<a href='cart?action=add&name=Mouse&price=29.99&qty=1'>Add Mouse ($29.99)</a>");
        out.println("<a href='cart?action=add&name=Keyboard&price=79.99&qty=1'>Add Keyboard ($79.99)</a>");
        out.println("</div>");

        // Action links
        out.println("<div class='actions'>");
        out.println("<a href='cart?action=clear'>Clear Cart</a>");
        out.println("<a href='cart?action=checkout'>Checkout</a>");
        out.println("</div>");

        out.println("<hr/>");
        out.println("<p><em>This demonstrates a @Stateful EJB. State persists across requests. ");
        out.println("Checkout calls @Remove, destroying the bean instance.</em></p>");
        out.println("</body></html>");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        doGet(req, resp);
    }
}
