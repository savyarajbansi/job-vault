import React, { createContext, useContext, useState, useCallback, useEffect } from "react";
import {
  getAccessToken,
  login as apiLogin,
  logout as apiLogout,
  whoami,
  AuthTokensResponse,
  AuthUser as AuthPrincipalSummary,
} from "./auth";

type AuthUser = {
  displayName?: string | null;
  email?: string | null;
  id?: string | null;
  roles: string[];
};

type AuthContextValue = {
  isAuthenticated: boolean;
  isSessionReady: boolean;
  roles: string[];
  user: AuthUser | null;
  login: (email: string, password: string) => Promise<AuthTokensResponse>;
  logout: () => Promise<void>;
  setUser: React.Dispatch<React.SetStateAction<AuthUser | null>>;
  refreshAuth: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(!!getAccessToken());
  const [isSessionReady, setIsSessionReady] = useState(!getAccessToken());
  const [user, setUser] = useState<AuthUser | null>(null);

  const hydrateSession = useCallback(async () => {
    const accessToken = getAccessToken();
    if (!accessToken) {
      setIsAuthenticated(false);
      setUser(null);
      setIsSessionReady(true);
      return;
    }

    setIsSessionReady(false);
    try {
      const profile: AuthPrincipalSummary = await whoami();
      setIsAuthenticated(true);
      setUser((current) =>
        current
          ? { ...current, roles: profile.roles, id: profile.id }
          : { roles: profile.roles, id: profile.id }
      );
    } catch {
      setIsAuthenticated(false);
      setUser(null);
    } finally {
      setIsSessionReady(true);
    }
  }, []);

  const refreshAuth = useCallback(async () => {
    await hydrateSession();
  }, [hydrateSession]);

  // Sync across tabs via localStorage events
  useEffect(() => {
    const handler = () => {
      void refreshAuth();
    };
    window.addEventListener("storage", handler);
    return () => window.removeEventListener("storage", handler);
  }, [refreshAuth]);

  useEffect(() => {
    void hydrateSession();
  }, [hydrateSession]);

  const login = useCallback(async (email: string, password: string) => {
    const result = await apiLogin(email, password);
    setIsAuthenticated(true);
    setIsSessionReady(true);
    setUser({
      id: result.user.id,
      roles: result.user.roles,
      email,
      displayName: null,
    });
    return result;
  }, []);

  const logout = useCallback(async () => {
    await apiLogout().catch(() => null);
    setIsAuthenticated(false);
    setUser(null);
    setIsSessionReady(true);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        isAuthenticated,
        isSessionReady,
        roles: user?.roles ?? [],
        user,
        login,
        logout,
        setUser,
        refreshAuth,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
