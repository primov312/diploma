import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Icon, type IconName } from './common/Icon';

type NavItem = { to: string; label: string; icon: IconName; also?: string[] };

const publicLinks: NavItem[] = [
  { to: '/marketplace', label: 'Marketplace', icon: 'store' },
  { to: '/how-it-works', label: 'How it works', icon: 'info' },
  { to: '/for-businesses', label: 'For businesses', icon: 'building' },
];
/** Signed-in navigation stays at four destinations; Balance, Repayments and Activity share the Wallet tabs. */
const accountLinks: NavItem[] = [
  { to: '/marketplace', label: 'Marketplace', icon: 'store' },
  { to: '/account-dashboard', label: 'Dashboard', icon: 'chart' },
  { to: '/applications', label: 'Applications', icon: 'file', also: ['/apply'] },
  { to: '/balance', label: 'Wallet', icon: 'wallet', also: ['/repayments', '/history'] },
];
const linkClass = (active: boolean) => 'flex min-h-11 items-center gap-2 rounded-md px-3 text-sm font-semibold transition-colors '
  + (active ? 'bg-white/15 text-white' : 'text-primary-100 hover:bg-white/10 hover:text-white');

const Header = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, loading, logout } = useAuth();
  const menuButton = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  useEffect(() => { setOpen(false); }, [location.pathname, location.search]);
  useEffect(() => {
    const close = (event: KeyboardEvent) => { if (event.key === 'Escape' && open) { setOpen(false); menuButton.current?.focus(); } };
    document.addEventListener('keydown', close);
    return () => document.removeEventListener('keydown', close);
  }, [open]);

  const links = !loading && user ? accountLinks : publicLinks;
  const items = <>
    {links.map((item) => <NavLink key={item.to} to={item.to}
      className={({ isActive }) => linkClass(isActive || (item.also ?? []).some((path) => location.pathname.startsWith(path)))}>
      <Icon name={item.icon} />{item.label}
    </NavLink>)}
    {!loading && (user
      ? <button type="button" className="flex min-h-11 items-center gap-2 rounded-md px-3 text-sm font-semibold text-white hover:bg-white/10 lg:ml-4"
          onClick={async () => { await logout(); navigate('/', { replace: true }); }}><Icon name="logout" />Sign out</button>
      : <>
        <NavLink to="/login" className={({ isActive }) => linkClass(isActive) + ' lg:ml-4'}><Icon name="user" />Sign in</NavLink>
        <Link to="/register" className="btn-secondary text-sm">Create account</Link>
      </>)}
  </>;

  return <header className="sticky top-0 z-50 border-b border-primary-700 bg-primary text-white">
    <a href="#main-content" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-3 focus:z-50 focus:rounded focus:bg-white focus:p-3 focus:text-primary">Skip to content</a>
    <nav aria-label="Main navigation" className="flex min-h-16 items-center justify-between gap-4 px-4 py-2 md:px-6">
      <Link to={user ? '/account-dashboard' : '/'} className="flex min-h-11 shrink-0 items-center gap-3">
        <Icon name="layers" className="h-7 w-7" /><span className="text-xl font-bold tracking-tight">Rocket Credit</span>
        <span className="rounded border border-primary-400 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-primary-100">Demo</span>
      </Link>
      <div className="hidden items-center gap-1 lg:flex">{items}</div>
      <button ref={menuButton} type="button" className="flex min-h-11 min-w-11 items-center justify-center rounded-md hover:bg-white/10 lg:hidden"
        aria-label="Toggle navigation menu" aria-expanded={open} aria-controls="mobile-navigation" onClick={() => setOpen(!open)}>
        <Icon name={open ? 'close' : 'menu'} />
      </button>
    </nav>
    {open && <nav id="mobile-navigation" aria-label="Mobile navigation" className="grid max-h-[calc(100dvh-5rem)] gap-1 overflow-y-auto border-t border-primary-700 px-4 pb-4 pt-2 lg:hidden">{items}</nav>}
  </header>;
};
export default Header;
