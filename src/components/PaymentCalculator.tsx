import { useMemo, useState } from "react";

const PAYMENT_PLANS = [
  {
    id: "4x",
    label: "4 payments (6 weeks)",
    installments: 4,
    interestRate: 0,
  },
  {
    id: "6x",
    label: "6 payments (3 months)",
    installments: 6,
    interestRate: 0.1,
  },
  {
    id: "12x",
    label: "12 payments (6 months)",
    installments: 12,
    interestRate: 0.15,
  },
] as const;

const formatCurrency = (value: number) =>
  new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(
    value,
  );

type PaymentCalculatorProps = {
  className?: string;
};

const PaymentCalculator = ({ className = "" }: PaymentCalculatorProps) => {
  const [amountInput, setAmountInput] = useState("500");
  const [selectedPlanId, setSelectedPlanId] = useState<
    (typeof PAYMENT_PLANS)[number]["id"]
  >(PAYMENT_PLANS[0].id);

  const plan = useMemo(
    () =>
      PAYMENT_PLANS.find((candidate) => candidate.id === selectedPlanId) ??
      PAYMENT_PLANS[0],
    [selectedPlanId],
  );

  const purchaseAmount = useMemo(() => {
    const parsed = Number.parseFloat(amountInput);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      return 0;
    }

    return parsed;
  }, [amountInput]);

  const {
    todayDue,
    recurringPayment,
    recurringCount,
    totalWithInterest,
    interestDescription,
  } = useMemo(() => {
    if (plan.installments <= 0) {
      return {
        todayDue: 0,
        recurringPayment: 0,
        recurringCount: 0,
        totalWithInterest: 0,
        interestDescription: "—",
      };
    }

    const total = purchaseAmount * (1 + plan.interestRate);
    const installment = plan.installments ? total / plan.installments : total;
    const remainingPayments = Math.max(plan.installments - 1, 0);
    const interest = total - purchaseAmount;

    return {
      todayDue: installment,
      recurringPayment: installment,
      recurringCount: remainingPayments,
      totalWithInterest: total,
      interestDescription:
        plan.interestRate > 0
          ? `${Math.round(plan.interestRate * 100)}% interest applied (${formatCurrency(interest)})`
          : "No interest, no fees",
    };
  }, [plan.installments, plan.interestRate, purchaseAmount]);

  return (
    <div
      className={["rounded-2xl bg-white p-6 shadow-hover lg:p-8", className]
        .filter(Boolean)
        .join(" ")}
    >
      <h3 className="mb-6 text-center text-xl font-semibold text-gray-800">
        Payment Calculator
      </h3>
      <div className="space-y-4">
        <div>
          <label className="mb-2 block text-sm font-medium text-gray-700">
            Purchase Amount
          </label>
          <div className="relative">
            <span className="absolute left-3 top-3 text-gray-500">$</span>
            <input
              type="number"
              className="form-input w-full pl-8"
              placeholder="500"
              value={amountInput}
              onChange={(event) => setAmountInput(event.target.value)}
              min="0"
            />
          </div>
        </div>
        <div>
          <label className="mb-2 block text-sm font-medium text-gray-700">
            Payment Plan
          </label>
          <select
            className="form-input w-full"
            value={selectedPlanId}
            onChange={(event) =>
              setSelectedPlanId(
                event.target.value as (typeof PAYMENT_PLANS)[number]["id"],
              )
            }
          >
            {PAYMENT_PLANS.map(({ id, label }) => (
              <option key={id} value={id}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <div className="mt-6 rounded-lg bg-gradient-secondary p-4">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-sm text-gray-600">Today</span>
            <span className="font-semibold text-gray-800">
              {formatCurrency(todayDue || 0)}
            </span>
          </div>
          {recurringCount > 0 ? (
            <div className="mb-2 flex items-center justify-between">
              <span className="text-sm text-gray-600">
                {`${recurringCount} payments of`}
              </span>
              <span className="font-semibold text-gray-800">
                {formatCurrency(recurringPayment || 0)}
              </span>
            </div>
          ) : (
            <div className="mb-2 flex items-center justify-between">
              <span className="text-sm text-gray-600">No future payments</span>
              <span className="font-semibold text-gray-800">—</span>
            </div>
          )}
          <div className="mt-2 border-t border-gray-200 pt-2">
            <div className="flex items-center justify-between">
              <span className="font-medium text-gray-800">Total</span>
              <span className="text-lg font-bold text-primary">
                {formatCurrency(totalWithInterest || 0)}
              </span>
            </div>
            <p className="mt-1 text-xs text-gray-500">{interestDescription}</p>
          </div>
        </div>
        <button type="button" className="btn-gradient mt-4 w-full">
          Get Pre-Approved
        </button>
      </div>
    </div>
  );
};

export default PaymentCalculator;
