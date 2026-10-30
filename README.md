# Psalmwell: Daily Bible Verse

Psalmwell is an Android app from Hartmann Studios for a quiet daily moment with Scripture.

- **Verse of the day**: the same verse for everyone each day, with an optional reminder.
- **All 150 Psalms**: read offline. No account needed.
- **Private journal and saved verses**: stored only on the device.
- **Reflections**: a moderated space to share a short reflection on the day's verse. Posting needs an account and agreeing to the community rules. Every reflection is checked on the device and again on the server. People can report reflections, report accounts and block people, and they can delete their account in the app or on the web.

Scripture text is the World English Bible, which is in the public domain.

Psalmwell is distributed only through Google Play.

## Project layout

| Path | What it is |
| --- | --- |
| `app/` | Android app (Kotlin, Jetpack Compose) |
| `worker/` | `psalmwell-api`, a Cloudflare Worker that checks and stores reflections and handles reports and account deletion |
| `site/` | The public website: privacy policy, community rules, child safety standards, support and account deletion |
| `firestore.rules`, `firestore.indexes.json` | Firestore security rules and indexes |

## Building

```
./gradlew :app:assembleDebug
```

Without `app/google-services.json`, the app still builds and runs. Reading, the Psalms, saved verses and the journal all work, and Reflections shows that it is unavailable. Release signing reads its keystore from environment variables, and the keystore is never committed.

## Contact

Support and appeals: me@charleshartmann.com
