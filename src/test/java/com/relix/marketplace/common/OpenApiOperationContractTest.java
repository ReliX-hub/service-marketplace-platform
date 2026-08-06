package com.relix.marketplace.common;

import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiOperationContractTest {

    @Test
    void everyRequestMappedControllerMethodDeclaresAnOperation() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        List<String> missingOperations = new ArrayList<>();
        for (var component : scanner.findCandidateComponents("com.relix.marketplace")) {
            Class<?> controller = Class.forName(component.getBeanClassName());
            if (controller.getEnclosingClass() != null) {
                continue;
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
                        && AnnotatedElementUtils.findMergedAnnotation(method, Operation.class) == null) {
                    missingOperations.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertTrue(
                missingOperations.isEmpty(),
                () -> "Request-mapped endpoints missing @Operation: " + missingOperations);
    }
}
