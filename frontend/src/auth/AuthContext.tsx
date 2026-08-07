import { createContext, useContext, useEffect, useMemo, useState, type PropsWithChildren } from "react";
import { api, ApiError, demoFallbackEnabled, tokenStore } from "../lib/api";
import { demoAdmin, demoUser } from "../data/demo";
import type { AuthResponse, UserResponse } from "../types";

interface AuthContextValue {
  auth: AuthResponse | null;
  user: UserResponse | null;
  loading: boolean;
  demo: boolean;
  login: (email: string, password: string) => Promise<UserResponse>;
  register: (body: { name: string; email: string; password: string; phone?: string }) => Promise<UserResponse>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function userFromAuth(auth: AuthResponse): UserResponse {
  return {
    ...(auth.role === "ADMIN" ? demoAdmin : demoUser),
    id: auth.userId,
    name: auth.name,
    email: auth.email,
    role: auth.role,
    capabilities: auth.capabilities,
  };
}

function demoAuth(email: string, name?: string): AuthResponse {
  const isAdmin = email.toLowerCase() === "admin@marketplace.com";
  return {
    userId: isAdmin ? 1 : 2,
    email: email.toLowerCase(),
    name: name || (isAdmin ? "System Admin" : "John Carter"),
    role: isAdmin ? "ADMIN" : "USER",
    capabilities: ["CLIENT", "WORKER"],
    accessToken: "demo-access-token",
    refreshToken: "demo-refresh-token",
    tokenType: "Bearer",
    expiresIn: 7200,
  };
}

export function AuthProvider({ children }: PropsWithChildren) {
  const [auth, setAuth] = useState<AuthResponse | null>(() => tokenStore.auth());
  const [user, setUser] = useState<UserResponse | null>(null);
  const [loading, setLoading] = useState(Boolean(auth));
  const [demo, setDemo] = useState(auth?.accessToken === "demo-access-token");

  const refreshUser = async () => {
    if (!tokenStore.access()) {
      setUser(null);
      return;
    }
    try {
      setUser(await api.auth.me());
      setDemo(false);
    } catch (caught) {
      const stored = tokenStore.auth();
      if (stored && demoFallbackEnabled && (caught instanceof ApiError ? caught.status === 0 || stored.accessToken === "demo-access-token" : true)) {
        setUser(userFromAuth(stored));
        setDemo(true);
        return;
      }
      tokenStore.clear();
      setAuth(null);
      setUser(null);
    }
  };

  useEffect(() => {
    if (!auth) {
      setLoading(false);
      return;
    }
    void refreshUser().finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const expire = () => {
      setAuth(null);
      setUser(null);
      setDemo(false);
    };
    window.addEventListener("marketplace:session-expired", expire);
    return () => window.removeEventListener("marketplace:session-expired", expire);
  }, []);

  const login = async (email: string, password: string) => {
    let response: AuthResponse;
    try {
      response = await api.auth.login({ email: email.trim().toLowerCase(), password });
      setDemo(false);
    } catch (caught) {
      const networkOnly = caught instanceof ApiError && caught.status === 0;
      if (!demoFallbackEnabled || !networkOnly || password !== "Demo1234!") throw caught;
      response = demoAuth(email);
      setDemo(true);
    }
    tokenStore.save(response);
    setAuth(response);
    const nextUser = userFromAuth(response);
    setUser(nextUser);
    return nextUser;
  };

  const register = async (body: { name: string; email: string; password: string; phone?: string }) => {
    let response: AuthResponse;
    try {
      response = await api.auth.register({ ...body, email: body.email.trim().toLowerCase() });
      setDemo(false);
    } catch (caught) {
      const networkOnly = caught instanceof ApiError && caught.status === 0;
      if (!demoFallbackEnabled || !networkOnly) throw caught;
      response = demoAuth(body.email, body.name);
      setDemo(true);
    }
    tokenStore.save(response);
    setAuth(response);
    const nextUser = userFromAuth(response);
    setUser(nextUser);
    return nextUser;
  };

  const logout = async () => {
    try {
      if (!demo) await api.auth.logout();
    } finally {
      tokenStore.clear();
      setAuth(null);
      setUser(null);
      setDemo(false);
    }
  };

  const value = useMemo(
    () => ({ auth, user, loading, demo, login, register, logout, refreshUser }),
    [auth, user, loading, demo],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside AuthProvider");
  return value;
}
