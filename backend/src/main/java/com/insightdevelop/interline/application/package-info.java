/**
 * Capa de aplicación: casos de uso que orquestan el dominio y los puertos.
 *
 * <p>Aquí sí se usan CDI y transacciones (Quarkus/Jakarta), pero nunca clases de
 * {@code infrastructure} (lo verifica ArchUnit). Los casos de uso son beans
 * {@code @ApplicationScoped}, el equivalente a {@code @Service} en Spring.
 */
package com.insightdevelop.interline.application;
