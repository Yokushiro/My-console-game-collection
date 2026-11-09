#!/usr/bin/env python3
"""Собирает офлайн-пакеты платформ (SQLite) из открытых баз Redump / No-Intro
(через libretro-database). Только стандартная библиотека Python 3.8+.

Запуск:  python tools/build_packs.py            (все платформы)
         python tools/build_packs.py ps1 n64    (только выбранные)
Результат: packs/<платформа>.db и packs/manifest.json
"""
import hashlib, json, re, sqlite3, sys, time, urllib.parse, urllib.request
from pathlib import Path

BASE = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat"

# ключ: (источник, имя файла, отображаемое название)
PLATFORMS = {
    "ps1":        ("redump",   "Sony - PlayStation",                  "PlayStation"),
    "ps2":        ("redump",   "Sony - PlayStation 2",                "PlayStation 2"),
    "ps3":        ("redump",   "Sony - PlayStation 3",                "PlayStation 3"),
    "psp":        ("redump",   "Sony - PlayStation Portable",         "PSP"),
    "vita":       ("no-intro", "Sony - PlayStation Vita",             "PS Vita"),
    "xbox":       ("redump",   "Microsoft - Xbox",                    "Xbox"),
    "xbox360":    ("redump",   "Microsoft - Xbox 360",                "Xbox 360"),
    "n64":        ("no-intro", "Nintendo - Nintendo 64",              "Nintendo 64"),
    "gamecube":   ("redump",   "Nintendo - GameCube",                 "GameCube"),
    "wii":        ("redump",   "Nintendo - Wii",                      "Wii"),
    "gba":        ("no-intro", "Nintendo - Game Boy Advance",         "Game Boy Advance"),
    "ds":         ("no-intro", "Nintendo - Nintendo DS",              "Nintendo DS"),
    "3ds":        ("no-intro", "Nintendo - Nintendo 3DS",             "Nintendo 3DS"),
    "saturn":     ("redump",   "Sega - Saturn",                       "Sega Saturn"),
    "dreamcast":  ("redump",   "Sega - Dreamcast",                    "Sega Dreamcast"),
    "megadrive":  ("no-intro", "Sega - Mega Drive - Genesis",         "Mega Drive / Genesis"),
    "megacd":     ("redump",   "Sega - Mega-CD - Sega CD",            "Mega-CD / Sega CD"),
    # не проверялись: при отсутствии файла будут пропущены
    "gb":         ("no-intro", "Nintendo - Game Boy",                 "Game Boy"),
    "gbc":        ("no-intro", "Nintendo - Game Boy Color",           "Game Boy Color"),
    "sms":        ("no-intro", "Sega - Master System - Mark III",     "Master System"),
    "gamegear":   ("no-intro", "Sega - Game Gear",                    "Game Gear"),
    "wiiu":       ("redump",   "Nintendo - Wii U",                    "Wii U"),
}

SKIP_TAG = re.compile(r"^(demo|beta|proto|sample|kiosk|unl|pirate|bios)", re.I)
LANG_TAG = re.compile(r"^[A-Z][a-z](,[A-Z][a-z])*$")
DISC_TAG = re.compile(r"^Disc (\d+)", re.I)
FIELD = re.compile(r'^\t(name|region|serial)\s+"(.*)"\s*$')


def fetch(source, name, cache):
    cache.mkdir(parents=True, exist_ok=True)
    f = cache / f"{source}__{name}.dat"
    if f.exists():
        return f.read_text(encoding="utf-8", errors="replace")
    url = f"{BASE}/{source}/{urllib.parse.quote(name)}.dat"
    try:
        with urllib.request.urlopen(url, timeout=120) as r:
            data = r.read().decode("utf-8", errors="replace")
    except Exception:
        return None
    if not data.startswith("clrmamepro"):
        return None
    f.write_text(data, encoding="utf-8")
    return data


def parse(text):
    version = (re.search(r'version\s+"([^"]*)"', text) or [None, ""])[1]
    games, cur = [], None
    for line in text.splitlines():
        if line == "game (":
            cur = {}
        elif line == ")" and cur is not None:
            games.append(cur)
            cur = None
        elif cur is not None:
            m = FIELD.match(line)
            if m:
                cur[m.group(1)] = m.group(2)
    return version, games


def norm(s):
    return re.sub(r"[^a-z0-9]+", " ", s.lower()).strip()


def convert(g):
    name = g.get("name", "")
    if not name or name.startswith("[BIOS]"):
        return None
    tags = re.findall(r"\(([^)]*)\)", name)
    if any(SKIP_TAG.match(t) for t in tags):
        return None
    title = re.split(r"\s\(", name, 1)[0].strip()
    langs = disc = None
    extra = []
    regions = g.get("region", "").split(", ")
    for t in tags:
        if LANG_TAG.match(t):
            langs = t
        elif DISC_TAG.match(t):
            disc = int(DISC_TAG.match(t).group(1))
        elif t not in regions and t != g.get("region"):
            extra.append(t)
    serials = [s.strip() for s in re.split(r"[,/]", g.get("serial", "")) if s.strip()]
    return dict(title=title, region=g.get("region", ""), languages=langs,
                disc=disc, extra="; ".join(extra), serials=serials)


def build(key, out, cache):
    source, name, label = PLATFORMS[key]
    text = fetch(source, name, cache)
    if text is None:
        print(f"  {key}: нет данных, пропуск")
        return None
    version, raw = parse(text)
    db = out / f"{key}.db"
    if db.exists():
        db.unlink()
    con = sqlite3.connect(db)
    con.executescript("""
    CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
    CREATE TABLE games(id INTEGER PRIMARY KEY, title TEXT, title_norm TEXT, region TEXT,
        languages TEXT, disc INTEGER, extra TEXT, serial TEXT);
    CREATE TABLE serial_index(serial_norm TEXT, game_id INTEGER);
    """)
    seen, n = set(), 0
    for g in raw:
        c = convert(g)
        if not c:
            continue
        k = (c["title"], c["region"], tuple(c["serials"]), c["disc"], c["extra"])
        if k in seen:
            continue
        seen.add(k)
        cur = con.execute(
            "INSERT INTO games(title,title_norm,region,languages,disc,extra,serial) VALUES(?,?,?,?,?,?,?)",
            (c["title"], norm(c["title"]), c["region"], c["languages"], c["disc"],
             c["extra"], ", ".join(c["serials"])))
        for s in c["serials"]:
            con.execute("INSERT INTO serial_index VALUES(?,?)",
                        (re.sub(r"[^A-Z0-9]", "", s.upper()), cur.lastrowid))
        n += 1
    con.executescript("CREATE INDEX i_serial ON serial_index(serial_norm);"
                      "CREATE INDEX i_title ON games(title_norm);")
    for k, v in (("platform", label), ("source", f"{source}/{name}"), ("source_version", version),
                 ("built_at", time.strftime("%Y-%m-%d"))):
        con.execute("INSERT INTO meta VALUES(?,?)", (k, v))
    con.commit()
    con.execute("VACUUM")
    con.close()
    data = db.read_bytes()
    print(f"  {key}: {n} игр (из {len(raw)}), {len(data)//1024} КБ")
    return dict(file=db.name, platform=label, games=n, bytes=len(data),
                sha256=hashlib.sha256(data).hexdigest(), source_version=version)


def main():
    keys = sys.argv[1:] or list(PLATFORMS)
    bad = [k for k in keys if k not in PLATFORMS]
    if bad:
        sys.exit(f"Неизвестные платформы: {bad}. Доступны: {list(PLATFORMS)}")
    root = Path(__file__).resolve().parent.parent
    out, cache = root / "packs", root / "packs" / "_cache"
    out.mkdir(exist_ok=True)
    manifest = {}
    for k in keys:
        m = build(k, out, cache)
        if m:
            manifest[k] = m
    (out / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Готово: {len(manifest)} пакетов в {out}")


if __name__ == "__main__":
    main()
