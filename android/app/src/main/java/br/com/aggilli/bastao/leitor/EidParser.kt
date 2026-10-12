package br.com.aggilli.bastao.leitor

/**
 * Acha o número do brinco (ISO 11784: país com 3 dígitos + número nacional com 12) no texto que
 * os bastões comerciais mandam pelo Bluetooth serial (SPP) ou digitam no modo teclado (HID).
 *
 * Os fabricantes (Allflex, Datamars/Tru-Test, Gallagher, Agrident) não publicam o formato exato,
 * e ele muda com o modelo e a configuração. Aceitamos as variações comuns:
 *   982000123456789 · 982 000123456789 · 982-000123456789 · 982.000123456789
 * com prefixo de tipo ("FDX-B", "HDX", "F", "H", "LA") ou data e hora antes ou depois.
 */
object EidParser {

    private val EID = Regex("""(?<!\d)(\d{3})[ .\-_]?(\d{12})(?!\d)""")

    /** Maior número nacional possível: 38 bits (ISO 11784). */
    private const val NACIONAL_MAX = 274_877_906_943L

    /** O primeiro número de brinco válido do texto, com 15 dígitos e sem separador; ou null. */
    fun extrair(texto: String): String? {
        for (m in EID.findAll(texto)) {
            val pais = m.groupValues[1].toInt()
            val nacional = m.groupValues[2].toLong()
            if (pais == 0 || nacional > NACIONAL_MAX) continue
            return m.groupValues[1] + m.groupValues[2]
        }
        return null
    }
}
