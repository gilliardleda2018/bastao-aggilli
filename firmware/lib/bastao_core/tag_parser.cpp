#include "tag_parser.h"

#include <stdio.h>
#include <string.h>

namespace tagparser {

// Layout do pacote WL134 (30 bytes):
//   [0]      STX 0x02
//   [1..10]  numero nacional, 10 caracteres hex ASCII, digito MENOS significativo primeiro
//   [11..14] codigo do pais, 4 caracteres hex ASCII, menos significativo primeiro
//   [15]     flag de bloco de dados ('0'/'1')
//   [16]     flag de aplicacao animal ('0'/'1')
//   [17..20] reservado (4 hex)
//   [21..26] reservado/estendido (6 hex)
//   [27]     checksum: XOR dos bytes [1..26]
//   [28]     complemento do checksum (~checksum)
//   [29]     ETX 0x03
// CONFIRMAR em bancada com o datasheet do modulo comprado (docs/06-plano-de-testes.md).

namespace {

int hexVal(uint8_t c) {
  if (c >= '0' && c <= '9') return c - '0';
  if (c >= 'A' && c <= 'F') return c - 'A' + 10;
  if (c >= 'a' && c <= 'f') return c - 'a' + 10;
  return -1;
}

const char HEX_UP[] = "0123456789ABCDEF";

// Hex ASCII com o digito menos significativo primeiro.
bool hexLsbFirst(const uint8_t* p, size_t len, uint64_t& out) {
  uint64_t v = 0;
  for (size_t i = 0; i < len; i++) {
    int h = hexVal(p[i]);
    if (h < 0) return false;
    v |= (uint64_t)h << (4 * i);
  }
  out = v;
  return true;
}

void formatId(uint16_t country, uint64_t national, char out[16]) {
  // 3 + 12 digitos, sem depender de printf com %llu (nem sempre disponivel)
  char tmp[16];
  uint64_t n = national;
  for (int i = 14; i >= 3; i--) { tmp[i] = (char)('0' + (n % 10)); n /= 10; }
  uint16_t c = country;
  for (int i = 2; i >= 0; i--) { tmp[i] = (char)('0' + (c % 10)); c /= 10; }
  tmp[15] = '\0';
  memcpy(out, tmp, 16);
}

bool isDigit(uint8_t c) { return c >= '0' && c <= '9'; }

Result checkPacket(const uint8_t* p, Tag& tag, bool strict) {
  if (p[0] != STX || p[WL134_LEN - 1] != ETX) return NOT_FOUND;

  uint64_t national = 0, country = 0;
  if (!hexLsbFirst(p + 1, 10, national)) return NOT_FOUND;
  if (!hexLsbFirst(p + 11, 4, country)) return NOT_FOUND;

  uint8_t x = 0;
  for (size_t i = 1; i <= 26; i++) x ^= p[i];
  bool sumOk = (p[27] == x) && (p[28] == (uint8_t)~x);
  if (!sumOk && strict) return BAD_CHECKSUM;

  if (country > 999 || national > NATIONAL_MAX) return OUT_OF_RANGE;

  tag.country = (uint16_t)country;
  tag.national = national;
  tag.animal = (p[16] == '1');
  formatId(tag.country, tag.national, tag.id);
  return OK;
}

}  // namespace

Result parseWl134(const uint8_t* buf, size_t n, Tag& tag, bool strictChecksum) {
  if (buf == nullptr || n < WL134_LEN) return NOT_FOUND;
  Result worst = NOT_FOUND;
  for (size_t i = 0; i + WL134_LEN <= n; i++) {
    if (buf[i] != STX) continue;
    Result r = checkPacket(buf + i, tag, strictChecksum);
    if (r == OK) return OK;
    if (r != NOT_FOUND) worst = r;  // guarda o erro mais informativo
  }
  return worst;
}

Result parseAscii15(const uint8_t* buf, size_t n, Tag& tag) {
  if (buf == nullptr) return NOT_FOUND;
  size_t i = 0;
  while (i < n) {
    if (!isDigit(buf[i]) || (i > 0 && isDigit(buf[i - 1]))) { i++; continue; }
    // Inicio de uma sequencia de digitos
    char d[16];
    size_t k = 0, j = i;
    bool usedSep = false;
    while (j < n && k < 16) {
      if (isDigit(buf[j])) { if (k < 15) d[k] = (char)buf[j]; k++; j++; continue; }
      bool sep = (buf[j] == ' ' || buf[j] == '_' || buf[j] == '-');
      if (sep && !usedSep && k == 3 && j + 1 < n && isDigit(buf[j + 1])) { usedSep = true; j++; continue; }
      break;
    }
    if (k == 15) {
      uint64_t national = 0;
      for (int q = 3; q < 15; q++) national = national * 10 + (uint64_t)(d[q] - '0');
      uint16_t country = (uint16_t)((d[0] - '0') * 100 + (d[1] - '0') * 10 + (d[2] - '0'));
      if (national > NATIONAL_MAX) return OUT_OF_RANGE;
      tag.country = country;
      tag.national = national;
      tag.animal = false;
      formatId(country, national, tag.id);
      return OK;
    }
    i = (j > i) ? j : i + 1;
  }
  return NOT_FOUND;
}

Result parse(Mode mode, const uint8_t* buf, size_t n, char* out, size_t outSize, bool strictChecksum) {
  if (out == nullptr || outSize == 0) return NOT_FOUND;
  out[0] = '\0';

  if (mode == MODE_RAW_HEX) {
    if (outSize < n * 2 + 1) return NOT_FOUND;
    for (size_t i = 0; i < n; i++) {
      out[i * 2] = HEX_UP[buf[i] >> 4];
      out[i * 2 + 1] = HEX_UP[buf[i] & 0x0F];
    }
    out[n * 2] = '\0';
    return n > 0 ? OK : NOT_FOUND;
  }

  if (outSize < 16) return NOT_FOUND;
  Tag tag;
  Result r = NOT_FOUND;

  if (mode == MODE_WL134 || mode == MODE_AUTO) {
    r = parseWl134(buf, n, tag, strictChecksum);
    // No AUTO, um frame que parece WL134 mas falhou no checksum NAO cai no ASCII15:
    // os caracteres hex do pacote poderiam virar um ID falso.
    if (r != NOT_FOUND || mode == MODE_WL134) {
      if (r == OK) memcpy(out, tag.id, 16);
      return r;
    }
  }
  r = parseAscii15(buf, n, tag);
  if (r == OK) memcpy(out, tag.id, 16);
  return r;
}

bool endsWithWl134(const uint8_t* buf, size_t n) {
  return buf != nullptr && n >= WL134_LEN && buf[n - 1] == ETX && buf[n - WL134_LEN] == STX;
}

void buildWl134(uint16_t country, uint64_t national, bool animal, uint8_t out[WL134_LEN]) {
  out[0] = STX;
  for (int i = 0; i < 10; i++) out[1 + i] = (uint8_t)HEX_UP[(national >> (4 * i)) & 0xF];
  for (int i = 0; i < 4; i++) out[11 + i] = (uint8_t)HEX_UP[(country >> (4 * i)) & 0xF];
  out[15] = '0';
  out[16] = animal ? '1' : '0';
  for (int i = 17; i <= 26; i++) out[i] = '0';
  uint8_t x = 0;
  for (int i = 1; i <= 26; i++) x ^= out[i];
  out[27] = x;
  out[28] = (uint8_t)~x;
  out[29] = ETX;
}

const char* resultName(Result r) {
  switch (r) {
    case OK: return "OK";
    case NOT_FOUND: return "NAO_RECONHECIDO";
    case BAD_CHECKSUM: return "CHECKSUM_INVALIDO";
    case OUT_OF_RANGE: return "FORA_DA_FAIXA";
  }
  return "?";
}

}  // namespace tagparser
