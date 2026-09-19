import { api } from './client';

export type User = {
  id: number;
  email: string;
  displayName: string;
  createdAt: string;
};

export const authApi = {
  me: () => api<User>('/api/me'),
  register: (email: string, password: string, displayName: string) =>
    api<User>('/api/auth/register', { method: 'POST', body: { email: email.trim(), password, displayName } }),
  login: (email: string, password: string) =>
    api<User>('/api/auth/login', { method: 'POST', body: { email: email.trim(), password } }),
  logout: () => api<void>('/api/auth/logout', { method: 'POST' }),
};
