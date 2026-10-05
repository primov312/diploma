import { Outlet } from 'react-router-dom';
import Header from './Header';

const Layout = () => (
  <div className="flex min-h-screen flex-col bg-secondary-50 text-secondary-900">
    <Header />
    <main id="main-content" className="min-w-0 flex-1" tabIndex={-1}>
      <Outlet />
    </main>
  </div>
);

export default Layout;
