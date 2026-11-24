// Automatic moderation for the community library (user decision 2026-11-24: no manual approval).
//
// A shared workout is published at once only if every check passes:
//   1. Its name (and note) pass a word filter and carry no links.
//   2. Its YouTube video, if any, is public, processed, embeddable and not age-restricted, and its
//      title and description pass the same word filter.
//   3. With the sharer's own Gemini key (never the server's), an AI look at the title, description
//      and thumbnail says it suits a general fitness audience. Without a key this step is skipped.
// After publishing, anyone can report a workout. A report re-runs check 2 and hides the workout at
// once if the video has since gone bad; otherwise it is hidden automatically after
// REPORTS_TO_HIDE different people report it. Nobody has to approve or review anything.

export const REPORTS_TO_HIDE = 3;
export const REPORT_REASONS = ["adult", "violent", "hateful", "illegal", "spam", "broken", "other"] as const;
export type ReportReason = (typeof REPORT_REASONS)[number];

/**
 * Words that have no place in a workout's name or a running video's title. Matched as whole words
 * after folding case, accents and common letter swaps (0→o, 1→i, 3→e, 4→a, 5→s, @→a, $→s), so
 * "Essex" or "Sussex" do not match "sex". Extra words can be added with MODERATION_WORDS
 * (comma-separated) without a code change.
 */
const WORDS = [
  "porn", "porno", "pornography", "xxx", "nsfw", "nude", "nudes", "naked", "sex", "sexy", "sexual", "hentai", "onlyfans",
  "fetish", "erotic", "erotica", "camgirl", "escort", "escorts", "stripper", "striptease", "milf", "boobs", "tits", "dick",
  "cock", "pussy", "cum", "orgasm", "masturbate", "masturbation", "blowjob", "anal", "bdsm", "nipple", "nipples", "upskirt",
  "cocaine", "heroin", "meth", "methamphetamine", "fentanyl", "gun sale", "buy guns", "beheading", "gore", "snuff",
  "kill yourself", "kys", "suicide method", "nazi", "isis", "terrorist", "child abuse", "cp", "loli", "shota",
];

function fold(s: string): string {
  return s
    .normalize("NFKD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[0]/g, "o").replace(/[1!|]/g, "i").replace(/3/g, "e").replace(/[4@]/g, "a").replace(/[5$]/g, "s").replace(/7/g, "t")
    .replace(/[^a-z\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function wordList(): string[] {
  const extra = (process.env.MODERATION_WORDS ?? "").split(",").map((w) => w.trim()).filter(Boolean);
  return [...WORDS, ...extra].map(fold).filter(Boolean);
}

const LINK = /(https?:\/\/|www\.|\b[a-z0-9-]+\.(com|net|org|io|xyz|ru|cn|tk|ly|gg|me|co|tv)\b)/i;

/** Why a piece of text cannot be published, or null if it is fine. */
export function textProblem(text: string, allowLinks = false): string | null {
  if (!text) return null;
  if (!allowLinks && LINK.test(text)) return "links are not allowed in shared workouts";
  const folded = ` ${fold(text)} `;
  for (const w of wordList()) {
    if (folded.includes(` ${w} `)) return "the text contains words that are not allowed";
  }
  return null;
}

/** What moderation needs to know about a YouTube video. */
export interface VideoSafety {
  title: string;
  description: string;
  channel: string;
  thumbnail: string;
  embeddable: boolean;
  privacyStatus: string;
  uploadStatus: string;
  ageRestricted: boolean;
  madeForKids: boolean;
}

/** Why a video cannot be shared, or null if it may be. */
export function videoProblem(v: VideoSafety | null): string | null {
  if (!v) return "that video is not on YouTube any more";
  if (v.ageRestricted) return "that video is age-restricted on YouTube";
  if (v.privacyStatus !== "public") return "that video is not public";
  if (v.uploadStatus && v.uploadStatus !== "processed") return "that video is not available";
  if (!v.embeddable) return "that video cannot be played inside other apps";
  // Video descriptions are full of links, so only the words are checked there.
  if (textProblem(v.title, true) || textProblem(v.description.slice(0, 2000), true)) return "that video's title or description is not suitable";
  return null;
}

/** The YouTube fields moderation needs (one quota unit). */
export async function videoSafety(videoId: string, key: string, fetcher: typeof fetch = fetch): Promise<VideoSafety | null> {
  const url = new URL("https://www.googleapis.com/youtube/v3/videos");
  url.searchParams.set("part", "snippet,contentDetails,status");
  url.searchParams.set("id", videoId);
  const res = await fetcher(url, { headers: { "x-goog-api-key": key } });
  if (!res.ok) throw new Error(`YouTube returned HTTP ${res.status}`);
  const v = ((await res.json()) as any).items?.[0];
  if (!v) return null;
  return {
    title: String(v.snippet?.title ?? ""),
    description: String(v.snippet?.description ?? ""),
    channel: String(v.snippet?.channelTitle ?? ""),
    thumbnail: String(v.snippet?.thumbnails?.medium?.url ?? v.snippet?.thumbnails?.default?.url ?? ""),
    embeddable: v.status?.embeddable !== false,
    privacyStatus: String(v.status?.privacyStatus ?? "public"),
    uploadStatus: String(v.status?.uploadStatus ?? "processed"),
    ageRestricted: v.contentDetails?.contentRating?.ytRating === "ytAgeRestricted",
    madeForKids: v.status?.madeForKids === true,
  };
}

const GEMINI = "https://generativelanguage.googleapis.com/v1beta/models";

/**
 * The AI look (check 3), on the sharer's own key. Returns a reason to refuse, or null. A failure of
 * the AI service never blocks a share: checks 1 and 2 still apply, and reports catch the rest.
 */
export async function aiProblem(v: VideoSafety, userKey: string, model: string, fetcher: typeof fetch = fetch): Promise<string | null> {
  try {
    const parts: any[] = [
      {
        text: `You moderate a community library of treadmill workouts that play YouTube videos, for a general audience including teenagers.
Decide whether this video is suitable: scenic runs, walks, hikes, city tours, nature, fitness and similar are suitable.
Unsuitable: sexual or suggestive content, nudity, graphic violence, hate, drugs, weapons sales, scams, self-harm, or anything illegal.
Title: ${JSON.stringify(v.title)}
Channel: ${JSON.stringify(v.channel)}
Description: ${JSON.stringify(v.description.slice(0, 1500))}
Answer with suitable true or false and a short reason.`,
      },
    ];
    if (v.thumbnail) {
      const img = await fetcher(v.thumbnail);
      if (img.ok) {
        const data = Buffer.from(await img.arrayBuffer()).toString("base64");
        parts.push({ inlineData: { mimeType: img.headers.get("content-type") ?? "image/jpeg", data } });
      }
    }
    const res = await fetcher(`${GEMINI}/${model}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": userKey },
      body: JSON.stringify({
        contents: [{ parts }],
        generationConfig: {
          temperature: 0,
          responseMimeType: "application/json",
          responseSchema: {
            type: "OBJECT",
            properties: { suitable: { type: "BOOLEAN" }, reason: { type: "STRING" } },
            required: ["suitable", "reason"],
          },
        },
      }),
    });
    if (!res.ok) return null;
    const json = (await res.json()) as any;
    // Gemini's own safety filters refusing the prompt is itself a verdict.
    if (json.promptFeedback?.blockReason) return "that video looks unsuitable for the library";
    const answer = JSON.parse(json.candidates?.[0]?.content?.parts?.[0]?.text ?? "{}");
    return answer.suitable === false ? "that video looks unsuitable for the library" : null;
  } catch {
    return null;
  }
}
