import { Outlet } from 'react-router-dom';
import Footer from './Footer';
import Header from './Header';

const Layout = () => (
  <div className="flex min-h-screen flex-col bg-secondary-50 text-secondary-900">
    <Header />
    <main className="flex-1">
      <Outlet />
    </main>
    <Footer />
  </div>
);

export default Layout;
