# JobVault

JobVault is a university job portal for job seekers and employers. Seekers upload a PDF resume, review explainable job matches and skill gaps, and manage applications. Employers publish jobs and review applications.

## Local development

### Requirements

- Java 25
- Maven (the repository includes `mvnw`/`mvnw.cmd`)
- PostgreSQL
- Node.js and npm for the frontend

Create a PostgreSQL database, then provide these backend environment variables:

```text
JOBVAULT_DB_URL=jdbc:postgresql://localhost:5432/jobvault
JOBVAULT_DB_USERNAME=...
JOBVAULT_DB_PASSWORD=...
JOBVAULT_JWT_SECRET=at-least-32-characters
JOBVAULT_REFRESH_HASH_SECRET=at-least-32-characters
```

Start the backend with `mvnw.cmd spring-boot:run` and the frontend from `frontend/` with `npm run dev`. The Vite development server proxies `/api` requests to `http://localhost:8080`.

Flyway applies the database migrations on backend startup. Do not edit migrations that have already been applied to a shared database.

Run backend tests with `mvnw.cmd test` and frontend tests with `npm test -- --run` from `frontend/`.

## Matching contract

Matching is deterministic and computed on demand. It is guidance for prioritising applications, not an automated hiring decision.

1. Eligibility is applied first. Only active jobs, parsed resumes, and enabled accounts participate. A seeker must match the job's preferred sector and work-mode constraints when those preferences are set.
2. Eligible results are ranked using a hybrid lexical-semantic score:
   - BM25 keyword relevance: 0.45
   - sentence-embedding similarity: 0.30
   - experience: 0.15
   - location/work-mode fit: 0.10
3. BM25 uses the active-job corpus, Okapi parameters `k1 = 1.2` and `b = 0.75`, canonical skill aliases from the local skill dictionary, and unigram/bigram tokens. Its raw result is divided by the theoretical maximum for the resume's known query terms and clamped to `[0, 1]`.
4. A factor with no usable input is omitted and the remaining weights are renormalized. The response identifies which factors were available so the score can be interpreted correctly.
5. Embeddings use a local, pinned `all-MiniLM-L6-v2` ONNX model through DJL and ONNX Runtime. Job vectors are cached in the in-memory active-job corpus; one resume vector is generated per matching request. If the model is disabled or unavailable, BM25 and the structured factors remain usable.
6. Experience is the candidate's years divided by the job's minimum years, capped at `1.0`. A missing or non-positive requirement, or missing candidate experience, makes the factor unavailable.
7. `strongMatch` requires an overall score of at least `0.70` and at least one available scoring factor. Required skills are not a second weighted score.
8. Skill gaps are the required skills not found in the parsed resume after skill-name canonicalisation. Automatic job skill extraction remains available for required-skill display and gap explanations.

Scores and skill gaps make the recommendation explainable, but employers remain responsible for reviewing the candidate and making the hiring decision.

## Embedding model setup

Embeddings run inside Spring Boot; no Python service is required. Keep the model assets outside Git and set `JOBVAULT_EMBEDDING_MODEL_DIR` to the local DJL-compatible `all-MiniLM-L6-v2` ONNX model directory. The directory must include the ONNX model, tokenizer assets, and the DJL serving configuration for ONNX Runtime and mean pooling. The application defaults to `models/all-MiniLM-L6-v2` and safely falls back to BM25 when those assets are not present.

DJL provides the [ONNX Runtime engine](https://docs.djl.ai/master/engines/onnxruntime/onnxruntime-engine/index.html) and [Hugging Face tokenizer support](https://github.com/deepjavalibrary/djl/tree/master/extensions/tokenizers). The application uses the local model rather than downloading it during a request.

## Matching endpoints

The seeker-facing matching endpoints are:

- `GET /api/seeker/matches/jobs`
- `GET /api/seeker/jobs/{jobId}/skill-gaps`

Recruiter-side candidate ranking, shortlist creation/acceptance, and candidate-match notifications were removed from the application. Normal job applications and application-status notifications remain available.
