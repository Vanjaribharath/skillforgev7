import type { Metadata, Viewport } from "next";
import "./globals.css";
import { AppShell } from "@/components/layout/app-shell";
import { Providers } from "@/components/providers";
import { PwaRegister } from "@/components/pwa-register";

export const metadata: Metadata = {
  title: "SkillForge Enterprise",
  description: "Enterprise internal assessment and certification platform.",
  manifest: "/manifest.json",
};

export const viewport: Viewport = {
  themeColor: "#1D4ED8",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>
        <Providers>
          <PwaRegister />
          <AppShell>{children}</AppShell>
        </Providers>
      </body>
    </html>
  );
}
