import axios from 'axios';
import type { GuildInfo, GachaRoleInfo, HealthResponse } from '../types';

const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
});

// Ensure CSRF token is available before mutating requests (POST/PUT/PATCH/DELETE)
api.interceptors.request.use(async (config) => {
  const method = (config.method || 'get').toLowerCase();
  const isMutating = ['post', 'put', 'patch', 'delete'].includes(method);

  if (isMutating && !hasCsrfToken()) {
    // Trigger server to set XSRF-TOKEN cookie without recursion into axios interceptors
    try {
      await fetch('/api/health', { credentials: 'include' });
    } catch {
      // ignore; we'll still attempt the request and let server respond
    }
  }

  const csrfToken = getCsrfTokenFromCookie();
  if (csrfToken) {
    if (!config.headers) {
      config.headers = {} as any;
    }
    // Axios v1 Headers can be AxiosHeaders or a plain object
    (config.headers as any)['X-XSRF-TOKEN'] = csrfToken;
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

function hasCsrfToken(): boolean {
  return getCsrfTokenFromCookie() !== null;
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
