import React, { createContext, useContext, useState, useCallback, ReactNode, useEffect } from "react";
import { toast } from "sonner";

interface AuthContextType {
  isAuthenticated: boolean;
  userId: string | null;
  login: (username: string, password: string) => Promise<boolean>;
  register: (username: string, password: string) => Promise<boolean>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [userId, setUserId] = useState<string | null>(null);

  useEffect(() => {
    const stored = localStorage.getItem("auth_userId");
    if (stored) {
      setUserId(stored);
    }
  }, []);

  const login = useCallback(async (username: string, password: string): Promise<boolean> => {
    try {
      const res = await fetch(`${API_BASE}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });
      const data = await res.json();
      if (data.success) {
        setUserId(data.userId);
        localStorage.setItem("auth_userId", data.userId);
        return true;
      }
      toast.error(data.message || "Login failed");
      return false;
    } catch (err: any) {
      toast.error("Login request failed: " + (err.message || "Unknown error"));
      return false;
    }
  }, []);

  const register = useCallback(async (username: string, password: string): Promise<boolean> => {
    try {
      const res = await fetch(`${API_BASE}/api/auth/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });
      const data = await res.json();
      if (data.success) {
        setUserId(data.userId);
        localStorage.setItem("auth_userId", data.userId);
        return true;
      }
      toast.error(data.message || "Registration failed");
      return false;
    } catch (err: any) {
      toast.error("Registration request failed: " + (err.message || "Unknown error"));
      return false;
    }
  }, []);

  const logout = useCallback(() => {
    setUserId(null);
    localStorage.removeItem("auth_userId");
  }, []);

  return (
    <AuthContext.Provider
      value={{
        isAuthenticated: !!userId,
        userId,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
