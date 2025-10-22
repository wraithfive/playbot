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
    // Trigger server to set XSRF-TOKEN cookie by calling authenticated CSRF endpoint
    try {
      await fetch('/api/csrf', { credentials: 'include' });
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
  removeBot: async (guildId: string) => {
    const result = await api.delete<{ message: string }>(`/servers/${guildId}/bot`);
    await api.post<{ message: string }>('/servers/refresh');
    return result;
  },
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
  /**
   * Force refresh the backend guilds cache for the current user
   */
  refreshGuildsCache: () => api.post<{ message: string }>('/servers/refresh'),
};

export const authApi = {
  login: () => {
    // For dev: OAuth flow must go directly to backend since Vite proxy doesn't handle full page redirects
    const backendUrl = import.meta.env.DEV ? 'http://localhost:8080' : '';
    window.location.href = `${backendUrl}/oauth2/authorization/discord`;
  },
  logout: () => {
    const backendUrl = import.meta.env.DEV ? 'http://localhost:8080' : '';
    window.location.href = `${backendUrl}/logout`;
  },
};

export default api;
