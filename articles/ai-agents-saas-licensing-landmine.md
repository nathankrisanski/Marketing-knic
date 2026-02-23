# The Licensing Landmine Nobody's Talking About — What Happens When AI Agents Become Your SaaS Users?

*By Nathan Knittel, Head of Technology at The Agency & Founder, KNIC Ventures*

---

Here's a scenario that should keep every SaaS CFO up at night.

A real estate agency — let's say one with 400 agents — pays for 400 CRM seats. One login per person. Simple. Predictable. The model that's powered the entire SaaS industry for two decades.

Now imagine this: the agency deploys an AI agent. It logs in with a single set of credentials. It updates listings, nurtures leads, triggers follow-ups, generates reports, and manages pipeline — 24 hours a day, 7 days a week, without coffee breaks or annual leave. It does the CRM work that previously required dozens of human users.

On one seat.

The per-seat model just broke. And almost nobody is talking about it.

## The Model That Built an Industry

Per-seat licensing is the backbone of SaaS. One user, one subscription, one predictable line item on the invoice. It's elegant in its simplicity. It scales with headcount. It gives vendors revenue visibility and gives customers cost predictability. From Salesforce to Slack to HubSpot, this model has generated trillions in enterprise value.

It works because of one foundational assumption: **a seat represents a human, and a human has finite capacity.** One person can only do so much in a day. They log in, they do their work, they log out. The value the software delivers is bounded by the hours and cognitive limits of the person using it.

That assumption is about to be obliterated.

## One Login. Infinite Capacity.

AI agents aren't copilots sitting alongside a human user. They're autonomous actors. They authenticate, they execute, they iterate — and they do it at a speed and scale that no human can match.

An AI agent doesn't just use your CRM. It *consumes* it. It can process every lead in your pipeline simultaneously. It can trigger a thousand personalised follow-ups in the time it takes a human to write one. And it can spin up multiple instances of itself, each operating concurrently, all under the same set of credentials.

So here's the question that nobody has a clean answer to: **what does the vendor charge for that?**

If you're paying per seat, and one AI agent is doing the work of fifty humans, you've just collapsed your SaaS bill by 98%. That's a spectacular outcome for the customer. It's an existential threat to the vendor.

## The Vendor's Impossible Trilemma

SaaS vendors are about to face a choice with no comfortable option.

**Option one: hold the line on per-seat pricing.** Customers consolidate dozens of logins into one AI agent credential. Revenue craters. The vendor watches their ARR shrink in direct proportion to their customers' AI maturity. The better AI gets, the worse the vendor's business becomes. That's not a sustainable position.

**Option two: pivot to usage-based or outcome-based pricing.** This is the theoretically elegant answer, but it's a fundamental restructuring of the entire business model. Usage-based pricing changes everything — revenue forecasting, sales compensation, customer success metrics, investor narratives. It's not a pricing tweak. It's a corporate transformation. And most SaaS companies aren't built for it.

**Option three: ban AI agent access entirely.** Update the Terms of Service. Require that every login be a verified human. Enforce it through behavioral detection. This might buy time, but it's a losing strategy. Customers will migrate to competitors who embrace agent access. And trying to stop AI adoption is like trying to hold back the tide — you might slow it down, but you're going to get wet.

None of these options are good. All of them are coming.

## The Customer's Quiet Panic

Vendors aren't the only ones in an awkward position. Customers are navigating this in real time — often without realising the legal and commercial risk they're carrying.

Right now, somewhere, a technology leader is deploying an AI agent that authenticates into a SaaS platform using existing credentials. They probably haven't checked the Terms of Service. They probably haven't asked the vendor for permission. They probably haven't even thought about whether this constitutes a violation.

But the questions are real:

- Does your SaaS agreement allow non-human users to access the platform?
- If your AI agent spawns multiple concurrent sessions, does each one constitute a "seat"?
- If the vendor discovers automated access patterns, can they terminate your contract?
- Are you liable for data processed by an AI agent acting within a vendor's platform?

I don't have definitive answers to these questions. I suspect most technology leaders don't either. And I suspect most SaaS vendors haven't thought through their own policies clearly enough to give you one.

We're in a grey zone. And grey zones have a way of becoming very expensive, very quickly, when someone decides to enforce the fine print.

## We've Seen This Movie Before

If this feels familiar, it should. We watched a nearly identical disruption play out when cloud computing broke the on-premise licensing model.

For decades, enterprise software was sold per-server or per-installation. Then virtualisation arrived. One physical server became ten virtual machines. Suddenly, a single license was running across multiple instances, and vendors had no framework to price for it.

The industry went through years of painful renegotiation. Oracle's licensing audits became legendary. Microsoft restructured its entire licensing model multiple times. Some vendors adapted and thrived. Others clung to the old model and lost market share to cloud-native competitors who priced differently from the start.

AI agents are the virtualisation moment for SaaS. The underlying asset — the software — hasn't changed. But the *consumption pattern* has changed so dramatically that the existing commercial model no longer maps to reality.

History doesn't repeat, but it rhymes. And this rhyme is getting loud.

## What Comes Next? New Models, New Questions

The industry will eventually land on new licensing frameworks. Some candidates are already emerging:

**Per-outcome pricing.** Don't charge for the seat or the session. Charge for the result. A lead converted, a contract generated, a listing published. This aligns vendor revenue with customer value — but it also makes revenue wildly variable and harder to predict.

**API-call-based pricing.** Charge based on the volume of interactions with the platform. This is already common in developer tools and infrastructure, but it changes the psychology of the relationship. Customers start optimising to minimise API calls, which means minimising their use of your product. That's a perverse incentive.

**Tiered agent licensing.** Create a new license category specifically for AI agents, priced differently from human seats. Maybe an agent seat costs 5x a human seat but allows unlimited concurrent sessions. This is pragmatic, but it requires vendors to define and enforce the distinction between human and AI usage — which is going to get increasingly difficult as AI becomes embedded in everything.

**Consumption-based models.** Charge for compute, storage, or processing volume consumed by the agent. This is the AWS model applied to SaaS. It's flexible and fair, but it introduces cost unpredictability for customers — exactly the problem SaaS was supposed to solve.

Each of these models has merit. Each has serious drawbacks. And none of them have been tested at scale in the context of autonomous AI agents operating across enterprise SaaS stacks.

## The Uncomfortable Question: Who Actually Benefits?

Here's where it gets really interesting — and where I'd urge technology leaders to think carefully.

The optimistic read: AI agents collapse the cost of SaaS consumption. Customers pay less for more output. The per-seat tax disappears. Efficiency wins.

The pessimistic read: vendors use the disruption as cover to *increase* prices. "Your AI agent is consuming 50x the resources of a human user — so your new agent license costs 10x a standard seat." The customer is still paying less than fifty seats, so it feels like a win. But the vendor has actually *increased* their revenue per unit of work delivered.

And there's a darker possibility: vendors detect AI agent usage, retroactively enforce ToS violations, and use the threat of contract termination as leverage in renewal negotiations. "We noticed you've been running automated agents on your platform. Let's talk about your new enterprise agreement."

If you think that sounds aggressive, you haven't been through enough enterprise software audits.

## The Call to Action — For Both Sides

This isn't a problem for next year. It's a problem for right now.

**If you're a SaaS vendor:** get ahead of this. Define your AI agent policy before your customers force you to. Build pricing models that accommodate non-human users. Talk to your legal team about Terms of Service updates. And do it openly — vendors who embrace agent access transparently will build trust. Those who wait for the audit-and-enforce cycle will build resentment.

**If you're a technology leader deploying AI:** audit your SaaS agreements now. Understand what your Terms of Service actually say about automated access and non-human users. Have the conversation with your vendors proactively — before they have it with you on their terms. And start thinking about how your SaaS costs should be structured in a world where headcount is no longer the primary driver of software consumption.

**If you're an AI platform provider:** recognise that your customers' ability to deploy your agents is constrained by the commercial terms of the SaaS ecosystem those agents operate within. This is your problem too.

## The Landmine Is Live

We are in the earliest innings of a commercial renegotiation that will reshape the entire SaaS industry. The per-seat model isn't just under pressure — its foundational assumption has been invalidated. And the replacement model hasn't been invented yet.

That gap — between the old model breaking and the new model emerging — is where the landmine sits. It's where contracts will be disputed, relationships will be strained, and billions of dollars in enterprise software revenue will be redistributed.

I don't have the answers. I'm not sure anyone does yet. But I'm certain of this: the companies that start asking these questions now — vendors and customers alike — will be in a far stronger position than those who wait for the explosion.

The fuse is lit. The only question is who moves first.

---

*Nathan Knittel is Head of Technology at The Agency, one of Australia's leading premium real estate brands, and founder of KNIC Ventures. With 15+ years in PropTech — including founding and exiting HomePrezzo — he works at the intersection of real estate, technology, and commercial strategy.*
