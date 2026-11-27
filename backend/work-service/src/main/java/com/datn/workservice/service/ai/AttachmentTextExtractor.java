package com.datn.workservice.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.extractor.XSSFExcelExtractor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Trích text từ tệp đính kèm cho AI đọc: PDF (≤20 trang), DOCX, XLSX, text thuần. Ảnh chỉ trả ghi chú. */
@Slf4j
@Component
public class AttachmentTextExtractor {

    public static final int MAX_CHARS = 20_000;
    private static final int MAX_PDF_PAGES = 20;
    private static final Set<String> TEXT_EXT = Set.of("txt", "csv", "md", "json", "xml", "yaml", "yml", "log", "sql", "html", "htm");

    public record Extracted(String text, boolean truncated, String note) {}

    public Extracted extract(InputStream in, String contentType, String fileName) throws Exception {
        String ext = ext(fileName);
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        byte[] bytes = in.readAllBytes();

        if (ct.startsWith("image/")) {
            return new Extracted(null, false, "Tệp ảnh – trợ lý chưa đọc được nội dung ảnh, chỉ có thông tin tệp.");
        }
        String text;
        if ("pdf".equals(ext) || ct.contains("pdf")) {
            try (PDDocument doc = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setEndPage(Math.min(MAX_PDF_PAGES, doc.getNumberOfPages()));
                text = stripper.getText(doc);
            }
        } else if ("docx".equals(ext) || ct.contains("wordprocessingml")) {
            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
                 XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
                text = ex.getText();
            }
        } else if ("xlsx".equals(ext) || ct.contains("spreadsheetml")) {
            try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes));
                 XSSFExcelExtractor ex = new XSSFExcelExtractor(wb)) {
                ex.setIncludeSheetNames(true);
                text = ex.getText();
            }
        } else if (TEXT_EXT.contains(ext) || ct.startsWith("text/") || ct.contains("json") || ct.contains("xml")) {
            text = new String(bytes, StandardCharsets.UTF_8);
        } else {
            return new Extracted(null, false, "Định dạng chưa hỗ trợ đọc nội dung (" + (ct.isEmpty() ? ext : ct) + ").");
        }

        text = text == null ? "" : text.strip();
        boolean truncated = text.length() > MAX_CHARS;
        return new Extracted(truncated ? text.substring(0, MAX_CHARS) : text, truncated,
                truncated ? "Nội dung dài, chỉ hiển thị " + MAX_CHARS + " ký tự đầu." : null);
    }

    private static String ext(String fileName) {
        if (fileName == null) return "";
        int i = fileName.lastIndexOf('.');
        return i < 0 ? "" : fileName.substring(i + 1).toLowerCase(Locale.ROOT);
    }
}
