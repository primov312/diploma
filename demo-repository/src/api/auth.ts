// AI-assisted (Claude), 2026: role and seller partner added; reviewed by the author.
import { api } from './client';

/** The three kinds of users. The server decides the role; the client only uses it to choose screens. */
export type Role = 'CUSTOMER' | 'SELLER' | 'ADMIN';

export type User = {
  id: number;
  email: string;
  displayName: string;
  createdAt: string;
  role: Role;
  /** sellers only: the partner they work for */
  partnerSlug?: string;
  partnerName?: string;
};

export const authApi = {
  me: () => api<User>('/api/me'),
  register: (email: string, password: string, displayName: string) =>
    api<User>('/api/auth/register', { method: 'POST', body: { email: email.trim(), password, displayName } }),
  login: (email: string, password: string) =>
    api<User>('/api/auth/login', { method: 'POST', body: { email: email.trim(), password } }),
  logout: () => api<void>('/api/auth/logout', { method: 'POST' }),
};
