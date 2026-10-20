export type ApplicationStatus =
  'APPLIED' | 'INTERVIEWING' | 'OFFER' | 'REJECTED' | 'WITHDRAWN';

export interface ApplicationSummary {
  id: string;
  company: string;
  roleTitle: string;
  status: ApplicationStatus;
  appliedDate: string;
  version: number;
  createdAt: string;
}

export interface ApplicationDetail extends ApplicationSummary {
  jdText: string | null;
  updatedAt?: string;
}

export interface CreateApplicationRequest {
  company: string;
  roleTitle: string;
  jdText?: string | null;
  appliedDate?: string | null;
}

export interface UpdateStatusRequest {
  status: ApplicationStatus;
  version: number;
}

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const ALLOWED_TRANSITIONS: Record<ApplicationStatus, ApplicationStatus[]> = {
  APPLIED: ['INTERVIEWING', 'REJECTED', 'WITHDRAWN'],
  INTERVIEWING: ['OFFER', 'REJECTED', 'WITHDRAWN'],
  OFFER: [],
  REJECTED: [],
  WITHDRAWN: []
};

export interface ApplicationStats {
  total: number;
  applied: number;
  interviewing: number;
  offer: number;
  rejected: number;
  withdrawn: number;
}
