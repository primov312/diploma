import SectionHeader from "../components/common/SectionHeader";
import StatGrid from "../components/common/StatGrid";

const ForBusinesses = () => (
  <div className="bg-gradient-hero">
    <section className="relative overflow-hidden py-20 lg:py-32">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid lg:grid-cols-2 gap-12 items-center">
          {/* Hero Content */}
          <div className="text-center lg:text-left">
            <div className="inline-flex items-center bg-gradient-secondary rounded-full px-4 py-2 mb-6">
              <svg
                className="w-4 h-4 text-primary mr-2"
                fill="currentColor"
                viewBox="0 0 24 24"
              >
                <path d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              <span className="text-sm font-medium text-gray-700">
                Trusted by 10,000+ merchants
              </span>
            </div>

            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold text-gray-800 leading-tight mb-6">
              Grow Your Business with
              <span className="bg-gradient-primary bg-clip-text text-transparent">
                Rocket Credit
              </span>
            </h1>
            <p className="text-xl text-gray-600 mb-8 leading-relaxed">
              Increase conversions by up to 35%, boost average order value by
              68%, and reduce cart abandonment. Join thousands of merchants who
              trust Rocket Credit to grow their revenue.
            </p>

            {/* Key Metrics */}
            <StatGrid
              className="mb-8 grid grid-cols-3 gap-6"
              cardClassName="text-center"
              valueClassName="mb-1 text-3xl font-bold text-primary"
              labelClassName="text-sm text-gray-600"
              items={[
                { value: "35%", label: "Conversion Increase" },
                { value: "68%", label: "Higher AOV" },
                { value: "24h", label: "Integration Time" },
              ]}
            />

            {/* Hero CTAs */}
            <div className="flex flex-col sm:flex-row gap-4 justify-center lg:justify-start">
              <a
                href="javascript:void(0)"
                className="btn-gradient text-lg px-8 py-4 inline-flex items-center justify-center"
              >
                <svg
                  className="w-5 h-5 mr-2"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2"
                    d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"
                  />
                </svg>
                Book a Demo
              </a>
              <a
                href="javascript:void(0)"
                className="btn-secondary text-lg px-8 py-4 inline-flex items-center justify-center"
              >
                Calculate ROI
                <svg
                  className="w-5 h-5 ml-2"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2"
                    d="M9 7h6m0 10v-3m-3 3h.01M9 17h.01M9 14h.01M12 14h.01M15 11h.01M12 11h.01M9 11h.01M7 21h10a2 2 0 002-2V5a2 2 0 00-2-2H7a2 2 0 00-2 2v14a2 2 0 002 2z"
                  />
                </svg>
              </a>
            </div>
          </div>

          {/* ROI Calculator Widget */}
          <div className="relative">
            <div className="bg-white rounded-2xl shadow-hover p-6 lg:p-8">
              <h3 className="text-xl font-semibold text-gray-800 mb-6 text-center">
                ROI Calculator
              </h3>

              {/* Calculator Form */}
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Monthly Revenue
                  </label>
                  <div className="relative">
                    <span className="absolute left-3 top-3 text-gray-500">
                      $
                    </span>
                    <input
                      type="number"
                      className="form-input pl-8 w-full"
                      placeholder="50,000"
                      value="50000"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Average Order Value
                  </label>
                  <div className="relative">
                    <span className="absolute left-3 top-3 text-gray-500">
                      $
                    </span>
                    <input
                      type="number"
                      className="form-input pl-8 w-full"
                      placeholder="150"
                      value="150"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Industry Type
                  </label>
                  <select className="form-input w-full">
                    <option>Fashion & Apparel</option>
                    <option>Electronics</option>
                    <option>Home & Garden</option>
                    <option>Beauty & Wellness</option>
                    <option>Sports & Fitness</option>
                  </select>
                </div>

                {/* ROI Results */}
                <div className="bg-gradient-secondary rounded-lg p-4 mt-6">
                  <h4 className="font-semibold text-gray-800 mb-3">
                    Projected Monthly Impact
                  </h4>
                  <div className="space-y-2">
                    <div className="flex justify-between items-center">
                      <span className="text-sm text-gray-600">
                        Additional Revenue
                      </span>
                      <span className="font-semibold text-primary">
                        +$17,500
                      </span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-sm text-gray-600">
                        New Customers
                      </span>
                      <span className="font-semibold text-primary">+117</span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-sm text-gray-600">
                        Conversion Boost
                      </span>
                      <span className="font-semibold text-primary">+35%</span>
                    </div>
                    <div className="border-t border-gray-200 pt-2 mt-2">
                      <div className="flex justify-between items-center">
                        <span className="font-medium text-gray-800">
                          Annual ROI
                        </span>
                        <span className="font-bold text-primary text-lg">
                          +$210,000
                        </span>
                      </div>
                    </div>
                  </div>
                </div>

                <button className="btn-gradient w-full mt-4">
                  Get Detailed Report
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    {/* Integration Timeline */}

    {/* Demo Booking Section */}
    <section className="py-20 bg-gradient-primary">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
        <h2 className="text-3xl lg:text-4xl font-bold text-white mb-6">
          Ready to Grow Your Business?
        </h2>
        <p className="text-xl text-white/90 mb-8 leading-relaxed">
          Join thousands of merchants who trust Rocket Credit to increase conversions
          and boost revenue. Book a personalized demo and see how we can help
          your business grow.
        </p>

        <div className="bg-white rounded-2xl p-8 max-w-2xl mx-auto">
          <h3 className="text-xl font-semibold text-gray-800 mb-6">
            Schedule Your Demo
          </h3>

          <form className="space-y-4">
            <div className="grid md:grid-cols-2 gap-4">
              <div>
                <input
                  type="text"
                  className="form-input w-full"
                  placeholder="First Name"
                  required
                />
              </div>
              <div>
                <input
                  type="text"
                  className="form-input w-full"
                  placeholder="Last Name"
                  required
                />
              </div>
            </div>

            <div>
              <input
                type="email"
                className="form-input w-full"
                placeholder="Business Email"
                required
              />
            </div>

            <div>
              <input
                type="text"
                className="form-input w-full"
                placeholder="Company Name"
                required
              />
            </div>

            <div className="grid md:grid-cols-2 gap-4">
              <div>
                <select className="form-input w-full" required>
                  <option value="">Industry</option>
                  <option>Fashion & Apparel</option>
                  <option>Electronics</option>
                  <option>Home & Garden</option>
                  <option>Beauty & Wellness</option>
                  <option>Sports & Fitness</option>
                  <option>Other</option>
                </select>
              </div>
              <div>
                <select className="form-input w-full" required>
                  <option value="">Monthly Revenue</option>
                  <option>Under $10K</option>
                  <option>$10K - $50K</option>
                  <option>$50K - $100K</option>
                  <option>$100K - $500K</option>
                  <option>$500K+</option>
                </select>
              </div>
            </div>

            <div>
              <textarea
                className="form-input w-full h-24 resize-none"
                placeholder="Tell us about your business goals and challenges..."
              ></textarea>
            </div>

            <button type="submit" className="btn-gradient w-full text-lg py-4">
              Book My Demo
            </button>
          </form>

          <p className="text-sm text-gray-500 mt-4">
            We'll contact you within 24 hours to schedule your personalized demo
          </p>
        </div>
      </div>
    </section>
  </div>
);

export default ForBusinesses;
