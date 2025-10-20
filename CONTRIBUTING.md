# Contributing to Playbot

Thank you for your interest in contributing to Playbot! This document provides guidelines for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [How to Contribute](#how-to-contribute)
- [Development Setup](#development-setup)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [License](#license)

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md). Please read it before contributing.

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone git@github.com:YOUR-USERNAME/playbot.git
   cd playbot
   ```
3. **Add upstream remote**:
   ```bash
   git remote add upstream git@github.com:wraithfive/playbot.git
   ```

## How to Contribute

### Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates. When creating a bug report, include:

- **Clear title and description**
- **Steps to reproduce** the issue
- **Expected vs actual behavior**
- **Screenshots** if applicable
- **Environment details** (OS, Java version, Node version)
- **Relevant logs** from the bot or browser console

Use the bug report template when available.

### Suggesting Features

Feature suggestions are welcome! Please:

- **Check existing issues** to avoid duplicates
- **Describe the feature** in detail
- **Explain the use case** - why is this needed?
- **Consider implementation** - optional but helpful

### Code Contributions

1. **Find or create an issue** describing what you'll work on
2. **Comment on the issue** to let others know you're working on it
3. **Create a feature branch** from `master`:
   ```bash
   git checkout -b feature/your-feature-name
   ```
4. **Make your changes** following our [coding standards](#coding-standards)
5. **Test your changes** thoroughly
6. **Commit with clear messages** (see below)
7. **Push to your fork** and create a Pull Request

## Development Setup

### Prerequisites

- Java 21 (LTS) or higher
- Maven 3.6+
- Node.js 18+
- Discord Bot Token ([Discord Developer Portal](https://discord.com/developers/applications))
- Discord OAuth2 credentials

### Setup Instructions

1. **Copy environment variables**:
   ```bash
   cp .env.example .env
   ```

2. **Add your Discord credentials** to `.env`:
   ```env
   DISCORD_TOKEN=your_bot_token_here
   DISCORD_CLIENT_ID=your_client_id_here
   DISCORD_CLIENT_SECRET=your_client_secret_here
   ADMIN_PANEL_URL=http://localhost:8080
   ```

3. **Build the project**:
   ```bash
   chmod +x build.sh
   ./build.sh
   ```

4. **Run the bot**:
   ```bash
   chmod +x start.sh
   ./start.sh
   ```

See [README.md](README.md) for detailed setup instructions.

## Pull Request Process

1. **Update documentation** if you've changed APIs or added features
2. **Add tests** for new functionality when applicable
3. **Ensure all tests pass**: `mvn test`
4. **Update CHANGELOG** if applicable (in PR description)
5. **Follow the PR template** when creating your pull request
6. **Request review** from maintainers
7. **Address feedback** promptly and professionally

### PR Requirements

- ✅ Code builds successfully (`./build.sh`)
- ✅ Tests pass (`mvn test`)
- ✅ Code follows project style (see below)
- ✅ Commits are clear and descriptive
- ✅ Documentation updated if needed
- ✅ No secrets or credentials committed

## Coding Standards

### Java (Backend)

- **Java 21** features encouraged (records, pattern matching, etc.)
- **Follow existing style**: 4 spaces, no tabs
- **Use meaningful names** for variables, methods, classes
- **Add JavaDoc** for public methods and classes
- **Keep methods focused** - one responsibility per method
- **Use SLF4J** for logging, not System.out
- **Handle exceptions** properly - don't swallow them

Example:
```java
/**
 * Checks if a user can roll based on cooldown.
 *
 * @param userId Discord user ID
 * @param guildId Discord guild ID
 * @return true if user can roll, false otherwise
 */
public boolean canUserRoll(String userId, String guildId) {
    Optional<UserCooldown> cooldown = cooldownRepository.findByUserIdAndGuildId(userId, guildId);
    return cooldown.isEmpty() || isExpired(cooldown.get());
}
```

### TypeScript/React (Frontend)

- **TypeScript strict mode** - no `any` types
- **Functional components** with hooks
- **Props interfaces** for all components
- **Follow existing style**: 2 spaces, semicolons
- **Use meaningful names**
- **Keep components small** and focused
- **Use React Query** for API calls
- **CSS in App.css** - no inline styles

Example:
```typescript
interface RoleCardProps {
  role: GachaRoleInfo;
  onClick?: () => void;
}

export default function RoleCard({ role, onClick }: RoleCardProps) {
  return (
    <div className="role-card" onClick={onClick}>
      <div className="role-color-preview" style={{ backgroundColor: role.hexColor }} />
      <div className="role-info">
        <h3>{role.displayName}</h3>
        <p>{role.rarity}</p>
      </div>
    </div>
  );
}
```

### Commit Messages

Use clear, descriptive commit messages:

- **Use imperative mood**: "Add feature" not "Added feature"
- **First line <= 72 characters**
- **Reference issues**: "Fix role assignment bug (#123)"
- **Explain why, not just what**

Good examples:
```
Add database persistence for roll cooldowns

- Replace in-memory map with H2 database
- Create UserCooldown entity and repository
- Update SlashCommandHandler to use database
- Fixes #42
```

```
Fix role hierarchy permission error

Users were getting errors when bot role was positioned
below gacha roles. Added detailed error messages to help
admins fix the issue.

Fixes #78
```

### Testing

- **Write tests** for new features when possible
- **Don't break existing tests**
- **Test manually** in Discord before submitting PR
- **Test edge cases**: empty inputs, rate limits, permissions

## Project Structure

```
playbot/
├── src/main/java/com/discordbot/     # Java backend
│   ├── entity/                        # JPA entities
│   ├── repository/                    # Data repositories
│   ├── web/                          # Web controllers & services
│   ├── Bot.java                      # Main application
│   └── SlashCommandHandler.java      # Discord command handler
├── frontend/src/                      # React frontend
│   ├── components/                   # React components
│   ├── api/                          # API client
│   └── types/                        # TypeScript types
├── src/main/resources/               # Configuration
└── frontend/public/                  # Static assets
```

## Questions or Need Help?

- **Open an issue** with the question label
- **Check existing issues** and documentation first
- **Be patient and respectful** - maintainers are volunteers

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).

## Recognition

Contributors will be recognized in:
- Git commit history
- GitHub contributors page
- ATTRIBUTIONS.md (for significant contributions)

Thank you for contributing to Playbot! 🎲
