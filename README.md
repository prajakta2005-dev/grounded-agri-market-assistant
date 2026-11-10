# Grounded Agri-Market Assistant

A React dashboard with a Java 21 / Spring Boot API and Spring AI. It supports:

- Crop and market questions grounded in a source library, with source links on every answer.
- Crop photo symptom triage through the multimodal NVIDIA Nemotron endpoint configured in the backend.
- PostgreSQL storage for source documents, marketplace sample lots, listing drafts, and prepared orders.
- pgvector retrieval for source passages, with a database keyword-search fallback if embedding service calls are unavailable.
- A token-protected source manager for previewing PDF or approved HTTPS imports, editing extracted text, and re-indexing saved references.
- Explicit uncertainty language and referral to a local extension officer when symptoms or source coverage are insufficient.

## Run it

1. In the project-root `.env`, set `NVIDIA_API_KEY`, `NVIDIA_BASE_URL`, and `NVIDIA_MODEL`. Spring Boot loads `.env` when started from the repository root or `backend/`. Keep the key out of the frontend and source control.
2. Add PostgreSQL connection settings to the same `.env`:

   ```dotenv
   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/agri_project
   SPRING_DATASOURCE_USERNAME=your_postgres_user
   SPRING_DATASOURCE_PASSWORD=your_postgres_password
   ```

   Set a private `SOURCE_ADMIN_TOKEN` in `.env` to unlock **Manage sources** in the Source library. Keep the token private; the browser holds it in memory only.

3. Ensure PostgreSQL has the pgvector extension available and that the configured database user can create it. On first startup, Flyway creates the tables and seeds the source library and sample market lots.
4. Start the backend from `backend/` with Maven and Java 21+: `mvn spring-boot:run`.
5. In another terminal, run `npm.cmd install` and `npm.cmd run dev` from `frontend/`.
6. Open the Vite URL shown in the terminal. The API listens on `http://localhost:8080`.

At backend startup, source passages are embedded with NVIDIA's Nemotron embedding endpoint and stored in pgvector. The default model is `nvidia/nemotron-3.5-embed-1b` at 1,024 dimensions; set `NVIDIA_EMBEDDING_MODEL` and `NVIDIA_EMBEDDING_DIMENSIONS` in `.env` only when changing to a compatible model and matching vector dimension. The backend sends passage mode when indexing and query mode during search. Changed passages are re-indexed on later starts. If embedding indexing or vector retrieval is unavailable, the API falls back to keyword retrieval from PostgreSQL. The listing drafts and prepared orders are saved in PostgreSQL and remain after a restart. Demo inventory is sample data, not a live market feed. Prepared orders do not reserve inventory or contact a seller.

## API

- Interactive Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- `POST /api/assistant/chat` (`multipart/form-data`: `question`, optional `photo`)
- `GET /api/market/listings?query=&location=`
- `POST /api/market/listings` (prepare and persist a produce listing draft)
- `POST /api/market/orders` (prepare and persist an order draft)
- `GET /api/sources`

The source manager API uses `X-Source-Admin-Token`. It exposes PDF and approved-URL preview endpoints, plus create, edit, re-index, and delete operations under `/api/admin/sources`. Imports are limited to 6 MB and 180,000 extracted characters. URL imports accept HTTPS pages and PDFs hosted on approved agriculture, education, and government domains; PDF previews with no extractable text need OCR before import. Review and edit the extracted text and source metadata in the UI before saving. User-added sources are indexed immediately when the embedding service is available; an indexing retry is available if it is not.

## Source notes

The crop source cards include the source publication/report date where the document gives one, and an access date for every source. Some crop-protection pages do not state a publication date; those are marked as undated instead of assigning them a made-up date. The marketplace sample inventory has its own update date and is not represented as official price data.
