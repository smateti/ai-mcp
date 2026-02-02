#!/bin/bash
# Load sample application log data into the app-logs index

ES_URL="${ES_URL:-http://localhost:9200}"
INDEX_NAME="app-logs"

echo "=== Loading sample data into $INDEX_NAME ==="

# Use bulk API for efficiency
curl -s -X POST "$ES_URL/$INDEX_NAME/_bulk" -H "Content-Type: application/x-ndjson" -d '
{"index":{}}
{"containerName":"app-inventory","namespace":"app-lambda-inventory","message":"2026-01-30T10:15:23.456Z [c3a1b2d4-5e6f-7890-abcd-ef1234567890] INFO  com.naagi.inventory.service.InventoryService - Processing inventory check for SKU-12345, warehouse: WH-EAST-01","timestamp":"2026-01-30T10:15:23.456Z","level":"INFO","correlationId":"c3a1b2d4-5e6f-7890-abcd-ef1234567890","serviceName":"inventory-service","environment":"production"}
{"index":{}}
{"containerName":"app-inventory","namespace":"app-lambda-inventory","message":"2026-01-30T10:15:23.512Z [c3a1b2d4-5e6f-7890-abcd-ef1234567890] INFO  com.naagi.inventory.service.InventoryService - Stock level for SKU-12345: 142 units available","timestamp":"2026-01-30T10:15:23.512Z","level":"INFO","correlationId":"c3a1b2d4-5e6f-7890-abcd-ef1234567890","serviceName":"inventory-service","environment":"production"}
{"index":{}}
{"containerName":"app-inventory","namespace":"app-lambda-inventory","message":"2026-01-30T10:15:24.001Z [c3a1b2d4-5e6f-7890-abcd-ef1234567890] DEBUG com.naagi.inventory.repository.InventoryRepository - Executing query: SELECT * FROM inventory WHERE sku_id = ? AND warehouse_id = ?","timestamp":"2026-01-30T10:15:24.001Z","level":"DEBUG","correlationId":"c3a1b2d4-5e6f-7890-abcd-ef1234567890","serviceName":"inventory-service","environment":"production"}
{"index":{}}
{"containerName":"app-payment-gw","namespace":"app-lambda-payment","message":"2026-01-30T10:15:25.100Z [a1b2c3d4-e5f6-7890-1234-567890abcdef] INFO  com.naagi.payment.service.PaymentService - Initiating payment processing for order ORD-98765, amount: $129.99","timestamp":"2026-01-30T10:15:25.100Z","level":"INFO","correlationId":"a1b2c3d4-e5f6-7890-1234-567890abcdef","serviceName":"payment-service","environment":"production"}
{"index":{}}
{"containerName":"app-payment-gw","namespace":"app-lambda-payment","message":"2026-01-30T10:15:25.350Z [a1b2c3d4-e5f6-7890-1234-567890abcdef] INFO  com.naagi.payment.service.FraudCheckService - Fraud check passed for order ORD-98765, risk score: 0.12","timestamp":"2026-01-30T10:15:25.350Z","level":"INFO","correlationId":"a1b2c3d4-e5f6-7890-1234-567890abcdef","serviceName":"payment-service","environment":"production"}
{"index":{}}
{"containerName":"app-payment-gw","namespace":"app-lambda-payment","message":"2026-01-30T10:15:26.200Z [a1b2c3d4-e5f6-7890-1234-567890abcdef] INFO  com.naagi.payment.service.PaymentService - Payment authorized successfully, transactionId: TXN-44556677","timestamp":"2026-01-30T10:15:26.200Z","level":"INFO","correlationId":"a1b2c3d4-e5f6-7890-1234-567890abcdef","serviceName":"payment-service","environment":"production"}
{"index":{}}
{"containerName":"app-payment-gw","namespace":"app-lambda-payment","message":"2026-01-30T10:16:01.890Z [b2c3d4e5-f6a7-8901-2345-678901bcdef0] ERROR com.naagi.payment.service.PaymentService - Payment processing failed for order ORD-11223\njava.net.SocketTimeoutException: Connect timed out\n\tat java.net.Socket.connect(Socket.java:600)\n\tat com.naagi.payment.gateway.StripeGateway.charge(StripeGateway.java:145)\n\tat com.naagi.payment.service.PaymentService.processPayment(PaymentService.java:89)\n\tat com.naagi.payment.controller.PaymentController.pay(PaymentController.java:42)","timestamp":"2026-01-30T10:16:01.890Z","level":"ERROR","correlationId":"b2c3d4e5-f6a7-8901-2345-678901bcdef0","stackTrace":"java.net.SocketTimeoutException: Connect timed out\n\tat java.net.Socket.connect(Socket.java:600)\n\tat com.naagi.payment.gateway.StripeGateway.charge(StripeGateway.java:145)\n\tat com.naagi.payment.service.PaymentService.processPayment(PaymentService.java:89)\n\tat com.naagi.payment.controller.PaymentController.pay(PaymentController.java:42)","serviceName":"payment-service","environment":"production"}
{"index":{}}
{"containerName":"app-order-proc","namespace":"app-lambda-orders","message":"2026-01-30T10:15:20.000Z [d4e5f6a7-b8c9-0123-4567-890abcdef012] INFO  com.naagi.order.service.OrderService - New order received: ORD-98765, customer: CUST-5001, items: 3","timestamp":"2026-01-30T10:15:20.000Z","level":"INFO","correlationId":"d4e5f6a7-b8c9-0123-4567-890abcdef012","serviceName":"order-service","environment":"production"}
{"index":{}}
{"containerName":"app-order-proc","namespace":"app-lambda-orders","message":"2026-01-30T10:15:20.250Z [d4e5f6a7-b8c9-0123-4567-890abcdef012] INFO  com.naagi.order.service.OrderService - Order ORD-98765 validated successfully, proceeding to inventory check","timestamp":"2026-01-30T10:15:20.250Z","level":"INFO","correlationId":"d4e5f6a7-b8c9-0123-4567-890abcdef012","serviceName":"order-service","environment":"production"}
{"index":{}}
{"containerName":"app-order-proc","namespace":"app-lambda-orders","message":"2026-01-30T10:15:30.500Z [d4e5f6a7-b8c9-0123-4567-890abcdef012] INFO  com.naagi.order.service.OrderService - Order ORD-98765 status updated to PAYMENT_PENDING","timestamp":"2026-01-30T10:15:30.500Z","level":"INFO","correlationId":"d4e5f6a7-b8c9-0123-4567-890abcdef012","serviceName":"order-service","environment":"production"}
{"index":{}}
{"containerName":"app-order-proc","namespace":"app-lambda-orders","message":"2026-01-30T10:16:05.100Z [e5f6a7b8-c9d0-1234-5678-90abcdef0123] ERROR com.naagi.order.service.OrderService - Failed to process order ORD-11223: payment gateway timeout\njava.lang.RuntimeException: Payment processing failed: Connect timed out\n\tat com.naagi.order.service.OrderService.processOrder(OrderService.java:156)\n\tat com.naagi.order.controller.OrderController.createOrder(OrderController.java:67)","timestamp":"2026-01-30T10:16:05.100Z","level":"ERROR","correlationId":"e5f6a7b8-c9d0-1234-5678-90abcdef0123","stackTrace":"java.lang.RuntimeException: Payment processing failed: Connect timed out\n\tat com.naagi.order.service.OrderService.processOrder(OrderService.java:156)\n\tat com.naagi.order.controller.OrderController.createOrder(OrderController.java:67)","serviceName":"order-service","environment":"production"}
{"index":{}}
{"containerName":"app-user-mgmt","namespace":"app-lambda-users","message":"2026-01-30T10:14:55.000Z [f6a7b8c9-d0e1-2345-6789-0abcdef01234] INFO  com.naagi.user.service.UserService - User login successful: CUST-5001, IP: 192.168.1.45","timestamp":"2026-01-30T10:14:55.000Z","level":"INFO","correlationId":"f6a7b8c9-d0e1-2345-6789-0abcdef01234","serviceName":"user-service","environment":"production"}
{"index":{}}
{"containerName":"app-user-mgmt","namespace":"app-lambda-users","message":"2026-01-30T10:14:55.200Z [f6a7b8c9-d0e1-2345-6789-0abcdef01234] DEBUG com.naagi.user.service.AuthService - JWT token generated for user CUST-5001, expires: 2026-01-30T11:14:55Z","timestamp":"2026-01-30T10:14:55.200Z","level":"DEBUG","correlationId":"f6a7b8c9-d0e1-2345-6789-0abcdef01234","serviceName":"user-service","environment":"production"}
{"index":{}}
{"containerName":"app-user-mgmt","namespace":"app-lambda-users","message":"2026-01-30T10:20:10.300Z [a7b8c9d0-e1f2-3456-7890-abcdef012345] ERROR com.naagi.user.service.UserService - Authentication failed for user unknown@example.com: invalid credentials, attempt 3 of 5\ncom.naagi.user.exception.AuthenticationException: Invalid credentials\n\tat com.naagi.user.service.AuthService.authenticate(AuthService.java:78)\n\tat com.naagi.user.service.UserService.login(UserService.java:45)\n\tat com.naagi.user.controller.AuthController.login(AuthController.java:32)","timestamp":"2026-01-30T10:20:10.300Z","level":"ERROR","correlationId":"a7b8c9d0-e1f2-3456-7890-abcdef012345","stackTrace":"com.naagi.user.exception.AuthenticationException: Invalid credentials\n\tat com.naagi.user.service.AuthService.authenticate(AuthService.java:78)\n\tat com.naagi.user.service.UserService.login(UserService.java:45)\n\tat com.naagi.user.controller.AuthController.login(AuthController.java:32)","serviceName":"user-service","environment":"production"}
{"index":{}}
{"containerName":"app-fraud","namespace":"app-lambda-fraud","message":"2026-01-30T10:15:25.200Z [a1b2c3d4-e5f6-7890-1234-567890abcdef] INFO  com.naagi.fraud.service.FraudDetectionService - Fraud check initiated for order ORD-98765, rules evaluated: 12","timestamp":"2026-01-30T10:15:25.200Z","level":"INFO","correlationId":"a1b2c3d4-e5f6-7890-1234-567890abcdef","serviceName":"fraud-service","environment":"production"}
{"index":{}}
{"containerName":"app-fraud","namespace":"app-lambda-fraud","message":"2026-01-30T10:15:25.300Z [a1b2c3d4-e5f6-7890-1234-567890abcdef] DEBUG com.naagi.fraud.service.FraudDetectionService - Rule evaluation complete: velocity_check=PASS, geo_check=PASS, amount_check=PASS, device_check=PASS","timestamp":"2026-01-30T10:15:25.300Z","level":"DEBUG","correlationId":"a1b2c3d4-e5f6-7890-1234-567890abcdef","serviceName":"fraud-service","environment":"production"}
{"index":{}}
{"containerName":"app-notification","namespace":"app-lambda-notifications","message":"2026-01-30T10:15:27.000Z [a1b2c3d4-e5f6-7890-1234-567890abcdef] INFO  com.naagi.notification.service.NotificationService - Email notification queued for order ORD-98765 confirmation to customer CUST-5001","timestamp":"2026-01-30T10:15:27.000Z","level":"INFO","correlationId":"a1b2c3d4-e5f6-7890-1234-567890abcdef","serviceName":"notification-service","environment":"production"}
{"index":{}}
{"containerName":"app-notification","namespace":"app-lambda-notifications","message":"2026-01-30T10:15:27.500Z [a1b2c3d4-e5f6-7890-1234-567890abcdef] INFO  com.naagi.notification.service.EmailSender - Email sent successfully to cust5001@email.com, messageId: MSG-778899","timestamp":"2026-01-30T10:15:27.500Z","level":"INFO","correlationId":"a1b2c3d4-e5f6-7890-1234-567890abcdef","serviceName":"notification-service","environment":"production"}
{"index":{}}
{"containerName":"app-notification","namespace":"app-lambda-notifications","message":"2026-01-30T10:16:10.000Z [b2c3d4e5-f6a7-8901-2345-678901bcdef0] ERROR com.naagi.notification.service.NotificationService - Failed to send SMS notification for order ORD-11223\njava.io.IOException: SMS gateway unreachable: Connection refused\n\tat com.naagi.notification.gateway.TwilioGateway.sendSms(TwilioGateway.java:92)\n\tat com.naagi.notification.service.NotificationService.sendSms(NotificationService.java:120)\n\tat com.naagi.notification.service.NotificationService.notifyOrderFailure(NotificationService.java:85)","timestamp":"2026-01-30T10:16:10.000Z","level":"ERROR","correlationId":"b2c3d4e5-f6a7-8901-2345-678901bcdef0","stackTrace":"java.io.IOException: SMS gateway unreachable: Connection refused\n\tat com.naagi.notification.gateway.TwilioGateway.sendSms(TwilioGateway.java:92)\n\tat com.naagi.notification.service.NotificationService.sendSms(NotificationService.java:120)\n\tat com.naagi.notification.service.NotificationService.notifyOrderFailure(NotificationService.java:85)","serviceName":"notification-service","environment":"production"}
{"index":{}}
{"containerName":"app-inventory","namespace":"app-lambda-inventory","message":"2026-01-30T10:17:00.000Z [1a2b3c4d-5e6f-7a8b-9c0d-e1f2a3b4c5d6] INFO  com.naagi.inventory.service.InventoryService - Batch stock sync started, processing 250 SKUs from warehouse WH-WEST-02","timestamp":"2026-01-30T10:17:00.000Z","level":"INFO","correlationId":"1a2b3c4d-5e6f-7a8b-9c0d-e1f2a3b4c5d6","serviceName":"inventory-service","environment":"production"}
{"index":{}}
{"containerName":"app-inventory","namespace":"app-lambda-inventory","message":"2026-01-30T10:17:15.500Z [1a2b3c4d-5e6f-7a8b-9c0d-e1f2a3b4c5d6] INFO  com.naagi.inventory.service.InventoryService - Batch stock sync completed: 248 updated, 2 failed, duration: 15500ms","timestamp":"2026-01-30T10:17:15.500Z","level":"INFO","correlationId":"1a2b3c4d-5e6f-7a8b-9c0d-e1f2a3b4c5d6","serviceName":"inventory-service","environment":"production"}
{"index":{}}
{"containerName":"app-inventory","namespace":"app-lambda-inventory","message":"2026-01-30T10:17:15.600Z [1a2b3c4d-5e6f-7a8b-9c0d-e1f2a3b4c5d6] ERROR com.naagi.inventory.service.InventoryService - Failed to sync stock for 2 SKUs: [SKU-99881, SKU-99882]\njava.sql.BatchUpdateException: Deadlock found when trying to get lock\n\tat com.naagi.inventory.repository.InventoryRepository.batchUpdate(InventoryRepository.java:201)\n\tat com.naagi.inventory.service.InventoryService.syncStock(InventoryService.java:178)","timestamp":"2026-01-30T10:17:15.600Z","level":"ERROR","correlationId":"1a2b3c4d-5e6f-7a8b-9c0d-e1f2a3b4c5d6","stackTrace":"java.sql.BatchUpdateException: Deadlock found when trying to get lock\n\tat com.naagi.inventory.repository.InventoryRepository.batchUpdate(InventoryRepository.java:201)\n\tat com.naagi.inventory.service.InventoryService.syncStock(InventoryService.java:178)","serviceName":"inventory-service","environment":"production"}
{"index":{}}
{"containerName":"app-payment-gw","namespace":"app-lambda-payment","message":"2026-01-30T10:18:00.000Z [2b3c4d5e-6f7a-8b9c-0d1e-f2a3b4c5d6e7] INFO  com.naagi.payment.service.RefundService - Refund initiated for transaction TXN-33221100, amount: $49.99, reason: customer_request","timestamp":"2026-01-30T10:18:00.000Z","level":"INFO","correlationId":"2b3c4d5e-6f7a-8b9c-0d1e-f2a3b4c5d6e7","serviceName":"payment-service","environment":"production"}
{"index":{}}
{"containerName":"app-payment-gw","namespace":"app-lambda-payment","message":"2026-01-30T10:18:02.100Z [2b3c4d5e-6f7a-8b9c-0d1e-f2a3b4c5d6e7] INFO  com.naagi.payment.service.RefundService - Refund completed successfully for TXN-33221100, refundId: REF-55667788","timestamp":"2026-01-30T10:18:02.100Z","level":"INFO","correlationId":"2b3c4d5e-6f7a-8b9c-0d1e-f2a3b4c5d6e7","serviceName":"payment-service","environment":"production"}
{"index":{}}
{"containerName":"app-order-proc","namespace":"app-lambda-orders","message":"2026-01-30T10:19:00.000Z [3c4d5e6f-7a8b-9c0d-1e2f-a3b4c5d6e7f8] DEBUG com.naagi.order.repository.OrderRepository - Cache hit for order ORD-98765, TTL remaining: 245s","timestamp":"2026-01-30T10:19:00.000Z","level":"DEBUG","correlationId":"3c4d5e6f-7a8b-9c0d-1e2f-a3b4c5d6e7f8","serviceName":"order-service","environment":"production"}
{"index":{}}
{"containerName":"app-order-proc","namespace":"app-lambda-orders","message":"2026-01-30T10:19:30.000Z [4d5e6f7a-8b9c-0d1e-2f3a-b4c5d6e7f8a9] INFO  com.naagi.order.service.OrderService - Order status query: ORD-98765 -> CONFIRMED, last updated: 2026-01-30T10:15:30Z","timestamp":"2026-01-30T10:19:30.000Z","level":"INFO","correlationId":"4d5e6f7a-8b9c-0d1e-2f3a-b4c5d6e7f8a9","serviceName":"order-service","environment":"production"}

'

echo ""
echo "=== Sample data loaded ==="
echo ""

# Verify count
echo "Document count:"
curl -s "$ES_URL/$INDEX_NAME/_count" 2>&1
echo ""
echo ""

# Show sample query
echo "=== Sample queries ==="
echo ""
echo "1. All ERROR logs:"
curl -s "$ES_URL/$INDEX_NAME/_search?pretty" -H "Content-Type: application/json" -d '{
  "query": { "term": { "level": "ERROR" } },
  "size": 3,
  "_source": ["containerName", "level", "message", "timestamp"]
}'
echo ""
