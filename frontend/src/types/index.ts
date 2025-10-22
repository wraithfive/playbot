export interface GuildInfo {
  id: string;
  name: string;
  iconUrl: string | null;
  userIsAdmin: boolean;
  botIsPresent: boolean;  // Backend uses 'botIsPresent' with capital I
}

export interface GachaRoleInfo {
  id: string;
  fullName: string;
  displayName: string;
  rarity: string | null;
  colorHex: string | null;
  position: number;
}

export interface BulkRoleCreationResult {
  successCount: number;
  skippedCount: number;
  failureCount: number;
  createdRoles: GachaRoleInfo[];
  skippedRoles: string[];
  errors: string[];
}

export interface RoleDeletionResult {
  roleId: string;
  roleName: string | null;
  success: boolean;
  error: string | null;
}

export interface BulkRoleDeletionResult {
  successCount: number;
  failureCount: number;
  deletedRoles: RoleDeletionResult[];
  errors: string[];
}

export interface RoleHierarchyStatus {
  isValid: boolean;
  botRoleName: string;
  botRolePosition: number;
  highestGachaRolePosition: number;
  conflictingRoles: string[];
}

export interface HealthResponse {
  status: string;
  bot: {
    connected: string;
    guilds: number;
    username: string;
  };
  timestamp: number;
}

export interface UserInfo {
  id: string;
  username: string;
  discriminator: string;
  avatar: string | null;
}
