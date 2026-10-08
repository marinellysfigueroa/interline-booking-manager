/**
 * Núcleo de dominio (hexágono interior).
 *
 * <p>Java puro: no depende de Quarkus, Jakarta EE, Hibernate ni Jackson. Esta regla
 * la verifica {@code HexagonalArchitectureTest} con ArchUnit en cada build.
 *
 * <p>Equivalente en Spring Boot: el mismo principio que aplicarías al separar tus
 * entidades de dominio de las {@code @Entity} JPA y de los {@code @Service}; aquí ni
 * siquiera hay anotaciones de CDI ({@code @ApplicationScoped}). Los servicios de
 * dominio se exponen como beans desde la capa de aplicación mediante productores CDI
 * (el equivalente a un método {@code @Bean} dentro de una {@code @Configuration}).
 */
package com.insightdevelop.interline.domain;
