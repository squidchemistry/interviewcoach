import { createContext, useContext } from 'react';
import type { LoginRequest, RegisterRequest, UserResponse } from '../../api/types';

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

export interface AuthContextValue {
  status: AuthStatus;
  user: UserResponse | null;
  login: (body: LoginRequest) => Promise<UserResponse>;
  register: (body: RegisterRequest) => Promise<UserResponse>;
  logout: () => Promise<void>;
  setUser: (user: UserResponse) => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return value;
}

/** Same rules as the backend's SafeRedirects: only same-site relative paths. */
export function safeRedirect(path: string | null | undefined): string {
  if (!path || !path.startsWith('/') || path.startsWith('//') || path.startsWith('/\\') || path.includes('//')) {
    return '/';
  }
  return path;
}

/**
 * Where to go after signing in: candidates without a profile go through onboarding first. Admins are never
 * intercepted; they manage the platform and may not have a candidate profile.
 */
export function postLoginPath(user: UserResponse, redirect: string | null | undefined): string {
  const target = safeRedirect(redirect);
  if (!user.profile && user.role === 'USER') {
    return target === '/' ? '/onboarding' : `/onboarding?redirect=${encodeURIComponent(target)}`;
  }
  return target;
}
