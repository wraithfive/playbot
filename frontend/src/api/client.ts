import axios from 'axios';
import type { GuildInfo, GachaRoleInfo, HealthResponse, BulkRoleCreationResult, RoleDeletionResult, BulkRoleDeletionResult, RoleHierarchyStatus } from '../types';
import type { QotdConfigDto, QotdQuestionDto, UpdateQotdRequest, UploadCsvResult, TextChannelInfo, QotdSubmissionDto, BulkActionResult } from '../types/qotd';

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
  createRole: (guildId: string, payload: { name: string; rarity: string; colorHex: string }) =>
    api.post<GachaRoleInfo>(`/servers/${guildId}/roles`, payload),
  checkRoleHierarchy: (guildId: string) => api.get<RoleHierarchyStatus>(`/servers/${guildId}/roles/hierarchy-check`),
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
  deleteRole: (guildId: string, roleId: string) =>
    api.delete<RoleDeletionResult>(`/servers/${guildId}/roles/${roleId}`),
  bulkDeleteRoles: (guildId: string, roleIds: string[]) =>
    api.post<BulkRoleDeletionResult>(`/servers/${guildId}/roles/bulk-delete`, { roleIds }),
  /**
   * Force refresh the backend guilds cache for the current user
   */
  refreshGuildsCache: () => api.post<{ message: string }>('/servers/refresh'),
};

export const qotdApi = {
  // Guild-level endpoints
  listChannels: (guildId: string) => api.get<TextChannelInfo[]>(`/servers/${guildId}/qotd/channels`),
  listConfigs: (guildId: string) => api.get<QotdConfigDto[]>(`/servers/${guildId}/qotd/configs`),
  listPending: (guildId: string) => api.get<QotdSubmissionDto[]>(`/servers/${guildId}/qotd/submissions`),
  reject: (guildId: string, id: number) => api.post<QotdSubmissionDto>(`/servers/${guildId}/qotd/submissions/${id}/reject`),
  bulkReject: (guildId: string, ids: number[]) => api.post<BulkActionResult>(`/servers/${guildId}/qotd/submissions/bulk-reject`, { ids }),

  // Per-channel endpoints
  getConfig: (guildId: string, channelId: string) => api.get<QotdConfigDto>(`/servers/${guildId}/channels/${channelId}/qotd/config`),
  updateConfig: (guildId: string, channelId: string, req: UpdateQotdRequest) => api.put<QotdConfigDto>(`/servers/${guildId}/channels/${channelId}/qotd/config`, req),
  listQuestions: (guildId: string, channelId: string) => api.get<QotdQuestionDto[]>(`/servers/${guildId}/channels/${channelId}/qotd/questions`),
  addQuestion: (guildId: string, channelId: string, text: string) => api.post<QotdQuestionDto>(`/servers/${guildId}/channels/${channelId}/qotd/questions`, { text }),
  deleteQuestion: (guildId: string, channelId: string, id: number) => api.delete<void>(`/servers/${guildId}/channels/${channelId}/qotd/questions/${id}`),
  reorderQuestions: (guildId: string, channelId: string, orderedIds: number[]) => api.put<void>(`/servers/${guildId}/channels/${channelId}/qotd/questions/reorder`, { orderedIds }),
  uploadCsv: (guildId: string, channelId: string, file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post<UploadCsvResult>(`/servers/${guildId}/channels/${channelId}/qotd/upload-csv`, formData, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  postNow: (guildId: string, channelId: string) => api.post<void>(`/servers/${guildId}/channels/${channelId}/qotd/post-now`),
  approve: (guildId: string, channelId: string, id: number) => api.post<QotdSubmissionDto>(`/servers/${guildId}/channels/${channelId}/qotd/submissions/${id}/approve`),
  bulkApprove: (guildId: string, channelId: string, ids: number[]) => api.post<BulkActionResult>(`/servers/${guildId}/channels/${channelId}/qotd/submissions/bulk-approve`, { ids }),
};

export const authApi = {
  login: () => {
    // For dev: OAuth flow must go directly to backend since Vite proxy doesn't handle full page redirects
    const backendUrl = import.meta.env.DEV ? 'http://localhost:8080' : '';
    window.location.href = `${backendUrl}/oauth2/authorization/discord`;
  },
  logout: async () => {
    const backendUrl = import.meta.env.DEV ? 'http://localhost:8080' : '';
    // Ensure we have a CSRF token cookie before POSTing logout
    if (!hasCsrfToken()) {
      try { await fetch(`${backendUrl}/api/csrf`, { credentials: 'include' }); } catch {}
    }
    const token = getCsrfTokenFromCookie();
    try {
      await fetch(`${backendUrl}/api/logout`, {
        method: 'POST',
        credentials: 'include',
        headers: token ? { 'X-XSRF-TOKEN': token } : undefined,
      });
    } catch {
      // ignore network errors; we'll still redirect client-side
    } finally {
      // Redirect back to root (the server's logoutSuccessUrl will also redirect)
      window.location.href = '/';
    }
  },
};

export default api;
