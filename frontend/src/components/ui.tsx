import React from "react";

/* ─── Button ──────────────────────────────────────────────────── */
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
  gap: "0.5rem",
  fontFamily: "var(--font-body)",
  fontWeight: 500,
  borderRadius: "var(--radius-sm)",
  border: "1.5px solid transparent",
  transition: "background 0.15s, border-color 0.15s, color 0.15s, box-shadow 0.15s, opacity 0.15s",
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
  sm: { padding: "0.375rem 0.875rem", fontSize: "0.8125rem" },
  md: { padding: "0.625rem 1.25rem", fontSize: "0.9375rem" },
  lg: { padding: "0.75rem 1.5rem", fontSize: "1rem" },
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
        ...(isDisabled ? { opacity: 0.55, cursor: "not-allowed" } : {}),
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

/* ─── Input ───────────────────────────────────────────────────── */
interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  hint?: string;
}

export function Input({ label, error, hint, id, style, ...props }: InputProps) {
  const inputId = id ?? label.toLowerCase().replace(/\s+/g, "-");
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "0.375rem" }}>
      <label
        htmlFor={inputId}
        style={{
          fontSize: "0.8125rem",
          fontWeight: 500,
          color: "var(--ink-2)",
          letterSpacing: "0.02em",
        }}
      >
        {label}
      </label>
      <input
        id={inputId}
        style={{
          padding: "0.625rem 0.875rem",
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
            ? "0 0 0 3px rgba(192,98,42,0.12)"
            : "0 0 0 3px rgba(45,110,110,0.12)";
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

/* ─── Alert ───────────────────────────────────────────────────── */
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
        padding: "0.75rem 1rem",
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

/* ─── Card ────────────────────────────────────────────────────── */
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
        ...(padded ? { padding: "1.5rem" } : {}),
        ...style,
      }}
    >
      {children}
    </div>
  );
}

/* ─── Badge ───────────────────────────────────────────────────── */
type BadgeTone = "neutral" | "success" | "warn" | "accent";

const badgeStyles: Record<BadgeTone, React.CSSProperties> = {
  neutral: { background: "var(--bg-subtle)", color: "var(--ink-2)" },
  success: { background: "var(--success-faint)", color: "var(--success)" },
  warn: { background: "var(--warn-faint)", color: "var(--warn)" },
  accent: { background: "var(--accent-faint)", color: "var(--accent)" },
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
        padding: "0.1875rem 0.625rem",
        borderRadius: "999px",
        fontSize: "0.75rem",
        fontWeight: 500,
        letterSpacing: "0.03em",
        ...badgeStyles[tone],
      }}
    >
      {children}
    </span>
  );
}

/* ─── Divider ─────────────────────────────────────────────────── */
export function Divider({ label }: { label?: string }) {
  if (!label) {
    return (
      <hr
        style={{
          border: "none",
          borderTop: "1px solid var(--border)",
        }}
      />
    );
  }
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: "0.75rem",
        color: "var(--ink-faint)",
        fontSize: "0.8125rem",
      }}
    >
      <div style={{ flex: 1, height: "1px", background: "var(--border)" }} />
      {label}
      <div style={{ flex: 1, height: "1px", background: "var(--border)" }} />
    </div>
  );
}

/* ─── Spinner ─────────────────────────────────────────────────── */
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

/* ─── Global keyframes injection ─────────────────────────────── */
export function GlobalStyles() {
  return (
    <style>{`
      @keyframes spin { to { transform: rotate(360deg); } }
      @keyframes fadeUp {
        from { opacity: 0; transform: translateY(10px); }
        to   { opacity: 1; transform: translateY(0); }
      }
      @keyframes fadeIn {
        from { opacity: 0; }
        to   { opacity: 1; }
      }
      @keyframes slideIn {
        from { opacity: 0; transform: translateX(-8px); }
        to   { opacity: 1; transform: translateX(0); }
      }
      .fade-up { animation: fadeUp 0.4s var(--ease-out) both; }
      .fade-in { animation: fadeIn 0.3s ease both; }

      button:not(:disabled):hover {
        filter: brightness(0.92);
      }
      input::placeholder { color: var(--ink-faint); }
    `}</style>
  );
}
