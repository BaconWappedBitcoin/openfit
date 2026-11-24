import type { VercelRequest, VercelResponse } from "@vercel/node";
import { allow, vote } from "../../../lib/store.js";
import { clientIp, fail, method, readBody, serverError, voterHash } from "../../../lib/http.js";
import { summary } from "../index.js";

// POST /api/programs/:id/rate  { stars: 1..5 }  -> the programme's summary
export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (!method(req, res, ["POST"])) return;
  const id = String(req.query.id ?? "");
  const body = readBody(req, res);
  if (!body) return;
  const stars = Number(body.stars);
  if (!/^[A-Za-z0-9_-]{4,16}$/.test(id)) return fail(res, 400, "bad id");
  if (!Number.isInteger(stars) || stars < 1 || stars > 5) return fail(res, 400, "stars must be 1 to 5");
  try {
    if (!(await allow("rate", clientIp(req), 60, 3600))) return fail(res, 429, "too many ratings, try again later");
    const p = await vote(id, voterHash(req), stars);
    if (!p) return fail(res, 404, "not found");
    res.status(200).json(summary(p));
  } catch (e) {
    serverError(res, e);
  }
}
