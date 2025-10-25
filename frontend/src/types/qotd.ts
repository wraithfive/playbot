export interface QotdQuestionDto {
  id: number;
  text: string;
  createdAt: string;
  authorUserId?: string | null;
  authorUsername?: string | null;
}

export interface QotdConfigDto {
  channelId: string;
  enabled: boolean;
  timezone: string;
  scheduleCron: string | null;
  randomize: boolean;
  autoApprove: boolean;
  lastPostedAt: string | null;
  nextIndex: number;
  nextRuns: string[];
}

export interface UploadCsvResult {
  successCount: number;
  failureCount: number;
  errors: string[];
}

export interface UpdateQotdRequest {
  enabled: boolean;
  timezone: string;
  advancedCron?: string | null;
  daysOfWeek?: string[]; // MON..SUN
  timeOfDay?: string; // HH:mm
  randomize: boolean;
  autoApprove: boolean;
}

export interface TextChannelInfo { id: string; name: string; }

export type SubmissionStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface QotdSubmissionDto {
  id: number;
  text: string;
  userId: string;
  username: string;
  status: SubmissionStatus;
  createdAt: string;
}

export interface BulkIdsRequest { ids: number[]; }

export interface ReorderQuestionsRequest { orderedIds: number[]; }

export interface BulkActionResult {
  successCount: number;
  failureCount: number;
  errors: string[];
}
