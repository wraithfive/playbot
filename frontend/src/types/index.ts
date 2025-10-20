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
