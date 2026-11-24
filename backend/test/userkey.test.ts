import { test } from "node:test";
import assert from "node:assert/strict";
import { USER_KEY } from "../lib/http.js";

test("accepts a Google API key shape and rejects everything else", () => {
  assert.ok(USER_KEY.test("AIza" + "a".repeat(35)));
  assert.ok(USER_KEY.test("AIzaSy" + "B-_9".repeat(8) + "x"));
  assert.ok(!USER_KEY.test("AIza" + "a".repeat(34)));
  assert.ok(!USER_KEY.test("sk-" + "a".repeat(40)));
  assert.ok(!USER_KEY.test("AIza" + "a".repeat(34) + "!"));
  assert.ok(!USER_KEY.test(""));
});
