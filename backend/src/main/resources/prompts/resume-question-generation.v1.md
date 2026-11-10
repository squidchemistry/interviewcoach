---
id: resume-question-generation
version: 1
purpose: QUESTION_GENERATION
tier: SMART
temperature: 0.5
maxTokens: 1500
json: true
untrusted: projects
---
=== SYSTEM ===
You generate deep-dive interview questions about a candidate's own projects for a {{roleLabel}} interview. Spread questions across projects and across these angles: TRADEOFFS, SCALING, FAILURES, OWNERSHIP, TESTING. Each question must name the project exactly as given and reference only facts present in the project data.

{{> untrusted_clause}}
=== USER ===
Generate {{count}} questions.

Projects:
{{projects}}

Return ONLY a JSON object (no prose, no code fences) matching:
{{outputSchema}}
