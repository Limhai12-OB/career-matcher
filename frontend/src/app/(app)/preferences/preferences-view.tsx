"use client";

import { toast } from "sonner";

import { PreferencesForm } from "@/components/forms/preferences-form";
import { PageHeader } from "@/components/shell/app-shell";
import { ErrorState, PageLoading } from "@/components/states/states";
import { Card, CardContent } from "@/components/ui/card";
import { usePreferences } from "@/lib/api/hooks";
import { preferencesToValues } from "@/lib/validation/schemas";

export function PreferencesView() {
  const { data, isPending, isError, error, refetch } = usePreferences();

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Career preferences"
        description="The roles, work arrangements and conditions you're looking for."
      />
      {isPending ? (
        <PageLoading label="Loading preferences" />
      ) : isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <Card>
          <CardContent>
            <PreferencesForm
              key={data.updatedAt}
              initial={preferencesToValues(data)}
              onSaved={() => toast.success("Preferences saved")}
            />
          </CardContent>
        </Card>
      )}
    </div>
  );
}
