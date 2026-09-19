import { Route, Routes } from 'react-router-dom';
import RequireAuth from './auth/RequireAuth';
import Layout from './components/Layout';
import AboutUs from './pages/AboutUs';
import AccountDashboard from './pages/AccountDashboard';
import ForBusinesses from './pages/ForBusinesses';
import HomePage from './pages/HomePage';
import HowItWorks from './pages/HowItWorks';
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';

const NotFound = () => (
  <div className="bg-gradient-hero py-24 text-center">
    <h1 className="text-4xl font-bold text-gray-800">Page not found</h1>
    <p className="mt-4 text-gray-600">The page you're looking for doesn't exist or has been moved.</p>
  </div>
);

const App = () => (
  <Routes>
    <Route element={<Layout />}>
      <Route index element={<HomePage />} />
      <Route path="how-it-works" element={<HowItWorks />} />
      <Route path="for-businesses" element={<ForBusinesses />} />
      <Route path="about-us" element={<AboutUs />} />
      <Route path="login" element={<LoginPage />} />
      <Route path="register" element={<RegisterPage />} />

      {/* Everything below needs a session; RequireAuth redirects to /login and back. */}
      <Route element={<RequireAuth />}>
        <Route path="account-dashboard" element={<AccountDashboard />} />
      </Route>

      <Route path="*" element={<NotFound />} />
    </Route>
  </Routes>
);

export default App;
