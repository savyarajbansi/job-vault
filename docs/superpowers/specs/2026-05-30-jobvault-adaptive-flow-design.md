# JobVault Adaptive Flow Design

Date: 2026-05-30

## Status

Confirmed design brief for implementation planning. This document defines a mid-fi role-aware app shell with an adaptive seeker readiness model, not a rigid wizard.

## Context

JobVault serves job seekers, employers, and admins. The primary frontend flow should help seekers reach ranked matches with enough data for the score to be meaningful. Employer and admin flows are secondary shell-level destinations, but the backend already includes real candidate-matching and moderation endpoints, so the UI should not describe those areas as fictional placeholders.

Relevant source-of-truth files:

- `PRODUCT.md` for product purpose, tone, anti-references, and accessibility goals.
- `DESIGN.md` for current visual constraints and restrained product direction.
- `frontend/src/api/auth.ts` for access-token persistence, refresh behavior, and `ERR_AUTH_003`.
- `src/main/java/com/project8/jobvault/matching/EmployerCandidateMatchController.java` for employer candidate matching.
- `src/main/java/com/project8/jobvault/admin/AdminJobModerationController.java` for admin moderation actions.

## Goals

- Provide one shared role-aware app shell after sign-in.
- Make seeker ranked matches the primary destination.
- Guide incomplete seekers toward resume upload and profile setup without enforcing a brittle linear wizard.
- Let returning users with ready data land directly on ranked matches.
- Keep employer and admin areas shell-level, while representing implemented backend capabilities accurately.
- Handle stale localStorage access tokens and missing or invalid refresh cookies without flashing protected content.

## Non-Goals

- Do not fully design the employer product area beyond shell-level job posting and candidate review.
- Do not fully design the admin product area beyond metrics and moderation entry/action patterns.
- Do not introduce a rigid step-indexed onboarding wizard.
- Do not invent external design references or require visual artifacts that are not checked into the repo.

## Design Direction

Use a restrained product UI. The interface should be light, calm, and readable, with tinted neutrals, clear boundaries, visible focus states, compact evidence-first layouts, and minimal accent color for primary actions or status.

The chosen direction is a guided seeker path for first-run users and an adaptive match workspace for returning users. The physical scene is a job seeker returning to a practical hiring workspace on a laptop, checking whether their profile and resume produce trustworthy matches.

The app should feel like a dependable workbench: direct actions, clear state, readable evidence, and no decorative dashboard theater.

## App Shell

The app shell is role-aware after sign-in:

- `Seeker` is the primary destination.
- `Employer` appears only for employer-capable users.
- `Admin` appears only for admin-capable users.
- Account and session recovery remain globally accessible.

The shell must not render users as confidently authenticated until the current session is verified. On initial load, if an access token exists in localStorage, render a neutral session-checking state while the first authorized request resolves.

If the next authorized request fails with `ERR_AUTH_003`, clear stale auth state and show:

`Your session has ended. Please sign in again.`

Protected shell content should not flash during this transitional state.

## Seeker Flow

The seeker flow is state-driven, not step-index-driven.

First-run happy path:

1. Sign in.
2. Upload resume.
3. Complete profile fields.
4. View ranked matches.

Returning-user path:

1. Shell checks auth.
2. If session is valid and seeker data is ready, route directly to ranked matches.
3. A readiness panel remains available for updating resume or profile but does not block the main view.

Readiness rules:

- Resume upload is required for skill-based matching because skills come from parsed resume text.
- Profile setup improves experience and location scoring.
- If `yearsExperience`, `preferredLocation`, and `remoteOk` are all null, show a soft profile completion prompt before loading or emphasizing matches.
- The profile prompt is advisory, not a hard block.
- If the seeker skips profile setup, show matches with a generic-score warning.

Profile setup card:

- One short card.
- `Years of experience`, number input, valid range `0` to `60`.
- `Preferred location`, text input.
- `Remote OK`, toggle.
- Save maps to `PATCH /api/seeker/profile` with `SeekerProfileRequest`.
- Skip remains available and explicit.

Primary seeker surface:

- Resume status.
- Profile scoring readiness.
- Ranked matches.
- Match explanation and score factors.
- Skill-gap action and result for selected jobs.

## Employer Shell-Level Flow

Employer is secondary, but candidate review should be designed around the implemented endpoint:

`GET /api/employer/jobs/{jobId}/matches/candidates`

Employer shell states:

- No jobs: show a create-posting prompt.
- Jobs exist: allow job selection.
- Selected job: expose candidate matches.
- Candidate matches loading: show stable candidate rows.
- Candidate matches available: show ranked candidates with score and factor breakdown.
- Matching unavailable: show a service-unavailable state with retry.

Candidate review content should include:

- Candidate name or identifier.
- Score.
- Factor breakdown.
- Evidence from resume or profile where available.
- Empty and error states for no candidates or matching unavailable.

This is not a full employer workspace spec. It is a shell-level flow that acknowledges the real ranked candidate API.

## Admin Shell-Level Flow

Admin is secondary, but moderation actions should be represented as real planned actions because the backend supports them:

- `POST /api/admin/jobs/{id}/approve`
- `POST /api/admin/jobs/{id}/reject`
- `POST /api/admin/jobs/{id}/disable`

Admin shell states:

- Metrics loading.
- Metrics error.
- Metrics available.
- Moderation list empty.
- Job selected for moderation.
- Approve success.
- Reject or disable requires a nonblank moderation reason.
- Invalid moderation transition conflict.
- Unauthorized admin access.

Moderation content should include:

- Job title and status.
- Employer.
- Current moderation state.
- Approve action.
- Reject action with reason.
- Disable action with reason.
- Conflict message for invalid moderation transition.

This is not a full admin console spec. It is a shell-level flow that exposes real moderation capability accurately.

## Key States

### Auth

- Initial unknown session.
- Verified signed-in session.
- Signed-out session.
- Stale localStorage token with missing or invalid refresh cookie.
- `ERR_AUTH_003` recovery.
- Unauthorized role access.

### Seeker

- Unauthenticated.
- Authenticated with no resume.
- Resume upload in progress.
- Resume parse failed.
- Resume present with missing profile.
- Profile skipped.
- Profile complete.
- Matches loading.
- Matches empty.
- Matches available.
- Match selected.
- Skill gaps loading.
- Skill gaps error.
- Skill gaps success.

### Employer

- No jobs.
- Jobs available.
- Job selected.
- Candidate matches loading.
- Candidate matches available.
- No candidate matches.
- Matching service unavailable.

### Admin

- Metrics loading.
- Metrics error.
- Metrics available.
- Moderation empty.
- Moderation job selected.
- Approve success.
- Reject or disable validation error.
- Moderation conflict.
- Unauthorized admin access.

## Content Requirements

Profile readiness prompt:

`Complete three profile fields to improve experience and location scoring. You can skip, but matches may be generic until this is filled in.`

Resume prerequisite:

`Upload a resume first. Skills are extracted from your resume and used for match and skill-gap analysis.`

Generic match warning:

`These matches may be generic because experience and location preferences are missing.`

Auth recovery:

`Your session has ended. Please sign in again.`

Core seeker labels:

- `Resume PDF`
- `Years of experience`
- `Preferred location`
- `Remote OK`
- `Save profile and view matches`
- `Skip for now`
- `Ranked matches`
- `Skill gaps`

Employer labels:

- `Create a posting`
- `Review candidates`
- `Candidate matches`
- `Score factors`

Admin labels:

- `Moderation`
- `Parsing health`
- `Matching health`
- `Recent failures`
- `Approve`
- `Reject`
- `Disable`
- `Moderation reason`

## Interaction Requirements

- Navigation must be role-aware and avoid showing inaccessible destinations unless transparency requires it.
- Unauthorized role routes must render a clear unauthorized state, not a generic 404.
- The seeker readiness panel should guide but not block.
- Returning ready users should land directly on ranked matches.
- Profile save should produce clear success and error feedback.
- Non-idempotent authorized requests should follow existing auth recovery behavior rather than silently retrying unsafe actions.
- Motion should be limited to 150 to 250 ms state transitions. No page-load choreography.

## Accessibility Requirements

- Preserve semantic landmarks, headings, labels, lists, and status text.
- Use visible `:focus-visible` states for all custom controls.
- Do not rely on color alone for scoring, status, severity, or required action.
- Keep recovery language plain.
- Support keyboard access for shell navigation, profile form, match selection, candidate review, and moderation actions.
- Avoid protected-content flashes during session verification.

## Implementation Boundaries

This spec should lead to an implementation plan before code changes. The plan should break work into:

- App shell and auth-state handling.
- Seeker readiness and profile setup.
- Resume status and ranked matches.
- Employer shell-level candidate review.
- Admin shell-level metrics and moderation.
- Shared UI states, copy, and accessibility checks.

Do not implement all areas as one large component. Keep route-level surfaces and reusable primitives separated enough to test and evolve independently.
