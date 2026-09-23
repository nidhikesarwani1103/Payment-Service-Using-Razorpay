# Payment Service

A Spring Boot backend service that integrates with **Razorpay Test Mode** to demonstrate how a real-world payment service can create payment orders, launch checkout, receive asynchronous payment events through webhooks, and reconcile payment status when webhooks are missed.

This project is built as a backend/SDE3 learning project with a focus on **distributed-system concepts, external API integration, asynchronous events, idempotency, failure handling, and scheduled reconciliation**.

---

## 1. Architecture

```text
                         ┌─────────────────────┐
                         │  Authorization      │
                         │  Server :8080       │
                         │  OAuth2 + JWT        │
                         └──────────┬──────────┘
                                    │
                                    │ JWT
                                    ▼
                         ┌─────────────────────┐
                         │   Product Service   │
                         │       :8081         │
                         └──────────┬──────────┘
                                    │
                                    │ Create Payment
                                    ▼
                         ┌─────────────────────┐
                         │   Payment Service   │
                         │       :8082         │
                         └──────────┬──────────┘
                                    │
                                    │ REST API
                                    ▼
                         ┌─────────────────────┐
                         │ Razorpay Test Mode  │
                         └──────────┬──────────┘
                                    │
                              Payment result
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │ Razorpay Webhook    │
                         │ payment.captured     │
                         │ payment.failed      │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │   Payment Service   │
                         │   Update DB Status   │
                         └─────────────────────┘
```

The Payment Service also has two scheduled jobs:

```text
PaymentExpiryJob
    │
    └── CREATED + expiresAt passed
              ↓
           EXPIRED


PaymentReconciliationJob
    │
    └── CREATED / PENDING
              ↓
       Ask Razorpay for
       payments on order
              ↓
       SUCCESS / FAILED
```

---

# 2. Technology Stack

* Java 24
* Spring Boot 4.1.1
* Spring Framework 7
* Maven
* Spring Web / `RestClient`
* Spring Data JPA
* Hibernate
* MySQL
* Razorpay Test Mode
* Ngrok for local webhook development
* OAuth2/JWT integration with the project's Authorization Server
* Micrometer/Spring scheduling infrastructure

---

# 3. Why a Payment Service?

The goal was not to build a payment processor.

Instead, the service demonstrates how an application integrates with an external payment provider.

The application owns the **business payment record**, while Razorpay owns the actual payment processing.

This creates two different sources of state:

```text
Our Database                         Razorpay

Payment                              Order
--------------------------------     ----------------
id                                   id
providerOrderId                     amount
providerPaymentId                   currency
amount                              status
currency                            payments[]
status
createdAt
expiresAt
```

The service therefore needs mechanisms to keep its local state consistent with the payment provider.

---

# 4. Payment Lifecycle

A payment starts in:

```text
CREATED
```

because the application has created a payment/order but has not yet received confirmation that money was successfully captured.

Possible states:

```text
                 ┌──────────────┐
                 │   CREATED    │
                 └──────┬───────┘
                        │
              ┌─────────┼─────────┐
              │         │         │
              ▼         ▼         ▼
          SUCCESS     FAILED   EXPIRED
```

`PENDING` is available as an intermediate state for future payment-attempt handling.

`REFUNDED` is reserved for the future refund workflow.

### PaymentStatus

```java
CREATED
PENDING
SUCCESS
FAILED
EXPIRED
REFUNDED
```

---

# 5. Step 1 — Create a Payment

The client sends:

```http
POST /payments
Content-Type: application/json
```

Example:

```json
{
  "amount": 49900,
  "currency": "INR"
}
```

The amount is represented in the smallest currency unit.

For INR:

```text
49900 paise = ₹499
```

The Payment Service then:

1. Generates a receipt.
2. Creates a Razorpay order through the Razorpay API.
3. Receives the Razorpay order ID.
4. Creates our local `Payment` entity.
5. Stores the Razorpay order ID as `providerOrderId`.
6. Sets the initial status to `CREATED`.
7. Sets `createdAt`, `updatedAt`, and `expiresAt`.
8. Saves the payment in MySQL.

---

# 6. Why Store `providerOrderId`?

The application has its own payment ID:

```text
payment.id
```

Razorpay has its own order ID:

```text
providerOrderId
```

These are different identifiers belonging to different systems.

Example:

```text
Our system:

payment.id = 15

Razorpay:

providerOrderId = order_TfRSdn6LzwBA6D
```

The provider order ID is later required to:

* open Razorpay Checkout
* associate payments with the order
* reconcile payment status
* process webhook events

---

# 7. External API Integration

Instead of putting Razorpay HTTP calls directly inside `PaymentService`, the project uses a dedicated client:

```text
PaymentService
      │
      ▼
RazorpayClient
      │
      ▼
Razorpay API
```

This separates:

```text
Business logic
```

from:

```text
External HTTP communication
```

The client uses Spring's `RestClient`.

---

# 8. How the Razorpay API Integration Was Designed

The important principle used here was:

> Don't invent the external API response structure. Derive it from the provider's API documentation.

The process was:

```text
Requirement
    ↓
What information do we need?
    ↓
Find the provider API
    ↓
Read endpoint documentation
    ↓
Read request parameters
    ↓
Read response parameters
    ↓
Look at example response
    ↓
Create only the DTO fields we actually need
```

For example, reconciliation requires:

> "What payments exist for this Razorpay order?"

The relevant provider API is:

```http
GET /v1/orders/{order_id}/payments
```

The response contains a collection of payments.

We therefore model:

```java
RazorpayPaymentsResponse
```

containing:

```java
entity
count
items
```

and each payment contains the fields required by our reconciliation logic:

```java
id
amount
currency
status
orderId
```

We intentionally do not copy every field returned by Razorpay into our DTO.

---

# 9. Razorpay Checkout

The frontend loads the Razorpay Checkout SDK:

```html
<script src="https://checkout.razorpay.com/v1/checkout.js"></script>
```

The backend creates the Razorpay order first.

The frontend receives:

```text
providerOrderId
amount
currency
```

and creates the Checkout object.

Conceptually:

```text
Backend
   │
   └── Creates Razorpay Order
            │
            ▼
       providerOrderId
            │
            ▼
Frontend
   │
   └── Razorpay Checkout
            │
            ▼
       User pays
```

The Razorpay **Key ID** used by Checkout is public.

The **Key Secret** must never be exposed to the browser.

---

# 10. Why the Browser Callback Is Not the Source of Truth

Razorpay Checkout provides a client-side callback after the payment flow.

However, the backend does not trust the browser callback as the final payment confirmation.

The browser is an untrusted client.

The backend instead relies on:

```text
Razorpay
    ↓
Webhook
    ↓
Payment Service
    ↓
Database
```

This is important because a successful browser callback alone should not be treated as proof that the backend has received a reliable payment confirmation.

---

# 11. Webhooks

Razorpay sends asynchronous events to:

```http
POST /webhooks/razorpay
```

The project handles:

```text
payment.captured
payment.failed
```

Example lifecycle:

```text
User completes payment
        ↓
Razorpay processes payment
        ↓
Razorpay generates event
        ↓
Webhook request
        ↓
Payment Service
        ↓
Verify signature
        ↓
Parse event
        ↓
Update Payment
```

---

# 12. Webhook Signature Verification

Webhook requests contain:

```text
X-Razorpay-Signature
```

The service verifies the signature using:

```text
HMAC-SHA256
```

and the webhook secret.

Conceptually:

```text
Raw webhook payload
        +
Webhook secret
        ↓
HMAC-SHA256
        ↓
Expected signature
        ↓
Compare with
X-Razorpay-Signature
```

The raw request body is verified **before parsing the JSON**.

This is important because the signature is generated over the original payload.

Invalid signatures are rejected.

---

# 13. Webhook DTO Structure

The Razorpay event has nested data.

Conceptually:

```text
event
 └── payload
      └── payment
           └── entity
                ├── id
                ├── amount
                ├── currency
                ├── status
                └── order_id
```

The DTOs mirror this structure:

```text
RazorpayWebhookPayload
        ↓
RazorpayWebhookPaymentPayload
        ↓
RazorpayWebhookPayment
        ↓
RazorpayWebhookPaymentEntity
```

This allows the application to deserialize only the structure required by the business logic.

---

# 14. Updating Payment on `payment.captured`

When the service receives:

```text
payment.captured
```

it:

1. Extracts the Razorpay order ID.
2. Finds the local payment using `providerOrderId`.
3. Validates the amount.
4. Stores the provider payment ID.
5. Changes the local status to `SUCCESS`.
6. Updates `updatedAt`.

```text
Razorpay payment
       ↓
providerOrderId
       ↓
Find local Payment
       ↓
Validate amount
       ↓
SUCCESS
```

---

# 15. Updating Payment on `payment.failed`

For:

```text
payment.failed
```

the same basic validation is performed.

The local payment becomes:

```text
FAILED
```

The provider payment ID is also stored.

The service does not downgrade:

```text
SUCCESS → FAILED
```

because a successful payment should not be overwritten by a later failure event.

---

# 16. Duplicate Webhooks

Webhook systems can deliver the same event more than once.

Therefore this code must be idempotent.

For example:

```text
payment.captured
        ↓
SUCCESS
```

If the same webhook arrives again:

```text
payment.captured
        ↓
already SUCCESS
        ↓
do nothing
```

The same principle is applied to duplicate failure events.

This prevents repeated webhook delivery from corrupting payment state.

---

# 17. Payment Expiration

A user can create a payment and never complete it.

For example:

```text
CREATED
   ↓
User closes browser
   ↓
No webhook
   ↓
Payment remains CREATED
```

We therefore store:

```text
expiresAt
```

When the expiration time passes, the scheduled job finds expired payments.

```text
PaymentExpiryJob
```

runs periodically:

```text
CREATED + expiresAt < now
             ↓
          EXPIRED
```

The job currently runs once every minute.

---

# 18. Why Closing Checkout Does Not Mean FAILED

Closing the Razorpay Checkout window does not necessarily mean that Razorpay has reported a failed payment.

Therefore:

```text
User closes checkout
```

does not automatically mean:

```text
FAILED
```

Instead, the local payment can remain:

```text
CREATED
```

until one of the following happens:

```text
Webhook → SUCCESS
Webhook → FAILED
Reconciliation → SUCCESS/FAILED
Expiry Job → EXPIRED
```

This avoids incorrectly marking an unfinished payment as failed.

---

# 19. Reconciliation

Webhooks are asynchronous.

A production payment system should not assume:

```text
No webhook = No payment
```

There can be failures such as:

```text
Razorpay → Webhook
              X
          network failure
              X
      Payment Service
```

The payment may have succeeded even though our service never received the webhook.

Reconciliation provides another mechanism to discover the provider's actual state.

---

# 20. Reconciliation Flow

The scheduled job looks for payments in:

```text
CREATED
PENDING
```

It then asks Razorpay:

```http
GET /v1/orders/{order_id}/payments
```

Conceptually:

```text
Local DB
   │
   │ CREATED / PENDING
   ▼
Reconciliation Job
   │
   ▼
Razorpay
   │
   │ payments for order
   ▼
Payment Service
   │
   ├── captured → SUCCESS
   │
   └── failed   → FAILED
```

The reconciliation job runs periodically.

---

# 21. Why Reconciliation Uses the Provider Order ID

The local payment ID is meaningful only inside our database.

Razorpay does not know:

```text
payment.id = 15
```

Razorpay knows:

```text
order_xxxxxxxxx
```

Therefore reconciliation uses:

```java
payment.getProviderOrderId()
```

to query Razorpay.

---

# 22. Reconciliation Safety Checks

Before changing the local state, reconciliation validates the payment amount.

```text
Our DB amount
      =
Razorpay amount
```

If they don't match, reconciliation fails rather than silently updating the payment.

This protects against inconsistent data.

---

# 23. Scheduled Jobs

Scheduling is enabled with:

```java
@EnableScheduling
```

Two jobs currently exist:

```text
PaymentExpiryJob
PaymentReconciliationJob
```

### Expiry

```text
Every 1 minute
    ↓
Find expired CREATED payments
    ↓
EXPIRED
```

### Reconciliation

```text
Every 5 minutes
    ↓
Find CREATED/PENDING payments
    ↓
Ask Razorpay for payment status
    ↓
Update local state
```

---

# 24. Environment Variables and Secrets

Secrets are not hardcoded into the repository.

Configuration uses environment variables:

```properties
razorpay.base-url=https://api.razorpay.com
razorpay.key-id=${RAZORPAY_KEY_ID}
razorpay.key-secret=${RAZORPAY_KEY_SECRET}
razorpay.webhook-secret=${RAZORPAY_WEBHOOK_SECRET}
```

The actual values are stored in the environment:

```text
RAZORPAY_KEY_ID
RAZORPAY_KEY_SECRET
RAZORPAY_WEBHOOK_SECRET
```

The repository therefore contains references to the secrets, not the secrets themselves.

**Never commit actual API secrets to Git.**

If a secret is accidentally committed, rotate/revoke it rather than simply deleting it from the latest commit.

---

# 25. Local Webhook Development

Because Razorpay needs to reach the locally running application, an HTTPS tunnel is used during development.

```text
Razorpay
    │
    │ HTTPS
    ▼
Ngrok
    │
    │ localhost
    ▼
Payment Service :8082
```

Webhook endpoint:

```text
/webhooks/razorpay
```

This allows Razorpay Test Mode to send real webhook events to the local Spring Boot application.

---

# 26. Database Model

The main entity is:

```text
Payment
```

Important fields:

```text
id
orderId
providerOrderId
providerPaymentId
amount
currency
status
userId
createdAt
updatedAt
expiresAt
```

The distinction between:

```text
orderId
providerOrderId
providerPaymentId
```

is intentional.

They represent identifiers belonging to different domains/systems.

---

# 27. Important Failure Scenarios

The project handles several real-world scenarios.

### Successful payment

```text
CREATED
   ↓
Razorpay payment
   ↓
payment.captured
   ↓
SUCCESS
```

### Failed payment

```text
CREATED
   ↓
Razorpay payment fails
   ↓
payment.failed
   ↓
FAILED
```

### Webhook lost

```text
Razorpay
   ↓
payment succeeds
   ↓
webhook lost
   ↓
local DB still CREATED
   ↓
reconciliation
   ↓
SUCCESS
```

### User abandons payment

```text
CREATED
   ↓
No payment
   ↓
expiresAt reached
   ↓
EXPIRED
```

### Duplicate webhook

```text
payment.captured
   ↓
SUCCESS

same event again
   ↓
already SUCCESS
   ↓
no duplicate state change
```

---

# 28. Lessons Learned

This project focuses on several backend engineering concepts.

### External API integration

Don't guess the API contract.

Start from:

```text
Requirement
→ API documentation
→ endpoint
→ request
→ response
→ DTO
→ client
```

### Asynchronous systems

A payment result may arrive later through a webhook rather than in the original HTTP request.

### Eventual consistency

The local database and payment provider can temporarily disagree.

```text
Razorpay = SUCCESS
Our DB   = CREATED
```

Reconciliation helps resolve this.

### Idempotency

The same webhook may be delivered multiple times.

The service must safely handle duplicate events.

### State machines

Payment status transitions need to be deliberate.

For example:

```text
CREATED → SUCCESS
CREATED → FAILED
CREATED → EXPIRED
```

but:

```text
SUCCESS → FAILED
```

should not happen simply because a late event was received.

### Failure isolation

Scheduled reconciliation should eventually process payments independently so that one provider/API failure does not prevent other payments from being processed.

---

# 29. Current Limitations / Future Improvements

This project intentionally stops short of full production hardening.

Planned improvements include:

### Idempotency keys

Prevent duplicate payment creation if a client retries:

```text
POST /payments
```

after a network timeout.

---

### Multiple payment attempts

The current reconciliation implementation initially considers the first payment returned by Razorpay.

A production implementation should properly handle multiple payment attempts against the same order.

```text
Order
 ├── Payment Attempt 1 → failed
 ├── Payment Attempt 2 → failed
 └── Payment Attempt 3 → captured
```

---

### Stronger state transitions

State transitions should eventually be implemented atomically.

This helps avoid race conditions such as:

```text
Expiry Job
      │
      ├── marks CREATED → EXPIRED
      │
      │       simultaneously
      │
      └─────────────── Webhook → SUCCESS
```

---

### Webhook event deduplication

A production implementation can persist provider event IDs and explicitly reject/process duplicate events.

---

### Scheduler failure isolation

The reconciliation scheduler should isolate failures per payment:

```text
Payment A → SUCCESS
Payment B → API error
Payment C → FAILED
```

An error processing B should not prevent C from being processed.

---

### Retry strategy

Temporary provider/network failures should use controlled retries rather than immediately abandoning reconciliation.

---

### Refunds

Implement:

```text
SUCCESS
   ↓
Refund request
   ↓
Razorpay Refund API
   ↓
REFUNDED
```

---

### Order Service

The current API accepts an amount directly:

```json
{
  "amount": 49900,
  "currency": "INR"
}
```

The next architectural step is to introduce an actual Order domain.

```text
Product Service
      ↓
Create Order
      ↓
Order CREATED
      ↓
Payment Service
      ↓
Payment CREATED
      ↓
Razorpay
```

This prevents the client from simply deciding the amount it wants to pay.

---

# 30. Planned Overall Architecture

The larger project is moving toward:

```text
                 Authorization Server
                       :8080
                          │
                         JWT
                          │
                          ▼
                  ┌───────────────┐
                  │Product Service│
                  │     :8081     │
                  │               │
                  │ Product       │
                  │ Category      │
                  │ Order         │
                  └───────┬───────┘
                          │
                          │ Payment Request
                          ▼
                  ┌───────────────┐
                  │Payment Service│
                  │     :8082     │
                  │               │
                  │ Payment       │
                  │ Reconciliation│
                  │ Webhooks      │
                  └───────┬───────┘
                          │
                          ▼
                    ┌───────────┐
                    │ Razorpay  │
                    │ Test Mode │
                    └───────────┘
```

Future distributed-system improvements may include:

```text
Kafka / Event Streaming
Outbox Pattern
Service-to-Service OAuth2
Distributed Tracing
Metrics
Centralized Logging
Retries
Circuit Breakers
Idempotency
Reconciliation
Refunds
```

---

# 31. Useful Resources

### Spring Boot

* Spring Boot Documentation
  https://docs.spring.io/spring-boot/

* Spring Framework Documentation
  https://docs.spring.io/spring-framework/

* Spring Data JPA
  https://docs.spring.io/spring-data/jpa/

### Spring Security / OAuth2

* Spring Security Documentation
  https://docs.spring.io/spring-security/

### Razorpay

* Razorpay API Documentation
  https://razorpay.com/docs/api/

* Razorpay Orders API
  https://razorpay.com/docs/api/orders/

* Razorpay Payments API
  https://razorpay.com/docs/api/payments/

* Razorpay Webhooks
  https://razorpay.com/docs/webhooks/

* Razorpay Standard Checkout
  https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/

### Ngrok

* Ngrok Documentation
  https://ngrok.com/docs

---

# 32. Running the Project

## Prerequisites

Install:

* Java 24
* Maven
* MySQL
* Razorpay Test Mode account
* Ngrok

---

## Environment Variables

Set:

```text
RAZORPAY_KEY_ID
RAZORPAY_KEY_SECRET
RAZORPAY_WEBHOOK_SECRET
```

Do not commit their actual values.

---

## Database

Create the database:

```sql
CREATE DATABASE payment_service;
```

Configure the database credentials in `application.properties`.

---

## Start the Application

Run:

```bash
mvn spring-boot:run
```

The Payment Service runs on:

```text
http://localhost:8082
```

---

# 33. Testing the Complete Flow

### 1. Start MySQL

Make sure the `payment_service` database is available.

### 2. Start Payment Service

```text
localhost:8082
```

### 3. Start Ngrok

Expose port `8082`.

### 4. Configure Razorpay Webhook

Configure:

```text
/webhooks/razorpay
```

and use the same webhook secret configured in the application environment.

Enable relevant payment events such as:

```text
payment.captured
payment.failed
```

### 5. Create a Payment

```http
POST http://localhost:8082/payments
```

Example:

```json
{
  "amount": 100,
  "currency": "INR"
}
```

### 6. Open Checkout

Use the returned:

```text
providerOrderId
amount
currency
```

to initialize Razorpay Checkout.

### 7. Complete a Test Payment

Use Razorpay Test Mode.

### 8. Verify Webhook

The application should receive:

```text
payment.captured
```

and the database should become:

```text
SUCCESS
```

### 9. Test Failure

Perform a failed test payment and verify:

```text
FAILED
```

### 10. Test Expiration

Create a payment and do not complete it.

After `expiresAt` passes:

```text
CREATED → EXPIRED
```

### 11. Test Reconciliation

Create a scenario where the provider has a payment result but the webhook is not processed.

The reconciliation scheduler should query Razorpay and update the local database.

---

# 34. Key Backend Design Principles Demonstrated

This project demonstrates the following backend principles:

```text
1. Separate business logic from external API clients.

2. Never hardcode secrets.

3. Treat external systems as unreliable.

4. Do not trust the browser as the payment source of truth.

5. Use webhooks for asynchronous provider events.

6. Verify webhook signatures.

7. Make webhook processing idempotent.

8. Validate important financial fields such as amount.

9. Expect missed events and provide reconciliation.

10. Use expiration for abandoned operations.

11. Design explicit state transitions.

12. Keep provider IDs separate from local IDs.

13. Think about retries, duplicate requests, race conditions,
    and partial failures before calling a distributed workflow complete.
```

---

## Project Status

**Core Razorpay payment integration: Complete**

Implemented:

* Payment creation
* Razorpay order creation
* Razorpay Checkout
* Webhook handling
* Webhook signature verification
* Payment success/failure handling
* Duplicate webhook protection
* Payment expiration
* Scheduled reconciliation
* Provider/local payment state synchronization

Next major milestone:

**Introduce an Order domain and connect Product Service → Order → Payment Service → Razorpay.**
