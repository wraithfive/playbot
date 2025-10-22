# Playbot

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-green.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19.1.1-blue.svg)](https://react.dev/)

A Discord bot that lets users roll for random colored name roles once per day, with a web-based admin panel for easy role management.

**[Features](#features) • [Quick Start](#quick-start) • [Documentation](#table-of-contents) • [Contributing](CONTRIBUTING.md) • [License](#license)**

## Features

- **Discord Bot** - Users roll for random colored roles with rarity-based weighting
- **Web Admin Panel** - Manage gacha roles through an intuitive web interface
- **OAuth2 Authentication** - Secure Discord login for server administrators
- **Rarity System** - 5-tier rarity system (Legendary, Epic, Rare, Uncommon, Common)
- **Daily Rolls** - Users can roll once per day (admins can use `!testroll` for unlimited testing)

## Technology Stack

**Backend:**
- Java 21 (LTS) with modern features
- Spring Boot 3.4.1
- JDA 5.0.0 (Java Discord API)
- Java Records for immutable DTOs
- OAuth2 authentication with Spring Security

**Frontend:**
- React 19.1.1 + TypeScript
- Vite 7.1.7 (build tool)
- React Router DOM (navigation)
- Axios with CSRF protection
- TanStack React Query (data fetching)

**Database:**
- H2 Database (file-based persistence)
- Spring Data JPA
- Cooldown data survives restarts

**Security:**
- CSRF protection enabled
- OAuth2 authentication flow
- Secure token handling

## Table of Contents

- [Quick Start](#quick-start)
- [Discord Bot Setup](#discord-bot-setup)
- [Web Admin Panel](#web-admin-panel)
- [Bot Permissions](#bot-permissions)
- [Creating Color Roles](#creating-color-roles)
- [Role Management](#role-management)
- [Commands](#commands)
- [API Documentation](#api-documentation)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

## Quick Start

### Prerequisites

- Java 21 (LTS) or higher
- Maven 3.6+
- Node.js 18+ (for web admin panel)
- Discord Bot Token
- Discord Application with OAuth2 configured

## Discord Bot Setup

### Installation

1. Clone this repository

2. **Get your Discord credentials** from the [Discord Developer Portal](https://discord.com/developers/applications):

   **Bot Token:**
   - Go to your application → Bot
   - Click "Reset Token" to generate a new token
   - Copy the token (you'll only see it once!)

   **OAuth2 Credentials:**
   - Go to your application → OAuth2 → General
   - Copy your "Client ID"
   - Click "Reset Secret" to generate a new Client Secret
   - Copy the Client Secret (you'll only see it once!)

3. Create a `.env` file in the project root with your credentials:
   ```env
   DISCORD_TOKEN=your_bot_token_here
   DISCORD_CLIENT_ID=your_client_id_here
   DISCORD_CLIENT_SECRET=your_client_secret_here
   ADMIN_PANEL_URL=http://localhost:8080
   ```

   **Example:**
   ```env
   DISCORD_TOKEN=your_actual_bot_token_from_discord_developer_portal
   DISCORD_CLIENT_ID=your_actual_client_id_here
   DISCORD_CLIENT_SECRET=your_actual_client_secret_here
   ADMIN_PANEL_URL=http://localhost:8080
   ```

   **Note:** For production, change `ADMIN_PANEL_URL` to your actual domain (e.g., `https://your-domain.com`)

   ⚠️ **Important:** Never commit your `.env` file to version control! It's already in `.gitignore`.

4. Build the project:
   ```bash
   # Make the build script executable (first time only)
   chmod +x build.sh

   # Build both backend and frontend
   ./build.sh

   # Options:
   # ./build.sh --skip-tests      # Skip running tests
   # ./build.sh --clean           # Clean before building
   # ./build.sh --production      # Production build with optimization
   ```

   Or build manually:
   ```bash
   # Backend only
   mvn clean package -DskipTests

   # Frontend only
   cd frontend && npm install && npm run build
   ```

### Quick Start - Run Both Services

For convenience, use the startup script to run both backend and frontend:

```bash
# Make the script executable (first time only)
chmod +x start.sh

# Start both services
./start.sh
```

The script will:
- Start the backend on port 8080
- Start the frontend on port 3000
- Display logs from both services
- Press Ctrl+C to stop both services

### Inviting the Bot

**Option 1: Using the Admin Panel (Recommended)**

1. Start the bot and web admin panel using `./start.sh`
2. Login to the admin panel at `http://localhost:3000`
3. Find a server where you're an admin but the bot isn't present
4. Click the "Invite Bot" button
5. Authorize the bot with the requested permissions
6. **IMPORTANT:** After the bot joins, go to Server Settings → Roles and drag the Playbot role ABOVE all gacha roles

**Option 2: Manual Invite (Advanced)**

1. Go to the [Discord Developer Portal](https://discord.com/developers/applications)
2. Select your application
3. Go to OAuth2 > URL Generator
4. Select scopes: `bot` and `applications.commands`
5. Select permissions: `Manage Roles`, `Send Messages`, `View Channels`
6. Use the generated URL to invite the bot to your server
7. **IMPORTANT:** After the bot joins, go to Server Settings → Roles and drag the Playbot role ABOVE all gacha roles

### OAuth2 Configuration

For the web admin panel, configure OAuth2 in the Developer Portal:

1. Go to OAuth2 > General
2. Add redirect URI:
   - Development: `http://localhost:8080/login/oauth2/code/discord`
   - Production: `https://your-domain.com/login/oauth2/code/discord`

**Important:** After OAuth2 login succeeds, the backend will redirect you back to the frontend at `http://localhost:3000`

## Web Admin Panel

The web admin panel provides a visual interface for managing gacha roles.

### Starting the Frontend

1. Navigate to the frontend directory and install dependencies:
   ```bash
   cd frontend
   npm install
   ```

2. Start the development server:
   ```bash
   npm run dev
   ```

   The frontend will start on port 3000 (Vite may use 5173 or another port if 3000 is taken).

3. Open your browser to the URL shown in the terminal (typically `http://localhost:3000`)

4. Click "Login with Discord" to authenticate with your configured OAuth2 credentials

### Features

- **Server List** - View all servers where you have admin permissions
- **Role Browser** - Browse all gacha roles organized by rarity
- **Color Preview** - Visual preview of each role's color
- **Bot Status** - Check which servers have the bot installed

### Access Requirements

To access the admin panel, you need:
- Administrator or Manage Server permissions on at least one server
- The bot must be installed on your server

### Customizing Legal Documents

The admin panel includes Privacy Policy and Terms of Service pages. For self-hosting, you should customize these:

1. Edit the template files in `frontend/src/components/`:
   - `PrivacyPolicy.template.tsx`
   - `TermsOfService.template.tsx`

2. Replace placeholders with your information:
   - `[YOUR NAME/ORGANIZATION]` - Your name or organization
   - `[YOUR CONTACT EMAIL]` - Your contact email
   - `[YOUR WEBSITE]` - Your website URL

3. Generate the production files:
   ```bash
   cd frontend
   npm run generate:legal
   ```

   Or set environment variables for automatic generation during build:
   ```bash
   ORGANIZATION_NAME="Your Name" \
   CONTACT_EMAIL="you@example.com" \
   WEBSITE_URL="https://example.com" \
   npm run build
   ```

**Note:** These templates are provided as-is. Consult with a lawyer to ensure compliance with your jurisdiction's laws.

## Bot Permissions

The bot requires the following permissions:

- **Manage Roles** - To assign and remove color roles from users
- **Send Messages** - To respond to commands
- **View Channels** - To see channels and respond to slash commands

### ⚠️ CRITICAL: Role Hierarchy Setup

**The bot's role MUST be positioned ABOVE all gacha roles in your server's role hierarchy.**

Discord's permission system prevents bots from managing roles that are higher than their own role in the list. Even if the bot has the "Manage Roles" permission, it cannot assign or remove roles that are positioned above it.

**How to fix role hierarchy:**

1. Open Discord and go to your Server Settings
2. Click on "Roles" in the left sidebar
3. Find the Playbot role (or your bot's role)
4. **Drag it ABOVE all roles that start with `gacha:`**
5. The hierarchy should look like this:

```
Administrator Roles
───────────────────
Playbot ← BOT ROLE MUST BE HERE
───────────────────
gacha:legendary:Rainbow
gacha:epic:Gold
gacha:rare:Pink
... (all other gacha roles)
───────────────────
@everyone
```

**If you get permission errors when using `/roll`:**
- This is almost always because the bot's role is too low in the hierarchy
- Move the bot's role higher and try again
- The bot will tell you exactly what's wrong in the error message

## Creating Color Roles

### Role Naming Format

The bot reads roles from your server that follow this naming convention:

```
gacha:rarity:ColorName
```

or

```
gacha:ColorName
```

**Examples:**
- `gacha:legendary:Rainbow` - Rainbow color with legendary rarity
- `gacha:epic:Gold` - Gold color with epic rarity
- `gacha:common:Gray` - Gray color with common rarity
- `gacha:Custom` - Custom color with no rarity (equal weight)

### Valid Rarities

| Rarity | Emoji | Drop Rate | Weight |
|--------|-------|-----------|--------|
| legendary | 🟡 | 0.5% | 0.25 |
| epic | 🟣 | 2.5% | 1.25 |
| rare | 🔵 | 7% | 2.33 |
| uncommon | 🟢 | 20% | 4 |
| common | ⚪ | 70% | 10 |

Roles without a rarity specification will have equal weight (1.0) in the selection pool.

### Quick Setup with Scripts

#### Option 1: Using Bash Script

1. Edit `create-roles.sh` and set your bot token and guild ID:
   ```bash
   BOT_TOKEN="your_bot_token_here"
   GUILD_ID="your_guild_id_here"
   ```

2. Run the script:
   ```bash
   chmod +x create-roles.sh
   ./create-roles.sh
   ```

This creates 17 default roles:
- 1 legendary (Rainbow)
- 2 epic (Gold, Violet)
- 3 rare (Pink, Cyan, Lime)
- 5 uncommon (Blue, Red, Green, Purple, Orange)
- 7 common (Gray, Brown, Olive, Teal, Navy, Maroon, Beige)

#### Option 2: Using Postman

1. Import `discord-gacha-roles.postman_collection.json` into Postman
2. Set collection variables:
   - `bot_token`: Your bot token
   - `guild_id`: Your server/guild ID
3. Run the collection

#### Option 3: Manual Creation

1. Go to Server Settings > Roles
2. Create a new role
3. Name it using the format: `gacha:rarity:ColorName`
4. Set the color you want
5. Ensure it's positioned BELOW the bot's role
6. Repeat for each color you want to add

## Role Management

### Role Hierarchy

```
[Bot Role] ← MUST BE HERE OR HIGHER
───────────
gacha:legendary:Rainbow
gacha:epic:Gold
gacha:epic:Violet
gacha:rare:Pink
... (other gacha roles)
───────────
@everyone
```

**Important:** If gacha roles are above the bot's role, the bot cannot assign them.

### Adding New Colors

To add a new color to the gacha pool:

1. Create a role with the naming format: `gacha:rarity:ColorName`
2. Set the desired color
3. Position it below the bot's role
4. Users can immediately start rolling for it (no bot restart needed!)

### Removing Colors

To remove a color from the gacha pool:

1. Delete or rename the role (remove the `gacha:` prefix)
2. The bot will stop including it in rolls immediately

### Changing Drop Rates

To adjust the distribution of rarities, edit the values in `ColorGachaHandler.java`:

```java
private enum Rarity {
    COMMON(10, "⚪", "70%"),      // Adjust weight (currently 10)
    UNCOMMON(4, "🟢", "20%"),     // Adjust weight (currently 4)
    RARE(2.33, "🔵", "7%"),       // Adjust weight (currently 2.33)
    EPIC(1.25, "🟣", "2.5%"),     // Adjust weight (currently 1.25)
    LEGENDARY(0.25, "🟡", "0.5%"); // Adjust weight (currently 0.25)
}
```

Then rebuild and restart the bot.

## Commands

All commands use Discord's slash command system. Type `/` in Discord to see available commands.

### User Commands

| Command | Description | Restrictions | Visibility |
|---------|-------------|--------------|------------|
| `/roll` | Roll for a random color | Once per day | Public (everyone sees result) |
| `/mycolor` | Check your current color and rarity | None | Private (only you see) |
| `/colors` | View all available colors and rarities | None | Private (only you see) |
| `/help` | Show help message and legal links | None | Private (only you see) |

### Admin/Moderator Commands

| Command | Description | Permissions Required | Visibility |
|---------|-------------|---------------------|------------|
| `/testroll` | Test roll (unlimited uses) | Administrator, Manage Server, or Moderate Members | Private (only you see) |

**Note:** Most commands use "ephemeral" responses, meaning only you can see the bot's reply. This keeps channels clean and makes the experience more private.

## API Documentation

The bot exposes a REST API for the web admin panel. For detailed API documentation, see [WEB_API_README.md](WEB_API_README.md).

### Available Endpoints

- `GET /api/health` - Check bot status (public)
- `GET /api/servers` - List manageable servers (requires auth)
- `GET /api/servers/{guildId}` - Get server details (requires auth)
- `GET /api/servers/{guildId}/roles` - List gacha roles (requires auth)

All API endpoints (except `/api/health`) require Discord OAuth2 authentication.

## Troubleshooting

### ❌ "Failed to assign role!" Error

**This is the most common issue and is almost always caused by role hierarchy.**

**Solution:**
1. Open Discord → Server Settings → Roles
2. Find the Playbot role (or your bot's role name)
3. **Drag it ABOVE all `gacha:` roles**
4. Try using `/roll` again

The bot will tell you the specific issue in the error message:
- **"Missing Permissions"** → Bot doesn't have "Manage Roles" permission
- **"hierarchy"** → Bot's role is too low in the role list
- Other errors will show the specific Discord API error

### Bot isn't changing user name colors

**Cause:** The bot's role is not high enough in the role hierarchy.

**Solution:**
1. Go to Server Settings > Roles
2. Drag the bot's role ABOVE all gacha roles
3. Try rolling again

See the [Role Hierarchy Setup](#️-critical-role-hierarchy-setup) section for detailed instructions.

### "No gacha roles found" error

**Cause:** No roles starting with `gacha:` exist on the server.

**Solution:** Create roles using the naming format `gacha:rarity:ColorName` or run the setup script.

### Rolls seem unbalanced

**Cause:** The distribution might be affected by the number of roles in each rarity tier.

**Example:** If you have 10 common roles and only 1 legendary role, the system works as intended - each individual common role is less likely than the legendary, but commons as a category are more likely.

**Solution:** This is by design. Each individual role is weighted by its rarity, but having more roles in a rarity tier doesn't change the overall probability of that tier.

### Bot not responding to slash commands

**Possible causes:**
1. Bot is offline - Check if the bot process is running (`./start.sh`)
2. Bot lacks permissions - Ensure bot has "Send Messages" and "View Channels" permissions
3. Commands not registered - Kick and re-invite the bot, or wait up to 1 hour for Discord to sync commands

### Slash commands not appearing

**Solution:**
1. Make sure you invited the bot with the `applications.commands` scope
2. If you used the old invite (before slash commands), remove and re-invite the bot
3. Commands are registered per-server when the bot joins or starts up

## Testing Mode

Currently, the daily roll limit is **disabled** for testing purposes. This is controlled in `ColorGachaHandler.java` lines 84-95.

To **enable** the daily limit for production:

1. Open `src/main/java/com/discordbot/ColorGachaHandler.java`
2. Find the comment `// TESTING MODE: Infinite rolls enabled`
3. Uncomment the daily check code block
4. Rebuild and restart the bot

## Finding Your Guild ID

1. Enable Developer Mode in Discord:
   - User Settings > Advanced > Developer Mode (toggle on)
2. Right-click your server icon
3. Click "Copy Server ID"

## Support

For issues or feature requests, please check the bot logs for error messages. The bot logs detailed information about role assignment operations.

Common log messages:
- `Removing old role: [role name]` - Bot is removing previous gacha role
- `Assigning role: [role name] to user: [username]` - Bot is assigning new role
- `Successfully assigned role!` - Role assignment succeeded
- `ERROR assigning role: [error]` - Role assignment failed (check permissions/hierarchy)

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on:

- Reporting bugs
- Suggesting features
- Submitting pull requests
- Code standards
- Development setup

### Quick Contribution Steps

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

Please read our [Code of Conduct](CODE_OF_CONDUCT.md) before contributing.

## Privacy & Legal

If you're self-hosting Playbot, you are responsible for providing your own Privacy Policy and Terms of Service to your users.

**Templates are provided** in `frontend/src/components/`:
- `PrivacyPolicy.template.tsx` - Customize with your information
- `TermsOfService.template.tsx` - Customize with your information

Copy these files to `PrivacyPolicy.tsx` and `TermsOfService.tsx` and replace all `[PLACEHOLDERS]` with your details. Consult with a lawyer to ensure compliance with your jurisdiction.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

### Attribution

This project uses several open source libraries. See [ATTRIBUTIONS.md](ATTRIBUTIONS.md) for full details and license information.

## Acknowledgments

- [JDA](https://github.com/discord-jda/JDA) for the excellent Discord API wrapper
- [Spring Boot](https://spring.io/projects/spring-boot) for the robust backend framework
- [React](https://react.dev/) for the frontend framework
- All [contributors](https://github.com/wraithfive/playbot/contributors) who have helped improve this project

---

**Built with ❤️ using Java and React**

**Repository:** https://github.com/wraithfive/playbot
