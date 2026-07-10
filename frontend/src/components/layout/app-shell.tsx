"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  BarChart3,
  BookOpen,
  CircleUserRound,
  ClipboardCheck,
  FileText,
  Home,
  Landmark,
  LogOut,
  MonitorDot,
  PanelRight,
  Search,
  Settings,
  Shield,
  UserRoundCheck,
  UserRoundCog,
} from "lucide-react";
import { clsx } from "clsx";
import { useAuthStore } from "@/store/use-auth-store";
import { api } from "@/lib/api-client";

export const nav = [
  { href: "/", label: "Command", icon: Home, roles: ["PLATFORM_ADMIN", "ORG_ADMIN", "TRAINER", "EVALUATOR"] },
  { href: "/question-bank", label: "Questions", icon: BookOpen, roles: ["PLATFORM_ADMIN", "ORG_ADMIN", "TRAINER", "EVALUATOR"] },
  { href: "/assessments", label: "Assessments", icon: ClipboardCheck, roles: ["PLATFORM_ADMIN", "ORG_ADMIN", "TRAINER"] },
  { href: "/candidates", label: "Candidates", icon: UserRoundCheck, roles: ["PLATFORM_ADMIN", "ORG_ADMIN", "TRAINER"] },
  { href: "/trainers", label: "Trainers", icon: UserRoundCog, roles: ["PLATFORM_ADMIN", "ORG_ADMIN"] },
  { href: "/live", label: "Live", icon: MonitorDot, roles: ["PLATFORM_ADMIN", "ORG_ADMIN", "TRAINER"] },
  { href: "/reports", label: "Reports", icon: BarChart3, roles: ["PLATFORM_ADMIN", "ORG_ADMIN", "TRAINER"] },
  { href: "/candidate", label: "Test Player", icon: FileText, roles: ["PLATFORM_ADMIN", "ORG_ADMIN", "TRAINER", "EVALUATOR", "CANDIDATE"] },
  { href: "/search", label: "Search", icon: Search, roles: ["PLATFORM_ADMIN", "ORG_ADMIN", "TRAINER"] },
  { href: "/settings", label: "Settings", icon: Settings, roles: ["PLATFORM_ADMIN", "ORG_ADMIN"] },
  { href: "/admin", label: "Admin", icon: Shield, roles: ["PLATFORM_ADMIN", "ORG_ADMIN"] },
] as const;

// Every route's minimum allowed role, mirroring `nav` above but also
// covering routes that aren't in the sidebar at all (there are none right
// now, but this is where they'd go). A CANDIDATE hitting /admin, /reports,
// etc. by guessing the URL used to see the exact same staff dashboard as an
// ORG_ADMIN -- there was no role check anywhere in the frontend. This is
// the fix for "Admin page is same, Trainer page is same, Candidate page is
// also same."
export function allowedRolesFor(pathname: string): readonly string[] | null {
  const match = nav.find((item) => pathname === item.href || pathname.startsWith(`${item.href}/`));
  return match ? match.roles : null; // null = no specific restriction (e.g. /login)
}

// Where to send someone who isn't allowed on the page they landed on.
// Candidates land in the Test Player (the only thing they're meant to use);
// everyone else falls back to Command.
export function landingPageFor(role: string | undefined): string {
  return role === "CANDIDATE" ? "/candidate" : "/";
}

// Routes reachable without an authenticated session.
const PUBLIC_ROUTES = ["/login", "/candidate", "/verify-email"];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { user, accessToken, hydrated, hydrate } = useAuthStore();

  useEffect(() => {
    hydrate();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const isPublicRoute = PUBLIC_ROUTES.some((route) => pathname === route || pathname.startsWith(`${route}/`));

  useEffect(() => {
    if (hydrated && !accessToken && !isPublicRoute) {
      router.replace("/login");
    }
  }, [hydrated, accessToken, isPublicRoute, pathname, router]);

  // Role gate: once we know who's signed in, bounce them off pages their
  // role doesn't cover. /candidate is intentionally exempt -- it's the
  // public candidate portal (token-gated, not role-gated) so staff can also
  // preview it.
  useEffect(() => {
    if (!hydrated || !user || pathname === "/candidate") return;
    const allowedRoles = allowedRolesFor(pathname);
    if (allowedRoles && !allowedRoles.includes(user.role)) {
      router.replace(landingPageFor(user.role));
    }
  }, [hydrated, user, pathname, router]);

  const visibleNav = nav.filter((item) => !user || (item.roles as readonly string[]).includes(user.role));

  // Login and email-verification pages render full-screen, without the app chrome.
  if (pathname === "/login" || pathname === "/verify-email") {
    return <>{children}</>;
  }

  // Avoid a flash of protected content before hydration/redirect resolves.
  if (!hydrated || (!accessToken && !isPublicRoute)) {
    return (
      <div className="grid min-h-screen place-items-center bg-white text-sm text-muted">
        Loading…
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-white text-ink">
      <aside className="fixed inset-y-0 left-0 z-20 hidden w-64 border-r border-line bg-white px-4 py-5 lg:block">
        <Link href="/" className="mb-8 flex items-center gap-3 rounded-lg px-2">
          <span className="grid h-9 w-9 place-items-center rounded-md bg-blue text-white"><Landmark size={20} /></span>
          <div>
            <div className="text-lg font-semibold tracking-normal">SkillForge</div>
            <div className="text-xs text-muted">Assess. Certify. Improve.</div>
          </div>
        </Link>
        <nav className="space-y-1">
          {visibleNav.map((item) => (
            <NavItem key={item.href} item={item} active={pathname === item.href || pathname.startsWith(`${item.href}/`)} />
          ))}
        </nav>
      </aside>
      <header className="sticky top-0 z-10 border-b border-line bg-white/95 px-4 py-3 backdrop-blur lg:ml-64">
        <div className="mx-auto flex max-w-6xl items-center justify-between">
          <Link href="/" className="flex items-center gap-2 lg:hidden">
            <span className="grid h-8 w-8 place-items-center rounded-md bg-blue text-white"><Landmark size={18} /></span>
            <span className="font-semibold">SkillForge</span>
          </Link>
          <div className="hidden items-center gap-2 text-sm text-muted lg:flex">
            <PanelRight size={17} />
            Internal certification workspace
          </div>
          <ProfileMenu />
        </div>
      </header>
      <main className="pb-24 lg:ml-64 lg:pb-10">
        <div className="mx-auto max-w-6xl px-4 py-5 sm:px-6 lg:px-8">{children}</div>
      </main>
      <nav className="fixed inset-x-0 bottom-0 z-30 grid grid-cols-5 border-t border-line bg-white px-2 py-2 lg:hidden">
        {visibleNav.slice(0, 5).map((item) => (
          <MobileNavItem key={item.href} item={item} active={pathname === item.href || pathname.startsWith(`${item.href}/`)} />
        ))}
      </nav>
    </div>
  );
}

function ProfileMenu() {
  const [open, setOpen] = useState(false);
  const router = useRouter();
  const { user, logout } = useAuthStore();

  return (
    <div className="relative">
      <button
        className="touch-target rounded-md border border-line p-2 text-muted"
        aria-label="Open profile"
        onClick={() => setOpen((v) => !v)}
      >
        <CircleUserRound size={22} />
      </button>
      {open && (
        <div className="absolute right-0 z-40 mt-2 w-56 rounded-md border border-line bg-white p-2 shadow-lg">
          {user ? (
            <>
              <div className="px-2 py-1.5">
                <div className="truncate text-sm font-medium text-ink">{user.fullName}</div>
                <div className="truncate text-xs text-muted">{user.email}</div>
                <div className="mt-1 inline-block rounded bg-[#e8f0fe] px-1.5 py-0.5 text-[11px] font-medium text-blue">
                  {user.role.replace("_", " ")}
                </div>
              </div>
              <button
                className="mt-1 flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm text-red-600 hover:bg-surface"
                onClick={() => {
                  const refreshToken = typeof window !== "undefined" ? window.localStorage.getItem("executionos.refreshToken") : null;
                  if (refreshToken) {
                    api.post("/auth/logout", { refreshToken }).catch(() => {
                      // Best-effort: even if this fails (e.g. already offline),
                      // we still clear the local session below so the person
                      // is signed out of this device regardless.
                    });
                  }
                  logout();
                  setOpen(false);
                  router.replace("/login");
                }}
              >
                <LogOut size={16} /> Sign out
              </button>
            </>
          ) : (
            <div className="px-2 py-1.5 text-sm text-muted">Not signed in</div>
          )}
        </div>
      )}
    </div>
  );
}

function NavItem({ item, active }: { item: (typeof nav)[number]; active: boolean }) {
  const Icon = item.icon;
  return (
    <Link
      href={item.href}
      className={clsx(
        "flex h-11 items-center gap-3 rounded-md px-3 text-sm font-medium text-muted transition",
        active && "bg-[#e8f0fe] text-blue",
        !active && "hover:bg-surface hover:text-ink",
      )}
    >
      <Icon size={19} />
      {item.label}
    </Link>
  );
}

function MobileNavItem({ item, active }: { item: (typeof nav)[number]; active: boolean }) {
  const Icon = item.icon;
  return (
    <Link href={item.href} className={clsx("flex flex-col items-center gap-1 rounded-md py-1 text-[11px] text-muted", active && "text-blue")}>
      <Icon size={20} />
      <span className="max-w-full truncate">{item.label}</span>
    </Link>
  );
}
