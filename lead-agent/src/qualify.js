// Optional AI step: Claude reads what the crawler found and rates how good a fit the
// business is for AI automation services, with a short tailored opening line.
// Enabled when ANTHROPIC_API_KEY is set.
import Anthropic from '@anthropic-ai/sdk';
import { z } from 'zod';
import { betaZodOutputFormat } from '@anthropic-ai/sdk/helpers/beta/zod';

const MODEL = process.env.CLAUDE_MODEL || 'claude-opus-5-5';

const Assessment = z.object({
  score: z.number().int().describe('1-10: how strong a lead this is for AI automation services'),
  summary: z.string().describe('One or two sentences on what the business does'),
  opportunities: z.array(z.string()).describe('2-4 specific automations that would help this business'),
  opener: z.string().describe('A short, friendly first line for a cold email or WhatsApp message'),
  language: z.string().describe('Main language of the website, e.g. English or Arabic'),
});

let client;
export const aiEnabled = () => Boolean(process.env.ANTHROPIC_API_KEY);

const SYSTEM = `You help a small agency that sells AI automation to local businesses: AI receptionists and chatbots (website, WhatsApp, Instagram DMs), online booking with automatic reminders, missed-call text-back, review requests, lead follow-up, and back-office automation.

You get facts about one business: its listing and what a crawler saw on its website. Judge how promising it is as a client.

Score higher when: the business takes appointments or bookings, gets lots of enquiries, has many reviews (busy), has contact details we can use, and lacks modern tools (no booking system, no chat, slow or no website). Score lower when: it already runs a chatbot and online booking, it looks closed or like a big chain, or we have no way to contact it.

Be concrete: name automations that fit this specific business. The opener must sound like a person, reference something real about the business, and not mention AI hype words. Write the opener in the website's main language.`;

export async function qualifyLead(lead) {
  client ||= new Anthropic();
  const e = lead.enrichment || {};
  const facts = {
    name: lead.name,
    category: lead.category,
    address: lead.address,
    google_rating: lead.rating,
    review_count: lead.reviews,
    website: lead.website || null,
    website_reachable: lead.website ? !e.error : null,
    website_uses_https: e.https ?? null,
    found_emails: lead.emails.length,
    found_phones: lead.phones.length,
    social_profiles: Object.keys(lead.socials || {}),
    chat_widgets_on_site: e.chatWidgets || [],
    booking_tools_on_site: e.bookingTools || [],
    has_contact_form: e.hasForm ?? null,
    page_title: e.title || null,
    meta_description: e.description || null,
    website_text_excerpt: (e.textSample || '').slice(0, 4000) || null,
  };

  const response = await client.beta.messages.parse({
    model: MODEL,
    max_tokens: 4000,
    output_config: { effort: 'low', format: betaZodOutputFormat(Assessment) },
    betas: ['server-side-fallback-2026-07-01'],
    fallbacks: 'default',
    system: SYSTEM,
    messages: [{ role: 'user', content: `Assess this business:\n${JSON.stringify(facts, null, 2)}` }],
  });

  if (response.stop_reason === 'refusal') throw new Error('Claude declined to assess this lead');
  if (!response.parsed_output) throw new Error(`No assessment returned (stop: ${response.stop_reason})`);
  const out = response.parsed_output;
  return { ...out, score: Math.max(1, Math.min(10, out.score)), model: response.model, at: new Date().toISOString() };
}
