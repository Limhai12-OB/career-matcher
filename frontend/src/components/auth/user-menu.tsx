"use client";

import { LogOut, Settings, User } from "lucide-react";
import Link from "next/link";
import { toast } from "sonner";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useProfile } from "@/lib/api/hooks";
import { logout } from "@/lib/auth/session";
import { useSession } from "@/lib/auth/use-session";

export function initials(name: string | undefined, fallback: string): string {
  const parts = (name ?? "").trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return fallback.slice(0, 1).toUpperCase();
  return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : "")).toUpperCase();
}

export function UserMenu() {
  const { user } = useSession();
  const isAdmin = user?.role === "ADMIN";
  const { data: profile } = useProfile({ enabled: !!user && !isAdmin });
  async function signOut() {
    try {
      await logout();
    } catch {
      toast.warning("Signed out on this device. The server could not be reached.");
    }
    // RequireAuth redirects to /login once the session is cleared.
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" className="h-10 gap-2 px-2" aria-label="Account menu">
          <Avatar className="size-8">
            <AvatarFallback className="bg-brand-100 text-xs font-semibold text-brand-700">
              {initials(profile?.fullName, user?.email ?? "?")}
            </AvatarFallback>
          </Avatar>
          <span className="hidden max-w-40 truncate text-sm font-medium sm:inline">
            {profile?.fullName ?? user?.email}
          </span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        <DropdownMenuLabel className="font-normal">
          <p className="truncate text-sm font-medium">
            {isAdmin ? "Administrator" : (profile?.fullName ?? "Your account")}
          </p>
          <p className="truncate text-xs text-muted-foreground">{user?.email}</p>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        {!isAdmin && (
          <DropdownMenuItem asChild>
            <Link href="/profile">
              <User /> Profile
            </Link>
          </DropdownMenuItem>
        )}
        <DropdownMenuItem asChild>
          <Link href="/settings">
            <Settings /> Settings
          </Link>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => void signOut()}>
          <LogOut /> Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
