import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from 'react';
import { authApi } from '../../api/endpoints';
import { clearSession, getSession, refreshSession, setSession, subscribe, updateUser } from '../../api/session';
import type { LoginRequest, RegisterRequest, UserResponse } from '../../api/types';
import { AuthContext, type AuthContextValue } from './authContext';

/** Restores the session from the refresh cookie on load and exposes login / register / logout. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const session = useSyncExternalStore(subscribe, getSession);
  const [bootstrapped, setBootstrapped] = useState(false);
  const queryClient = useQueryClient();

  useEffect(() => {
    let active = true;
    void refreshSession().finally(() => {
      if (active) setBootstrapped(true);
    });
    return () => {
      active = false;
    };
  }, []);

  const login = useCallback(async (body: LoginRequest) => {
    const auth = await authApi.login(body);
    setSession(auth);
    return auth.user;
  }, []);

  const register = useCallback(async (body: RegisterRequest) => {
    const auth = await authApi.register(body);
    setSession(auth);
    return auth.user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      clearSession();
      queryClient.clear();
    }
  }, [queryClient]);

  const setUser = useCallback((user: UserResponse) => updateUser(user), []);

  const value = useMemo<AuthContextValue>(
    () => ({
      status: !bootstrapped && !session.user ? 'loading' : session.user ? 'authenticated' : 'anonymous',
      user: session.user,
      login,
      register,
      logout,
      setUser,
    }),
    [bootstrapped, session.user, login, register, logout, setUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
