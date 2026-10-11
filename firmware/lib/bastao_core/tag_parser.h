#pragma once
// Parser dos frames do modulo leitor FDX-B (ISO 11784/11785).
// Codigo C++ puro, sem Arduino: compila no ESP32 e no PC (testes em firmware/test_host).

#include <stddef.h>
#include <stdint.h>

namespace tagparser {

enum Mode : uint8_t {
  MODE_ASCII15 = 0,  // ID ja em decimal ASCII (ex.: "982000123456789" ou "982 000123456789")
  MODE_RAW_HEX = 1,  // envia o frame bruto em hexadecimal (diagnostico)
  MODE_WL134   = 2,  // pacote binario de 30 bytes (familia WL-134 e compativeis)
  MODE_AUTO    = 3,  // tenta WL134 e depois ASCII15
};

// Tamanho e marcadores do pacote WL-134
constexpr size_t  WL134_LEN = 30;
constexpr uint8_t STX = 0x02;
constexpr uint8_t ETX = 0x03;

// Maior numero nacional possivel no ISO 11784 (38 bits)
constexpr uint64_t NATIONAL_MAX = 274877906943ULL;

struct Tag {
  char     id[16];    // 15 digitos: pais (3) + numero nacional (12)
  uint16_t country;   // 0..999 (900-998 = codigos de fabricante)
  uint64_t national;  // 0..274877906943
  bool     animal;    // flag "aplicacao animal" (so no WL134)
};

// Resultado do parse
enum Result : uint8_t {
  OK = 0,
  NOT_FOUND,         // nenhum pacote reconhecido no frame
  BAD_CHECKSUM,      // pacote WL134 com checksum invalido
  OUT_OF_RANGE,      // pais > 999 ou numero nacional > 38 bits
};

// Procura um pacote WL134 valido dentro de buf[0..n). Retorna OK e preenche tag.
// strictChecksum = false aceita pacote com checksum errado (so para diagnostico).
Result parseWl134(const uint8_t* buf, size_t n, Tag& tag, bool strictChecksum = true);

// Procura uma sequencia isolada de 15 digitos decimais (aceita 1 separador ' ', '_' ou '-'
// entre o pais e o numero nacional).
Result parseAscii15(const uint8_t* buf, size_t n, Tag& tag);

// Converte o frame em texto conforme o modo. Para MODE_RAW_HEX escreve o hex em out.
// out precisa de pelo menos 16 bytes (ou n*2+1 no modo RAW_HEX).
Result parse(Mode mode, const uint8_t* buf, size_t n, char* out, size_t outSize,
             bool strictChecksum = true);

// Indica se buf termina com um pacote WL134 completo (STX ... ETX com 30 bytes).
// Usado pelo firmware para processar o pacote na hora, sem esperar o silencio da UART.
bool endsWithWl134(const uint8_t* buf, size_t n);

// Monta um pacote WL134 valido (usado nos testes e no simulador).
void buildWl134(uint16_t country, uint64_t national, bool animal, uint8_t out[WL134_LEN]);

const char* resultName(Result r);

}  // namespace tagparser
