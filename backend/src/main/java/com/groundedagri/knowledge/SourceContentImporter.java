package com.groundedagri.knowledge;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class SourceContentImporter {
    private static final int MAX_BYTES = 6 * 1024 * 1024;
    private static final int MAX_TEXT_CHARS = 180_000;
    private static final Set<String> ALLOWED_SUFFIXES = Set.of(
            "tnau.ac.in", "agmarknet.gov.in", "icar.gov.in", "gov.in", "edu.in",
            "fao.org", "icrisat.org", "cabi.org");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public SourceContentImporter(@Value("${app.source-ingestion.max-text-chars:180000}") int maxTextChars) {
        this.maxTextChars = maxTextChars;
    }

    private final int maxTextChars;

    public SourceImportPreview previewPdf(byte[] bytes, String originalFileName) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Choose a PDF file first.");
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("PDF files must be 6 MB or smaller.");
        if (!isPdf(bytes)) throw new IllegalArgumentException("The selected file is not a valid PDF.");

        String fileName = safeFileName(originalFileName);
        String content = extractPdf(bytes, fileName);
        validateExtractedText(content);
        String suggestedTitle = fileName.replaceFirst("(?i)\\.pdf$", "").replace('_', ' ').trim();
        return new SourceImportPreview(suggestedTitle, content, "UPLOAD", fileName, "");
    }

    public SourceImportPreview previewUrl(String sourceUrl) {
        URI uri = validateSourceUrl(sourceUrl);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(18))
                .header("User-Agent", "GroundedAgriSourceImporter/1.0")
                .header("Accept", "application/pdf,text/html,application/xhtml+xml;q=0.9")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream stream = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalArgumentException("The source URL returned HTTP " + response.statusCode() + ".");
                }
                long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                if (contentLength > MAX_BYTES) {
                    throw new IllegalArgumentException("Source pages and PDFs must be 6 MB or smaller.");
                }
                byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) {
                    throw new IllegalArgumentException("Source pages and PDFs must be 6 MB or smaller.");
                }

                String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                if (isPdf(bytes) || contentType.contains("application/pdf")) {
                    if (!isPdf(bytes)) throw new IllegalArgumentException("The source declared a PDF but did not contain PDF data.");
                    String fileName = fileNameFromUrl(uri);
                    String content = extractPdf(bytes, fileName);
                    validateExtractedText(content);
                    return new SourceImportPreview(fileName.replaceFirst("(?i)\\.pdf$", "").replace('_', ' '),
                            content, "LINK", fileName, uri.toString());
                }
                if (!contentType.contains("text/html") && !contentType.contains("application/xhtml+xml")
                        && !looksLikeHtml(bytes)) {
                    throw new IllegalArgumentException("Use an HTTPS webpage or PDF from an approved public source.");
                }
                org.jsoup.nodes.Document page = Jsoup.parse(new ByteArrayInputStream(bytes), null, uri.toString());
                page.select("script, style, nav, footer, header, noscript, svg, form").remove();
                Element main = page.selectFirst("main, article, [role=main]");
                String content = main == null ? page.body().text() : main.text();
                content = normalizeText(content);
                validateExtractedText(content);
                String title = normalizeText(page.title());
                if (title.isBlank()) title = uri.getHost();
                return new SourceImportPreview(title, content, "LINK", "", uri.toString());
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("The source import was interrupted. Try again.");
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read the approved source URL. Check it and try again.");
        }
    }

    private URI validateSourceUrl(String value) {
        final URI uri;
        try {
            uri = URI.create(value == null ? "" : value.trim()).normalize();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Enter a valid HTTPS source URL.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getPort() != -1 && uri.getPort() != 443) {
            throw new IllegalArgumentException("Use an HTTPS URL without login details, query parameters, or a custom port.");
        }
        String parsedHost;
        try {
            parsedHost = IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("The source hostname is not valid.");
        }
        String host = parsedHost.endsWith(".") ? parsedHost.substring(0, parsedHost.length() - 1) : parsedHost;
        if (host.matches("[0-9.]+") || host.contains(":")) {
            throw new IllegalArgumentException("Source URLs must use a public hostname, not an IP address.");
        }
        boolean allowed = ALLOWED_SUFFIXES.stream().anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix));
        if (!allowed) {
            throw new IllegalArgumentException("URL imports are limited to HTTPS agriculture, education, and government sources. Upload a PDF for other sources.");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(SourceContentImporter::isPrivateOrReserved)) {
                throw new IllegalArgumentException("That source hostname does not resolve to a public address.");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("The source hostname could not be resolved.");
        }
        return uri;
    }

    private static boolean isPrivateOrReserved(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int a = bytes[0] & 0xff;
            int b = bytes[1] & 0xff;
            int c = bytes[2] & 0xff;
            return a == 0 || a >= 224 || (a == 100 && b >= 64 && b <= 127)
                    || (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31)
                    || (a == 192 && b == 168) || (a == 192 && b == 0)
                    || (a == 192 && b == 0 && c == 2) || (a == 198 && (b == 18 || b == 19))
                    || (a == 198 && b == 51 && c == 100) || (a == 203 && b == 0 && c == 113);
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return (first & 0xfe) == 0xfc || (first == 0xfe && (second & 0xc0) == 0x80)
                    || (first == 0x20 && second == 0x01 && (bytes[2] & 0xff) == 0x0d
                    && (bytes[3] & 0xff) == 0xb8);
        }
        return true;
    }

    private String extractPdf(byte[] bytes, String fileName) {
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        try {
            List<Document> pages = new PagePdfDocumentReader(resource).read();
            return normalizeText(pages.stream().map(Document::getText).filter(text -> text != null && !text.isBlank())
                    .reduce((first, second) -> first + "\n\n" + second).orElse(""));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not extract text from this PDF. Try another file.");
        }
    }

    private void validateExtractedText(String content) {
        if (content.length() < 80) {
            throw new IllegalArgumentException("This source has too little extractable text. Scanned PDFs need OCR before import.");
        }
        if (content.length() > maxTextChars) {
            throw new IllegalArgumentException("This source is too long. Split it into sections before importing.");
        }
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replace('\u00a0', ' ').replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll("\\n{3,}", "\\n\\n").trim();
    }

    private String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) return "source.pdf";
        String name = originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank()) return "source.pdf";
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private String fileNameFromUrl(URI uri) {
        String path = uri.getPath();
        String name = path == null ? "source.pdf" : path.substring(path.lastIndexOf('/') + 1);
        return safeFileName(name.isBlank() ? "source.pdf" : name);
    }

    private boolean isPdf(byte[] bytes) {
        int scanLimit = Math.min(bytes.length, 1024);
        byte[] signature = "%PDF-".getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i <= scanLimit - signature.length; i++) {
            boolean matches = true;
            for (int j = 0; j < signature.length; j++) {
                if (bytes[i + j] != signature[j]) { matches = false; break; }
            }
            if (matches) return true;
        }
        return false;
    }

    private boolean looksLikeHtml(byte[] bytes) {
        int length = Math.min(bytes.length, 256);
        String prefix = new String(bytes, 0, length, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
        return prefix.startsWith("<!doctype html") || prefix.startsWith("<html") || prefix.startsWith("<head");
    }
}
