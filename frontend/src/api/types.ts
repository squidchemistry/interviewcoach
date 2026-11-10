/**
 * Friendly aliases over the generated OpenAPI types (src/api/schema.d.ts).
 * Regenerate the schema with `npm run gen:api` whenever backend DTOs change; CI fails if it is stale.
 */
import type { components } from './schema';

type Schemas = components['schemas'];

export type AuthResponse = Schemas['AuthResponse'];
export type UserResponse = Schemas['UserResponse'];
export type ProfileResponse = Schemas['ProfileResponse'];
export type CompanyResponse = Schemas['CompanyResponse'];
export type ProvidersResponse = Schemas['ProvidersResponse'];
export type HealthResponse = Schemas['HealthResponse'];
export type AdminUserResponse = Schemas['AdminUserResponse'];
export type AdminUserPage = Schemas['PageResponseAdminUserResponse'];
export type RegisterRequest = Schemas['RegisterRequest'];
export type LoginRequest = Schemas['LoginRequest'];
export type UpdateProfileRequest = Schemas['UpdateProfileRequest'];

export type Role = UserResponse['role'];
export type TargetRole = UpdateProfileRequest['targetRole'];
export type CodeLanguage = UpdateProfileRequest['preferredLanguage'];
