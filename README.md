# Chat Application

A simple client-server chat application built with Java 25, Maven, and Swing.

---

## Project Structure

```
chatapp/
├── pom.xml                          # Root POM
├── server/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/chatapp/server/
│       │   ├── ServerMain.java
│       │   ├── Config.java
│       │   ├── UserStore.java
│       │   ├── ChatServer.java
│       │   ├── ClientHandler.java
│       │   └── ServerGui.java
│       └── resources/
│           └── server.properties
└── client/
    ├── pom.xml
    └── src/main/
        ├── java/com/chatapp/client/
        │   ├── ClientMain.java
        │   ├── Config.java
        │   ├── ServerConnection.java
        │   └── ClientGui.java
        └── resources/
            └── client.properties
```

---

## Prerequisites

| Tool       | Version  | Notes                              |
|------------|----------|------------------------------------|
| JDK        | 25       | Must include `jpackage` (bundled)  |
| Maven      | 3.9+     |                                    |
| Windows 10/11 | —     | Required for `jpackage` EXE output |

Verify your environment:

```cmd
java -version
mvn -version
jpackage --version
```

---

## Build Instructions

### 1. Clone / extract the project

```cmd
cd C:\projects\chatapp
```

### 2. Build both modules

```cmd
mvn clean package
```

This produces:
- `server\target\server.jar`
- `client\target\client.jar`

---

## Run Without Packaging (Development)

### Start the server

```cmd
java -jar server\target\server.jar
```

### Start one or more clients (each in a new terminal)

```cmd
java -jar client\target\client.jar
```

---

## jpackage — Windows Native Executables

Run these commands from the **project root** after `mvn clean package`.

### Package the server

```cmd
jpackage ^
  --input server\target ^
  --name ChatServer ^
  --main-jar server.jar ^
  --main-class com.chatapp.server.ServerMain ^
  --type exe ^
  --dest dist\server ^
  --app-version 1.0.0 ^
  --win-console ^
  --java-options "-Xmx256m"
```

### Package the client

```cmd
jpackage ^
  --input client\target ^
  --name ChatClient ^
  --main-jar client.jar ^
  --main-class com.chatapp.client.ClientMain ^
  --type exe ^
  --dest dist\client ^
  --app-version 1.0.0 ^
  --java-options "-Xmx256m"
```

> **Note:** `jpackage` bundles a full JRE into the installer. The output in
> `dist\server\` and `dist\client\` will each contain a self-contained
> `ChatServer.exe` / `ChatClient.exe` that requires no separate Java
> installation on the target machine.
>
> If you only want a portable app directory instead of an installer, replace
> `--type exe` with `--type app-image`.

---

## Configuration

Both configuration files are embedded inside the JAR via
`src/main/resources/`. To change the host or port, edit them before building.

**`server/src/main/resources/server.properties`**
```properties
host=127.0.0.1
port=5000
```

**`client/src/main/resources/client.properties`**
```properties
server.host=127.0.0.1
server.port=5000
```

---

## Usage

### Server window

1. Launch `ChatServer.exe` (or `server.jar`).
2. The server starts automatically on port 5000.
3. The **Chat Log** panel shows all messages in real time.
4. The **Console** panel shows connection events and errors.
5. Use **Stop** / **Start** to restart the server without closing the window.

### Client window

1. Launch `ChatClient.exe` (or `client.jar`) — open as many as you like.
2. On the **Login / Register** screen:
   - **Register** to create a new account.
   - **Login** with an existing account.
3. After authentication the chat screen opens automatically.
4. Type a message and press **Enter** or click **Send**.

---

## Authentication Rules

| Rule | Detail |
|------|--------|
| Username length | 1–20 characters |
| Username start | Must begin with a letter |
| Allowed characters | Letters, digits, `_`, `-` |
| Consecutive symbols | Not allowed (`john__doe`, `john-_doe`) |
| Password minimum | 4 characters |
| Duplicate login | Rejected with a specific error message |

---

## Networking Protocol

All messages are newline-delimited UTF-8 plain text over a raw TCP socket.

| Direction | Format | Purpose |
|-----------|--------|---------|
| Client → Server | `LOGIN\|username\|password` | Authenticate existing user |
| Client → Server | `REGISTER\|username\|password` | Create and authenticate new user |
| Client → Server | `CHAT\|message text` | Send a chat message |
| Server → Client | `OK` | Auth success |
| Server → Client | `ERROR\|reason` | Auth failure or invalid command |
| Server → Client | `MSG\|username\|message text` | Broadcast chat message |
| Server → Client | `SYSTEM\|text` | Join / leave notifications |

---

## Data Persistence

User accounts are saved to **`chatapp_users.csv`** in the current user's home
directory (e.g. `C:\Users\YourName\chatapp_users.csv`). This avoids
write-permission issues when the EXE is installed under `Program Files`.
The file is created automatically on first run.

```csv
username,password
alice,1234
bob,abcd
```

Chat messages are **not** persisted — they exist only for the duration of the
session.

---

## Limitations (by design — coursework scope)

- Passwords are stored in plain text.
- No private / direct messaging.
- No chat history loaded on login.
- Immediate shutdown (no graceful drain of in-flight messages).
- Single chat room only.
