#!/usr/bin/env python3
"""Simula o módulo leitor FDX-B enviando pacotes WL-134 (30 bytes) por uma porta serial.

Permite testar o ESP32 + app sem o módulo leitor e sem brincos:
ligue o TX de um adaptador USB-UART 3,3 V ao GPIO16 do ESP32 (GND comum) e rode:

    pip install pyserial
    python tools/simulate_tag.py /dev/ttyUSB0                     # 5 brincos aleatórios
    python tools/simulate_tag.py COM5 --ids 076000000001234 982000123456789
    python tools/simulate_tag.py COM5 --count 50 --interval 1.5   # lote de 50 animais
    python tools/simulate_tag.py COM5 --repeat 10                 # brinco parado no campo (testa anti-duplicata)
    python tools/simulate_tag.py COM5 --corrupt                   # checksum errado (deve ser descartado)
    python tools/simulate_tag.py --stdout --ids 076000000001234   # só mostra o pacote em hex

O layout do pacote é o mesmo de firmware/lib/bastao_core/tag_parser.cpp.
"""
import argparse
import random
import sys
import time

STX, ETX = 0x02, 0x03
HEX = "0123456789ABCDEF"
NATIONAL_MAX = 274877906943


def build_wl134(tag_id: str, animal: bool = True, corrupt: bool = False) -> bytes:
    if len(tag_id) != 15 or not tag_id.isdigit():
        raise ValueError(f"ID deve ter 15 dígitos: {tag_id!r}")
    country, national = int(tag_id[:3]), int(tag_id[3:])
    if national > NATIONAL_MAX:
        raise ValueError(f"Número nacional acima de 38 bits: {tag_id}")
    body = [HEX[(national >> (4 * i)) & 0xF] for i in range(10)]
    body += [HEX[(country >> (4 * i)) & 0xF] for i in range(4)]
    body += ["0", "1" if animal else "0"] + ["0"] * 10
    data = bytearray([STX]) + bytearray("".join(body), "ascii")
    x = 0
    for b in data[1:27]:
        x ^= b
    if corrupt:
        x ^= 0x5A
    data += bytes([x, (~x) & 0xFF, ETX])
    assert len(data) == 30
    return bytes(data)


def random_ids(n: int, country: str = "076") -> list:
    return [country + f"{random.randint(1, NATIONAL_MAX):012d}" for _ in range(n)]


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("port", nargs="?", help="porta serial (ex.: /dev/ttyUSB0, COM5)")
    ap.add_argument("--baud", type=int, default=9600)
    ap.add_argument("--ids", nargs="*", help="IDs de 15 dígitos")
    ap.add_argument("--count", type=int, default=5, help="quantidade de IDs aleatórios")
    ap.add_argument("--country", default="076", help="código do país dos IDs aleatórios (076 = Brasil)")
    ap.add_argument("--interval", type=float, default=2.0, help="segundos entre animais")
    ap.add_argument("--repeat", type=int, default=3, help="pacotes por animal (o módulo repete enquanto o brinco está no campo)")
    ap.add_argument("--repeat-gap", type=float, default=0.1, help="segundos entre repetições")
    ap.add_argument("--corrupt", action="store_true", help="envia com checksum errado")
    ap.add_argument("--stdout", action="store_true", help="não abre a serial; imprime os pacotes em hex")
    a = ap.parse_args()

    ids = a.ids or random_ids(a.count, a.country)

    ser = None
    if not a.stdout:
        if not a.port:
            sys.exit("Informe a porta serial ou use --stdout.")
        try:
            import serial  # pyserial
        except ImportError:
            sys.exit("Instale o pyserial: pip install pyserial")
        ser = serial.Serial(a.port, a.baud)
        print(f"Enviando para {a.port} a {a.baud} baud.")

    try:
        for n, tag in enumerate(ids, 1):
            pkt = build_wl134(tag, corrupt=a.corrupt)
            print(f"#{n} {tag}  {pkt.hex(' ').upper()}")
            if ser:
                for _ in range(a.repeat):
                    ser.write(pkt)
                    ser.flush()
                    time.sleep(a.repeat_gap)
                time.sleep(a.interval)
    except KeyboardInterrupt:
        print("\nEncerrado.")
    finally:
        if ser:
            ser.close()


if __name__ == "__main__":
    main()
