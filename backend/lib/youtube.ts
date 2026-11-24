// YouTube Data API v3: search and video length. Only metadata; nothing is downloaded.

const API = "https://www.googleapis.com/youtube/v3";

export const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/;

export interface VideoInfo {
  id: string;
  title: string;
  channel: string;
  thumbnail: string;
  durationSeconds: number;
  embeddable: boolean;
}

/** ISO 8601 duration such as PT1H2M3S to seconds. */
export function parseIsoDuration(iso: string): number {
  const m = /^P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$/.exec(iso);
  if (!m) return 0;
  const [d, h, mi, s] = [m[1], m[2], m[3], m[4]].map((x) => Number(x ?? 0));
  return d * 86400 + h * 3600 + mi * 60 + s;
}

async function get(path: string, params: Record<string, string>, key: string): Promise<any> {
  const url = new URL(`${API}/${path}`);
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v);
  const res = await fetch(url, { headers: { "x-goog-api-key": key } });
  if (!res.ok) throw new Error(`YouTube returned HTTP ${res.status}`);
  return res.json();
}

export async function videoDetails(ids: string[], key: string): Promise<VideoInfo[]> {
  if (ids.length === 0) return [];
  const data = await get("videos", { part: "snippet,contentDetails,status", id: ids.join(","), maxResults: "50" }, key);
  return (data.items ?? []).map((v: any): VideoInfo => ({
    id: v.id,
    title: v.snippet.title,
    channel: v.snippet.channelTitle,
    thumbnail: v.snippet.thumbnails?.medium?.url ?? v.snippet.thumbnails?.default?.url ?? "",
    durationSeconds: parseIsoDuration(v.contentDetails.duration),
    embeddable: v.status?.embeddable !== false,
  }));
}

/** Search for running videos. Only embeddable videos are returned: the app plays them in YouTube's own player. */
export async function search(q: string, key: string): Promise<VideoInfo[]> {
  const found = await get(
    "search",
    { part: "snippet", type: "video", videoEmbeddable: "true", maxResults: "20", q, safeSearch: "strict" },
    key,
  );
  const ids = (found.items ?? []).map((i: any) => i.id.videoId).filter((id: string) => VIDEO_ID.test(id));
  const details = await videoDetails(ids, key);
  return details.filter((v) => v.embeddable && v.durationSeconds > 0);
}
