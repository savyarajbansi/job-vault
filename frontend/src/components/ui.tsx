import { useEffect } from "react";
import React from "react";

/* ─────────────────────────────────────────────────────────────────────────────
   DESIGN RULES (enforced here)

   1. Typography hierarchy
      - h1 / h2 / h3 / h4 and numeric callouts use Plus Jakarta Sans through
        the shared font tokens. Keep type hierarchy in weight and size rather
        than introducing a second font family.

   2. Card discipline
      - <Card> is for isolated objects (a single entity's detail).
      - <RowList> + <RowItem> is for lists of repeating items.
      - Never nest Card inside Card.

   3. Spacing
      - Use CSS variables (--space-N) wherever possible so adjustments cascade.
      - Minimum gap between sibling Card components: var(--space-5) (20px).
      - Tighter within a Card: var(--space-4) between form rows, var(--space-3)
        between meta details.

   4. Colour
      - Status conveyed with tone + label text, never colour alone.
      - Ink hierarchy: --ink for primary, --ink-2 for body copy, --ink-muted
        for secondary/meta, --ink-faint for placeholders and empty states.
───────────────────────────────────────────────────────────────────────────── */

/* ─── Button ────────────────────────────────────────────────────────────────*/
type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";
type ButtonSize = "sm" | "md" | "lg";

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  fullWidth?: boolean;
}

const btnBase: React.CSSProperties = {
  display: "inline-flex",
  alignItems: "center",
  justifyContent: "center",
  gap: "var(--space-2)",
  fontFamily: "var(--font-body)",
  fontWeight: 500,
  borderRadius: "var(--radius-sm)",
  border: "1.5px solid transparent",
  transition: "background 0.15s, border-color 0.15s, color 0.15s, opacity 0.15s",
  cursor: "pointer",
  whiteSpace: "nowrap",
  letterSpacing: "0.01em",
};

const btnVariants: Record<ButtonVariant, React.CSSProperties> = {
  primary: {
    background: "var(--accent)",
    color: "#fff",
    borderColor: "var(--accent)",
  },
  secondary: {
    background: "transparent",
    color: "var(--accent)",
    borderColor: "var(--accent)",
  },
  ghost: {
    background: "transparent",
    color: "var(--ink-2)",
    borderColor: "var(--border)",
  },
  danger: {
    background: "var(--warn)",
    color: "#fff",
    borderColor: "var(--warn)",
  },
};

const btnSizes: Record<ButtonSize, React.CSSProperties> = {
  sm: { padding: "0.3125rem 0.75rem",  fontSize: "0.8125rem" },
  md: { padding: "0.5625rem 1.125rem", fontSize: "0.9375rem" },
  lg: { padding: "0.6875rem 1.375rem", fontSize: "1rem" },
};

export function Button({
  variant = "primary",
  size = "md",
  loading = false,
  fullWidth = false,
  children,
  disabled,
  style,
  ...props
}: ButtonProps) {
  const isDisabled = disabled || loading;
  return (
    <button
      {...props}
      disabled={isDisabled}
      style={{
        ...btnBase,
        ...btnVariants[variant],
        ...btnSizes[size],
        ...(fullWidth ? { width: "100%" } : {}),
        ...(isDisabled ? { opacity: 0.5, cursor: "not-allowed" } : {}),
        ...style,
      }}
    >
      {loading && (
        <span
          style={{
            width: "1em",
            height: "1em",
            border: "2px solid currentColor",
            borderTopColor: "transparent",
            borderRadius: "50%",
            display: "inline-block",
            animation: "spin 0.7s linear infinite",
          }}
        />
      )}
      {children}
    </button>
  );
}

/* ─── Input ─────────────────────────────────────────────────────────────────*/
interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  hint?: string;
}

export function Input({ label, error, hint, id, style, ...props }: InputProps) {
  const inputId = id ?? label.toLowerCase().replace(/\s+/g, "-");
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-1)" }}>
      <label
        htmlFor={inputId}
        style={{
          fontSize: "0.8125rem",
          fontWeight: 500,
          color: "var(--ink-2)",
          letterSpacing: "0.015em",
        }}
      >
        {label}
      </label>
      <input
        id={inputId}
        style={{
          padding: "0.5625rem 0.8125rem",
          borderRadius: "var(--radius-sm)",
          border: `1.5px solid ${error ? "var(--warn)" : "var(--border)"}`,
          background: "var(--bg-card)",
          color: "var(--ink)",
          fontSize: "0.9375rem",
          outline: "none",
          transition: "border-color 0.15s, box-shadow 0.15s",
          width: "100%",
          ...style,
        }}
        onFocus={(e) => {
          e.currentTarget.style.borderColor = error ? "var(--warn)" : "var(--border-focus)";
          e.currentTarget.style.boxShadow = error
            ? "0 0 0 3px rgba(192,98,42,0.1)"
            : "0 0 0 3px rgba(45,110,110,0.1)";
        }}
        onBlur={(e) => {
          e.currentTarget.style.borderColor = error ? "var(--warn)" : "var(--border)";
          e.currentTarget.style.boxShadow = "none";
        }}
        {...props}
      />
      {error && (
        <span style={{ fontSize: "0.8125rem", color: "var(--warn)", lineHeight: 1.4 }}>
          {error}
        </span>
      )}
      {hint && !error && (
        <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)", lineHeight: 1.4 }}>
          {hint}
        </span>
      )}
    </div>
  );
}

/* ─── Alert ─────────────────────────────────────────────────────────────────*/
type AlertTone = "success" | "error" | "info";

interface AlertProps {
  tone: AlertTone;
  children: React.ReactNode;
}

const alertStyles: Record<AlertTone, React.CSSProperties> = {
  success: {
    background: "var(--success-faint)",
    borderLeft: "3px solid var(--success)",
    color: "var(--success)",
  },
  error: {
    background: "var(--warn-faint)",
    borderLeft: "3px solid var(--warn)",
    color: "var(--warn)",
  },
  info: {
    background: "var(--accent-faint)",
    borderLeft: "3px solid var(--accent)",
    color: "var(--accent)",
  },
};

export function Alert({ tone, children }: AlertProps) {
  return (
    <div
      role="alert"
      style={{
        padding: "0.6875rem var(--space-4)",
        borderRadius: "var(--radius-sm)",
        fontSize: "0.875rem",
        lineHeight: 1.5,
        ...alertStyles[tone],
      }}
    >
      {children}
    </div>
  );
}

/* ─── Card ───────────────────────────────────────────────────────────────────
   Use for a single isolated object or a small contained panel.
   Do NOT use Card to wrap lists of repeating rows — use RowList instead.
───────────────────────────────────────────────────────────────────────────── */
interface CardProps {
  children: React.ReactNode;
  style?: React.CSSProperties;
  padded?: boolean;
}

export function Card({ children, style, padded = true }: CardProps) {
  return (
    <div
      style={{
        background: "var(--bg-card)",
        borderRadius: "var(--radius)",
        border: "1px solid var(--border)",
        boxShadow: "var(--shadow-sm)",
        ...(padded ? { padding: "var(--space-6)" } : {}),
        ...style,
      }}
    >
      {children}
    </div>
  );
}

/* ─── RowList + RowItem ──────────────────────────────────────────────────────
   For lists of repeating items (jobs, applications, resumes).
   Items are separated by hairlines. No box-shadow on individual rows.
───────────────────────────────────────────────────────────────────────────── */
interface RowListProps {
  children: React.ReactNode;
  style?: React.CSSProperties;
}

export function RowList({ children, style }: RowListProps) {
  return (
    <div
      style={{
        background: "var(--bg-card)",
        border: "1px solid var(--border)",
        borderRadius: "var(--radius)",
        boxShadow: "var(--shadow-xs)",
        overflow: "hidden",
        ...style,
      }}
    >
      {children}
    </div>
  );
}

interface RowItemProps {
  children: React.ReactNode;
  style?: React.CSSProperties;
  interactive?: boolean;
  selected?: boolean;
  onClick?: () => void;
}

export function RowItem({ children, style, interactive, selected, onClick }: RowItemProps) {
  const isButton = Boolean(onClick);
  const Tag = isButton ? "button" : "div";
  return (
    <Tag
      {...(isButton ? { type: "button" as const } : {})}
      onClick={onClick}
      style={{
        display: "flex",
        alignItems: "center",
        gap: "var(--space-4)",
        padding: "var(--space-4) var(--space-5)",
        background: selected ? "var(--accent-faint)" : "transparent",
        width: "100%",
        textAlign: "left",
        fontFamily: "var(--font-body)",
        color: "inherit",
        border: "none",
        borderBottom: "1px solid var(--border)" as string,
        cursor: interactive || onClick ? "pointer" : "default",
        transition: "background 0.12s",
        ...style,
      } as React.CSSProperties}
      onMouseEnter={
        (interactive || onClick)
          ? (e: React.MouseEvent<HTMLElement>) => {
              if (!selected)
                (e.currentTarget as HTMLElement).style.background = "var(--bg-row)";
            }
          : undefined
      }
      onMouseLeave={
        (interactive || onClick)
          ? (e: React.MouseEvent<HTMLElement>) => {
              if (!selected)
                (e.currentTarget as HTMLElement).style.background = "transparent";
            }
          : undefined
      }
    >
      {children}
    </Tag>
  );
}

/* ─── Icon ───────────────────────────────────────────────────────────────────
   Small inline SVG icon set for route-level actions and empty states.
*/
type IconName =
  | "briefcase"
  | "calendar"
  | "check"
  | "close"
  | "clock"
  | "chevron-down"
  | "chevron-up"
  | "edit"
  | "file"
  | "location"
  | "remote"
  | "upload";

export function Icon({
  name,
  size = 16,
  strokeWidth = 2,
  style,
}: {
  name: IconName;
  size?: number;
  strokeWidth?: number;
  style?: React.CSSProperties;
}) {
  const common = {
    width: size,
    height: size,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    "aria-hidden": true,
  };

  switch (name) {
    case "briefcase":
      return (
        <svg {...common} style={style}>
          <path d="M10 6h4a2 2 0 0 1 2 2v1H8V8a2 2 0 0 1 2-2Z" />
          <path d="M4 11h16v7a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-7Z" />
          <path d="M9 11v-1h6v1" />
        </svg>
      );
    case "calendar":
      return (
        <svg {...common} style={style}>
          <rect x="4" y="5" width="16" height="15" rx="2" />
          <path d="M8 3v4M16 3v4M4 9h16" />
        </svg>
      );
    case "check":
      return (
        <svg {...common} style={style}>
          <path d="M20 6 9 17l-5-5" />
        </svg>
      );
    case "close":
      return (
        <svg {...common} style={style}>
          <path d="M6 6l12 12M18 6 6 18" />
        </svg>
      );
    case "clock":
      return (
        <svg {...common} style={style}>
          <circle cx="12" cy="12" r="8" />
          <path d="M12 8v5l3 2" />
        </svg>
      );
    case "chevron-down":
      return (
        <svg {...common} style={style}>
          <path d="m6 9 6 6 6-6" />
        </svg>
      );
    case "chevron-up":
      return (
        <svg {...common} style={style}>
          <path d="m18 15-6-6-6 6" />
        </svg>
      );
    case "edit":
      return (
        <svg {...common} style={style}>
          <path d="M12 20h9" />
          <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5Z" />
        </svg>
      );
    case "file":
      return (
        <svg {...common} style={style}>
          <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8Z" />
          <path d="M14 3v5h5" />
        </svg>
      );
    case "location":
      return (
        <svg {...common} style={style}>
          <path d="M12 21s6-5.3 6-10.2A6 6 0 1 0 6 10.8C6 15.7 12 21 12 21Z" />
          <circle cx="12" cy="10.5" r="2.2" />
        </svg>
      );
    case "remote":
      return (
        <svg {...common} style={style}>
          <rect x="4" y="5" width="16" height="12" rx="2" />
          <path d="M8 19h8M12 17v2" />
        </svg>
      );
    case "upload":
      return (
        <svg {...common} style={style}>
          <path d="M12 16V4" />
          <path d="M7 9l5-5 5 5" />
          <path d="M5 20h14" />
        </svg>
      );
  }
}

/* ─── Modal ──────────────────────────────────────────────────────────────────
   Lightweight dialog shell used for seeker edit and upload flows.
*/
export function Modal({
  open,
  title,
  description,
  children,
  footer,
  onClose,
}: {
  open: boolean;
  title: string;
  description?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
  onClose: () => void;
}) {
  useEffect(() => {
    if (!open) return;
    const previousOverflow = document.body.style.overflow;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };
    document.body.style.overflow = "hidden";
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open, onClose]);

  if (!open) {
    return null;
  }

  return (
    <div
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 200,
        background: "rgba(26, 25, 22, 0.52)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "var(--space-6)",
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        style={{
          width: "min(100%, 640px)",
          maxHeight: "min(88vh, 760px)",
          display: "flex",
          flexDirection: "column",
          background: "var(--bg-card)",
          borderRadius: "var(--radius-lg)",
          border: "1px solid var(--border)",
          boxShadow: "var(--shadow-lg)",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "flex-start",
            justifyContent: "space-between",
            gap: "var(--space-4)",
            padding: "var(--space-6) var(--space-6) var(--space-4)",
            borderBottom: "1px solid var(--border)",
          }}
        >
          <div style={{ minWidth: 0 }}>
            <h2 style={{ fontSize: "1.125rem", marginBottom: description ? "0.35rem" : 0 }}>
              {title}
            </h2>
            {description && (
              <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem", maxWidth: "52ch" }}>
                {description}
              </p>
            )}
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Close dialog"
            style={{
              border: "1px solid var(--border)",
              background: "var(--bg-subtle)",
              color: "var(--ink-2)",
              borderRadius: "999px",
              width: 34,
              height: 34,
              display: "inline-flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <Icon name="close" size={14} />
          </button>
        </div>

        <div
          style={{
            padding: "var(--space-6)",
            overflowY: "auto",
          }}
        >
          {children}
        </div>

        {footer && (
          <div
            style={{
              display: "flex",
              justifyContent: "flex-end",
              gap: "var(--space-3)",
              padding: "var(--space-4) var(--space-6) var(--space-6)",
              borderTop: "1px solid var(--border)",
              background: "var(--bg-row)",
            }}
          >
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}

/* ─── Badge ─────────────────────────────────────────────────────────────────*/
type BadgeTone = "neutral" | "success" | "warn" | "accent";

const badgeStyles: Record<BadgeTone, React.CSSProperties> = {
  neutral: { background: "var(--bg-subtle)", color: "var(--ink-2)" },
  success: { background: "var(--success-faint)", color: "var(--success)" },
  warn:    { background: "var(--warn-faint)",    color: "var(--warn)" },
  accent:  { background: "var(--accent-faint)",  color: "var(--accent)" },
};

export function Badge({
  tone = "neutral",
  children,
}: {
  tone?: BadgeTone;
  children: React.ReactNode;
}) {
  return (
    <span
      style={{
        display: "inline-block",
        padding: "0.1875rem 0.5625rem",
        borderRadius: "999px",
        fontSize: "0.75rem",
        fontWeight: 600,
        letterSpacing: "0.02em",
        lineHeight: 1.4,
        whiteSpace: "nowrap",
        ...badgeStyles[tone],
      }}
    >
      {children}
    </span>
  );
}

/* ─── ScoreBar ───────────────────────────────────────────────────────────────
   Score numerics use the shared .display-num treatment.
───────────────────────────────────────────────────────────────────────────── */
function scoreTone(value: number): BadgeTone {
  if (value >= 0.7) return "success";
  if (value >= 0.4) return "warn";
  return "neutral";
}

function scoreTrackColor(value: number): string {
  if (value >= 0.7) return "var(--success)";
  if (value >= 0.4) return "#B07D20";
  return "var(--ink-muted)";
}

export function ScoreBar({ value, label }: { value: number; label: string }) {
  const clamped = Math.max(0, Math.min(1, value));
  const percent = Math.round(clamped * 100);
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-1)" }}>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          gap: "var(--space-3)",
        }}
      >
        <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>{label}</span>
        {/* Numeric label — this is a sanctioned display-num use */}
        <span
          className="display-num"
          style={{
            fontSize: "0.875rem",
            fontWeight: 700,
            color: scoreTrackColor(clamped),
          }}
        >
          {percent}%
        </span>
      </div>
      <div
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        aria-valuetext={`${percent}% ${label}`}
        style={{
          height: 3,
          borderRadius: 999,
          background: "var(--bg-subtle)",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            height: "100%",
            width: `${percent}%`,
            background: scoreTrackColor(clamped),
            borderRadius: 999,
            transition: "width 0.5s var(--ease-out)",
          }}
        />
      </div>
    </div>
  );
}

/* ─── ScorePill ─────────────────────────────────────────────────────────────
   The large "XX%" callout seen in match lists. Uses display-num class.
───────────────────────────────────────────────────────────────────────────── */
export function ScorePill({ value }: { value: number }) {
  const clamped = Math.max(0, Math.min(1, value));
  const percent = Math.round(clamped * 100);
  const color = scoreTrackColor(clamped);
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        width: 48,
        height: 48,
        borderRadius: "var(--radius-sm)",
        background: "var(--bg-subtle)",
        border: "1px solid var(--border)",
        flexShrink: 0,
      }}
    >
      <span
        className="display-num"
        style={{ fontSize: "1rem", fontWeight: 700, lineHeight: 1, color }}
      >
        {percent}
      </span>
      <span style={{ fontSize: "0.625rem", color: "var(--ink-faint)", letterSpacing: "0.03em" }}>
        %
      </span>
    </div>
  );
}

/* ─── StatTile ───────────────────────────────────────────────────────────────
   Compact metric tile. Value uses display-num; label and sub use body font.
───────────────────────────────────────────────────────────────────────────── */
export function StatTile({
  label,
  value,
  sub,
}: {
  label: string;
  value: string | number;
  sub?: string;
}) {
  return (
    <div
      style={{
        background: "var(--bg-card)",
        border: "1px solid var(--border)",
        borderRadius: "var(--radius)",
        padding: "var(--space-5) var(--space-6)",
        boxShadow: "var(--shadow-xs)",
        display: "flex",
        flexDirection: "column",
        gap: "var(--space-1)",
      }}
    >
      <span
        style={{
          fontSize: "0.6875rem",
          fontWeight: 600,
          letterSpacing: "0.07em",
          textTransform: "uppercase",
          color: "var(--ink-muted)",
        }}
      >
        {label}
      </span>
      {/* Sanctioned display-num use */}
      <span
        className="display-num"
        style={{ fontSize: "1.75rem", fontWeight: 700, lineHeight: 1.1, color: "var(--ink)" }}
      >
        {value}
      </span>
      {sub && (
        <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)", marginTop: "2px" }}>
          {sub}
        </span>
      )}
    </div>
  );
}

/* ─── Divider ────────────────────────────────────────────────────────────────*/
export function Divider({ label }: { label?: string }) {
  if (!label) {
    return (
      <hr
        style={{
          border: "none",
          borderTop: "1px solid var(--border)",
          margin: "var(--space-5) 0",
        }}
      />
    );
  }
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: "var(--space-3)",
        color: "var(--ink-faint)",
        fontSize: "0.6875rem",
        fontWeight: 600,
        letterSpacing: "0.07em",
        textTransform: "uppercase",
        margin: "var(--space-6) 0 var(--space-4)",
      }}
    >
      <div style={{ flex: 1, height: "1px", background: "var(--border)" }} />
      {label}
      <div style={{ flex: 1, height: "1px", background: "var(--border)" }} />
    </div>
  );
}

/* ─── Spinner ────────────────────────────────────────────────────────────────*/
export function Spinner({ size = 20 }: { size?: number }) {
  return (
    <>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
      <div
        style={{
          width: size,
          height: size,
          border: "2px solid var(--border)",
          borderTopColor: "var(--accent)",
          borderRadius: "50%",
          animation: "spin 0.7s linear infinite",
          display: "inline-block",
          flexShrink: 0,
        }}
      />
    </>
  );
}

/* ─── EmptyState ─────────────────────────────────────────────────────────────
   Replaces the "no data" Card pattern with a lighter, purpose-built treatment.
───────────────────────────────────────────────────────────────────────────── */
export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: React.ReactNode;
}) {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        textAlign: "center",
        padding: "var(--space-12) var(--space-6)",
        border: "1px dashed var(--border)",
        borderRadius: "var(--radius)",
        background: "var(--bg-card)",
      }}
    >
      <p
        style={{
          fontWeight: 600,
          color: "var(--ink-2)",
          fontSize: "0.9375rem",
          marginBottom: description ? "var(--space-2)" : 0,
        }}
      >
        {title}
      </p>
      {description && (
        <p
          style={{
            color: "var(--ink-muted)",
            fontSize: "0.875rem",
            maxWidth: "40ch",
            marginBottom: action ? "var(--space-5)" : 0,
          }}
        >
          {description}
        </p>
      )}
      {action}
    </div>
  );
}

/* ─── SectionHeader ──────────────────────────────────────────────────────────
   Consistent heading + optional action link used before a list or panel.
───────────────────────────────────────────────────────────────────────────── */
export function SectionHeader({
  title,
  action,
}: {
  title: string;
  action?: React.ReactNode;
}) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "baseline",
        justifyContent: "space-between",
        gap: "var(--space-4)",
        marginBottom: "var(--space-4)",
      }}
    >
      <h2 style={{ fontSize: "1rem", fontWeight: 600, color: "var(--ink)" }}>{title}</h2>
      {action && (
        <span style={{ fontSize: "0.8125rem", color: "var(--accent)", fontWeight: 500 }}>
          {action}
        </span>
      )}
    </div>
  );
}

/* ─── GlobalStyles ───────────────────────────────────────────────────────────*/
export function GlobalStyles() {
  return (
    <style>{`
      @keyframes spin    { to { transform: rotate(360deg); } }
      @keyframes fadeUp  { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
      @keyframes fadeIn  { from { opacity: 0; } to { opacity: 1; } }

      .fade-up { animation: fadeUp 0.4s var(--ease-out) both; }
      .fade-in { animation: fadeIn 0.3s ease both; }

      /* Shared numeric callout treatment */
      .display-num { font-family: var(--font-display); font-weight: 700; line-height: 1; }

      button:not(:disabled):hover { filter: brightness(0.92); }
      input::placeholder { color: var(--ink-faint); }
    `}</style>
  );
}
