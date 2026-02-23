# The SaaS Hostage Problem — How AI Agents Are Shifting Power Back to Customers

*By Nathan Knittel, Head of Technology at The Agency & Founder, KNIC Ventures*

---

Let me describe a scenario that will be painfully familiar to anyone who runs technology for a mid-to-large organisation.

You've got a critical business system — your CRM, your property management platform, your marketing automation tool. Your team has spent years building workflows around it. Your data — years of client relationships, transaction history, operational intelligence — lives inside it. And one day, you need to connect it to something else. Maybe you want to automate a reporting workflow. Maybe you need to push data into a new tool your team has adopted. Maybe you just want to extract your own information in a format that's actually useful.

So you go looking for the API.

And that's where the fun starts.

## The API Tax

If you're lucky, the vendor has an API. If you're less lucky, it's behind an enterprise pricing tier you're not currently on. If you're truly unlucky — and I've been here more times than I care to count — the API exists but is deliberately limited. It exposes some endpoints but not others. Read access but not write. Certain object types but not the ones you actually need. And the vendor's roadmap for expanding it? "We'll get to that."

This is the quiet power play that has defined enterprise SaaS for the past fifteen years. The software is sold as a service, but the real product is dependency. Your data goes in easily. Getting it out — or getting it to talk to anything else — is where the leverage sits.

I've spent over fifteen years in PropTech, including founding and exiting HomePrezzo and now running technology across a network of roughly 400 agents and 900 staff at The Agency. I've sat on both sides of this table. I've built SaaS products and I've been the customer trapped by them. And the pattern is always the same: the vendor controls the integration roadmap, the feature velocity, and the pricing — and the customer's only real negotiating tool is the threat of migration, which everyone knows is expensive, disruptive, and rarely followed through on.

We've normalised this. We shouldn't have.

## Your Data, Their Terms

Here's the part that should bother us more than it does.

When you sign up for a SaaS platform and pour your operational data into it, there's a quiet assumption that you're the owner of that data. Most terms of service will even say so. But ownership without practical access is a hollow concept. If I own my data but can only retrieve it through an API the vendor chooses to build, chooses to maintain, and chooses to price — do I really own it in any meaningful sense?

This isn't an abstract philosophical question. It has real operational consequences. I've watched businesses delay strategic projects by months because a vendor hadn't shipped an integration. I've seen teams manually exporting CSVs and copy-pasting between systems because the API didn't cover a particular workflow. I've seen organisations pay tens of thousands of dollars annually for API access tiers that essentially amount to paying for permission to use their own data programmatically.

The vendor's incentive structure is clear. Every gap in API coverage is a moat. Every missing integration is a reason you can't leave. Every premium API tier is rent on data you generated.

This isn't malice, necessarily. It's just business. But it's a business model built on information asymmetry and switching costs — and it's been unchallenged for a long time.

Until now.

## Enter the Universal Integrator

AI agents with computer-use capabilities represent something genuinely new in this dynamic. Not incrementally new. Structurally new.

An AI agent that can operate a computer the way a human does — clicking buttons, navigating interfaces, reading screens, filling forms, extracting information — doesn't need an API. It doesn't need the vendor's permission. It doesn't need to wait for an integration to be built. It interacts with the software through the same front door your employees use every day.

Think about what that means for a moment.

That reporting workflow you couldn't automate because the API didn't expose the right data? An agent can log in, navigate to the report, extract the data, and pipe it wherever you need it. That cross-platform sync you couldn't build because vendor A and vendor B had incompatible APIs? An agent can operate both systems simultaneously, acting as a bridge that neither vendor needs to sanction.

The integration bottleneck doesn't disappear, but it moves. It shifts from being a vendor-controlled gate to a customer-controlled capability. You're no longer asking "has the vendor built this integration?" You're asking "can we instruct an agent to do what a human would do?" And the answer, increasingly, is yes.

This is not theoretical. We're already in the early innings of deploying agent-based workflows that interact with systems exactly this way — not because we want to circumvent our vendors, but because waiting for them was costing us time and operational agility we couldn't afford to lose.

## Does Lock-In Disappear — or Just Change Shape?

I want to be honest about the limitations here, because the temptation with any new technology paradigm is to overstate the revolution.

AI agents with computer-use capabilities weaken one specific form of vendor lock-in: the integration bottleneck. They make it dramatically easier to get data in and out of systems, to bridge workflows across platforms, and to automate processes that previously required either an API or a human sitting at a keyboard.

But they don't eliminate lock-in entirely. Your team's muscle memory with a platform still matters. Your historical data still lives somewhere. The switching cost of retraining 400 agents on a new CRM is still real and substantial. And agents introduce their own dependencies — on the AI platforms that power them, on the infrastructure that runs them, on the expertise required to orchestrate them effectively.

Lock-in doesn't vanish. It redistributes. But the redistribution matters, because it shifts leverage back toward the customer in negotiations that have been one-sided for far too long. When you can credibly say "we can access and move our data regardless of your API strategy," the conversation about pricing, features, and roadmap changes materially.

## The Vendor's Counterargument (And Where It Holds Up)

SaaS vendors will push back on this, and some of their arguments deserve serious consideration.

Security is a legitimate concern. An AI agent logging into a system with user credentials and performing automated actions at scale introduces risk vectors that are different from — and in some cases greater than — traditional API-based integrations. Session management, rate limiting, audit trails, error handling — these are real engineering problems that are more elegantly solved through well-designed APIs than through screen-level automation.

Data integrity is another fair point. APIs enforce schemas and validation rules. An agent interacting through a UI is more brittle, more susceptible to breaking when the vendor ships a UI update, and more likely to create inconsistencies if it misinterprets a screen state. Anyone who has built a web scraper knows the maintenance burden of keeping up with front-end changes.

And then there are terms of service. Many SaaS agreements explicitly prohibit automated access through the UI, scraping, or bot-based interaction. Vendors will argue — not without some justification — that their systems weren't designed for this kind of usage and that it creates infrastructure costs they didn't price for.

These are all real concerns. I don't dismiss them. But here's the tension: these arguments are strongest when they're about genuine technical and security considerations, and weakest when they're used as cover for maintaining the very dependency model that created the problem in the first place. The vendor who says "you can't automate against our UI for security reasons" but also doesn't provide an adequate API alternative isn't protecting you. They're protecting their moat.

## What This Means for How We Buy Software

The practical implications of this shift are already changing how I think about vendor evaluation and negotiation.

First, API quality and completeness should be weighted more heavily than ever in procurement decisions — not because agents eliminate the need for APIs, but because a vendor with a comprehensive, well-maintained API is signalling something about their philosophy toward customer data access. An agent-based workaround is always second-best to a proper integration. The vendors who understand this will win in the long run.

Second, the negotiating dynamic around data portability is shifting. When an organisation has the technical capability to extract its data through agent-based automation regardless of API availability, the vendor's leverage in conversations about data export, migration support, and integration pricing diminishes. Smart vendors will lean into this by making data portability a feature rather than a concession. The rest will learn the hard way.

Third, businesses need to start building internal capability around agent orchestration now. This isn't about replacing your SaaS stack — it's about building a layer of operational intelligence on top of it that you control. The organisations that develop this muscle early will have a compounding advantage in operational agility over those that continue to outsource their integration strategy to their vendors' product roadmaps.

Finally — and this is the big one — we need to have a more honest conversation as an industry about data ownership. Not the legal abstraction of it, but the practical reality. If a business generates data through its operations, stores it in a SaaS platform, and pays ongoing licensing fees for the privilege, the right to access that data programmatically — in full, without artificial limitation, and without punitive pricing — should be table stakes. It shouldn't require an AI agent to break down the wall. The wall shouldn't be there in the first place.

## The Bigger Picture

I'm not anti-SaaS. I've built SaaS products. I believe in the model. The best SaaS companies create genuine, compounding value for their customers, and the subscription model aligns incentives in ways that traditional software licensing never did.

But the industry has developed some bad habits around data access and integration control, and those habits have persisted because customers haven't had alternatives. AI agents with computer-use capabilities don't fix everything, but they introduce a credible alternative to the status quo for the first time. And credible alternatives have a way of improving everyone's behaviour.

The best vendors will respond to this shift by opening up — by building better APIs, by embracing data portability, by competing on the quality of their product rather than the stickiness of their data moat. The ones who double down on restriction will find their customers increasingly capable of routing around them.

The SaaS hostage era isn't over. But the hostages just got a set of tools the vendors didn't anticipate. And that changes the negotiation permanently.

---

*Nathan Knittel is Head of Technology at The Agency, one of Australia's leading premium real estate brands, and founder of KNIC Ventures. He has 15+ years of experience in PropTech, including founding and exiting HomePrezzo.*
