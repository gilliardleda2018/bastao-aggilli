# PlatformIO para compilar o firmware do ESP32 (o compilador do ESP32 fica no volume pio-cache).
FROM python:3.11-slim
RUN pip install --no-cache-dir platformio
