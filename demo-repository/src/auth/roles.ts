// AI-assisted (Claude), 2026; reviewed by the author.
import type { Role, User } from '../api/auth';

/** Each kind of user has its own start page; the logo, sign-in and "page not found" all use this. */
export const HOME: Record<Role, string> = { CUSTOMER: '/account-dashboard', SELLER: '/seller', ADMIN: '/admin' };

export const homeFor = (user: User | null): string => (user ? HOME[user.role] : '/');

/** Where a path belongs; used to decide whether a remembered "from" path suits the signed-in role. */
export const roleForPath = (path: string): Role | null => {
  if (path === '/admin' || path.startsWith('/admin/')) return 'ADMIN';
  if (path === '/seller' || path.startsWith('/seller/')) return 'SELLER';
  if (/^\/(account-dashboard|balance|repayments|history|consents|apply|applications)(\/|\?|$)/.test(path)) return 'CUSTOMER';
  return null;
};

export const ROLE_LABEL: Record<Role, string> = { CUSTOMER: 'customer', SELLER: 'seller', ADMIN: 'administrator' };
