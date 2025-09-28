import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';

type NavLinkItem = {
  to: string;
  label: string;
};

const NAV_LINKS: NavLinkItem[] = [
  { to: '/how-it-works', label: 'How It Works' },
  { to: '/for-businesses', label: 'For Businesses' },
  { to: '/about-us', label: 'About Us' },
];

const Header = () => {
  const location = useLocation();
  const [isMobileOpen, setIsMobileOpen] = useState<boolean>(false);

  useEffect(() => {
    setIsMobileOpen(false);
  }, [location.pathname]);

  return (
    <header className="sticky top-0 z-50 border-b border-gray-100 bg-white/95 shadow-soft backdrop-blur-sm">
      <nav className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <Link to="/" className="flex items-center space-x-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-primary">
            <svg className="h-5 w-5 text-white" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
            </svg>
          </div>
          <span className="text-xl font-bold text-gray-800">Rocket Credit</span>
        </Link>

        <div className="hidden items-center space-x-8 md:flex">
          {NAV_LINKS.map(({ to, label }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `font-medium transition-smooth ${isActive ? 'text-primary' : 'text-gray-600 hover:text-primary'}`
              }
            >
              {label}
            </NavLink>
          ))}
          <button
            type="button"
            className="font-medium text-gray-600 transition-smooth hover:text-primary"
          >
            Help Center
          </button>
        </div>

        <div className="flex items-center space-x-4">
          <NavLink
            to="/account-dashboard"
            className={({ isActive }) =>
              `hidden text-sm font-medium transition-smooth sm:inline-flex ${
                isActive ? 'text-primary' : 'text-gray-600 hover:text-primary'
              }`
            }
          >
            Sign In
          </NavLink>
          <Link to="/account-dashboard" className="btn-gradient px-4 py-2 text-sm">
            Get Started
          </Link>
          <button
            type="button"
            className="rounded-lg p-2 transition-smooth hover:bg-gray-100 md:hidden"
            onClick={() => setIsMobileOpen((prev) => !prev)}
            aria-label="Toggle navigation menu"
            aria-expanded={isMobileOpen}
          >
            <svg className="h-6 w-6 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="2"
                d={isMobileOpen ? 'M6 18L18 6M6 6l12 12' : 'M4 6h16M4 12h16M4 18h16'}
              />
            </svg>
          </button>
        </div>
      </nav>

      {isMobileOpen && (
        <div className="border-t border-gray-100 bg-white px-4 pb-6 pt-4 shadow-soft md:hidden">
          <div className="space-y-4">
            {NAV_LINKS.map(({ to, label }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  `block font-medium transition-smooth ${
                    isActive ? 'text-primary' : 'text-gray-700 hover:text-primary'
                  }`
                }
              >
                {label}
              </NavLink>
            ))}
            <button
              type="button"
              className="block font-medium text-gray-700 transition-smooth hover:text-primary"
            >
              Help Center
            </button>
            <div className="pt-3">
              <Link to="/account-dashboard" className="btn-gradient block text-center">
                Get Started
              </Link>
            </div>
            <NavLink
              to="/account-dashboard"
              className={({ isActive }) =>
                `block text-sm font-medium transition-smooth ${
                  isActive ? 'text-primary' : 'text-gray-600 hover:text-primary'
                }`
              }
            >
              Sign In
            </NavLink>
          </div>
        </div>
      )}
    </header>
  );
};

export default Header;
