import axios from 'axios';
import type { GuildInfo, GachaRoleInfo, HealthResponse, BulkRoleCreationResult, RoleDeletionResult, BulkRoleDeletionResult, RoleHierarchyStatus } from '../types';
import type { QotdConfigDto, QotdQuestionDto, UploadCsvResult, TextChannelInfo, QotdSubmissionDto, BulkActionResult, QotdStreamDto, CreateStreamRequest, UpdateStreamRequest, ChannelStreamStatusDto } from '../types/qotd';

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
export function getCsrfTokenFromCookie(): string | null {
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
  getChannelOptions: async (guildId: string): Promise<import('../types/qotd').ChannelTreeNodeDto[]> => {
    const res = await api.get(`/servers/${guildId}/channel-options`);
    return res.data;
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
  /**
   * Get all roles in a guild for mention dropdown (excludes bot-managed roles and @everyone)
   */
  getAllRoles: (guildId: string) => api.get<import('../types').DiscordRoleDto[]>(`/servers/${guildId}/all-roles`),
  /**
   * Get guild members for mention dropdown (paginated)
   */
  getGuildMembers: (guildId: string, limit: number = 100) =>
    api.get<import('../types').DiscordMemberDto[]>(`/servers/${guildId}/members`, { params: { limit } }),
};

export const qotdApi = {
  getBanner: (guildId: string, channelId: string) => api.get<string>(`/servers/${guildId}/channels/${channelId}/qotd/banner`),
  setBanner: (guildId: string, channelId: string, bannerText: string) => api.put<void>(`/servers/${guildId}/channels/${channelId}/qotd/banner`, bannerText, { headers: { 'Content-Type': 'text/plain' } }),
  resetBanner: (guildId: string, channelId: string) => api.post<void>(`/servers/${guildId}/channels/${channelId}/qotd/banner/reset`),
  getBannerColor: (guildId: string, channelId: string) => api.get<number | null>(`/servers/${guildId}/channels/${channelId}/qotd/banner/color`),
  setBannerColor: (guildId: string, channelId: string, color: number) => api.put<void>(`/servers/${guildId}/channels/${channelId}/qotd/banner/color`, color, { headers: { 'Content-Type': 'application/json' } }),
  getBannerMention: (guildId: string, channelId: string) => api.get<string | null>(`/servers/${guildId}/channels/${channelId}/qotd/banner/mention`),
  setBannerMention: (guildId: string, channelId: string, mention: string) => api.put<void>(`/servers/${guildId}/channels/${channelId}/qotd/banner/mention`, mention, { headers: { 'Content-Type': 'text/plain' } }),
  // Guild-level endpoints
  listChannels: async (guildId: string) => (await api.get<TextChannelInfo[]>(`/servers/${guildId}/qotd/channels`)).data,
  listConfigs: async (guildId: string) => (await api.get<QotdConfigDto[]>(`/servers/${guildId}/qotd/configs`)).data,
  listPending: async (guildId: string) => (await api.get<QotdSubmissionDto[]>(`/servers/${guildId}/qotd/submissions`)).data,
  getStreamStatus: async (guildId: string) => (await api.get<ChannelStreamStatusDto[]>(`/servers/${guildId}/qotd/stream-status`)).data,
  reject: (guildId: string, id: number) => api.post<QotdSubmissionDto>(`/servers/${guildId}/qotd/submissions/${id}/reject`),
  bulkReject: (guildId: string, ids: number[]) => api.post<BulkActionResult>(`/servers/${guildId}/qotd/submissions/bulk-reject`, { ids }),

  // Per-channel endpoints
  postNow: (guildId: string, channelId: string) => api.post<void>(`/servers/${guildId}/channels/${channelId}/qotd/post-now`),
  approve: (guildId: string, channelId: string, id: number, streamId?: number) => api.post<QotdSubmissionDto>(`/servers/${guildId}/channels/${channelId}/qotd/submissions/${id}/approve${streamId ? `?streamId=${streamId}` : ''}`),
  bulkApprove: (guildId: string, channelId: string, ids: number[], streamId?: number) => api.post<BulkActionResult>(`/servers/${guildId}/channels/${channelId}/qotd/submissions/bulk-approve${streamId ? `?streamId=${streamId}` : ''}`, { ids }),

  // NEW: Stream management endpoints
  listStreams: async (guildId: string, channelId: string) => (await api.get<QotdStreamDto[]>(`/servers/${guildId}/channels/${channelId}/qotd/streams`)).data,
  getStream: (guildId: string, channelId: string, streamId: number) => api.get<QotdStreamDto>(`/servers/${guildId}/channels/${channelId}/qotd/streams/${streamId}`),
  createStream: (guildId: string, channelId: string, req: CreateStreamRequest) => api.post<QotdStreamDto>(`/servers/${guildId}/channels/${channelId}/qotd/streams`, req),
  updateStream: (guildId: string, channelId: string, streamId: number, req: UpdateStreamRequest) => api.put<QotdStreamDto>(`/servers/${guildId}/channels/${channelId}/qotd/streams/${streamId}`, req),
  deleteStream: (guildId: string, channelId: string, streamId: number) => api.delete<void>(`/servers/${guildId}/channels/${channelId}/qotd/streams/${streamId}`),

  // Stream-scoped question management
  listStreamQuestions: async (guildId: string, channelId: string, streamId: number) => (await api.get<QotdQuestionDto[]>(`/servers/${guildId}/channels/${channelId}/qotd/streams/${streamId}/questions`)).data,
  addStreamQuestion: (guildId: string, channelId: string, streamId: number, text: string) => api.post<QotdQuestionDto>(`/servers/${guildId}/channels/${channelId}/qotd/streams/${streamId}/questions`, { text }),
  deleteStreamQuestion: (guildId: string, channelId: string, streamId: number, questionId: number) => api.delete<void>(`/servers/${guildId}/channels/${channelId}/qotd/streams/${streamId}/questions/${questionId}`),
  reorderStreamQuestions: (guildId: string, channelId: string, streamId: number, orderedIds: number[]) => api.put<void>(`/servers/${guildId}/channels/${channelId}/qotd/streams/${streamId}/questions/reorder`, { orderedIds }),
  uploadStreamCsv: (guildId: string, channelId: string, streamId: number, file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post<UploadCsvResult>(`/servers/${guildId}/channels/${channelId}/qotd/streams/${streamId}/upload-csv`, formData, { headers: { 'Content-Type': 'multipart/form-data' } });
  },

  // Stream-scoped banner management
  getStreamBanner: (guildId: string, channelId: string, streamId: number) => api.get<string>(`/servers/${guildId}/channels/${channelId}/qotd/streams/${streamId}/banner`),
  setStreamBanner: (guildId: string, channelId: string, streamId: number, bannerText: string) => api.put<void>(`/servers/${guildId}/channels/${channelId}/qotd/streams/${streamId}/banner`, bannerText, { headers: { 'Content-Type': 'text/plain' } }),
  getStreamBannerColor: (guildId: string, channelId: string, streamId: number) => api.get<number | null>(`/servers/${guildId}/channels/${channelId}/qotd/streams/${streamId}/banner/color`),
  setStreamBannerColor: (guildId: string, channelId: string, streamId: number, color: number) => api.put<void>(`/servers/${guildId}/channels/${channelId}/qotd/streams/${streamId}/banner/color`, color, { headers: { 'Content-Type': 'application/json' } }),
  getStreamBannerMention: (guildId: string, channelId: string, streamId: number) => api.get<string | null>(`/servers/${guildId}/channels/${channelId}/qotd/streams/${streamId}/banner/mention`),
  setStreamBannerMention: (guildId: string, channelId: string, streamId: number, mention: string) => api.put<void>(`/servers/${guildId}/channels/${channelId}/qotd/streams/${streamId}/banner/mention`, mention, { headers: { 'Content-Type': 'text/plain' } }),
  resetStreamBanner: (guildId: string, channelId: string, streamId: number) => api.post<void>(`/servers/${guildId}/channels/${channelId}/qotd/streams/${streamId}/banner/reset`),

  // Stream post-now
  postStreamNow: (guildId: string, channelId: string, streamId: number) => api.post<void>(`/servers/${guildId}/channels/${channelId}/qotd/streams/${streamId}/post-now`),
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
