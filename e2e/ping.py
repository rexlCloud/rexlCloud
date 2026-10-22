"""Server list ping: prints the status JSON of a Minecraft server or proxy.

    python3 e2e/ping.py 127.0.0.1 25565
"""
import json
import socket
import struct
import sys


def varint(value):
    out = b""
    value &= 0xFFFFFFFF
    while True:
        byte = value & 0x7F
        value >>= 7
        out += struct.pack("B", byte | (0x80 if value else 0))
        if not value:
            return out


def read_varint(sock):
    result = 0
    for shift in range(0, 35, 7):
        byte = sock.recv(1)
        if not byte:
            raise ConnectionError("connection closed")
        result |= (byte[0] & 0x7F) << shift
        if not byte[0] & 0x80:
            return result
    raise ValueError("VarInt too long")


def packet(packet_id, payload=b""):
    body = varint(packet_id) + payload
    return varint(len(body)) + body


def ping(host, port):
    with socket.create_connection((host, port), timeout=10) as sock:
        host_bytes = host.encode()
        # Handshake with protocol -1 ("any"), next state 1 = status
        handshake = varint(-1) + varint(len(host_bytes)) + host_bytes + struct.pack(">H", port) + varint(1)
        sock.sendall(packet(0, handshake) + packet(0))
        read_varint(sock)
        if read_varint(sock) != 0:
            raise ValueError("not a status response")
        length = read_varint(sock)
        data = b""
        while len(data) < length:
            chunk = sock.recv(length - len(data))
            if not chunk:
                raise ConnectionError("connection closed")
            data += chunk
        return json.loads(data.decode())


if __name__ == "__main__":
    print(json.dumps(ping(sys.argv[1], int(sys.argv[2]))))
