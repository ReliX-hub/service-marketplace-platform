package com.relix.marketplace.ticket.dto;

import com.relix.marketplace.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ticket author summary embedded to avoid follow-up API calls")
public class AuthorSummary {

    @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(example = "Alex Morgan", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(example = "https://cdn.example.com/avatars/12.jpg", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String avatarUrl;
    @Schema(description = "Author reputation as a client", type = "string", example = "4.85",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal rating;

    public static AuthorSummary from(User user) {
        return AuthorSummary.builder()
                .id(user.getId())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .rating(user.getClientRating())
                .build();
    }
}
