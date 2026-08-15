import { http } from "./http";
import { devServer } from "../mock/dev-server";
import { isDeveloperModeEnabled } from "../utils/dev-mode";

export const bilibiliSourcesApi = {
  favoriteFolders(credentials) {
    if (isDeveloperModeEnabled()) return devServer.listBilibiliFavoriteFolders(credentials);
    return http.post("/bilibili-sources/favorite-folders", credentials);
  },
  favoriteVideos(folderId, payload) {
    if (isDeveloperModeEnabled()) return devServer.listBilibiliFavoriteVideos(folderId, payload);
    return http.post(`/bilibili-sources/favorite-folders/${folderId}/videos`, payload);
  },
  upVideos(payload) {
    if (isDeveloperModeEnabled()) return devServer.listBilibiliUpVideos(payload);
    return http.post("/bilibili-sources/up-videos", payload);
  },
};
