/**
 * ⚠️ PRIVACY POLICY TEMPLATE
 *
 * This is a template file. If you're self-hosting Playbot, you should:
 * 1. Copy this file to PrivacyPolicy.tsx
 * 2. Replace all [PLACEHOLDERS] with your information
 * 3. Consult with a lawyer to ensure compliance with your jurisdiction
 * 4. Update the "Last Updated" date
 *
 * This template is provided as-is with no legal warranty.
 * See: https://github.com/wraith-five/playbot for more information
 */

export default function PrivacyPolicy() {
  return (
    <div className="legal-page">
      <h1>Privacy Policy</h1>
  <p className="last-updated">Last Updated: October 20, 2025</p>

      <section>
        <h2>1. Introduction</h2>
        <p>
          This Privacy Policy explains how [YOUR NAME/ORGANIZATION] ("we", "us", or "our") collects, uses, and protects
          your information when you use our Playbot Discord bot, whether you are a server administrator using
          the admin panel or a regular user using bot commands in Discord servers.
        </p>
      </section>

      <section>
        <h2>2. Who This Policy Applies To</h2>
        <p>This policy applies to:</p>
        <ul>
          <li><strong>Server Administrators:</strong> Users who invite the bot and use the admin panel</li>
          <li><strong>Server Members:</strong> Users who interact with the bot through Discord commands (e.g., /roll)</li>
        </ul>
      </section>

      <section>
        <h2>3. Information We Collect</h2>
        <h3>3.1 For Server Administrators</h3>
        <p>When you use the admin panel, we collect:</p>
        <ul>
          <li>Discord User ID (for authentication)</li>
          <li>Username and avatar (for display in the admin panel)</li>
          <li>Server/Guild IDs where you have admin permissions</li>
          <li>Server names and icons (for display purposes)</li>
          <li>OAuth2 access tokens (temporarily, for authentication)</li>
          <li>OAuth2 refresh tokens (securely stored, for persistent login)</li>
        </ul>

        <h3>3.2 For All Users (Including Server Members)</h3>
        <p>When you use bot commands in Discord, we collect:</p>
        <ul>
          <li>Discord User ID (to track roll cooldowns and assign roles)</li>
          <li>Username (for bot response messages)</li>
          <li>Current roles (to manage gacha role assignments)</li>
          <li>Command usage data (which commands you use and when)</li>
        </ul>

        <h3>3.3 Server-Level Data</h3>
        <p>For servers where the bot is present, we collect:</p>
        <ul>
          <li>Role information (names, colors, positions) for gacha role management</li>
          <li>Channel IDs where commands are used (to respond in the correct channel)</li>
        </ul>

        <h3>3.4 What We Do NOT Collect</h3>
        <p>We explicitly do NOT collect:</p>
        <ul>
          <li>Message content (except the command itself, e.g., "/roll")</li>
          <li>Private/direct messages</li>
          <li>Voice data or activity</li>
          <li>Messages in channels where the bot is not used</li>
          <li>Email addresses or phone numbers</li>
        </ul>
      </section>

      <section>
        <h2>4. How We Use Your Information</h2>
        <p>We use the collected information to:</p>
        <ul>
          <li>Provide and maintain the Playbot service for all users</li>
          <li>Authenticate server administrators in the admin panel</li>
          <li>Manage gacha roles in Discord servers (creating, assigning, removing)</li>
          <li>Track daily roll cooldowns to enforce once-per-day rolling</li>
          <li>Respond to bot commands in the correct Discord channels</li>
          <li>Prevent spam and abuse of bot commands</li>
          <li>Improve and debug the bot functionality</li>
          <li>Ensure security and prevent unauthorized access</li>
        </ul>
      </section>

      <section>
        <h2>5. Data Storage and Security</h2>
        <h3>5.1 Data Retention</h3>
        <p>
          We store minimal data required for bot operation:
        </p>
        <ul>
          <li><strong>Roll Cooldowns:</strong> Stored in a database to persist across bot restarts. This includes your Discord User ID, Server ID, username, and timestamp of your last roll. Cooldown data is retained indefinitely but only used to enforce the once-per-day roll limit.</li>
          <li><strong>Role Assignments:</strong> Managed by Discord; we only read current roles when needed. No role data is permanently stored by Playbot.</li>
          <li><strong>Admin Authentication:</strong> OAuth2 access tokens cached briefly (10 seconds) and then discarded. OAuth2 refresh tokens are stored securely in an encrypted database to allow persistent login for server administrators. Refresh tokens are only used to obtain new access tokens and are never shared with third parties.</li>
        </ul>
        <h3>5.4 Persistent Login and Session Management</h3>
        <p>
          If you log in to the admin panel, your session may remain valid for up to 30 days unless you log out or clear your cookies. We use secure session cookies and store OAuth2 refresh tokens in our database to maintain your login. Refresh tokens are used only to obtain new access tokens and are never shared with third parties. You can revoke access at any time by removing the bot from your server or revoking Playbot's access in your Discord Authorized Apps settings.
        </p>

        <p>
          We do NOT permanently store:
        </p>
        <ul>
          <li>Personal messages or server conversation data</li>
          <li>Command history beyond temporary logs</li>
          <li>User profiles or personal information</li>
        </ul>

        <h3>5.2 Where Data Is Stored</h3>
        <p>
          Data is stored on [YOUR HOSTING PROVIDER]:
        </p>
        <ul>
          <li><strong>Location:</strong> Stored on servers hosted by [HOSTING PROVIDER] in [LOCATION/COUNTRY]</li>
          <li><strong>Database:</strong> Stored in a secure database on our application server</li>
          <li><strong>Third-Party Access:</strong> [HOSTING PROVIDER] provides the infrastructure, but does not have access to bot data. Data is not shared with other third parties except as required to operate the Discord bot.</li>
          <li><strong>Backup:</strong> Database backups may be created for disaster recovery purposes and stored on the same infrastructure</li>
        </ul>

        <h3>5.3 Security Measures</h3>
        <p>
          We implement reasonable security measures to protect your data, including:
        </p>
        <ul>
          <li>Secure authentication using Discord OAuth2</li>
          <li>HTTPS encryption for all web traffic</li>
          <li>Industry-standard cloud security provided by [HOSTING PROVIDER]</li>
          <li>Access controls limiting who can manage servers</li>
          <li>Permission verification before role assignments</li>
          <li>Regular security updates and monitoring</li>
        </ul>
      </section>

      <section>
        <h2>6. Data Sharing</h2>
        <p>
          We do not sell, trade, or rent your personal information to third parties. We only share data with:
        </p>
        <ul>
          <li><strong>Discord:</strong> As required to operate the bot through their API (user IDs, role data, etc.)</li>
          <li><strong>[HOSTING PROVIDER]:</strong> As our hosting provider, [HOSTING PROVIDER] hosts the infrastructure where data is stored, but does not have access to or use your data</li>
          <li><strong>Law Enforcement:</strong> Only when legally required by valid court orders or legal processes</li>
        </ul>
        <p>
          We do NOT share your data with advertisers, marketing companies, or data brokers.
        </p>
      </section>

      <section>
        <h2>7. Your Rights</h2>
        <h3>7.1 For All Users (Server Members)</h3>
        <p>If you use the bot in a Discord server, you have the right to:</p>
        <ul>
          <li><strong>Stop using the bot:</strong> Simply don't use bot commands (e.g., /roll)</li>
          <li><strong>Request data deletion:</strong> Contact your server administrator to remove the bot</li>
          <li><strong>View your roles:</strong> Check your Discord profile to see assigned gacha roles</li>
          <li><strong>Remove roles:</strong> Ask a server admin or use Discord's role management</li>
        </ul>

        <h3>7.2 For Server Administrators</h3>
        <p>If you manage servers with the bot, you have the right to:</p>
        <ul>
          <li><strong>Access your data:</strong> View it through the admin panel</li>
          <li><strong>Revoke access:</strong> Through Discord's User Settings → Authorized Apps</li>
          <li><strong>Remove the bot:</strong> Kick it from your server at any time (data automatically cleared)</li>
          <li><strong>Control member data:</strong> Manage roles for server members through the admin panel</li>
        </ul>

        <h3>7.3 Data Deletion</h3>
        <p>
          To delete your data:
        </p>
        <ul>
          <li><strong>Server Members:</strong> Your cooldown data (User ID, username, last roll time) is stored permanently in the database but only used to enforce roll limits. To request deletion, contact the bot operator at [YOUR CONTACT INFO].</li>
          <li><strong>Administrators:</strong> Remove the bot from your server; all cooldown data for that server can be deleted. Use the "Remove Bot" button in the admin panel or kick the bot from Discord Server Settings.</li>
          <li><strong>Both:</strong> Roles assigned by the bot remain on Discord and must be removed through Discord's interface</li>
          <li><strong>Complete Deletion:</strong> For complete deletion of your cooldown data across all servers, contact [YOUR CONTACT INFO]</li>
        </ul>
      </section>

      <section>
        <h2>8. Children's Privacy</h2>
        <p>
          Our Service is intended for users aged 13 and older, consistent with Discord's Terms of Service.
          We do not knowingly collect personal information from children under 13. If you believe a child
          under 13 has provided us with personal information, please contact us at [YOUR CONTACT INFO].
        </p>
      </section>

      <section>
        <h2>9. International Users</h2>
        <p>
          If you are accessing the Service from outside [YOUR COUNTRY/REGION], please be aware that your information
          may be transferred to, stored, and processed in [YOUR COUNTRY/REGION]. By using the Service, you consent
          to the transfer of your information to [YOUR COUNTRY/REGION] and agree to this Privacy Policy.
        </p>
      </section>

      <section>
        <h2>10. Changes to This Privacy Policy</h2>
        <p>
          We may update this Privacy Policy from time to time. We will notify you of material changes by:
        </p>
        <ul>
          <li>Updating the "Last Updated" date at the top of this policy</li>
          <li>Posting an announcement in Discord servers where the bot is present (for major changes)</li>
          <li>Sending a notification through the admin panel (for administrators)</li>
        </ul>
        <p>
          Your continued use of the Service after changes constitutes acceptance of the updated Privacy Policy.
        </p>
      </section>

      <section>
        <h2>11. Contact Us</h2>
        <p>
          If you have questions about this Privacy Policy or how we handle your data, you can:
        </p>
        <ul>
          <li>Email: [YOUR EMAIL]</li>
          <li>GitHub Issues: [YOUR GITHUB REPO URL]</li>
          <li>Discord: [YOUR DISCORD SERVER IF APPLICABLE]</li>
        </ul>
      </section>
    </div>
  );
}
