#!/usr/bin/env python3
"""
License: MIT License
Copyright (c) 2023 Miel Donkers

Very simple HTTP server in python for logging requests
Usage::
    ./server.py [<port>]
"""
from http.server import BaseHTTPRequestHandler, HTTPServer
import logging


class S(BaseHTTPRequestHandler):
    def _set_response(self, status_code=200): # default status code is 200
        self.send_response(status_code)
        self.send_header('Content-type', 'text/html')
        self.end_headers()

    def do_GET(self):
        if (self.path == '/token'):
            self._set_response()
            self.wfile.write("valid_token".encode('utf-8'))
            return
        logging.info("GET request,\nPath: %s\nHeaders:\n%s\n", str(self.path), str(self.headers))
        self._set_response()
        self.wfile.write("GET request for {}".format(self.path).encode('utf-8'))

    def do_POST(self):
        logging.info('headers', self.headers)
        if self.path == '/authenticated' and self.headers['Authorization'] != 'Bearer valid_token':
            self._set_response(401)
            self.wfile.write("Not Authorized!".encode('utf-8'))
        else:
            content_length = int(self.headers['Content-Length'])  # <--- Gets the size of data
            post_data = self.rfile.read(content_length)  # <--- Gets the data itself
            logging.info("POST request,\nPath: %s\nHeaders:\n%s\n\nBody:\n%s\n",
                         str(self.path), str(self.headers), post_data.decode('utf-8'))

            self._set_response()
            self.wfile.write(
                "POST request for {} with body {}".format(self.path, post_data.decode('utf-8')).encode('utf-8'))


def run(server_class=HTTPServer, handler_class=S, port=8100):
    logging.basicConfig(level=logging.INFO)
    server_address = ('', port)
    httpd = server_class(server_address, handler_class)
    logging.info('Starting httpd...\n')
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    httpd.server_close()
    logging.info('Stopping httpd...\n')


if __name__ == '__main__':
    from sys import argv

    if len(argv) == 2:
        run(port=int(argv[1]))
    else:
        run()
