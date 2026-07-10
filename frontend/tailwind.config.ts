import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#111827",
        muted: "#64748B",
        line: "#CBD5E1",
        blue: "#1D4ED8",
        green: "#0F766E",
        amber: "#F59E0B",
        coral: "#DC2626",
        surface: "#f8fafd",
      },
      boxShadow: {
        soft: "0 1px 2px rgba(15, 23, 42, 0.08), 0 8px 20px rgba(15, 23, 42, 0.04)",
      },
    },
  },
  plugins: [],
};

export default config;
