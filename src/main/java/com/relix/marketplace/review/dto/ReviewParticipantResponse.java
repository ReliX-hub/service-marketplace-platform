package com.relix.marketplace.review.dto;

import com.relix.marketplace.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Public identity summary embedded in a review")
public class ReviewParticipantResponse {

    @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(example = "Alex Morgan", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(example = "https://cdn.example.com/avatars/12.jpg",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String avatarUrl;

    public static ReviewParticipantResponse from(User user) {
        return ReviewParticipantResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
