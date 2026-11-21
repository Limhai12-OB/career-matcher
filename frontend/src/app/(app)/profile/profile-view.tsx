"use client";

import { toast } from "sonner";

import { ProfileForm } from "@/components/forms/profile-form";
import { PageHeader } from "@/components/shell/app-shell";
import { ErrorState, PageLoading } from "@/components/states/states";
import { Card, CardContent } from "@/components/ui/card";
import { useProfile } from "@/lib/api/hooks";
import { profileToValues } from "@/lib/validation/schemas";

export function ProfileView() {
  const { data, isPending, isError, error, refetch } = useProfile();

  return (
    <div className="animate-fade-in">
      <PageHeader title="Profile" description="How you present yourself to employers." />
      {isPending ? (
        <PageLoading label="Loading profile" />
      ) : isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <Card>
          <CardContent>
            <ProfileForm
              key={data.updatedAt}
              initial={profileToValues(data)}
              onSaved={() => toast.success("Profile saved")}
            />
          </CardContent>
        </Card>
      )}
    </div>
  );
}
