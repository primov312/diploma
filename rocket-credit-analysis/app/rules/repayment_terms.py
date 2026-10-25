"""Decimal counterpart of Java RepaymentCalculator; shared golden cases guard the wire contract."""
from decimal import Decimal, ROUND_HALF_UP, ROUND_DOWN, localcontext

CENT = Decimal("0.01")


def maximum_payment(principal, terms):
    with localcontext() as ctx:
        ctx.prec = 34
        rate = terms.annualRate / (52 if terms.termUnit == "WEEK" else 12)
        count = terms.installmentCount
        payment = ((principal / count) if not rate else
                   principal * rate / (1 - (1 + rate) ** -count)).quantize(CENT, rounding=ROUND_HALF_UP)
        remaining = principal
        amounts = []
        for sequence in range(1, count + 1):
            interest = (remaining * rate).quantize(CENT, rounding=ROUND_HALF_UP)
            capital = remaining if sequence == count else payment - interest
            if capital <= 0 or capital > remaining or (sequence < count and capital == remaining):
                return None
            amounts.append(capital + interest)
            remaining -= capital
        return max(amounts)


def affordable_principal(monthly_capacity, terms):
    with localcontext() as ctx:
        ctx.prec = 34
        capacity = monthly_capacity * Decimal(12) / 52 if terms.termUnit == "WEEK" else monthly_capacity
        capacity = capacity.quantize(CENT, rounding=ROUND_DOWN)
        rate = terms.annualRate / (52 if terms.termUnit == "WEEK" else 12)
        count = terms.installmentCount
        amount = (capacity * count if not rate else capacity * (1 - (1 + rate) ** -count) / rate)
        amount = amount.quantize(CENT, rounding=ROUND_DOWN)
        while amount > 0:
            highest = maximum_payment(amount, terms)
            if highest is not None and highest <= capacity:
                return amount
            amount -= CENT
        return Decimal("0.00")
