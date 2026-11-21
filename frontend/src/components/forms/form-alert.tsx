import { AlertCircle } from "lucide-react";

import { Alert, AlertDescription } from "@/components/ui/alert";

export function FormAlert({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <Alert variant="destructive" className="animate-fade-in">
      <AlertCircle />
      <AlertDescription>{message}</AlertDescription>
    </Alert>
  );
}
