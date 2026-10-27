package de.sulo.manitr

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Schreibt den SK-Verlauf als echte .xlsx-Datei (ohne zusätzliche Bibliothek). */
object SkExport {
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

    fun kindLabel(kind: String) = when (kind) { "SA" -> "Samstag"; "ME" -> "Monatsende"; else -> "Samstag + Monatsende" }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun num(d: Double) = java.math.BigDecimal(d).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
    private fun str(ref: String, s: Int, text: String) = "<c r=\"$ref\" s=\"$s\" t=\"inlineStr\"><is><t>${esc(text)}</t></is></c>"

    fun xlsx(rows: List<SkSnapshot>): ByteArray {
        val sheet = StringBuilder(HEAD)
        sheet.append("<worksheet xmlns=\"$NS\"><cols><col min=\"1\" max=\"1\" width=\"14\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"24\" customWidth=\"1\"/>")
            .append("<col min=\"3\" max=\"4\" width=\"20\" customWidth=\"1\"/><col min=\"5\" max=\"5\" width=\"16\" customWidth=\"1\"/></cols><sheetData>")
        sheet.append("<row r=\"1\">")
            .append(str("A1", 1, "Datum")).append(str("B1", 1, "Art")).append(str("C1", 1, "Gesamtwert USD")).append(str("D1", 1, "Gesamtwert EUR")).append(str("E1", 1, "Hinweis"))
            .append("</row>")
        rows.sortedBy { it.epochDay }.forEachIndexed { i, r ->
            val n = i + 2
            sheet.append("<row r=\"$n\"><c r=\"A$n\" s=\"3\"><v>${r.epochDay + 25569}</v></c>")
                .append(str("B$n", 0, kindLabel(r.kind)))
                .append("<c r=\"C$n\" s=\"2\"><v>${num(r.usd)}</v></c><c r=\"D$n\" s=\"2\"><v>${num(r.eur)}</v></c>")
                .append(str("E$n", 0, if (r.late) "nachgetragen" else ""))
                .append("</row>")
        }
        sheet.append("</sheetData></worksheet>")

        val parts = linkedMapOf(
            "[Content_Types].xml" to HEAD + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>",
            "_rels/.rels" to HEAD + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>",
            "xl/workbook.xml" to HEAD + "<workbook xmlns=\"$NS\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets><sheet name=\"SK Verlauf\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>",
            "xl/_rels/workbook.xml.rels" to HEAD + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>",
            "xl/styles.xml" to HEAD + "<styleSheet xmlns=\"$NS\">" +
                "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
                "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
                "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                "<cellXfs count=\"4\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
                "<xf numFmtId=\"4\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
                "<xf numFmtId=\"14\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs>" +
                "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>",
            "xl/worksheets/sheet1.xml" to sheet.toString(),
        )
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, content) in parts) {
                z.putNextEntry(ZipEntry(name))
                z.write(content.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
