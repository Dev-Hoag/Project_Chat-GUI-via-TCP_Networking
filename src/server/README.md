# Server Module

## Purpose

This folder contains the TCP chat server runtime. The server is responsible for:

- authenticating users
- keeping track of online clients and rooms
- routing commands to the correct handler
- broadcasting real-time events such as join, leave, typing, and room updates
- saving and loading chat history from the database
- handling avatar and file transfer metadata

## Main Flow

1. `ChatServer` accepts a socket connection.
2. `ClientHandler` authenticates the client with `LOGIN` or `REGISTER`.
3. `ClientHandler` passes parsed commands to `MessageRouter`.
4. `HandlerFactory` maps the command to a `MessageStrategy`.
5. The selected strategy calls router methods such as room message, private message, typing, or history.
6. `ChatService` applies business rules, saves data, and sends responses.

## Current Structure

### Entry and socket layer

- `ChatServer.java`
- `ClientHandler.java`

### Routing and command handling

- `router/MessageRouter.java`
- `handler/HandlerFactory.java`
- `handler/MessageStrategy.java`
- `handler/CreateRoomStrategy.java`
- `handler/HistoryRequestStrategy.java`
- `handler/JoinRoomStrategy.java`
- `handler/LeaveRoomStrategy.java`
- `handler/PrivateMessageStrategy.java`
- `handler/RoomMessageStrategy.java`
- `handler/TypingStrategy.java`
- `handler/ForwardMessageStrategy.java`

### Core room model

- `core/ChatRoom.java`

### Observer support for real-time room and server events

- `observer/RoomObserver.java`
- `observer/ClientRoomObserver.java`
- `observer/ServerObserver.java`
- `observer/ServerBroadcaster.java`

### Services

- `service/chat/ChatService.java`
- `service/client/ClientManager.java`
- `service/auth/AuthService.java`
- `service/profile/ProfileService.java`
- `service/avatar/AvatarService.java`
- `service/database/DatabaseManager.java`
- `service/file/FileService.java`

### Contracts

- `service/client/IClientManager.java`
- `service/database/IDatabase.java`
- `service/file/IFileService.java`
- `service/user/IUserRepository.java`

### Sending helpers

- `decorator/SocketHandler.java`
- `decorator/ValidationHandler.java`

## Design Choices

The current server keeps only the patterns that are actively useful in the runtime flow:

- `Factory`: `HandlerFactory` maps each protocol command to the correct handler
- `Strategy`: each command type has its own handling class
- `Observer`: room and server events can notify connected clients without hard-coding all broadcast logic into `ChatRoom`

## Real-Time Notes

- Private typing is sent directly to the receiver.
- Room typing is sent through the room observer list.
- Join, leave, user list, and room list updates are broadcast through `ServerBroadcaster`.
- Room messages are delivered to room members and acknowledged back to the sender.

## Practical Limitation

At runtime, when a user disconnects, the server removes that user's observer and membership from in-memory rooms. Persistent room data can still be restored from the database on the next login, but in-memory membership is not kept for offline users.
