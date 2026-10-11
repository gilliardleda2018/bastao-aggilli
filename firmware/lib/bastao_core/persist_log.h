#pragma once
// Diario (log) da fila em flash: sobrevive a desligar o bastao.
//
// Cada evento vira um registro de tamanho fixo (REC_SIZE bytes) acrescentado ao fim do arquivo:
//   PUSH: uma leitura nova entrou na fila
//   ACK : o app confirmou as leituras ate seq
// Ao ligar, o firmware le o arquivo do inicio e refaz a fila (replay). Um registro cortado ao meio
// (falta de energia durante a gravacao) ou com CRC errado e ignorado, assim como tudo depois dele.
// Quando o arquivo cresce demais, o firmware o reescreve so com a fila atual (compactacao).
//
// C++ puro, sem Arduino: testado no PC. O firmware so cuida de abrir/gravar o arquivo (LittleFS).

#include <stddef.h>
#include <stdint.h>
#include <string.h>

#include "reading_queue.h"

namespace plog {

constexpr size_t  REC_SIZE = 48;
constexpr uint8_t MAGIC = 0xB7;
constexpr uint8_t T_PUSH = 'P';
constexpr uint8_t T_ACK = 'A';

// Layout (little-endian):
//  [0] MAGIC  [1] tipo  [2] crc8 dos bytes 4..47  [3] reservado
//  [4..7] seq  [8..9] boot  [10..11] flags  [12..15] uptimeMs  [16..19] epoch  [20..43] id  [44..47] 0

inline uint8_t crc8(const uint8_t* d, size_t n) {
  uint8_t c = 0;
  for (size_t i = 0; i < n; i++) {
    c ^= d[i];
    for (int b = 0; b < 8; b++) c = (c & 0x80) ? (uint8_t)((c << 1) ^ 0x07) : (uint8_t)(c << 1);
  }
  return c;
}

inline void put32(uint8_t* p, uint32_t v) { p[0] = v; p[1] = v >> 8; p[2] = v >> 16; p[3] = v >> 24; }
inline void put16(uint8_t* p, uint16_t v) { p[0] = v; p[1] = v >> 8; }
inline uint32_t get32(const uint8_t* p) { return p[0] | (p[1] << 8) | (p[2] << 16) | ((uint32_t)p[3] << 24); }
inline uint16_t get16(const uint8_t* p) { return (uint16_t)(p[0] | (p[1] << 8)); }

inline void seal(uint8_t* rec, uint8_t type) {
  rec[0] = MAGIC;
  rec[1] = type;
  rec[3] = 0;
  rec[2] = crc8(rec + 4, REC_SIZE - 4);
}

inline void encodePush(const QueuedReading& r, uint8_t rec[REC_SIZE]) {
  memset(rec, 0, REC_SIZE);
  put32(rec + 4, r.seq);
  put16(rec + 8, r.boot);
  put16(rec + 10, r.flags);
  put32(rec + 12, r.uptimeMs);
  put32(rec + 16, r.epoch);
  size_t n = strnlen(r.id, READING_ID_MAX - 1);
  memcpy(rec + 20, r.id, n);
  seal(rec, T_PUSH);
}

inline void encodeAck(uint32_t seq, uint8_t rec[REC_SIZE]) {
  memset(rec, 0, REC_SIZE);
  put32(rec + 4, seq);
  seal(rec, T_ACK);
}

struct ReplayResult {
  size_t records = 0;     // registros validos lidos
  size_t validBytes = 0;  // bytes ate o ultimo registro valido (o resto e lixo e pode ser truncado)
  uint32_t maxSeq = 0;    // maior seq visto (para continuar a numeracao depois de religar)
  uint16_t maxBoot = 0;   // maior numero de ligacao visto
  bool corrupted = false; // havia um registro invalido (o arquivo deve ser compactado)
};

// Refaz a fila a partir do conteudo do arquivo.
template <size_t CAP>
ReplayResult replay(const uint8_t* data, size_t len, ReadingQueue<CAP>& q) {
  ReplayResult res;
  q.clear();
  bool anySeq = false;
  size_t off = 0;
  while (off + REC_SIZE <= len) {
    const uint8_t* rec = data + off;
    if (rec[0] != MAGIC || rec[2] != crc8(rec + 4, REC_SIZE - 4) ||
        (rec[1] != T_PUSH && rec[1] != T_ACK)) {
      res.corrupted = true;
      break;
    }
    uint32_t seq = get32(rec + 4);
    if (rec[1] == T_PUSH) {
      QueuedReading r;
      r.seq = seq;
      r.boot = get16(rec + 8);
      r.flags = get16(rec + 10);
      r.uptimeMs = get32(rec + 12);
      r.epoch = get32(rec + 16);
      memcpy(r.id, rec + 20, READING_ID_MAX);
      r.id[READING_ID_MAX - 1] = '\0';
      q.pushEntry(r);
      if (!anySeq || (int32_t)(seq - res.maxSeq) > 0) res.maxSeq = seq;
      anySeq = true;
      if (r.boot > res.maxBoot) res.maxBoot = r.boot;
    } else {
      q.ack(seq);
      if (!anySeq || (int32_t)(seq - res.maxSeq) > 0) res.maxSeq = seq;
      anySeq = true;
    }
    off += REC_SIZE;
    res.records++;
  }
  if (off < len) res.corrupted = true;  // sobra no fim: registro cortado
  res.validBytes = off;
  q.rewind();
  return res;
}

// Quantos bytes o arquivo compactado tera (um ACK marcador + os PUSH da fila atual).
template <size_t CAP>
size_t compactSize(const ReadingQueue<CAP>& q) { return (q.size() + 1) * REC_SIZE; }

// Gera o arquivo compactado registro por registro: write(rec, REC_SIZE) e chamado para cada um.
// O primeiro registro e um ACK com lastSeq: guarda a numeracao mesmo com a fila vazia, e como vem
// antes dos PUSH nao remove nenhuma leitura no replay.
template <size_t CAP, typename WriteFn>
void compact(const ReadingQueue<CAP>& q, uint32_t lastSeq, WriteFn write) {
  uint8_t rec[REC_SIZE];
  encodeAck(lastSeq, rec);
  write(rec, REC_SIZE);
  for (size_t i = 0; i < q.size(); i++) {
    encodePush(q.at(i), rec);
    write(rec, REC_SIZE);
  }
}

}  // namespace plog
