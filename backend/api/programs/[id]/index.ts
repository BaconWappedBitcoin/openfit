import type { VercelRequest, VercelResponse } from "@vercel/node";
import { getProgram } from "../../../lib/store.js";
import { fail, method, serverError } from "../../../lib/http.js";
import { summary } from "../index.js";

// GET /api/programs/:id  -> { ...summary, note, program }
export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (!method(req, res, ["GET"])) return;
  const id = String(req.query.id ?? "");
  if (!/^[A-Za-z0-9_-]{4,16}$/.test(id)) return fail(res, 400, "bad id");
  try {
    const p = await getProgram(id);
    if (!p) return fail(res, 404, "not found");
    res.status(200).json({ ...summary(p), note: p.note, program: p.program });
  } catch (e) {
    serverError(res, e);
  }
}
