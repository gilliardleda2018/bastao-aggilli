package br.com.aggilli.bastao.leitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Formatos que os bastões comerciais costumam mandar, e o que não pode virar brinco. */
class EidParserTest {

    private val brinco = "982000123456789"

    @Test
    fun numeroPuro() = assertEquals(brinco, EidParser.extrair("982000123456789"))

    @Test
    fun separadores() {
        assertEquals(brinco, EidParser.extrair("982 000123456789"))
        assertEquals(brinco, EidParser.extrair("982-000123456789"))
        assertEquals(brinco, EidParser.extrair("982.000123456789"))
    }

    @Test
    fun prefixosDeTipo() {
        assertEquals(brinco, EidParser.extrair("FDX-B 982 000123456789"))
        assertEquals(brinco, EidParser.extrair("HDX982000123456789"))
        assertEquals(brinco, EidParser.extrair("LA 982000123456789"))
        assertEquals("076000000001234", EidParser.extrair("F 076 000000001234"))
    }

    @Test
    fun comDataEHora() {
        assertEquals(brinco, EidParser.extrair("982000123456789 12/10/2026 14:30:15"))
        assertEquals(brinco, EidParser.extrair("2026-10-12 14:30:15;982 000123456789"))
        assertEquals(brinco, EidParser.extrair("20261012143015,982000123456789"))
    }

    @Test
    fun naoEhBrinco() {
        assertNull(EidParser.extrair(""))
        assertNull(EidParser.extrair("12345"))
        assertNull(EidParser.extrair("98200012345678"))         // 14 dígitos
        assertNull(EidParser.extrair("9820001234567890"))       // 16 dígitos
        assertNull(EidParser.extrair("1760287815123"))          // horário em milissegundos
        assertNull(EidParser.extrair("000000123456789"))        // país 000
        assertNull(EidParser.extrair("982999999999999"))        // nacional acima de 38 bits
        assertNull(EidParser.extrair("BAT 87% OK"))
    }
}
