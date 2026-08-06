package com.relix.marketplace.common.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageResponseTest {

    @Test
    void mapsItemsAndCopiesAllPaginationMetadata() {
        PageImpl<Integer> source = new PageImpl<>(
                List.of(10, 20),
                PageRequest.of(1, 2),
                5);

        PageResponse<String> response = PageResponse.of(source, value -> "$" + value);

        assertEquals(List.of("$10", "$20"), response.getItems());
        assertEquals(1, response.getPage());
        assertEquals(2, response.getSize());
        assertEquals(5, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertTrue(response.isHasNext());
    }

    @Test
    void rejectsNullSourceOrMapper() {
        PageImpl<Integer> source = new PageImpl<>(List.of(1));

        assertThrows(NullPointerException.class, () -> PageResponse.of(null, Object::toString));
        assertThrows(NullPointerException.class, () -> PageResponse.of(source, null));
    }
}
