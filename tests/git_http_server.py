#!/usr/bin/env python3
import os
import subprocess
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

REPO = os.path.abspath(sys.argv[1])
PORT_FILE = sys.argv[2]


class GitHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.run_backend()

    def do_POST(self):
        self.run_backend()

    def run_backend(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        path = self.path.split("?", 1)[0]
        env = os.environ.copy()
        env.update(
            GIT_PROJECT_ROOT=os.path.dirname(REPO),
            GIT_HTTP_EXPORT_ALL="1",
            PATH_INFO=path,
            REQUEST_METHOD=self.command,
            QUERY_STRING=self.path.split("?", 1)[1] if "?" in self.path else "",
            CONTENT_TYPE=self.headers.get("Content-Type", ""),
            CONTENT_LENGTH=str(length),
            REMOTE_ADDR="127.0.0.1",
        )
        result = subprocess.run(
            ["git", "http-backend"], input=body, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env
        )
        header, separator, payload = result.stdout.partition(b"\r\n\r\n")
        if not separator:
            header, separator, payload = result.stdout.partition(b"\n\n")
        status = 200
        headers = []
        for line in header.splitlines():
            text = line.decode("latin1")
            if text.lower().startswith("status:"):
                status = int(text.split(None, 1)[1].split()[0])
            elif ":" in text:
                key, value = text.split(":", 1)
                headers.append((key.strip(), value.strip()))
        self.send_response(status)
        for key, value in headers:
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *_):
        pass


server = ThreadingHTTPServer(("127.0.0.1", 0), GitHandler)
with open(PORT_FILE, "w", encoding="ascii") as port_file:
    port_file.write(str(server.server_port))
server.serve_forever()