import test from "node:test";
import assert from "node:assert/strict";
import { moderate } from "../src/moderation.js";

test("kind reflections pass", () => {
  for (const t of [
    "This verse reminds me that I am never alone, even on hard days.",
    "Like a bridegroom coming out of his room, the sun rises.",
    "I'm closer to peace than I was last year.",
    "God will not hurt you; he holds you.",
    "Psalm 23 got me through 2019.",
  ]) assert.equal(moderate(t).allowed, true, t);
});

test("abuse is blocked", () => {
  for (const t of ["you are an idiot", "f u c k this", "FUUUUCK", "sh1t", "kill yourself", "buy now at cheap.com",
    "call me +1 555 123 4567", "mail me a@b.co", "send pics", "", "x".repeat(501), "THIS IS ALL SHOUTING TEXT"]) {
    assert.equal(moderate(t).allowed, false, t);
  }
});
