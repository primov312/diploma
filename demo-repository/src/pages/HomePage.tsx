import { Link } from "react-router-dom";

import PaymentCalculator from "../components/PaymentCalculator";
import SectionHeader from "../components/common/SectionHeader";

const HERO_ASSURANCES = [
  {
    label: "No Hidden Fees",
    icon: (
      <svg
        className="mr-2 h-5 w-5 text-green-500"
        fill="currentColor"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
    ),
  },
  {
    label: "Bank-Level Security",
    icon: (
      <svg
        className="mr-2 h-5 w-5 text-green-500"
        fill="currentColor"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <path d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
      </svg>
    ),
  },
  {
    label: "Instant Approval",
    icon: (
      <svg
        className="mr-2 h-5 w-5 text-green-500"
        fill="currentColor"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <path d="M13 10V3L4 14h7v7l9-11h-7z" />
      </svg>
    ),
  },
];

const VALUE_PROPS = [
  {
    title: "Transparent & Trustworthy",
    description:
      "No hidden fees, no surprises. What you see is what you pay. Our transparent terms put you in complete control of your finances.",
    icon: (
      <svg
        className="h-8 w-8 text-white"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="2"
          d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"
        />
      </svg>
    ),
  },
  {
    title: "Instant Decisions",
    description:
      "Get approved in seconds, not days. Our smart approval system gives you instant access to flexible payment options.",
    icon: (
      <svg
        className="h-8 w-8 text-white"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="2"
          d="M13 10V3L4 14h7v7l9-11h-7z"
        />
      </svg>
    ),
  },
  {
    title: "Built for Your Wellbeing",
    description:
      "We care about your financial health. Our platform includes tools and resources to help you make informed spending decisions.",
    icon: (
      <svg
        className="h-8 w-8 text-white"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="2"
          d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"
        />
      </svg>
    ),
  },
];

const BRAND_NAMES = [
  "TechStore",
  "FashionHub",
  "HomeDecor",
  "SportGear",
  "BeautyBox",
  "BookWorld",
];

const HomePage = () => (
  <div className="bg-gradient-hero">
    <section className="relative overflow-hidden py-20 lg:py-32">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="grid items-center gap-12 lg:grid-cols-2">
          <div className="text-center lg:text-left">
            <h1 className="mb-6 text-4xl font-bold leading-tight text-gray-800 sm:text-5xl lg:text-6xl">
              Shop with
              <span className="bg-gradient-primary bg-clip-text text-transparent">
                {" "}
                Confidence
              </span>
              , Pay with
              <span className="bg-gradient-primary bg-clip-text text-transparent">
                {" "}
                Flexibility
              </span>
            </h1>
            <p className="mb-8 text-xl leading-relaxed text-gray-600">
              The trustworthy Buy Now Pay Later platform that empowers your
              financial wellness. No hidden fees, no surprises – just
              transparent, flexible payments that work for you.
            </p>

            <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:justify-center lg:justify-start">
              <Link
                to="/account-dashboard"
                className="btn-gradient inline-flex items-center justify-center px-8 py-4 text-lg"
              >
                <svg
                  className="mr-2 h-5 w-5"
                  fill="currentColor"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.095 3.2 5.076 4.487 3.74 1.551 3.74 1.034 4.417.97.678-.064 2.182-.887 2.49-1.743.308-.856.308-1.59.215-1.743-.09-.149-.33-.248-.627-.397z" />
                </svg>
                Start Shopping
              </Link>
              <Link
                to="/how-it-works"
                className="inline-flex items-center justify-center rounded-lg border border-gray-200 px-8 py-4 font-semibold text-gray-700 transition-smooth hover:bg-white"
              >
                <span>See How It Works</span>
                <svg
                  className="ml-2 h-5 w-5"
                  fill="currentColor"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path
                    stroke="currentColor"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2"
                    d="M9 5l7 7-7 7"
                  />
                </svg>
              </Link>
            </div>

            <div className="flex flex-wrap items-center justify-center gap-6 text-sm text-gray-500 lg:justify-start">
              {HERO_ASSURANCES.map(({ label, icon }) => (
                <div key={label} className="flex items-center">
                  {icon}
                  {label}
                </div>
              ))}
            </div>
          </div>

          <div className="relative">
            <PaymentCalculator />
          </div>
        </div>
      </div>
    </section>

    <section className="bg-white py-20">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <SectionHeader
          title="Your Financial Wellness Partner"
          subtitle="We're not just another payment method. We're here to help you make smart financial decisions and shop with complete confidence."
        />
        <div className="grid gap-8 md:grid-cols-3">
          {VALUE_PROPS.map(({ title, description, icon }) => (
            <div key={title} className="group text-center">
              <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-primary transition-smooth group-hover:scale-110">
                {icon}
              </div>
              <h3 className="mb-4 text-xl font-semibold text-gray-800">
                {title}
              </h3>
              <p className="leading-relaxed text-gray-600">{description}</p>
            </div>
          ))}
        </div>
      </div>
    </section>

    <section className="bg-white py-20">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <SectionHeader
          className="mb-12"
          titleClassName="mb-4 text-2xl font-bold text-gray-800 lg:text-3xl"
          subtitleClassName="text-lg text-gray-600"
          title="Trusted by Leading Brands"
          subtitle="Join thousands of merchants who trust Rocket Credit to grow their business"
        />

        <div className="grid grid-cols-2 items-center gap-8 opacity-60 md:grid-cols-4 lg:grid-cols-6">
          {BRAND_NAMES.map((brand) => (
            <div key={brand} className="flex h-12 items-center justify-center">
              <div className="text-2xl font-bold text-gray-400">{brand}</div>
            </div>
          ))}
        </div>

        <div className="mt-12 text-center">
          <Link
            to="/for-businesses"
            className="btn-secondary inline-flex items-center"
          >
            Become a Partner
            <svg
              className="ml-2 h-5 w-5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="2"
                d="M9 5l7 7-7 7"
              />
            </svg>
          </Link>
        </div>
      </div>
    </section>

    <section className="bg-gradient-primary py-20">
      <div className="mx-auto max-w-4xl px-4 text-center sm:px-6 lg:px-8">
        <h2 className="mb-6 text-3xl font-bold text-white lg:text-4xl">
          Ready to Shop with Confidence?
        </h2>
        <p className="mb-8 text-xl leading-relaxed text-white/90">
          Join thousands of happy shoppers who trust Rocket Credit for their flexible
          payment needs. Get started in minutes with instant approval.
        </p>
        <div className="flex flex-col justify-center gap-4 sm:flex-row">
          <button
            type="button"
            className="inline-flex items-center justify-center rounded-lg bg-white px-8 py-4 font-semibold text-primary shadow-card transition-smooth hover:bg-gray-50 hover:shadow-hover"
          >
            <svg
              className="mr-2 h-5 w-5"
              fill="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893A11.821 11.821 0 0020.885 3.488" />
            </svg>
            Download App
          </button>
          <Link
            to="/account-dashboard"
            className="inline-flex items-center justify-center rounded-lg border border-white/30 px-8 py-4 font-semibold text-white transition-smooth hover:bg-white/30"
          >
            Sign Up Now
            <svg
              className="ml-2 h-5 w-5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="2"
                d="M9 5l7 7-7 7"
              />
            </svg>
          </Link>
        </div>
      </div>
    </section>
  </div>
);

export default HomePage;
