# Security Policy

## Supported Versions

Only the latest version of Playbot is currently supported with security updates.

| Version | Supported          |
| ------- | ------------------ |
| Latest  | :white_check_mark: |
| < Latest| :x:                |

## Reporting a Vulnerability

We take the security of Playbot seriously. If you believe you have found a security vulnerability, please follow these steps:

1. **Do NOT open a public issue** for the vulnerability
2. Report the vulnerability through [GitHub's private vulnerability reporting](https://github.com/wraithfive/playbot/security/advisories/new) including:
   - A description of the vulnerability
   - Steps to reproduce the issue
   - Possible impacts of the vulnerability
   - Any potential mitigations you've identified

   If you don't have a GitHub account, you can create one for free to submit the report.

### What to Expect

- **Initial Response:** You will receive an initial response within 48 hours acknowledging receipt of your report
- **Updates:** We will keep you informed of our progress in addressing the vulnerability
- **Resolution:** Once fixed, we will notify you and discuss coordinated disclosure

### Disclosure Policy

- We practice responsible disclosure
- Please allow us time to address the vulnerability before public disclosure
- We will credit you for the discovery unless you request otherwise

## Security Best Practices

### For Server Administrators

1. **Bot Token Security**
   - Never share your Discord bot token
   - Rotate tokens if they may have been compromised
   - Use environment variables for sensitive configuration

2. **Role Management**
   - Ensure the bot's role is positioned correctly in the role hierarchy
   - Regularly audit role permissions
   - Remove unused gacha roles using the admin panel

3. **Access Control**
   - Only grant bot admin access to trusted users
   - Regularly review who has access to the admin panel
   - Use strong passwords for admin accounts

### For Developers

1. **Environment Security**
   - Never commit .env files or sensitive credentials
   - Use the provided .env.example as a template
   - Keep all dependencies updated

2. **Code Security**
   - Follow secure coding practices
   - Use input validation for all user inputs
   - Handle errors appropriately without exposing sensitive information

3. **OAuth2 Security**
   - Use HTTPS in production
   - Keep client secrets secure
   - Validate all OAuth2 tokens and requests

## Security Updates

Security updates will be released as soon as possible when vulnerabilities are discovered. Updates will be published:

1. As new releases in the GitHub repository
2. Through our announcement channels (if critical)
3. Via direct communication to known affected parties

We recommend watching the repository for notifications about new security releases.