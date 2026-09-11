import { Link } from 'react-router-dom';

type FooterNavLink = {
  label: string;
  to: string;
};

type SocialLink = {
  label: string;
  href: string;
  icon: JSX.Element;
};

const productLinks: FooterNavLink[] = [
  { label: 'Overview', to: '/how-it-works' },
  { label: 'Pricing', to: '#' },
  { label: 'Features', to: '/for-businesses' },
  { label: 'Customers', to: '#' },
];

const companyLinks: FooterNavLink[] = [
  { label: 'About Us', to: '/about-us' },
  { label: 'Careers', to: '#' },
  { label: 'Newsroom', to: '#' },
  { label: 'Partners', to: '/for-businesses' },
];

const supportLinks: FooterNavLink[] = [
  { label: 'Contact Us', to: '#' },
  { label: 'FAQ', to: '#' },
  { label: 'Security', to: '#' },
  { label: 'Privacy Policy', to: '#' },
];

const legalLinks: FooterNavLink[] = [
  { label: 'Terms of Service', to: '#' },
  { label: 'Privacy Policy', to: '#' },
  { label: 'Accessibility', to: '#' },
];

const socialLinks: SocialLink[] = [
  {
    label: 'Twitter',
    href: '#',
    icon: (
      <svg className="h-5 w-5" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M24 4.557c-.883.392-1.832.656-2.828.775 1.017-.609 1.798-1.574 2.165-2.724-.951.564-2.005.974-3.127 1.195-.897-.957-2.178-1.555-3.594-1.555-3.179 0-5.515 2.966-4.797 6.045C7.62 7.93 4.002 5.97 1.573 2.991.283 5.204.904 8.099 3.096 9.565c-.806-.026-1.566-.247-2.229-.616-.054 2.281 1.581 4.415 3.949 4.89-.693.188-1.452.232-2.224.084.626 1.956 2.444 3.379 4.6 3.419-2.07 1.623-4.678 2.348-7.29 2.04 2.179 1.397 4.768 2.212 7.548 2.212 9.142 0 14.307-7.721 13.995-14.646a9.935 9.935 0 0 0 2.457-2.549z" />
      </svg>
    ),
  },
  {
    label: 'LinkedIn',
    href: '#',
    icon: (
      <svg className="h-5 w-5" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M22.46 6c-.77.35-1.6.58-2.46.69.88-.53 1.56-1.37 1.88-2.38-.83.5-1.75.85-2.72 1.05C18.37 4.5 17.26 4 16 4c-2.35 0-4.27 1.92-4.27 4.29 0 .34.04.67.11.98-3.18-.16-6.35-1.87-8.46-4.46-.37.63-.58 1.37-.58 2.15 0 1.49.75 2.81 1.91 3.56-.71 0-1.37-.2-1.95-.5v.03c0 2.08 1.48 3.82 3.44 4.21-.67.18-1.43.22-2.2.08.63 1.96 2.44 3.38 4.6 3.42-2.07 1.62-4.68 2.35-7.29 2.04 2.18 1.4 4.77 2.21 7.55 2.21 7.71 0 11.92-6.19 11.92-11.95 0-.19-.01-.37-.02-.56.82-.6 1.54-1.36 2.12-2.23z" />
      </svg>
    ),
  },
  {
    label: 'GitHub',
    href: '#',
    icon: (
      <svg className="h-5 w-5" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12.017 0C5.396 0 .029 5.367.029 11.987c0 5.079 3.158 9.417 7.618 11.174-.105-.949-.199-2.403.041-3.439.219-.937 1.406-5.957 1.406-5.957s-.359-.72-.359-1.781c0-1.663.967-2.911 2.168-2.911 1.024 0 1.518.769 1.518 1.688 0 1.029-.653 2.567-.992 3.992-.285 1.193.6 2.165 1.775 2.165 2.128 0 3.768-2.245 3.768-5.487 0-2.861-2.063-4.869-5.008-4.869-3.41 0-5.409 2.562-5.409 5.199 0 1.033.394 2.143.889 2.741.099.12.112.225.085.345-.09.375-.293 1.199-.334 1.363-.053.225-.172.271-.402.165-1.495-.69-2.433-2.878-2.433-4.646 0-3.776 2.748-7.252 7.92-7.252 4.158 0 7.392 2.967 7.392 6.923 0 4.135-2.607 7.462-6.233 7.462-1.214 0-2.357-.629-2.75-1.378l-.748 2.853c-.271 1.043-1.002 2.35-1.492 3.146C9.57 23.812 10.763 24.009 12.017 24.009c6.624 0 11.99-5.367 11.99-11.988C24.007 5.367 18.641.001.012.001z" />
      </svg>
    ),
  },
];

const Footer = () => {
  const currentYear = new Date().getFullYear();

  return (
    <footer className="bg-gray-900 text-white">
      <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
        <div className="grid gap-12 md:grid-cols-4">
          <div>
            <Link to="/" className="flex items-center space-x-2">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-primary">
                <svg className="h-6 w-6 text-white" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
                </svg>
              </div>
              <span className="text-2xl font-bold">Rocket Credit</span>
            </Link>
            <p className="mt-4 text-sm text-gray-400">
              The trustworthy Buy Now Pay Later platform that empowers your financial wellness. No hidden fees, no surprises.
            </p>
          </div>

          <div>
            <h4 className="mb-6 font-semibold">Product</h4>
            <ul className="space-y-3 text-sm text-gray-400">
              {productLinks.map(({ label, to }) => (
                <li key={label}>
                  {to === '#' ? (
                    <span className="cursor-not-allowed opacity-70">{label}</span>
                  ) : (
                    <Link to={to} className="transition-smooth hover:text-white">
                      {label}
                    </Link>
                  )}
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h4 className="mb-6 font-semibold">Company</h4>
            <ul className="space-y-3 text-sm text-gray-400">
              {companyLinks.map(({ label, to }) => (
                <li key={label}>
                  {to === '#' ? (
                    <span className="cursor-not-allowed opacity-70">{label}</span>
                  ) : (
                    <Link to={to} className="transition-smooth hover:text-white">
                      {label}
                    </Link>
                  )}
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h4 className="mb-6 font-semibold">Support</h4>
            <ul className="space-y-3 text-sm text-gray-400">
              {supportLinks.map(({ label, to }) => (
                <li key={label}>
                  {to === '#' ? (
                    <span className="cursor-not-allowed opacity-70">{label}</span>
                  ) : (
                    <Link to={to} className="transition-smooth hover:text-white">
                      {label}
                    </Link>
                  )}
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className="mt-16">
          <h4 className="mb-6 font-semibold">Connect</h4>
          <div className="flex space-x-4">
            {socialLinks.map(({ label, href, icon }) => (
              <a
                key={label}
                href={href}
                aria-label={label}
                className="flex h-10 w-10 items-center justify-center rounded-lg bg-gray-800 transition-smooth hover:bg-primary"
              >
                {icon}
              </a>
            ))}
          </div>
        </div>

        <div className="mt-12 flex flex-col items-center justify-between gap-4 border-t border-gray-800 pt-8 text-sm text-gray-400 md:flex-row">
          <p>© {currentYear} Rocket Credit. All Rights Reserved.</p>
          <div className="flex space-x-6">
            {legalLinks.map(({ label, to }) => (
              <a key={label} href={to} className="transition-smooth hover:text-white">
                {label}
              </a>
            ))}
          </div>
        </div>
      </div>
    </footer>
  );
};

export default Footer;
