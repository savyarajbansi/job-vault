# JobVault Adaptive Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship one role-aware React shell that verifies sessions before showing protected content, routes seekers into an adaptive readiness workspace, and exposes real employer and admin shell actions with explainable states.

**Architecture:** Keep the frontend split by responsibility. Shared session and navigation logic lives in small layout helpers, API calls stay in focused client modules, and each route owns its own workspace surface. The seeker flow is state-driven, not wizard-driven, so the main screen can show readiness, matches, and skill gaps at once while still guiding first-run users toward resume upload and profile completion.

**Tech Stack:** React 18, React Router 6, TypeScript, Vite, Vitest, browser-native form controls, existing fetch wrapper in `src/api/client.ts`.

---

### Task 1: Add shared session gating and role-aware shell navigation

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/layout/AppLayout.tsx`
- Create: `frontend/src/layout/session.ts`
- Create: `frontend/src/layout/session.test.ts`
- Create: `frontend/src/routes/AccessDenied.tsx`

- [ ] **Step 1: Write the failing test**

Write `frontend/src/layout/session.test.ts` with pure helper assertions for:

```ts
expect(isAuthRecoveryError(new Error("ERR_AUTH_003: Session expired. Please sign in again."))).toBe(true);
expect(isAuthRecoveryError(new Error("ERR_AUTH_002: expired"))).toBe(false);
expect(formatRoleLabel("JOB_SEEKER")).toBe("Seeker");
expect(formatRoleLabel("EMPLOYER")).toBe("Employer");
expect(formatRoleLabel("ADMIN")).toBe("Admin");
```

Also add a render test for the layout shell that proves the auth-checking state is shown before protected navigation when the session has not been verified.

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- frontend/src/layout/session.test.ts`
Expected: fail because `session.ts` and the new shell behavior do not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/layout/session.ts` with the smallest helpers needed for:

```ts
export function isAuthRecoveryError(error: unknown): boolean {
  return error instanceof Error && error.message.includes("ERR_AUTH_003");
}

export function formatRoleLabel(role: string): string {
  if (role === "JOB_SEEKER") return "Seeker";
  if (role === "EMPLOYER") return "Employer";
  if (role === "ADMIN") return "Admin";
  return role;
}
```

Update `AppLayout` so it:
- checks `getAccessToken()` on mount,
- calls `whoami()` only when a token exists,
- shows a neutral session-checking state until verification completes,
- clears stale auth state and shows `Your session has ended. Please sign in again.` when verification fails with `ERR_AUTH_003`,
- renders role-aware navigation after verification,
- keeps inaccessible destinations hidden unless the user has that role,
- routes unauthorized role access to a dedicated `AccessDenied` page instead of a generic 404.

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- frontend/src/layout/session.test.ts`
Expected: pass with no new warnings.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/App.tsx frontend/src/layout/AppLayout.tsx frontend/src/layout/session.ts frontend/src/layout/session.test.ts frontend/src/routes/AccessDenied.tsx
git commit -m "feat: gate the shell on verified sessions"
```

### Task 2: Rebuild the seeker workspace around readiness, ranked matches, and skill gaps

**Files:**
- Modify: `frontend/src/api/seeker.ts`
- Create: `frontend/src/api/seeker.test.ts`
- Modify: `frontend/src/routes/SeekerWorkspace.tsx`
- Create: `frontend/src/routes/seeker/readiness.ts`
- Create: `frontend/src/routes/seeker/readiness.test.ts`
- Create: `frontend/src/routes/seeker/SeekerWorkspacePanels.tsx`

- [ ] **Step 1: Write the failing test**

Add API tests for the seeker profile endpoints in `frontend/src/api/seeker.test.ts`:

```ts
expect(authorizedRequest).toHaveBeenCalledWith("/api/seeker/profile", { method: "GET" });
expect(authorizedRequest).toHaveBeenCalledWith("/api/seeker/profile", {
  method: "PATCH",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    preferredSector: "Platform",
    preferredLocation: "Kathmandu",
    remoteOk: true,
    yearsExperience: 7
  })
});
```

Add `frontend/src/routes/seeker/readiness.test.ts` for the advisory logic:

```ts
expect(shouldShowGenericMatchWarning({ yearsExperience: null, preferredLocation: null, remoteOk: null })).toBe(true);
expect(shouldShowGenericMatchWarning({ yearsExperience: 4, preferredLocation: "Kathmandu", remoteOk: true })).toBe(false);
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:
`npm test -- frontend/src/api/seeker.test.ts frontend/src/routes/seeker/readiness.test.ts`

Expected: fail because the profile API methods and readiness helpers are missing.

- [ ] **Step 3: Write minimal implementation**

Extend `frontend/src/api/seeker.ts` with:
- `getSeekerProfile()`
- `updateSeekerProfile(payload)`
- a `SeekerProfileResponse` type that matches `/api/seeker/profile`

Add `frontend/src/routes/seeker/readiness.ts` with small helpers for:
- showing the profile prompt only when the three profile fields are missing,
- showing the generic match warning when profile fields are incomplete,
- deriving a compact status label for resume, profile, and match readiness.

Refactor `SeekerWorkspace.tsx` into a main route plus local panels that cover:
- resume upload with explicit PDF-only messaging,
- profile setup card with `Years of experience`, `Preferred location`, and `Remote OK`,
- save and skip actions,
- ranked matches with score factors and missing skills,
- skill-gap lookup for the selected job,
- neutral empty, loading, and error states without decorative dashboards.

- [ ] **Step 4: Run the tests to verify they pass**

Run:
`npm test -- frontend/src/api/seeker.test.ts frontend/src/routes/seeker/readiness.test.ts`

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/seeker.ts frontend/src/api/seeker.test.ts frontend/src/routes/SeekerWorkspace.tsx frontend/src/routes/seeker/readiness.ts frontend/src/routes/seeker/readiness.test.ts frontend/src/routes/seeker/SeekerWorkspacePanels.tsx
git commit -m "feat: make seeker matching adaptive"
```

### Task 3: Add the employer shell and candidate match client

**Files:**
- Create: `frontend/src/api/employer.ts`
- Create: `frontend/src/api/employer.test.ts`
- Create: `frontend/src/routes/EmployerWorkspace.tsx`
- Create: `frontend/src/routes/employer/EmployerWorkspace.test.ts`

- [ ] **Step 1: Write the failing test**

Add `frontend/src/api/employer.test.ts` for the candidate match endpoint:

```ts
expect(authorizedRequest).toHaveBeenCalledWith(
  "/api/employer/jobs/4f7026fc-6f3d-42c3-b09c-b7b491aabdfd/matches/candidates?limit=20&offset=0",
  { method: "GET" }
);
```

Add a workspace test that proves the empty shell shows a create-posting prompt before a job ID is entered, and that a selected job renders ranked candidate matches when the API returns items.

- [ ] **Step 2: Run the test to verify it fails**

Run:
`npm test -- frontend/src/api/employer.test.ts frontend/src/routes/employer/EmployerWorkspace.test.ts`

Expected: fail because the new API module and workspace do not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/api/employer.ts` with the candidate match types and a `getEmployerCandidateMatches(jobId, { limit, offset })` function that wraps `authorizedRequest`.

Create `EmployerWorkspace.tsx` as a shell-level surface with:
- a create-posting prompt when no job ID is selected,
- a job ID field for loading candidate matches,
- ranked candidate rows with score, factor breakdown, and missing skills,
- empty and service-unavailable states that use the real backend text.

- [ ] **Step 4: Run the tests to verify they pass**

Run:
`npm test -- frontend/src/api/employer.test.ts frontend/src/routes/employer/EmployerWorkspace.test.ts`

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/employer.ts frontend/src/api/employer.test.ts frontend/src/routes/EmployerWorkspace.tsx frontend/src/routes/employer/EmployerWorkspace.test.ts
git commit -m "feat: add employer candidate review shell"
```

### Task 4: Expand the admin surface to include moderation actions

**Files:**
- Modify: `frontend/src/api/admin.ts`
- Create: `frontend/src/api/admin.test.ts`
- Modify: `frontend/src/routes/AdminMetrics.tsx`
- Create: `frontend/src/routes/admin/moderation.test.ts`

- [ ] **Step 1: Write the failing test**

Extend `frontend/src/api/admin.test.ts` with endpoint coverage for moderation actions:

```ts
expect(authorizedRequest).toHaveBeenCalledWith("/api/admin/jobs/123/approve", { method: "POST" });
expect(authorizedRequest).toHaveBeenCalledWith("/api/admin/jobs/123/reject", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ moderationReason: "Policy violation" })
});
expect(authorizedRequest).toHaveBeenCalledWith("/api/admin/jobs/123/disable", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ moderationReason: "Spam" })
});
```

Add a moderation UI test that proves:
- approve is available without a reason,
- reject and disable require a nonblank reason,
- conflict errors render the invalid transition message instead of collapsing into a generic failure.

- [ ] **Step 2: Run the test to verify it fails**

Run:
`npm test -- frontend/src/api/admin.test.ts frontend/src/routes/admin/moderation.test.ts`

Expected: fail because the moderation API functions and UI are not wired up yet.

- [ ] **Step 3: Write minimal implementation**

Add the moderation API functions in `frontend/src/api/admin.ts`:
- `approveAdminJob(jobId)`
- `rejectAdminJob(jobId, moderationReason)`
- `disableAdminJob(jobId, moderationReason)`

Update `AdminMetrics.tsx` so it remains a metrics view but now includes a moderation list section with:
- selected job ID,
- approve, reject, disable actions,
- required moderation reason input for reject and disable,
- clear loading, success, unauthorized, and conflict states.

- [ ] **Step 4: Run the tests to verify they pass**

Run:
`npm test -- frontend/src/api/admin.test.ts frontend/src/routes/admin/moderation.test.ts`

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/admin.ts frontend/src/api/admin.test.ts frontend/src/routes/AdminMetrics.tsx frontend/src/routes/admin/moderation.test.ts
git commit -m "feat: surface admin moderation controls"
```

### Task 5: Restyle the shell for clarity, then verify the full build in browser and tests

**Files:**
- Modify: `frontend/src/index.css`
- Modify: `frontend/src/layout/AppLayout.tsx`
- Modify: `frontend/src/routes/Home.tsx`
- Modify: `frontend/src/routes/NotFound.tsx`

- [ ] **Step 1: Write the failing test**

Add a small smoke test for the layout copy or helper state that proves the shell can render the neutral loading state, the signed-in nav, and the unauthorized state without overlapping content.

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test`
Expected: the new smoke test fails until the shared shell copy and CSS are updated.

- [ ] **Step 3: Write minimal implementation**

Update the CSS to use restrained tinted neutrals, visible focus states, compact panels, and stable responsive spacing. Keep the product calm and evidence-first:
- no decorative dashboard framing,
- no card-on-card nesting,
- no startup-gradient palette,
- no hidden focus indicators,
- no protected-content flash during session verification.

Make `Home.tsx` route users to the actual working surfaces, and make `NotFound.tsx` and `AccessDenied.tsx` explicit about the problem instead of generic.

- [ ] **Step 4: Run the full verification**

Run:
`npm test`
`npm run build`

Then open the app in the browser, check the shell at desktop and mobile widths, and verify:
- the auth-checking state appears before protected content,
- seeker readiness shows the profile prompt only when appropriate,
- employer and admin routes are accessible only to the right roles,
- no layout clipping occurs on narrow screens.

Expected: tests pass, build passes, and browser checks show the adaptive shell without obvious overflow or content flashing.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/index.css frontend/src/layout/AppLayout.tsx frontend/src/routes/Home.tsx frontend/src/routes/NotFound.tsx
git commit -m "feat: polish the adaptive jobvault shell"
```

## Self-Review

- [ ] The plan covers the shell/auth state, seeker readiness, employer candidate review, admin moderation, and shared styling.
- [ ] Every task has exact file paths and a test-first sequence.
- [ ] No step says "implement later" or leaves behavior unspecified.
- [ ] The plan stays within the current Vite/React frontend instead of inventing a new framework.
- [ ] The plan preserves the real backend endpoints already present in the repo and only adds thin client wrappers where the frontend is missing them.
