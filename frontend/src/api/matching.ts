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

export type NepalCity =
  | "Kathmandu"
  | "Lalitpur"
  | "Bhaktapur"
  | "Pokhara"
  | "Bharatpur"
  | "Biratnagar"
  | "Birgunj"
  | "Butwal"
  | "Dharan"
  | "Hetauda"
  | "Janakpur"
  | "Nepalgunj"
  | "Dhangadhi"
  | "Itahari"
  | "Tulsipur";

export const NEPAL_CITY_OPTIONS: Array<{ value: NepalCity; label: string }> = [
  { value: "Kathmandu", label: "Kathmandu" },
  { value: "Lalitpur", label: "Lalitpur" },
  { value: "Bhaktapur", label: "Bhaktapur" },
  { value: "Pokhara", label: "Pokhara" },
  { value: "Bharatpur", label: "Bharatpur" },
  { value: "Biratnagar", label: "Biratnagar" },
  { value: "Birgunj", label: "Birgunj" },
  { value: "Butwal", label: "Butwal" },
  { value: "Dharan", label: "Dharan" },
  { value: "Hetauda", label: "Hetauda" },
  { value: "Janakpur", label: "Janakpur" },
  { value: "Nepalgunj", label: "Nepalgunj" },
  { value: "Dhangadhi", label: "Dhangadhi" },
  { value: "Itahari", label: "Itahari" },
  { value: "Tulsipur", label: "Tulsipur" },
];

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
