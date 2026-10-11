#pragma once
// Logica pura do bastao (sem Arduino), testada no PC em firmware/test_host:
//  - TriggerFsm: gatilho com filtro de ruido, toque curto e toque longo
//  - batteryPercent: tensao da celula Li-ion -> carga aproximada
//  - civil time: data/hora UNIX -> data e hora de calendario (para o cartao SD)
//  - csvLine / csvFileName: registros do historico no cartao SD
//  - buildScreen: o que o display mostra em cada situacao

#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

namespace logic {

// ---------------------------------------------------------------------------
// Gatilho
// ---------------------------------------------------------------------------
enum TriggerEvent : uint8_t { EV_NONE = 0, EV_PRESS, EV_RELEASE, EV_LONG_PRESS };

class TriggerFsm {
 public:
  TriggerFsm(uint32_t debounceMs = 30, uint32_t longMs = 1500) : debounce_(debounceMs), long_(longMs) {}

  // Chamar a cada volta do loop com o estado bruto do pino (true = apertado).
  TriggerEvent update(bool rawPressed, uint32_t nowMs) {
    if (rawPressed != lastRaw_) { lastRaw_ = rawPressed; changedAt_ = nowMs; }
    if ((uint32_t)(nowMs - changedAt_) >= debounce_ && rawPressed != stable_) {
      stable_ = rawPressed;
      if (stable_) { pressedAt_ = nowMs; longFired_ = false; return EV_PRESS; }
      return EV_RELEASE;
    }
    if (stable_ && !longFired_ && (uint32_t)(nowMs - pressedAt_) >= long_) {
      longFired_ = true;
      return EV_LONG_PRESS;
    }
    return EV_NONE;
  }
  bool pressed() const { return stable_; }

 private:
  uint32_t debounce_, long_;
  bool lastRaw_ = false, stable_ = false, longFired_ = false;
  uint32_t changedAt_ = 0, pressedAt_ = 0;
};

// ---------------------------------------------------------------------------
// Bateria Li-ion (tensao em repouso, aproximada)
// ---------------------------------------------------------------------------
inline int batteryPercent(uint32_t mv) {
  static const uint16_t MV[] = {3270, 3610, 3690, 3710, 3730, 3750, 3770, 3790, 3800, 3820, 3840,
                                3850, 3870, 3910, 3950, 3980, 4020, 4080, 4110, 4150, 4200};
  static const uint8_t PCT[] = {0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50,
                                55, 60, 65, 70, 75, 80, 85, 90, 95, 100};
  const size_t n = sizeof(MV) / sizeof(MV[0]);
  if (mv <= MV[0]) return 0;
  if (mv >= MV[n - 1]) return 100;
  for (size_t i = 1; i < n; i++) {
    if (mv <= MV[i]) {
      uint32_t span = MV[i] - MV[i - 1];
      return PCT[i - 1] + (int)((mv - MV[i - 1]) * (PCT[i] - PCT[i - 1]) / span);
    }
  }
  return 100;
}

// ---------------------------------------------------------------------------
// Data/hora: UNIX (segundos, UTC) + fuso em minutos -> calendario
// ---------------------------------------------------------------------------
struct Civil { int year, month, day, hour, minute, second; };

inline Civil toCivil(uint32_t epoch, int tzMinutes) {
  int64_t t = (int64_t)epoch + (int64_t)tzMinutes * 60;
  int64_t days = t / 86400;
  int64_t rem = t % 86400;
  if (rem < 0) { rem += 86400; days--; }
  // Algoritmo de Howard Hinnant (days_from_civil inverso)
  int64_t z = days + 719468;
  int64_t era = (z >= 0 ? z : z - 146096) / 146097;
  int64_t doe = z - era * 146097;
  int64_t yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
  int64_t y = yoe + era * 400;
  int64_t doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
  int64_t mp = (5 * doy + 2) / 153;
  int64_t d = doy - (153 * mp + 2) / 5 + 1;
  int64_t m = mp < 10 ? mp + 3 : mp - 9;
  Civil c;
  c.year = (int)(y + (m <= 2));
  c.month = (int)m;
  c.day = (int)d;
  c.hour = (int)(rem / 3600);
  c.minute = (int)((rem % 3600) / 60);
  c.second = (int)(rem % 60);
  return c;
}

// ---------------------------------------------------------------------------
// Historico no cartao SD
// ---------------------------------------------------------------------------
// Arquivo por dia quando a data e conhecida (/leituras/2026-10-11.csv); sem data, por ligacao
// do bastao (/leituras/sem-data-ligacao-0042.csv).
inline void csvFileName(uint32_t epoch, int tzMinutes, uint16_t boot, char* out, size_t outSize) {
  if (epoch > 0) {
    Civil c = toCivil(epoch, tzMinutes);
    snprintf(out, outSize, "/leituras/%04d-%02d-%02d.csv", c.year, c.month, c.day);
  } else {
    snprintf(out, outSize, "/leituras/sem-data-ligacao-%04u.csv", (unsigned)boot);
  }
}

inline const char* csvHeader() { return "seq;data_hora;brinco;ligacao;uptime_ms;origem\n"; }

// Uma linha do historico. origem: "online" (celular conectado) ou "offline" (guardada na fila).
inline int csvLine(uint32_t seq, uint32_t epoch, int tzMinutes, const char* id, uint16_t boot,
                   uint32_t uptimeMs, bool online, char* out, size_t outSize) {
  char when[32];
  if (epoch > 0) {
    Civil c = toCivil(epoch, tzMinutes);
    int tzh = tzMinutes / 60, tzm = (tzMinutes < 0 ? -tzMinutes : tzMinutes) % 60;
    snprintf(when, sizeof(when), "%04d-%02d-%02dT%02d:%02d:%02d%+03d:%02d", c.year, c.month, c.day,
             c.hour, c.minute, c.second, tzh, tzm);
  } else {
    when[0] = '\0';
  }
  return snprintf(out, outSize, "%lu;%s;%s;%u;%lu;%s\n", (unsigned long)seq, when, id, (unsigned)boot,
                  (unsigned long)uptimeMs, online ? "online" : "offline");
}

// ---------------------------------------------------------------------------
// Display (128x64): quatro linhas
// ---------------------------------------------------------------------------
enum SdState : uint8_t { SD_NONE = 0, SD_OK = 1, SD_ERROR = 2 };
enum Flash : uint8_t { FL_NONE = 0, FL_NEW, FL_STORED, FL_DUP, FL_MODE };

struct ScreenState {
  bool connected = false;
  int battPct = -1;          // -1 = desconhecida
  uint8_t mode = 0;          // 0 continuo, 1 gatilho
  SdState sd = SD_NONE;
  const char* lastId = "";   // ultimo brinco ("" = nenhum)
  int32_t sessionCount = -1; // contagem da sessao enviada pelo app (-1 = desconhecida)
  uint32_t localCount = 0;   // leituras desde que ligou
  uint32_t pending = 0;      // leituras na fila esperando o celular
  Flash flash = FL_NONE;     // aviso temporario na ultima linha
};

struct Screen {
  char status[40];   // linha de cima (fonte pequena)
  char country[8];   // pais do brinco (fonte pequena)
  char national[16]; // numero do animal (fonte grande)
  char footer[40];   // linha de baixo (fonte pequena)
};

inline void buildScreen(const ScreenState& s, Screen& out) {
  char batt[6];
  if (s.battPct >= 0) snprintf(batt, sizeof(batt), "%d%%", s.battPct > 100 ? 100 : s.battPct);
  else snprintf(batt, sizeof(batt), "--%%");
  snprintf(out.status, sizeof(out.status), "%s %s %s %s", s.connected ? "BT" : "--",
           s.mode ? "GAT" : "CONT", s.sd == SD_OK ? "SD" : (s.sd == SD_ERROR ? "SD!" : "  "), batt);

  size_t n = strlen(s.lastId);
  bool iso = (n == 15);
  for (size_t i = 0; iso && i < n; i++) iso = (s.lastId[i] >= '0' && s.lastId[i] <= '9');
  if (n == 0) {
    snprintf(out.country, sizeof(out.country), "%s", "");
    snprintf(out.national, sizeof(out.national), "%s", "aguardando");
  } else if (iso) {
    snprintf(out.country, sizeof(out.country), "%.3s", s.lastId);
    snprintf(out.national, sizeof(out.national), "%s", s.lastId + 3);
  } else {  // modo diagnostico: mostra o fim do hex bruto
    snprintf(out.country, sizeof(out.country), "%s", "RAW");
    snprintf(out.national, sizeof(out.national), "%s", n > 12 ? s.lastId + (n - 12) : s.lastId);
  }

  switch (s.flash) {
    case FL_DUP: snprintf(out.footer, sizeof(out.footer), "%s", "REPETIDO"); return;
    case FL_STORED: snprintf(out.footer, sizeof(out.footer), "GUARDADO (fila %lu)", (unsigned long)s.pending); return;
    case FL_MODE: snprintf(out.footer, sizeof(out.footer), "MODO %s", s.mode ? "GATILHO" : "CONTINUO"); return;
    default: break;
  }
  if (s.sessionCount >= 0 && s.connected)
    snprintf(out.footer, sizeof(out.footer), "Sessao %ld  Fila %lu", (long)s.sessionCount, (unsigned long)s.pending);
  else
    snprintf(out.footer, sizeof(out.footer), "Lidos %lu  Fila %lu", (unsigned long)s.localCount, (unsigned long)s.pending);
}

}  // namespace logic
