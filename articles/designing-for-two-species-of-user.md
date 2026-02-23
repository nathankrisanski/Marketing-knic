# Designing for Two Species of User — How SaaS Must Evolve When AI Agents Are Your Customers Too

*By Nathan Knittel — Head of Technology, The Agency | Founder, KNIC Ventures*

---

Every SaaS product you've ever used was designed for you. A human. Someone who scans a page from top-left to bottom-right, who understands that a red badge means "pay attention," who knows that a hamburger icon hides a menu. Every colour choice, every hover state, every carefully crafted onboarding tooltip exists because a person with eyes, intuition, and a mouse is on the other side of the screen.

That assumption — so foundational it's almost invisible — is starting to break down.

AI agents are becoming users of software. Not through APIs alone, but through the same interfaces you and I use every day. They're logging into platforms, navigating dashboards, filling out forms, extracting data, and completing multi-step workflows. This isn't science fiction or a demo reel. Computer-use AI is shipping now, and it's getting better fast.

If you build SaaS, or if you're a business leader choosing which tools your organisation runs on, this shift demands your attention. Because the products that serve only human users are about to face a very uncomfortable competitive disadvantage.

## The World We Built for Human Eyes

Think about how much of modern software design assumes human cognition. We use spatial layout to imply hierarchy. We use colour to encode status — green for good, red for bad, amber for "maybe worry about this." We rely on users understanding visual metaphors: a trash can means delete, a pencil means edit, a cloud with an arrow means upload.

These conventions are brilliant. Decades of UX research have made software dramatically easier for people to use. But they are, almost without exception, designed for a single species of user.

When a human looks at a dashboard, they take in the whole page in a glance. They notice the big number at the top, the chart trending downward, the notification dot in the corner. They synthesise all of that visual information in milliseconds, drawing on years of learned convention.

An AI agent interacting with that same dashboard experiences something entirely different.

## How AI Agents Actually "See" Your Product

An AI agent using computer-use capabilities doesn't perceive your interface the way a person does. Depending on the agent architecture, it might be processing a screenshot as pixels, parsing the underlying DOM structure, or working with an accessibility tree. In every case, the experience is fundamentally different from human perception.

That beautiful gradient you spent a sprint perfecting? Irrelevant. The subtle animation that guides a user's eye toward the call-to-action? Invisible. The icon that every human instantly recognises as a settings cog? Potentially meaningless without an accessible label.

AI agents navigate software by identifying patterns, reading text, interpreting structure, and inferring state. They look for consistent element naming, predictable page layouts, and clear textual indicators of what's happening. They perform best when software is explicit rather than implicit — when meaning is encoded in text and structure, not just in visual design.

This doesn't mean your visual design is wrong. It means it's incomplete.

## The Dual-Design Challenge

Here's where it gets interesting — and where most SaaS companies haven't yet started thinking clearly.

You can't abandon human-centric design. Your human users aren't going anywhere, and the qualities that make software intuitive for people — clean visual hierarchy, thoughtful interaction patterns, responsive feedback — remain essential. The challenge isn't to choose between human and AI users. It's to serve both simultaneously.

This is a genuinely new product design problem. For the first time, SaaS teams need to think about two fundamentally different types of user navigating the same product, with different capabilities, different strengths, and different failure modes.

A human user can look at a messy interface and still figure out what to do. They're resilient to inconsistency. An AI agent, confronted with the same mess, may fail silently or take the wrong action entirely. Conversely, an AI agent can process a thousand rows of structured data in seconds, while a human needs filters, pagination, and visual summaries to make sense of the same information.

Designing for both means building products that are simultaneously visually intuitive *and* structurally legible.

## Practical Design Principles for the Dual-User Era

This isn't purely theoretical. There are concrete things SaaS teams can start doing now to make their products work for both humans and AI agents.

**Semantic, well-structured markup matters more than ever.** If your front-end is a soup of generic `div` elements with meaning conveyed entirely through CSS classes, an AI agent will struggle to understand your interface. Semantic HTML — proper use of headings, landmarks, form labels, and ARIA attributes — gives agents (and screen readers) the structural information they need to navigate purposefully.

**Consistent, descriptive naming is critical.** Buttons that say "Submit" in one context and "Go" in another, or worse, buttons with no text label at all (just an icon), create ambiguity for agents. Every interactive element should have a clear, consistent textual identity. If a human can hover over an icon to see a tooltip, an agent needs that same information available in the markup.

**Predictable navigation patterns reduce agent errors.** If your settings page is reached via a dropdown in one section and a sidebar link in another, a human might adapt without noticing. An AI agent benefits enormously from consistent, predictable paths through your product. This doesn't mean dumbing down your navigation — it means being disciplined about information architecture.

**State should be explicitly communicated, not just visually implied.** When a form has a validation error, is that error only indicated by a red border? Or is there also a text message, an `aria-invalid` attribute, a clear programmatic signal that something needs attention? AI agents need state communicated in the structure, not just the styling.

**Structured data exposure accelerates agent workflows.** If your product displays data in a table, make sure that table is a real HTML table with proper headers — not a visual grid built from styled divs. Better yet, consider exposing key data through lightweight, well-documented endpoints that agents can consume directly.

## The API–Computer Use Spectrum

It's worth being precise about the landscape here, because "AI agents using your software" isn't a single pattern. It's a spectrum.

On one end, you have agents interacting through traditional APIs. This is well-understood territory — if your product has a robust, well-documented API, agents can already automate workflows programmatically. Many SaaS companies have invested heavily here.

On the other end, you have computer-use agents that interact with your product through the visual interface itself — clicking buttons, reading screens, filling forms. This is newer, rougher, and rapidly improving.

In the middle, you have emerging patterns like MCP (Model Context Protocol) and similar frameworks that let AI agents discover and interact with your product's capabilities in structured ways — without requiring either a full API integration or raw screen interaction.

The SaaS companies that will thrive are those building for the full spectrum. A great API for deep integrations. A UI that's parseable and navigable by computer-use agents. And ideally, support for the structured interaction protocols that are emerging as the connective tissue between AI and software.

## The Accessibility Parallel

If this dual-design challenge sounds familiar, it should. We've faced a version of it before.

The accessibility movement spent decades arguing that software should be usable by people with diverse abilities — including those using screen readers, keyboard navigation, and other assistive technologies. The core insight was the same: meaning encoded only in visual presentation is meaning that's locked away from an entire category of user.

The principles that emerged from that work — semantic markup, text alternatives, keyboard navigability, programmatic state indication — are *precisely* the principles that make software AI-agent-friendly. The accessibility tree that screen readers use to interpret a page is, in many cases, the same structure that AI agents rely on.

This is more than analogy. It's a direct technical overlap. SaaS companies that took accessibility seriously are already ahead. Those that treated it as a compliance checkbox are now doubly exposed — their products are harder to use for both people with disabilities and AI agents.

## The Competitive Stakes

So why should SaaS founders and product leaders care about this *now*, rather than in two or three years?

Because tool selection is increasingly going to be influenced by how well a product works with AI agents. This is already happening. When a business deploys AI to automate parts of a workflow — processing inbound enquiries, managing data entry, generating reports — the AI's effectiveness is directly constrained by the tools it has to work with. If your CRM is a nightmare for an agent to navigate, and your competitor's CRM is cleanly structured and predictable, the competitor wins. Not because their product is prettier, but because it's *operable* by the full workforce — human and AI alike.

In real estate, where I work, this is about to become very tangible. An agency running 400 agents with AI assistants handling listing coordination, compliance checks, and client communication needs every tool in the stack to play nicely with those AI systems. The platforms that do will become indispensable. The platforms that don't will get routed around — or replaced.

This is the new switching cost. Products that are AI-agent-friendly become stickier, because they're woven into automated workflows that are painful to rebuild elsewhere. Products that resist this shift become the weak link in the chain — the tool that everyone has to work around.

## What Happens If You Don't Adapt

The uncomfortable reality for SaaS companies that ignore this shift is that AI agents won't politely wait for your product to catch up. They'll find alternatives.

If your platform can't be reliably navigated by an AI agent, businesses will build brittle workarounds — or more likely, they'll migrate to a competitor whose product works seamlessly. The businesses deploying AI at scale will evaluate tools through a new lens: "Can my agents use this effectively?" If the answer is no, it doesn't matter how good your human UX is.

This isn't a distant scenario. It's the evaluation framework that forward-thinking companies are already applying.

## A Wake-Up Call, Not a Funeral

None of this means SaaS design is broken. The craft of building software for humans remains vital and valuable. What's changing is the scope of who "users" are.

The SaaS companies that move early — that start thinking about semantic structure, predictable patterns, explicit state, and structured data exposure as first-class design concerns — will find themselves with a meaningful competitive edge. Their products will be the ones that humans love to use *and* that AI agents can operate reliably. That combination is going to be very hard to beat.

For SaaS builders: start auditing your product through the eyes of an AI agent. Can it navigate your interface reliably? Can it determine state without visual cues? Can it complete core workflows without hitting ambiguous dead ends? The answers will tell you where your gaps are.

For business leaders evaluating tools: start asking vendors about AI-agent compatibility. Not just "do you have an API?" but "can an AI agent use your product effectively through the interface?" The answers — or the blank stares — will tell you a lot about which products are ready for what's coming.

We're entering an era where the best software is software that works for everyone at the table — including the participants that aren't human. The companies that design for both species of user won't just survive this transition. They'll define it.

---

*Nathan Knittel is Head of Technology at The Agency, one of Australia's leading premium real estate brands, and founder of KNIC Ventures. With 15+ years in PropTech — including founding and exiting HomePrezzo — he builds technology products at the intersection of product design and operational deployment.*
