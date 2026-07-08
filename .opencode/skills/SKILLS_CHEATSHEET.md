# OpenCode Skills Cheat Sheet — Installed from davidondrej/skills

| Skill | Type | Trigger / How to Invoke | What It Does |
|---|---|---|---|
| **effective-agent-skills** | Auto | Say: "create a skill", "new skill", "update this skill", "improve a skill", "why isn't my skill triggering" | The definitive guide on writing agent skills (anatomy, design patterns, anti-patterns, testing, security checklist) |
| **handoff** | Manual | `handoff` — invoke explicitly when hitting context limits or ending a session | Compacts the conversation into a structured handoff document so a fresh agent can continue without losing context |
| **short** | Manual | `short` — or say "short", "shorter", "simpler", "too long", "tl;dr" | Forces agent to compress its last response — strip filler, simplify wording |
| **copywriting** | Auto | Triggers when writing any user-facing copy (tweets, READMEs, emails, bios, announcements) | Writing style guide: short sentences, no emojis, minimum hype, authentic vs formal modes, anti-AI-tells |
| **brain-to-docs** | Auto | Say: "brain-to-docs", "build out the docs", "extract the vision", "let's document this project" | Extracts project vision/decisions from your head into README + ADRs via back-and-forth Q&A |
| **teach** | Manual | `teach <topic>` — or say "teach me X" | Full interactive teaching system with lessons (HTML), references, learning records, spaced repetition |
| **interview-style-doc-building** | Auto | Say: "let's build a doc", "create a priorities doc", "walk me through this document" | Builds structured strategic docs by asking one question at a time, patching the file after each answer |

## Trigger Types Explained

- **Auto** — skill activates automatically when your request matches its description. Just talk naturally.
- **Manual** — skill has `disable-model-invocation: true`. You need to explicitly invoke it (say its name or the trigger phrase) for it to load.

## Quick Reference: Manual Invocation

```
"handoff"            → Generate session handoff
"short" / "tl;dr"   → Compress last response
"teach me Python"   → Start interactive teaching session
```

## Where Skills Live

```
.opencode/skills/
├── effective-agent-skills/
├── handoff/
├── short/
├── copywriting/
├── brain-to-docs/
├── teach/
└── interview-style-doc-building/
```

## Source

All skills ported from [davidondrej/skills](https://github.com/davidondrej/skills) (MIT license).
