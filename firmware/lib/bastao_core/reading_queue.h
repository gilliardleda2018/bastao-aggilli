#pragma once
// Fila circular das leituras ainda nao confirmadas pelo app.
// Cada leitura fica na fila ate o app responder "A;<seq>" (confirma todas ate seq).
// Se a fila enche, a leitura mais antiga e descartada (contador dropped).
// A fila tambem e gravada em flash (persist_log.h) para sobreviver a desligar o bastao.
// C++ puro, sem Arduino: testada no PC em firmware/test_host.

#include <stddef.h>
#include <stdint.h>

constexpr size_t READING_ID_MAX = 24;  // 15 digitos ISO, ou hex bruto truncado no modo diagnostico

struct QueuedReading {
  uint32_t seq;
  uint16_t boot;      // numero da ligacao do bastao em que a leitura foi feita
  uint16_t flags;     // reservado
  uint32_t uptimeMs;  // millis() no momento da leitura (vale so dentro da mesma ligacao)
  uint32_t epoch;     // data/hora UNIX em segundos; 0 = desconhecida (celular ainda nao mandou a hora)
  char     id[READING_ID_MAX];
};

template <size_t CAP>
class ReadingQueue {
 public:
  // Insere uma leitura. Retorna false se precisou descartar a mais antiga.
  bool push(uint32_t seq, uint16_t boot, uint32_t uptimeMs, uint32_t epoch, const char* id) {
    QueuedReading r;
    r.seq = seq;
    r.boot = boot;
    r.flags = 0;
    r.uptimeMs = uptimeMs;
    r.epoch = epoch;
    size_t i = 0;
    for (; id != nullptr && id[i] != '\0' && i < sizeof(r.id) - 1; i++) r.id[i] = id[i];
    r.id[i] = '\0';
    return pushEntry(r);
  }

  bool pushEntry(const QueuedReading& r) {
    bool ok = true;
    if (count_ == CAP) {  // cheia: descarta a mais antiga
      head_ = (head_ + 1) % CAP;
      count_--;
      if (sent_ > 0) sent_--;
      dropped_++;
      ok = false;
    }
    items_[(head_ + count_) % CAP] = r;
    count_++;
    return ok;
  }

  // Remove todas as leituras com seq <= ackSeq. Retorna quantas sairam.
  size_t ack(uint32_t ackSeq) {
    size_t removed = 0;
    while (count_ > 0 && (int32_t)(items_[head_].seq - ackSeq) <= 0) {
      head_ = (head_ + 1) % CAP;
      count_--;
      removed++;
      if (sent_ > 0) sent_--;
    }
    return removed;
  }

  // Proxima leitura ainda nao enviada nesta conexao (nullptr se nao ha).
  const QueuedReading* nextToSend() const {
    if (sent_ >= count_) return nullptr;
    return &items_[(head_ + sent_) % CAP];
  }
  void markSent() { if (sent_ < count_) sent_++; }

  // Nova conexao: reenvia tudo o que nao foi confirmado.
  void rewind() { sent_ = 0; }

  // Leitura i (0 = mais antiga). Para gravar a fila em flash.
  const QueuedReading& at(size_t i) const { return items_[(head_ + i) % CAP]; }

  // O celular informou a hora: completa as leituras desta ligacao que ainda nao tinham data.
  // epochAtBoot = data/hora UNIX (s) do instante em que o bastao ligou.
  size_t fillEpoch(uint16_t boot, uint32_t epochAtBoot) {
    size_t n = 0;
    for (size_t i = 0; i < count_; i++) {
      QueuedReading& r = items_[(head_ + i) % CAP];
      if (r.boot == boot && r.epoch == 0) { r.epoch = epochAtBoot + r.uptimeMs / 1000; n++; }
    }
    return n;
  }

  void clear() { head_ = count_ = sent_ = 0; }
  size_t size() const { return count_; }
  size_t unsent() const { return count_ - sent_; }
  uint32_t dropped() const { return dropped_; }
  static constexpr size_t capacity() { return CAP; }

 private:
  QueuedReading items_[CAP];
  size_t head_ = 0;
  size_t count_ = 0;
  size_t sent_ = 0;   // quantas (a partir do head) ja foram enviadas nesta conexao
  uint32_t dropped_ = 0;
};
