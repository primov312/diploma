import type { ReactNode } from "react";

export type SectionHeaderProps = {
  title: ReactNode;
  subtitle?: ReactNode;
  align?: "left" | "center";
  className?: string;
  titleClassName?: string;
  subtitleClassName?: string;
  eyebrow?: ReactNode;
};

const SectionHeader = ({
  title,
  subtitle,
  align = "center",
  className = "",
  titleClassName = "",
  subtitleClassName = "",
  eyebrow,
}: SectionHeaderProps) => {
  const containerClassName = [
    "mb-16",
    align === "center" ? "text-center" : "text-left",
    className,
  ]
    .filter(Boolean)
    .join(" ");

  const computedTitleClassName = [
    "mb-4 text-3xl font-bold text-gray-800 lg:text-4xl",
    titleClassName,
  ]
    .filter(Boolean)
    .join(" ");

  const computedSubtitleClassName = [
    "text-xl text-gray-600",
    align === "center" ? "mx-auto max-w-3xl" : "",
    subtitleClassName,
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <div className={containerClassName}>
      {eyebrow ? (
        <p className="mb-3 text-sm font-semibold uppercase tracking-wide text-primary">
          {eyebrow}
        </p>
      ) : null}
      <h2 className={computedTitleClassName}>{title}</h2>
      {subtitle ? (
        <p className={computedSubtitleClassName}>{subtitle}</p>
      ) : null}
    </div>
  );
};

export default SectionHeader;
