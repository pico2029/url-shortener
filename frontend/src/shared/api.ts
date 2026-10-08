export type ApiProblem = {
  status?: number;
  code?: string;
  detail?: string;
  fieldErrors?: Record<string, string>;
};

export class ApiError extends Error {
  constructor(public readonly problem: ApiProblem) {
    super(problem.detail || 'The service is unavailable. Please try again later.');
  }
}

export async function postJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({})) as ApiProblem;
    throw new ApiError({ ...problem, status: response.status });
  }
  return response.json() as Promise<T>;
}

export async function getJson<T>(path: string, token: string): Promise<T> {
  const response = await fetch(path, { headers: { Accept: 'application/json', Authorization: `Bearer ${token}` } });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({})) as ApiProblem;
    throw new ApiError({ ...problem, status: response.status });
  }
  return response.json() as Promise<T>;
}
