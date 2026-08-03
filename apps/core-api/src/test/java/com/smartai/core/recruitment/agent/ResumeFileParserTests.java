package com.smartai.core.recruitment.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class ResumeFileParserTests {

	private final ResumeFileParser parser = new ResumeFileParser();

	@Test
	void extractsPdfWithApacheTika() throws Exception {
		byte[] pdf;
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
				content.newLineAtOffset(72, 720);
				content.showText("Name: Alice Chen");
				content.newLineAtOffset(0, -18);
				content.showText("Location: Singapore");
				content.newLineAtOffset(0, -18);
				content.showText("5 years of experience");
				content.endText();
			}
			document.save(output);
			pdf = output.toByteArray();
		}

		var outcome = parser.parse(pdf, "alice.pdf", "application/pdf");

		assertThat(outcome.parseStatus()).isEqualTo("PARSED");
		assertThat(outcome.detectedMimeType()).isEqualTo("application/pdf");
		assertThat(outcome.parsedProfile().name()).isEqualTo("Alice Chen");
		assertThat(outcome.parsedProfile().location()).isEqualTo("Singapore");
		assertThat(outcome.parsedProfile().experienceYears()).isEqualByComparingTo("5");
	}

	@Test
	void extractsDocxWithApacheTika() throws Exception {
		byte[] docx = docx(
			"Name: Bob Li",
			"Education",
			"Bachelor of Engineering",
			"Skills",
			"Java, SQL");

		var outcome = parser.parse(
			docx,
			"bob.docx",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document");

		assertThat(outcome.parseStatus()).isEqualTo("PARSED");
		assertThat(outcome.detectedMimeType())
			.isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
		assertThat(outcome.parsedProfile().name()).isEqualTo("Bob Li");
		assertThat(outcome.parsedProfile().educationLevel()).isEqualTo("本科");
		assertThat(outcome.parsedProfile().skills()).containsExactly("Java", "SQL");
	}

	@Test
	void reportsUnsupportedAndOversizedExtractionOutcomes() {
		byte[] png = new byte[] {
			(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00
		};
		var unsupported = parser.parse(png, "photo.png", "image/png");
		assertThat(unsupported.parseStatus()).isEqualTo("PARSE_FAILED");
		assertThat(unsupported.failureCode()).isEqualTo("RESUME_UNSUPPORTED_TYPE");
		assertThat(unsupported.retryable()).isFalse();

		byte[] largeText = ("Name: Large Resume\n" + "x".repeat(510_000)).getBytes(StandardCharsets.UTF_8);
		var limited = parser.parse(largeText, "large.txt", "text/plain");
		assertThat(limited.parseStatus()).isEqualTo("PARSE_FAILED");
		assertThat(limited.failureCode()).isEqualTo("RESUME_OUTPUT_LIMIT");
		assertThat(limited.retryable()).isFalse();
	}

	private static byte[] docx(String... paragraphs) throws Exception {
		StringBuilder body = new StringBuilder();
		for (String paragraph : paragraphs) {
			body.append("<w:p><w:r><w:t xml:space=\"preserve\">")
				.append(paragraph)
				.append("</w:t></w:r></w:p>");
		}
		String documentXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
			+ "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
			+ "<w:body>" + body + "</w:body></w:document>";
		String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
			+ "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
			+ "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
			+ "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
			+ "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
			+ "</Types>";
		String relationships = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
			+ "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
			+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
			+ "</Relationships>";
		try (ByteArrayOutputStream output = new ByteArrayOutputStream();
				ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
			write(zip, "[Content_Types].xml", contentTypes);
			write(zip, "_rels/.rels", relationships);
			write(zip, "word/document.xml", documentXml);
			zip.finish();
			return output.toByteArray();
		}
	}

	private static void write(ZipOutputStream zip, String path, String content) throws Exception {
		zip.putNextEntry(new ZipEntry(path));
		zip.write(content.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}
}
