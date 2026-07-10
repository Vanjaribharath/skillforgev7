import axios from "axios";

// ExecutionOS endpoints (tasks, schedules, notes, journal, knowledge,
// attachments, admin, search, analytics) live at /api/v1/* on the backend,
// not /api/v1/skillforge/* -- @/lib/api-client.ts defaults its baseURL to
// /api/skillforge, which is correct for the SkillForge assessment platform
// but wrong for these. Same proxy route (/api/[...path]), same JWT (the
// backend's JwtAuthenticationFilter now resolves either user table from
// one token), just a different base path.
export const executionApi = axios.create({
  baseURL: process.env.NEXT_PUBLIC_EXECUTION_API_URL ?? "/api",
  withCredentials: true,
});

executionApi.interceptors.request.use((config) => {
  if (typeof window !== "undefined") {
    const token = window.localStorage.getItem("executionos.accessToken");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

executionApi.interceptors.response.use(
  (response) => response,
  (error) => {
    if (typeof window !== "undefined" && error?.response?.status === 401) {
      window.localStorage.removeItem("executionos.accessToken");
      window.localStorage.removeItem("executionos.user");
      if (window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  },
);
