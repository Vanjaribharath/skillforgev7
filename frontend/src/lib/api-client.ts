import axios from "axios";

// Deliberately sessionStorage, not localStorage. localStorage is shared by
// every tab open on the same origin, so testing Admin in one tab and Trainer
// in another used to mean whichever logged in most recently silently won
// for *all* tabs -- the earlier tab kept showing its own role in the UI but
// every request it made actually carried the other tab's token underneath,
// producing role-mismatched 403s that looked like a permissions bug. Each
// tab gets its own sessionStorage, so each tab now stays logged in as
// whichever account it actually signed into.
export const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL ?? "/api/skillforge",
  withCredentials: true,
});

api.interceptors.request.use((config) => {
  if (typeof window !== "undefined") {
    const token = window.sessionStorage.getItem("executionos.accessToken");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

function clearSessionAndRedirect() {
  if (typeof window === "undefined") return;
  window.sessionStorage.removeItem("executionos.accessToken");
  window.sessionStorage.removeItem("executionos.refreshToken");
  window.sessionStorage.removeItem("executionos.user");
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
      const refreshToken = typeof window !== "undefined" ? window.sessionStorage.getItem("executionos.refreshToken") : null;
      if (!refreshToken) throw new Error("No refresh token available");
      const res = await axios.post(`${api.defaults.baseURL}/auth/refresh`, { refreshToken });
      const { accessToken, refreshToken: newRefreshToken, user } = res.data;
      window.sessionStorage.setItem("executionos.accessToken", accessToken);
      window.sessionStorage.setItem("executionos.refreshToken", newRefreshToken);
      window.sessionStorage.setItem("executionos.user", JSON.stringify(user));
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
