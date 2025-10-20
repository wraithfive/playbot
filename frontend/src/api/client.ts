import axios from 'axios';
import type { GuildInfo, GachaRoleInfo, HealthResponse } from '../types';

const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
});

// Add interceptor to include CSRF token from cookie in requests
api.interceptors.request.use((config) => {
  const csrfToken = getCsrfTokenFromCookie();
  if (csrfToken) {
    config.headers['X-XSRF-TOKEN'] = csrfToken;
  }
  return config;
});

// Helper function to extract CSRF token from cookie
function getCsrfTokenFromCookie(): string | null {
  const name = 'XSRF-TOKEN=';
  const decodedCookie = decodeURIComponent(document.cookie);
  const cookies = decodedCookie.split(';');

  for (let cookie of cookies) {
    cookie = cookie.trim();
    if (cookie.startsWith(name)) {
      return cookie.substring(name.length);
    }
  }
  return null;
}

export const healthApi = {
  check: () => api.get<HealthResponse>('/health'),
};

interface BulkRoleCreationResult {
  successCount: number;
  failureCount: number;
  createdRoles: GachaRoleInfo[];
  errors: string[];
}

export const serverApi = {
  getServers: () => api.get<GuildInfo[]>('/servers'),
  getServer: (guildId: string) => api.get<GuildInfo>(`/servers/${guildId}`),
  getBotInviteUrl: (guildId: string) => api.get<{ inviteUrl: string }>(`/servers/${guildId}/invite`),
  removeBot: (guildId: string) => api.delete<{ message: string }>(`/servers/${guildId}/bot`),
  getRoles: (guildId: string) => api.get<GachaRoleInfo[]>(`/servers/${guildId}/roles`),
  initializeDefaultRoles: (guildId: string) =>
    api.post<BulkRoleCreationResult>(`/servers/${guildId}/roles/init-defaults`),
  uploadCsv: (guildId: string, file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post<BulkRoleCreationResult>(`/servers/${guildId}/roles/upload-csv`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },
  downloadExampleCsv: (guildId: string) =>
    api.get(`/servers/${guildId}/roles/download-example`, {
      responseType: 'blob',
    }),
};

export const authApi = {
  login: () => {
    window.location.href = '/oauth2/authorization/discord';
  },
  logout: () => {
    window.location.href = '/logout';
  },
};

export default api;
