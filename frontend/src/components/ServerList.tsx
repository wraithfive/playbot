import { useQuery } from '@tanstack/react-query';
import { serverApi } from '../api/client';
import { useNavigate } from 'react-router-dom';
import type { GuildInfo } from '../types';
import { useState } from 'react';

export default function ServerList() {
  const navigate = useNavigate();
  const [invitingGuildId, setInvitingGuildId] = useState<string | null>(null);
  const { data: servers, isLoading, error } = useQuery({
    queryKey: ['servers'],
    queryFn: async () => {
      const response = await serverApi.getServers();
      return response.data;
    },
  });

  const handleInviteBot = async (guildId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      setInvitingGuildId(guildId);
      const response = await serverApi.getBotInviteUrl(guildId);
      // Open Discord OAuth in new window
      window.open(response.data.inviteUrl, '_blank');
    } catch (err) {
      console.error('Failed to get bot invite URL:', err);
      alert('Failed to generate invite link. Please try again.');
    } finally {
      setInvitingGuildId(null);
    }
  };

  if (isLoading) {
    return <div className="loading">Loading servers...</div>;
  }

  if (error) {
    return (
      <div className="error">
        <h2>Error loading servers</h2>
        <p>Please make sure you're logged in and try again.</p>
      </div>
    );
  }

  const availableServers = servers?.filter((s: GuildInfo) => s.botIsPresent) || [];
  const unavailableServers = servers?.filter((s: GuildInfo) => !s.botIsPresent) || [];

  return (
    <div className="server-list-container">
      <h1>Your Servers</h1>
      <p className="subtitle">Select a server to manage gacha roles</p>

      {availableServers.length > 0 && (
        <div className="servers-section">
          <h2>Available Servers</h2>
          <div className="server-grid">
            {availableServers.map((server: GuildInfo) => (
              <div
                key={server.id}
                className="server-card"
                onClick={() => navigate(`/servers/${server.id}`)}
              >
                {server.iconUrl ? (
                  <img src={server.iconUrl} alt={server.name} className="server-icon" />
                ) : (
                  <div className="server-icon-placeholder">
                    {server.name.charAt(0).toUpperCase()}
                  </div>
                )}
                <h3>{server.name}</h3>
              </div>
            ))}
          </div>
        </div>
      )}

      {unavailableServers.length > 0 && (
        <div className="servers-section">
          <h2>Add Bot to These Servers</h2>
          <p className="section-description">
            Playbot isn't in these servers yet. Click "Invite Bot" to add it and start managing gacha roles.
          </p>
          <div className="info-notice">
            <h3>ℹ️ Important: After the bot joins your server</h3>
            <p>
              You'll need to position the bot's role correctly in Discord Server Settings → Roles.
              Drag the Playbot role <strong>ABOVE all gacha roles</strong> or the bot won't be able to assign them.
            </p>
          </div>
          <div className="server-grid">
            {unavailableServers.map((server: GuildInfo) => (
              <div key={server.id} className="server-card server-card-invite">
                {server.iconUrl ? (
                  <img src={server.iconUrl} alt={server.name} className="server-icon" />
                ) : (
                  <div className="server-icon-placeholder">
                    {server.name.charAt(0).toUpperCase()}
                  </div>
                )}
                <h3>{server.name}</h3>
                <p className="bot-status">Bot not present</p>
                <button
                  className="btn btn-primary invite-btn"
                  onClick={(e) => handleInviteBot(server.id, e)}
                  disabled={invitingGuildId === server.id}
                >
                  {invitingGuildId === server.id ? 'Opening...' : 'Invite Bot'}
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {servers && servers.length === 0 && (
        <div className="empty-state">
          <h2>No Servers Found</h2>
          <p>You don't have administrator permissions on any servers.</p>
        </div>
      )}
    </div>
  );
}
