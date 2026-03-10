import { http } from "./http";
import { devServer } from "../mock/dev-server";
import { isDeveloperModeEnabled } from "../utils/dev-mode";

export const sessionsApi = {
  create(payload) {
    if (isDeveloperModeEnabled()) {
      return devServer.createSession(payload);
    }
    return http.post("/sessions", payload);
  },
  list() {
    if (isDeveloperModeEnabled()) {
      return devServer.listSessions();
    }
    return http.get("/sessions");
  },
  detail(id) {
    if (isDeveloperModeEnabled()) {
      return devServer.getSession(id);
    }
    return http.get(`/sessions/${id}`);
  },
  remove(id) {
    if (isDeveloperModeEnabled()) {
      return devServer.removeSession(id);
    }
    return http.delete(`/sessions/${id}`);
  },
};
