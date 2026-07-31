package com.smartai.core.recruitment.agent;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.smartai.core.recruitment.agent.RequirementDraftModels.Fields;
import com.smartai.core.recruitment.agent.RequirementDraftModels.RequirementField;

@Component
final class RequirementDraftParser {

	private static final BigDecimal EXPLICIT_CONFIDENCE = new BigDecimal("0.95");
	private static final BigDecimal INFERRED_CONFIDENCE = new BigDecimal("0.35");
	private static final Pattern POSITION_LABEL = Pattern.compile(
		"(?:岗位|职位)\\s*[:：]\\s*([^，。；,;\\n]{2,50})",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern POSITION_ACTION = Pattern.compile(
		"(?:招聘|招募|寻找|寻求)\\s*(?:[一二两三四五六七八九十\\d]+\\s*(?:名|位|人))?\\s*([^，。；,;\\n]{2,50})",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern ROLE_SUFFIX = Pattern.compile(
		"([\\p{IsHan}A-Za-z0-9+#./ -]{2,40}?(?:工程师|经理|专家|主管|总监|顾问|分析师|设计师|专员|负责人))",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern ORGANIZATION = Pattern.compile(
		"(?:用人部门|所属部门|部门|为)\\s*[:：]?\\s*([\\p{IsHan}A-Za-z0-9]{2,24}?(?:事业部|中心|团队|部门|部))(?=在|招聘|招募|需要|寻找|[，。；,;\\s])");
	private static final Pattern ORGANIZATION_SUBJECT = Pattern.compile(
		"([\\p{IsHan}A-Za-z0-9]{2,24}?(?:事业部|中心|团队|部门|部))(?=需要|计划|想|希望|拟|正在)");
	private static final Pattern HEADCOUNT = Pattern.compile("([一二两三四五六七八九十\\d]+)\\s*(?:名|位|人)");
	private static final Pattern TARGET_DATE = Pattern.compile(
		"(?:目标日期|目标到岗|到岗日期|希望到岗|最晚到岗|截止日期|希望)\\s*[:：为是]?\\s*"
			+ "(20\\d{2}(?:[-/.]\\d{1,2}[-/.]\\d{1,2}|年\\d{1,2}月\\d{1,2}日?)|\\d{1,2}月\\d{1,2}日|[^，。；,;]{2,12}(?:内|前))");
	private static final Pattern DATE_VALUE = Pattern.compile(
		"(20\\d{2}(?:[-/.]\\d{1,2}[-/.]\\d{1,2}|年\\d{1,2}月\\d{1,2}日?)|\\d{1,2}月(?:\\d{1,2}日|底|末))(?:前)?(?:到岗|完成)?");
	private static final Pattern CORE_REQUIREMENTS = Pattern.compile(
		"(?:任职要求|候选人需要|候选人需|要求)\\s*[:：]?\\s*([^。；;\\n]{2,1000})",
		Pattern.CASE_INSENSITIVE);
	private static final List<String> CITIES = List.of(
		"北京", "上海", "广州", "深圳", "天津", "重庆", "杭州", "南京", "苏州", "成都", "武汉", "西安", "长沙", "青岛", "大连", "厦门", "郑州", "合肥", "宁波", "海外", "远程");

	Fields parse(String rawInput) {
		String input = rawInput.strip();
		Extracted position = position(input);
		Extracted organization = firstMatch(ORGANIZATION, input);
		if (organization == null) organization = firstMatch(ORGANIZATION_SUBJECT, input);
		Extracted headcount = headcount(input);
		Extracted targetDate = targetDate(input);
		Extracted requirements = firstMatch(CORE_REQUIREMENTS, input);
		List<String> locations = locations(input);

		return new Fields(
			field(position),
			field(organization),
			locations.isEmpty()
				? missingField("原始输入未明确工作地点")
				: explicitField(locations, String.join("、", locations)),
			headcount == null
				? defaultField(1, "原始输入未明确招聘人数，暂按 1 人生成，必须由 HR 确认")
				: explicitField(Integer.parseInt(headcount.value()), headcount.evidence()),
			recruitmentType(input),
			priority(input),
			field(targetDate),
			requirements == null
				? missingField("原始输入未明确核心任职要求")
				: explicitField(requirementItems(requirements.value()), requirements.evidence()),
			knowledgeScope(input));
	}

	private static Extracted position(String input) {
		Extracted extracted = firstMatch(POSITION_LABEL, input);
		if (extracted == null) extracted = firstMatch(POSITION_ACTION, input);
		if (extracted == null) extracted = firstMatch(ROLE_SUFFIX, input);
		if (extracted == null) return null;
		String cleaned = extracted.value()
			.replaceFirst("^(?:一名|一位|一个)", "")
			.replaceFirst("\\s*\\d+\\s*(?:名|位|人)$", "")
			.strip();
		return cleaned.isBlank() ? null : new Extracted(cleaned, extracted.evidence());
	}

	private static Extracted headcount(String input) {
		Extracted extracted = firstMatch(HEADCOUNT, input);
		if (extracted == null) return null;
		Integer value = chineseNumber(extracted.value());
		return value == null || value < 1 || value > 10000
			? null
			: new Extracted(value.toString(), extracted.evidence());
	}

	private static Extracted targetDate(String input) {
		Extracted extracted = lastMatch(TARGET_DATE, input);
		if (extracted == null) extracted = lastMatch(DATE_VALUE, input);
		if (extracted == null) return null;
		try {
			Matcher monthEnd = Pattern.compile("^(\\d{1,2})月(?:底|末)(?:前)?$").matcher(extracted.value());
			if (monthEnd.matches()) {
				int month = Integer.parseInt(monthEnd.group(1));
				LocalDate today = LocalDate.now(ZoneOffset.UTC);
				YearMonth target = YearMonth.of(today.getYear(), month);
				if (target.atEndOfMonth().isBefore(today)) target = target.plusYears(1);
				return new Extracted(target.atEndOfMonth().toString(), extracted.evidence());
			}
			Matcher monthDay = Pattern.compile("^(\\d{1,2})月(\\d{1,2})日?$").matcher(extracted.value());
			if (monthDay.matches()) {
				LocalDate today = LocalDate.now(ZoneOffset.UTC);
				LocalDate target = LocalDate.of(
					today.getYear(),
					Integer.parseInt(monthDay.group(1)),
					Integer.parseInt(monthDay.group(2)));
				if (target.isBefore(today)) target = target.plusYears(1);
				return new Extracted(target.toString(), extracted.evidence());
			}
			Matcher chinese = Pattern.compile("^(20\\d{2})年(\\d{1,2})月(\\d{1,2})日?$").matcher(extracted.value());
			if (chinese.matches()) {
				String normalized = LocalDate.of(
					Integer.parseInt(chinese.group(1)),
					Integer.parseInt(chinese.group(2)),
					Integer.parseInt(chinese.group(3))).toString();
				return new Extracted(normalized, extracted.evidence());
			}
		}
		catch (DateTimeException ignored) {
			return null;
		}
		String normalized = extracted.value().replace('/', '-').replace('.', '-');
		return new Extracted(normalized, extracted.evidence());
	}

	private static RequirementField recruitmentType(String input) {
		if (input.contains("校招") || input.contains("校园招聘")) return explicitField("CAMPUS", "校招");
		if (input.contains("社招") || input.contains("社会招聘")) return explicitField("SOCIAL", "社招");
		if (input.contains("内部招聘") || input.contains("内部竞聘")) return explicitField("INTERNAL", "内部招聘");
		if (input.contains("实习")) {
			return inferredField("CAMPUS", "输入提到实习；当前任务类型契约无实习枚举，暂映射为 CAMPUS，需确认");
		}
		return defaultField("SOCIAL", "原始输入未明确招聘类型，暂按社招生成，必须由 HR 确认");
	}

	private static RequirementField priority(String input) {
		if (input.contains("非常紧急") || input.contains("最高优先级")) return explicitField("URGENT", "非常紧急");
		if (input.contains("紧急") || input.contains("高优先级") || input.contains("优先级高")) {
			return explicitField("HIGH", "紧急/高优先级");
		}
		if (input.contains("低优先级") || input.contains("不着急")) return explicitField("LOW", "低优先级/不着急");
		if (input.contains("普通优先级") || input.contains("正常优先级")) return explicitField("NORMAL", "普通/正常优先级");
		return defaultField("NORMAL", "原始输入未明确优先级，暂按普通优先级生成，必须由 HR 确认");
	}

	private static RequirementField knowledgeScope(String input) {
		Set<String> scopes = new LinkedHashSet<>();
		List<String> evidence = new ArrayList<>();
		if (input.toLowerCase(Locale.ROOT).contains("jd") || input.contains("岗位描述") || input.contains("历史岗位")) {
			scopes.add("JOB_DESCRIPTION_HISTORY");
			evidence.add("历史 JD/岗位描述");
		}
		if (input.contains("人才画像") || input.contains("过往入职") || input.contains("成功画像")) {
			scopes.add("TALENT_PROFILE");
			evidence.add("人才画像/过往入职");
		}
		if (input.contains("用人标准") || input.contains("评分标准") || input.contains("推荐标准")) {
			scopes.add("HIRING_STANDARD");
			evidence.add("用人/评分/推荐标准");
		}
		if (!scopes.isEmpty()) return explicitField(List.copyOf(scopes), String.join("；", evidence));
		return defaultField(
			List.of("JOB_DESCRIPTION_HISTORY", "TALENT_PROFILE"),
			"未指定知识范围，系统仅建议参考历史 JD 和人才画像，必须由 HR 确认");
	}

	private static List<String> locations(String input) {
		return CITIES.stream().filter(input::contains).distinct().toList();
	}

	private static List<String> requirementItems(String value) {
		List<String> items = Pattern.compile("[，,、]").splitAsStream(value)
			.map(String::strip)
			.filter(item -> !item.isBlank())
			.limit(20)
			.toList();
		return items.isEmpty() ? List.of(value.strip()) : items;
	}

	private static RequirementField field(Extracted extracted) {
		return extracted == null
			? missingField("原始输入未提供该字段")
			: explicitField(extracted.value(), extracted.evidence());
	}

	private static RequirementField explicitField(Object value, String evidence) {
		return new RequirementField(value, EXPLICIT_CONFIDENCE, "USER", false, evidence);
	}

	private static RequirementField inferredField(Object value, String evidence) {
		return new RequirementField(value, INFERRED_CONFIDENCE, "AI", true, evidence);
	}

	private static RequirementField defaultField(Object value, String evidence) {
		return new RequirementField(value, new BigDecimal("0.20"), "DEFAULT", true, evidence);
	}

	private static RequirementField missingField(String evidence) {
		return new RequirementField(null, BigDecimal.ZERO, "AI", true, evidence);
	}

	private static Extracted firstMatch(Pattern pattern, String input) {
		Matcher matcher = pattern.matcher(input);
		if (!matcher.find()) return null;
		return new Extracted(matcher.group(1).strip(), matcher.group().strip());
	}

	private static Extracted lastMatch(Pattern pattern, String input) {
		Matcher matcher = pattern.matcher(input);
		Extracted extracted = null;
		while (matcher.find()) extracted = new Extracted(matcher.group(1).strip(), matcher.group().strip());
		return extracted;
	}

	private static Integer chineseNumber(String value) {
		try {
			return Integer.valueOf(value);
		}
		catch (NumberFormatException ignored) {
			return switch (value) {
				case "一" -> 1;
				case "二", "两" -> 2;
				case "三" -> 3;
				case "四" -> 4;
				case "五" -> 5;
				case "六" -> 6;
				case "七" -> 7;
				case "八" -> 8;
				case "九" -> 9;
				case "十" -> 10;
				default -> null;
			};
		}
	}

	private record Extracted(String value, String evidence) {
	}
}
