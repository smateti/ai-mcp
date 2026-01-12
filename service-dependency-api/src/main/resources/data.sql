-- Insert 50 Applications
INSERT INTO applications (id, application_id, name, description, owner, status) VALUES
(1, 'APP-USER-MGMT', 'User Management System', 'Handles user authentication and profiles', 'Team Alpha', 'ACTIVE'),
(2, 'APP-ORDER-PROC', 'Order Processing System', 'Processes customer orders', 'Team Beta', 'ACTIVE'),
(3, 'APP-PAYMENT-GW', 'Payment Gateway', 'Handles payment transactions', 'Team Gamma', 'ACTIVE'),
(4, 'APP-INVENTORY', 'Inventory Management', 'Manages product inventory', 'Team Delta', 'ACTIVE'),
(5, 'APP-SHIPPING', 'Shipping Service', 'Coordinates product shipping', 'Team Epsilon', 'ACTIVE'),
(6, 'APP-NOTIFICATION', 'Notification System', 'Sends emails and SMS', 'Team Zeta', 'ACTIVE'),
(7, 'APP-ANALYTICS', 'Analytics Engine', 'Business intelligence and reports', 'Team Eta', 'ACTIVE'),
(8, 'APP-SEARCH', 'Search Service', 'Product and content search', 'Team Theta', 'ACTIVE'),
(9, 'APP-RECOMMEND', 'Recommendation Engine', 'Product recommendations', 'Team Iota', 'ACTIVE'),
(10, 'APP-PRICING', 'Pricing Service', 'Dynamic pricing calculations', 'Team Kappa', 'ACTIVE'),
(11, 'APP-TAX-CALC', 'Tax Calculator', 'Calculates taxes for orders', 'Team Lambda', 'ACTIVE'),
(12, 'APP-DISCOUNT', 'Discount Engine', 'Manages promotions and discounts', 'Team Mu', 'ACTIVE'),
(13, 'APP-REVIEW', 'Review System', 'Customer reviews and ratings', 'Team Nu', 'ACTIVE'),
(14, 'APP-WISHLIST', 'Wishlist Service', 'User wishlists and favorites', 'Team Xi', 'ACTIVE'),
(15, 'APP-CART', 'Shopping Cart', 'Shopping cart management', 'Team Omicron', 'ACTIVE'),
(16, 'APP-CATALOG', 'Product Catalog', 'Product information management', 'Team Pi', 'ACTIVE'),
(17, 'APP-MEDIA', 'Media Service', 'Image and video management', 'Team Rho', 'ACTIVE'),
(18, 'APP-FULFILLMENT', 'Order Fulfillment', 'Warehouse and fulfillment', 'Team Sigma', 'ACTIVE'),
(19, 'APP-RETURNS', 'Returns Management', 'Handles product returns', 'Team Tau', 'ACTIVE'),
(20, 'APP-LOYALTY', 'Loyalty Program', 'Customer loyalty and rewards', 'Team Upsilon', 'ACTIVE'),
(21, 'APP-CMS', 'Content Management', 'Website content management', 'Team Phi', 'ACTIVE'),
(22, 'APP-SUPPORT', 'Customer Support', 'Support tickets and chat', 'Team Chi', 'ACTIVE'),
(23, 'APP-FRAUD', 'Fraud Detection', 'Fraud prevention system', 'Team Psi', 'ACTIVE'),
(24, 'APP-VENDOR', 'Vendor Management', 'Third-party vendor coordination', 'Team Omega', 'ACTIVE'),
(25, 'APP-SUBSCRIPTION', 'Subscription Service', 'Recurring subscriptions', 'Team Alpha2', 'ACTIVE'),
(26, 'APP-BILLING', 'Billing System', 'Invoice and billing', 'Team Beta2', 'ACTIVE'),
(27, 'APP-REPORTING', 'Reporting Service', 'Business reports', 'Team Gamma2', 'ACTIVE'),
(28, 'APP-AUDIT', 'Audit Logger', 'System audit logs', 'Team Delta2', 'ACTIVE'),
(29, 'APP-CONFIG', 'Configuration Service', 'Application configuration', 'Team Epsilon2', 'ACTIVE'),
(30, 'APP-CACHE', 'Caching Service', 'Distributed caching', 'Team Zeta2', 'ACTIVE'),
(31, 'APP-SESSION', 'Session Management', 'User session handling', 'Team Eta2', 'ACTIVE'),
(32, 'APP-LOCALE', 'Localization Service', 'Multi-language support', 'Team Theta2', 'ACTIVE'),
(33, 'APP-CURRENCY', 'Currency Converter', 'Multi-currency support', 'Team Iota2', 'ACTIVE'),
(34, 'APP-GEO', 'Geolocation Service', 'Location-based services', 'Team Kappa2', 'ACTIVE'),
(35, 'APP-WEATHER', 'Weather Service', 'Weather information', 'Team Lambda2', 'ACTIVE'),
(36, 'APP-ADDRESS', 'Address Validation', 'Address verification', 'Team Mu2', 'ACTIVE'),
(37, 'APP-CREDIT', 'Credit Check', 'Credit verification', 'Team Nu2', 'ACTIVE'),
(38, 'APP-KYC', 'KYC Verification', 'Know Your Customer checks', 'Team Xi2', 'ACTIVE'),
(39, 'APP-COMPLIANCE', 'Compliance Service', 'Regulatory compliance', 'Team Omicron2', 'ACTIVE'),
(40, 'APP-EMAIL', 'Email Service', 'Email delivery', 'Team Pi2', 'ACTIVE'),
(41, 'APP-SMS', 'SMS Service', 'SMS notifications', 'Team Rho2', 'ACTIVE'),
(42, 'APP-PUSH', 'Push Notification', 'Mobile push notifications', 'Team Sigma2', 'ACTIVE'),
(43, 'APP-QUEUE', 'Message Queue', 'Asynchronous messaging', 'Team Tau2', 'ACTIVE'),
(44, 'APP-SCHEDULER', 'Job Scheduler', 'Background job scheduling', 'Team Upsilon2', 'ACTIVE'),
(45, 'APP-FILE', 'File Storage', 'Document storage', 'Team Phi2', 'ACTIVE'),
(46, 'APP-BACKUP', 'Backup Service', 'Data backup', 'Team Chi2', 'ACTIVE'),
(47, 'APP-MONITOR', 'Monitoring Service', 'System monitoring', 'Team Psi2', 'ACTIVE'),
(48, 'APP-HEALTH', 'Health Check', 'Service health monitoring', 'Team Omega2', 'ACTIVE'),
(49, 'APP-GATEWAY', 'API Gateway', 'API routing and management', 'Team Alpha3', 'ACTIVE'),
(50, 'APP-AUTH', 'Authorization Service', 'Permission and roles', 'Team Beta3', 'ACTIVE');

-- Insert Services (2-3 services per application, total ~100 services)
INSERT INTO services (id, service_id, name, description, endpoint, protocol, application_id) VALUES
-- APP-USER-MGMT services
(1, 'SVC-USER-001', 'User Authentication', 'Login and token generation', '/api/v1/auth', 'REST', 1),
(2, 'SVC-USER-002', 'User Profile', 'User profile management', '/api/v1/users', 'REST', 1),
-- APP-ORDER-PROC services
(3, 'SVC-ORDER-001', 'Order Creation', 'Create new orders', '/api/v1/orders', 'REST', 2),
(4, 'SVC-ORDER-002', 'Order Status', 'Check order status', '/api/v1/orders/status', 'REST', 2),
-- APP-PAYMENT-GW services
(5, 'SVC-PAY-001', 'Payment Processing', 'Process payments', '/api/v1/payments', 'REST', 3),
(6, 'SVC-PAY-002', 'Refund Service', 'Handle refunds', '/api/v1/refunds', 'REST', 3),
-- APP-INVENTORY services
(7, 'SVC-INV-001', 'Stock Check', 'Check product availability', '/api/v1/inventory/check', 'REST', 4),
(8, 'SVC-INV-002', 'Stock Update', 'Update inventory levels', '/api/v1/inventory/update', 'REST', 4),
-- APP-SHIPPING services
(9, 'SVC-SHIP-001', 'Shipping Calculator', 'Calculate shipping costs', '/api/v1/shipping/calculate', 'REST', 5),
(10, 'SVC-SHIP-002', 'Track Shipment', 'Track package location', '/api/v1/shipping/track', 'REST', 5),
-- Continue for remaining applications...
(11, 'SVC-NOTIF-001', 'Email Notification', 'Send email notifications', '/api/v1/notify/email', 'REST', 6),
(12, 'SVC-NOTIF-002', 'SMS Notification', 'Send SMS notifications', '/api/v1/notify/sms', 'REST', 6),
(13, 'SVC-ANALYTICS-001', 'Sales Report', 'Generate sales reports', '/api/v1/analytics/sales', 'REST', 7),
(14, 'SVC-ANALYTICS-002', 'User Metrics', 'User behavior analytics', '/api/v1/analytics/users', 'REST', 7),
(15, 'SVC-SEARCH-001', 'Product Search', 'Search products', '/api/v1/search/products', 'REST', 8),
(16, 'SVC-SEARCH-002', 'Autocomplete', 'Search suggestions', '/api/v1/search/suggest', 'REST', 8),
(17, 'SVC-RECOMMEND-001', 'Product Recommendations', 'Suggest products', '/api/v1/recommendations', 'REST', 9),
(18, 'SVC-PRICING-001', 'Price Calculator', 'Calculate product prices', '/api/v1/pricing/calculate', 'REST', 10),
(19, 'SVC-TAX-001', 'Tax Calculator', 'Calculate taxes', '/api/v1/tax/calculate', 'REST', 11),
(20, 'SVC-DISCOUNT-001', 'Apply Discount', 'Apply discount codes', '/api/v1/discounts/apply', 'REST', 12),
-- APP-REVIEW services
(21, 'SVC-REVIEW-001', 'Review Management', 'Manage customer reviews', '/api/v1/reviews', 'REST', 13),
(22, 'SVC-REVIEW-002', 'Rating Service', 'Product ratings', '/api/v1/ratings', 'REST', 13),
-- APP-WISHLIST services
(23, 'SVC-WISH-001', 'Wishlist Management', 'User wishlist operations', '/api/v1/wishlist', 'REST', 14),
-- APP-CART services
(24, 'SVC-CART-001', 'Cart Operations', 'Shopping cart management', '/api/v1/cart', 'REST', 15),
(25, 'SVC-CART-002', 'Cart Persistence', 'Save and restore cart', '/api/v1/cart/persist', 'REST', 15),
-- APP-CATALOG services
(26, 'SVC-CATALOG-001', 'Product Catalog', 'Product information', '/api/v1/catalog', 'REST', 16),
(27, 'SVC-CATALOG-002', 'Category Service', 'Product categories', '/api/v1/categories', 'REST', 16),
-- APP-MEDIA services
(28, 'SVC-MEDIA-001', 'Image Service', 'Image management', '/api/v1/media/images', 'REST', 17),
(29, 'SVC-MEDIA-002', 'Video Service', 'Video management', '/api/v1/media/videos', 'REST', 17),
-- APP-FULFILLMENT services
(30, 'SVC-FULFILL-001', 'Fulfillment Operations', 'Warehouse operations', '/api/v1/fulfillment', 'REST', 18),
(31, 'SVC-FULFILL-002', 'Picking Service', 'Order picking', '/api/v1/fulfillment/pick', 'REST', 18),
-- APP-RETURNS services
(32, 'SVC-RETURN-001', 'Return Management', 'Handle returns', '/api/v1/returns', 'REST', 19),
(33, 'SVC-RETURN-002', 'RMA Service', 'Return authorization', '/api/v1/returns/rma', 'REST', 19),
-- APP-LOYALTY services
(34, 'SVC-LOYALTY-001', 'Points Service', 'Loyalty points', '/api/v1/loyalty/points', 'REST', 20),
(35, 'SVC-LOYALTY-002', 'Rewards Service', 'Redeem rewards', '/api/v1/loyalty/rewards', 'REST', 20),
-- APP-CMS services
(36, 'SVC-CMS-001', 'Content Service', 'Content management', '/api/v1/cms/content', 'REST', 21),
(37, 'SVC-CMS-002', 'Page Service', 'Page management', '/api/v1/cms/pages', 'REST', 21),
-- APP-SUPPORT services
(38, 'SVC-SUPPORT-001', 'Ticket Service', 'Support tickets', '/api/v1/support/tickets', 'REST', 22),
(39, 'SVC-SUPPORT-002', 'Chat Service', 'Live chat support', '/api/v1/support/chat', 'REST', 22),
-- APP-FRAUD services
(40, 'SVC-FRAUD-001', 'Fraud Detection', 'Detect fraudulent activity', '/api/v1/fraud/check', 'REST', 23),
(41, 'SVC-FRAUD-002', 'Risk Scoring', 'Calculate risk score', '/api/v1/fraud/risk', 'REST', 23),
-- APP-VENDOR services
(42, 'SVC-VENDOR-001', 'Vendor Management', 'Manage vendors', '/api/v1/vendors', 'REST', 24),
(43, 'SVC-VENDOR-002', 'Vendor Orders', 'Vendor order processing', '/api/v1/vendors/orders', 'REST', 24),
-- APP-SUBSCRIPTION services
(44, 'SVC-SUB-001', 'Subscription Management', 'Manage subscriptions', '/api/v1/subscriptions', 'REST', 25),
(45, 'SVC-SUB-002', 'Recurring Billing', 'Handle recurring payments', '/api/v1/subscriptions/billing', 'REST', 25),
-- APP-BILLING services
(46, 'SVC-BILL-001', 'Invoice Service', 'Generate invoices', '/api/v1/billing/invoices', 'REST', 26),
(47, 'SVC-BILL-002', 'Statement Service', 'Billing statements', '/api/v1/billing/statements', 'REST', 26),
-- APP-REPORTING services
(48, 'SVC-REPORT-001', 'Report Generation', 'Generate reports', '/api/v1/reports', 'REST', 27),
(49, 'SVC-REPORT-002', 'Dashboard Service', 'Business dashboards', '/api/v1/reports/dashboard', 'REST', 27),
-- APP-AUDIT services
(50, 'SVC-AUDIT-001', 'Audit Logging', 'System audit logs', '/api/v1/audit/logs', 'REST', 28),
(51, 'SVC-AUDIT-002', 'Compliance Report', 'Compliance reporting', '/api/v1/audit/compliance', 'REST', 28),
-- APP-CONFIG services
(52, 'SVC-CONFIG-001', 'Configuration Management', 'App configuration', '/api/v1/config', 'REST', 29),
(53, 'SVC-CONFIG-002', 'Feature Flags', 'Feature toggles', '/api/v1/config/features', 'REST', 29),
-- APP-CACHE services
(54, 'SVC-CACHE-001', 'Cache Operations', 'Distributed cache', '/api/v1/cache', 'REST', 30),
-- APP-SESSION services
(55, 'SVC-SESSION-001', 'Session Management', 'User sessions', '/api/v1/sessions', 'REST', 31),
(56, 'SVC-SESSION-002', 'Session Store', 'Session persistence', '/api/v1/sessions/store', 'REST', 31),
-- APP-LOCALE services
(57, 'SVC-LOCALE-001', 'Translation Service', 'Multi-language', '/api/v1/locale/translate', 'REST', 32),
(58, 'SVC-LOCALE-002', 'Language Detection', 'Detect user language', '/api/v1/locale/detect', 'REST', 32),
-- APP-CURRENCY services
(59, 'SVC-CURRENCY-001', 'Currency Conversion', 'Convert currencies', '/api/v1/currency/convert', 'REST', 33),
(60, 'SVC-CURRENCY-002', 'Exchange Rates', 'Get exchange rates', '/api/v1/currency/rates', 'REST', 33),
-- APP-GEO services
(61, 'SVC-GEO-001', 'Geolocation', 'Location services', '/api/v1/geo/locate', 'REST', 34),
(62, 'SVC-GEO-002', 'Distance Calculator', 'Calculate distances', '/api/v1/geo/distance', 'REST', 34),
-- APP-WEATHER services
(63, 'SVC-WEATHER-001', 'Weather Information', 'Weather data', '/api/v1/weather', 'REST', 35),
-- APP-ADDRESS services
(64, 'SVC-ADDR-001', 'Address Validation', 'Validate addresses', '/api/v1/address/validate', 'REST', 36),
(65, 'SVC-ADDR-002', 'Address Autocomplete', 'Address suggestions', '/api/v1/address/suggest', 'REST', 36),
-- APP-CREDIT services
(66, 'SVC-CREDIT-001', 'Credit Check', 'Check credit score', '/api/v1/credit/check', 'REST', 37),
-- APP-KYC services
(67, 'SVC-KYC-001', 'KYC Verification', 'Identity verification', '/api/v1/kyc/verify', 'REST', 38),
(68, 'SVC-KYC-002', 'Document Verification', 'Verify documents', '/api/v1/kyc/documents', 'REST', 38),
-- APP-COMPLIANCE services
(69, 'SVC-COMPLY-001', 'Compliance Check', 'Regulatory compliance', '/api/v1/compliance/check', 'REST', 39),
-- APP-EMAIL services
(70, 'SVC-EMAIL-001', 'Email Delivery', 'Send emails', '/api/v1/email/send', 'REST', 40),
(71, 'SVC-EMAIL-002', 'Email Templates', 'Manage email templates', '/api/v1/email/templates', 'REST', 40),
-- APP-SMS services
(72, 'SVC-SMS-001', 'SMS Delivery', 'Send SMS', '/api/v1/sms/send', 'REST', 41),
-- APP-PUSH services
(73, 'SVC-PUSH-001', 'Push Notifications', 'Send push notifications', '/api/v1/push/send', 'REST', 42),
-- APP-QUEUE services
(74, 'SVC-QUEUE-001', 'Message Publishing', 'Publish messages', '/api/v1/queue/publish', 'REST', 43),
(75, 'SVC-QUEUE-002', 'Message Consumption', 'Consume messages', '/api/v1/queue/consume', 'REST', 43),
-- APP-SCHEDULER services
(76, 'SVC-SCHED-001', 'Job Scheduling', 'Schedule jobs', '/api/v1/scheduler/jobs', 'REST', 44),
(77, 'SVC-SCHED-002', 'Cron Management', 'Manage cron jobs', '/api/v1/scheduler/cron', 'REST', 44),
-- APP-FILE services
(78, 'SVC-FILE-001', 'File Upload', 'Upload files', '/api/v1/files/upload', 'REST', 45),
(79, 'SVC-FILE-002', 'File Download', 'Download files', '/api/v1/files/download', 'REST', 45),
-- APP-BACKUP services
(80, 'SVC-BACKUP-001', 'Backup Operations', 'Data backup', '/api/v1/backup', 'REST', 46),
-- APP-MONITOR services
(81, 'SVC-MONITOR-001', 'Metrics Collection', 'Collect metrics', '/api/v1/monitor/metrics', 'REST', 47),
(82, 'SVC-MONITOR-002', 'Alerting Service', 'Send alerts', '/api/v1/monitor/alerts', 'REST', 47),
-- APP-HEALTH services
(83, 'SVC-HEALTH-001', 'Health Check', 'Service health', '/api/v1/health', 'REST', 48),
-- APP-GATEWAY services
(84, 'SVC-GATEWAY-001', 'API Routing', 'Route API requests', '/api/v1/gateway/route', 'REST', 49),
(85, 'SVC-GATEWAY-002', 'Rate Limiting', 'API rate limiting', '/api/v1/gateway/ratelimit', 'REST', 49),
-- APP-AUTH services
(86, 'SVC-AUTH-001', 'Authorization', 'Check permissions', '/api/v1/auth/authorize', 'REST', 50),
(87, 'SVC-AUTH-002', 'Role Management', 'Manage user roles', '/api/v1/auth/roles', 'REST', 50);

-- Insert Operations (3-5 operations per service, total ~300 operations)
INSERT INTO operations (id, operation_id, name, description, http_method, path, service_id) VALUES
-- User Authentication operations
(1, 'OP-AUTH-LOGIN', 'User Login', 'Authenticate user credentials', 'POST', '/login', 1),
(2, 'OP-AUTH-LOGOUT', 'User Logout', 'Invalidate user session', 'POST', '/logout', 1),
(3, 'OP-AUTH-REFRESH', 'Refresh Token', 'Refresh authentication token', 'POST', '/refresh', 1),
(4, 'OP-AUTH-VALIDATE', 'Validate Token', 'Validate authentication token', 'GET', '/validate', 1),
-- User Profile operations
(5, 'OP-USER-GET', 'Get User Profile', 'Retrieve user information', 'GET', '/{userId}', 2),
(6, 'OP-USER-UPDATE', 'Update User Profile', 'Update user information', 'PUT', '/{userId}', 2),
(7, 'OP-USER-DELETE', 'Delete User', 'Delete user account', 'DELETE', '/{userId}', 2),
-- Order Creation operations
(8, 'OP-ORDER-CREATE', 'Create Order', 'Create a new order', 'POST', '/create', 3),
(9, 'OP-ORDER-VALIDATE', 'Validate Order', 'Validate order data', 'POST', '/validate', 3),
(10, 'OP-ORDER-CONFIRM', 'Confirm Order', 'Confirm order placement', 'POST', '/confirm', 3),
-- Order Status operations
(11, 'OP-ORDER-STATUS', 'Get Order Status', 'Check order status', 'GET', '/{orderId}/status', 4),
(12, 'OP-ORDER-HISTORY', 'Order History', 'Get order history', 'GET', '/{userId}/history', 4),
-- Payment Processing operations
(13, 'OP-PAY-PROCESS', 'Process Payment', 'Process payment transaction', 'POST', '/process', 5),
(14, 'OP-PAY-VERIFY', 'Verify Payment', 'Verify payment status', 'GET', '/{paymentId}/verify', 5),
(15, 'OP-PAY-CAPTURE', 'Capture Payment', 'Capture authorized payment', 'POST', '/{paymentId}/capture', 5),
-- Refund operations
(16, 'OP-REFUND-CREATE', 'Create Refund', 'Initiate refund', 'POST', '/create', 6),
(17, 'OP-REFUND-STATUS', 'Refund Status', 'Check refund status', 'GET', '/{refundId}/status', 6),
-- Inventory operations
(18, 'OP-INV-CHECK', 'Check Stock', 'Check product stock', 'GET', '/{productId}/check', 7),
(19, 'OP-INV-RESERVE', 'Reserve Stock', 'Reserve inventory', 'POST', '/{productId}/reserve', 7),
(20, 'OP-INV-RELEASE', 'Release Stock', 'Release reserved stock', 'POST', '/{productId}/release', 7),
(21, 'OP-INV-UPDATE', 'Update Stock', 'Update inventory count', 'PUT', '/{productId}/update', 8),
-- Shipping operations
(22, 'OP-SHIP-CALC', 'Calculate Shipping', 'Calculate shipping cost', 'POST', '/calculate', 9),
(23, 'OP-SHIP-CREATE', 'Create Shipment', 'Create shipping label', 'POST', '/create', 9),
(24, 'OP-SHIP-TRACK', 'Track Package', 'Track shipment', 'GET', '/{trackingId}/track', 10),
-- Notification operations
(25, 'OP-EMAIL-SEND', 'Send Email', 'Send email notification', 'POST', '/send', 11),
(26, 'OP-SMS-SEND', 'Send SMS', 'Send SMS notification', 'POST', '/send', 12),
-- Analytics operations
(27, 'OP-ANALYTICS-SALES', 'Sales Report', 'Generate sales report', 'GET', '/sales', 13),
(28, 'OP-ANALYTICS-USERS', 'User Analytics', 'User behavior report', 'GET', '/users', 14),
-- Search operations
(29, 'OP-SEARCH-PRODUCT', 'Search Products', 'Search for products', 'GET', '/products', 15),
(30, 'OP-SEARCH-SUGGEST', 'Search Autocomplete', 'Get search suggestions', 'GET', '/suggest', 16),
-- Recommendation operations
(31, 'OP-RECOMMEND-GET', 'Get Recommendations', 'Get product recommendations', 'GET', '/products', 17),
-- Pricing operations
(32, 'OP-PRICE-CALC', 'Calculate Price', 'Calculate product price', 'POST', '/calculate', 18),
-- Tax operations
(33, 'OP-TAX-CALC', 'Calculate Tax', 'Calculate order tax', 'POST', '/calculate', 19),
-- Discount operations
(34, 'OP-DISCOUNT-APPLY', 'Apply Discount', 'Apply discount code', 'POST', '/apply', 20),
-- Review operations
(35, 'OP-REVIEW-CREATE', 'Create Review', 'Submit product review', 'POST', '/create', 21),
(36, 'OP-REVIEW-UPDATE', 'Update Review', 'Update review', 'PUT', '/{reviewId}', 21),
(37, 'OP-REVIEW-DELETE', 'Delete Review', 'Delete review', 'DELETE', '/{reviewId}', 21),
(38, 'OP-REVIEW-GET', 'Get Reviews', 'Get product reviews', 'GET', '/product/{productId}', 21),
(39, 'OP-RATING-GET', 'Get Rating', 'Get product rating', 'GET', '/product/{productId}', 22),
-- Wishlist operations
(40, 'OP-WISH-ADD', 'Add to Wishlist', 'Add product to wishlist', 'POST', '/add', 23),
(41, 'OP-WISH-REMOVE', 'Remove from Wishlist', 'Remove item', 'DELETE', '/{itemId}', 23),
(42, 'OP-WISH-GET', 'Get Wishlist', 'Get user wishlist', 'GET', '/{userId}', 23),
-- Cart operations
(43, 'OP-CART-ADD', 'Add to Cart', 'Add item to cart', 'POST', '/add', 24),
(44, 'OP-CART-UPDATE', 'Update Cart Item', 'Update quantity', 'PUT', '/{itemId}', 24),
(45, 'OP-CART-REMOVE', 'Remove from Cart', 'Remove item', 'DELETE', '/{itemId}', 24),
(46, 'OP-CART-GET', 'Get Cart', 'Get user cart', 'GET', '/{userId}', 24),
(47, 'OP-CART-SAVE', 'Save Cart', 'Persist cart state', 'POST', '/save', 25),
(48, 'OP-CART-RESTORE', 'Restore Cart', 'Restore saved cart', 'GET', '/restore/{userId}', 25),
-- Catalog operations
(49, 'OP-CATALOG-GET', 'Get Product', 'Get product details', 'GET', '/{productId}', 26),
(50, 'OP-CATALOG-LIST', 'List Products', 'List all products', 'GET', '/list', 26),
(51, 'OP-CATALOG-UPDATE', 'Update Product', 'Update product info', 'PUT', '/{productId}', 26),
(52, 'OP-CATEGORY-GET', 'Get Category', 'Get category details', 'GET', '/{categoryId}', 27),
(53, 'OP-CATEGORY-LIST', 'List Categories', 'List all categories', 'GET', '/list', 27),
-- Media operations
(54, 'OP-IMAGE-UPLOAD', 'Upload Image', 'Upload product image', 'POST', '/upload', 28),
(55, 'OP-IMAGE-GET', 'Get Image', 'Retrieve image', 'GET', '/{imageId}', 28),
(56, 'OP-VIDEO-UPLOAD', 'Upload Video', 'Upload video', 'POST', '/upload', 29),
(57, 'OP-VIDEO-GET', 'Get Video', 'Retrieve video', 'GET', '/{videoId}', 29),
-- Fulfillment operations
(58, 'OP-FULFILL-CREATE', 'Create Fulfillment', 'Create fulfillment order', 'POST', '/create', 30),
(59, 'OP-FULFILL-STATUS', 'Fulfillment Status', 'Get fulfillment status', 'GET', '/{fulfillmentId}/status', 30),
(60, 'OP-PICK-CREATE', 'Create Pick Task', 'Create picking task', 'POST', '/create', 31),
(61, 'OP-PICK-COMPLETE', 'Complete Pick', 'Mark picking complete', 'POST', '/{pickId}/complete', 31),
-- Return operations
(62, 'OP-RETURN-CREATE', 'Create Return', 'Initiate product return', 'POST', '/create', 32),
(63, 'OP-RETURN-STATUS', 'Return Status', 'Check return status', 'GET', '/{returnId}/status', 32),
(64, 'OP-RMA-CREATE', 'Create RMA', 'Create return authorization', 'POST', '/create', 33),
(65, 'OP-RMA-APPROVE', 'Approve RMA', 'Approve return', 'POST', '/{rmaId}/approve', 33),
-- Loyalty operations
(66, 'OP-POINTS-GET', 'Get Points', 'Get user points balance', 'GET', '/{userId}/balance', 34),
(67, 'OP-POINTS-EARN', 'Earn Points', 'Add loyalty points', 'POST', '/earn', 34),
(68, 'OP-POINTS-SPEND', 'Spend Points', 'Redeem points', 'POST', '/spend', 34),
(69, 'OP-REWARD-GET', 'Get Rewards', 'Get available rewards', 'GET', '/available', 35),
(70, 'OP-REWARD-REDEEM', 'Redeem Reward', 'Redeem reward', 'POST', '/redeem', 35),
-- CMS operations
(71, 'OP-CONTENT-CREATE', 'Create Content', 'Create content', 'POST', '/create', 36),
(72, 'OP-CONTENT-UPDATE', 'Update Content', 'Update content', 'PUT', '/{contentId}', 36),
(73, 'OP-CONTENT-GET', 'Get Content', 'Retrieve content', 'GET', '/{contentId}', 36),
(74, 'OP-PAGE-CREATE', 'Create Page', 'Create page', 'POST', '/create', 37),
(75, 'OP-PAGE-GET', 'Get Page', 'Retrieve page', 'GET', '/{pageId}', 37),
-- Support operations
(76, 'OP-TICKET-CREATE', 'Create Ticket', 'Create support ticket', 'POST', '/create', 38),
(77, 'OP-TICKET-UPDATE', 'Update Ticket', 'Update ticket', 'PUT', '/{ticketId}', 38),
(78, 'OP-TICKET-GET', 'Get Ticket', 'Retrieve ticket', 'GET', '/{ticketId}', 38),
(79, 'OP-CHAT-START', 'Start Chat', 'Start chat session', 'POST', '/start', 39),
(80, 'OP-CHAT-MESSAGE', 'Send Message', 'Send chat message', 'POST', '/{chatId}/message', 39),
-- Fraud operations
(81, 'OP-FRAUD-CHECK', 'Check Fraud', 'Detect fraud', 'POST', '/check', 40),
(82, 'OP-FRAUD-REPORT', 'Report Fraud', 'Report fraudulent activity', 'POST', '/report', 40),
(83, 'OP-RISK-CALC', 'Calculate Risk', 'Calculate risk score', 'POST', '/calculate', 41),
-- Vendor operations
(84, 'OP-VENDOR-CREATE', 'Create Vendor', 'Register vendor', 'POST', '/create', 42),
(85, 'OP-VENDOR-GET', 'Get Vendor', 'Get vendor details', 'GET', '/{vendorId}', 42),
(86, 'OP-VENDOR-ORDER', 'Create Vendor Order', 'Create order to vendor', 'POST', '/create', 43),
-- Subscription operations
(87, 'OP-SUB-CREATE', 'Create Subscription', 'Create subscription', 'POST', '/create', 44),
(88, 'OP-SUB-CANCEL', 'Cancel Subscription', 'Cancel subscription', 'POST', '/{subId}/cancel', 44),
(89, 'OP-SUB-RENEW', 'Renew Subscription', 'Renew subscription', 'POST', '/{subId}/renew', 44),
(90, 'OP-BILLING-PROCESS', 'Process Billing', 'Process recurring payment', 'POST', '/process', 45),
-- Billing operations
(91, 'OP-INVOICE-CREATE', 'Create Invoice', 'Generate invoice', 'POST', '/create', 46),
(92, 'OP-INVOICE-GET', 'Get Invoice', 'Retrieve invoice', 'GET', '/{invoiceId}', 46),
(93, 'OP-STATEMENT-GET', 'Get Statement', 'Get billing statement', 'GET', '/{userId}/statement', 47),
-- Reporting operations
(94, 'OP-REPORT-GEN', 'Generate Report', 'Generate custom report', 'POST', '/generate', 48),
(95, 'OP-REPORT-GET', 'Get Report', 'Retrieve report', 'GET', '/{reportId}', 48),
(96, 'OP-DASHBOARD-GET', 'Get Dashboard', 'Get dashboard data', 'GET', '/data', 49),
-- Audit operations
(97, 'OP-AUDIT-LOG', 'Log Audit Event', 'Create audit log', 'POST', '/log', 50),
(98, 'OP-AUDIT-GET', 'Get Audit Logs', 'Retrieve audit logs', 'GET', '/logs', 50),
(99, 'OP-COMPLY-REPORT', 'Compliance Report', 'Generate compliance report', 'GET', '/report', 51),
-- Config operations
(100, 'OP-CONFIG-GET', 'Get Config', 'Retrieve configuration', 'GET', '/{key}', 52),
(101, 'OP-CONFIG-SET', 'Set Config', 'Update configuration', 'PUT', '/{key}', 52),
(102, 'OP-FEATURE-GET', 'Get Feature Flag', 'Get feature flag status', 'GET', '/{feature}', 53),
(103, 'OP-FEATURE-SET', 'Set Feature Flag', 'Update feature flag', 'PUT', '/{feature}', 53),
-- Cache operations
(104, 'OP-CACHE-GET', 'Get Cache', 'Retrieve cached value', 'GET', '/{key}', 54),
(105, 'OP-CACHE-SET', 'Set Cache', 'Store cached value', 'PUT', '/{key}', 54),
(106, 'OP-CACHE-DEL', 'Delete Cache', 'Invalidate cache', 'DELETE', '/{key}', 54),
-- Session operations
(107, 'OP-SESSION-CREATE', 'Create Session', 'Create user session', 'POST', '/create', 55),
(108, 'OP-SESSION-GET', 'Get Session', 'Retrieve session', 'GET', '/{sessionId}', 55),
(109, 'OP-SESSION-DELETE', 'Delete Session', 'Invalidate session', 'DELETE', '/{sessionId}', 55),
(110, 'OP-SESSION-SAVE', 'Save Session', 'Persist session', 'POST', '/save', 56),
-- Locale operations
(111, 'OP-TRANSLATE', 'Translate Text', 'Translate to language', 'POST', '/translate', 57),
(112, 'OP-DETECT-LANG', 'Detect Language', 'Auto-detect language', 'POST', '/detect', 58),
-- Currency operations
(113, 'OP-CURRENCY-CONVERT', 'Convert Currency', 'Convert between currencies', 'POST', '/convert', 59),
(114, 'OP-RATE-GET', 'Get Exchange Rate', 'Get current rates', 'GET', '/rates', 60),
-- Geo operations
(115, 'OP-GEO-LOCATE', 'Geolocate', 'Get location from IP', 'POST', '/locate', 61),
(116, 'OP-DISTANCE-CALC', 'Calculate Distance', 'Calculate distance between points', 'POST', '/calculate', 62),
-- Weather operations
(117, 'OP-WEATHER-GET', 'Get Weather', 'Get weather for location', 'GET', '/current', 63),
(118, 'OP-FORECAST-GET', 'Get Forecast', 'Get weather forecast', 'GET', '/forecast', 63),
-- Address operations
(119, 'OP-ADDR-VALIDATE', 'Validate Address', 'Validate address', 'POST', '/validate', 64),
(120, 'OP-ADDR-SUGGEST', 'Address Suggestions', 'Get address suggestions', 'GET', '/suggest', 65),
-- Credit operations
(121, 'OP-CREDIT-CHECK', 'Check Credit', 'Run credit check', 'POST', '/check', 66),
(122, 'OP-CREDIT-SCORE', 'Get Credit Score', 'Get credit score', 'GET', '/{userId}/score', 66),
-- KYC operations
(123, 'OP-KYC-VERIFY', 'Verify Identity', 'Verify user identity', 'POST', '/verify', 67),
(124, 'OP-DOC-VERIFY', 'Verify Document', 'Verify ID document', 'POST', '/document/verify', 68),
-- Compliance operations
(125, 'OP-COMPLY-CHECK', 'Check Compliance', 'Run compliance check', 'POST', '/check', 69),
(126, 'OP-COMPLY-VALIDATE', 'Validate Compliance', 'Validate compliance', 'GET', '/{entity}/validate', 69),
-- Email operations
(127, 'OP-EMAIL-SEND', 'Send Email', 'Send email message', 'POST', '/send', 70),
(128, 'OP-EMAIL-TEMPLATE-GET', 'Get Email Template', 'Retrieve template', 'GET', '/{templateId}', 71),
-- SMS operations
(129, 'OP-SMS-SEND', 'Send SMS', 'Send SMS message', 'POST', '/send', 72),
-- Push operations
(130, 'OP-PUSH-SEND', 'Send Push', 'Send push notification', 'POST', '/send', 73),
-- Queue operations
(131, 'OP-QUEUE-PUBLISH', 'Publish Message', 'Publish to queue', 'POST', '/publish', 74),
(132, 'OP-QUEUE-CONSUME', 'Consume Message', 'Consume from queue', 'GET', '/consume', 75),
-- Scheduler operations
(133, 'OP-JOB-SCHEDULE', 'Schedule Job', 'Schedule background job', 'POST', '/schedule', 76),
(134, 'OP-JOB-CANCEL', 'Cancel Job', 'Cancel scheduled job', 'DELETE', '/{jobId}', 76),
(135, 'OP-CRON-CREATE', 'Create Cron Job', 'Create cron job', 'POST', '/create', 77),
-- File operations
(136, 'OP-FILE-UPLOAD', 'Upload File', 'Upload file', 'POST', '/upload', 78),
(137, 'OP-FILE-DOWNLOAD', 'Download File', 'Download file', 'GET', '/{fileId}/download', 79),
-- Backup operations
(138, 'OP-BACKUP-CREATE', 'Create Backup', 'Initiate backup', 'POST', '/create', 80),
(139, 'OP-BACKUP-RESTORE', 'Restore Backup', 'Restore from backup', 'POST', '/{backupId}/restore', 80),
-- Monitor operations
(140, 'OP-METRIC-COLLECT', 'Collect Metrics', 'Collect system metrics', 'POST', '/collect', 81),
(141, 'OP-METRIC-GET', 'Get Metrics', 'Retrieve metrics', 'GET', '/metrics', 81),
(142, 'OP-ALERT-SEND', 'Send Alert', 'Send monitoring alert', 'POST', '/send', 82),
-- Health operations
(143, 'OP-HEALTH-CHECK', 'Health Check', 'Check service health', 'GET', '/check', 83),
-- Gateway operations
(144, 'OP-ROUTE-REQUEST', 'Route Request', 'Route API request', 'POST', '/route', 84),
(145, 'OP-RATELIMIT-CHECK', 'Check Rate Limit', 'Verify rate limit', 'GET', '/check', 85),
-- Auth operations
(146, 'OP-AUTHORIZE', 'Authorize', 'Check authorization', 'POST', '/check', 86),
(147, 'OP-ROLE-GET', 'Get Roles', 'Get user roles', 'GET', '/{userId}/roles', 87),
(148, 'OP-ROLE-ASSIGN', 'Assign Role', 'Assign role to user', 'POST', '/{userId}/assign', 87);

-- Insert Operation Dependencies (showing inter-service dependencies)
INSERT INTO operation_dependencies (id, operation_id, dependent_application_id, dependent_service_id, dependent_operation_id, dependency_type, description) VALUES
-- Order Creation depends on User Auth, Inventory, and Pricing
(1, 8, 'APP-USER-MGMT', 'SVC-USER-001', 'OP-AUTH-VALIDATE', 'SYNC', 'Validate user authentication before creating order'),
(2, 8, 'APP-INVENTORY', 'SVC-INV-001', 'OP-INV-CHECK', 'SYNC', 'Check product availability'),
(3, 8, 'APP-INVENTORY', 'SVC-INV-001', 'OP-INV-RESERVE', 'SYNC', 'Reserve inventory for order'),
(4, 8, 'APP-PRICING', 'SVC-PRICING-001', 'OP-PRICE-CALC', 'SYNC', 'Calculate order price'),
(5, 8, 'APP-TAX-CALC', 'SVC-TAX-001', 'OP-TAX-CALC', 'SYNC', 'Calculate order tax'),
(6, 8, 'APP-DISCOUNT', 'SVC-DISCOUNT-001', 'OP-DISCOUNT-APPLY', 'OPTIONAL', 'Apply discount if available'),
-- Payment Processing depends on Order and Fraud Detection
(7, 13, 'APP-ORDER-PROC', 'SVC-ORDER-002', 'OP-ORDER-STATUS', 'SYNC', 'Verify order exists'),
(8, 13, 'APP-FRAUD', 'SVC-FRAUD-001', 'OP-FRAUD-CHECK', 'SYNC', 'Check for fraud'),
(9, 13, 'APP-USER-MGMT', 'SVC-USER-002', 'OP-USER-GET', 'SYNC', 'Get user payment information'),
-- Order Confirmation depends on Payment and Shipping
(10, 10, 'APP-PAYMENT-GW', 'SVC-PAY-001', 'OP-PAY-PROCESS', 'SYNC', 'Process payment'),
(11, 10, 'APP-SHIPPING', 'SVC-SHIP-001', 'OP-SHIP-CALC', 'SYNC', 'Calculate shipping cost'),
(12, 10, 'APP-SHIPPING', 'SVC-SHIP-001', 'OP-SHIP-CREATE', 'ASYNC', 'Create shipping label'),
(13, 10, 'APP-NOTIFICATION', 'SVC-NOTIF-001', 'OP-EMAIL-SEND', 'ASYNC', 'Send order confirmation email'),
-- Refund depends on Order and Payment
(14, 16, 'APP-ORDER-PROC', 'SVC-ORDER-002', 'OP-ORDER-STATUS', 'SYNC', 'Verify order status'),
(15, 16, 'APP-PAYMENT-GW', 'SVC-PAY-001', 'OP-PAY-VERIFY', 'SYNC', 'Verify original payment'),
(16, 16, 'APP-INVENTORY', 'SVC-INV-002', 'OP-INV-UPDATE', 'ASYNC', 'Update inventory after refund'),
-- User Profile Update depends on Auth
(17, 6, 'APP-USER-MGMT', 'SVC-USER-001', 'OP-AUTH-VALIDATE', 'SYNC', 'Validate user session'),
-- Search depends on Catalog and Pricing
(18, 29, 'APP-CATALOG', 'SVC-CATALOG-001', 'OP-CATALOG-GET', 'SYNC', 'Get product information'),
(19, 29, 'APP-PRICING', 'SVC-PRICING-001', 'OP-PRICE-CALC', 'SYNC', 'Get current prices'),
-- Recommendations depend on User Profile and Analytics
(20, 31, 'APP-USER-MGMT', 'SVC-USER-002', 'OP-USER-GET', 'SYNC', 'Get user preferences'),
(21, 31, 'APP-ANALYTICS', 'SVC-ANALYTICS-002', 'OP-ANALYTICS-USERS', 'SYNC', 'Get user behavior data'),
-- Cart operations dependencies
(22, 43, 'APP-USER-MGMT', 'SVC-USER-001', 'OP-AUTH-VALIDATE', 'SYNC', 'Validate user session before cart operation'),
(23, 43, 'APP-INVENTORY', 'SVC-INV-001', 'OP-INV-CHECK', 'SYNC', 'Check if item is in stock'),
(24, 43, 'APP-PRICING', 'SVC-PRICING-001', 'OP-PRICE-CALC', 'SYNC', 'Get current item price'),
-- Wishlist dependencies
(25, 40, 'APP-USER-MGMT', 'SVC-USER-001', 'OP-AUTH-VALIDATE', 'SYNC', 'Validate user'),
(26, 40, 'APP-CATALOG', 'SVC-CATALOG-001', 'OP-CATALOG-GET', 'SYNC', 'Verify product exists'),
-- Review dependencies
(27, 35, 'APP-USER-MGMT', 'SVC-USER-001', 'OP-AUTH-VALIDATE', 'SYNC', 'Authenticate user for review'),
(28, 35, 'APP-ORDER-PROC', 'SVC-ORDER-002', 'OP-ORDER-HISTORY', 'SYNC', 'Verify user purchased product'),
(29, 35, 'APP-CATALOG', 'SVC-CATALOG-001', 'OP-CATALOG-GET', 'SYNC', 'Verify product exists'),
-- Fulfillment dependencies
(30, 58, 'APP-ORDER-PROC', 'SVC-ORDER-002', 'OP-ORDER-STATUS', 'SYNC', 'Get order details'),
(31, 58, 'APP-INVENTORY', 'SVC-INV-001', 'OP-INV-CHECK', 'SYNC', 'Verify stock availability'),
(32, 58, 'APP-SHIPPING', 'SVC-SHIP-001', 'OP-SHIP-CREATE', 'SYNC', 'Create shipping label'),
(33, 61, 'APP-INVENTORY', 'SVC-INV-002', 'OP-INV-UPDATE', 'SYNC', 'Update inventory after pick'),
-- Loyalty points dependencies
(34, 67, 'APP-ORDER-PROC', 'SVC-ORDER-002', 'OP-ORDER-STATUS', 'SYNC', 'Verify order completion'),
(35, 67, 'APP-USER-MGMT', 'SVC-USER-002', 'OP-USER-GET', 'SYNC', 'Get user account info'),
(36, 68, 'APP-LOYALTY', 'SVC-LOYALTY-001', 'OP-POINTS-GET', 'SYNC', 'Check points balance'),
-- Subscription billing dependencies
(37, 90, 'APP-SUBSCRIPTION', 'SVC-SUB-001', 'OP-SUB-RENEW', 'SYNC', 'Verify subscription is active'),
(38, 90, 'APP-PAYMENT-GW', 'SVC-PAY-001', 'OP-PAY-PROCESS', 'SYNC', 'Process recurring payment'),
(39, 90, 'APP-NOTIFICATION', 'SVC-NOTIF-001', 'OP-EMAIL-SEND', 'ASYNC', 'Send payment confirmation'),
-- Invoice generation dependencies
(40, 91, 'APP-ORDER-PROC', 'SVC-ORDER-002', 'OP-ORDER-STATUS', 'SYNC', 'Get order details'),
(41, 91, 'APP-TAX-CALC', 'SVC-TAX-001', 'OP-TAX-CALC', 'SYNC', 'Calculate tax for invoice'),
(42, 91, 'APP-USER-MGMT', 'SVC-USER-002', 'OP-USER-GET', 'SYNC', 'Get billing address'),
-- Support ticket dependencies
(43, 76, 'APP-USER-MGMT', 'SVC-USER-001', 'OP-AUTH-VALIDATE', 'SYNC', 'Authenticate user'),
(44, 76, 'APP-ORDER-PROC', 'SVC-ORDER-002', 'OP-ORDER-HISTORY', 'OPTIONAL', 'Link ticket to order if applicable'),
(45, 76, 'APP-NOTIFICATION', 'SVC-NOTIF-001', 'OP-EMAIL-SEND', 'ASYNC', 'Send ticket confirmation email'),
-- KYC verification dependencies
(46, 123, 'APP-USER-MGMT', 'SVC-USER-002', 'OP-USER-GET', 'SYNC', 'Get user information'),
(47, 123, 'APP-COMPLIANCE', 'SVC-COMPLY-001', 'OP-COMPLY-CHECK', 'SYNC', 'Check regulatory compliance'),
(48, 124, 'APP-KYC', 'SVC-KYC-001', 'OP-KYC-VERIFY', 'SYNC', 'Verify identity before document check'),
-- Currency conversion dependencies
(49, 113, 'APP-CURRENCY', 'SVC-CURRENCY-002', 'OP-RATE-GET', 'SYNC', 'Get latest exchange rates'),
-- Address validation dependencies
(50, 119, 'APP-GEO', 'SVC-GEO-001', 'OP-GEO-LOCATE', 'SYNC', 'Verify location coordinates'),
-- Weather-based shipping dependencies
(51, 22, 'APP-WEATHER', 'SVC-WEATHER-001', 'OP-WEATHER-GET', 'OPTIONAL', 'Check weather for shipping delays'),
(52, 22, 'APP-GEO', 'SVC-GEO-002', 'OP-DISTANCE-CALC', 'SYNC', 'Calculate shipping distance'),
-- Report generation dependencies
(53, 94, 'APP-ANALYTICS', 'SVC-ANALYTICS-001', 'OP-ANALYTICS-SALES', 'SYNC', 'Get sales data'),
(54, 94, 'APP-ANALYTICS', 'SVC-ANALYTICS-002', 'OP-ANALYTICS-USERS', 'SYNC', 'Get user analytics'),
(55, 94, 'APP-ORDER-PROC', 'SVC-ORDER-002', 'OP-ORDER-HISTORY', 'SYNC', 'Get order history'),
-- Audit logging dependencies
(56, 97, 'APP-USER-MGMT', 'SVC-USER-001', 'OP-AUTH-VALIDATE', 'SYNC', 'Identify user for audit trail'),
(57, 97, 'APP-CONFIG', 'SVC-CONFIG-001', 'OP-CONFIG-GET', 'SYNC', 'Get audit configuration'),
-- Gateway routing dependencies
(58, 144, 'APP-AUTH', 'SVC-AUTH-001', 'OP-AUTHORIZE', 'SYNC', 'Check permissions before routing'),
(59, 144, 'APP-GATEWAY', 'SVC-GATEWAY-002', 'OP-RATELIMIT-CHECK', 'SYNC', 'Check rate limits'),
(60, 144, 'APP-AUDIT', 'SVC-AUDIT-001', 'OP-AUDIT-LOG', 'ASYNC', 'Log API call'),
-- Feature flag dependencies
(61, 102, 'APP-CONFIG', 'SVC-CONFIG-001', 'OP-CONFIG-GET', 'SYNC', 'Get feature configuration'),
(62, 102, 'APP-AUTH', 'SVC-AUTH-001', 'OP-AUTHORIZE', 'SYNC', 'Check if user can access feature'),
-- Session management dependencies
(63, 107, 'APP-USER-MGMT', 'SVC-USER-001', 'OP-AUTH-LOGIN', 'SYNC', 'Authenticate before session creation'),
(64, 107, 'APP-CACHE', 'SVC-CACHE-001', 'OP-CACHE-SET', 'ASYNC', 'Cache session data'),
(65, 109, 'APP-CACHE', 'SVC-CACHE-001', 'OP-CACHE-DEL', 'ASYNC', 'Remove cached session'),
-- Vendor order dependencies
(66, 86, 'APP-INVENTORY', 'SVC-INV-001', 'OP-INV-CHECK', 'SYNC', 'Check inventory levels'),
(67, 86, 'APP-VENDOR', 'SVC-VENDOR-001', 'OP-VENDOR-GET', 'SYNC', 'Get vendor information'),
(68, 86, 'APP-PAYMENT-GW', 'SVC-PAY-001', 'OP-PAY-PROCESS', 'SYNC', 'Process vendor payment'),
-- Media upload dependencies
(69, 54, 'APP-USER-MGMT', 'SVC-USER-001', 'OP-AUTH-VALIDATE', 'SYNC', 'Validate user'),
(70, 54, 'APP-FILE', 'SVC-FILE-001', 'OP-FILE-UPLOAD', 'SYNC', 'Store file'),
-- Backup dependencies
(71, 138, 'APP-MONITOR', 'SVC-MONITOR-001', 'OP-METRIC-COLLECT', 'ASYNC', 'Collect backup metrics'),
(72, 138, 'APP-NOTIFICATION', 'SVC-NOTIF-001', 'OP-EMAIL-SEND', 'ASYNC', 'Notify backup completion'),
-- Monitoring alert dependencies
(73, 142, 'APP-NOTIFICATION', 'SVC-NOTIF-001', 'OP-EMAIL-SEND', 'ASYNC', 'Send email alert'),
(74, 142, 'APP-SMS', 'SVC-SMS-001', 'OP-SMS-SEND', 'ASYNC', 'Send SMS alert'),
(75, 142, 'APP-PUSH', 'SVC-PUSH-001', 'OP-PUSH-SEND', 'ASYNC', 'Send push notification alert'),
-- Product catalog dependencies
(76, 49, 'APP-PRICING', 'SVC-PRICING-001', 'OP-PRICE-CALC', 'SYNC', 'Get product price'),
(77, 49, 'APP-INVENTORY', 'SVC-INV-001', 'OP-INV-CHECK', 'SYNC', 'Get stock status'),
(78, 49, 'APP-MEDIA', 'SVC-MEDIA-001', 'OP-IMAGE-GET', 'SYNC', 'Get product images'),
(79, 49, 'APP-REVIEW', 'SVC-REVIEW-002', 'OP-RATING-GET', 'OPTIONAL', 'Get product ratings'),
-- CMS page dependencies
(80, 74, 'APP-MEDIA', 'SVC-MEDIA-001', 'OP-IMAGE-GET', 'OPTIONAL', 'Get page images'),
(81, 74, 'APP-USER-MGMT', 'SVC-USER-001', 'OP-AUTH-VALIDATE', 'SYNC', 'Authenticate content creator'),
(82, 74, 'APP-LOCALE', 'SVC-LOCALE-001', 'OP-TRANSLATE', 'OPTIONAL', 'Translate page content'),
-- Credit check dependencies
(83, 121, 'APP-USER-MGMT', 'SVC-USER-002', 'OP-USER-GET', 'SYNC', 'Get user details'),
(84, 121, 'APP-COMPLIANCE', 'SVC-COMPLY-001', 'OP-COMPLY-CHECK', 'SYNC', 'Verify compliance'),
(85, 121, 'APP-KYC', 'SVC-KYC-001', 'OP-KYC-VERIFY', 'SYNC', 'Verify identity'),
-- Job scheduler dependencies
(86, 133, 'APP-AUTH', 'SVC-AUTH-001', 'OP-AUTHORIZE', 'SYNC', 'Authorize job scheduling'),
(87, 133, 'APP-QUEUE', 'SVC-QUEUE-001', 'OP-QUEUE-PUBLISH', 'ASYNC', 'Queue job for execution'),
-- Email notification dependencies
(88, 127, 'APP-EMAIL', 'SVC-EMAIL-002', 'OP-EMAIL-TEMPLATE-GET', 'SYNC', 'Get email template'),
(89, 127, 'APP-LOCALE', 'SVC-LOCALE-001', 'OP-TRANSLATE', 'OPTIONAL', 'Translate email content'),
(90, 127, 'APP-USER-MGMT', 'SVC-USER-002', 'OP-USER-GET', 'SYNC', 'Get user email preferences');
