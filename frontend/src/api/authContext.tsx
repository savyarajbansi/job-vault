import React, { createContext, useContext, useState, useCallback, useEffect, useRef } from "react";
import { useLocation } from "react-router-dom";
import {
  getAccessToken,
  login as apiLogin,
  register as apiRegister,
  logout as apiLogout,
  whoami,
  AuthTokensResponse,
  AuthUser as AuthPrincipalSummary,
  RegisterRole,
} from "./auth";

type AuthUser = {
  displayName: string | null;
  email: string | null;
  id: string | null;
  roles: string[];
};

type AuthContextValue = {
  isAuthenticated: boolean;
  isSessionReady: boolean;
  roles: string[];
  user: AuthUser | null;
  login: (email: string, password: string) => Promise<AuthTokensResponse>;
  register: (
    email: string,
    password: string,
    displayName: string,
    role: RegisterRole
  ) => Promise<AuthTokensResponse>;
  logout: () => Promise<void>;
  setUser: React.Dispatch<React.SetStateAction<AuthUser | null>>;
  refreshAuth: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

function toAuthUser(profile: AuthPrincipalSummary): AuthUser {
  return {
    id: profile.id,
    roles: profile.roles,
    email: profile.email,
    displayName: profile.displayName,
  };
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(!!getAccessToken());
  const [isSessionReady, setIsSessionReady] = useState(!getAccessToken());
  const [user, setUser] = useState<AuthUser | null>(null);
  const location = useLocation();
  const lastKnownTokenRef = useRef<string | null>(getAccessToken());

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
      const profile = await whoami();
      setIsAuthenticated(true);
      setUser(toAuthUser(profile));
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

  // Re-check auth state on every same-tab navigation. The underlying access
  // token can change without going through this context's own login()/
  // logout() calls — most commonly a background silent-refresh failing
  // (auth.ts: markRefreshFailure()) — which previously left the nav bar
  // showing a stale signed-in/signed-out state until a full reload or a
  // cross-tab storage event.
  useEffect(() => {
    const currentToken = getAccessToken();
    if (currentToken === lastKnownTokenRef.current) {
      return;
    }
    lastKnownTokenRef.current = currentToken;

    if (!currentToken) {
      setIsAuthenticated(false);
      setUser(null);
      setIsSessionReady(true);
      return;
    }

    void hydrateSession();
  }, [location.pathname, hydrateSession]);

  const login = useCallback(async (email: string, password: string) => {
    const result = await apiLogin(email, password);
    lastKnownTokenRef.current = getAccessToken();
    setIsAuthenticated(true);
    setIsSessionReady(true);
    setUser(toAuthUser(result.user));
    return result;
  }, []);

  const register = useCallback(
    async (email: string, password: string, displayName: string, role: RegisterRole) => {
      const result = await apiRegister(email, password, displayName, role);
      lastKnownTokenRef.current = getAccessToken();
      setIsAuthenticated(true);
      setIsSessionReady(true);
      setUser(toAuthUser(result.user));
      return result;
    },
    []
  );

  const logout = useCallback(async () => {
    await apiLogout().catch(() => null);
    lastKnownTokenRef.current = getAccessToken();
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
        register,
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