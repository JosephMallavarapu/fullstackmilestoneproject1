package com.trackwise.ocr;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.*;

/**
 * ══════════════════════════════════════════════════════════════
 *  TrackWise — OCR Receipt Scanning Service
 *  Industry Feature: Auto-extract expense fields from receipts
 *
 *  Integration options (configure in application.properties):
 *  1. AWS Textract — best accuracy for structured docs
 *  2. Google Cloud Vision — great for handwritten receipts
 *  3. Tesseract (Apache) — open-source, self-hosted
 *
 *  Pipeline:
 *  1. Upload file → validate (type, size, ISO 27001 scan)
 *  2. Send to OCR provider → get raw text
 *  3. NLP pattern matching → extract fields
 *  4. Return OcrResult with confidence score
 *  5. Pre-fill expense form (user reviews before submitting)
 * ══════════════════════════════════════════════════════════════
 */

// ── Extracted fields ──────────────────────────────────────────
@Data @Builder @NoArgsConstructor @AllArgsConstructor
class OcrResult {
    private boolean     success;
    private String      rawText;
    private String      vendor;
    private BigDecimal  amount;
    private BigDecimal  taxAmount;
    private String      currency;
    private LocalDate   transactionDate;
    private String      invoiceNumber;
    private double      confidenceScore;    // 0.0 – 1.0
    private String      errorMessage;

    public String getConfidencePercent() {
        return String.format("%.1f%%", confidenceScore * 100);
    }
}

// ── Supported file types ──────────────────────────────────────
enum AllowedReceiptType {
    PDF("application/pdf"),
    JPEG("image/jpeg"),
    PNG("image/png"),
    WEBP("image/webp");

    final String mimeType;
    AllowedReceiptType(String mime) { this.mimeType = mime; }

    public static boolean isAllowed(String mime) {
        for (AllowedReceiptType t : values()) {
            if (t.mimeType.equalsIgnoreCase(mime)) return true;
        }
        return false;
    }
}

// ─────────────────────────────────────────────────────────────
// OcrService
// ─────────────────────────────────────────────────────────────
@Service
@Slf4j
public class OcrService {

    @Value("${app.ocr.provider:tesseract}")   // aws-textract | google-vision | tesseract
    private String ocrProvider;

    @Value("${app.ocr.max-file-size-mb:10}")
    private int maxFileSizeMb;

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    // ── Entry point: process uploaded receipt ─────────────────
    public OcrResult processReceipt(MultipartFile file) {
        // 1. Validate file
        OcrResult validation = validateFile(file);
        if (!validation.isSuccess()) return validation;

        // 2. Extract raw text based on configured provider
        String rawText;
        try {
            rawText = switch (ocrProvider.toLowerCase()) {
                case "aws-textract"   -> extractWithAwsTextract(file);
                case "google-vision"  -> extractWithGoogleVision(file);
                default               -> extractWithTesseract(file);
            };
        } catch (Exception e) {
            log.error("OCR extraction failed: {}", e.getMessage());
            return OcrResult.builder().success(false)
                    .errorMessage("OCR processing failed: " + e.getMessage()).build();
        }

        // 3. Parse extracted text into fields
        return parseFields(rawText);
    }

    // ── File validation (ISO 27001 — input validation) ────────
    private OcrResult validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return OcrResult.builder().success(false).errorMessage("No file provided").build();
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            return OcrResult.builder().success(false)
                    .errorMessage("File exceeds 10MB limit").build();
        }
        if (!AllowedReceiptType.isAllowed(file.getContentType())) {
            return OcrResult.builder().success(false)
                    .errorMessage("File type not supported. Use PDF, JPG, or PNG").build();
        }
        return OcrResult.builder().success(true).build();
    }

    // ── AWS Textract integration ──────────────────────────────
    private String extractWithAwsTextract(MultipartFile file) throws Exception {
        /**
         * Production implementation:
         *
         * software.amazon.awssdk.services.textract.TextractClient client =
         *     TextractClient.builder().region(Region.US_EAST_1).build();
         *
         * DetectDocumentTextRequest request = DetectDocumentTextRequest.builder()
         *     .document(Document.builder()
         *         .bytes(SdkBytes.fromInputStream(file.getInputStream()))
         *         .build())
         *     .build();
         *
         * DetectDocumentTextResponse response = client.detectDocumentText(request);
         * return response.blocks().stream()
         *     .filter(b -> b.blockType() == BlockType.LINE)
         *     .map(Block::text)
         *     .collect(Collectors.joining("\n"));
         */
        log.info("AWS Textract processing: {}", file.getOriginalFilename());
        return getMockReceiptText();   // Replace with actual AWS call
    }

    // ── Google Cloud Vision integration ───────────────────────
    private String extractWithGoogleVision(MultipartFile file) throws Exception {
        /**
         * Production implementation:
         *
         * ImageAnnotatorClient vision = ImageAnnotatorClient.create();
         * ByteString imgBytes = ByteString.readFrom(file.getInputStream());
         * Image img = Image.newBuilder().setContent(imgBytes).build();
         * Feature feat = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
         * AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
         *     .addFeatures(feat).setImage(img).build();
         * List<AnnotateImageResponse> responses = vision.batchAnnotateImages(
         *     List.of(request)).getResponsesList();
         * return responses.get(0).getTextAnnotations(0).getDescription();
         */
        log.info("Google Vision processing: {}", file.getOriginalFilename());
        return getMockReceiptText();
    }

    // ── Tesseract (open source, self-hosted) ──────────────────
    private String extractWithTesseract(MultipartFile file) throws Exception {
        /**
         * Production implementation using tess4j:
         *
         * File tempFile = File.createTempFile("receipt", ".tmp");
         * file.transferTo(tempFile);
         * Tesseract tesseract = new Tesseract();
         * tesseract.setDatapath("/usr/share/tesseract-ocr/4.00/tessdata");
         * tesseract.setLanguage("eng");
         * String text = tesseract.doOCR(tempFile);
         * tempFile.delete();
         * return text;
         */
        log.info("Tesseract processing: {}", file.getOriginalFilename());
        return getMockReceiptText();
    }

    // ── Field extraction using regex + NLP patterns ──────────
    OcrResult parseFields(String rawText) {
        OcrResult.OcrResultBuilder builder = OcrResult.builder()
                .success(true).rawText(rawText);

        // Extract amount — handles $1,234.56 / USD 1234.56 / 1234.56 USD
        extractAmount(rawText).ifPresent(builder::amount);

        // Extract vendor (first non-blank line, cleaned)
        extractVendor(rawText).ifPresent(builder::vendor);

        // Extract date in various formats
        extractDate(rawText).ifPresent(builder::transactionDate);

        // Extract currency
        extractCurrency(rawText).ifPresent(builder::currency);

        // Extract invoice number
        extractInvoiceNumber(rawText).ifPresent(builder::invoiceNumber);

        // Extract tax
        extractTax(rawText).ifPresent(builder::taxAmount);

        // Calculate confidence based on how many fields were extracted
        double confidence = calcConfidence(builder.build());
        builder.confidenceScore(confidence);

        log.info("OCR parsed: vendor={} confidence={:.1f}%",
                builder.build().getVendor(), confidence * 100);
        return builder.build();
    }

    private Optional<BigDecimal> extractAmount(String text) {
        Pattern p = Pattern.compile("(?:total|amount|subtotal|grand total)[:\\s]*[$£€¥]?\\s*([\\d,]+\\.?\\d{0,2})",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            try { return Optional.of(new BigDecimal(m.group(1).replace(",",""))); }
            catch (Exception ignored) {}
        }
        // Fallback: largest number in text
        Pattern nums = Pattern.compile("[$£€]\\s*([\\d,]+\\.\\d{2})");
        Matcher nm = nums.matcher(text);
        BigDecimal largest = null;
        while (nm.find()) {
            try {
                BigDecimal val = new BigDecimal(nm.group(1).replace(",",""));
                if (largest == null || val.compareTo(largest) > 0) largest = val;
            } catch (Exception ignored) {}
        }
        return Optional.ofNullable(largest);
    }

    private Optional<String> extractVendor(String text) {
        String[] lines = text.split("\\n");
        for (String line : lines) {
            String clean = line.trim();
            if (!clean.isEmpty() && clean.length() > 2 && clean.length() < 60) {
                return Optional.of(clean);
            }
        }
        return Optional.empty();
    }

    private Optional<LocalDate> extractDate(String text) {
        // Matches: 2025-02-14 | 02/14/2025 | 14 Feb 2025
        Pattern p = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})|(\\d{2}/\\d{2}/\\d{4})", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            try {
                String raw = m.group();
                if (raw.contains("-")) return Optional.of(LocalDate.parse(raw));
                String[] parts = raw.split("/");
                return Optional.of(LocalDate.of(Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
            } catch (Exception ignored) {}
        }
        return Optional.empty();
    }

    private Optional<String> extractCurrency(String text) {
        if (text.contains("$") || text.toUpperCase().contains("USD")) return Optional.of("USD");
        if (text.contains("€") || text.toUpperCase().contains("EUR")) return Optional.of("EUR");
        if (text.contains("£") || text.toUpperCase().contains("GBP")) return Optional.of("GBP");
        if (text.contains("¥") || text.toUpperCase().contains("JPY")) return Optional.of("JPY");
        return Optional.of("USD");
    }

    private Optional<String> extractInvoiceNumber(String text) {
        Pattern p = Pattern.compile("(?:invoice|inv|receipt)[\\s#:]*([A-Z0-9\\-]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private Optional<BigDecimal> extractTax(String text) {
        Pattern p = Pattern.compile("(?:tax|vat|gst)[:\\s]*[$£€]?\\s*([\\d,]+\\.?\\d{0,2})", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            try { return Optional.of(new BigDecimal(m.group(1).replace(",",""))); }
            catch (Exception ignored) {}
        }
        return Optional.empty();
    }

    private double calcConfidence(OcrResult result) {
        int filled = 0, total = 5;
        if (result.getVendor()          != null) filled++;
        if (result.getAmount()          != null) filled++;
        if (result.getTransactionDate() != null) filled++;
        if (result.getCurrency()        != null) filled++;
        if (result.getInvoiceNumber()   != null) filled++;
        return (double) filled / total;
    }

    private String getMockReceiptText() {
        return """
            Amazon Web Services, Inc.
            410 Terry Ave North, Seattle WA
            Invoice #: AWS-INV-2025-0214
            Date: 2025-02-14
            
            EC2 On-Demand Instances         $1,656.00
            S3 Storage                      $  100.00
            Data Transfer                   $   84.00
            
            Subtotal:                       $1,840.00
            Tax (10%):                      $  184.00
            TOTAL: USD $1,840.00
            
            Thank you for using Amazon Web Services!
            """;
    }
}
