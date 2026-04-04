import { useQuery } from "@tanstack/react-query"

import { apiJson } from "@/api/http"

export interface ConfigPropSource {
  name: string
  properties: Record<string, { value: unknown; origin?: string }>
}

export interface EnvDto {
  propertySources: ConfigPropSource[]
}

export interface HealthDto {
  status: string
  components?: Record<string, { status: string }>
}

export interface InfoDto {
  app?: { name?: string; version?: string }
  java?: { version?: string }
  build?: { time?: string; version?: string }
  [k: string]: unknown
}

export interface MetricsDto {
  names: string[]
}

export function useConfigEnv() {
  return useQuery({
    queryKey: ["config-env"],
    queryFn: () => apiJson<EnvDto>("/actuator/env"),
  })
}

export function useHealth() {
  return useQuery({
    queryKey: ["health"],
    queryFn: () => apiJson<HealthDto>("/actuator/health"),
    refetchInterval: 15_000,
  })
}

export function useInfo() {
  return useQuery({
    queryKey: ["info"],
    queryFn: () => apiJson<InfoDto>("/actuator/info"),
  })
}

export function useMetric(name: string) {
  return useQuery({
    queryKey: ["metrics", name],
    queryFn: () =>
      apiJson<{
        name: string
        measurements: { statistic: string; value: number }[]
      }>(`/actuator/metrics/${name}`),
  })
}
