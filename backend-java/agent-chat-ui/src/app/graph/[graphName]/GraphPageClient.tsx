"use client";

import React from "react";
import { Toaster } from "@/components/ui/sonner";
import { GraphThreadProvider } from "@/providers/GraphThread";
import { GraphStreamProvider } from "@/providers/GraphStream";
import { GraphWorkspace } from "@/components/graph/GraphWorkspace";
import { AuthProvider, useAuth } from "@/providers/Auth";
import { LoginPage } from "@/components/LoginPage";

interface GraphPageClientProps {
  graphName: string;
}

function GraphPageInner({ graphName }: GraphPageClientProps) {
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <LoginPage />;
  }

  return (
    <GraphThreadProvider graphName={graphName}>
      <GraphStreamProvider>
        <GraphWorkspace />
      </GraphStreamProvider>
    </GraphThreadProvider>
  );
}

export function GraphPageClient({ graphName }: GraphPageClientProps) {
  if (!graphName) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p className="text-muted-foreground">Invalid graph</p>
      </div>
    );
  }

  return (
    <AuthProvider>
      <Toaster />
      <GraphPageInner graphName={graphName} />
    </AuthProvider>
  );
}
