/**
 * ⚠️ TERMS OF SERVICE TEMPLATE
 *
 * This is a template file. If you're self-hosting Playbot, you should:
 * 1. Copy this file to TermsOfService.tsx
 * 2. Replace all [PLACEHOLDERS] with your information
 * 3. Consult with a lawyer to ensure compliance with your jurisdiction  
 * 4. Update the "Last Updated" date
 *
 * This template is provided as-is with no legal warranty.
 * See: https://github.com/wraith-five/playbot for more information
 */

export default function TermsOfService() {
  return (
    <div className="legal-page">
      <h1>Terms of Service</h1>
      <p className="last-updated">Last Updated: [DATE]</p>

      <section>
        <h2>1. Acceptance of Terms</h2>
        <p>
          By using Playbot (the "Service"), whether as a server administrator or as a server member
          using bot commands, you agree to be bound by these Terms of Service ("Terms").
          If you do not agree to these Terms, do not use the Service.
        </p>
      </section>

      <section>
        <h2>2. Description of Service</h2>
        <p>
          Playbot is a Discord bot that provides a gacha-style role system where users can roll for
          random colored name roles. The Service includes:
        </p>
        <ul>
          <li><strong>For All Users:</strong> Discord bot commands (e.g., !roll, !help) for rolling and viewing roles</li>
          <li><strong>For Server Admins:</strong> Web-based admin panel for role management</li>
          <li><strong>For Server Admins:</strong> Role creation and configuration tools</li>
        </ul>
      </section>

      <section>
        <h2>3. User Eligibility</h2>
        <p>
          You must be at least 13 years old (or the minimum age required in your jurisdiction) to use
          this Service. By using the Service (whether as an admin or regular user), you represent that
          you meet this age requirement and comply with Discord's Terms of Service.
        </p>
      </section>

      <section>
        <h2>4. Server Administrator Responsibilities</h2>
        <p>If you invite Playbot to your Discord server, you agree to:</p>
        <ul>
          <li>Grant only necessary permissions to the bot</li>
          <li>Monitor bot usage and user behavior in your server</li>
          <li>Ensure your server members comply with these Terms</li>
          <li>Not use the bot to violate Discord's Terms of Service</li>
          <li>Configure roles responsibly and appropriately</li>
        </ul>
      </section>

      <section>
        <h2>5. Server Member Responsibilities</h2>
        <p>If you use Playbot commands in a Discord server, you agree to:</p>
        <ul>
          <li>Follow the server's rules and the bot's usage guidelines</li>
          <li>Respect the daily roll cooldown (once per 24 hours)</li>
          <li>Not spam or abuse bot commands</li>
          <li>Report bugs or issues to server administrators</li>
          <li>Understand that roles are assigned randomly and cannot be guaranteed</li>
        </ul>
      </section>

      <section>
        <h2>6. Acceptable Use (All Users)</h2>
        <p>Whether you are an administrator or regular user, you agree NOT to:</p>
        <ul>
          <li>Abuse or spam bot commands</li>
          <li>Attempt to exploit, hack, or disrupt the Service</li>
          <li>Use the bot for illegal activities or harassment</li>
          <li><strong>(Admins only)</strong> Create roles with offensive, hateful, or inappropriate names</li>
          <li>Circumvent rate limits or cooldowns through technical means</li>
          <li>Impersonate others or misrepresent your affiliation with the Service</li>
          <li>Use automated tools, bots, or scripts to interact with Playbot</li>
          <li>Share or exploit bugs that give unfair advantages</li>
        </ul>
      </section>

      <section>
        <h2>7. Bot Permissions</h2>
        <p>Playbot requires the following Discord permissions to function:</p>
        <ul>
          <li>
            <strong>Manage Roles:</strong> To create and assign gacha roles
          </li>
          <li>
            <strong>View Channels:</strong> To see channels and respond to commands
          </li>
          <li>
            <strong>Send Messages:</strong> To send roll results and help information
          </li>
        </ul>
        <p>
          We will only use these permissions for their intended purposes. We will not read private
          messages or collect data beyond what is necessary for bot operation.
        </p>
      </section>

      <section>
        <h2>8. Data Collection and Storage</h2>
        <p>By using Playbot, you acknowledge and agree that:</p>
        <ul>
          <li>
            <strong>Cooldown Data:</strong> Your Discord User ID, username, server ID, and last roll timestamp are stored in a database to enforce the daily roll limit. This data persists across bot restarts.
          </li>
          <li>
            <strong>Data Retention:</strong> Cooldown data is retained indefinitely unless you request deletion or the bot is removed from the server.
          </li>
          <li>
            <strong>Data Location:</strong> Data is stored on cloud infrastructure ([YOUR HOSTING PROVIDER]) in [LOCATION/COUNTRY] and is not shared with third parties except as disclosed in our Privacy Policy.
          </li>
          <li>
            <strong>Persistent Login:</strong> If you log in to the admin panel, your session may remain valid for up to 30 days unless you log out or clear your cookies. We use secure session cookies and store OAuth2 refresh tokens in our database to maintain your login and avoid repeated Discord authentication. You can revoke access at any time by removing the bot from your server or revoking Playbot's access in your Discord Authorized Apps settings.
          </li>
          <li>
            <strong>Privacy:</strong> For complete details on data collection, storage, and your rights, see our <a href="/privacy">Privacy Policy</a>.
          </li>
        </ul>
      </section>

      <section>
        <h2>9. Service Availability</h2>
        <p>
          We strive to keep Playbot available 24/7, but we do not guarantee uninterrupted service.
          The Service may be temporarily unavailable due to:
        </p>
        <ul>
          <li>Maintenance or updates</li>
          <li>Technical issues or outages</li>
          <li>Discord API limitations or outages</li>
          <li>Force majeure events</li>
        </ul>
      </section>

      <section>
        <h2>10. Disclaimer of Warranties</h2>
        <p>
          THE SERVICE IS PROVIDED "AS IS" WITHOUT WARRANTIES OF ANY KIND, EXPRESS OR IMPLIED,
          INCLUDING BUT NOT LIMITED TO WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
          PURPOSE, OR NON-INFRINGEMENT.
        </p>
        <p>We do not warrant that:</p>
        <ul>
          <li>The Service will be error-free or uninterrupted</li>
          <li>Defects will be corrected</li>
          <li>The Service is free of viruses or harmful components</li>
          <li>Results from using the Service will meet your requirements</li>
        </ul>
      </section>

      <section>
        <h2>11. Limitation of Liability</h2>
        <p>
          TO THE MAXIMUM EXTENT PERMITTED BY LAW, WE SHALL NOT BE LIABLE FOR ANY INDIRECT,
          INCIDENTAL, SPECIAL, CONSEQUENTIAL, OR PUNITIVE DAMAGES, OR ANY LOSS OF PROFITS OR
          REVENUES, WHETHER INCURRED DIRECTLY OR INDIRECTLY, OR ANY LOSS OF DATA, USE, GOODWILL,
          OR OTHER INTANGIBLE LOSSES RESULTING FROM:
        </p>
        <ul>
          <li>Your use or inability to use the Service</li>
          <li>Unauthorized access to or alteration of your data</li>
          <li>Any conduct or content of third parties on the Service</li>
          <li>Any content obtained from the Service</li>
        </ul>
      </section>

      <section>
        <h2>12. Modifications to Service</h2>
        <p>
          We reserve the right to modify, suspend, or discontinue the Service (or any part thereof)
          at any time, with or without notice. We will not be liable to you or any third party for
          any modification, suspension, or discontinuation of the Service.
        </p>
      </section>

      <section>
        <h2>13. Termination</h2>
        <p>We may terminate or suspend your access to the Service immediately, without prior notice or liability, for any reason, including if you breach these Terms.</p>
        <p>You may terminate your use of the Service at any time by:</p>
        <ul>
          <li>Removing the bot from your Discord server</li>
          <li>Revoking access through Discord's Authorized Apps settings</li>
          <li>Ceasing to use bot commands</li>
        </ul>
      </section>

      <section>
        <h2>14. Discord Terms of Service</h2>
        <p>
          Your use of Playbot is also subject to Discord's Terms of Service. You must comply with
          Discord's Terms and Community Guidelines. Violation of Discord's Terms may result in
          termination of your access to Playbot.
        </p>
      </section>

      <section>
        <h2>15. Intellectual Property</h2>
        <p>
          The Service and its original content, features, and functionality are owned by Playbot
          and are protected by international copyright, trademark, and other intellectual property laws.
        </p>
      </section>

      <section>
        <h2>16. Changes to Terms</h2>
        <p>
          We reserve the right to modify these Terms at any time. We will notify users of material
          changes by updating the "Last Updated" date. Your continued use of the Service after
          changes constitutes acceptance of the updated Terms.
        </p>
      </section>

      <section>
        <h2>17. Governing Law</h2>
        <p>
          These Terms shall be governed by and construed in accordance with applicable laws,
          without regard to conflict of law provisions.
        </p>
      </section>

      <section>
        <h2>18. Severability</h2>
        <p>
          If any provision of these Terms is found to be unenforceable or invalid, that provision
          will be limited or eliminated to the minimum extent necessary, and the remaining provisions
          will remain in full force and effect.
        </p>
      </section>

      <section>
        <h2>19. Contact Information</h2>
        <p>
          For questions about these Terms of Service, you can contact us by:
        </p>
        <ul>
          <li>Removing the bot from your server if you disagree with the Terms</li>
          <li>Opening an issue on GitHub (if applicable)</li>
        </ul>
      </section>
    </div>
  );
}
