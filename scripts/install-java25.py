#!/usr/bin/env python3
"""Install Mojang's Java 25 runtime (java-runtime-epsilon) to ~/.jdk25 (Linux x64).
Loom needs Java 25 to run Gradle; the mod itself still targets Java 21."""
import json, os, sys, urllib.request, concurrent.futures as cf
DEST = os.path.expanduser(sys.argv[1] if len(sys.argv) > 1 else "~/.jdk25")
ALL = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"
get = lambda u: urllib.request.urlopen(u, timeout=60).read()
if os.path.exists(os.path.join(DEST, "bin", "java")):
    print("already installed:", DEST); sys.exit(0)
rt = json.loads(get(ALL))["linux"]["java-runtime-epsilon"][0]
files = json.loads(get(rt["manifest"]["url"]))["files"]
def fetch(item):
    path, f = item
    p = os.path.join(DEST, path)
    if f["type"] == "directory": os.makedirs(p, exist_ok=True); return
    if f["type"] == "link": return item
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "wb") as out: out.write(get(f["downloads"]["raw"]["url"]))
    if f.get("executable"): os.chmod(p, 0o755)
with cf.ThreadPoolExecutor(16) as ex: links = [l for l in ex.map(fetch, sorted(files.items())) if l]
for path, f in links:
    p = os.path.join(DEST, path)
    if not os.path.lexists(p): os.symlink(f["target"], p)
print("installed Java", rt["version"]["name"], "to", DEST)
