"use client";

import { X } from "lucide-react";
import Link from "next/link";
import { toast } from "sonner";

import { PageHeader } from "@/components/shell/app-shell";
import { EmptyState, ErrorState, PageLoading } from "@/components/states/states";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { userMessage } from "@/lib/api/errors";
import { useCandidateSkills, useRemoveSkill } from "@/lib/api/hooks";
import { groupByCategory } from "@/lib/cv";

export function SkillsView() {
  const skills = useCandidateSkills();
  const remove = useRemoveSkill();

  async function onRemove(skillId: string, name: string) {
    try {
      await remove.mutateAsync(skillId);
      toast.success(`${name} removed from your profile.`);
    } catch (err) {
      toast.error(userMessage(err));
    }
  }

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="My skills"
        description="The skills used to match you with jobs. Add skills by analyzing your CV."
        actions={
          <Button asChild variant="outline">
            <Link href="/cv">Analyze a CV</Link>
          </Button>
        }
      />
      {skills.isPending ? (
        <PageLoading label="Loading skills" />
      ) : skills.isError ? (
        <ErrorState error={skills.error} onRetry={() => void skills.refetch()} />
      ) : skills.data.length === 0 ? (
        <EmptyState
          title="No skills yet"
          description="Upload your CV and confirm the skills we find to build your skill profile."
          action={
            <Button asChild>
              <Link href="/cv">Upload your CV</Link>
            </Button>
          }
        />
      ) : (
        <div className="grid gap-5 md:grid-cols-2">
          {groupByCategory(skills.data).map((group) => (
            <Card key={group.category}>
              <CardHeader>
                <CardTitle className="text-base">{group.label}</CardTitle>
              </CardHeader>
              <CardContent>
                <ul className="flex flex-wrap gap-2">
                  {group.items.map((s) => (
                    <li
                      key={s.skillId}
                      className="inline-flex items-center gap-1 rounded-full border bg-secondary py-1 pr-1 pl-3 text-sm"
                      title={s.evidence ? `From your CV: “${s.evidence}”` : undefined}
                    >
                      {s.name}
                      <button
                        type="button"
                        onClick={() => void onRemove(s.skillId, s.name)}
                        disabled={remove.isPending}
                        className="rounded-full p-1 text-muted-foreground hover:bg-foreground/10 hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
                        aria-label={`Remove ${s.name}`}
                      >
                        <X className="size-3.5" />
                      </button>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
