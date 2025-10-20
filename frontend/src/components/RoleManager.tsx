import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { serverApi } from '../api/client';
import { useParams, useNavigate } from 'react-router-dom';
import type { GachaRoleInfo } from '../types';

const rarityEmojis: Record<string, string> = {
  common: '⚪',
  uncommon: '🟢',
  rare: '🔵',
  epic: '🟣',
  legendary: '🟡',
};

const rarityColors: Record<string, string> = {
  common: '#ffffff',
  uncommon: '#00ff00',
  rare: '#0099ff',
  epic: '#9933ff',
  legendary: '#ffcc00',
};

export default function RoleManager() {
  const { guildId } = useParams<{ guildId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploadMessage, setUploadMessage] = useState<string>('');
  const [showRemoveConfirm, setShowRemoveConfirm] = useState(false);

  const { data: server, isLoading: serverLoading } = useQuery({
    queryKey: ['server', guildId],
    queryFn: async () => {
      const response = await serverApi.getServer(guildId!);
      return response.data;
    },
    enabled: !!guildId,
  });

  const { data: roles, isLoading: rolesLoading, error } = useQuery({
    queryKey: ['roles', guildId],
    queryFn: async () => {
      const response = await serverApi.getRoles(guildId!);
      return response.data;
    },
    enabled: !!guildId,
  });

  const initDefaultsMutation = useMutation({
    mutationFn: () => serverApi.initializeDefaultRoles(guildId!),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['roles', guildId] });
      const result = response.data;
      setUploadMessage(`✓ Created ${result.successCount} roles. ${result.failureCount > 0 ? `Failed: ${result.failureCount}` : ''}`);
    },
    onError: () => {
      setUploadMessage('✗ Failed to initialize default roles');
    },
  });

  const uploadCsvMutation = useMutation({
    mutationFn: (file: File) => serverApi.uploadCsv(guildId!, file),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['roles', guildId] });
      const result = response.data;
      setUploadMessage(`✓ Created ${result.successCount} roles. ${result.failureCount > 0 ? `Failed: ${result.failureCount}` : ''}`);
      setSelectedFile(null);
    },
    onError: () => {
      setUploadMessage('✗ Failed to upload CSV');
    },
  });

  const removeBotMutation = useMutation({
    mutationFn: () => serverApi.removeBot(guildId!),
    onSuccess: () => {
      // Navigate back to server list after successful removal
      navigate('/');
    },
    onError: () => {
      alert('Failed to remove bot. Please try again or remove it manually from Discord Server Settings.');
      setShowRemoveConfirm(false);
    },
  });

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0]);
      setUploadMessage('');
    }
  };

  const handleUploadCsv = () => {
    if (selectedFile) {
      uploadCsvMutation.mutate(selectedFile);
    }
  };

  const handleDownloadExample = async () => {
    try {
      const response = await serverApi.downloadExampleCsv(guildId!);
      const blob = new Blob([response.data], { type: 'text/csv' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'example-roles.csv';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (error) {
      setUploadMessage('✗ Failed to download example CSV');
    }
  };

  if (serverLoading || rolesLoading) {
    return <div className="loading">Loading...</div>;
  }

  if (error) {
    return (
      <div className="error">
        <h2>Error loading roles</h2>
        <p>Please make sure you have permission to manage this server.</p>
        <button onClick={() => navigate('/')} className="btn btn-primary">
          Back to Servers
        </button>
      </div>
    );
  }

  // Group roles by rarity
  const rolesByRarity: Record<string, GachaRoleInfo[]> = {};
  const noRarityRoles: GachaRoleInfo[] = [];

  roles?.forEach((role: GachaRoleInfo) => {
    if (role.rarity) {
      const rarityKey = role.rarity.toLowerCase();
      if (!rolesByRarity[rarityKey]) {
        rolesByRarity[rarityKey] = [];
      }
      rolesByRarity[rarityKey].push(role);
    } else {
      noRarityRoles.push(role);
    }
  });

  const rarityOrder = ['legendary', 'epic', 'rare', 'uncommon', 'common'];

  return (
    <div className="role-manager-container">
      <div className="header">
        <button onClick={() => navigate('/')} className="btn btn-back">
          ← Back to Servers
        </button>
        <div className="header-info">
          <h1>{server?.name}</h1>
          <p className="role-count">{roles?.length || 0} gacha roles</p>
        </div>
        <button
          onClick={() => setShowRemoveConfirm(true)}
          className="btn btn-danger btn-remove"
          title="Remove bot from this server"
        >
          Remove Bot
        </button>
      </div>

      {/* Remove Bot Confirmation Modal */}
      {showRemoveConfirm && (
        <div className="modal-overlay" onClick={() => setShowRemoveConfirm(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h2>Remove Bot from Server?</h2>
            <p>Are you sure you want to remove Playbot from <strong>{server?.name}</strong>?</p>
            <p className="warning-text">
              ⚠️ This will remove the bot from your server. Users won't be able to use gacha commands until you re-invite it.
            </p>
            <div className="modal-actions">
              <button
                onClick={() => setShowRemoveConfirm(false)}
                className="btn btn-secondary"
                disabled={removeBotMutation.isPending}
              >
                Cancel
              </button>
              <button
                onClick={() => removeBotMutation.mutate()}
                className="btn btn-danger"
                disabled={removeBotMutation.isPending}
              >
                {removeBotMutation.isPending ? 'Removing...' : 'Yes, Remove Bot'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Discord API Limitations Notice */}
      <div className="info-notice">
        <h3>ℹ️ Discord API Limitations</h3>
        <p>
          The bot can create standard colored roles, but <strong>cannot create blended gradient or holographic roles</strong> due to Discord API restrictions.
          These special roles must be created manually in Discord Server Settings.
        </p>
        <p>
          However, the bot <strong>can see and assign</strong> manually-created blended/holographic roles if they follow the naming format: <code>gacha:rarity:Name</code>
        </p>
      </div>

      {/* Role Hierarchy Warning */}
      <div className="warning-notice">
        <h3>⚠️ IMPORTANT: Role Hierarchy Setup</h3>
        <p>
          <strong>The bot's role MUST be positioned ABOVE all gacha roles</strong> in your Discord Server Settings → Roles.
        </p>
        <p>
          Discord prevents bots from managing roles that are higher than their own role in the list.
          If users get permission errors when using <code>/roll</code>, this is almost always the cause.
        </p>
        <p>
          <strong>How to fix:</strong> Go to Server Settings → Roles, find the Playbot role, and drag it ABOVE all <code>gacha:</code> roles.
        </p>
      </div>

      {/* Role Management Actions */}
      {(!roles || roles.length === 0) && (
        <div className="role-actions">
          <h3>Get Started</h3>

          <div className="action-card">
            <h4>Option 1: Initialize Default Roles</h4>
            <p>Create 10 pre-configured roles with beautiful colors across all rarity tiers.</p>
            <button
              onClick={() => initDefaultsMutation.mutate()}
              disabled={initDefaultsMutation.isPending}
              className="btn btn-primary"
            >
              {initDefaultsMutation.isPending ? 'Creating...' : 'Initialize 10 Default Roles'}
            </button>
          </div>

          <div className="action-card">
            <h4>Option 2: Upload Custom Roles via CSV</h4>
            <p>Upload a CSV file with your custom role definitions (name, rarity, hex color).</p>
            <div className="csv-upload">
              <button onClick={handleDownloadExample} className="btn btn-secondary">
                📥 Download Example CSV
              </button>
              <input
                type="file"
                accept=".csv"
                onChange={handleFileChange}
                id="csv-upload"
                style={{ display: 'none' }}
              />
              <label htmlFor="csv-upload" className="btn btn-secondary">
                📄 Choose CSV File
              </label>
              {selectedFile && (
                <div className="file-selected">
                  <span>{selectedFile.name}</span>
                  <button
                    onClick={handleUploadCsv}
                    disabled={uploadCsvMutation.isPending}
                    className="btn btn-primary"
                  >
                    {uploadCsvMutation.isPending ? 'Uploading...' : 'Upload & Create Roles'}
                  </button>
                </div>
              )}
            </div>
          </div>

          {uploadMessage && (
            <div className={`upload-message ${uploadMessage.startsWith('✓') ? 'success' : 'error'}`}>
              {uploadMessage}
            </div>
          )}
        </div>
      )}

      {/* Existing Roles Display */}
      {roles && roles.length > 0 && (
        <>
          <div className="role-actions-compact">
            <button
              onClick={handleDownloadExample}
              className="btn btn-secondary btn-sm"
            >
              📥 Download Example CSV
            </button>
            <div className="csv-upload-inline">
              <input
                type="file"
                accept=".csv"
                onChange={handleFileChange}
                id="csv-upload-inline"
                style={{ display: 'none' }}
              />
              <label htmlFor="csv-upload-inline" className="btn btn-secondary btn-sm">
                📄 Upload CSV
              </label>
              {selectedFile && (
                <>
                  <span className="file-name">{selectedFile.name}</span>
                  <button
                    onClick={handleUploadCsv}
                    disabled={uploadCsvMutation.isPending}
                    className="btn btn-primary btn-sm"
                  >
                    {uploadCsvMutation.isPending ? 'Uploading...' : 'Create'}
                  </button>
                </>
              )}
            </div>
            {uploadMessage && (
              <div className={`upload-message-inline ${uploadMessage.startsWith('✓') ? 'success' : 'error'}`}>
                {uploadMessage}
              </div>
            )}
          </div>

          <div className="roles-content">
            {rarityOrder.map((rarity) => {
              const rarityRoles = rolesByRarity[rarity];
              if (!rarityRoles || rarityRoles.length === 0) return null;

              return (
                <div key={rarity} className="rarity-section">
                  <h2 className="rarity-header" style={{ color: rarityColors[rarity] }}>
                    {rarityEmojis[rarity]} {rarity.toUpperCase()}
                    <span className="rarity-count">({rarityRoles.length})</span>
                  </h2>
                  <div className="role-grid">
                    {rarityRoles.map((role: GachaRoleInfo) => (
                      <div
                        key={role.id}
                        className="role-card"
                        style={{ borderLeftColor: role.colorHex || '#666' }}
                      >
                        <div className="role-color-preview" style={{ backgroundColor: role.colorHex || '#666' }} />
                        <div className="role-info">
                          <h3 className="role-name">{role.displayName}</h3>
                          <p className="role-hex">{role.colorHex || 'No color'}</p>
                          <p className="role-full-name">{role.fullName}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}

            {noRarityRoles.length > 0 && (
              <div className="rarity-section">
                <h2 className="rarity-header">No Rarity <span className="rarity-count">({noRarityRoles.length})</span></h2>
                <div className="role-grid">
                  {noRarityRoles.map((role: GachaRoleInfo) => (
                    <div
                      key={role.id}
                      className="role-card"
                      style={{ borderLeftColor: role.colorHex || '#666' }}
                    >
                      <div className="role-color-preview" style={{ backgroundColor: role.colorHex || '#666' }} />
                      <div className="role-info">
                        <h3 className="role-name">{role.displayName}</h3>
                        <p className="role-hex">{role.colorHex || 'No color'}</p>
                        <p className="role-full-name">{role.fullName}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
