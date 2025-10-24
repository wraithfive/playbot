import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { serverApi } from '../api/client';
import { useParams, useNavigate } from 'react-router-dom';
import type { GachaRoleInfo, BulkRoleCreationResult } from '../types';
import { useWebSocket } from '../hooks/useWebSocket';

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
  const [selectedRoleIds, setSelectedRoleIds] = useState<Set<string>>(new Set());
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [openRarity, setOpenRarity] = useState<string | null>(null);
  const [newRoleName, setNewRoleName] = useState<string>('');
  const [newRoleColor, setNewRoleColor] = useState<string>('');

  // Simple color name suggestion: pick nearest from a small palette
  const COMMON_COLORS: Array<{ name: string; hex: string }> = [
    { name: 'Black', hex: '#000000' },
    { name: 'White', hex: '#FFFFFF' },
    { name: 'Gray', hex: '#808080' },
    { name: 'Silver', hex: '#C0C0C0' },
    { name: 'Maroon', hex: '#800000' },
    { name: 'Red', hex: '#FF0000' },
    { name: 'Orange', hex: '#FFA500' },
    { name: 'Gold', hex: '#FFD700' },
    { name: 'Yellow', hex: '#FFFF00' },
    { name: 'Olive', hex: '#808000' },
    { name: 'Lime', hex: '#00FF00' },
    { name: 'Green', hex: '#008000' },
    { name: 'Teal', hex: '#008080' },
    { name: 'Cyan', hex: '#00FFFF' },
    { name: 'Sky Blue', hex: '#87CEEB' },
    { name: 'Dodger Blue', hex: '#1E90FF' },
    { name: 'Blue', hex: '#0000FF' },
    { name: 'Indigo', hex: '#4B0082' },
    { name: 'Purple', hex: '#800080' },
    { name: 'Violet', hex: '#EE82EE' },
    { name: 'Magenta', hex: '#FF00FF' },
    { name: 'Pink', hex: '#FFC0CB' },
    { name: 'Rose', hex: '#FF007F' },
    { name: 'Brown', hex: '#A52A2A' },
    { name: 'Chocolate', hex: '#D2691E' },
    { name: 'Tan', hex: '#D2B48C' },
  ];

  function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
    const cleaned = hex.trim().startsWith('#') ? hex.trim().slice(1) : hex.trim();
    if (!/^[0-9a-fA-F]{6}$/.test(cleaned)) return null;
    const r = parseInt(cleaned.slice(0, 2), 16);
    const g = parseInt(cleaned.slice(2, 4), 16);
    const b = parseInt(cleaned.slice(4, 6), 16);
    return { r, g, b };
  }

  function distanceSq(a: { r: number; g: number; b: number }, b: { r: number; g: number; b: number }): number {
    const dr = a.r - b.r;
    const dg = a.g - b.g;
    const db = a.b - b.b;
    return dr * dr + dg * dg + db * db;
  }

  function suggestColorName(hex: string): string | null {
    const rgb = hexToRgb(hex);
    if (!rgb) return null;
    let bestName = COMMON_COLORS[0].name;
    let bestDist = Number.POSITIVE_INFINITY;
    for (const c of COMMON_COLORS) {
      const base = hexToRgb(c.hex)!;
      const d = distanceSq(rgb, base);
      if (d < bestDist) {
        bestDist = d;
        bestName = c.name;
      }
    }
    return bestName;
  }

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

  const { data: hierarchyStatus } = useQuery({
    queryKey: ['hierarchy', guildId],
    queryFn: async () => {
      const response = await serverApi.checkRoleHierarchy(guildId!);
      return response.data;
    },
    enabled: !!guildId && !!roles && roles.length > 0,
  });

  // Listen for real-time role updates via WebSocket
  useWebSocket((message) => {
    if (message.type === 'ROLES_CHANGED' && message.guildId === guildId) {
      console.log('[RoleManager] Received ROLES_CHANGED event, refreshing roles...');
      queryClient.invalidateQueries({ queryKey: ['roles', guildId] });
      queryClient.invalidateQueries({ queryKey: ['server', guildId] });
    }
  });

  const initDefaultsMutation = useMutation<{ data: BulkRoleCreationResult }>({
    mutationFn: () => serverApi.initializeDefaultRoles(guildId!),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['roles', guildId] });
      queryClient.invalidateQueries({ queryKey: ['server', guildId] });
      queryClient.invalidateQueries({ queryKey: ['servers'] });
      const result = response.data;
      const parts = [`✓ Created ${result.successCount} roles`];
      if (result.skippedCount > 0) parts.push(`${result.skippedCount} already existed`);
      if (result.failureCount > 0) parts.push(`${result.failureCount} failed`);
      setUploadMessage(parts.join(', '));
    },
    onError: () => {
      setUploadMessage('✗ Failed to initialize default roles');
    },
  });

  const uploadCsvMutation = useMutation<{ data: BulkRoleCreationResult }, Error, File>({
    mutationFn: (file: File) => serverApi.uploadCsv(guildId!, file),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['roles', guildId] });
      queryClient.invalidateQueries({ queryKey: ['server', guildId] });
      queryClient.invalidateQueries({ queryKey: ['servers'] });
      const result = response.data;
      const parts = [`✓ Created ${result.successCount} roles`];
      if (result.skippedCount > 0) parts.push(`${result.skippedCount} already existed`);
      if (result.failureCount > 0) parts.push(`${result.failureCount} failed`);
      setUploadMessage(parts.join(', '));
      setSelectedFile(null);
    },
    onError: () => {
      setUploadMessage('✗ Failed to upload CSV');
    },
  });

  const removeBotMutation = useMutation({
    mutationFn: () => serverApi.removeBot(guildId!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['servers'] });
      queryClient.invalidateQueries({ queryKey: ['server', guildId] });
      queryClient.invalidateQueries({ queryKey: ['roles', guildId] });
      navigate('/');
    },
    onError: () => {
      alert('Failed to remove bot. Please try again or remove it manually from Discord Server Settings.');
      setShowRemoveConfirm(false);
    },
  });

  const bulkDeleteMutation = useMutation({
    mutationFn: (roleIds: string[]) => serverApi.bulkDeleteRoles(guildId!, roleIds),
    onSuccess: (response) => {
      const result = response.data;
      
      // Build detailed message
      let message = `✓ Deleted ${result.successCount} role${result.successCount !== 1 ? 's' : ''}`;
      
      if (result.failureCount > 0) {
        message += ` | ✗ ${result.failureCount} failed`;
        // Add first error as detail
        if (result.errors.length > 0) {
          const firstError = result.errors[0];
          // Extract just the error message part after "Role <id>: "
          const errorDetail = firstError.includes(': ') 
            ? firstError.split(': ').slice(1).join(': ')
            : firstError;
          message += `: ${errorDetail}`;
        }
      }
      
      setUploadMessage(message);
      setSelectedRoleIds(new Set());
      setShowDeleteConfirm(false);
      
      // Force refetch of roles to update UI
      queryClient.invalidateQueries({ queryKey: ['roles', guildId] });
      queryClient.invalidateQueries({ queryKey: ['server', guildId] });
      queryClient.invalidateQueries({ queryKey: ['servers'] });
    },
    onError: (error: any) => {
      const errorMsg = error?.response?.data?.message || error?.message || 'Failed to delete roles';
      setUploadMessage(`✗ ${errorMsg}`);
      setShowDeleteConfirm(false);
      
      // Still refresh to show current state
      queryClient.invalidateQueries({ queryKey: ['roles', guildId] });
    },
  });

  const createRoleMutation = useMutation({
    mutationFn: (payload: { name: string; rarity: string; colorHex: string }) =>
      serverApi.createRole(guildId!, payload),
    onSuccess: (response) => {
      const created = response.data;
      setUploadMessage(`✓ Created role ${created.displayName}`);
      setOpenRarity(null);
      setNewRoleName('');
      setNewRoleColor('');
      // Refresh lists
      queryClient.invalidateQueries({ queryKey: ['roles', guildId] });
      queryClient.invalidateQueries({ queryKey: ['server', guildId] });
    },
    onError: (error: any) => {
      const msg = error?.response?.data?.message || error?.message || 'Failed to create role';
      setUploadMessage(`✗ ${msg}`);
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
  a.download = 'gacha-roles-example.csv';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (error) {
      setUploadMessage('✗ Failed to download example CSV');
    }
  };

  const handleToggleRole = (roleId: string) => {
    setSelectedRoleIds(prev => {
      const newSet = new Set(prev);
      if (newSet.has(roleId)) {
        newSet.delete(roleId);
      } else {
        newSet.add(roleId);
      }
      return newSet;
    });
  };

  const handleSelectAll = () => {
    if (roles) {
      setSelectedRoleIds(new Set(roles.map(r => r.id)));
    }
  };

  const handleSelectNone = () => {
    setSelectedRoleIds(new Set());
  };

  const handleBulkDelete = () => {
    setShowDeleteConfirm(true);
  };

  const confirmBulkDelete = () => {
    bulkDeleteMutation.mutate(Array.from(selectedRoleIds));
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

  roles?.forEach((role: GachaRoleInfo) => {
    if (role.rarity) {
      const rarityKey = role.rarity.toLowerCase();
      if (!rolesByRarity[rarityKey]) {
        rolesByRarity[rarityKey] = [];
      }
      rolesByRarity[rarityKey].push(role);
    }
  });

  const rarityOrder = ['legendary', 'epic', 'rare', 'uncommon', 'common'];

  return (
    <div className="role-manager-container">
      <nav className="server-nav">
        <div className="server-nav-container">
          <div className="server-nav-left">
            <button onClick={() => navigate('/')} className="btn btn-back">
              ← Back to Servers
            </button>
            <div className="server-nav-tabs">
              <button className="server-nav-tab active">
                🎨 Gacha Config
              </button>
              <button
                onClick={() => navigate(`/servers/${guildId}/qotd`)}
                className="server-nav-tab"
              >
                💬 QOTD Config
              </button>
            </div>
          </div>
          <button
            onClick={() => setShowRemoveConfirm(true)}
            className="btn btn-danger"
            title="Remove bot from this server"
          >
            Remove Bot
          </button>
        </div>
      </nav>

      {/* Remove Bot Confirmation Modal */}
      {showRemoveConfirm && (
        <div className="modal-overlay" onClick={() => setShowRemoveConfirm(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h2>Remove Bot from Server?</h2>
            <p>Are you sure you want to remove Playbot from <strong>{server?.name}</strong>?</p>
            <p className="warning-text">
              ⚠️ This will remove the bot from your server. Users won't be able to use gacha commands until you re-invite it.
            </p>
            <div className="modal-buttons">
              <button
                onClick={() => setShowRemoveConfirm(false)}
                className="btn btn-secondary"
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

      {/* Bulk Delete Confirmation Modal */}
      {showDeleteConfirm && (
        <div className="modal-overlay" onClick={() => setShowDeleteConfirm(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h2>Delete Selected Roles?</h2>
            <p>Are you sure you want to delete <strong>{selectedRoleIds.size}</strong> role(s)?</p>
            <p className="warning-text">
              ⚠️ This action cannot be undone. The roles will be permanently removed from Discord.
            </p>
            <div className="role-list-preview">
              {roles?.filter(r => selectedRoleIds.has(r.id)).map(r => (
                <div key={r.id} className="role-preview-item">
                  <div className="role-color-dot" style={{ backgroundColor: r.colorHex || '#666' }} />
                  {r.displayName}
                </div>
              ))}
            </div>
            <div className="modal-buttons">
              <button
                onClick={() => setShowDeleteConfirm(false)}
                className="btn btn-secondary"
              >
                Cancel
              </button>
              <button
                onClick={confirmBulkDelete}
                className="btn btn-danger"
                disabled={bulkDeleteMutation.isPending}
              >
                {bulkDeleteMutation.isPending ? 'Deleting...' : 'Yes, Delete Roles'}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="server-content">
        {/* Role Hierarchy Warning */}
        {hierarchyStatus && !hierarchyStatus.isValid && (
          <div className="error-notice hierarchy-warning">
            <h3>⚠️ CRITICAL: Role Hierarchy Problem Detected</h3>
          <p>
            <strong>The bot cannot manage gacha roles because its role is positioned below them!</strong>
          </p>
          <p>
            Bot's role: <code>{hierarchyStatus.botRoleName}</code> (position {hierarchyStatus.botRolePosition})
          </p>
          <p>
            {hierarchyStatus.conflictingRoles.length} role(s) are above the bot and cannot be managed:
          </p>
          <ul className="conflicting-roles-list">
            {hierarchyStatus.conflictingRoles.slice(0, 5).map((roleName, idx) => (
              <li key={idx}><code>{roleName}</code></li>
            ))}
            {hierarchyStatus.conflictingRoles.length > 5 && (
              <li>... and {hierarchyStatus.conflictingRoles.length - 5} more</li>
            )}
          </ul>
          <div className="fix-instructions">
            <strong>How to fix:</strong>
            <ol>
              <li>Go to Discord Server Settings → Roles</li>
              <li>Find the <code>{hierarchyStatus.botRoleName}</code> role</li>
              <li>Drag it ABOVE all <code>gacha:</code> roles in the list</li>
              <li>Save and refresh this page</li>
            </ol>
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

          <div className="bulk-actions">
            <div className="selection-controls">
              <button onClick={handleSelectAll} className="btn btn-link">
                Select All
              </button>
              <button onClick={handleSelectNone} className="btn btn-link">
                Select None
              </button>
              <span className="selection-count">
                {selectedRoleIds.size} selected
              </span>
            </div>
            {selectedRoleIds.size > 0 && (
              <button
                onClick={handleBulkDelete}
                className="btn btn-danger btn-sm"
                disabled={bulkDeleteMutation.isPending}
              >
                🗑️ Delete Selected ({selectedRoleIds.size})
              </button>
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
                    <button
                      className="btn btn-link btn-add-role"
                      onClick={() => {
                        setOpenRarity(rarity);
                        setNewRoleName('');
                        // Default to this rarity's color if available
                        const def = rarityColors[rarity] || '#666666';
                        setNewRoleColor(def.startsWith('#') ? def : `#${def}`);
                      }}
                    >
                      + Add
                    </button>
                  </h2>

                  {openRarity === rarity && (
                    <div className="add-role-form">
                      <div className="form-row">
                        <div className="field field-name">
                          <label className="field-label" htmlFor={`name-${rarity}`}>Display name</label>
                          <input
                            id={`name-${rarity}`}
                            type="text"
                            placeholder="e.g., Ocean Wave"
                            value={newRoleName}
                            onChange={(e) => setNewRoleName(e.target.value)}
                            className="input"
                          />
                        </div>
                        <div className="field field-hex">
                          <label className="field-label" htmlFor={`color-${rarity}`}>Color hex</label>
                          <input
                            id={`color-${rarity}`}
                            type="text"
                            placeholder="#RRGGBB"
                            value={newRoleColor}
                            onChange={(e) => {
                              const v = e.target.value;
                              setNewRoleColor(v);
                              // Auto-suggest name when hex looks valid
                              const hex = v.startsWith('#') ? v : `#${v}`;
                              if (/^#?[0-9a-fA-F]{6}$/.test(v) || /^#[0-9a-fA-F]{6}$/.test(hex)) {
                                const suggested = suggestColorName(hex);
                                if (suggested) setNewRoleName(suggested);
                              }
                            }}
                            className="input color"
                          />
                        </div>
                        <div className="field field-picker">
                          <label className="field-label">Color picker</label>
                          <input
                            id={`picker-${rarity}`}
                            aria-label="Pick color"
                            type="color"
                            value={newRoleColor && /^#?[0-9a-fA-F]{6}$/.test(newRoleColor) ? (newRoleColor.startsWith('#') ? newRoleColor : `#${newRoleColor}`) : '#666666'}
                            onChange={(e) => {
                              const v = e.target.value; // always #RRGGBB
                              setNewRoleColor(v);
                              // Always auto-suggest name from color
                              const suggested = suggestColorName(v);
                              if (suggested) setNewRoleName(suggested);
                            }}
                            className="color-picker-input"
                          />
                        </div>
                      </div>
                      <div className="form-actions">
                        <button
                          className="btn btn-primary btn-sm"
                          disabled={createRoleMutation.isPending || !newRoleName || !/^#?[0-9a-fA-F]{6}$/.test(newRoleColor)}
                          onClick={() => {
                            // Normalize color to have leading '#'
                            const hex = newRoleColor.startsWith('#') ? newRoleColor : `#${newRoleColor}`;
                            createRoleMutation.mutate({ name: newRoleName.trim(), rarity, colorHex: hex });
                          }}
                        >
                          {createRoleMutation.isPending ? 'Creating…' : 'Create Role'}
                        </button>
                        <button className="btn btn-secondary btn-sm" onClick={() => setOpenRarity(null)}>Cancel</button>
                      </div>
                    </div>
                  )}
                  <div className="role-grid">
                    {rarityRoles.map((role: GachaRoleInfo) => (
                      <div
                        key={role.id}
                        className={`role-card ${selectedRoleIds.has(role.id) ? 'selected' : ''}`}
                        style={{ borderLeftColor: role.colorHex || '#666' }}
                      >
                        <input
                          type="checkbox"
                          className="role-checkbox"
                          checked={selectedRoleIds.has(role.id)}
                          onChange={() => handleToggleRole(role.id)}
                        />
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

            {/* Roles require a rarity; no additional sections */}
          </div>
        </>
      )}
      </div>
    </div>
  );
}
