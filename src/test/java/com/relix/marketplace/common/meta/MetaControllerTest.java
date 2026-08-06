package com.relix.marketplace.common.meta;

import com.relix.marketplace.config.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = MetaController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(EnumMetadataService.class)
class MetaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsFrontendReadyMetadataInStableOrder() throws Exception {
        mockMvc.perform(get("/api/meta/enums"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.ticketKind", hasSize(2)))
                .andExpect(jsonPath("$.data.ticketKind[0].value").value("OFFER"))
                .andExpect(jsonPath("$.data.ticketKind[0].label").value("Service offer"))
                .andExpect(jsonPath("$.data.ticketKind[0].description").isNotEmpty())
                .andExpect(jsonPath("$.data.ticketKind[0].colorHint").value("blue"))
                .andExpect(jsonPath("$.data.ticketKind[1].value").value("REQUEST"))
                .andExpect(jsonPath("$.data.ticketStatus", hasSize(6)))
                .andExpect(jsonPath("$.data.ticketStatus[1].value").value("OPEN"))
                .andExpect(jsonPath("$.data.pricingMode", hasSize(3)))
                .andExpect(jsonPath("$.data.locationMode", hasSize(3)))
                .andExpect(jsonPath("$.data.engagementStatus", hasSize(8)))
                .andExpect(jsonPath("$.data.engagementStatus[0].value").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.engagementStatus[7].value").value("REFUNDED"))
                .andExpect(jsonPath("$.data.applicationStatus", hasSize(5)))
                .andExpect(jsonPath("$.data.applicationStatus[4].value").value("EXPIRED"))
                .andExpect(jsonPath("$.data.applicationStatus[4].label").value("Expired"))
                .andExpect(jsonPath("$.data.applicationStatus[4].description").isNotEmpty())
                .andExpect(jsonPath("$.data.applicationStatus[4].colorHint").value("slate"))
                .andExpect(jsonPath("$.data.refundStatus", hasSize(4)))
                .andExpect(jsonPath("$.data.refundStatus[0].value").value("PENDING"))
                .andExpect(jsonPath("$.data.refundStatus[0].label").value("Pending"))
                .andExpect(jsonPath("$.data.refundStatus[0].colorHint").value("amber"))
                .andExpect(jsonPath("$.data.refundStatus[1].value").value("PROCESSING"))
                .andExpect(jsonPath("$.data.refundStatus[2].value").value("COMPLETED"))
                .andExpect(jsonPath("$.data.refundStatus[3].value").value("FAILED"))
                .andExpect(jsonPath("$.data.refundStatus[3].label").value("Failed"))
                .andExpect(jsonPath("$.data.refundStatus[3].colorHint").value("red"))
                .andExpect(jsonPath("$.data.credentialStatus", hasSize(4)))
                .andExpect(jsonPath("$.data.credentialType", hasSize(3)))
                .andExpect(jsonPath("$.data.credentialType[2].value").value("BACKGROUND_CHECK"))
                .andExpect(jsonPath("$.data.mediaErrorCodes", hasSize(19)))
                .andExpect(jsonPath("$.data.mediaErrorCodes[0].code").value("IMAGE_EMPTY"))
                .andExpect(jsonPath("$.data.mediaErrorCodes[0].description").isNotEmpty())
                .andExpect(jsonPath("$.data.mediaErrorCodes[10].code").value("FILE_NOT_FOUND"))
                .andExpect(jsonPath("$.data.mediaErrorCodes[12].code").value("DELIVERABLE_REQUIRED"))
                .andExpect(jsonPath("$.data.mediaErrorCodes[16].code").value("CREDENTIAL_DOCUMENT_REQUIRED"));
    }

    @Test
    void declaresTheEndpointPublicAtMethodLevel() throws Exception {
        Method endpoint = MetaController.class.getMethod("getEnums");
        PreAuthorize preAuthorize = endpoint.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("permitAll()");
    }
}
