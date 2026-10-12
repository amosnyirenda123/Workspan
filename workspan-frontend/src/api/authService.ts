import { apiClient } from './http'
import type { AuthUser } from './types'

export type RegisterRequest = { organizationName: string; displayName: string; email: string; password: string }
export type LoginRequest = { email: string; password: string }

export const authService = {
  async me(): Promise<AuthUser> { return (await apiClient.get<AuthUser>('/auth/me')).data },
  async register(payload: RegisterRequest): Promise<AuthUser> { return (await apiClient.post<AuthUser>('/auth/register', payload)).data },
  async login(payload: LoginRequest): Promise<AuthUser> { return (await apiClient.post<AuthUser>('/auth/login', payload)).data },
  async logout(): Promise<void> { await apiClient.post('/auth/logout') },
}
