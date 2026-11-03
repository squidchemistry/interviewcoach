# AI Interview Coach: frontend

React 19 single-page app (Create React App) for the AI Interview Coach API. See the [root README](../README.md) for the whole project.

## Run

```bash
cp .env.example .env.local   # REACT_APP_API_URL=http://localhost:8000
npm ci
npm start                    # http://localhost:3000
```

The backend must be running (`docker compose up` in the repo root starts everything).

## Test and build

```bash
CI=true npm test -- --watchAll=false
npm run build
```

Tests use Jest + React Testing Library with the API client mocked. `package.json` maps the React Router 7 entry points for CRA's Jest 27, and `src/setupTests.js` polyfills `TextEncoder`.

## Configuration

| Variable | When | Purpose |
|---|---|---|
| `REACT_APP_API_URL` | build time | Base URL of the API (inlined by CRA). Set it in the Vercel project settings. |

## Deploy (Vercel)

Import the repo in Vercel, set **Root Directory** to `frontend`, and add `REACT_APP_API_URL` = your Render API URL. `vercel.json` sets the build and the SPA rewrite.

Routes are documented in [ROUTES.md](ROUTES.md).
