#!/usr/bin/env python3
"""Dev mock for Brand GEO site publishing.

Start from repo root:
    python tools/brand-geo-site-mock-server.py
"""

from http.server import BaseHTTPRequestHandler, HTTPServer
import json


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/api/v1/content":
            self.send_json(404, {"code": 404, "message": "not found"})
            return

        length = int(self.headers.get("Content-Length", "0") or "0")
        raw = self.rfile.read(length).decode("utf-8")
        try:
            payload = json.loads(raw or "{}")
        except json.JSONDecodeError:
            self.send_json(400, {"code": 400, "message": "invalid json"})
            return

        site_code = payload.get("siteCode")
        article_type = payload.get("articleType")
        title = payload.get("title")
        if site_code == "ok":
            self.send_json(200, {
                "code": 200,
                "message": "ok",
                "data": {
                    "id": 12345,
                    "siteCode": site_code,
                    "articleType": article_type,
                    "title": title,
                    "createdAt": "2026-04-30 12:00:00",
                },
            })
        elif site_code == "bad":
            self.send_json(400, {"code": 400, "message": "invalid request"})
        elif site_code == "fail":
            self.send_json(500, {"code": 500, "message": "server error"})
        elif site_code == "biz-fail":
            self.send_json(200, {"code": 1001, "message": "siteCode not found"})
        elif site_code == "no-id":
            self.send_json(200, {"code": 200, "message": "ok", "data": {}})
        else:
            self.send_json(400, {"code": 400, "message": "unsupported siteCode"})

    def log_message(self, fmt, *args):
        print("%s - %s" % (self.address_string(), fmt % args))

    def send_json(self, status, body):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


if __name__ == "__main__":
    server = HTTPServer(("127.0.0.1", 18080), Handler)
    print("Brand GEO site mock listening on http://127.0.0.1:18080/api/v1/content")
    server.serve_forever()
