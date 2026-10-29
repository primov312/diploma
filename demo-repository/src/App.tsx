// AI-assisted (Claude), 2026: seller and administrator routes added; reviewed by the author.
import { Link, Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import RequireRole from './auth/RequireRole';
import { homeFor } from './auth/roles';
import Layout from './components/Layout';
import AccountDashboard from './pages/AccountDashboard';
import ForBusinesses from './pages/ForBusinesses';
import HomePage from './pages/HomePage';
import HowItWorks from './pages/HowItWorks';
import ApplicationDetailPage from './pages/app/ApplicationDetailPage';
import ApplicationsPage from './pages/app/ApplicationsPage';
import ApplyPage from './pages/app/ApplyPage';
import BalancePage from './pages/app/BalancePage';
import ConsentsPage from './pages/app/ConsentsPage';
import RepaymentsPage from './pages/app/RepaymentsPage';
import HistoryPage from './pages/app/HistoryPage';
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import MarketplacePage from './pages/stores/MarketplacePage';
import SellerOverviewPage from './pages/seller/SellerOverviewPage';
import SellerOrdersPage from './pages/seller/SellerOrdersPage';
import SellerCatalogPage from './pages/seller/SellerCatalogPage';
import AdminOverviewPage from './pages/admin/AdminOverviewPage';
import AdminUsersPage from './pages/admin/AdminUsersPage';
import AdminReviewsPage from './pages/admin/AdminReviewsPage';
import AdminReviewDetailPage from './pages/admin/AdminReviewDetailPage';
import AdminPartnersPage from './pages/admin/AdminPartnersPage';
import AdminAuditPage from './pages/admin/AdminAuditPage';

const NotFound = () => {
  const { user } = useAuth();
  return (
    <div className="page-shell">
      <h1 className="text-4xl font-bold text-gray-800">Page not found</h1>
      <p className="mt-4 text-gray-600">This page doesn't exist.</p>
      <Link to={homeFor(user)} className="btn-primary mt-6">{user ? 'Go to your start page' : 'Go to the home page'}</Link>
    </div>
  );
};

const App = () => (
  <Routes>
    <Route element={<Layout />}>
      <Route index element={<HomePage />} />
      <Route path="how-it-works" element={<HowItWorks />} />
      <Route path="for-businesses" element={<ForBusinesses />} />
      <Route path="about-us" element={<Navigate to="/" replace />} />
      <Route path="login" element={<LoginPage />} />
      <Route path="register" element={<RegisterPage />} />

      {/* One public marketplace for every seller; "Apply for credit" leads into the private flow. */}
      <Route path="marketplace" element={<MarketplacePage />} />
      <Route path="stores" element={<Navigate to="/marketplace" replace />} />
      <Route path="stores/:slug" element={<Navigate to="/marketplace" replace />} />

      {/* Each area needs a session with its role; RequireRole redirects to /login and back (query string
          included), or to the user's own start page. The server enforces the same roles. */}
      <Route element={<RequireRole role="CUSTOMER" />}>
        <Route path="account-dashboard" element={<AccountDashboard />} />
        <Route path="balance" element={<BalancePage />} />
        <Route path="repayments" element={<RepaymentsPage />} />
        <Route path="history" element={<HistoryPage />} />
        <Route path="consents" element={<ConsentsPage />} />
        <Route path="apply" element={<ApplyPage />} />
        <Route path="applications" element={<ApplicationsPage />} />
        <Route path="applications/:id" element={<ApplicationDetailPage />} />
      </Route>
      <Route element={<RequireRole role="SELLER" />}>
        <Route path="seller" element={<SellerOverviewPage />} />
        <Route path="seller/orders" element={<SellerOrdersPage />} />
        <Route path="seller/catalog" element={<SellerCatalogPage />} />
      </Route>
      <Route element={<RequireRole role="ADMIN" />}>
        <Route path="admin" element={<AdminOverviewPage />} />
        <Route path="admin/users" element={<AdminUsersPage />} />
        <Route path="admin/reviews" element={<AdminReviewsPage />} />
        <Route path="admin/reviews/:applicationId" element={<AdminReviewDetailPage />} />
        <Route path="admin/partners" element={<AdminPartnersPage />} />
        <Route path="admin/audit" element={<AdminAuditPage />} />
      </Route>

      <Route path="*" element={<NotFound />} />
    </Route>
  </Routes>
);

export default App;
