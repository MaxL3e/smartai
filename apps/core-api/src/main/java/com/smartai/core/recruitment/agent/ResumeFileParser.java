package com.smartai.core.recruitment.agent;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import com.smartai.core.recruitment.agent.ResumeFileModels.ParsedResumeProfile;
import com.smartai.core.recruitment.agent.ResumeFileModels.ParserOutcome;
import com.smartai.core.recruitment.agent.ResumeFileModels.ResumeEvidence;

@Component
final class ResumeFileParser {

	static final String PARSER_VERSION = "apache-tika-3.2.1/rule-extractor-v1";
	private static final int MAX_EXTRACTED_CHARACTERS = 500_000;
	private static final Pattern EMAIL = Pattern.compile(
		"(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}(?![A-Z0-9._%+-])");
	private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)");
	private static final Pattern EXPLICIT_NAME = Pattern.compile(
		"(?im)^\\s*(?:姓名|name)\\s*[:：]\\s*([^\\r\\n]{1,80})\\s*$");
	private static final Pattern LOCATION = Pattern.compile(
		"(?im)^\\s*(?:所在地|现居地|居住地|location)\\s*[:：]\\s*([^\\r\\n]{1,100})\\s*$");
	private static final Pattern EXPERIENCE_ZH = Pattern.compile(
		"(?i)(\\d{1,2}(?:\\.\\d)?)\\s*年(?:以上)?(?:的)?工作经验");
	private static final Pattern EXPERIENCE_EN = Pattern.compile(
		"(?i)(\\d{1,2}(?:\\.\\d)?)\\s*\\+?\\s*years?\\s+of\\s+(?:professional\\s+)?experience");
	private static final Pattern SKILLS_SECTION = Pattern.compile(
		"(?ims)^\\s*(?:专业技能|核心技能|技能|技术栈|skills?|technical skills?)\\s*[:：]?\\s*$\\R"
			+ "(.*?)(?=^\\s*(?:工作经历|工作经验|项目经历|教育经历|教育背景|个人总结|证书|experience|education|projects?|summary|certifications?)\\s*[:：]?\\s*$|\\z)");
	private static final Pattern HEADING = Pattern.compile(
		"(?i)^(?:个人简历|简历|resume|curriculum vitae|基本信息|联系方式|contact|工作经历|工作经验|experience|"
			+ "教育经历|教育背景|education|专业技能|核心技能|技能|skills?|项目经历|projects?|个人总结|summary)$");

	private static final List<DegreeTerm> DEGREE_TERMS = List.of(
		new DegreeTerm("博士", "博士"),
		new DegreeTerm("PhD", "博士"),
		new DegreeTerm("Doctorate", "博士"),
		new DegreeTerm("硕士", "硕士"),
		new DegreeTerm("Master", "硕士"),
		new DegreeTerm("本科", "本科"),
		new DegreeTerm("Bachelor", "本科"),
		new DegreeTerm("学士", "本科"),
		new DegreeTerm("大专", "大专"),
		new DegreeTerm("专科", "大专"),
		new DegreeTerm("Associate", "大专"));

	private final Tika tika = new Tika();

	ParserOutcome parse(byte[] bytes, String fileName, String declaredMimeType) {
		String detectedMimeType;
		try {
			detectedMimeType = baseMime(tika.detect(bytes, fileName));
		}
		catch (Exception exception) {
			return failure(null, "RESUME_PARSE_FAILED", true);
		}
		if (!isSupported(detectedMimeType)) {
			return failure(detectedMimeType, "RESUME_UNSUPPORTED_TYPE", false);
		}

		Metadata metadata = new Metadata();
		metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
		if (declaredMimeType != null && !declaredMimeType.isBlank()) {
			metadata.set(Metadata.CONTENT_TYPE, declaredMimeType);
		}
		BodyContentHandler handler = new BodyContentHandler(MAX_EXTRACTED_CHARACTERS);
		try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
			new AutoDetectParser().parse(input, handler, metadata, new ParseContext());
			String extracted = normalizeText(handler.toString());
			if (extracted.isBlank()) {
				return failure(detectedMimeType, "RESUME_EMPTY_CONTENT", false);
			}
			String parserDetected = metadata.get(Metadata.CONTENT_TYPE);
			if (parserDetected != null && !parserDetected.isBlank()) detectedMimeType = baseMime(parserDetected);
			return new ParserOutcome(
				detectedMimeType, extracted, "PARSED", PARSER_VERSION, null, false, extract(extracted));
		}
		catch (Exception exception) {
			if (causedBy(exception, "WriteLimitReached")) {
				return failure(detectedMimeType, "RESUME_OUTPUT_LIMIT", false);
			}
			if (causedBy(exception, "Encrypted")) {
				return failure(detectedMimeType, "RESUME_ENCRYPTED", false);
			}
			return failure(detectedMimeType, "RESUME_PARSE_FAILED", true);
		}
	}

	private static ParsedResumeProfile extract(String text) {
		List<ResumeEvidence> evidence = new ArrayList<>();
		String name = extractExplicit(text, EXPLICIT_NAME, 1, "name", "HEADER", evidence);
		if (name == null) name = fallbackName(text, evidence);
		List<String> emails = matches(text, EMAIL, "email", "CONTACT", evidence);
		List<String> phones = matches(text, PHONE, "phone", "CONTACT", evidence);
		String location = extractExplicit(text, LOCATION, 1, "location", "LOCATION", evidence);
		DegreeValue degree = education(text, evidence);
		BigDecimal experienceYears = experience(text, evidence);
		List<String> skills = skills(text, evidence);
		return new ParsedResumeProfile(
			name, emails, phones, degree == null ? null : degree.normalized(), experienceYears,
			skills, location, List.copyOf(evidence));
	}

	private static String fallbackName(String text, List<ResumeEvidence> evidence) {
		int offset = 0;
		for (String line : text.split("\\R", -1)) {
			String value = line.strip();
			int start = offset + Math.max(0, line.indexOf(value));
			offset += line.length() + 1;
			if (value.isBlank() || value.length() > 50 || HEADING.matcher(value).matches()
					|| EMAIL.matcher(value).find() || PHONE.matcher(value).find() || value.contains(":" ) || value.contains("：")) {
				continue;
			}
			if (!value.matches(".*[\\p{L}].*")) continue;
			evidence.add(new ResumeEvidence("name", "HEADER", value, start, start + value.length()));
			return value;
		}
		return null;
	}

	private static DegreeValue education(String text, List<ResumeEvidence> evidence) {
		String lower = text.toLowerCase(Locale.ROOT);
		for (DegreeTerm term : DEGREE_TERMS) {
			int start = lower.indexOf(term.term().toLowerCase(Locale.ROOT));
			if (start >= 0) {
				String quote = text.substring(start, start + term.term().length());
				evidence.add(new ResumeEvidence(
					"educationLevel", "EDUCATION", quote, start, start + quote.length()));
				return new DegreeValue(term.normalized());
			}
		}
		return null;
	}

	private static BigDecimal experience(String text, List<ResumeEvidence> evidence) {
		Matcher matcher = EXPERIENCE_ZH.matcher(text);
		if (!matcher.find()) matcher = EXPERIENCE_EN.matcher(text);
		if (!matcher.find(0)) return null;
		String quote = matcher.group();
		evidence.add(new ResumeEvidence(
			"experienceYears", "EXPERIENCE", quote, matcher.start(), matcher.end()));
		return new BigDecimal(matcher.group(1));
	}

	private static List<String> skills(String text, List<ResumeEvidence> evidence) {
		Matcher section = SKILLS_SECTION.matcher(text);
		if (!section.find()) return List.of();
		String body = section.group(1);
		int bodyStart = section.start(1);
		Set<String> values = new LinkedHashSet<>();
		Matcher token = Pattern.compile("[^,，、;；|\\r\\n]+", Pattern.UNICODE_CHARACTER_CLASS).matcher(body);
		while (token.find() && values.size() < 100) {
			String value = token.group().replaceFirst("^\\s*[-*•·]\\s*", "").strip();
			if (value.isBlank() || value.length() > 100) continue;
			values.add(value);
			int relative = token.group().indexOf(value);
			int start = bodyStart + token.start() + Math.max(0, relative);
			evidence.add(new ResumeEvidence("skill", "SKILLS", value, start, start + value.length()));
		}
		return List.copyOf(values);
	}

	private static String extractExplicit(
			String text,
			Pattern pattern,
			int group,
			String field,
			String section,
			List<ResumeEvidence> evidence) {
		Matcher matcher = pattern.matcher(text);
		if (!matcher.find()) return null;
		String value = matcher.group(group).strip();
		int start = matcher.start(group) + matcher.group(group).indexOf(value);
		evidence.add(new ResumeEvidence(field, section, value, start, start + value.length()));
		return value;
	}

	private static List<String> matches(
			String text,
			Pattern pattern,
			String field,
			String section,
			List<ResumeEvidence> evidence) {
		Set<String> values = new LinkedHashSet<>();
		Matcher matcher = pattern.matcher(text);
		while (matcher.find() && values.size() < 20) {
			String value = matcher.group();
			if (values.add(value)) {
				evidence.add(new ResumeEvidence(field, section, value, matcher.start(), matcher.end()));
			}
		}
		return List.copyOf(values);
	}

	private static boolean isSupported(String mimeType) {
		if (mimeType == null) return false;
		String value = baseMime(mimeType);
		return value.equals("text/plain")
			|| value.equals("application/pdf")
			|| value.equals("application/msword")
			|| value.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
	}

	private static String baseMime(String mimeType) {
		return mimeType.toLowerCase(Locale.ROOT).split(";", 2)[0].strip();
	}

	private static boolean causedBy(Throwable throwable, String typeFragment) {
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (current.getClass().getSimpleName().contains(typeFragment)) return true;
		}
		return false;
	}

	private static String normalizeText(String text) {
		return text.replace("\r\n", "\n").replace('\r', '\n').replace("\u0000", "").strip();
	}

	private static ParserOutcome failure(String detectedMimeType, String code, boolean retryable) {
		return new ParserOutcome(
			detectedMimeType, null, "PARSE_FAILED", PARSER_VERSION, code, retryable,
			new ParsedResumeProfile(null, List.of(), List.of(), null, null, List.of(), null, List.of()));
	}

	private record DegreeTerm(String term, String normalized) {
	}

	private record DegreeValue(String normalized) {
	}
}
