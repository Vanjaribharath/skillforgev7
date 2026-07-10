import { create } from "zustand";

export type SkillForgeUser = {
  id: string;
  organizationId: string;
  email: string;
  fullName: string;
  role: "PLATFORM_ADMIN" | "ORG_ADMIN" | "TRAINER" | "EVALUATOR" | "CANDIDATE";
  status: string;
};

type AuthState = {
  user: SkillForgeUser | null;
  accessToken: string | null;
  refreshToken: string | null;
  hydrated: boolean;
  setSession: (user: SkillForgeUser, accessToken: string, refreshToken?: string) => void;
  logout: () => void;
  hydrate: () => void;
};

const TOKEN_KEY = "executionos.accessToken";
const REFRESH_KEY = "executionos.refreshToken";
const USER_KEY = "executionos.user";

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  accessToken: null,
  refreshToken: null,
  hydrated: false,
  setSession: (user, accessToken, refreshToken) => {
    if (typeof window !== "undefined") {
      window.localStorage.setItem(TOKEN_KEY, accessToken);
      window.localStorage.setItem(USER_KEY, JSON.stringify(user));
      if (refreshToken) window.localStorage.setItem(REFRESH_KEY, refreshToken);
    }
    set({ user, accessToken, refreshToken: refreshToken ?? null, hydrated: true });
  },
  logout: () => {
    if (typeof window !== "undefined") {
      window.localStorage.removeItem(TOKEN_KEY);
      window.localStorage.removeItem(REFRESH_KEY);
      window.localStorage.removeItem(USER_KEY);
    }
    set({ user: null, accessToken: null, refreshToken: null, hydrated: true });
  },
  // Zustand state doesn't survive a page reload on its own; the token/user
  // were previously only readable via localStorage directly (api-client.ts),
  // meaning `user` would reset to null on every refresh even with a still
  // valid token. Call this once on app mount to restore both.
  hydrate: () => {
    if (typeof window === "undefined") {
      set({ hydrated: true });
      return;
    }
    const token = window.localStorage.getItem(TOKEN_KEY);
    const refreshToken = window.localStorage.getItem(REFRESH_KEY);
    const rawUser = window.localStorage.getItem(USER_KEY);
    let user: SkillForgeUser | null = null;
    if (rawUser) {
      try {
        user = JSON.parse(rawUser) as SkillForgeUser;
      } catch {
        window.localStorage.removeItem(USER_KEY);
      }
    }
    set({ user, accessToken: token, refreshToken, hydrated: true });
  },
}));
