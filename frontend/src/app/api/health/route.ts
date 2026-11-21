import { connection } from "next/server";

/** Frontend liveness probe. Does not depend on the backend. */
export async function GET() {
  await connection(); // evaluate per request rather than prerendering at build time
  return Response.json({ status: "ok", service: "frontend" });
}
