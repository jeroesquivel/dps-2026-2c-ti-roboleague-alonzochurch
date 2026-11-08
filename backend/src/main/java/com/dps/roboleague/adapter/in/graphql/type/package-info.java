/**
 * Los tipos de salida de la API GraphQL y sus mappers desde el núcleo. Los nombres de los componentes de cada record
 * son los campos del esquema; un componente que no está en el esquema (por ejemplo el {@code teamId} de una fila de
 * posiciones) sólo lo usan los resolvers de los campos que se cargan aparte. Este paquete no depende de los
 * controllers, así los tipos pueden referirse entre sí sin formar ciclos entre paquetes.
 */
package com.dps.roboleague.adapter.in.graphql.type;
