import { RequireAuth } from "@/components/auth/require-auth";
import { UserMenu } from "@/components/auth/user-menu";
import { Logo } from "@/components/brand/logo";

export default function OnboardingLayout({ children }: { children: React.ReactNode }) {
  return (
    <RequireAuth onboarding="only">
      <div className="flex min-h-svh flex-1 flex-col">
        <header className="flex h-16 items-center justify-between border-b bg-background px-4 sm:px-6">
          <Logo />
          <UserMenu />
        </header>
        <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-10 sm:px-6">{children}</main>
      </div>
    </RequireAuth>
  );
}
