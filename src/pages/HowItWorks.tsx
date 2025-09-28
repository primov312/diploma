import { Link } from "react-router-dom";

import SectionHeader from "../components/common/SectionHeader";
import StatGrid from "../components/common/StatGrid";

const HERO_STATS = [
  { value: "3 Steps", label: "Simple process" },
  { value: "0%", label: "Interest on time" },
  { value: "Instant", label: "Approval decision" },
];

const WELLNESS_FEATURES = [
  {
    title: "Transparent Terms",
    description:
      "No hidden fees. You see your payment schedule upfront, including due dates and amounts.",
  },
  {
    title: "Smart Reminders",
    description:
      "Automated notifications keep you informed so you never miss a payment.",
  },
  {
    title: "Spending Insights",
    description:
      "Track category spending and understand your habits with visual dashboards.",
  },
  {
    title: "Credit Friendly",
    description:
      "Responsible usage can help you build positive credit history over time.",
  },
  {
    title: "Flexible Payments",
    description:
      "Pay early, change your payment method, or adjust your schedule when life happens.",
  },
  {
    title: "Secure Platform",
    description:
      "Bank-grade encryption and fraud detection keep your information safe.",
  },
];

const SHOPPER_STATS = [
  { value: "92%", label: "On-time payments" },
  { value: "4.9/5", label: "App Store rating" },
  { value: "87%", label: "Approval rate" },
  { value: "500K+", label: "Active users" },
];

const FAQ_ITEMS = [
  {
    question: "Does Rocket Credit charge interest or fees?",
    answer:
      "Rocket Credit never charges interest when you pay on time. If a payment is missed, a small fee may apply, but we always send reminders before your installment is due.",
  },
  {
    question: "Will using Rocket Credit affect my credit score?",
    answer:
      "We run a soft credit check when you apply, which doesn’t impact your score. Making payments on time can positively influence your payment history with credit bureaus.",
  },
  {
    question: "How do automatic payments work?",
    answer:
      "When you sign up, you can link a debit card or bank account for auto-pay. We send reminders before each payment, and you can reschedule or make payments early anytime.",
  },
  {
    question: "Can I pay off my plan early?",
    answer:
      "Absolutely. You can pay off your remaining balance at any time without any penalties or additional fees.",
  },
];

const CONFIDENCE_POINTS = [
  {
    title: "Bank-level security",
    description:
      "256-bit encryption and continuous fraud monitoring keep your data safe. ",
  },
  {
    title: "Protected purchases",
    description:
      "Every transaction is covered by our customer protection promise.",
  },
  {
    title: "Support when you need it",
    description: "Our support team is available 24/7 by chat, email, or phone.",
  },
];

const HowItWorks = () => (
  <div className="bg-gradient-hero">
    <section className="py-16 lg:py-24">
      <div className="mx-auto max-w-7xl px-4 text-center sm:px-6 lg:px-8">
        <h1 className="mb-6 text-4xl font-bold leading-tight text-gray-800 sm:text-5xl lg:text-6xl">
          How{" "}
          <span className="bg-gradient-primary bg-clip-text text-transparent">
            Rocket Credit
          </span>{" "}
          Works
        </h1>
        <p className="mx-auto mb-8 max-w-3xl text-xl leading-relaxed text-gray-600">
          Shopping with Rocket Credit is simple, transparent, and designed to fit your
          lifestyle. No hidden fees, no surprises – just flexible payments that
          work for you.
        </p>
        <StatGrid
          items={HERO_STATS}
          className="mx-auto mb-12 grid max-w-4xl grid-cols-1 gap-6 md:grid-cols-3"
          cardClassName="rounded-2xl bg-white p-6 shadow-card"
          valueClassName="mb-2 text-3xl font-bold text-primary"
          labelClassName="text-gray-600"
        />
      </div>
    </section>

    <section className="bg-gradient-secondary py-20">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <SectionHeader
          title="Built for Financial Wellness"
          subtitle="Rocket Credit is more than a payment plan. It's a partner in helping you shop smarter, spend responsibly, and stay in control of your finances."
        />

        <div className="grid gap-8 md:grid-cols-2 lg:grid-cols-3">
          {WELLNESS_FEATURES.map(({ title, description }) => (
            <div
              key={title}
              className="rounded-2xl bg-white p-6 shadow-card transition-smooth hover:-translate-y-1 hover:shadow-hover"
            >
              <div className="mb-4 h-12 w-12 rounded-xl bg-gradient-primary" />
              <h3 className="mb-3 text-xl font-semibold text-gray-800">
                {title}
              </h3>
              <p className="text-gray-600">{description}</p>
            </div>
          ))}
        </div>
      </div>
    </section>

    <section className="bg-white py-20">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <SectionHeader
          title="Why Shoppers Choose Rocket Credit"
          subtitle="Thousands of happy customers use Rocket Credit every day for smarter spending"
        />

        <StatGrid
          items={SHOPPER_STATS}
          className="grid gap-8 md:grid-cols-2 lg:grid-cols-4"
          cardClassName="rounded-2xl bg-gradient-secondary p-6 text-center shadow-card"
          valueClassName="text-3xl font-bold text-primary"
          labelClassName="mt-2 text-sm text-gray-600"
        />
      </div>
    </section>

    <section className="bg-gradient-secondary py-20">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <SectionHeader
          title="Questions? We've Got Answers"
          subtitle="Everything you need to know about using Rocket Credit"
        />

        <div className="mx-auto max-w-4xl space-y-6">
          {FAQ_ITEMS.map(({ question, answer }) => (
            <div
              key={question}
              className="rounded-2xl bg-white p-6 shadow-card"
            >
              <div className="flex items-start justify-between">
                <div>
                  <h3 className="text-lg font-semibold text-gray-800">
                    {question}
                  </h3>
                  <p className="mt-2 text-gray-600">{answer}</p>
                </div>
                <span className="ml-4 flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-primary">
                  ?
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>

    <section className="bg-white py-20">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="grid gap-12 lg:grid-cols-2">
          <div>
            <SectionHeader
              align="left"
              className="mb-8"
              title="Confidence Built into Every Step"
              subtitle="From secure verification to payment protection, Rocket Credit keeps your finances safe and your experience smooth."
            />
            <div className="space-y-6">
              {CONFIDENCE_POINTS.map(({ title, description }) => (
                <div
                  key={title}
                  className="rounded-2xl bg-gradient-secondary p-6 shadow-soft"
                >
                  <h3 className="text-xl font-semibold text-gray-800">
                    {title}
                  </h3>
                  <p className="mt-2 text-gray-600">{description}</p>
                </div>
              ))}
            </div>
          </div>
          <div>
            <div className="rounded-3xl bg-gradient-secondary p-8 shadow-hover">
              <div className="rounded-2xl bg-white p-6 shadow-card">
                <h3 className="mb-4 text-xl font-semibold text-gray-800">
                  Safe &amp; Simple Payments
                </h3>
                <div className="space-y-4 text-sm text-gray-600">
                  <div className="flex items-center justify-between">
                    <span>Payment Method</span>
                    <span className="rounded-full bg-primary/10 px-3 py-1 text-xs font-semibold text-primary">
                      Verified
                    </span>
                  </div>
                  <div className="rounded-lg border border-dashed border-primary/30 p-4">
                    <p className="font-semibold text-gray-800">
                      Ending in ••42
                    </p>
                    <p className="text-xs text-gray-500">Bank account (ACH)</p>
                  </div>
                  <div className="flex items-center justify-between">
                    <span>Auto-pay</span>
                    <span className="font-semibold text-primary">Enabled</span>
                  </div>
                  <button type="button" className="btn-gradient w-full">
                    Manage Payment Methods
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section className="bg-gradient-primary py-20 text-white">
      <div className="mx-auto max-w-5xl px-4 text-center sm:px-6 lg:px-8">
        <h2 className="mb-6 text-3xl font-bold lg:text-4xl">
          Ready to Experience Rocket Credit?
        </h2>
        <p className="mb-10 text-lg text-white/90">
          Start shopping with flexible payments in minutes. Create your account,
          get approved instantly, and check out with confidence.
        </p>
        <div className="flex flex-col items-center justify-center gap-4 sm:flex-row">
          <Link to="/account-dashboard" className="btn-gradient px-8 py-4">
            Get Started Today
          </Link>
          <Link
            to="/for-businesses"
            className="inline-flex items-center justify-center rounded-lg border border-white/40 bg-white/20 px-8 py-4 font-semibold text-white transition-smooth hover:bg-white/30"
          >
            Offer Rocket Credit at Checkout
          </Link>
        </div>
      </div>
    </section>
  </div>
);

export default HowItWorks;
