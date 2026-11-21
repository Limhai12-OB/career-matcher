/** A verbatim quote from the candidate's CV that supports an extracted item. */
export function Evidence({ text }: { text: string }) {
  return (
    <blockquote className="mt-1.5 border-l-2 border-brand-300 pl-3 text-xs leading-relaxed text-muted-foreground italic">
      <span className="sr-only">From your CV: </span>“{text}”
    </blockquote>
  );
}
