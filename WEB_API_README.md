# Web Admin API - MVP Documentation

This bot now includes a **secured REST API** for web-based administration.

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
    "botIsPresent": true
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
  "botIsPresent": true
}
```

**Error Responses:**
- `401 Unauthorized` - Not logged in
- `403 Forbidden` - User is not an admin in this server OR bot is not present
- `404 Not Found` - Server not found

---

#### GET /api/servers/{guildId}/roles
List all gatcha roles for a server

**Authentication:** Required
**Authorization:** User must be admin in this server AND bot must be present
**Response:**
```json
[
  {
    "id": "987654321",
    "name": "gatcha:legendary:Rainbow Dreams",
    "displayName": "Rainbow Dreams",
    "rarity": "legendary",
    "color": "#FF00FF",
    "position": 15
  },
  {
    "id": "987654322",
    "name": "gatcha:epic:Sunset Gradient",
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
java -jar target/discord-bot-1.0.0.jar
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

## Next Steps

This MVP provides:
- ✅ Secured REST API
- ✅ Permission validation (user admin + bot present)
- ✅ List manageable servers
- ✅ List gatcha roles per server

**Not yet implemented:**
- ⏳ Frontend UI (React)
- ⏳ Create/Edit/Delete role endpoints
- ⏳ Statistics/analytics endpoints
- ⏳ Role assignment preview
- ⏳ Bulk operations

---

## Security Notes

- CSRF is currently disabled for API simplicity (see `SecurityConfig.java`)
- For production, enable CSRF protection
- For production, set `server.servlet.session.cookie.secure=true` in `application.properties`
- OAuth2 access tokens should be properly managed using `OAuth2AuthorizedClientService`
- Consider adding rate limiting for production use

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
