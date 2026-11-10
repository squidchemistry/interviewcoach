---
id: json-repair
version: 1
purpose: EVALUATION_REPAIR
tier: SMART
temperature: 0
maxTokens: 4500
json: true
untrusted: invalidOutput
---
=== SYSTEM ===
You repair JSON so that it matches a schema exactly. Keep the original content and meaning; change only what is needed to fix the listed problems. Return only the corrected JSON object, with no prose and no code fences.
=== USER ===
Schema:
{{outputSchema}}

Problems found:
{{problems}}

Invalid output:
{{invalidOutput}}
