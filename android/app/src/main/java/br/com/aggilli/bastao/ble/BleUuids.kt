package br.com.aggilli.bastao.ble

import java.util.UUID

/** Mesmos UUIDs de firmware/include/config.h */
object BleUuids {
    val SERVICE: UUID = UUID.fromString("a91c0001-5f3e-4b8a-9d27-3c6e1b7f0a10")
    val READING: UUID = UUID.fromString("a91c0002-5f3e-4b8a-9d27-3c6e1b7f0a10")
    val STATUS: UUID = UUID.fromString("a91c0003-5f3e-4b8a-9d27-3c6e1b7f0a10")
    val CONTROL: UUID = UUID.fromString("a91c0004-5f3e-4b8a-9d27-3c6e1b7f0a10")

    /** Client Characteristic Configuration Descriptor (padrão Bluetooth) */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
