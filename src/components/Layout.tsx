import { useEffect } from 'react';
import { Outlet } from 'react-router-dom';
import Footer from './Footer';
import Header from './Header';

const ROCKET_SCRIPT_ID = 'rocket-web-script';
const ROCKET_SCRIPT_SRC =
  'https://static.rocket.new/rocket-web.js?_cfg=https%3A%2F%2Fflexpay6817back.builtwithrocket.new&_be=https%3A%2F%2Fapplication.rocket.new&_v=0.1.8';

const Layout = () => {
  useEffect(() => {
    if (typeof document === 'undefined') return;

    if (!document.getElementById(ROCKET_SCRIPT_ID)) {
      const script = document.createElement('script');
      script.id = ROCKET_SCRIPT_ID;
      script.type = 'module';
      script.src = ROCKET_SCRIPT_SRC;
      document.body.appendChild(script);
    }
  }, []);

  return (
    <div className="flex min-h-screen flex-col bg-secondary-50 text-secondary-900">
      <Header />
      <main className="flex-1">
        <Outlet />
      </main>
      <Footer />
    </div>
  );
};

export default Layout;
