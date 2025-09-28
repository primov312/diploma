import SectionHeader from "../components/common/SectionHeader";
import StatGrid from "../components/common/StatGrid";
import { cn } from "../utils/cn";
import { applyImageFallback } from "../utils/images";

const CheckIcon = () => (
  <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
    <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
  </svg>
);

const ShieldCheckIcon = () => (
  <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="2"
      d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"
    />
  </svg>
);

const LockIcon = () => (
  <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="2"
      d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
    />
  </svg>
);

const CheckCircleIcon = () => (
  <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="2"
      d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
    />
  </svg>
);

const LightningIcon = () => (
  <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
  </svg>
);

const BookIcon = () => (
  <svg className="w-10 h-10 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="2"
      d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.746 0 3.332.477 4.5 1.253v13C19.832 18.477 18.246 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"
    />
  </svg>
);

const HeartIcon = () => (
  <svg className="w-10 h-10 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="2"
      d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"
    />
  </svg>
);

const RocketIcon = () => (
  <svg className="w-10 h-10 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
  </svg>
);

type SocialPlatform = "linkedin" | "twitter" | "github";

const LinkedInIcon = () => (
  <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
    <path d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433c-1.144 0-2.063-.926-2.063-2.065 0-1.138.92-2.063 2.063-2.063 1.14 0 2.064.925 2.064 2.063 0 1.139-.925 2.065-2.064 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z" />
  </svg>
);

const TwitterIcon = () => (
  <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
    <path d="M23.953 4.57a10 10 0 01-2.825.775 4.958 4.958 0 002.163-2.723c-.951.555-2.005.959-3.127 1.184a4.92 4.92 0 00-8.384 4.482C7.69 8.095 4.067 6.13 1.64 3.162a4.822 4.822 0 00-.666 2.475c0 1.71.87 3.213 2.188 4.096a4.904 4.904 0 01-2.228-.616v.06a4.923 4.923 0 003.946 4.827 4.996 4.996 0 01-2.212.085 4.936 4.936 0 004.604 3.417 9.867 9.867 0 01-6.102 2.105c-.39 0-.779-.023-1.17-.067a13.995 13.995 0 007.557 2.209c9.053 0 13.998-7.496 13.998-13.985 0-.21 0-.42-.015-.63A9.935 9.935 0 0024 4.59z" />
  </svg>
);

const GitHubIcon = () => (
  <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
    <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z" />
  </svg>
);

const SOCIAL_ICONS: Record<SocialPlatform, () => JSX.Element> = {
  linkedin: LinkedInIcon,
  twitter: TwitterIcon,
  github: GitHubIcon,
};

const CORE_VALUES = [
  {
    title: "Radical Transparency",
    description: "No hidden fees, no fine print surprises. What you see is exactly what you pay.",
  },
  {
    title: "Genuine Care",
    description: "We measure success by your financial wellness, not just transaction volume.",
  },
  {
    title: "Empowerment First",
    description: "Every tool and feature is designed to give you more control over your finances.",
  },
] as const;

const TIMELINE_EVENTS = [
  {
    title: "Company Founded",
    description: "Sarah and Michael officially launch Rocket Credit with a mission to democratize financial flexibility.",
    dateLabel: "January 2020",
  },
  {
    title: "First 10,000 Users",
    description: "Reached our first major milestone with 10,000 happy customers and zero hidden fees.",
    dateLabel: "March 2021",
  },
  {
    title: "Series A Funding",
    description: "Raised $25M to expand our platform and bring transparent payments to more people.",
    dateLabel: "August 2022",
  },
  {
    title: "Financial Wellness Tools",
    description: "Launched budgeting tools and financial education resources to empower smarter spending.",
    dateLabel: "June 2023",
  },
  {
    title: "500K+ Customers",
    description: "Celebrating half a million customers who trust Rocket Credit for transparent, flexible payments.",
    dateLabel: "December 2024",
  },
] as const;

type SocialProfile = {
  platform: SocialPlatform;
  href: string;
  label: string;
};

type LeadershipTeamMember = {
  name: string;
  role: string;
  bio: string;
  imageSrc: string;
  fallbackSrc: string;
  socials: SocialProfile[];
};

const LEADERSHIP_TEAM: LeadershipTeamMember[] = [
  {
    name: "Sarah Chen",
    role: "CEO & Co-Founder",
    bio: "Former Goldman Sachs analyst with a passion for democratizing financial services. Stanford MBA, advocates for financial literacy education.",
    imageSrc:
      "https://images.unsplash.com/photo-1494790108755-2616b612b786?q=80&w=300&auto=format&fit=crop&ixlib=rb-4.0.3",
    fallbackSrc:
      "https://images.pexels.com/photos/1239291/pexels-photo-1239291.jpeg?auto=compress&cs=tinysrgb&w=300&h=300&fit=crop",
    socials: [
      { platform: "linkedin", href: "javascript:void(0)", label: "Connect with Sarah on LinkedIn" },
      { platform: "twitter", href: "javascript:void(0)", label: "Follow Sarah on Twitter" },
    ],
  },
  {
    name: "Michael Rodriguez",
    role: "CTO & Co-Founder",
    bio: "Former PayPal engineer with 15+ years in fintech. MIT Computer Science, specializes in secure payment systems and user experience.",
    imageSrc:
      "https://images.pexels.com/photos/2379004/pexels-photo-2379004.jpeg?auto=compress&cs=tinysrgb&w=300&h=300&fit=crop",
    fallbackSrc:
      "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?q=80&w=300&auto=format&fit=crop&ixlib=rb-4.0.3",
    socials: [
      { platform: "linkedin", href: "javascript:void(0)", label: "Connect with Michael on LinkedIn" },
      { platform: "github", href: "javascript:void(0)", label: "View Michael's GitHub" },
    ],
  },
  {
    name: "David Kim",
    role: "Head of Product",
    bio: "Former Stripe product manager focused on user-centric design. Berkeley Engineering, passionate about financial inclusion.",
    imageSrc:
      "https://images.pixabay.com/photo/2016/11/21/12/42/beard-1845166_960_720.jpg",
    fallbackSrc:
      "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?q=80&w=300&auto=format&fit=crop&ixlib=rb-4.0.3",
    socials: [
      { platform: "linkedin", href: "javascript:void(0)", label: "Connect with David on LinkedIn" },
      { platform: "twitter", href: "javascript:void(0)", label: "Follow David on Twitter" },
    ],
  },
  {
    name: "Emily Rodriguez",
    role: "Head of Customer Success",
    bio: "Former Zendesk customer experience leader. Northwestern MBA, dedicated to ensuring every customer feels heard and valued.",
    imageSrc:
      "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?q=80&w=300&auto=format&fit=crop&ixlib=rb-4.0.3",
    fallbackSrc:
      "https://images.pexels.com/photos/774909/pexels-photo-774909.jpeg?auto=compress&cs=tinysrgb&w=300&h=300&fit=crop",
    socials: [
      { platform: "linkedin", href: "javascript:void(0)", label: "Connect with Emily on LinkedIn" },
      { platform: "twitter", href: "javascript:void(0)", label: "Follow Emily on Twitter" },
    ],
  },
  {
    name: "James Wilson",
    role: "Head of Security",
    bio: "Former cybersecurity consultant with expertise in financial systems. Carnegie Mellon Computer Science, ensures your data stays protected.",
    imageSrc:
      "https://images.pexels.com/photos/1222271/pexels-photo-1222271.jpeg?auto=compress&cs=tinysrgb&w=300&h=300&fit=crop",
    fallbackSrc:
      "https://images.unsplash.com/photo-1560250097-0b93528c311a?q=80&w=300&auto=format&fit=crop&ixlib=rb-4.0.3",
    socials: [
      { platform: "linkedin", href: "javascript:void(0)", label: "Connect with James on LinkedIn" },
      { platform: "github", href: "javascript:void(0)", label: "View James's GitHub" },
    ],
  },
  {
    name: "Lisa Thompson",
    role: "Head of Marketing",
    bio: "Former Airbnb growth marketing lead with expertise in user acquisition. Wharton MBA, passionate about authentic brand storytelling.",
    imageSrc:
      "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?q=80&w=300&auto=format&fit=crop&ixlib=rb-4.0.3",
    fallbackSrc:
      "https://images.pexels.com/photos/1181519/pexels-photo-1181519.jpeg?auto=compress&cs=tinysrgb&w=300&h=300&fit=crop",
    socials: [
      { platform: "linkedin", href: "javascript:void(0)", label: "Connect with Lisa on LinkedIn" },
      { platform: "twitter", href: "javascript:void(0)", label: "Follow Lisa on Twitter" },
    ],
  },
];

type SecurityBadge = {
  title: string;
  description: string;
  Icon: () => JSX.Element;
};

const SECURITY_BADGES: SecurityBadge[] = [
  {
    title: "PCI DSS Compliant",
    description: "Level 1 PCI DSS certification for secure payment processing",
    Icon: ShieldCheckIcon,
  },
  {
    title: "256-bit SSL",
    description: "Bank-level encryption protects all your data in transit",
    Icon: LockIcon,
  },
  {
    title: "SOC 2 Type II",
    description: "Independently audited security and availability controls",
    Icon: CheckCircleIcon,
  },
  {
    title: "GDPR Compliant",
    description: "Full compliance with global privacy regulations",
    Icon: LightningIcon,
  },
];

type CommunityImpact = {
  title: string;
  statistic: string;
  description: string;
  Icon: () => JSX.Element;
};

const COMMUNITY_IMPACTS: CommunityImpact[] = [
  {
    title: "Financial Education",
    statistic: "50,000+",
    description: "People reached through our free financial literacy workshops and online resources",
    Icon: BookIcon,
  },
  {
    title: "Community Support",
    statistic: "$500K+",
    description: "Donated to financial wellness nonprofits and community organizations",
    Icon: HeartIcon,
  },
  {
    title: "Small Business Support",
    statistic: "2,500+",
    description: "Small businesses empowered with flexible payment solutions to grow their customer base",
    Icon: RocketIcon,
  },
];

const AboutUs = () => (
  <div className="bg-gradient-hero">
    <section className="relative overflow-hidden py-20 lg:py-32">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center">
          <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold text-gray-800 leading-tight mb-6">
            Empowering Your
            <span className="bg-gradient-primary bg-clip-text text-transparent">
              Financial Journey
            </span>
          </h1>
          <p className="text-xl text-gray-600 mb-8 leading-relaxed max-w-3xl mx-auto">
            At Rocket Credit, we believe financial wellness shouldn't come with
            anxiety. We're building a world where flexible payments empower
            confident choices, not create financial stress.
          </p>

          {/* Trust Stats */}
          <StatGrid
            className="mx-auto grid max-w-4xl grid-cols-2 gap-8 md:grid-cols-4"
            cardClassName="text-center"
            valueClassName="mb-2 text-3xl font-bold text-primary"
            labelClassName="text-sm text-gray-600"
            items={[
              { value: "500K+", label: "Happy Customers" },
              { value: "10K+", label: "Partner Merchants" },
              { value: "$2B+", label: "Processed Safely" },
              { value: "99.9%", label: "Uptime" },
            ]}
          />
        </div>
      </div>
    </section>

    {/* Mission Section */}
    <section className="py-20 bg-white">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid lg:grid-cols-2 gap-16 items-center">
          {/* Mission Content */}
          <div>
            <h2 className="text-3xl lg:text-4xl font-bold text-gray-800 mb-6">
              Our Mission: Financial Wellness for Everyone
            </h2>
            <p className="text-lg text-gray-600 mb-6 leading-relaxed">
              We're not just another payment method. Rocket Credit exists to
              democratize financial flexibility, making it accessible,
              transparent, and genuinely helpful for people's financial
              wellbeing.
            </p>
            <p className="text-lg text-gray-600 mb-8 leading-relaxed">
              Every feature we build, every partnership we form, and every
              decision we make is guided by one simple question: "Does this
              genuinely help our users make better financial choices?"
            </p>

            {/* Core Values */}
            <div className="space-y-4">
              {CORE_VALUES.map(({ title, description }) => (
                <div key={title} className="flex items-start">
                  <div className="w-6 h-6 bg-gradient-primary rounded-full flex items-center justify-center mr-4 mt-1">
                    <CheckIcon />
                  </div>
                  <div>
                    <h3 className="font-semibold text-gray-800 mb-1">{title}</h3>
                    <p className="text-gray-600">{description}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Mission Image */}
          <div className="relative">
            <div className="bg-gradient-secondary rounded-2xl p-8 shadow-hover">
              <img
                src="https://images.pexels.com/photos/3184465/pexels-photo-3184465.jpeg?auto=compress&cs=tinysrgb&w=600&h=400&fit=crop"
                alt="Team collaboration"
                className="w-full h-80 object-cover rounded-xl"
                loading="lazy"
                data-fallback="https://images.unsplash.com/photo-1522071820081-009f0129c71c?q=80&w=600&auto=format&fit=crop&ixlib=rb-4.0.3"
                onError={applyImageFallback}
              />
            </div>
          </div>
        </div>
      </div>
    </section>

    {/* Founder Story Section */}
    <section className="py-20 bg-gradient-secondary">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <SectionHeader
          title="Founded on Personal Experience"
          subtitle="Rocket Credit was born from our founders' own struggles with traditional credit and their vision for a more transparent, user-friendly financial future."
        />

        <div className="grid lg:grid-cols-2 gap-16 items-center">
          {/* Founder Image */}
          <div className="relative">
            <img
              src="https://images.pixabay.com/photo/2016/11/21/14/53/man-1845814_960_720.jpg"
              alt="Sarah Chen, CEO & Co-Founder"
              className="w-full h-96 object-cover rounded-2xl shadow-hover"
              loading="lazy"
              data-fallback="https://images.unsplash.com/photo-1494790108755-2616b612b786?q=80&w=600&auto=format&fit=crop&ixlib=rb-4.0.3"
              onError={applyImageFallback}
            />

            {/* Quote Overlay */}
            <div className="absolute bottom-6 left-6 right-6 bg-white/95 backdrop-blur-sm rounded-xl p-4">
              <p className="text-gray-800 font-medium italic">
                "Financial stress shouldn't be the price of flexibility. We
                built Rocket Credit to prove that."
              </p>
              <p className="text-sm text-gray-600 mt-2">
                - Sarah Chen, CEO & Co-Founder
              </p>
            </div>
          </div>

          {/* Founder Story */}
          <div>
            <h3 className="text-2xl font-bold text-gray-800 mb-6">
              The Story Behind Rocket Credit
            </h3>
            <div className="space-y-4 text-gray-600 leading-relaxed">
              <p>
                In 2019, our co-founder Sarah Chen was a graduate student
                juggling part-time work and mounting expenses. When her laptop
                died during finals week, she needed a replacement immediately
                but couldn't afford the full price upfront.
              </p>
              <p>
                The existing "buy now, pay later" options were either predatory
                with hidden fees or had confusing terms that made her anxious
                about the real cost. She ended up using a high-interest credit
                card, creating months of financial stress.
              </p>
              <p>
                That experience sparked a question: "Why can't flexible payments
                be transparent, fair, and actually helpful for people's
                financial wellness?" Sarah partnered with fintech veteran
                Michael Rodriguez, and together they built Rocket Credit from the
                ground up.
              </p>
              <p className="font-medium text-gray-800">
                Today, Rocket Credit serves over 500,000 customers who deserve better
                than hidden fees and financial anxiety.
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>

    {/* Company Timeline */}
    <section className="py-20 bg-white">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <SectionHeader
          title="Our Journey So Far"
          subtitle="Key milestones in building a more transparent financial future"
        />

        <div className="relative">
          {/* Timeline Line */}
          <div className="absolute left-1/2 transform -translate-x-px h-full w-0.5 bg-gradient-primary"></div>

          {/* Timeline Items */}
          <div className="space-y-12">
            {TIMELINE_EVENTS.map(({ title, description, dateLabel }, index) => {
              const isLeftAligned = index % 2 === 0;
              const card = (
                <div className="bg-white rounded-xl p-6 shadow-card hover:shadow-hover transition-smooth">
                  <h3 className="text-lg font-semibold text-gray-800 mb-2">{title}</h3>
                  <p className="text-gray-600 mb-2">{description}</p>
                  <span className="text-sm text-primary font-medium">{dateLabel}</span>
                </div>
              );

              return (
                <div key={title} className="relative flex items-center">
                  <div className={cn("flex-1 pr-8", isLeftAligned && "text-right")}>{isLeftAligned ? card : null}</div>
                  <div className="w-4 h-4 bg-gradient-primary rounded-full border-4 border-white shadow-card"></div>
                  <div className="flex-1 pl-8">{isLeftAligned ? null : card}</div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </section>

    {/* Team Section */}
    <section className="py-20 bg-gradient-secondary">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <SectionHeader
          title="Meet Our Leadership Team"
          subtitle="The passionate people building a more transparent financial future"
        />

        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-8">
          {LEADERSHIP_TEAM.map(({ name, role, bio, imageSrc, fallbackSrc, socials }) => (
            <div
              key={name}
              className="bg-white rounded-2xl p-6 shadow-card hover:shadow-hover transition-smooth text-center"
            >
              <img
                src={imageSrc}
                alt={name}
                className="w-24 h-24 rounded-full object-cover mx-auto mb-4"
                loading="lazy"
                data-fallback={fallbackSrc}
                onError={applyImageFallback}
              />
              <h3 className="text-xl font-semibold text-gray-800 mb-2">{name}</h3>
              <p className="text-primary font-medium mb-3">{role}</p>
              <p className="text-gray-600 text-sm leading-relaxed mb-4">{bio}</p>
              <div className="flex justify-center space-x-3">
                {socials.map(({ platform, href, label }) => {
                  const Icon = SOCIAL_ICONS[platform];
                  return (
                    <a
                      key={`${name}-${platform}`}
                      href={href}
                      aria-label={label}
                      className="w-8 h-8 bg-gray-100 rounded-full flex items-center justify-center hover:bg-primary hover:text-white transition-smooth"
                    >
                      <Icon />
                    </a>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>

    {/* Security & Certifications */}
    <section className="py-20 bg-white">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <SectionHeader
          title="Security & Trust Certifications"
          subtitle="Your security and privacy are our top priorities"
        />

        <div className="grid md:grid-cols-2 lg:grid-cols-4 gap-8">
          {SECURITY_BADGES.map(({ title, description, Icon }) => (
            <div
              key={title}
              className="bg-gradient-secondary rounded-2xl p-6 text-center shadow-card hover:shadow-hover transition-smooth"
            >
              <div className="w-16 h-16 bg-gradient-primary rounded-2xl flex items-center justify-center mx-auto mb-4">
                <Icon />
              </div>
              <h3 className="font-semibold text-gray-800 mb-2">{title}</h3>
              <p className="text-sm text-gray-600">{description}</p>
            </div>
          ))}
        </div>
      </div>
    </section>

    {/* Community Impact */}
    <section className="py-20 bg-white">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <SectionHeader
          title="Our Community Impact"
          subtitle="Building financial wellness extends beyond our platform"
        />

        <div className="grid md:grid-cols-3 gap-8">
          {COMMUNITY_IMPACTS.map(({ title, statistic, description, Icon }) => (
            <div key={title} className="text-center">
              <div className="w-20 h-20 bg-gradient-primary rounded-2xl flex items-center justify-center mx-auto mb-6">
                <Icon />
              </div>
              <h3 className="text-xl font-semibold text-gray-800 mb-4">{title}</h3>
              <div className="text-3xl font-bold text-primary mb-2">{statistic}</div>
              <p className="text-gray-600">{description}</p>
            </div>
          ))}
        </div>
      </div>
    </section>

    {/* CTA Section */}
    <section className="py-20 bg-gradient-primary">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
        <h2 className="text-3xl lg:text-4xl font-bold text-white mb-6">
          Ready to Join Our Mission?
        </h2>
        <p className="text-xl text-white/90 mb-8 leading-relaxed">
          Whether you're looking for flexible payment options or want to partner
          with us, we're here to support your financial journey.
        </p>

        <div className="flex flex-col sm:flex-row gap-4 justify-center">
          <a
            href="/account-dashboard"
            className="bg-white text-primary font-semibold py-4 px-8 rounded-lg hover:bg-gray-50 transition-smooth shadow-card hover:shadow-hover inline-flex items-center justify-center"
          >
            Get Started Today
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
                d="M9 5l7 7-7 7"
              />
            </svg>
          </a>
          <a
            href="/for-businesses"
            className="bg-white/20 text-white font-semibold py-4 px-8 rounded-lg hover:bg-white/30 transition-smooth border border-white/30 inline-flex items-center justify-center"
          >
            Partner With Us
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
                d="M17 8l4 4m0 0l-4 4m4-4H3"
              />
            </svg>
          </a>
        </div>
      </div>
    </section>
  </div>
);

export default AboutUs;
