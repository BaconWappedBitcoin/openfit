import type { VercelRequest, VercelResponse } from "@vercel/node";
import { getProgram, hideProgram } from "../../../lib/store.js";
import { fail, isAdmin, method, serverError } from "../../../lib/http.js";
import { summary } from "../index.js";

// GET    /api/programs/:id  -> { ...summary, note, program }
// DELETE /api/programs/:id  (owner only, x-admin-token) -> { removed: true }: takes a workout out of
// the community library at once (user request 2026-11-24). It is hidden, kept with the reason.
export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (!method(req, res, ["GET", "DELETE"])) return;
  const id = String(req.query.id ?? "");
  if (!/^[A-Za-z0-9_-]{4,16}$/.test(id)) return fail(res, 400, "bad id");
  if (req.method === "DELETE") {
    if (!isAdmin(req)) return fail(res, 403, "owner only");
    try {
      if (!(await getProgram(id, true))) return fail(res, 404, "not found");
      await hideProgram(id, "removed by the owner");
      return res.status(200).json({ removed: true });
    } catch (e) {
      return serverError(res, e);
    }
  }
  try {
    const p = await getProgram(id);
    if (!p) return fail(res, 404, "not found");
    res.status(200).json({ ...summary(p), note: p.note, program: p.program });
  } catch (e) {
    serverError(res, e);
  }
}
