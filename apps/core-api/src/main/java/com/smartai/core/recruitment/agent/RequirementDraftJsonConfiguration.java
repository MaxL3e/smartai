package com.smartai.core.recruitment.agent;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

@Configuration(proxyBeanMethods = false)
class RequirementDraftJsonConfiguration {

	@Bean
	ObjectMapper requirementDraftObjectMapper() {
		SimpleModule javaTime = new SimpleModule()
			.addSerializer(OffsetDateTime.class, new JsonSerializer<>() {
				@Override
				public void serialize(OffsetDateTime value, JsonGenerator generator, SerializerProvider serializers)
						throws IOException {
					generator.writeString(value.toString());
				}
			})
			.addDeserializer(OffsetDateTime.class, new JsonDeserializer<>() {
				@Override
				public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
					return OffsetDateTime.parse(parser.getValueAsString());
				}
			})
			.addSerializer(LocalDate.class, new JsonSerializer<>() {
				@Override
				public void serialize(LocalDate value, JsonGenerator generator, SerializerProvider serializers)
						throws IOException {
					generator.writeString(value.toString());
				}
			})
			.addDeserializer(LocalDate.class, new JsonDeserializer<>() {
				@Override
				public LocalDate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
					return LocalDate.parse(parser.getValueAsString());
				}
			});
		return new ObjectMapper().registerModule(javaTime);
	}
}
