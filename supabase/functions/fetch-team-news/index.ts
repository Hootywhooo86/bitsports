// bitsports: fetches a team's news from Google News RSS (free, no key), caches it in
// public.team_news, and returns the normalized list. Deployed to the shared
// "128bit play" Supabase project. Called directly from the client with the publishable key.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
};

const CACHE_MINUTES = 15;
const MAX_ITEMS = 12;

function slug(s: string) {
  return s.trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
}

function decodeEntities(s: string) {
  return s
    .replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'").replace(/&nbsp;/g, " ").replace(/&amp;/g, "&");
}

type NewsItem = { title: string; link: string; source: string; publishedAt: string | null };

// Google News RSS gives no real article summary (its <description> is a disguised
// link), so we only take title/link/source/date and skip the fake snippet.
function parseRssItems(xml: string): NewsItem[] {
  const items: NewsItem[] = [];
  const blocks = xml.split("<item>").slice(1);
  for (const block of blocks) {
    const body = block.split("</item>")[0];
    const grab = (tag: string) => {
      const m = body.match(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)</${tag}>`));
      if (!m) return "";
      return decodeEntities(m[1].replace("<![CDATA[", "").replace("]]>", "")).trim();
    };
    const title = grab("title");
    const link = grab("link");
    const pubDate = grab("pubDate");
    const source = grab("source");
    if (!title || !link) continue;
    let publishedAt: string | null = null;
    if (pubDate) {
      const d = new Date(pubDate);
      if (!Number.isNaN(d.getTime())) publishedAt = d.toISOString();
    }
    items.push({ title, link, source: source || "Google News", publishedAt });
  }
  return items;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });

  const url = new URL(req.url);
  const team = (url.searchParams.get("team") || "").trim();
  const sport = (url.searchParams.get("sport") || "").trim().toLowerCase();
  const league = (url.searchParams.get("league") || "").trim();
  const fresh = url.searchParams.get("fresh") === "1";
  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { ...CORS, "content-type": "application/json" } });

  if (!team || !sport) return json({ error: "team and sport are required" }, 400);

  const teamKey = `${sport}:${slug(team)}`;
  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

  const readCache = () =>
    supabase
      .from("team_news")
      .select("title, link, source, published_at, fetched_at")
      .eq("team_key", teamKey)
      .order("published_at", { ascending: false, nullsFirst: false })
      .limit(MAX_ITEMS);

  if (!fresh) {
    const { data: cached } = await readCache();
    if (cached?.length && Date.now() - new Date(cached[0].fetched_at).getTime() < CACHE_MINUTES * 60_000) {
      return json({ teamKey, items: cached, cached: true });
    }
  }

  const q = encodeURIComponent(league ? `${team} ${league}` : team);
  const rssUrl = `https://news.google.com/rss/search?q=${q}&hl=en-US&gl=US&ceid=US:en`;

  let items: NewsItem[];
  try {
    const res = await fetch(rssUrl, { headers: { "user-agent": "Mozilla/5.0 (bitsports news fetcher)" } });
    if (!res.ok) throw new Error(`upstream ${res.status}`);
    items = parseRssItems(await res.text()).slice(0, MAX_ITEMS);
  } catch (e) {
    // Upstream is down or unreachable: serve what we have cached rather than fail hard.
    const { data: cached } = await readCache();
    if (cached?.length) return json({ teamKey, items: cached, cached: true, stale: true });
    return json({ error: String(e) }, 502);
  }

  if (items.length) {
    const rows = items.map((it) => ({
      sport,
      team_key: teamKey,
      team_name: team,
      title: it.title,
      link: it.link,
      source: it.source,
      published_at: it.publishedAt,
      snippet: "",
      fetched_at: new Date().toISOString(),
    }));
    await supabase.from("team_news").upsert(rows, { onConflict: "team_key,link" });
  }

  return json({ teamKey, items, cached: false });
});
