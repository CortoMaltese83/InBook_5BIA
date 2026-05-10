package com.inbook.service;

import com.inbook.repository.AppUserRepository;
import com.inbook.repository.InstitutionRepository;
import com.inbook.repository.SubjectRepository;
import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.Book;
import com.inbook.repository.entity.Institution;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.repository.entity.Subject;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class InstitutionBookExportService {

    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final InstitutionRepository institutionRepository;
    private final SubjectRepository subjectRepository;
    private final AppUserRepository userRepository;

    public InstitutionBookExportService(InstitutionRepository institutionRepository,
                                        SubjectRepository subjectRepository,
                                        AppUserRepository userRepository) {
        this.institutionRepository = institutionRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ActiveBookExport exportActiveBooks(Long institutionId, Principal principal) {
        requireAdmin(principal);
        Institution institution = requireInstitution(institutionId);
        List<Subject> subjects = subjectRepository.findActiveBookAssignmentsByInstitution(institution.getId());
        String filename = "libri-attivi-" + filenamePart(institution) + ".xlsx";
        return new ActiveBookExport(filename, XLSX_CONTENT_TYPE, buildWorkbook(institution, subjects));
    }

    public record ActiveBookExport(String filename, String contentType, byte[] content) {
    }

    private AppUser requireAdmin(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalArgumentException("Utente non autenticato");
        }
        AppUser user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Utente non trovato: " + principal.getName()));
        if (user.getRoles() == null || !user.getRoles().toUpperCase(Locale.ITALIAN).contains("ADMIN")) {
            throw new AccessDeniedException("Forbidden");
        }
        return user;
    }

    private Institution requireInstitution(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Istituto mancante");
        }
        return institutionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Istituto non trovato"));
    }

    private byte[] buildWorkbook(Institution institution, List<Subject> subjects) {
        List<List<Cell>> rows = new ArrayList<>();
        rows.add(List.of(
                Cell.text("Istituto"),
                Cell.text("Codice istituto"),
                Cell.text("ID classe"),
                Cell.text("Classe"),
                Cell.text("Anno"),
                Cell.text("Sezione"),
                Cell.text("Stato classe"),
                Cell.text("Materia"),
                Cell.text("Docente"),
                Cell.text("Email docente"),
                Cell.text("ISBN"),
                Cell.text("Titolo"),
                Cell.text("Autore"),
                Cell.text("Volume"),
                Cell.text("Casa editrice"),
                Cell.text("Prezzo"),
                Cell.text("Da acquistare"),
                Cell.text("Consigliato")
        ));

        for (Subject subject : subjects) {
            SchoolClass schoolClass = subject.getClasse();
            AppUser teacher = subject.getDocente();
            Book book = subject.getBook();
            rows.add(List.of(
                    Cell.text(institution.getName()),
                    Cell.text(institution.getCode()),
                    Cell.number(schoolClass != null && schoolClass.getId() != null ? schoolClass.getId().toString() : ""),
                    Cell.text(schoolClass != null ? schoolClass.getNome() : ""),
                    Cell.text(schoolClass != null ? schoolClass.getAnno() : ""),
                    Cell.text(schoolClass != null ? schoolClass.getSezione() : ""),
                    Cell.text(schoolClass != null ? schoolClass.getStato() : ""),
                    Cell.text(subject.getNomeMateria()),
                    Cell.text(formatTeacher(teacher)),
                    Cell.text(teacher != null ? teacher.getEmail() : ""),
                    Cell.text(book != null ? book.getIsbn() : ""),
                    Cell.text(book != null ? book.getTitolo() : ""),
                    Cell.text(book != null ? book.getAutore() : ""),
                    Cell.number(book != null ? String.valueOf(book.getVolume()) : ""),
                    Cell.text(book != null ? book.getCasaEditrice() : ""),
                    Cell.number(book != null ? Double.toString(book.getPrezzo()) : ""),
                    Cell.text(book != null && book.isDaAcquistare() ? "Si" : "No"),
                    Cell.text(book != null && book.isConsigliato() ? "Si" : "No")
            ));
        }

        return XlsxWriter.write("Libri attivi", rows);
    }

    private String formatTeacher(AppUser teacher) {
        if (teacher == null) {
            return "";
        }
        String fullName = ((teacher.getName() != null ? teacher.getName() : "") + " "
                + (teacher.getSurname() != null ? teacher.getSurname() : "")).trim();
        return !fullName.isBlank() ? fullName : teacher.getUsername();
    }

    private String filenamePart(Institution institution) {
        String source = institution.getCode() != null && !institution.getCode().isBlank()
                ? institution.getCode()
                : institution.getName();
        String clean = source == null ? "istituto" : source.toLowerCase(Locale.ITALIAN)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return clean.isBlank() ? "istituto" : clean;
    }

    private record Cell(String value, boolean numeric) {
        static Cell text(String value) {
            return new Cell(value == null ? "" : value, false);
        }

        static Cell number(String value) {
            return new Cell(value == null ? "" : value, true);
        }
    }

    private static final class XlsxWriter {
        private XlsxWriter() {
        }

        static byte[] write(String sheetName, List<List<Cell>> rows) {
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                    put(zip, "[Content_Types].xml", contentTypes());
                    put(zip, "_rels/.rels", packageRelationships());
                    put(zip, "xl/workbook.xml", workbook(sheetName));
                    put(zip, "xl/_rels/workbook.xml.rels", workbookRelationships());
                    put(zip, "xl/styles.xml", styles());
                    put(zip, "xl/worksheets/sheet1.xml", worksheet(rows));
                }
                return output.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException("Errore nella generazione del file Excel", e);
            }
        }

        private static void put(ZipOutputStream zip, String path, String content) throws IOException {
            zip.putNextEntry(new ZipEntry(path));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        private static String contentTypes() {
            return """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                      <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                      <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                    </Types>
                    """;
        }

        private static String packageRelationships() {
            return """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """;
        }

        private static String workbook(String sheetName) {
            return """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets>
                        <sheet name="%s" sheetId="1" r:id="rId1"/>
                      </sheets>
                    </workbook>
                    """.formatted(xml(sheetName));
        }

        private static String workbookRelationships() {
            return """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                      <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                    </Relationships>
                    """;
        }

        private static String styles() {
            return """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                      <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
                      <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
                      <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                      <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                      <cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>
                      <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
                    </styleSheet>
                    """;
        }

        private static String worksheet(List<List<Cell>> rows) {
            int maxColumns = rows.stream().mapToInt(List::size).max().orElse(1);
            String lastCell = cellRef(maxColumns, Math.max(rows.size(), 1));
            StringBuilder xml = new StringBuilder();
            xml.append("""
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    """);
            xml.append("<dimension ref=\"A1:").append(lastCell).append("\"/>");
            xml.append("<sheetViews><sheetView workbookViewId=\"0\"/></sheetViews>");
            xml.append("<cols>");
            for (int col = 1; col <= maxColumns; col++) {
                xml.append("<col min=\"").append(col).append("\" max=\"").append(col).append("\" width=\"18\" customWidth=\"1\"/>");
            }
            xml.append("</cols><sheetData>");

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                int excelRow = rowIndex + 1;
                xml.append("<row r=\"").append(excelRow).append("\">");
                List<Cell> row = rows.get(rowIndex);
                for (int colIndex = 0; colIndex < row.size(); colIndex++) {
                    Cell cell = row.get(colIndex);
                    String ref = cellRef(colIndex + 1, excelRow);
                    if (cell.numeric() && !cell.value().isBlank()) {
                        xml.append("<c r=\"").append(ref).append("\"><v>")
                                .append(xml(cell.value()))
                                .append("</v></c>");
                    } else {
                        xml.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"");
                        if (rowIndex == 0) {
                            xml.append(" s=\"1\"");
                        }
                        xml.append("><is><t>").append(xml(cell.value())).append("</t></is></c>");
                    }
                }
                xml.append("</row>");
            }

            xml.append("</sheetData>");
            if (!rows.isEmpty()) {
                xml.append("<autoFilter ref=\"A1:").append(lastCell).append("\"/>");
            }
            xml.append("</worksheet>");
            return xml.toString();
        }

        private static String cellRef(int column, int row) {
            StringBuilder col = new StringBuilder();
            int current = column;
            while (current > 0) {
                current--;
                col.insert(0, (char) ('A' + (current % 26)));
                current /= 26;
            }
            return col + String.valueOf(row);
        }

        private static String xml(String value) {
            if (value == null || value.isEmpty()) {
                return "";
            }
            StringBuilder escaped = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '&') {
                    escaped.append("&amp;");
                } else if (c == '<') {
                    escaped.append("&lt;");
                } else if (c == '>') {
                    escaped.append("&gt;");
                } else if (c == '"') {
                    escaped.append("&quot;");
                } else if (c == '\'' ) {
                    escaped.append("&apos;");
                } else if (c == '\t' || c == '\n' || c == '\r' || c >= 0x20) {
                    escaped.append(c);
                }
            }
            return escaped.toString();
        }
    }
}
