import { useQuery } from '@tanstack/react-query';
import { apiRequest } from './http';
import type { HealthResponse } from './types';

export type { HealthResponse } from './types';
export type HealthStatus = 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN';

export function fetchHealth(signal?: AbortSignal): Promise<HealthResponse> {
  // 503 still carries a HealthResponse body describing which dependency is down.
  return apiRequest<HealthResponse>('/api/v1/health', { signal, acceptStatuses: [503] });
}

export const HEALTH_REFRESH_MS = 15_000;

export function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: ({ signal }) => fetchHealth(signal),
    refetchInterval: HEALTH_REFRESH_MS,
    retry: false,
  });
}
