import React, { createContext, useContext, useState, useCallback, useEffect } from "react";
import { getAccessToken, login as apiLogin, logout as apiLogout, AuthTokensResponse } from "./auth";

type AuthUser = {
  displayName?: string | null;
  email?: string | null;
};

type AuthContextValue = {
  isAuthenticated: boolean;
  user: AuthUser | null;
  login: (email: string, password: string) => Promise<AuthTokensResponse>;
  logout: () => Promise<void>;
  setUser: (user: AuthUser | null) => void;
  refreshAuth: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(!!getAccessToken());
  const [user, setUser] = useState<AuthUser | null>(null);

  const refreshAuth = useCallback(() => {
    setIsAuthenticated(!!getAccessToken());
  }, []);

  // Sync across tabs via localStorage events
  useEffect(() => {
    const handler = () => refreshAuth();
    window.addEventListener("storage", handler);
    return () => window.removeEventListener("storage", handler);
  }, [refreshAuth]);

  const login = useCallback(async (email: string, password: string) => {
    const result = await apiLogin(email, password);
    setIsAuthenticated(true);
    setUser({ email });
    return result;
  }, []);

  const logout = useCallback(async () => {
    await apiLogout().catch(() => null);
    setIsAuthenticated(false);
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ isAuthenticated, user, login, logout, setUser, refreshAuth }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
