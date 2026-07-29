export type SectorCode =
  | "BUSINESS"
  | "HEALTHCARE"
  | "IT"
  | "SOFTWARE"
  | "FINANCE"
  | "MARKETING"
  | "EDUCATION"
  | "DESIGN"
  | "ENGINEERING"
  | "SALES";

export type WorkMode = "ON_SITE" | "REMOTE" | "HYBRID";

export const SECTOR_OPTIONS: Array<{ value: SectorCode; label: string }> = [
  { value: "BUSINESS", label: "Business" },
  { value: "HEALTHCARE", label: "Healthcare" },
  { value: "IT", label: "IT" },
  { value: "SOFTWARE", label: "Software" },
  { value: "FINANCE", label: "Finance" },
  { value: "MARKETING", label: "Marketing" },
  { value: "EDUCATION", label: "Education" },
  { value: "DESIGN", label: "Design" },
  { value: "ENGINEERING", label: "Engineering" },
  { value: "SALES", label: "Sales" },
];

export const WORK_MODE_LABELS: Record<WorkMode, string> = {
  ON_SITE: "On-site",
  REMOTE: "Remote",
  HYBRID: "Hybrid",
};
