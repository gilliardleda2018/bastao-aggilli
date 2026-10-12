"""Gera os recursos do app a partir da logomarca original. Uso: python tools/gerar_marca.py docs/marca/logo-aggilli-agro.webp android/app/src/main/res <pasta-temp>"""
import os
import sys

from PIL import Image

logo_path, res, scratch = sys.argv[1], sys.argv[2], sys.argv[3]
logo = Image.open(logo_path).convert("RGBA")

# 1) Símbolo: região do "A" com a folha, sem o "A" prateado da palavra AGGILLI
sim = logo.crop((101, 15, 776, 552)).copy()
px = sim.load()
w, h = sim.size
for y in range(h):
    borda = 345 + 0.625 * y + 18  # borda externa da haste direita (do vértice ao canto de baixo)
    for x in range(int(borda), w):
        px[x, y] = (0, 0, 0, 0)  # resto da palavra AGGILLI e do traço do AGRO
bb = sim.getchannel("A").point(lambda v: 255 if v > 40 else 0).getbbox()
sim = sim.crop(bb)
sim.save(os.path.join(scratch, "simbolo_limpo.png"))

# 2) Logomarca completa, recortada nas bordas, para o cabeçalho
full = logo.crop(logo.getchannel("A").point(lambda v: 255 if v > 40 else 0).getbbox())
full.thumbnail((1200, 1200), Image.LANCZOS)
nodpi = os.path.join(res, "drawable-nodpi")
os.makedirs(nodpi, exist_ok=True)
full.save(os.path.join(nodpi, "logo_aggilli_agro.png"), optimize=True)


def centrado(img, lado, ocupacao, sobe=0.0):
    """img centralizado num quadrado transparente; o maior lado ocupa `ocupacao` do quadrado."""
    canvas = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
    i = img.copy()
    alvo = int(lado * ocupacao)
    i.thumbnail((alvo, alvo), Image.LANCZOS)
    canvas.alpha_composite(i, ((lado - i.width) // 2, (lado - i.height) // 2 - round(sobe * lado)))
    return canvas


def silhueta(img, cor):
    alpha = img.getchannel("A")
    s = Image.new("RGBA", img.size, cor)
    s.putalpha(alpha)
    return s


# 3) Ícone adaptativo: 108 dp por densidade; o símbolo cabe no círculo seguro de 66 dp
densidades = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
for nome, f in densidades.items():
    pasta = os.path.join(res, f"mipmap-{nome}")
    os.makedirs(pasta, exist_ok=True)
    lado = round(108 * f)
    fg = centrado(sim, lado, 0.52, sobe=0.047)  # cantos de baixo dentro do círculo de 66 dp
    fg.save(os.path.join(pasta, "ic_launcher_fg.png"), optimize=True)
    silhueta(fg, (0, 0, 0, 255)).save(os.path.join(pasta, "ic_launcher_mono.png"), optimize=True)

# 4) Ícone da notificação: 24 dp, silhueta branca
for nome, f in densidades.items():
    pasta = os.path.join(res, f"drawable-{nome}")
    os.makedirs(pasta, exist_ok=True)
    lado = round(24 * f)
    silhueta(centrado(sim, lado, 0.92), (255, 255, 255, 255)).save(
        os.path.join(pasta, "ic_stat_bastao.png"), optimize=True)

print("simbolo", sim.size, "logo", full.size)
