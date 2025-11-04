# Web Admin API Documentation

This bot includes a **secured REST API** and **WebSocket notifications** for the web-based admin panel.

## Security Model

All API endpoints (except `/api/health`) require **Discord OAuth2 authentication**.

### Permission Checks:
1. User must be authenticated via Discord OAuth2
2. User must have `ADMINISTRATOR` or `MANAGE_SERVER` permission in the Discord server
3. Bot must be present in the Discord server
4. All three conditions must be met to access server-specific endpoints

## API Endpoints

### Public Endpoints

#### GET /api/health
Health check endpoint - **NO AUTHENTICATION REQUIRED**

**Response:**
```json
{
  "status": "UP",
  "bot": {
    "connected": "CONNECTED",
    "guilds": 5,
    "username": "Color Gatcha Bot"
  },
  "timestamp": 1697567890123
}
```

---

### Secured Endpoints (Require OAuth2 Login)

#### GET /api/servers
List all servers where:
- User is an admin (has ADMINISTRATOR or MANAGE_SERVER permission)
- Bot is present

**Authentication:** Required
**Response:**
```json
[
  {
    "id": "123456789",
    "name": "My Discord Server",
    "iconUrl": "https://cdn.discordapp.com/icons/123456789/abc123.png",
    "userIsAdmin": true,
    "botIsPresent": true,
    "supportsEnhancedRoleColors": false
  }
]
```

---

#### GET /api/servers/{guildId}
Get details about a specific server

**Authentication:** Required
**Authorization:** User must be admin in this server AND bot must be present
**Response:**
```json
{
  "id": "123456789",
  "name": "My Discord Server",
  "iconUrl": "https://cdn.discordapp.com/icons/123456789/abc123.png",
  "userIsAdmin": true,
  "botIsPresent": true,
  "supportsEnhancedRoleColors": true
}
```

**Error Responses:**
- `401 Unauthorized` - Not logged in
- `403 Forbidden` - User is not an admin in this server OR bot is not present
- `404 Not Found` - Server not found

---

#### GET /api/servers/{guildId}/roles
List all gacha roles for a server

**Authentication:** Required
**Authorization:** User must be admin in this server AND bot must be present
**Response:**
```json
[
  {
    "id": "987654321",
    "name": "gacha:legendary:Rainbow Dreams",
    "displayName": "Rainbow Dreams",
    "rarity": "legendary",
    "color": "#FF00FF",
    "position": 15
  },
  {
    "id": "987654322",
    "name": "gacha:epic:Sunset Gradient",
    "displayName": "Sunset Gradient",
    "rarity": "epic",
    "color": "#FF8800",
    "position": 14
  }
]
```

**Error Responses:**
- `401 Unauthorized` - Not logged in
- `403 Forbidden` - User is not an admin in this server OR bot is not present

---

### Bulk role upload (CSV)

#### POST /api/servers/{guildId}/roles/upload-csv
Bulk create roles from a CSV file (multipart/form-data, field name `file`). Max size: 1MB.

Authentication: Required

Response:
```json
{
  "successCount": 10,
  "skippedCount": 1,
  "failureCount": 2,
  "createdRoles": [ /* GachaRoleInfo[] */ ],
  "skippedRoles": ["Sunset Glow"],
  "errors": ["Failed to create 'Prism Shift': Guild lacks enhanced role colors"]
}
```

CSV format:
- Header (recommended): `name,rarity,colorHex,secondaryColorHex,tertiaryColorHex`
- Required columns: `name`, `rarity`, `colorHex`
- Optional columns: `secondaryColorHex`, `tertiaryColorHex`
- Color values: hex in the form `#RRGGBB` (case-insensitive)
- Blank optional fields are allowed. Empty `colorHex` will default to a solid white fallback.

Behavior:
- If `secondaryColorHex` is provided (and `tertiaryColorHex` is empty), a gradient role is requested.
- If both `secondaryColorHex` and `tertiaryColorHex` are provided, a holographic role is requested.
- Enhanced colors (gradient/holographic) are created via the Discord HTTP API only when the guild supports the feature. If unsupported or the API call fails, the bot gracefully falls back to creating a solid-color role using `colorHex`.
- Duplicate role names (same full name `gatcha:{rarity}:{name}`) in the guild are skipped and reported under `skippedRoles`.

Example CSV:
```csv
name,rarity,colorHex,secondaryColorHex,tertiaryColorHex
# Solid color roles (secondary/tertiary left blank)
Sunset Glow,legendary,#FF6B35,,
Midnight Purple,legendary,#7209B7,,

# Gradient role example (primary + secondary)
Aurora Wave,epic,#7F00FF,#E100FF,

# Holographic role example (primary + secondary + tertiary)
Prism Shift,legendary,#00C6FF,#0072FF,#FF00FF
```

Related:
- GET `/api/servers/{guildId}/roles/download-example` — Download a ready-made example CSV from the server.

---

### QOTD (Question of the Day)

All QOTD endpoints require OAuth2 auth and server admin permissions.

#### GET /api/servers/{guildId}/qotd/channels
List text channels for configuration selection.

#### GET /api/servers/{guildId}/qotd/configs
List all channel QOTD configs in a guild.

#### GET /api/servers/{guildId}/channels/{channelId}/qotd/config
Get a single channel’s QOTD configuration.

#### PUT /api/servers/{guildId}/channels/{channelId}/qotd/config
Update a channel’s QOTD configuration.

Body:
```json
{
  "enabled": true,
  "scheduleCron": "0 9 * * *",
  "timezone": "UTC",
  "randomize": false,
  "autoApprove": false
}
```

#### GET /api/servers/{guildId}/channels/{channelId}/qotd/questions
List questions for a channel.

#### POST /api/servers/{guildId}/channels/{channelId}/qotd/questions
Add a question to a channel.

Body:
```json
{ "text": "What’s your favorite weekend activity?" }
```

#### DELETE /api/servers/{guildId}/channels/{channelId}/qotd/questions/{id}
Delete a question from a channel.

#### POST /api/servers/{guildId}/channels/{channelId}/qotd/upload-csv
Bulk upload questions via CSV (multipart/form-data, field name `file`).

#### POST /api/servers/{guildId}/channels/{channelId}/qotd/post-now
Immediately post the next question to the configured channel.

#### GET /api/servers/{guildId}/qotd/submissions
List pending user submissions for moderation.

#### POST /api/servers/{guildId}/channels/{channelId}/qotd/submissions/{id}/approve
Approve a specific submission and add it to the channel’s questions.

Body:
```json
{ "approverUserId": "123", "approverUsername": "AdminUser" }
```

#### POST /api/servers/{guildId}/channels/{channelId}/qotd/submissions/{id}/reject
Reject a specific submission.

Body:
```json
{ "approverUserId": "123", "approverUsername": "AdminUser" }
```

#### POST /api/servers/{guildId}/channels/{channelId}/qotd/submissions/bulk-approve
Bulk approve a list of submission IDs for a channel.

Body:
```json
{ "ids": [1,2,3], "approverUserId": "123", "approverUsername": "AdminUser" }
```

#### POST /api/servers/{guildId}/channels/{channelId}/qotd/submissions/bulk-reject
Bulk reject a list of submission IDs for a channel.

Body:
```json
{ "ids": [1,2,3], "approverUserId": "123", "approverUsername": "AdminUser" }
```

## Setup Instructions

### 1. Create Discord OAuth2 Application

1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Select your bot application
3. Go to **OAuth2** section
4. Note your **Client ID** and **Client Secret**
5. Add redirect URIs:
   - Development: `http://localhost:8080/login/oauth2/code/discord`
   - Production: `https://your-domain.com/login/oauth2/code/discord`

### 2. Configure Environment Variables

Update your `.env` file:

```env
DISCORD_TOKEN=your_bot_token_here
DISCORD_CLIENT_ID=your_client_id_here
DISCORD_CLIENT_SECRET=your_client_secret_here
```

### 3. Run the Application

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/playbot-1.0.0.jar
```

The web server will start on **port 8080**.

---

## Testing the API

### Test Health Endpoint (No Auth)

```bash
curl http://localhost:8080/api/health
```

### Test Secured Endpoints

**Option 1: Use a browser**
1. Navigate to `http://localhost:8080/`
2. Click "Login with Discord"
3. Authorize the application
4. Access API endpoints via browser or tools like Postman (session cookies will be set)

**Option 2: Use Postman**
1. Import the Discord OAuth2 flow in Postman
2. Configure OAuth2 with your Client ID and Secret
3. Set redirect URI to match your configuration
4. Get access token
5. Use token in Authorization header

---

## Realtime Notifications (WebSocket/STOMP)

- STOMP endpoint: `/ws` (SockJS supported)
- Topic: `/topic/guild-updates`
- Events: `GUILD_JOINED`, `GUILD_LEFT`, `ROLES_CHANGED`, `QOTD_QUESTIONS_CHANGED`, `QOTD_SUBMISSIONS_CHANGED`
- Auth: Requires an authenticated session; CSRF is disabled only for `/ws/**`

Example (JavaScript) using @stomp/stompjs:

```js
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'ws://localhost:8080/ws/websocket',
  reconnectDelay: 5000,
  // Cookies carry session; XSRF token not required for /ws/**
});

client.onConnect = () => {
  client.subscribe('/topic/guild-updates', (msg) => {
    const payload = JSON.parse(msg.body);
    console.log('Update:', payload);
  });
};

client.activate();
```

---

## Security Notes

- CSRF is enabled via `CookieCsrfTokenRepository` for the REST API. Your SPA client must echo the `XSRF-TOKEN` cookie value in the `X-XSRF-TOKEN` header for state-changing requests.
- CSRF is ignored for `/ws/**` only to allow WebSocket upgrades.
- Sessions persist via Spring Session JDBC; OAuth2 authorized clients are stored via JDBC as well.
- For production, set `server.servlet.session.cookie.secure=true` and review CORS origins.
- OAuth2 access tokens are managed via `OAuth2AuthorizedClientService`.

---

## Troubleshooting

**401 Unauthorized on all /api/servers endpoints:**
- Make sure you're logged in via Discord OAuth2
- Check that `DISCORD_CLIENT_ID` and `DISCORD_CLIENT_SECRET` are set correctly
- Verify redirect URI matches exactly in Discord Developer Portal

**403 Forbidden on specific server:**
- Verify you have admin permissions in that Discord server
- Verify the bot is present in that server
- Check logs for permission check failures

**Bot not starting:**
- Check `DISCORD_TOKEN` is valid
- Check Spring Boot logs for errors
- Verify port 8080 is not already in use
