import { Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import AccountDashboard from './pages/AccountDashboard';
import AboutUs from './pages/AboutUs';
import ForBusinesses from './pages/ForBusinesses';
import HomePage from './pages/HomePage';
import HowItWorks from './pages/HowItWorks';

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
      <Route path="account-dashboard" element={<AccountDashboard />} />
      <Route path="*" element={<NotFound />} />
    </Route>
  </Routes>
);

export default App;
