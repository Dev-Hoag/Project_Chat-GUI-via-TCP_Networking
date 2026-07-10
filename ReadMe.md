# Project Chat GUI via TCP Networking

Desktop chat application built with Java and TCP sockets, with a Swing-based client interface and a server that supports persistent chat data through MariaDB.

## Overview

This project focuses on building a local-network chat system with a graphical client, custom TCP message protocol, and a server-side architecture organized around handlers, services, and persistence.

The repository also includes supporting coursework materials such as Word documents, PowerPoint slides, and extra resources used during development.

## Main Features

- User login and registration
- Public lobby chat
- Private messaging between users
- Room creation, invitations, and room chat
- Chat history loading
- Typing status updates
- File transfer
- Avatar upload and retrieval
- MariaDB-backed persistence for users, messages, rooms, and conversations

## Tech Stack

- Java
- Java Swing
- TCP sockets
- MariaDB
- HeidiSQL for local database management

## Repository Structure

- `Code/TCPChatApp`: main Java source code
- `DOCX`: Word documents related to the project
- `PPTX`: presentation files
- `Extra`: extra project artifacts
- `run-server.bat`: quick Windows script to start the server
- `run-client.bat`: quick Windows script to start the client

## Chat App Structure

Inside `Code/TCPChatApp`:

- `src/client`: client startup, socket service, and login flow
- `src/client/gui`: Swing user interface
- `src/common`: shared protocol and message utilities
- `src/server`: server entry point and server-side logic
- `src/server/service`: database, file, profile, auth, and chat services
- `src/server/handler`: message handling strategies
- `src/server/router`: request routing
- `lib`: bundled MariaDB JDBC driver for local runs

## Quick Start

### 1. Prepare the database

Create a MariaDB database named `chatapp`.

Detailed setup guide:

- [MARIADB_SETUP.md](C:/Users/PC/Documents/Project_Chat-GUI-via-TCP_Networking/Code/TCPChatApp/MARIADB_SETUP.md)

Example `.env` file:

- [Code/TCPChatApp/.env.example](C:/Users/PC/Documents/Project_Chat-GUI-via-TCP_Networking/Code/TCPChatApp/.env.example)

### 2. Build the project

Build the project from IntelliJ so compiled classes are generated under:

`Code/TCPChatApp/out/production/TCPChatApp`

### 3. Start the server

From the repository root:

```bat
run-server.bat
```

Default server port:

`5001`

### 4. Start the client

From the repository root:

```bat
run-client.bat
```

Or connect to another host and port:

```bat
run-client.bat 192.168.1.91 5001
```

## Database Notes

The project has been updated to use MariaDB instead of PostgreSQL/Supabase-style configuration.

Current environment variables:

```env
DB_URL=jdbc:mariadb://127.0.0.1:3306/chatapp
DB_USER=root
DB_PASSWORD=your_password_here
```

The server automatically initializes the required tables when the database connection succeeds.

## Networking Notes

- If server and client run on the same machine, use `127.0.0.1`
- If client runs on another machine in the same LAN, use the server machine's local IP
- If port `5001` is already in use, stop the old server process or change the port when starting the server

## Current Status

- MariaDB integration is included
- MariaDB JDBC driver is bundled for local Windows runs
- Quick launch scripts are included
- The repository is suitable for local development, demo, and coursework presentation

## Notes

- The project is currently set up primarily for Windows-based local development
- Some IntelliJ project files are included to make local setup easier
- Runtime-generated folders such as `server_files` may be created during use
