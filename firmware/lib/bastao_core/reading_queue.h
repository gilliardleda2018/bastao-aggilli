#pragma once
// Fila circular das leituras ainda nao confirmadas pelo app.
// Cada leitura fica na fila ate o app responder "A;<seq>" (confirma todas ate seq).
// Se a fila enche, a leitura mais antiga e descartada (contador dropped).
// C++ puro, sem Arduino: testada no PC em firmware/test_host.

#include <stddef.h>
#include <stdint.h>

struct QueuedReading {
  uint32_t seq;
  uint32_t uptimeMs;  // millis() no momento da leitura
  char     id[64];    // 15 digitos, ou hex bruto truncado no modo diagnostico
};

template <size_t CAP>
class ReadingQueue {
 public:
  // Insere uma leitura. Retorna false se precisou descartar a mais antiga.
  bool push(uint32_t seq, uint32_t uptimeMs, const char* id) {
    bool ok = true;
    if (count_ == CAP) {          // cheia: descarta a mais antiga
      head_ = (head_ + 1) % CAP;
      count_--;
      if (sent_ > 0) sent_--;
      dropped_++;
      ok = false;
    }
    QueuedReading& r = items_[(head_ + count_) % CAP];
    r.seq = seq;
    r.uptimeMs = uptimeMs;
    size_t i = 0;
    for (; id[i] != '\0' && i < sizeof(r.id) - 1; i++) r.id[i] = id[i];
    r.id[i] = '\0';
    count_++;
    return ok;
  }

  // Remove todas as leituras com seq <= ackSeq. Retorna quantas saíram.
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
