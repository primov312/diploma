// AI-assisted (Claude), 2026: shows the notice left by a role redirect; reviewed by the author.
import { Outlet, useLocation } from 'react-router-dom';
import Header from './Header';
import { Notice } from './app/Ui';

/** RequireRole leaves a short notice when it sends a user to their own start page. */
const RedirectNotice = () => {
  const notice = (useLocation().state as { notice?: string } | null)?.notice;
  return notice ? <div className="px-4 pt-4 md:px-6"><Notice tone="warning">{notice}</Notice></div> : null;
};

/** The demo notice lives here once, so individual pages do not repeat it. */
const Layout = () => (
  <div className="flex min-h-screen flex-col bg-secondary-50 text-secondary-900">
    <Header />
    <main id="main-content" className="min-w-0 flex-1" tabIndex={-1}>
      <RedirectNotice />
      <Outlet />
    </main>
    <footer className="border-t border-gray-200 px-4 py-4 text-xs text-gray-500 md:px-6">
      Rocket Credit is a diploma demo: synthetic data, simulated money, no real lending or payments.
      Amounts are shown in HUF (fixed rate 360 HUF = 1 USD).
    </footer>
  </div>
);

export default Layout;
