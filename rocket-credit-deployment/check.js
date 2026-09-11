import http from 'k6/http';
import { check } from 'k6';

export const options = { vus: 10, iterations: 10 };

export default function () {
  const url = 'http://localhost:8080/checkout';
  const payload = JSON.stringify({
    partnerId: "Shidi",
    partnerPaymentId: "PAY-12345",
    amount: 50.0,
    currency: "USD",
    installmentDurationMonths: 6,
    buyer: {
        partnerUserId: "partner-uid-789",
        email: "alice@company.com",
        name: "Alice Doe",
        cardToken: "pm_tok_abc",
        transactions: [
        { date: "2025-09-01", amount: 120.5, method: "CARD" },
        { date: "2025-08-15", amount: 80.0, method: "BANK_TRANSFER" },
        { date: "2025-08-15", amount: 80.0, method: "BANK_TRANSFER" },
        { date: "2025-08-15", amount: 80.0, method: "BANK_TRANSFER" },
        { date: "2025-08-15", amount: 80.0, method: "BANK_TRANSFER" },
        { date: "2025-08-15", amount: 80.0, method: "BANK_TRANSFER" },
        { date: "2025-08-15", amount: 80.0, method: "BANK_TRANSFER" }
        ],
        items: [
        { sku: "ABC", name: "gun", price: 40.0, quantity: 3 }
        ]
    }
  });
  const params = { headers: { 'Content-Type': 'application/json' } };
  const res = http.post(url, payload, params);
  check(res, { 'status is 200 or 409': r => r.status === 200 || r.status === 409 });
}