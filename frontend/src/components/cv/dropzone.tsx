"use client";

import { FileUp, Loader2 } from "lucide-react";
import { useId, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { ACCEPTED_CV_TYPES, validateCvFile } from "@/lib/cv";
import { cn } from "@/lib/utils";

interface DropzoneProps {
  onFile: (file: File) => void;
  disabled?: boolean;
  busy?: boolean;
  disabledReason?: string;
}

/** Drag-and-drop or click-to-browse file picker for a single CV. Keyboard accessible. */
export function CvDropzone({ onFile, disabled, busy, disabledReason }: DropzoneProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const hintId = useId();
  const errorId = useId();

  function handle(file: File | undefined) {
    if (!file) return;
    const problem = validateCvFile(file);
    setError(problem);
    if (!problem) onFile(file);
  }

  return (
    <div>
      <div
        onDragOver={(e) => {
          e.preventDefault();
          if (!disabled) setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          if (!disabled) handle(e.dataTransfer.files?.[0]);
        }}
        className={cn(
          "flex flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed px-6 py-10 text-center transition-colors",
          dragging ? "border-primary bg-accent" : "border-border bg-card",
          disabled && "opacity-60",
        )}
      >
        <span className="grid size-12 place-items-center rounded-full bg-accent text-accent-foreground">
          {busy ? <Loader2 className="size-6 animate-spin" aria-hidden /> : <FileUp className="size-6" aria-hidden />}
        </span>
        <div>
          <p className="font-medium">{busy ? "Uploading…" : "Drop your CV here"}</p>
          <p id={hintId} className="mt-1 text-sm text-muted-foreground">
            PDF or Word (.docx), up to 5 MB. Your file stays private and encrypted.
          </p>
        </div>
        <Button
          type="button"
          onClick={() => inputRef.current?.click()}
          disabled={disabled}
          aria-describedby={`${hintId} ${error ? errorId : ""}`}
        >
          Choose file
        </Button>
        <input
          ref={inputRef}
          type="file"
          accept={ACCEPTED_CV_TYPES}
          className="sr-only"
          tabIndex={-1}
          aria-label="CV file"
          data-testid="cv-file-input"
          onChange={(e) => {
            handle(e.target.files?.[0]);
            e.target.value = ""; // allow re-selecting the same file
          }}
        />
        {disabled && disabledReason && <p className="text-sm text-muted-foreground">{disabledReason}</p>}
      </div>
      {error && (
        <p id={errorId} role="alert" className="mt-2 text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
