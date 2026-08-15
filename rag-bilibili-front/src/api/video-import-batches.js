import { http } from "./http";
import { devServer } from "../mock/dev-server";
import { isDeveloperModeEnabled } from "../utils/dev-mode";

export const videoImportBatchesApi = {
  create(payload) {
    return isDeveloperModeEnabled()
      ? devServer.createVideoImportBatch(payload)
      : http.post("/video-import-batches", payload);
  },
  list() {
    return isDeveloperModeEnabled()
      ? devServer.listVideoImportBatches()
      : http.get("/video-import-batches");
  },
  detail(id) {
    return isDeveloperModeEnabled()
      ? devServer.getVideoImportBatch(id)
      : http.get(`/video-import-batches/${id}`);
  },
  retryFailed(id) {
    return isDeveloperModeEnabled()
      ? devServer.retryFailedVideoImports(id)
      : http.post(`/video-import-batches/${id}/retry-failed`);
  },
};
