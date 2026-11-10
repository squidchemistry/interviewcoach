import type { AdminUserResponse, AuthResponse, CompanyResponse, ProfileResponse, UserResponse } from '../api/types';

export const companies: CompanyResponse[] = [
  { id: '00000000-0000-0000-0000-00000000000a', name: 'Amazon', slug: 'amazon' },
  { id: '00000000-0000-0000-0000-00000000000b', name: 'Flipkart', slug: 'flipkart' },
  { id: '00000000-0000-0000-0000-00000000000c', name: 'Google', slug: 'google' },
];

export const profile: ProfileResponse = {
  targetRole: 'SDE_1',
  experienceYears: 0.5,
  preferredLanguage: 'PYTHON',
  targetCompanies: [companies[0]!],
  onboardedAt: '2026-11-01T10:00:00Z',
  updatedAt: '2026-11-01T10:00:00Z',
};

export function makeUser(overrides: Partial<UserResponse> = {}): UserResponse {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    email: 'priya@example.com',
    name: 'Priya Sharma',
    role: 'USER',
    plan: 'FREE',
    emailVerified: false,
    hasPassword: true,
    profile,
    createdAt: '2026-11-01T10:00:00Z',
    ...overrides,
  };
}

export function makeAuth(user: UserResponse = makeUser(), accessToken = 'access-1'): AuthResponse {
  return { user, accessToken, expiresIn: 900 };
}

export const adminRows: AdminUserResponse[] = [
  { id: '11111111-1111-1111-1111-111111111111', email: 'admin@aicoach.dev', name: 'Admin', role: 'ADMIN', plan: 'FREE', emailVerified: false, createdAt: '2026-11-01T10:00:00Z' },
  { id: '22222222-2222-2222-2222-222222222222', email: 'rahul@example.com', name: 'Rahul', role: 'USER', plan: 'FREE', emailVerified: true, createdAt: '2026-11-02T10:00:00Z' },
];

export function problem(status: number, code: string, detail: string, errors?: Array<{ path: string; message: string }>) {
  return {
    body: { status, code, detail, title: code, requestId: 'req-test', ...(errors ? { errors } : {}) },
    init: { status, headers: { 'Content-Type': 'application/problem+json' } },
  };
}
