# Frontend routes

| Path | Component | API calls |
|---|---|---|
| `/` | `Home` | none |
| `/interview` | `InterviewSetup` | `POST /v1/sessions` |
| `/sessions/:sessionId/run` | `InterviewRunner` + `InterviewSession` | `GET /v1/sessions/{id}` (on refresh/resume), `POST /v1/sessions/{id}/answers` |
| `/sessions/:sessionId` | `SessionDetail` + `Summary` | `GET /v1/sessions/{id}`, `GET /v1/sessions/{id}/summary` |
| `/history?page=N` | `HistoryPage` | `GET /v1/sessions?limit=10&offset=(N-1)*10` |

Flow: setup creates a session, then the runner asks the next unanswered question. Each answer is saved as it is submitted. When every question is answered, the runner redirects to the session detail page. Because state lives in the API, any of these URLs can be refreshed or bookmarked, and an in-progress session can be continued from History.

On Vercel, `vercel.json` rewrites every path to `index.html` so these client-side routes work on a hard refresh.
