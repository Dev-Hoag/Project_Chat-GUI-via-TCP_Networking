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
