import { Link, Navigate, Route, Routes } from 'react-router-dom';
import RequireAuth from './auth/RequireAuth';
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

const NotFound = () => (
  <div className="page-shell">
    <h1 className="text-4xl font-bold text-gray-800">Page not found</h1>
    <p className="mt-4 text-gray-600">This page doesn't exist.</p>
    <Link to="/" className="btn-primary mt-6">Go to the home page</Link>
  </div>
);

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

      {/* Everything below needs a session; RequireAuth redirects to /login and back (query string included). */}
      <Route element={<RequireAuth />}>
        <Route path="account-dashboard" element={<AccountDashboard />} />
        <Route path="balance" element={<BalancePage />} />
        <Route path="repayments" element={<RepaymentsPage />} />
        <Route path="history" element={<HistoryPage />} />
        <Route path="consents" element={<ConsentsPage />} />
        <Route path="apply" element={<ApplyPage />} />
        <Route path="applications" element={<ApplicationsPage />} />
        <Route path="applications/:id" element={<ApplicationDetailPage />} />
      </Route>

      <Route path="*" element={<NotFound />} />
    </Route>
  </Routes>
);

export default App;
