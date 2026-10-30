#!/usr/bin/env python3
"""Builds the translated pages of the Psalmwell website into site/.

Sources live in site-src/: home.html, privacy.html, terms.html and strings.json (English), and
site-src/i18n/<code>_<page>.html / <code>_strings.json for each language in langs.json.
Run: python3 scripts/build_site.py
"""
import json, os, html, re
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'site-src'); OUT = os.path.join(ROOT, 'site')
BASE = 'https://psalmwell.web.app'
LANGS = [('en', 'en', 'English')] + [tuple(x) for x in json.load(open(os.path.join(SRC, 'langs.json')))['LANGS']]
PAGES = ['home', 'privacy', 'terms']

def path(code, page):
    p = '' if page == 'home' else page
    if code == 'en':
        return '/' + p
    return f'/{code}/{p}' if p else f'/{code}'

def load(code, name):
    f = os.path.join(SRC, name if code == 'en' else os.path.join('i18n', f'{code}_{name}'))
    return open(f, encoding='utf-8').read()

def shots(s):
    return ''.join(f'<img src="/img/shots/{i}.webp" width="540" height="960" loading="lazy" alt="{html.escape(a)}">' for i, a in enumerate(s['shots_alt'], 1))

def switcher(code, page, s):
    links = ''.join(f'<a href="{path(c, page)}" hreflang="{hl}" lang="{hl}"' + (' aria-current="true"' if c == code else '') + f'>{html.escape(n)}</a>' for c, hl, n in LANGS)
    return f'<details class="lang"><summary aria-label="{html.escape(s["language"])}">&#127760; {html.escape(s["lang_name"])}</summary><div>{links}</div></details>'

def localize_links(frag, code):
    if code == 'en':
        return frag
    for p in ('privacy', 'terms'):
        frag = frag.replace(f'href="/{p}"', f'href="/{code}/{p}"')
    return frag.replace('href="/"', f'href="/{code}"')

def page(code, hl, page_name, s):
    body = load(code, page_name + '.html')
    if page_name == 'home':
        if code != 'en':
            # Scripture stays in the World English Bible's own words; it is never machine translated.
            en_verse = re.search(r'<p class="verse">.*?</p>', load('en', 'home.html'), re.S).group(0)
            body = re.sub(r'<p class="verse">.*?</p>', lambda m: en_verse.replace('<p class="verse">', '<p class="verse" lang="en" dir="ltr">'), body, count=1, flags=re.S)
        body = body.replace('{{SHOTS}}', shots(s)).replace('{{NOTE}}', '' if code == 'en' else f'<p class="note">{html.escape(s["app_lang_note"])}</p>')
    elif code != 'en':
        body = f'<p class="legal">{html.escape(s["legal_note"])}</p>\n' + body
    body = localize_links(body, code)
    title = s['home_title'] if page_name == 'home' else s[page_name + '_title']
    desc = s['home_desc'] if page_name == 'home' else s[page_name + '_desc']
    alts = ''.join(f'<link rel="alternate" hreflang="{h}" href="{BASE}{path(c, page_name)}">' for c, h, _ in LANGS) + f'<link rel="alternate" hreflang="x-default" href="{BASE}{path("en", page_name)}">'
    en = code == 'en'
    def u(p):
        return path(code, p)
    nav = (f'<a href="{u("privacy")}">{s["nav_privacy"]}</a><a href="{u("terms")}">{s["nav_terms"]}</a>'
           f'<a href="/community-rules">{s["nav_rules"]}</a><a href="/child-safety">{s["nav_child"]}</a><a href="/support">{s["nav_support"]}</a>')
    foot = (f'<a href="{u("privacy")}">{s["f_privacy"]}</a> · <a href="{u("terms")}">{s["f_terms"]}</a> · <a href="/community-rules">{s["f_rules"]}</a> · '
            f'<a href="/child-safety">{s["f_child"]}</a> · <a href="/delete-account">{s["f_delete"]}</a> · <a href="/support">{s["f_support"]}</a>')
    d = ' dir="rtl"' if hl == 'ar' else ''
    return f"""<!doctype html>
<html lang="{hl}"{d}><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{html.escape(title)}</title><meta name="description" content="{html.escape(desc)}"><link rel="canonical" href="{BASE}{u(page_name)}">{alts}
<meta property="og:title" content="{html.escape(title)}"><meta property="og:description" content="{html.escape(desc)}"><meta property="og:image" content="{BASE}/img/og.jpg"><meta property="og:url" content="{BASE}{u(page_name)}"><meta name="twitter:card" content="summary_large_image">
<link rel="icon" href="/icon.svg" type="image/svg+xml"><link rel="stylesheet" href="/styles.css"></head>
<body><header><div class="wrap"><a class="brand" href="{u('home')}">Psalmwell</a><nav>{nav}</nav>{switcher(code, page_name, s)}</div></header>
<main class="wrap">
{body}
</main>
<footer><div class="wrap">{html.escape(s['footer_line'])}<br>
{foot}</div></footer></body></html>
"""

def main():
    urls = []
    for code, hl, _ in LANGS:
        s = json.loads(load(code, 'strings.json'))
        for p in PAGES:
            rel = path(code, p).strip('/')  # '/es' -> site/es/index.html
            d = os.path.join(OUT, rel) if rel else OUT
            os.makedirs(d, exist_ok=True)
            open(os.path.join(d, 'index.html'), 'w', encoding='utf-8').write(page(code, hl, p, s))
            urls.append(BASE + path(code, p))
    for p in ('community-rules', 'child-safety', 'support', 'delete-account'):
        urls.append(f'{BASE}/{p}')
    open(os.path.join(OUT, 'sitemap.xml'), 'w').write('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">' + ''.join(f'<url><loc>{x}</loc></url>' for x in urls) + '</urlset>\n')
    print(len(urls), 'urls')

if __name__ == '__main__':
    main()
