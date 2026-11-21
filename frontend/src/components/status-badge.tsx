import type { ServiceState } from "@/lib/health";

const STYLES: Record<ServiceState, string> = {
  UP: "bg-emerald-50 text-emerald-700 ring-emerald-600/20",
  DOWN: "bg-red-50 text-red-700 ring-red-600/20",
  UNREACHABLE: "bg-amber-50 text-amber-800 ring-amber-600/20",
};

export function StatusBadge({ state }: { state: ServiceState }) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${STYLES[state]}`}
    >
      {state}
    </span>
  );
}
