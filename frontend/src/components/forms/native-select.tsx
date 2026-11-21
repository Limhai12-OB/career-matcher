import { ChevronDown } from "lucide-react";
import * as React from "react";

import { cn } from "@/lib/utils";

/** Styled native <select>: accessible, mobile-friendly, works well with long lists. */
export const NativeSelect = React.forwardRef<HTMLSelectElement, React.ComponentProps<"select">>(
  function NativeSelect({ className, children, ...props }, ref) {
    return (
      <div className="relative">
        <select
          ref={ref}
          className={cn(
            "h-9 w-full appearance-none rounded-lg border border-input bg-transparent py-1 pr-9 pl-3 text-sm shadow-xs transition-[color,box-shadow] outline-none",
            "focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50",
            "disabled:cursor-not-allowed disabled:opacity-50",
            "aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20",
            className,
          )}
          {...props}
        >
          {children}
        </select>
        <ChevronDown
          className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden
        />
      </div>
    );
  },
);
