import React, { useState, FormEvent } from "react";
import { useAuth } from "@/providers/Auth";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Label } from "./ui/label";

export function LoginPage() {
  const { login, register } = useAuth();
  const [isRegister, setIsRegister] = useState(false);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) return;
    setLoading(true);
    if (isRegister) {
      await register(username.trim(), password);
    } else {
      await login(username.trim(), password);
    }
    setLoading(false);
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-b from-slate-50 to-white">
      <div className="w-full max-w-sm rounded-xl border bg-white p-8 shadow-lg">
        <div className="mb-6 text-center">
          <h1 className="text-2xl font-bold tracking-tight">
            <span className="text-green-600 italic">Spring AI Alibaba</span>
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {isRegister ? "Create a new account" : "Sign in to continue"}
          </p>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="username">Username</Label>
            <Input
              id="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Enter username"
              autoFocus
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter password"
            />
          </div>
          <Button type="submit" disabled={loading || !username.trim() || !password.trim()}>
            {loading ? "Please wait..." : isRegister ? "Register" : "Login"}
          </Button>
        </form>

        <div className="mt-4 text-center">
          <button
            type="button"
            className="text-sm text-green-600 hover:underline"
            onClick={() => setIsRegister((p) => !p)}
          >
            {isRegister
              ? "Already have an account? Login"
              : "No account? Register"}
          </button>
        </div>
      </div>
    </div>
  );
}
