/**
 * Puertos de salida del dominio: lo que el núcleo necesita del mundo exterior,
 * expresado en términos de dominio. Los adaptadores de {@code infrastructure} los
 * implementan (Panache, REST Client, WireMock...).
 *
 * <p>Spring Boot: equivalen a las interfaces que inyectarías en un {@code @Service};
 * la diferencia es que en Quarkus la implementación concreta se elige en tiempo de
 * build (ArC resuelve los beans al compilar), p. ej. con {@code @LookupIfProperty} o
 * {@code @IfBuildProperty} en lugar de {@code @ConditionalOnProperty}.
 */
package com.insightdevelop.interline.domain.port;
