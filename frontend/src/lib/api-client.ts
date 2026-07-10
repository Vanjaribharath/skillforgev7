import axios from "axios";

export const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL ?? "/api/skillforge",
  withCredentials: true,
});

api.interceptors.request.use((config) => {
  if (typeof window !== "undefined") {
    const token = window.localStorage.getItem("executionos.accessToken");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

function clearSessionAndRedirect() {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem("executionos.accessToken");
  window.localStorage.removeItem("executionos.refreshToken");
  window.localStorage.removeItem("executionos.user");
  if (window.location.pathname !== "/login") {
    window.location.href = "/login";
  }
}

// Access tokens are short-lived (15 minutes) by design. Without this, every
// user would be silently kicked back to /login every 15 minutes no matter
// what they were doing. Multiple requests can 401 around the same moment
// (e.g. a page that fires several calls at once) -- inFlightRefresh makes
// sure only one actual /auth/refresh call happens and everyone else just
// waits on it, instead of racing to invalidate each other's new tokens.
let inFlightRefresh: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  if (!inFlightRefresh) {
    inFlightRefresh = (async () => {
      const refreshToken = typeof window !== "undefined" ? window.localStorage.getItem("executionos.refreshToken") : null;
      if (!refreshToken) throw new Error("No refresh token available");
      const res = await axios.post(`${api.defaults.baseURL}/auth/refresh`, { refreshToken });
      const { accessToken, refreshToken: newRefreshToken, user } = res.data;
      window.localStorage.setItem("executionos.accessToken", accessToken);
      window.localStorage.setItem("executionos.refreshToken", newRefreshToken);
      window.localStorage.setItem("executionos.user", JSON.stringify(user));
      return accessToken as string;
    })().finally(() => {
      inFlightRefresh = null;
    });
  }
  return inFlightRefresh;
}

// A 401 means the access token expired, was revoked, or never existed. This
// tries exactly one silent refresh-and-retry before giving up -- the
// `_retried` flag prevents an infinite loop if the refresh token itself is
// also invalid/expired, in which case it falls back to the original
// behavior: clear the stale session and send the person to /login.
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error?.config;
    if (typeof window !== "undefined" && error?.response?.status === 401 && originalRequest && !originalRequest._retried) {
      originalRequest._retried = true;
      try {
        const newAccessToken = await refreshAccessToken();
        originalRequest.headers = { ...originalRequest.headers, Authorization: `Bearer ${newAccessToken}` };
        return api(originalRequest);
      } catch {
        clearSessionAndRedirect();
        return Promise.reject(error);
      }
    }
    if (typeof window !== "undefined" && error?.response?.status === 401) {
      clearSessionAndRedirect();
    }
    return Promise.reject(error);
  },
);
