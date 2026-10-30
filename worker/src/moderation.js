// Server-side moderation for Psalmwell reflections. The app runs a similar check first; this one
// is the authority. Anything that gets through can still be reported and removed.
export const MAX_LENGTH = 500;

const LINK = /(https?:\/\/|www\.|\b[a-z0-9-]+\.(com|net|org|io|ly|me|xyz|info|app|link|click|ru|tk|gg|co)\b)/i;
const CONTACT = /([A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,})|(\+?\d[\d\s().-]{8,}\d)/i;
const COMPACT = ["fuck", "motherfucker", "nigger", "faggot", "childporn"];

const TERMS = {
  "strong profanity": ["fuck", "fucking", "fucker", "motherfucker", "shit", "bullshit", "bitch", "cunt", "asshole", "bastard", "dickhead", "piss off", "wtf", "stfu"],
  "sexual content": ["porn", "porno", "nude", "nudes", "naked pics", "sex chat", "sexting", "onlyfans", "horny", "blowjob", "dick pic", "send pics", "hookup", "nsfw", "xxx"],
  "harassment": ["idiot", "moron", "retard", "loser", "worthless", "kill yourself", "kys", "shut up", "nobody likes you", "you are stupid", "you're stupid", "go die", "ugly", "pathetic"],
  "threats": ["i will kill", "i'll kill you", "im going to kill you", "shoot you", "stab you", "beat you up", "find where you live", "watch your back"],
  "hate speech": ["subhuman", "vermin", "inferior race", "go back to your country", "nigger", "nigga", "faggot", "fag", "kike", "spic", "chink", "tranny", "heil hitler"],
  "child safety": ["child porn", "cp links", "minor nudes", "pedo", "pedophile", "jailbait", "underage nudes", "teen nudes"],
  "spam": ["buy now", "free money", "crypto giveaway", "click here", "dm me", "whatsapp me", "telegram me", "earn money fast", "promo code", "follow me", "subscribe to my"],
  "self-harm": ["i want to kill myself", "how to kill myself", "end my life", "suicide method"],
};

export function normalize(input) {
  return input
    .normalize("NFKD").replace(/\p{M}+/gu, "")
    .toLowerCase()
    .replace(/0/g, "o").replace(/1/g, "i").replace(/3/g, "e").replace(/4/g, "a").replace(/5/g, "s").replace(/7/g, "t").replace(/@/g, "a").replace(/\$/g, "s")
    .replace(/(.)\1{2,}/g, "$1");
}

export function moderate(raw) {
  const text = String(raw ?? "").trim();
  if (!text) return { allowed: false, categories: ["empty"], message: "Write something before posting." };
  if ([...text].length > MAX_LENGTH) return { allowed: false, categories: ["length"], message: `Please keep reflections under ${MAX_LENGTH} characters.` };
  if (LINK.test(text)) return { allowed: false, categories: ["link"], message: "Links aren't allowed in reflections." };
  if (CONTACT.test(text)) return { allowed: false, categories: ["personal information"], message: "Please don't share phone numbers or email addresses." };
  const letters = text.replace(/[^\p{L}]/gu, "");
  if (letters.length >= 12 && letters === letters.toUpperCase() && letters !== letters.toLowerCase()) {
    return { allowed: false, categories: ["shouting"], message: "Please don't write in all capital letters." };
  }
  if (/(.)\1{9,}/u.test(text)) return { allowed: false, categories: ["spam"], message: "This looks like spam. Please rephrase." };
  const n = normalize(text);
  const compact = n.replace(/[^a-z]/g, "");
  const spaced = " " + n.replace(/[^a-z']+/g, " ").trim() + " ";
  const found = new Set();
  if (COMPACT.some((w) => compact.includes(w))) found.add("strong profanity");
  for (const [category, words] of Object.entries(TERMS)) {
    if (words.some((w) => spaced.includes(` ${w} `))) found.add(category);
  }
  if (found.size === 0) return { allowed: true, categories: [] };
  const categories = [...found];
  return { allowed: false, categories, message: `Please rephrase this reflection. It may include ${categories.join(", ")}, which the community rules don't allow.` };
}

export function cleanDetails(raw, max = 500) {
  return String(raw ?? "").replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, max);
}
