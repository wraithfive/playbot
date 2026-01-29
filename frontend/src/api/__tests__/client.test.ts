import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { getCsrfTokenFromCookie, healthApi, serverApi, qotdApi, authApi } from '../client';

// Mock axios module completely
vi.mock('axios', () => {
  const mockAxiosInstance = {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
    interceptors: {
      request: {
        use: vi.fn(),
      },
      response: {
        use: vi.fn(),
      },
    },
  };

  return {
    default: {
      create: vi.fn(() => mockAxiosInstance),
    },
  };
});

describe('getCsrfTokenFromCookie', () => {
  it('returns null if no XSRF-TOKEN cookie is present', () => {
    Object.defineProperty(document, 'cookie', { value: '', writable: true, configurable: true });
    expect(getCsrfTokenFromCookie()).toBeNull();
  });

  it('returns the token if XSRF-TOKEN cookie is present', () => {
    Object.defineProperty(document, 'cookie', { value: 'foo=bar; XSRF-TOKEN=abc123', writable: true, configurable: true });
    expect(getCsrfTokenFromCookie()).toBe('abc123');
  });

  it('returns the token if XSRF-TOKEN is first cookie', () => {
    Object.defineProperty(document, 'cookie', { value: 'XSRF-TOKEN=xyz789; foo=bar', writable: true, configurable: true });
    expect(getCsrfTokenFromCookie()).toBe('xyz789');
  });

  it('handles URL-encoded cookie values', () => {
    Object.defineProperty(document, 'cookie', { value: 'XSRF-TOKEN=abc%20123', writable: true, configurable: true });
    expect(getCsrfTokenFromCookie()).toBe('abc 123');
  });

  it('returns null for multiple cookies without XSRF-TOKEN', () => {
    Object.defineProperty(document, 'cookie', { value: 'session=123; user=test; theme=dark', writable: true, configurable: true });
    expect(getCsrfTokenFromCookie()).toBeNull();
  });
});

describe('authApi', () => {
  let originalLocation: Location;

  beforeEach(() => {
    originalLocation = window.location;
    delete (window as any).location;
    (window as any).location = { ...originalLocation, href: '' } as Location;
    vi.stubEnv('DEV', false);
    global.fetch = vi.fn();
  });

  afterEach(() => {
    (window as any).location = originalLocation;
    vi.unstubAllEnvs();
  });

  describe('login', () => {
    it('redirects to OAuth2 authorization in production', () => {
      authApi.login();
      expect(window.location.href).toBe('/oauth2/authorization/discord');
    });

    it('redirects to backend OAuth2 authorization in development', () => {
      vi.stubEnv('DEV', true);
      authApi.login();
      expect(window.location.href).toBe('http://localhost:8080/oauth2/authorization/discord');
    });
  });

  describe('logout', () => {
    beforeEach(() => {
      Object.defineProperty(document, 'cookie', { value: 'XSRF-TOKEN=test-token', writable: true, configurable: true });
    });

    it('calls logout endpoint with CSRF token and redirects', async () => {
      (global.fetch as any).mockResolvedValue({ ok: true });

      await authApi.logout();

      expect(global.fetch).toHaveBeenCalledWith(
        '/api/logout',
        expect.objectContaining({
          method: 'POST',
          credentials: 'include',
          headers: { 'X-XSRF-TOKEN': 'test-token' },
        })
      );
      expect(window.location.href).toBe('/');
    });

    it('fetches CSRF token if not present before logout', async () => {
      Object.defineProperty(document, 'cookie', { value: '', writable: true, configurable: true });
      (global.fetch as any).mockResolvedValue({ ok: true });

      await authApi.logout();

      expect(global.fetch).toHaveBeenCalledWith('/api/csrf', { credentials: 'include' });
      expect(window.location.href).toBe('/');
    });

    it('redirects even if logout request fails', async () => {
      (global.fetch as any).mockRejectedValue(new Error('Network error'));

      await authApi.logout();

      expect(window.location.href).toBe('/');
    });

    it('uses backend URL in development mode', async () => {
      vi.stubEnv('DEV', true);
      (global.fetch as any).mockResolvedValue({ ok: true });

      await authApi.logout();

      expect(global.fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/logout',
        expect.any(Object)
      );
    });
  });
});

describe('API modules exist', () => {
  it('healthApi has check method', () => {
    expect(healthApi).toBeDefined();
    expect(typeof healthApi.check).toBe('function');
  });

  it('serverApi has all required methods', () => {
    expect(serverApi).toBeDefined();
    expect(typeof serverApi.getServers).toBe('function');
    expect(typeof serverApi.getServer).toBe('function');
    expect(typeof serverApi.getBotInviteUrl).toBe('function');
    expect(typeof serverApi.removeBot).toBe('function');
    expect(typeof serverApi.getRoles).toBe('function');
    expect(typeof serverApi.createRole).toBe('function');
    expect(typeof serverApi.checkRoleHierarchy).toBe('function');
    expect(typeof serverApi.initializeDefaultRoles).toBe('function');
    expect(typeof serverApi.uploadCsv).toBe('function');
    expect(typeof serverApi.downloadExampleCsv).toBe('function');
    expect(typeof serverApi.deleteRole).toBe('function');
    expect(typeof serverApi.bulkDeleteRoles).toBe('function');
    expect(typeof serverApi.refreshGuildsCache).toBe('function');
    expect(typeof serverApi.getChannelOptions).toBe('function');
  });

  it('qotdApi has all required methods', () => {
    expect(qotdApi).toBeDefined();
    expect(typeof qotdApi.getBanner).toBe('function');
    expect(typeof qotdApi.setBanner).toBe('function');
    expect(typeof qotdApi.resetBanner).toBe('function');
    expect(typeof qotdApi.getBannerColor).toBe('function');
    expect(typeof qotdApi.setBannerColor).toBe('function');
    expect(typeof qotdApi.getBannerMention).toBe('function');
    expect(typeof qotdApi.setBannerMention).toBe('function');
    expect(typeof qotdApi.listChannels).toBe('function');
    expect(typeof qotdApi.listConfigs).toBe('function');
    expect(typeof qotdApi.listPending).toBe('function');
    expect(typeof qotdApi.reject).toBe('function');
    expect(typeof qotdApi.bulkReject).toBe('function');
    expect(typeof qotdApi.postNow).toBe('function');
    expect(typeof qotdApi.approve).toBe('function');
    expect(typeof qotdApi.bulkApprove).toBe('function');
  });
});
