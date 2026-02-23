# When AI Gets It Wrong: Trust, Accountability, and the Questions Every Leader Should Be Asking Now

*By Nathan Knittel — Head of Technology, The Agency | Founder, KNIC Ventures*

---

Last month, one of our AI agents drafted a follow-up email to a prospective vendor. The tone was fine. The content was accurate. But it referenced a detail from a completely different conversation thread — one involving a different vendor, a different property, and a different set of commercial terms.

Nobody got hurt. We caught it before it went out. But it sat with me for days afterwards, because the failure mode wasn't dramatic. It was mundane. And mundane failures at scale are exactly the kind of risk that keeps me up at night.

I've spent the better part of 15 years in PropTech — founding and exiting HomePrezzo, and now leading technology at The Agency across roughly 400 agents and 900 staff. I'm actively deploying AI agents into real business workflows. Not demos. Not proofs of concept. Production systems where decisions have real consequences, real money attached, and real people on the other end.

I'm bullish on this technology. Genuinely. But I've reached a point where I think the most important conversation in enterprise AI isn't about capability. It's about accountability. And most organisations deploying AI agents haven't had that conversation yet.

## The Accountability Gap Nobody's Talking About

Here's the core tension. We have two mental models for thinking about responsibility when something goes wrong.

The first is the **tool model**. A spreadsheet doesn't get blamed for a bad financial forecast — the analyst does. Under this framework, AI is just software, and the human who uses it owns the outcome entirely.

The second is the **worker model**. When you delegate a task to an employee, you still bear management responsibility, but the employee also carries some accountability for execution. There are shared expectations, training standards, and performance reviews.

AI agents don't fit neatly into either category. They're too autonomous to be tools. They make contextual decisions, generate novel outputs, and operate across workflows in ways that a spreadsheet never could. But they're also not workers. They don't have judgment in any meaningful sense. They can't be disciplined, retrained the way a human can, or held personally responsible.

This creates what I call the accountability gap — a grey zone where it's genuinely unclear who owns the outcome when an AI agent makes a consequential error.

And the gap is widening. Every time we give an agent more autonomy — letting it send communications without human review, letting it make recommendations that feed into pricing decisions, letting it process data that touches compliance obligations — we're pushing further into territory where our existing accountability frameworks simply don't apply.

## Where This Gets Real

Let me ground this in scenarios that are happening right now, not in some speculative future.

**Client communications.** An AI agent drafts and sends a property appraisal summary to a client. It pulls comparable sales data, but weights a recent outlier too heavily, inflating the suggested range. The client lists based on that expectation. The property sells below it. Who owns that outcome? The agent who relied on the AI's output? The technology team that deployed the system? The vendor whose model made the error?

**Financial decisions.** An AI agent recommends a marketing spend allocation across a portfolio of listings based on predicted buyer interest. The model's training data doesn't reflect a recent shift in market sentiment. The spend is misallocated, and several listings underperform. The loss is real and measurable. Where does the buck stop?

**Regulatory compliance.** An AI agent processes and categorises client data as part of a CRM workflow. It misclassifies a record in a way that creates a privacy obligation the business doesn't meet. The regulator doesn't care that an algorithm made the error. They care that the business failed its obligations.

**Vendor relationships.** An AI agent handles routine correspondence with a supplier, and in doing so, inadvertently agrees to terms that weren't authorised. The language was ambiguous. The agent interpreted it one way. The supplier interpreted it another. Now there's a commercial dispute, and the "decision-maker" was a language model.

None of these scenarios are far-fetched. Versions of all of them have either happened to us, happened to people I know, or are one deployment decision away from happening.

## The Trust Calibration Problem

There's a deeper issue underneath the accountability question, and it's fundamentally human.

We're terrible at calibrating trust in AI systems. Research on automation bias has shown this for decades — once people trust an automated system, they tend to over-trust it. They stop checking outputs. They defer to the machine even when their own judgment should take precedence. I've watched experienced professionals accept an AI-generated recommendation without scrutiny that they would have questioned immediately if a junior colleague had presented it.

The inverse is equally dangerous. Some people refuse to delegate to AI at all, treating every output as suspect. This creates its own failure mode: bottlenecks, missed opportunities, and the gradual erosion of competitive advantage as peers move faster.

The right posture is neither blind trust nor blanket scepticism. It's *calibrated* trust — understanding what the system is good at, where it's likely to fail, and adjusting oversight accordingly. But calibrated trust requires something most organisations haven't built yet: a systematic understanding of their AI agents' failure modes.

How often does the agent get it wrong? In what circumstances? What's the blast radius when it does? Most teams deploying AI agents can't answer these questions with any precision. And if you can't answer them, you can't calibrate trust. You're flying blind.

## Building Accountability Frameworks That Actually Work

I don't think the answer is to slow down. The competitive pressure is real, the productivity gains are real, and the organisations that figure out how to deploy AI agents effectively will have a genuine structural advantage.

But deploying without governance is like driving fast without brakes. Eventually, speed becomes the problem.

Here's how I'm thinking about accountability frameworks in practice.

**Tiered autonomy, not blanket autonomy.** Not every AI decision carries the same risk. A draft social media caption and a client pricing recommendation are fundamentally different. We tier our AI agent workflows by consequence severity. Low-consequence tasks get more autonomy. High-consequence tasks get mandatory human review. The tiers aren't static — they shift as we build confidence in the system's reliability in specific domains.

**Decision logging and audit trails.** Every consequential AI agent action should be logged in a way that allows post-hoc review. Not just what the agent did, but what inputs it used, what alternatives it considered (where applicable), and what confidence signals were available. If something goes wrong, you need to be able to reconstruct the decision chain. This isn't optional. It's the minimum viable governance.

**Escalation protocols.** AI agents need to know what they don't know — or more accurately, the systems around them need to recognise uncertainty and route decisions to humans when confidence is low or stakes are high. Building good escalation protocols is harder than it sounds, because it requires you to define thresholds in advance for situations you may not have encountered yet.

**Clear ownership, not diffused responsibility.** The most dangerous thing you can do is deploy an AI agent without a named human who owns the outcome. Not the vendor. Not "the technology team." A specific person who is accountable for the agent's outputs in a specific workflow. This sounds old-fashioned. It is. That's why it works.

**Regular failure reviews.** We do post-mortems on AI agent errors the same way engineering teams do incident reviews. No blame, just learning. What went wrong, why, and what changes to the system or the oversight model would prevent recurrence. This practice alone has been more valuable than any technical safeguard we've implemented.

## The Vendor Question

There's an uncomfortable conversation that needs to happen between AI vendors and the businesses deploying their products.

Most vendor agreements today disclaim liability for AI outputs almost entirely. The terms of service essentially say: this is a tool, you're responsible for how you use it, and we make no guarantees about accuracy. That's understandable from the vendor's perspective — they can't control how their product is deployed. But it leaves deployers holding all the risk.

As the market matures, I expect we'll see this shift. Vendors who can demonstrate reliability, who can provide meaningful performance guarantees in specific domains, and who can offer shared accountability models will have a significant competitive advantage. The current posture of blanket disclaimers is a transitional state, not a permanent one.

In the meantime, if you're deploying AI agents from a third-party vendor, you need to understand exactly what you're signing up for. Read the indemnification clauses. Understand the liability boundaries. And don't assume the vendor has your back if their model makes a costly error in your workflow.

## The Insurance and Legal Frontier

This is still early days, but the legal and insurance landscape is starting to move. Professional indemnity insurers are beginning to ask questions about AI in business workflows. Some are adding exclusions. Others are developing new products specifically for AI-related errors and omissions.

The legal frameworks are evolving too. Existing negligence principles can stretch to cover some AI failure scenarios, but they weren't designed for systems that make autonomous contextual decisions. The question of reasonable care — what level of oversight constitutes due diligence when you're deploying an AI agent — doesn't have settled answers yet.

Leaders who are proactive about governance now aren't just managing risk. They're building a defensible position for when the regulatory and legal frameworks do crystallise. Being able to demonstrate that you had accountability structures in place, that you logged decisions, that you maintained appropriate human oversight — that's going to matter.

## The Leadership Posture

I want to be clear about what I'm advocating for here. This is not a case for caution over action. It's a case for *deliberate* action.

The organisations that will lead in the AI era aren't the ones that move fastest with the least oversight. They're the ones that move fast *and* build the governance structures that allow them to sustain that speed over time. Speed without accountability eventually produces a failure that forces you to stop entirely. That's the real risk of moving fast and breaking things when the things you're breaking are client relationships, compliance obligations, and commercial outcomes.

Every leader deploying AI agents should be able to answer five questions today:

1. Where are our AI agents making decisions that have material consequences?
2. What happens when they get one of those decisions wrong?
3. Who specifically is accountable for each of those outcomes?
4. What audit trail exists to reconstruct a failure after the fact?
5. How are we systematically learning from errors to improve the system?

If you can't answer all five, you don't have an accountability framework. You have a hope that nothing goes wrong. And hope is not a strategy.

The AI agent revolution is real. The productivity gains are transformative. But the organisations that capture those gains sustainably will be the ones that pair ambition with governance — not because regulators demand it, but because accountability is what makes sustained autonomy possible.

---

*Nathan Knittel is Head of Technology at The Agency and founder of KNIC Ventures. He has over 15 years of experience in PropTech, including founding and exiting HomePrezzo. He writes about the practical realities of deploying AI in enterprise environments.*
