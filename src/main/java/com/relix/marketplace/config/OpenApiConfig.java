package com.relix.marketplace.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String ENGAGEMENT_RESPONSE_SCHEMA = "EngagementResponse";
    private static final String REFUND_SUMMARY_SCHEMA_REF =
            "#/components/schemas/RefundSummaryResponse";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Service Marketplace Platform API")
                        .version("1.0.0")
                        .description("Two-sided service marketplace API for discovery, matching, payments, refunds, settlements, and reviews")
                        .contact(new Contact()
                                .name("Service Marketplace Team")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development")
                ));
    }

    /**
     * OpenAPI 3.0 cannot attach {@code nullable} beside a bare {@code $ref}. Wrap the
     * refund summary reference in {@code allOf} so generated clients model the runtime
     * contract as {@code RefundSummaryResponse | null}.
     */
    @Bean
    public OpenApiCustomizer nullableEngagementRefundSummary() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            Schema<?> engagementSchema = openApi.getComponents().getSchemas()
                    .get(ENGAGEMENT_RESPONSE_SCHEMA);
            if (engagementSchema == null) {
                return;
            }

            Schema<Object> summaryReference = new Schema<>();
            summaryReference.set$ref(REFUND_SUMMARY_SCHEMA_REF);
            ComposedSchema nullableSummary = new ComposedSchema();
            nullableSummary.setDescription(
                    "Refund lifecycle summary when this engagement has a refund; null otherwise");
            nullableSummary.setNullable(true);
            nullableSummary.addAllOfItem(summaryReference);
            engagementSchema.addProperty("refundSummary", nullableSummary);
        };
    }
}
