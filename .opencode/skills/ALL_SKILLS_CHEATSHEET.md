# OpenCode All Skills Cheat Sheet

**Total: 54 skills** | Updated: July 2026

---

## ⚙️ Development Process

| Skill | Type | Description |
|---|---|---|
| test-driven-development | Auto | Drive dev with tests — implement logic, fix bugs, prove code works |
| spec-driven-development | Auto | Create specs before coding for new features or significant changes |
| source-driven-development | Auto | Ground decisions in official docs — authoritative, source-cited code |
| doubt-driven-development | Auto | Adversarial review of every non-trivial decision before it stands |
| code-review-and-quality | Auto | Multi-axis code review before merging |
| code-simplification | Auto | Refactor for clarity without changing behavior |
| coding-standards | Auto | Cross-project conventions for naming, readability, immutability |
| incremental-implementation | Auto | Deliver changes incrementally — land big work in small steps |
| planning-and-task-breakdown | Auto | Break specs into ordered, implementable tasks |
| debugging-and-error-recovery | Auto | Systematic root-cause debugging — tests fail, builds break |
| karpathy-guidelines | Auto | Reduce common LLM coding mistakes — surgical changes, verifiable criteria |
| ci-cd-and-automation | Auto | Setup/modify build pipelines, quality gates, test runners |
| deprecation-and-migration | Auto | Remove old systems, migrate users, sunset features |
| shipping-and-launch | Auto | Production launch checklists, monitoring, staged rollout, rollback |

---

## 🏗️ Architecture & Design

| Skill | Type | Description |
|---|---|---|
| api-and-interface-design | Auto | Design stable APIs, REST/GraphQL endpoints, type contracts |
| backend-patterns | Auto | Node.js/Express/Next.js backend architecture, DB optimization |
| deployment-patterns | Auto | CI/CD, Docker, health checks, rollback, production readiness |
| docker-patterns | Auto | Docker Compose, container security, networking, volumes |
| documentation-and-adrs | Auto | Record decisions, ADRs, public API changes |
| frontend-design | Auto | Distinctive visual design — aesthetic direction, typography |
| frontend-ui-engineering | Auto | Production-quality UIs — components, layouts, state |
| design-reference | Auto | 73 brand DESIGN.md files for matching brand aesthetics |
| ui-ux-pro-max | Auto | UI/UX design intelligence with searchable database |
| security-and-hardening | Auto | Harden against vulns — input handling, auth, data storage |
| performance-optimization | Auto | Optimize Core Web Vitals, load times, fix bottlenecks |
| observability-and-instrumentation | Auto | Add logging, metrics, tracing, alerting to production code |

---

## 🔍 Codebase Understanding

| Skill | Type | Description |
|---|---|---|
| understand | Auto | Analyze codebase → interactive knowledge graph |
| understand-chat | Auto | Ask questions about codebase using knowledge graph |
| understand-dashboard | Auto | Launch interactive web dashboard for knowledge graph |
| understand-diff | Auto | Analyze git diffs/PRs — what changed, affected components, risks |
| understand-domain | Auto | Extract business domain knowledge → domain flow graph |
| understand-explain | Auto | Deep-dive explanation of specific file, function, or module |
| understand-knowledge | Auto | Analyze Karpathy-pattern LLM wiki → knowledge graph |
| understand-onboard | Auto | Generate onboarding guide for new team members |
| using-agent-skills | Auto | Meta-skill — discover and invoke other skills |

---

## 🌐 Research & Web

| Skill | Type | Description |
|---|---|---|
| agent-reach | Auto | Internet research across 15 platforms — social, dev, career, video |
| browser-testing-with-devtools | Auto | Real browser testing via Chrome DevTools — DOM, console, network |

---

## 🧠 Agent & Context Mastery

| Skill | Type | Description |
|---|---|---|
| context-engineering | Auto | Optimize agent context setup, rules files, session start |
| gsd-core | Auto | Discuss→Plan→Execute→Verify→Ship phase loop |
| handoff | **Manual** | Compact session into handoff for fresh agent — context-limit escape |
| short | **Manual** | Force-compress last response — strip filler, simplify |
| interview-me | Auto | Extract user's true intent via one-at-a-time Q&A to 95% confidence |
| interview-style-doc-building | Auto | Build strategic docs via Q&A — patch file after each answer |
| idea-refine | Auto | Refine raw ideas → sharp concepts via divergent/convergent thinking |
| effective-agent-skills | **Manual** | Complete guide to writing agent skills — anatomy, patterns, testing |
| agent-eval | Auto | Head-to-head comparison of coding agents (Claude Code, Aider, Codex) |
| benchmark-methodology | Auto | Score competitors across 9 weighted dimensions |
| context-budget | Auto | Audit context window — identify bloat, prioritize savings |
| cost-aware-llm-pipeline | Auto | Model routing by complexity, budget tracking, retry, caching |
| cost-tracking | Auto | Track token usage, spending, budgets from ECC metrics |

---

## 🎯 Specific Domains

| Skill | Type | Description |
|---|---|---|
| caveman | Auto | Ultra-compressed communication — cuts tokens ~75% |
| cpp-coding-standards | Auto | C++ Core Guidelines — modern, safe, idiomatic C++ |
| cpp-testing | Auto | GoogleTest/CTest — write, fix, diagnose failing C++ tests |
| continuous-learning-v2 | Auto | Instinct-based learning — observes sessions, creates atomic instincts |
| autonomous-loops | Auto | Patterns for autonomous agent loops — pipeline to multi-agent DAG |

---

## 🆕 From davidondrej/skills (Newly Added)

| Skill | Type | Description |
|---|---|---|
| brain-to-docs | Auto | Extract project vision/decisions → README + ADRs via Q&A loop |
| copywriting | Auto | Writing style guide — authentic/formal, anti-AI-tells, concise |
| teach | **Manual** | Full interactive teaching — lessons (HTML), references, learning records |
| handoff | **Manual** | Compact session → structured handoff for context-limit recovery |
| short | **Manual** | Force last response to be simpler & shorter |
| effective-agent-skills | **Manual** | Guide to writing, testing, debugging agent skills |
| interview-style-doc-building | Auto | Build strategic docs one Q&A at a time |

---

## 🔑 Legend

- **Auto** — Activates automatically when your request matches the description
- **Manual** — `disable-model-invocation: true`. Invoke by name or trigger phrase
- Skills live at: `~\.config\opencode\skills\`
