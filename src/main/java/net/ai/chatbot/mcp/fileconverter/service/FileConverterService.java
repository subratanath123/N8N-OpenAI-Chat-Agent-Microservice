package net.ai.chatbot.mcp.fileconverter.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.Attachment;
import net.ai.chatbot.dto.AttachmentStorageResult;
import net.ai.chatbot.service.AttachmentStorageService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service to convert text content to various file formats using free/open-source libraries.
 * Supports: txt, java, csv, docx, pdf, xlsx
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileConverterService {

    private static final Map<String, String> EXTENSION_TO_MIME = Map.of(
            "txt", "text/plain",
            "java", "text/plain",
            "csv", "text/csv",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "pdf", "application/pdf",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final AttachmentStorageService attachmentStorageService;

    public static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "java", "csv", "docx", "pdf", "xlsx");

    /**
     * Convert text content to the specified file format.
     *
     * @param content    The text content to convert
     * @param extension  Target file extension (txt, java, csv, docx, pdf, xlsx)
     * @param filename   Optional filename (without extension) for the output
     * @return Converted file as byte array
     */
    public byte[] convert(String content, String extension, String filename) {
        if (content == null) {
            content = "";
        }
        String ext = extension == null ? "txt" : extension.toLowerCase().trim();
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Unsupported extension: " + extension +
                    ". Supported: " + String.join(", ", SUPPORTED_EXTENSIONS));
        }

        return switch (ext) {
            case "txt" -> toTxt(content);
            case "java" -> toJava(content);
            case "csv" -> toCsv(content);
            case "docx" -> toDocx(content);
            case "pdf" -> toPdf(content);
            case "xlsx" -> toXlsx(content);
            default -> toTxt(content);
        };
    }

    public String getSuggestedFilename(String extension, String filename) {
        String ext = extension == null ? "txt" : extension.toLowerCase().trim();
        String base = (filename != null && !filename.isBlank()) ? filename.trim() : "converted";
        if (!base.toLowerCase().endsWith("." + ext)) {
            base = base + "." + ext;
        }
        return base;
    }

    /**
     * Convert content to file format, save via AttachmentStorageService, and return download link.
     *
     * @param content    Text content to convert
     * @param extension  Target extension (txt, java, csv, docx, pdf, xlsx)
     * @param filename   Optional output filename
     * @param chatbotId  Chatbot ID for storage (required for download path)
     * @param baseUrl    Base URL for the download link (e.g. https://api.example.com)
     * @return Map with fileId, downloadUrl, fileName, fileSize
     */
    public AttachmentStorageResult convertAndStore(String content, String extension, String filename,
                                                   String chatbotId, String baseUrl) throws IOException {
        byte[] fileBytes = convert(content, extension, filename);
        String suggestedFilename = getSuggestedFilename(extension, filename);
        String mimeType = EXTENSION_TO_MIME.getOrDefault(extension.toLowerCase().trim(), "application/octet-stream");

        Attachment attachment = Attachment.builder()
                .chatbotId(chatbotId)
                .name(suggestedFilename)
                .type(mimeType)
                .size(fileBytes.length)
                .length(fileBytes.length)
                .data(fileBytes)
                .uploadedAt(new java.util.Date())
                .build();

        AttachmentStorageResult result = attachmentStorageService.storeAttachmentInMongoDB(attachment, chatbotId);

        String downloadPath = "/api/attachments/download/" + result.getFileId() + "/" + chatbotId;
        String downloadUrl = (baseUrl != null && !baseUrl.isBlank())
                ? baseUrl.replaceAll("/$", "") + downloadPath
                : downloadPath;

        return AttachmentStorageResult.builder()
                .fileId(result.getFileId())
                .fileName(result.getFileName())
                .mimeType(result.getMimeType())
                .fileSize(result.getFileSize())
                .uploadedAt(result.getUploadedAt())
                .status(result.getStatus())
                .downloadUrl(downloadUrl)
                .build();
    }

    private byte[] toTxt(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] toJava(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] toCsv(String content) {
        try (StringWriter sw = new StringWriter();
             CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.withRecordSeparator("\n"))) {

            String[] lines = content.split("\\r?\\n");
            for (String line : lines) {
                // Split by comma for multi-column; single column if no comma
                String[] cells = line.split(",", -1);
                List<String> trimmed = Arrays.stream(cells).map(String::trim).collect(Collectors.toList());
                printer.printRecord(trimmed.isEmpty() ? List.of("") : trimmed);
            }
            printer.flush();
            return sw.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("CSV conversion failed: {}", e.getMessage());
            throw new RuntimeException("CSV conversion failed: " + e.getMessage(), e);
        }
    }

    private byte[] toDocx(String content) {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            String[] paragraphs = content.split("\\r?\\n");
            for (String paraText : paragraphs) {
                XWPFParagraph para = doc.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(paraText.isEmpty() ? " " : paraText);
            }
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("DOCX conversion failed: {}", e.getMessage());
            throw new RuntimeException("DOCX conversion failed: " + e.getMessage(), e);
        }
    }

    private byte[] toPdf(String content) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            float margin = 50;
            float pageHeight = 842; // A4
            float lineHeight = 15f;
            float y = pageHeight - margin;

            PDPage page = new PDPage();
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);

            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 12);
            cs.setLeading(lineHeight);
            cs.newLineAtOffset(margin, y);

            String[] lines = content.split("\\r?\\n");
            for (String line : lines) {
                if (y < margin) {
                    cs.endText();
                    cs.close();
                    page = new PDPage();
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 12);
                    cs.setLeading(lineHeight);
                    cs.newLineAtOffset(margin, pageHeight - margin);
                    y = pageHeight - margin;
                }
                String display = sanitizeForPdf(line);
                cs.showText(display);
                cs.newLine();
                y -= lineHeight;
            }
            cs.endText();
            cs.close();

            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("PDF conversion failed: {}", e.getMessage());
            throw new RuntimeException("PDF conversion failed: " + e.getMessage(), e);
        }
    }

    private String sanitizeForPdf(String s) {
        if (s == null) return "";
        return s.replace("\r", "").replace("\n", " ")
                .replace("\u0000", "")
                .replace("\t", "    ");
    }

    private byte[] toXlsx(String content) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet("Sheet1");
            String[] lines = content.split("\\r?\\n");

            for (int i = 0; i < lines.length; i++) {
                XSSFRow row = sheet.createRow(i);
                XSSFCell cell = row.createCell(0);
                cell.setCellValue(lines[i]);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("XLSX conversion failed: {}", e.getMessage());
            throw new RuntimeException("XLSX conversion failed: " + e.getMessage(), e);
        }
    }
}
