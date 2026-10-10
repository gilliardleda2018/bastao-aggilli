#!/usr/bin/env python3
"""Captura os frames brutos do módulo leitor RFID pela serial (etapa de bancada).

Uso:
    pip install pyserial
    python tools/serial_hexdump.py <porta> [baud]

Exemplos:
    python tools/serial_hexdump.py /dev/ttyUSB0 9600
    python tools/serial_hexdump.py COM5 9600

Ligação (adaptador USB-UART de 3,3 V): TX do módulo leitor -> RX do adaptador, GND comum.
Cada frame (separado por um silêncio de 40 ms) é impresso em hexadecimal e ASCII.
Use essa saída para conferir com o datasheet e ajustar o parser do firmware.
"""
import sys
import time

try:
    import serial  # pyserial
except ImportError:
    sys.exit("Instale o pyserial: pip install pyserial")

GAP_S = 0.040


def show(frame: bytes, count: int) -> None:
    hex_part = " ".join(f"{b:02X}" for b in frame)
    ascii_part = "".join(chr(b) if 32 <= b < 127 else "." for b in frame)
    stamp = time.strftime("%H:%M:%S")
    print(f"[{stamp}] #{count} {len(frame):3d} bytes | {hex_part} | {ascii_part}")


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    port = sys.argv[1]
    baud = int(sys.argv[2]) if len(sys.argv) > 2 else 9600

    ser = serial.Serial(port, baud, timeout=0.01)
    print(f"Lendo {port} a {baud} baud. Aproxime um brinco da antena (Ctrl+C para sair).")

    buf = bytearray()
    last = time.monotonic()
    count = 0
    try:
        while True:
            data = ser.read(64)
            now = time.monotonic()
            if data:
                buf.extend(data)
                last = now
            elif buf and (now - last) >= GAP_S:
                count += 1
                show(bytes(buf), count)
                buf.clear()
    except KeyboardInterrupt:
        print("\nEncerrado.")
    finally:
        ser.close()


if __name__ == "__main__":
    main()
