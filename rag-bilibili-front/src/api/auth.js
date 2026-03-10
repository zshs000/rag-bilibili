import { http } from "./http";
import { getDevUserProfile, isDeveloperModeEnabled } from "../utils/dev-mode";

export const authApi = {
  register(payload) {
    return http.post("/auth/register", payload);
  },
  login(payload) {
    return http.post("/auth/login", payload);
  },
  logout() {
    return http.post("/auth/logout");
  },
  current() {
    if (isDeveloperModeEnabled()) {
      return Promise.resolve(getDevUserProfile());
    }
    return http.get("/auth/current");
  },
};
