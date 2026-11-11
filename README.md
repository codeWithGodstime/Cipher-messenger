# Cipher

1:1 messenger for Android. The app encrypts messages and attachments on the device. The server stores ciphertext, public keys, and accounts.

UI reference: [Cipher Android App Prototype](https://stitch.withgoogle.com/projects/15997076852132234392).

## Links

- Backend: https://messenger.publicvm.com/
- Download the app: https://example.com/cipher.apk
- Demo: [Demo Link](https://drive.google.com/file/d/15Bbt5vpRkPJt3CNUvBpHulO2d8PB1X4d/view?usp=sharing)
- What I learnt: https://example.com/blog/cipher

## Scope

- Username signup and login. No phone number and no verification code.
- Profile (display name, about, photo URL), contacts, and username search.
- One conversation per pair of accounts. One device per account.
- Signal encryption for text. Photos, files, and voice notes use AES-256-GCM; the file key rides inside the Signal message.
- WebSocket for live send and username search. HTTP can send a message if the socket is down.

Groups, calls, and multi-device sessions are out of scope.

## Architecture

```text
Android (Compose, libsignal)  →  Go API (Gin)  →  Postgres or SQLite
                                      ↓
                                 ciphertext files
```

The app encrypts, then sends. The API checks that the caller is a member of the conversation, stores the body, and pushes the same ciphertext to the other member.

`mobile/` is the app. Signed-out flow is welcome, username, then profile setup. Signed-in flow is the chat list, a conversation, new chat, and settings. Keys and sessions stay in encrypted preferences, one slot per username.

`backend/` is handler → service → repository. GORM creates the tables on startup. Routes cover auth, profile, keys, contacts, conversations, messages, attachments, and `GET /ws`. The socket frames are `message.send`, `user.search`, and `ping`.

Accounts, sessions, contacts, conversations, ciphertext messages, and public prekeys live in the database. Attachment bytes live in `MEDIA_DIR` (32 MiB max) with a SHA-256 only. The server does not store file names, media types, or keys.

Each account uploads an identity key, a signed prekey, a Kyber prekey, and one-time prekeys. The first message to someone claims their bundle and opens a session. Later messages use the ratchet on the device. Plaintext is padded before encryption.

## Run

From `backend/`, set `DATABASE_URL` in `.env` (`sqlite://messenger.db` locally) and run `make start`. The API listens on port 8080.

Open `mobile/` in Android Studio. `API_BASE_URL` is in `mobile/.env.example`.

## What I learnt

Write-up of building Cipher: [What I learnt](https://example.com/blog/cipher).
