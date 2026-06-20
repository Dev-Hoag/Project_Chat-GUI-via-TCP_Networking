# Refactor Log

## 2026-06-08

- Reworked `server.router.MessageRouter` to keep orchestration only and dispatch commands through `RouterCommand`.
- Extracted concrete `Command` implementation into `server.command.RouterCommand`.
- Updated room observer contract to include `roomId`, so room callbacks can target the correct conversation.
- Wired `server.core.ChatRoom` to notify observers with `roomId`.
- Moved room event handling out of `server.ClientHandler` into `server.observer.ClientRoomObserver`.
- Updated `server.service.client.ClientManager` to manage room observer adapters per client and attach them to rooms.
- Extracted chat orchestration into `server.service.chat.ChatService` so `MessageRouter` no longer owns message/file/room/presence logic.
- Split `server.service.database.DatabaseManager` configuration and schema setup into `DatabaseConfig` and `DatabaseSchemaInitializer`.
- Upgraded `server.command.CommandQueue` to run commands sequentially on a dedicated worker thread.
- Kept protocol format unchanged so the existing client should continue to interoperate.

## Notes

- `server.ChatRoom` still exists as a legacy class, but the runtime flow now uses `server.core.ChatRoom`.
- `CommandQueue` now preserves order and executes on a single worker thread.

## 2026-06-09

- Added auth flow with `REGISTER` and password-based `LOGIN` using `AuthService`.
- Added user persistence with `IUserRepository` and a `users` table for hashed credentials and profile data.
- Added avatar upload flow with `AVATAR_SET` and dedicated `AvatarService` / `ProfileService`.
- Updated client login UI to support login/register mode, password entry, display name, and optional avatar selection.
- Added avatar upload from chat UI and a small emoji picker for inserting Unicode emoji into messages.
- Added online user payload metadata so client can render avatar thumbnails in the user list and profile header.
- Added avatar preview rendering and cached thumbnail loading on the client side.
- Kept chat/file protocol line-based and UTF-8 encoded, so emoji text is supported without changing the transport model.

## 2026-06-16

- Added client-side right-click forward flow for text/file messages with a destination picker over rooms and users.
- Added `FORWARD_MSG` on the server so a message can be forwarded to another room or private conversation.
- Fixed room typing payload to use the same `TYPING_STATUS` shape as private chat, so the client can render "typing..." correctly.
- Fixed room message realtime delivery by broadcasting room chat directly to members and sending a sender ack with `ROOM_MSG_SENT`.
- Added real-time typing indicators with `TYPING_START`, `TYPING_STOP`, and `TYPING_STATUS`.
- Routed typing events through `MessageRouter` and a dedicated `TypingStrategy` so the router stays thin.
- Updated `ChatRoom` and `RoomObserver` so room typing state is pushed through the existing observer flow.
- Updated `ClientSocketService` to send typing events and surface typing updates to the UI listener.
- Added typing debounce and auto-stop logic in `ChatFrame` so the UI does not spam the server while the user is typing.
- Added a live `đang soạn...` label in the chat window that follows the currently selected conversation.
