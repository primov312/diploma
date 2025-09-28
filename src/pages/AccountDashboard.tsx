const AccountDashboard = () => (
  <div className="bg-gradient-hero py-20 lg:py-24">
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div className="grid gap-8 xl:grid-cols-3">
        <div className="xl:col-span-2 space-y-8">
          <section className="bg-white rounded-2xl p-6 shadow-card">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-semibold text-gray-800">
                Payment Timeline
              </h2>
              <button className="btn-secondary text-sm px-4 py-2">
                View All
              </button>
            </div>

            <div className="space-y-4">
              {/* Upcoming Payment */}
              <div className="flex items-center p-4 bg-gradient-secondary rounded-xl">
                <div className="w-12 h-12 bg-primary rounded-xl flex items-center justify-center mr-4">
                  <svg
                    className="w-6 h-6 text-white"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth="2"
                      d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
                    />
                  </svg>
                </div>
                <div className="flex-1">
                  <div className="flex items-center justify-between">
                    <h3 className="font-semibold text-gray-800">
                      MacBook Pro Payment
                    </h3>
                    <span className="text-lg font-bold text-gray-800">
                      $125.00
                    </span>
                  </div>
                  <p className="text-sm text-gray-600">
                    Due October 4, 2025 • 7 days remaining
                  </p>
                  <div className="flex items-center mt-2">
                    <button className="btn-gradient text-sm px-4 py-2 mr-3">
                      Pay Now
                    </button>
                    <button className="text-sm text-primary hover:text-primary-600 transition-smooth">
                      Pay Early &amp; Save
                    </button>
                  </div>
                </div>
              </div>

              {/* Recent Payment */}
              <div className="flex items-center p-4 border border-gray-100 rounded-xl">
                <div className="w-12 h-12 bg-success/20 rounded-xl flex items-center justify-center mr-4">
                  <svg
                    className="w-6 h-6 text-success"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth="2"
                      d="M5 13l4 4L19 7"
                    />
                  </svg>
                </div>
                <div className="flex-1">
                  <div className="flex items-center justify-between">
                    <h3 className="font-semibold text-gray-800">
                      iPhone 15 Payment
                    </h3>
                    <span className="text-lg font-bold text-success">
                      $200.00
                    </span>
                  </div>
                  <p className="text-sm text-gray-600">
                    Paid September 20, 2025 • On time
                  </p>
                </div>
              </div>

              {/* Scheduled Payment */}
              <div className="flex items-center p-4 border border-gray-100 rounded-xl">
                <div className="w-12 h-12 bg-gray-100 rounded-xl flex items-center justify-center mr-4">
                  <svg
                    className="w-6 h-6 text-gray-400"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth="2"
                      d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"
                    />
                  </svg>
                </div>
                <div className="flex-1">
                  <div className="flex items-center justify-between">
                    <h3 className="font-semibold text-gray-800">
                      Furniture Set Payment
                    </h3>
                    <span className="text-lg font-bold text-gray-800">
                      $150.00
                    </span>
                  </div>
                  <p className="text-sm text-gray-600">
                    Due November 1, 2025 • Scheduled
                  </p>
                </div>
              </div>
            </div>
          </section>

          {/* Spending Analytics */}
          <section className="bg-white rounded-2xl p-6 shadow-card">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-semibold text-gray-800">
                Spending Analytics
              </h2>
              <select className="form-input text-sm py-2 px-3">
                <option>This Month</option>
                <option>Last 3 Months</option>
                <option>This Year</option>
              </select>
            </div>

            {/* Chart Placeholder */}
            <div className="h-64 bg-gradient-secondary rounded-xl flex items-center justify-center mb-6">
              <div className="text-center">
                <svg
                  className="w-16 h-16 text-primary mx-auto mb-4"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2"
                    d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
                  />
                </svg>
                <p className="text-gray-600">Interactive spending chart</p>
              </div>
            </div>

            {/* Category Breakdown */}
            <div className="grid grid-cols-2 gap-4">
              <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                <div className="flex items-center">
                  <div className="w-3 h-3 bg-primary rounded-full mr-3" />
                  <span className="text-sm text-gray-600">Electronics</span>
                </div>
                <span className="text-sm font-semibold text-gray-800">
                  $1,200
                </span>
              </div>
              <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                <div className="flex items-center">
                  <div className="w-3 h-3 bg-accent rounded-full mr-3" />
                  <span className="text-sm text-gray-600">
                    Home &amp; Garden
                  </span>
                </div>
                <span className="text-sm font-semibold text-gray-800">
                  $450
                </span>
              </div>
              <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                <div className="flex items-center">
                  <div className="w-3 h-3 bg-secondary rounded-full mr-3" />
                  <span className="text-sm text-gray-600">Fashion</span>
                </div>
                <span className="text-sm font-semibold text-gray-800">
                  $320
                </span>
              </div>
              <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                <div className="flex items-center">
                  <div className="w-3 h-3 bg-warning rounded-full mr-3" />
                  <span className="text-sm text-gray-600">Other</span>
                </div>
                <span className="text-sm font-semibold text-gray-800">
                  $180
                </span>
              </div>
            </div>
          </section>
        </div>

        {/* Right Column */}
        <div className="space-y-8">
          {/* Quick Actions */}
          <section className="bg-white rounded-2xl p-6 shadow-card">
            <h2 className="text-xl font-semibold text-gray-800 mb-6">
              Quick Actions
            </h2>

            <div className="space-y-4">
              <button className="w-full flex items-center p-4 bg-gradient-primary text-white rounded-xl hover:opacity-90 transition-smooth">
                <svg
                  className="w-6 h-6 mr-3"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2"
                    d="M12 6v6m0 0v6m0-6h6m-6 0H6"
                  />
                </svg>
                Make a Payment
              </button>

              <button className="w-full flex items-center p-4 border border-gray-200 rounded-xl hover:bg-gray-50 transition-smooth">
                <svg
                  className="w-6 h-6 mr-3 text-gray-600"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2"
                    d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z"
                  />
                </svg>
                <span className="text-gray-800">New Purchase</span>
              </button>

              <button className="w-full flex items-center p-4 border border-gray-200 rounded-xl hover:bg-gray-50 transition-smooth">
                <svg
                  className="w-6 h-6 mr-3 text-gray-600"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2"
                    d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
                  />
                </svg>
                <span className="text-gray-800">View Reports</span>
              </button>
            </div>
          </section>

          {/* Financial Wellness Score */}
          <section className="bg-white rounded-2xl p-6 shadow-card">
            <h2 className="text-xl font-semibold text-gray-800 mb-6">
              Financial Wellness
            </h2>

            <div className="text-center mb-6">
              <div className="relative w-32 h-32 mx-auto mb-4">
                <svg
                  className="w-32 h-32 transform -rotate-90"
                  viewBox="0 0 120 120"
                >
                  <circle
                    cx="60"
                    cy="60"
                    r="50"
                    stroke="#E5E7EB"
                    strokeWidth="8"
                    fill="none"
                  />
                  <circle
                    cx="60"
                    cy="60"
                    r="50"
                    stroke="url(#gradient)"
                    strokeWidth="8"
                    fill="none"
                    strokeDasharray="314"
                    strokeDashoffset="47"
                    strokeLinecap="round"
                  />
                  <defs>
                    <linearGradient
                      id="gradient"
                      x1="0%"
                      y1="0%"
                      x2="100%"
                      y2="0%"
                    >
                      <stop offset="0%" stopColor="#FF69B4" />
                      <stop offset="100%" stopColor="#40E0D0" />
                    </linearGradient>
                  </defs>
                </svg>
                <div className="absolute inset-0 flex items-center justify-center">
                  <div className="text-center">
                    <div className="text-2xl font-bold text-gray-800">85</div>
                    <div className="text-sm text-gray-600">Excellent</div>
                  </div>
                </div>
              </div>
              <p className="text-sm text-gray-600">
                Your financial wellness has improved by 12 points this month!
              </p>
            </div>

            <div className="space-y-3">
              <div className="flex items-start p-3 bg-success/10 rounded-lg">
                <svg
                  className="w-5 h-5 text-success mr-3 mt-0.5"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2"
                    d="M5 13l4 4L19 7"
                  />
                </svg>
                <div>
                  <p className="text-sm font-medium text-gray-800">
                    On-time payments
                  </p>
                  <p className="text-xs text-gray-600">
                    Keep up the great work!
                  </p>
                </div>
              </div>

              <div className="flex items-start p-3 bg-warning/10 rounded-lg">
                <svg
                  className="w-5 h-5 text-warning mr-3 mt-0.5"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2"
                    d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L3.732 16.5c-.77.833.192 2.5 1.732 2.5z"
                  />
                </svg>
                <div>
                  <p className="text-sm font-medium text-gray-800">
                    Budget tracking
                  </p>
                  <p className="text-xs text-gray-600">
                    Set monthly spending limits
                  </p>
                </div>
              </div>
            </div>
          </section>

          {/* Notifications Center */}
          <section className="bg-white rounded-2xl p-6 shadow-card">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-semibold text-gray-800">
                Notifications
              </h2>
              <button className="text-sm text-primary hover:text-primary-600 transition-smooth">
                Mark all read
              </button>
            </div>

            <div className="space-y-4">
              <div className="flex items-start p-3 bg-primary/5 rounded-lg border-l-4 border-primary">
                <svg
                  className="w-5 h-5 text-primary mr-3 mt-0.5"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2"
                    d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
                <div>
                  <p className="text-sm font-medium text-gray-800">
                    Payment reminder
                  </p>
                  <p className="text-xs text-gray-600">
                    MacBook Pro payment due in 7 days
                  </p>
                  <p className="text-xs text-gray-500 mt-1">2 hours ago</p>
                </div>
              </div>

              <div className="flex items-start p-3 bg-gray-50 rounded-lg">
                <svg
                  className="w-5 h-5 text-success mr-3 mt-0.5"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2"
                    d="M5 13l4 4L19 7"
                  />
                </svg>
                <div>
                  <p className="text-sm font-medium text-gray-800">
                    Payment successful
                  </p>
                  <p className="text-xs text-gray-600">
                    iPhone 15 payment processed
                  </p>
                  <p className="text-xs text-gray-500 mt-1">1 week ago</p>
                </div>
              </div>

              <div className="flex items-start p-3 bg-gray-50 rounded-lg">
                <svg
                  className="w-5 h-5 text-accent mr-3 mt-0.5"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2"
                    d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1"
                  />
                </svg>
                <div>
                  <p className="text-sm font-medium text-gray-800">
                    Referral bonus earned
                  </p>
                  <p className="text-xs text-gray-600">
                    $25 added to your account
                  </p>
                  <p className="text-xs text-gray-500 mt-1">2 weeks ago</p>
                </div>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  </div>
);

export default AccountDashboard;
