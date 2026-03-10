import { http } from "./http";
import { devServer } from "../mock/dev-server";
import { isDeveloperModeEnabled } from "../utils/dev-mode";

export const videosApi = {
  importVideo(payload) {
    if (isDeveloperModeEnabled()) {
      return devServer.importVideo(payload);
    }
    return http.post("/videos", payload);
  },
  list() {
    if (isDeveloperModeEnabled()) {
      return devServer.listVideos();
    }
    return http.get("/videos");
  },
  detail(id) {
    if (isDeveloperModeEnabled()) {
      return devServer.getVideo(id);
    }
    return http.get(`/videos/${id}`);
  },
  remove(id) {
    if (isDeveloperModeEnabled()) {
      return devServer.removeVideo(id);
    }
    return http.delete(`/videos/${id}`);
  },
};
