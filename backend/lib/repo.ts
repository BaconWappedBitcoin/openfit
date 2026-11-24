// The community data as plain JSON files in the project's own git repository (user decision
// 2026-11-24): anyone can read, fork or self-host it, and nothing has to be paid for to keep it.
//
//   community/programs.json                 every shared workout (StoredProgram[])
//   community/votes/<id>.json               { voter: stars }, voters keyed with a server-only secret
//   community/cache/<kind>/<videoId>.json   AI results (analysis readings, facts, landmarks)
//
// Reads and writes go through GitHub's contents API with a token that can write to the repository
// (GITHUB_TOKEN, GITHUB_REPO "owner/name", optional GITHUB_BRANCH and COMMUNITY_DIR). Each write is
// a commit. A write that lost a race to another one (HTTP 409/422) is reported, so the caller can
// read again and retry.

export interface JsonFile<T> {
  value: T;
  /** The file's blob sha, needed to replace it. */
  sha: string;
}

export interface JsonStore {
  read<T>(path: string): Promise<JsonFile<T> | null>;
  /** Write [value] to [path]; [sha] is the version being replaced (absent for a new file). False on a conflict. */
  write(path: string, value: unknown, message: string, sha?: string): Promise<boolean>;
}

export function repoConfigured(): boolean {
  return !!process.env.GITHUB_TOKEN && !!process.env.GITHUB_REPO;
}

/** GitHub's contents API. */
export class GitHubStore implements JsonStore {
  constructor(
    private readonly token: string,
    private readonly repo: string,
    private readonly branch = "main",
    private readonly dir = "community",
    private readonly fetcher: typeof fetch = fetch,
  ) {
    if (!/^[\w.-]+\/[\w.-]+$/.test(repo)) throw new Error("GITHUB_REPO must be owner/name");
  }

  static fromEnv(): GitHubStore {
    return new GitHubStore(
      process.env.GITHUB_TOKEN!,
      process.env.GITHUB_REPO!,
      process.env.GITHUB_BRANCH || "main",
      process.env.COMMUNITY_DIR || "community",
    );
  }

  private url(path: string) {
    const clean = `${this.dir}/${path}`.split("/").map(encodeURIComponent).join("/");
    return `https://api.github.com/repos/${this.repo}/contents/${clean}`;
  }

  private headers(extra: Record<string, string> = {}) {
    return {
      Authorization: `Bearer ${this.token}`,
      "X-GitHub-Api-Version": "2022-11-28",
      "User-Agent": "openfit-backend",
      ...extra,
    };
  }

  async read<T>(path: string): Promise<JsonFile<T> | null> {
    const res = await this.fetcher(`${this.url(path)}?ref=${encodeURIComponent(this.branch)}`, {
      headers: this.headers({ Accept: "application/vnd.github.object+json" }),
    });
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`GitHub read ${path}: HTTP ${res.status}`);
    const body = (await res.json()) as { sha: string; content?: string; encoding?: string; download_url?: string };
    let text: string;
    if (body.content && body.encoding === "base64") {
      text = Buffer.from(body.content, "base64").toString("utf8");
    } else {
      // Files over 1 MB come without content: fetch the raw bytes.
      const raw = await this.fetcher(`${this.url(path)}?ref=${encodeURIComponent(this.branch)}`, {
        headers: this.headers({ Accept: "application/vnd.github.raw+json" }),
      });
      if (!raw.ok) throw new Error(`GitHub read ${path}: HTTP ${raw.status}`);
      text = await raw.text();
    }
    return { value: JSON.parse(text) as T, sha: body.sha };
  }

  async write(path: string, value: unknown, message: string, sha?: string): Promise<boolean> {
    const res = await this.fetcher(this.url(path), {
      method: "PUT",
      headers: this.headers({ Accept: "application/vnd.github+json", "Content-Type": "application/json" }),
      body: JSON.stringify({
        message,
        branch: this.branch,
        content: Buffer.from(JSON.stringify(value, null, 1) + "\n", "utf8").toString("base64"),
        ...(sha ? { sha } : {}),
      }),
    });
    if (res.status === 409 || res.status === 422) return false;
    if (!res.ok) throw new Error(`GitHub write ${path}: HTTP ${res.status}`);
    return true;
  }
}

/** In memory, for tests (and a run with no storage configured). */
export class MemoryStore implements JsonStore {
  private files = new Map<string, { text: string; sha: string }>();
  private n = 0;
  writes = 0;

  async read<T>(path: string): Promise<JsonFile<T> | null> {
    const f = this.files.get(path);
    return f ? { value: JSON.parse(f.text) as T, sha: f.sha } : null;
  }

  async write(path: string, value: unknown, _message: string, sha?: string): Promise<boolean> {
    const current = this.files.get(path);
    if ((current?.sha ?? undefined) !== sha) return false;
    this.files.set(path, { text: JSON.stringify(value), sha: `s${++this.n}` });
    this.writes++;
    return true;
  }
}

/**
 * Read, change and write one file, again from a fresh read if another write got there first.
 * [change] returns the new value, or undefined to leave the file as it is.
 */
export async function update<T>(
  store: JsonStore,
  path: string,
  empty: T,
  message: string,
  change: (current: T) => T | undefined,
  tries = 5,
): Promise<T | undefined> {
  for (let i = 0; i < tries; i++) {
    const file = await store.read<T>(path);
    const next = change(file ? file.value : structuredClone(empty));
    if (next === undefined) return undefined;
    if (await store.write(path, next, message, file?.sha)) return next;
  }
  throw new Error(`could not update ${path}: too many concurrent writes`);
}
