#!/usr/bin/env python3
"""Serve the APKs to a phone over the local network and collect pasted reports back.

The laptop cannot use USB and the phones predate wireless debugging, so there is no adb. This
replaces it for the two things adb was actually needed for: getting an APK onto a phone, and
getting text off one.

    ./gradlew :app:dist :probe:assembleDebug
    tools/serve-and-collect.py

Then open http://<laptop-lan-ip>:8770 on the phone.

GET  /            the download and paste page
GET  /results/    everything collected so far
POST /results     saves a pasted report
"""

import argparse
import html
import re
import shutil
import socket
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs

MAX_BODY = 512 * 1024
HERE = Path(__file__).resolve().parent
REPO = HERE.parent


class Handler(SimpleHTTPRequestHandler):
    results_dir: Path

    def do_POST(self):
        if self.path.rstrip("/") != "/results":
            self.send_error(404)
            return

        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0 or length > MAX_BODY:
            self.send_error(413)
            return

        fields = parse_qs(self.rfile.read(length).decode("utf-8", "replace"))
        report = (fields.get("results") or [""])[0].strip()
        label = (fields.get("label") or [""])[0].strip()

        if not report:
            self.reply(400, "Nothing pasted", "The box was empty. Go back and paste the report.")
            return

        slug = re.sub(r"[^a-z0-9]+", "-", label.lower()).strip("-") or "unlabelled"
        stamp = time.strftime("%Y%m%d-%H%M%S")
        path = self.results_dir / f"{stamp}-{slug}.txt"
        path.write_text(f"label: {label or '(none)'}\nreceived: {stamp}\n\n{report}\n", "utf-8")

        print(f"\n=== received {path.name} ({len(report)} chars) ===\n{report}\n", flush=True)
        self.reply(
            200,
            "Saved",
            f"Saved as {html.escape(path.name)}. You can close this, or paste another report.",
        )

    def reply(self, code: int, heading: str, message: str):
        page = f"""<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>{heading}</title>
<style>:root{{color-scheme:light dark}}body{{font-family:-apple-system,system-ui,sans-serif;
margin:0;padding:32px 20px;line-height:1.6;max-width:32rem}}h1{{font-size:1.3rem;margin:0 0 8px}}
a{{display:inline-block;margin-top:20px}}</style></head><body>
<h1>{heading}</h1><p>{message}</p><a href="/">Back</a></body></html>"""
        body = page.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print(f"{self.address_string()} {fmt % args}", flush=True)

    def handle_one_request(self):
        # A phone that cancels a download mid-stream is normal, not an error worth a stack trace.
        try:
            super().handle_one_request()
        except (ConnectionResetError, BrokenPipeError):
            self.close_connection = True


def stage_apks(into: Path):
    """Copy the built APKs in under stable names, so the page can link to them."""
    newest_app = sorted((REPO / "dist").glob("fassistant-*.apk"), key=lambda p: p.stat().st_mtime)
    wanted = {
        "fassistant.apk": newest_app[-1] if newest_app else None,
        "probe.apk": REPO / "probe/build/outputs/apk/debug/probe-debug.apk",
    }
    for name, source in wanted.items():
        if source and source.is_file():
            shutil.copy2(source, into / name)
            print(f"staged {name} from {source.relative_to(REPO)}", flush=True)
        else:
            print(f"MISSING {name} — build it first, the page will 404 on that link", flush=True)


def lan_address() -> str:
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("192.0.2.1", 1))
        return probe.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        probe.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8770)
    parser.add_argument("--dir", default=str(HERE / "serve"))
    args = parser.parse_args()

    served = Path(args.dir).resolve()
    results = served / "results"
    results.mkdir(parents=True, exist_ok=True)
    stage_apks(served)

    Handler.results_dir = results
    handler = lambda *a, **kw: Handler(*a, directory=str(served), **kw)

    print(f"\nopen this on the phone:  http://{lan_address()}:{args.port}", flush=True)
    print(f"reports land in:         {results}\n", flush=True)
    ThreadingHTTPServer(("0.0.0.0", args.port), handler).serve_forever()


if __name__ == "__main__":
    main()
