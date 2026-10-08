package com.insightdevelop.interline.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.insightdevelop.interline.infrastructure.NativeReflectionConfig;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * En nativo, Jackson solo ve los miembros registrados para reflexión. Como los recursos REST
 * devuelven {@code Response}, Quarkus no deduce qué DTO serializa: si uno queda sin registrar,
 * el JSON sale incompleto SOLO en el ejecutable nativo (pasó con {@code ProblemDto}, que perdía
 * {@code code} y {@code detail}). Esta prueba lo detecta en el build JVM, en milisegundos.
 */
class NativeReflectionCoverageTest {

    @Test
    void every_generated_rest_dto_is_registered_for_reflection() {
        Set<String> explicitTargets = Arrays.stream(NativeReflectionConfig.class
                        .getAnnotation(RegisterForReflection.class).targets())
                .map(Class::getName)
                .collect(Collectors.toSet());

        List<String> missing = new ClassFileImporter()
                .importPackages("com.insightdevelop.interline.infrastructure.rest.dto")
                .stream()
                .filter(c -> !c.isNestedClass())
                .filter(c -> !c.isAnnotatedWith(RegisterForReflection.class))
                .map(JavaClass::getName)
                .filter(name -> !explicitTargets.contains(name))
                .sorted()
                .toList();

        assertThat(missing).as("DTO sin registrar para reflexión en nativo").isEmpty();
    }
}
